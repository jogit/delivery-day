package app.deliveryday

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Welcome screen, before signing in. */
@Composable
fun WelcomeView(busy: Boolean, error: String?, onLogin: () -> Unit, onPaste: (String) -> Unit) {
    var manual by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }
    val appName = stringResource(R.string.app_name)

    Column(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(TeslaColors.palette.welcomeTop, TeslaColors.Bg), endY = 1400f))
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Box(
            Modifier.size(112.dp).shadow(24.dp, RoundedCornerShape(30.dp), spotColor = TeslaColors.Red)
                .clip(RoundedCornerShape(30.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF22262D), Color(0xFF121418)))),  // same as the launcher icon
            contentAlignment = Alignment.Center,
        ) {
            Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(28.dp))
        Text(appName, color = TeslaColors.Text, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.welcome_subtitle), color = TeslaColors.Muted, fontSize = 15.sp, textAlign = TextAlign.Center, lineHeight = 21.sp)

        Spacer(Modifier.height(32.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(TeslaColors.Card).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Feature("📅", stringResource(R.string.feature_window_title), stringResource(R.string.feature_window_text))
            Feature("🔔", stringResource(R.string.feature_alerts_title), stringResource(R.string.feature_alerts_text))
            Feature("📱", stringResource(R.string.feature_widget_title), stringResource(R.string.feature_widget_text))
        }

        // Plainly stated before signing in: unofficial, can stop working, where credentials go.
        Spacer(Modifier.height(16.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(TeslaColors.Amber.copy(alpha = 0.10f))
                .border(1.dp, TeslaColors.Amber.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(stringResource(R.string.disclaimer_title), color = TeslaColors.Amber, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.disclaimer_text), color = TeslaColors.Text, fontSize = 13.sp, lineHeight = 18.sp)
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onLogin, enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TeslaColors.Red),
        ) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.login_busy), fontSize = 16.sp)
            } else Text(stringResource(R.string.login_button), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.login_hint, appName), color = TeslaColors.Muted, fontSize = 12.sp, textAlign = TextAlign.Center)

        error?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(16.dp))
            Text(
                it, color = TeslaColors.Red, fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(TeslaColors.Red.copy(alpha = 0.12f)).padding(12.dp),
            )
        }

        Spacer(Modifier.height(20.dp))
        TextButton(onClick = { manual = !manual }) {
            Text(stringResource(if (manual) R.string.manual_hide else R.string.manual_toggle), color = TeslaColors.Muted, fontSize = 13.sp)
        }
        AnimatedVisibility(manual) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    url, { url = it }, singleLine = true,
                    label = { Text("tesla://auth/callback?code=…") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(enabled = url.isNotBlank() && !busy, onClick = { onPaste(url) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.manual_confirm))
                }
            }
        }
    }
}

@Composable
private fun Feature(emoji: String, title: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(TeslaColors.CardHigh), contentAlignment = Alignment.Center) {
            Text(emoji, fontSize = 18.sp)
        }
        Column {
            Text(title, color = TeslaColors.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(text, color = TeslaColors.Muted, fontSize = 13.sp)
        }
    }
}
