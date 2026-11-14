package com.smarthisab.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** কার্ড নিচ থেকে ধীরে উঠে আসে (একের পর এক) */
fun Modifier.riseIn(index: Int = 0): Modifier = composed {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(index.coerceIn(0, 8) * 70L); a.animateTo(1f, tween(650, easing = FastOutSlowInEasing)) }
    graphicsLayer {
        alpha = a.value; translationY = (1f - a.value) * 56f
        val s = .96f + .04f * a.value; scaleX = s; scaleY = s
    }
}

/** ছোট জিনিস লাফিয়ে আসে */
fun Modifier.popIn(index: Int = 0): Modifier = composed {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(index.coerceIn(0, 40) * 12L); a.animateTo(1f, spring(dampingRatio = .5f, stiffness = Spring.StiffnessMedium)) }
    graphicsLayer { scaleX = a.value; scaleY = a.value; alpha = a.value.coerceIn(0f, 1f) }
}

/** চাপলে একটু বসে যায়, ছাড়লে ফিরে আসে */
fun Modifier.bounceClick(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) .95f else 1f, spring(dampingRatio = .5f, stiffness = Spring.StiffnessMedium), label = "press")
    graphicsLayer { scaleX = s; scaleY = s }
        .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
}

/** আলোর ঝলক বাঁ থেকে ডানে যায় */
fun Modifier.shine(periodMs: Int = 4200): Modifier = composed {
    val x by rememberInfiniteTransition(label = "shine").animateFloat(-0.5f, 1.7f, infiniteRepeatable(tween(periodMs, easing = LinearEasing)), label = "x")
    drawWithContent {
        drawContent()
        val left = x * size.width
        drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = .3f), Color.Transparent), startX = left, endX = left + size.width * .4f))
    }
}

/** ৩D ভাব: ওপরে আলোর হাইলাইট, নিচে ভেতরের ছায়া */
fun Modifier.glossy(corner: Dp = 18.dp): Modifier = drawWithContent {
    drawContent()
    val cr = CornerRadius(corner.toPx())
    drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = .26f), Color.Transparent), endY = size.height * .45f), cornerRadius = cr)
    drawRoundRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .14f)), startY = size.height * .6f, endY = size.height), cornerRadius = cr)
}

private fun circle(cx: Number, cy: Number, r: Number): String {
    val x = cx.toDouble(); val y = cy.toDouble(); val rad = r.toDouble()
    val radStr = if (rad % 1.0 == 0.0) rad.toInt().toString() else rad.toString()
    val dStr = if ((2 * rad) % 1.0 == 0.0) (2 * rad).toInt().toString() else (2 * rad).toString()
    val mdStr = if ((-2 * rad) % 1.0 == 0.0) (-2 * rad).toInt().toString() else (-2 * rad).toString()
    val xDiffStr = if ((x - rad) % 1.0 == 0.0) (x - rad).toInt().toString() else (x - rad).toString()
    val yStr = if (y % 1.0 == 0.0) y.toInt().toString() else y.toString()
    return "M$xDiffStr $yStr a$radStr $radStr 0 1 0 $dStr 0 a$radStr $radStr 0 1 0 $mdStr 0 z "
}

