package com.planavora.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

private val Ivory = Color(0xfffaf7f2)
private val Plum = Color(0xff493a58)
private val light = lightColorScheme(
    primary = Plum, onPrimary = Color.White, primaryContainer = Color(0xffeee3f1), onPrimaryContainer = Color(0xff362340),
    secondary = Color(0xff3e5c46), secondaryContainer = Color(0xffdce9da), onSecondaryContainer = Color(0xff213725),
    tertiary = Color(0xff775626), tertiaryContainer = Color(0xfff6e5c6), onTertiaryContainer = Color(0xff382509),
    background = Ivory, surface = Color(0xfffffdfb), surfaceVariant = Color(0xfff2ecef), onSurface = Color(0xff29242c),
    onSurfaceVariant = Color(0xff554c58), outlineVariant = Color(0xffdfd4dc)
)
private val dark = darkColorScheme(
    primary = Color(0xffe3c7ee), onPrimary = Color(0xff3d2748), primaryContainer = Color(0xff45344f), onPrimaryContainer = Color(0xfff4dcff),
    secondaryContainer = Color(0xff2b4234), onSecondaryContainer = Color(0xffd0ebd4),
    tertiaryContainer = Color(0xff4c3821), onTertiaryContainer = Color(0xffffe1b2),
    background = Color(0xff1d1820), surface = Color(0xff27212b), surfaceVariant = Color(0xff382f3b),
    onSurface = Color(0xfff0e6f2), onSurfaceVariant = Color(0xffdbcedd), outlineVariant = Color(0xff53465a)
)
private val type = Typography().let {
    it.copy(displaySmall = it.displaySmall.copy(fontFamily = FontFamily.Serif),
        headlineMedium = it.headlineMedium.copy(fontFamily = FontFamily.Serif),
        headlineSmall = it.headlineSmall.copy(fontFamily = FontFamily.Serif))
}

@Composable
fun PlanTheme(theme: String?, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (theme == "dark" || (theme != "light" && isSystemInDarkTheme())) dark else light,
        typography = type,
        shapes = Shapes(small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp)),
        content = content
    )
}

@Composable
fun Panel(content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder().copy(width = 0.7.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}
