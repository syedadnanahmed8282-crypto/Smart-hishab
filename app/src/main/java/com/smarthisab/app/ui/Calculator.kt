package com.smarthisab.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.smarthisab.app.domain.CalcEngine
import com.smarthisab.app.domain.Fmt
import com.smarthisab.app.domain.Mode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class CalcView { CLOSED, FULL, MINI, DOCKED }

/** ক্যালকুলেটর থেকে এন্ট্রিতে পাঠানো মান। target: "qty" (মাল), "exp" (খরচ), "inc" (আয়) */
data class CalcPrefill(val target: String, val value: Double)

/** ক্যালকুলেটরের অবস্থা — AppVm-এ থাকে, তাই ট্যাব বদলালেও লেখা, ইতিহাস ও জায়গা থেকে যায় */
@Stable
class CalcState {
    var view by mutableStateOf(CalcView.CLOSED)
    var restore by mutableStateOf(CalcView.FULL)
    var x by mutableFloatStateOf(Float.NaN)       // dp; NaN = ডিফল্ট জায়গা
    var y by mutableFloatStateOf(Float.NaN)
    var dockLeft by mutableStateOf(false)
    var dockY by mutableFloatStateOf(200f)
    var expr by mutableStateOf("")
    val history = mutableStateListOf<Pair<String, String>>()   // (হিসাব, ফল ASCII)
    private var fresh = false

    fun open() { view = when (view) { CalcView.CLOSED -> CalcView.FULL; CalcView.DOCKED -> restore; else -> CalcView.FULL } }

    fun value(): Double? = CalcEngine.eval(expr)
    val live: String get() = value()?.let { CalcEngine.format(it) } ?: if (expr.none { it.isDigit() }) "০" else "ত্রুটি"

    private fun currentNumber() = expr.takeLastWhile { it.isDigit() || it == '.' }

    fun digit(d: String) {
        if (fresh) { expr = ""; fresh = false }
        if (expr.length >= 40 || expr.endsWith("%")) return
        val cur = currentNumber()
        if (d == "00") { if (cur.isEmpty()) expr += "0" else if (cur != "0") expr += "00"; return }
        if (cur == "0") expr = expr.dropLast(1)
        expr += d
    }

    fun dot() {
        if (fresh) { expr = ""; fresh = false }
        if (expr.endsWith("%") || expr.length >= 40) return
        val cur = currentNumber()
        if ('.' in cur) return
        expr += if (cur.isEmpty()) "0." else "."
    }

    fun op(o: Char) {
        fresh = false
        if (expr.isEmpty()) { if (o == '−') expr = "−"; return }
        if (expr == "−") return
        val last = expr.last()
        expr = if (last in CalcEngine.OPS || last == '.') expr.dropLast(1) + o else expr + o
    }

    fun percent() { fresh = false; if (expr.isNotEmpty() && expr.last().isDigit()) expr += "%" }
    fun back() { fresh = false; if (expr.isNotEmpty()) expr = expr.dropLast(1) }
    fun clear() { expr = ""; fresh = false }

    fun evaluate() {
        val v = value() ?: return
        if (expr.drop(1).any { it in CalcEngine.OPS } || expr.endsWith("%")) {
            history.add(0, CalcEngine.norm(expr) to CalcEngine.plain(v))
            while (history.size > 6) history.removeAt(history.lastIndex)
        }
        expr = CalcEngine.plain(v)
        fresh = true
    }

    fun reuse(plain: String) { expr = plain; fresh = true }
}

private enum class KeyKind(val top: Color, val bottom: Color, val edge: Color, val text: Color) {
    N(Color.White, Color(0xFFE3F1FB), Color(0xFFB0D1E9), Color(0xFF082F49)),
    O(Color(0xFFCFEAFD), Color(0xFF9CD2F6), Color(0xFF5AA6D6), Color(0xFF075985)),
    F(Color(0xFFFFEBEE), Color(0xFFFFC8D0), Color(0xFFE48A97), Color(0xFFBE123C)),
    E(Color(0xFF1A9BDC), Color(0xFF075985), Color(0xFF04406A), Color.White),
}

