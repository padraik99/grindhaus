package com.padraik99.grindhaus.ui.components

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** The installed version, e.g. "0.1.12" (the CI build number is the last part). Empty if unknown. */
@Composable
fun rememberVersionName(): String {
    val context = LocalContext.current
    return remember(context) { appVersionName(context) }
}

fun appVersionName(context: Context): String = runCatching {
    val pm = context.packageManager
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        pm.getPackageInfo(context.packageName, 0)
    }
    info.versionName.orEmpty()
}.getOrDefault("")
