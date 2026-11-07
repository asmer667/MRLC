package com.neonplayer

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

data class NeonPalette(
    val background: Color = Color(0xFF090D12),
    val panel: Color = Color(0xFF121A22),
    val panel2: Color = Color(0xFF18232D),
    val primary: Color = Color(0xFF69E6D2),
    val secondary: Color = Color(0xFFB4A1FF),
    val muted: Color = Color(0xFF91A0AD)
)

val LocalNeonPalette = compositionLocalOf { NeonPalette() }

@Composable fun currentPalette(settings: PlayerSettings) = NeonPalette(
    background = if (settings.amoled) Color.Black else Color(settings.backgroundColor),
    panel = if (settings.amoled) Color(0xFF080808) else Color(settings.backgroundColor).copy(red = (Color(settings.backgroundColor).red * .78f).coerceIn(0f,1f), green = (Color(settings.backgroundColor).green * .78f).coerceIn(0f,1f), blue = (Color(settings.backgroundColor).blue * .78f).coerceIn(0f,1f)),
    panel2 = if (settings.amoled) Color(0xFF101010) else Color(settings.backgroundColor).copy(alpha = .92f),
    primary = Color(settings.primaryColor), secondary = Color(settings.secondaryColor)
)

fun applyLauncherIcon(context: Context, style: String) {
    val aliases = mapOf(
        "mint" to "com.neonplayer.LauncherMint",
        "violet" to "com.neonplayer.LauncherViolet",
        "amber" to "com.neonplayer.LauncherAmber",
        "mono" to "com.neonplayer.LauncherMono"
    )
    val pm = context.packageManager
    aliases.values.forEach { className ->
        pm.setComponentEnabledSetting(ComponentName(context, className),
            if (className == aliases[style]) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP)
    }
}

fun parseHexColor(value: String, fallback: Long): Long {
    val clean = value.trim().removePrefix("#")
    return runCatching { (0xFF000000L or (clean.toLong(16) and 0xFFFFFFL)) }.getOrDefault(fallback)
}