private class K(val label: String, val kind: KeyKind, val act: (CalcState) -> Unit)

private val KEYS: List<List<K>> = listOf(
    listOf(K("C", KeyKind.F) { it.clear() }, K("⌫", KeyKind.F) { it.back() }, K("%", KeyKind.O) { it.percent() }, K("÷", KeyKind.O) { it.op('÷') }),
    listOf(K("৭", KeyKind.N) { it.digit("7") }, K("৮", KeyKind.N) { it.digit("8") }, K("৯", KeyKind.N) { it.digit("9") }, K("×", KeyKind.O) { it.op('×') }),
    listOf(K("৪", KeyKind.N) { it.digit("4") }, K("৫", KeyKind.N) { it.digit("5") }, K("৬", KeyKind.N) { it.digit("6") }, K("−", KeyKind.O) { it.op('−') }),
    listOf(K("১", KeyKind.N) { it.digit("1") }, K("২", KeyKind.N) { it.digit("2") }, K("৩", KeyKind.N) { it.digit("3") }, K("+", KeyKind.O) { it.op('+') }),
    listOf(K("০০", KeyKind.N) { it.digit("00") }, K("০", KeyKind.N) { it.digit("0") }, K(".", KeyKind.N) { it.dot() }, K("=", KeyKind.E) { it.evaluate() }),
)

/** ৩ডি কি: নিচে মোটা ধার, চাপলে ৫ডিপি নামে, ছায়া ছোট হয়, ভেতরে অন্ধকার, ঢেউ ছড়ায়, হালকা কম্পন */
@Composable
private fun Key3D(label: String, kind: KeyKind, modifier: Modifier, onTap: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val depth by animateDpAsState(if (pressed) 5.dp else 0.dp, spring(dampingRatio = .5f, stiffness = 1200f), label = "kd")
    val ring = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier.height(60.dp).pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    pressed = true
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    scope.launch { ring.snapTo(0f); ring.animateTo(1f, tween(420)) }
                    tryAwaitRelease()
                    pressed = false
                },
                onTap = { onTap() },
            )
        },
    ) {
        Box(Modifier.fillMaxWidth().height(54.dp).offset(y = 6.dp).shadow(if (pressed) 2.dp else 10.dp, shape, ambientColor = kind.edge, spotColor = kind.edge).background(kind.edge, shape))
        Box(
            Modifier.fillMaxWidth().height(54.dp).offset(y = depth).clip(shape).background(Brush.verticalGradient(listOf(kind.top, kind.bottom))),
            contentAlignment = Alignment.Center,
        ) {
            if (kind != KeyKind.N) Box(Modifier.align(Alignment.TopCenter).fillMaxWidth(.8f).padding(top = 3.dp).height(20.dp).background(Brush.verticalGradient(listOf(Color.White.copy(alpha = .5f), Color.Transparent)), RoundedCornerShape(14.dp)))
            Text(label, color = kind.text, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
            if (depth > 1.dp) Box(Modifier.fillMaxSize().background(Color(0x22082F49)))
        }
        Box(Modifier.fillMaxWidth().height(54.dp).graphicsLayer { val r = ring.value; scaleX = 1f + .25f * r; scaleY = 1f + .35f * r; alpha = (1f - r) * .8f }.border(2.dp, Sky500, shape))
    }
}

@Composable
private fun SmallBtn(icon: String, tint: Color, bg: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier.size(34.dp).shadow(3.dp, shape, ambientColor = Sky300, spotColor = Sky300).clip(shape).background(bg).bounceClick(true, onClick),
        contentAlignment = Alignment.Center,
    ) { Ico(icon, size = 17.dp, tint = tint, sw = 2.4f) }
}