object Ic {
    val home = "M3 11 l9 -8 l9 8 v9 a1 1 0 0 1 -1 1 h-5 v-6 H9 v6 H4 a1 1 0 0 1 -1 -1 z"
    val clock = circle(12, 12, 9) + "M12 7 v5 l3 2"
    val chart = "M4 20 V10 M10 20 V4 M16 20 v-7 M22 20 H2"
    val sliders = "M4 6 h8 M18 6 h2 M4 12 h2 M12 12 h8 M4 18 h10 M20 18 h0.01 " + circle(15, 6, 2) + circle(9, 12, 2) + circle(17, 18, 2)
    val back = "M15 5 l-7 7 l7 7"
    val fwd = "M9 5 l7 7 l-7 7"
    val search = circle(11, 11, 7) + "M20 20 l-4 -4"
    val cal = "M6 5 h12 a3 3 0 0 1 3 3 v10 a3 3 0 0 1 -3 3 H6 a3 3 0 0 1 -3 -3 V8 a3 3 0 0 1 3 -3 z M3 10 h18 M8 3 v4 M16 3 v4"
    val cloud = "M7 18 a4 4 0 0 1 -0.5 -7.97 A5.5 5.5 0 0 1 17 9 a4.5 4.5 0 0 1 0.5 9 z"
    val refresh = "M20 11 a8 8 0 0 0 -14 -4 M4 13 a8 8 0 0 0 14 4 M20 4 v7 h-7 M4 20 v-7 h7"
    val trash = "M4 7 h16 M10 11 v6 M14 11 v6 M6 7 l1 13 h10 l1 -13 M9 7 V4 h6 v3"
    val edit = "M4 20 h4 L19 9 l-4 -4 L4 16 z M13 7 l4 4"
    val lock = "M7 11 h10 a2 2 0 0 1 2 2 v6 a2 2 0 0 1 -2 2 H7 a2 2 0 0 1 -2 -2 v-6 a2 2 0 0 1 2 -2 z M8 11 V8 a4 4 0 0 1 8 0 v3"
    val eye = "M2 12 s4 -7 10 -7 s10 7 10 7 s-4 7 -10 7 s-10 -7 -10 -7 z " + circle(12, 12, 3)
    val eyeOff = "M2 12 s4 -7 10 -7 s10 7 10 7 s-4 7 -10 7 s-10 -7 -10 -7 z " + circle(12, 12, 3) + "M4 4 l16 16"
    val sun = circle(12, 12, 4) + "M12 2 v2 M12 20 v2 M2 12 h2 M20 12 h2 M4.9 4.9 l1.4 1.4 M17.7 17.7 l1.4 1.4 M4.9 19.1 l1.4 -1.4 M17.7 6.3 l1.4 -1.4"
    val target = "M3 17 l6 -6 l4 4 l8 -8 M15 7 h6 v6"
    val gauge = "M3 17 a9 9 0 0 1 18 0 M12 17 l4 -5"
    val coin = "M12 3 v18 M16.5 7 c-1 -1.8 -2.8 -2.5 -4.5 -2.5 c-2.5 0 -4 1.2 -4 3 c0 4 8.5 2 8.5 6 c0 1.8 -1.8 3 -4.5 3 c-2 0 -3.7 -0.8 -4.5 -2.5"
    val box = "M21 8 l-9 -5 l-9 5 v8 l9 5 l9 -5 z M3 8 l9 5 l9 -5 M12 13 v8"
    val csun = "M7 19 a4 4 0 0 1 -0.5 -7.97 A5.5 5.5 0 0 1 17.5 12 a3.5 3.5 0 0 1 -0.5 7 z M9 5 l1 1.5 M4 8 l1.7 0.6 M14.5 4 l-0.3 1.8"
    val drop = "M12 3 s6 6.5 6 11 a6 6 0 0 1 -12 0 c0 -4.5 6 -11 6 -11 z"
    val wind = "M3 8 h11 a3 3 0 1 0 -3 -3 M3 12 h15 a3 3 0 1 1 -3 3 M3 16 h8"
    val rain = "M7 15 a4 4 0 0 1 -0.5 -7.97 A5.5 5.5 0 0 1 17 6 a4.5 4.5 0 0 1 0.5 9 z M8 18 l-1 2 M12 18 l-1 2 M16 18 l-1 2"
    val leaf = "M5 19 C5 10 11 4 20 4 c0 9 -6 15 -15 15 z M5 19 c3 -6 6 -9 11 -11"
    val dial = circle(12, 12, 8) + "M12 12 l4 -3"
    val up = "M12 19 V5 M5 12 l7 -7 l7 7"
    val pin = "M12 21 s-7 -6.2 -7 -11.5 a7 7 0 0 1 14 0 C19 14.8 12 21 12 21 z " + circle(12, 9.5, 2.5)
    val chevR = "M9 6 l6 6 l-6 6"
    val locate = circle(12, 12, 6) + "M12 2 v4 M12 18 v4 M2 12 h4 M18 12 h4"
    val down = "M3 7 l6 6 l4 -4 l8 8 M15 17 h6 v-6"
    val bulb = "M9 18 h6 M10 21 h4 M12 3 a6 6 0 0 0 -3.5 10.9 c0.6 0.5 1 1.2 1 2.1 h5 c0 -0.9 0.4 -1.6 1 -2.1 A6 6 0 0 0 12 3 z"
    val file = "M7 3 h7 l5 5 v11 a2 2 0 0 1 -2 2 H7 a2 2 0 0 1 -2 -2 V5 a2 2 0 0 1 2 -2 z M14 3 v5 h5 M9 13 h6 M9 17 h6"
    val dl = "M12 4 v11 M7 11 l5 5 l5 -5 M4 20 h16"
    val wallet = "M3 7 a2 2 0 0 1 2 -2 h12 v4 M3 7 v10 a2 2 0 0 0 2 2 h14 a1 1 0 0 0 1 -1 v-9 a1 1 0 0 0 -1 -1 H5 a2 2 0 0 1 -2 -2 M16 14 h2"
    val swap = "M7 4 v16 M3 8 l4 -4 l4 4 M17 20 V4 M13 16 l4 4 l4 -4"
    val cup = "M5 8 h11 v6 a4 4 0 0 1 -4 4 H9 a4 4 0 0 1 -4 -4 z M16 9 h2 a2 2 0 0 1 0 5 h-2 M8 3 v2 M12 3 v2"
    val bowl = "M3 12 h18 a9 9 0 0 1 -18 0 z M8 8 c0 -2 4 -2 4 -4 M14 8 c0 -2 4 -2 4 -4"
    val bus = "M5 5 h14 a1 1 0 0 1 1 1 v9 a1 1 0 0 1 -1 1 H5 a1 1 0 0 1 -1 -1 V6 a1 1 0 0 1 1 -1 z M4 11 h16 M7 18 v2 M17 18 v2 M8 14 h0.01 M16 14 h0.01"
    val dots = "M6 12 h0.01 M12 12 h0.01 M18 12 h0.01"
    val cart = "M3 4 h2 l2 11 h11 l2 -8 H6 M9 20 h0.01 M18 20 h0.01"
    val cash = "M3 7 h18 v10 H3 z " + circle(12, 12, 3)
    val tag = "M3 12 V4 h8 l10 10 l-8 8 z M7.5 8 h0.01"
    val dn = "M12 5 v14 M19 12 l-7 7 l-7 -7"
    val mail = "M6 5 h12 a3 3 0 0 1 3 3 v8 a3 3 0 0 1 -3 3 H6 a3 3 0 0 1 -3 -3 V8 a3 3 0 0 1 3 -3 z M3 8 l9 6 l9 -6"
    val user = circle(12, 8, 4) + "M4 21 a8 8 0 0 1 16 0"
    val key = circle(8, 15, 4) + "M11 12 l9 -9 M16 7 l3 3"
    val check = "M5 12 l5 5 l9 -10"
    val x = "M6 6 l12 12 M18 6 L6 18"
    val folder = "M3 7 a2 2 0 0 1 2 -2 h4 l2 2 h8 a2 2 0 0 1 2 2 v8 a2 2 0 0 1 -2 2 H5 a2 2 0 0 1 -2 -2 z"
    val plus = "M12 5 v14 M5 12 h14"
    val chev = "M6 9 l6 6 l6 -6"
    val sort = "M4 6 h16 M7 12 h10 M10 18 h4"
    val out = "M9 4 H5 a2 2 0 0 0 -2 2 v12 a2 2 0 0 0 2 2 h4 M16 8 l5 4 l-5 4 M21 12 H9"
    val warn = "M12 3 l10 18 H2 z M12 10 v5 M12 18 h0.01"
    val more = "M12 5 h0.01 M12 12 h0.01 M12 19 h0.01"
    val bars = "M5 20 V11 M12 20 V4 M19 20 v-6"
}

private val iconCache = HashMap<String, ImageVector>()
private fun iconVector(path: String, sw: Float): ImageVector = iconCache.getOrPut("$sw|$path") {
    ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .addPath(pathData = addPathNodes(path), stroke = SolidColor(Color.Black), strokeLineWidth = sw, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
        .build()
}

@Composable
fun Ico(path: String, modifier: Modifier = Modifier, size: Dp = 22.dp, tint: Color = LocalContentColor.current, sw: Float = 2.2f) {
    Icon(iconVector(path, sw), null, modifier.size(size), tint)
}
