package com.neojou.foldmerge

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import org.jetbrains.compose.resources.Font

private val Ink = Color(0xFF241C14)
private val InkSoft = Color(0xFF5E5348)
private val Paper = Color(0xFFF3EEE6)
private val PaperCard = Color(0xFFFBF7F1)
private val PaperDeep = Color(0xFFE6DDD0)
private val Bronze = Color(0xFF8C5A32)
private val BronzeInk = Color(0xFF4E2E18)
private val Line = Color(0xFFD5C7B6)

/** Side-by-side compare window. Equal rows share [ComparePaper]; differences use the two washes. */
internal val ComparePaper = Color(0xFFFBF7F1)
internal val CompareInk = Color(0xFF241C14)
internal val CompareMuted = Color(0xFF5E5348)
internal val CompareBronze = Color(0xFF8C5A32)
internal val CompareLeft = Color(0xFFF3E4D0)
internal val CompareLeftSelected = Color(0xFFE8D3B8)
internal val CompareLeftPad = Color(0xFFF8F1E7)
internal val CompareRight = Color(0xFFE3E6DC)
internal val CompareRightSelected = Color(0xFFD4D9CE)
internal val CompareRightPad = Color(0xFFEEF0EA)
internal val CompareDecided = Color(0xFFE4D6C6)
internal val CompareDecidedSelected = Color(0xFFD7C6B2)

/**
 * Bundled CJK-capable family — Noto Sans TC Regular (SIL OFL 1.1).
 *
 * Compose Wasm/Desktop draw with Skia, which does **not** pick up browser or
 * CSS fonts. Without a bundled CJK face, Chinese glyphs render as blank.
 */
@Composable
fun appFontFamily(): FontFamily = FontFamily(Font(Res.font.notosanstc_regular))

private fun Typography.withFontFamily(fontFamily: FontFamily): Typography = copy(
    displayLarge = displayLarge.copy(fontFamily = fontFamily),
    displayMedium = displayMedium.copy(fontFamily = fontFamily),
    displaySmall = displaySmall.copy(fontFamily = fontFamily),
    headlineLarge = headlineLarge.copy(fontFamily = fontFamily),
    headlineMedium = headlineMedium.copy(fontFamily = fontFamily),
    headlineSmall = headlineSmall.copy(fontFamily = fontFamily),
    titleLarge = titleLarge.copy(fontFamily = fontFamily),
    titleMedium = titleMedium.copy(fontFamily = fontFamily),
    titleSmall = titleSmall.copy(fontFamily = fontFamily),
    bodyLarge = bodyLarge.copy(fontFamily = fontFamily),
    bodyMedium = bodyMedium.copy(fontFamily = fontFamily),
    bodySmall = bodySmall.copy(fontFamily = fontFamily),
    labelLarge = labelLarge.copy(fontFamily = fontFamily),
    labelMedium = labelMedium.copy(fontFamily = fontFamily),
    labelSmall = labelSmall.copy(fontFamily = fontFamily),
)

/**
 * Warm paper, ink, and a single bronze accent.
 *
 * Program text does not use this family. Callers that draw file contents use [FontFamily.Monospace].
 */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val fontFamily = appFontFamily()
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Bronze,
            onPrimary = PaperCard,
            primaryContainer = Color(0xFFF0E0D0),
            onPrimaryContainer = BronzeInk,
            secondary = Bronze,
            onSecondary = PaperCard,
            secondaryContainer = Color(0xFFF0E0D0),
            onSecondaryContainer = BronzeInk,
            background = Paper,
            onBackground = Ink,
            surface = PaperCard,
            onSurface = Ink,
            surfaceVariant = PaperDeep,
            onSurfaceVariant = InkSoft,
            surfaceContainerLowest = Color(0xFFFFFCF8),
            surfaceContainerLow = PaperCard,
            surfaceContainer = Paper,
            surfaceContainerHigh = PaperDeep,
            surfaceContainerHighest = Color(0xFFDDD2C3),
            outline = Line,
            outlineVariant = Color(0xFFE7DCCE),
            error = Color(0xFF8C3D2F),
            onError = PaperCard,
            errorContainer = Color(0xFFF4DED6),
            onErrorContainer = Color(0xFF5C241C),
        ),
        typography = Typography().withFontFamily(fontFamily),
        content = content,
    )
}
