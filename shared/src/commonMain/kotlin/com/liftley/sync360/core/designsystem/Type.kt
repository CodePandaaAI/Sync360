package com.liftley.sync360.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import sync360.shared.generated.resources.Res
import sync360.shared.generated.resources.bricolage_grotesque_variable

private val baseline = Typography()

@Composable
fun appTypography(): Typography {
    val bricolageGrotesque = FontFamily(
        Font(Res.font.bricolage_grotesque_variable, weight = FontWeight.Light),
        Font(Res.font.bricolage_grotesque_variable, weight = FontWeight.Normal),
        Font(Res.font.bricolage_grotesque_variable, weight = FontWeight.Medium),
        Font(Res.font.bricolage_grotesque_variable, weight = FontWeight.SemiBold),
        Font(Res.font.bricolage_grotesque_variable, weight = FontWeight.Bold)
    )

    return Typography(
        displayLarge = baseline.displayLarge.copy(fontFamily = bricolageGrotesque),
        displayMedium = baseline.displayMedium.copy(fontFamily = bricolageGrotesque),
        displaySmall = baseline.displaySmall.copy(fontFamily = bricolageGrotesque),
        headlineLarge = baseline.headlineLarge.copy(fontFamily = bricolageGrotesque),
        headlineMedium = baseline.headlineMedium.copy(fontFamily = bricolageGrotesque),
        headlineSmall = baseline.headlineSmall.copy(fontFamily = bricolageGrotesque),
        titleLarge = baseline.titleLarge.copy(fontFamily = bricolageGrotesque),
        titleMedium = baseline.titleMedium.copy(fontFamily = bricolageGrotesque),
        titleSmall = baseline.titleSmall.copy(fontFamily = bricolageGrotesque),
        bodyLarge = baseline.bodyLarge.copy(fontFamily = bricolageGrotesque),
        bodyMedium = baseline.bodyMedium.copy(fontFamily = bricolageGrotesque),
        bodySmall = baseline.bodySmall.copy(fontFamily = bricolageGrotesque),
        labelLarge = baseline.labelLarge.copy(fontFamily = bricolageGrotesque),
        labelMedium = baseline.labelMedium.copy(fontFamily = bricolageGrotesque),
        labelSmall = baseline.labelSmall.copy(fontFamily = bricolageGrotesque)
    )
}