private fun sendOptions(mode: Mode): List<Triple<String, String, String>> = when (mode) {
    Mode.MAIN -> listOf(Triple("qty", "মালে বসান", Ic.box), Triple("exp", "খরচে বসান", Ic.dn))
    Mode.ALT -> listOf(Triple("exp", "খরচে বসান", Ic.dn))
    Mode.PREMIUM -> listOf(Triple("inc", "আয়ে বসান", Ic.up), Triple("exp", "খরচে বসান", Ic.dn))
}

/** নীল ডিসপ্লে: ওপরে হিসাবের লাইন, বড় ফল (সংখ্যা গড়িয়ে ওঠে)। ফল ধরে রাখলে এন্ট্রিতে পাঠানোর বাটন আসে। */
@Composable
private fun CalcDisplay(st: CalcState, mode: Mode, onSend: (String) -> Unit) {
    var sendOpen by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(sendOpen) { if (sendOpen) { delay(5000); sendOpen = false } }
    val glow by animateFloatAsState(if (sendOpen) 1f else 0f, tween(300), label = "glow")
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier.fillMaxWidth().shadow(10.dp, shape, ambientColor = Sky800, spotColor = Sky800).clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF0C6BA3), Sky700, Sky800)))
            .border((3 * glow).dp, Sky300.copy(alpha = glow), shape)
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); sendOpen = true }, onTap = { sendOpen = false })
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(Modifier.align(Alignment.TopStart).clip(CircleShape).background(Color.White.copy(alpha = .16f)).padding(horizontal = 9.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Ico(modeIcon(mode), size = 13.dp, tint = Sky100)
            Text(" ${mode.label}", fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, color = Sky100, maxLines = 1, softWrap = false)
        }
        Column(Modifier.fillMaxWidth().padding(top = 22.dp), horizontalAlignment = Alignment.End) {
            Text(Fmt.bn(st.expr.takeLast(26)).ifEmpty { " " }, fontSize = 16.sp, lineHeight = 22.sp, color = Sky200, maxLines = 1, overflow = TextOverflow.Clip)
            AnimatedContent(
                targetState = st.live, label = "res",
                transitionSpec = { (slideInVertically { it / 2 } + fadeIn(tween(160))).togetherWith(slideOutVertically { -it / 2 } + fadeOut(tween(120))) },
            ) { txt -> FitText(txt, Modifier.fillMaxWidth(), maxSize = 46.sp, minSize = 22.sp, color = Color.White, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.End, lineHeight = 54.sp) }
            AnimatedContent(targetState = sendOpen, label = "send") { open ->
                if (!open) Row(verticalAlignment = Alignment.CenterVertically) {
                    Ico(Ic.hand, size = 14.dp, tint = Sky200)
                    Text(" ধরে রাখুন → এন্ট্রিতে পাঠান", fontSize = 12.sp, lineHeight = 16.sp, color = Sky200, maxLines = 1)
                } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    sendOptions(mode).forEach { (target, label, icon) ->
                        val bad = target == "exp"
                        Row(
                            Modifier.shadow(4.dp, CircleShape).clip(CircleShape).background(if (bad) Color(0xFFFFE4E6) else Sky100).bounceClick(true) { sendOpen = false; onSend(target) }.padding(horizontal = 13.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Ico(icon, size = 16.dp, tint = if (bad) Rose else Sky800, sw = 2.4f)
                            Text(" $label", fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.ExtraBold, color = if (bad) Rose else Sky800, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FullPanel(
    st: CalcState, mode: Mode, w: Float, x: Float, y: Float, dragging: Boolean,
    onDragStart: () -> Unit, onDrag: (Float, Float) -> Unit, onDragEnd: () -> Unit,
    onMini: () -> Unit, onDock: () -> Unit, onClose: () -> Unit, onSend: (String) -> Unit,
) {
    val lift by animateFloatAsState(if (dragging) 1f else 0f, spring(dampingRatio = .6f), label = "lift")
    val shape = RoundedCornerShape(32.dp)
    Column(
        Modifier.offset { IntOffset(x.dp.roundToPx(), y.dp.roundToPx()) }.width(w.dp)
            .graphicsLayer { scaleX = 1f + .02f * lift; scaleY = 1f + .02f * lift; rotationZ = -1.5f * lift }
            .shadow((22 + 14 * lift).dp, shape, ambientColor = Sky800, spotColor = Sky800)
            .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFEAF5FD))), shape).border(1.dp, Sky100, shape)
            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 10.dp).pointerInput(w) {
                detectDragGestures(onDragStart = { onDragStart() }, onDragEnd = { onDragEnd() }, onDragCancel = { onDragEnd() }) { change, amount ->
                    change.consume(); onDrag(amount.x.toDp().value, amount.y.toDp().value)
                }
            },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.width(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Box(Modifier.width(26.dp).height(4.dp).clip(CircleShape).background(Color(0xFFC9D9E6)))
                Box(Modifier.width(18.dp).height(4.dp).clip(CircleShape).background(Color(0xFFDCE8F2)))
            }
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Ico(Ic.calc, size = 19.dp, tint = Sky700)
                Text(" ক্যালকুলেটর", fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.ExtraBold, color = Navy, maxLines = 1, softWrap = false)
            }
            SmallBtn(Ic.minus, Slate, Color(0xFFEEF5FB), onMini)
            SmallBtn(Ic.dock, Slate, Color(0xFFEEF5FB), onDock)
            SmallBtn(Ic.x, Rose, Color(0xFFFFE4E6), onClose)
        }
        if (st.history.isNotEmpty()) Row(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            st.history.take(2).forEach { (e, p) ->
                Text(
                    "${p.toDoubleOrNull()?.let { CalcEngine.format(it) } ?: p} ← ${Fmt.bn(e.take(12))}", Modifier.clip(CircleShape).background(Sky100).bounceClick(true) { st.reuse(p) }.padding(horizontal = 11.dp, vertical = 3.dp),
                    fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, color = Sky800, maxLines = 1, softWrap = false,
                )
            }
        }
        CalcDisplay(st, mode, onSend)
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            KEYS.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { row.forEach { k -> Key3D(k.label, k.kind, Modifier.weight(1f)) { k.act(st) } } }
            }
        }
    }
}

