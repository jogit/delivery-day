package app.deliveryday

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

const val ISSUES_URL = "https://github.com/jogit/delivery-day/issues/new/choose"
const val RELEASES_URL = "https://github.com/jogit/delivery-day/releases/latest"
const val PROJECT_URL = "https://jogit.github.io/delivery-day/"

@Composable
fun appVersion(): String {
    val ctx = LocalContext.current
    return remember { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "" }
}
