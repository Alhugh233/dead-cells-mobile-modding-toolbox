package com.deadcells.modding.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@Composable
fun DCMMTTheme(content: @Composable () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    val controller = ThemeController(
        colorSchemeMode = if (darkTheme) ColorSchemeMode.Dark else ColorSchemeMode.Light,
        isDark = darkTheme,
    )
    MiuixTheme(
        controller = controller,
        content = content,
    )
}