@Composable
private fun MiniCard(
    st: CalcState, mode: Mode, x: Float, y: Float, dragging: Boolean,
    onDragStart: () -> Unit, onDrag: (Float, Float) -> Unit, onDragEnd: () -> Unit, onExpand: () -> Unit, onClose: () -> Unit, onSend: (String) -> Unit,
) {
    val t = rememberInfiniteTransition(label = "mini")
    val bob by t.animateFloat(0f, -4f, infiniteRepeatable(tween(1500), RepeatMode.Reverse), label = "bob")
    val lift by animateFloatAsState(if (dragging) 1f else 0f, spring(dampingRatio = .6f), label = "lift")
    var menu by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(24.dp)
    Box(Modifier.offset { IntOffset(x.dp.roundToPx(), (y + (1f - lift) * bob).dp.roundToPx()) }.graphicsLayer { rotationZ = -4f * lift; scaleX = 1f + .04f * lift; scaleY = 1f + .04f * lift }) {
        Row(
            Modifier.size(196.dp, 68.dp).shadow((16 + 14 * lift).dp, shape, ambientColor = Sky800, spotColor = Sky800).clip(shape)
                .background(Brush.linearGradient(listOf(Color(0xFF0C6BA3), Sky800)))
                .pointerInput(Unit) { detectTapGestures(onTap = { onExpand() }, onLongPress = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); menu = true }) }
                .pointerInput(Unit) {
                    detectDragGestures(onDragStart = { onDragStart() }, onDragEnd = { onDragEnd() }, onDragCancel = { onDragEnd() }) { change, amount ->
                        change.consume(); onDrag(amount.x.toDp().value, amount.y.toDp().value)
                    }
                }
                .padding(start = 9.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(42.dp).shadow(6.dp, RoundedCornerShape(15.dp)).clip(RoundedCornerShape(15.dp)).background(Brush.linearGradient(listOf(Color(0xFF34D399), Color(0xFF047857)))), contentAlignment = Alignment.Center) {
                Ico(Ic.calc, size = 22.dp, tint = Color.White, sw = 2.2f)
            }
            Column(Modifier.weight(1f)) {
                Text(Fmt.bn(st.expr.takeLast(14)).ifEmpty { "ক্যালকুলেটর" }, fontSize = 12.sp, lineHeight = 16.sp, color = Sky200, maxLines = 1, softWrap = false)
                FitText(st.live, Modifier.fillMaxWidth(), maxSize = 24.sp, minSize = 14.sp, color = Color.White, fontWeight = FontWeight.ExtraBold, lineHeight = 30.sp)
            }
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = .18f)), contentAlignment = Alignment.Center) { Ico(Ic.expand, size = 17.dp, tint = Color.White, sw = 2.4f) }
        }
        Box(
            Modifier.align(Alignment.TopEnd).offset(6.dp, (-6).dp).size(22.dp).shadow(4.dp, CircleShape).clip(CircleShape).background(Color.White).bounceClick(true, onClose),
            contentAlignment = Alignment.Center,
        ) { Ico(Ic.x, size = 12.dp, tint = Slate, sw = 3f) }
        DropdownMenu(menu, { menu = false }) {
            sendOptions(mode).forEach { (target, label, _) -> DropdownMenuItem({ Text(label, fontWeight = FontWeight.Bold) }, { menu = false; onSend(target) }) }
        }
    }
}

