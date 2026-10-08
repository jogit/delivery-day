package app.deliveryday

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var store: Store
    private lateinit var api: TeslaApi
    /** tesla://auth/callback?code=… URL received from the browser after signing in. */
    private var callback by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        api = TeslaApi(store)
        callback = intent?.data?.toString()
        // Re-apply the check settings (interval, flex window) after an update.
        if (store.refreshToken != null && !store.needsLogin) schedule()
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)

        // Enforced from target SDK 35: draw behind the system bars and pad the content ourselves.
        enableEdgeToEdge()
        setContent {
            TeslaTheme {
                Surface(Modifier.fillMaxSize()) { Box(Modifier.safeDrawingPadding()) { Screen() } }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        callback = intent.data?.toString()
    }

    private fun schedule() = CheckWorker.schedule(this, force = true)

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Screen() {
        val scope = rememberCoroutineScope()
        var connected by remember { mutableStateOf(store.refreshToken != null && !store.needsLogin) }
        var loginError by remember { mutableStateOf<String?>(null) }
        var lastError by remember { mutableStateOf(store.lastError) }
        var last by remember { mutableStateOf(store.lastCheck) }
        var busy by remember { mutableStateOf(false) }
        var snapshot by remember { mutableStateOf(store.snapshot) }
        var showRaw by remember { mutableStateOf(false) }
        val errorFormat = stringResource(R.string.error_prefix)

        fun reload() {
            lastError = store.lastError; last = store.lastCheck; snapshot = store.snapshot
            if (store.needsLogin) connected = false
        }

        // Back in the foreground: the background worker may have checked meanwhile (local read, no network call).
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { reload() }

        fun submit(u: String) {
            busy = true
            scope.launch {
                val r = runCatching { withContext(Dispatchers.IO) { api.exchangeCode(u) } }
                busy = false
                if (r.isSuccess) {
                    store.needsLogin = false; connected = true; loginError = null; schedule()
                    busy = true; checkNow(); reload(); busy = false
                } else loginError = String.format(errorFormat, r.exceptionOrNull()?.message)
            }
        }
        LaunchedEffect(callback) {
            callback?.let { callback = null; if (!connected) submit(it) }
        }

        if (!connected) {
            Column(Modifier.fillMaxSize().background(TeslaColors.Bg).verticalScroll(rememberScrollState())) {
                WelcomeView(
                    busy = busy,
                    error = loginError ?: if (store.needsLogin) stringResource(R.string.relogin_needed) else null,
                    onLogin = { loginError = null; startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(api.loginUrl()))) },
                    onPaste = { submit(it) },
                )
            }
            return
        }

        fun refresh() {
            busy = true
            scope.launch { checkNow(); reload(); busy = false }
        }

        var menu by remember { mutableStateOf(false) }
        var confirmLogout by remember { mutableStateOf(false) }

        // Pull down at the top of the screen to run a check.
        PullToRefreshBox(isRefreshing = busy, onRefresh = ::refresh, modifier = Modifier.fillMaxSize().background(TeslaColors.Bg)) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header: app name, last check, refresh, menu.
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.app_name).uppercase(), color = TeslaColors.Red, fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = 3.sp)
                    Text(Fmt.updated(this@MainActivity, last).ifEmpty { stringResource(R.string.never_updated) }, color = TeslaColors.Muted, fontSize = 12.sp)
                }
                IconButton(onClick = ::refresh, enabled = !busy) {
                    Icon(painterResource(R.drawable.ic_refresh), stringResource(R.string.action_refresh), tint = TeslaColors.Text)
                }
                Box {
                    IconButton(onClick = { menu = true }) { Text("⋮", color = TeslaColors.Text, fontSize = 22.sp) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(if (showRaw) R.string.menu_raw_hide else R.string.menu_raw_show)) },
                            onClick = { showRaw = !showRaw; menu = false })
                        DropdownMenuItem(text = { Text(stringResource(R.string.menu_logout), color = TeslaColors.Red) },
                            onClick = { confirmLogout = true; menu = false })
                    }
                }
            }
            lastError?.let { Notice("⚠ " + String.format(errorFormat, it), TeslaColors.Red) }
            store.tasksError?.let { Notice(stringResource(R.string.tasks_error, it), TeslaColors.Amber) }

            val orders = remember(snapshot) { Orders.parse(snapshot) }
            if (orders.isEmpty()) Text(stringResource(R.string.no_orders), color = TeslaColors.Muted)
            var tradeIn by remember { mutableStateOf(store.tradeInEstimate) }
            // Reference to know later whether Tesla deducted the trade-in: the amount due when it was typed.
            val due = orders.firstOrNull()?.amountDue
            if (tradeIn != null && store.tradeInBaseline == null && due != null) store.tradeInBaseline = due
            val history = remember(snapshot) { store.history }
            orders.forEach {
                OrderView(
                    it, tradeIn, store.tradeInBaseline,
                    onTradeInEstimate = { v ->
                        store.tradeInEstimate = v; tradeIn = v
                        store.tradeInBaseline = if (v != null) it.amountDue else null
                    },
                    lastNews = store.lastNews, lastNewsAt = store.lastNewsAt, history = history,
                )
            }
            Text(
                stringResource(if (CheckWorker.intervalFor(snapshot) == 15L) R.string.auto_check_fast else R.string.auto_check_hourly),
                color = TeslaColors.Muted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
            if (showRaw) Recap.details(snapshot).forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = TeslaColors.Muted) }
            Spacer(Modifier.height(16.dp))
        }
        }

        if (confirmLogout) AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text(stringResource(R.string.logout_title)) },
            text = { Text(stringResource(R.string.logout_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    WorkManager.getInstance(this@MainActivity).cancelUniqueWork("check")
                    store.logout(); connected = false; lastError = null
                }) { Text(stringResource(R.string.menu_logout), color = TeslaColors.Red) }
            },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    @Composable
    private fun Notice(text: String, color: Color) {
        Text(
            text, color = color, fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth().background(color.copy(alpha = 0.12f), RoundedCornerShape(12.dp)).padding(12.dp),
        )
    }

    private suspend fun checkNow() = withContext(Dispatchers.IO) { CheckWorker.checkNow(this@MainActivity) }
}
