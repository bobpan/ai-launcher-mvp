package com.bobpan.ailauncher.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val LauncherShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small      = RoundedCornerShape(10.dp),
    medium     = RoundedCornerShape(16.dp),
    large      = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

object Space {
    val xxs: Dp = 4.dp
    val xs:  Dp = 8.dp
    val sm:  Dp = 12.dp
    val md:  Dp = 16.dp
    val lg:  Dp = 20.dp
    val xl:  Dp = 24.dp
    val xxl: Dp = 32.dp
}