@Composable
private fun DockTab(st: CalcState, topInset: Float, bottomLimit: Float, onRestore: () -> Unit) {
    val t = rememberInfiniteTransition(label = "dock")
    val nudge by t.animateFloat(0f, 4f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "n")
    val glow by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "g")
    val left = st.dockLeft
    val shape = if (left) RoundedCornerShape(topEnd = 22.dp, bottomEnd = 22.dp) else RoundedCornerShape(topStart = 22.dp, bottomStart = 22.dp)
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.align(if (left) Alignment.TopStart else Alignment.TopEnd).offset { IntOffset(0, st.dockY.dp.roundToPx()) }.size(36.dp, 82.dp)
                .shadow((10 + 8 * glow).dp, shape, ambientColor = Sky800, spotColor = Sky800).clip(shape)
                .background(Brush.verticalGradient(listOf(Color(0xFF1A9BDC), Sky800)))
                .pointerInput(Unit) { detectTapGestures { onRestore() } }
                .pointerInput(Unit) { detectVerticalDragGestures { change, dy -> change.consume(); st.dockY = (st.dockY + dy.toDp().value).coerceIn(topInset, bottomLimit - 84f) } },
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        ) {
            Ico(Ic.calc, size = 20.dp, tint = Color.White, sw = 2.2f)
            Ico(if (left) Ic.fwd else Ic.back, Modifier.offset(x = (if (left) nudge else -nudge).dp), size = 16.dp, tint = Sky200, sw = 3f)
        }
    }
}

/** অ্যাপের সবচেয়ে ওপরের স্তরে ভাসে: পূর্ণ আকার ↔ ছোট পপআপ ↔ কিনারায় লুকানো ট্যাব। যেকোনো জায়গায় টেনে সরানো যায়। */
@Composable
fun CalculatorHost(vm: AppVm, nav: NavHostController) {
    val st = vm.calc
    val mode by vm.mode.collectAsState()
    val viewer by vm.viewer.collectAsState()
    val folder by vm.current.collectAsState()
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding().value + 8f
    var dragging by remember { mutableStateOf(false) }
    if (st.view == CalcView.CLOSED) return
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sw = maxWidth.value
        val sh = maxHeight.value
        val fullW = minOf(356f, sw - 32f)
        val fullH = 566f
        val miniW = 196f
        val miniH = 68f
        val bottomLimit = sh - 92f
        fun px(w: Float) = if (st.x.isNaN()) (sw - w) / 2f else st.x
        fun defY(w: Float, h: Float) = if (w > 250f) maxOf(topInset, (sh - h) / 2f - 20f) else sh - h - 112f
        fun py(w: Float, h: Float) = (if (st.y.isNaN()) defY(w, h) else st.y).coerceIn(topInset, maxOf(topInset, bottomLimit - h))
        fun drag(dx: Float, dy: Float, w: Float, h: Float) {
            if (st.x.isNaN() || st.y.isNaN()) { st.x = px(w); st.y = py(w, h) }
            st.x += dx; st.y += dy
        }
        fun dock(left: Boolean, y: Float) {
            if (st.view != CalcView.DOCKED) st.restore = st.view
            st.dockLeft = left
            st.dockY = (y + 20f).coerceIn(topInset, bottomLimit - 84f)
            st.view = CalcView.DOCKED
        }
        fun endDrag(w: Float, h: Float) {
            dragging = false
            val rawX = px(w)
            val y = py(w, h)
            val thr = if (w > 250f) -40f else 6f
            when {
                rawX < thr -> { st.x = 12f; st.y = y; dock(true, y) }
                rawX + w > sw - thr -> { st.x = sw - w - 12f; st.y = y; dock(false, y) }
                else -> { st.x = rawX.coerceIn(8f, maxOf(8f, sw - w - 8f)); st.y = y }
            }
        }
        fun send(target: String) {
            val v = st.value() ?: 0.0
            val f = folder
            when {
                v <= 0.0 -> vm.say("আগে একটা ফল বের করুন")
                viewer -> vm.say("ভিউয়ার মোডে এন্ট্রি বন্ধ")
                f == null -> vm.say("আগে একটা ফোল্ডার বানান")
                f.closed -> vm.say("ফোল্ডার সমাপ্ত, আগে আবার চালু করুন")
                else -> { vm.prefill = CalcPrefill(target, v); st.view = CalcView.MINI; nav.navigate("entry") }
            }
        }
        AnimatedContent(
            targetState = st.view, modifier = Modifier.fillMaxSize(), label = "calcView",
            transitionSpec = { (fadeIn(tween(160)) + scaleIn(spring(dampingRatio = .6f, stiffness = 380f), initialScale = .7f, transformOrigin = TransformOrigin(.85f, 1f))).togetherWith(fadeOut(tween(120)) + scaleOut(targetScale = .85f, transformOrigin = TransformOrigin(.85f, 1f))) },
        ) { v ->
            when (v) {
                CalcView.FULL -> Box(Modifier.fillMaxSize()) {
                    FullPanel(
                        st, mode, fullW, px(fullW), py(fullW, fullH), dragging,
                        onDragStart = { dragging = true }, onDrag = { dx, dy -> drag(dx, dy, fullW, fullH) }, onDragEnd = { endDrag(fullW, fullH) },
                        onMini = { st.view = CalcView.MINI }, onDock = { dock(px(fullW) + fullW / 2f < sw / 2f, py(fullW, fullH)) }, onClose = { st.view = CalcView.CLOSED }, onSend = { send(it) },
                    )
                }
                CalcView.MINI -> Box(Modifier.fillMaxSize()) {
                    MiniCard(
                        st, mode, px(miniW), py(miniW, miniH), dragging,
                        onDragStart = { dragging = true }, onDrag = { dx, dy -> drag(dx, dy, miniW, miniH) }, onDragEnd = { endDrag(miniW, miniH) },
                        onExpand = { st.view = CalcView.FULL }, onClose = { st.view = CalcView.CLOSED }, onSend = { send(it) },
                    )
                }
                CalcView.DOCKED -> DockTab(st, topInset, bottomLimit) {
                    val w = if (st.restore == CalcView.FULL) fullW else miniW
                    val h = if (st.restore == CalcView.FULL) fullH else miniH
                    st.x = if (st.dockLeft) 12f else sw - w - 12f
                    st.y = (st.dockY - 20f).coerceIn(topInset, maxOf(topInset, bottomLimit - h))
                    st.view = st.restore
                }
                CalcView.CLOSED -> Unit
            }
        }
    }
}
