package com.example.xrpad

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt

@Composable
fun VirtualKeyboard(
    modifier: Modifier = Modifier,
    pointerX: Float,
    pointerY: Float,
    tracking: Boolean,
    clickPulse: Long,
    pad: Float,
    ipd: Float,
    onText: (String) -> Unit,
    onKeyTap: (String) -> Unit,
    onClose: () -> Unit
) {
    val boundsMap = remember { mutableStateMapOf<String, Rect>() }
    var lastConsumedPulse by remember { mutableStateOf(0L) }

    var vkWindowRect by remember { mutableStateOf<Rect?>(null) }

    val density = LocalDensity.current
    val hitSlopPx = with(density) { 22.dp.toPx() }

    fun hit(rect: Rect, p: Offset): Boolean {
        return p.x >= rect.left - hitSlopPx &&
                p.x <= rect.right + hitSlopPx &&
                p.y >= rect.top - hitSlopPx &&
                p.y <= rect.bottom + hitSlopPx
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { vkWindowRect = it.boundsInWindow() }
    ) {
        val rootW = with(density) { maxWidth.toPx() }
        val rootH = with(density) { maxHeight.toPx() }

        val win = vkWindowRect

        // ✅ 중요: hoveredKey는 remember/derivedState로 "캡처"하면 고정될 수 있음
        // -> 매 리컴포지션마다 계산
        val hoveredKey: String? = run {
            if (!tracking) return@run null
            val wRect = win ?: return@run null

            val baseLeft = wRect.left
            val baseTop = wRect.top

            val padPx = pad.coerceIn(0f, 0.2f) * rootW
            val availPx = (rootW - 2f * padPx).coerceAtLeast(1f)
            val eyePx = availPx / 2f
            val ipdShiftPx = ipd * rootW

            val px = pointerX.coerceIn(0f, 1f)
            val py = pointerY.coerceIn(0f, 1f)

            val leftStart = padPx
            val rightStart = padPx + eyePx

            val leftXLocal = (leftStart + px * eyePx + ipdShiftPx)
                .coerceIn(leftStart, leftStart + eyePx)
            val rightXLocal = (rightStart + px * eyePx - ipdShiftPx)
                .coerceIn(rightStart, rightStart + eyePx)

            val leftPoint = Offset(baseLeft + leftXLocal, baseTop + py * rootH)
            val rightPoint = Offset(baseLeft + rightXLocal, baseTop + py * rootH)

            boundsMap.entries.firstOrNull { it.key.startsWith("L:") && hit(it.value, leftPoint) }?.key
                ?: boundsMap.entries.firstOrNull { it.key.startsWith("R:") && hit(it.value, rightPoint) }?.key
        }

        LaunchedEffect(clickPulse) {
            if (clickPulse == 0L) return@LaunchedEffect
            if (clickPulse == lastConsumedPulse) return@LaunchedEffect
            lastConsumedPulse = clickPulse

            val key = hoveredKey ?: return@LaunchedEffect
            performKey(key, onText, onKeyTap, onClose)
        }

        val padDp = (maxWidth * pad).coerceAtLeast(0.dp)
        val availW = (maxWidth - padDp * 2).coerceAtLeast(1.dp)
        val eyeW = availW / 2
        val ipdShiftInt = (with(density) { (maxWidth * ipd).toPx() }).roundToInt()

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = padDp)
                .padding(bottom = 12.dp)
                .heightIn(max = 260.dp)
        ) {
            KeyboardEyePane(
                eyeTag = "L",
                hoveredKey = hoveredKey,
                boundsMap = boundsMap,
                onKey = { performKey(it, onText, onKeyTap, onClose) },
                modifier = Modifier
                    .width(eyeW)
                    .fillMaxHeight()
                    .offset { IntOffset(+ipdShiftInt, 0) }
            )
            KeyboardEyePane(
                eyeTag = "R",
                hoveredKey = hoveredKey,
                boundsMap = boundsMap,
                onKey = { performKey(it, onText, onKeyTap, onClose) },
                modifier = Modifier
                    .width(eyeW)
                    .fillMaxHeight()
                    .offset { IntOffset(-ipdShiftInt, 0) }
            )
        }
    }
}

@Composable
private fun KeyboardEyePane(
    eyeTag: String,
    hoveredKey: String?,
    boundsMap: MutableMap<String, Rect>,
    onKey: (String) -> Unit,
    modifier: Modifier
) {
    val bg = Color(0x55000000)
    Column(
        modifier = modifier
            .background(bg, RoundedCornerShape(18.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        KeyRow(eyeTag, "QWERTYUIOP".map { it.toString() }, hoveredKey, boundsMap, onKey)
        KeyRow(eyeTag, "ASDFGHJKL".map { it.toString() }, hoveredKey, boundsMap, onKey)
        KeyRow(eyeTag, "ZXCVBNM".map { it.toString() }, hoveredKey, boundsMap, onKey)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            KeyButton("$eyeTag:CLOSE", "CLOSE", hoveredKey, boundsMap, onKey, Modifier.weight(1.1f))
            KeyButton("$eyeTag:SPACE", "SPACE", hoveredKey, boundsMap, onKey, Modifier.weight(2.2f))
            KeyButton("$eyeTag:BS", "BS", hoveredKey, boundsMap, onKey, Modifier.weight(1.0f))
            KeyButton("$eyeTag:ENTER", "ENTER", hoveredKey, boundsMap, onKey, Modifier.weight(1.3f))
        }
    }
}

@Composable
private fun KeyRow(
    eyeTag: String,
    keys: List<String>,
    hoveredKey: String?,
    boundsMap: MutableMap<String, Rect>,
    onKey: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        keys.forEach { k ->
            KeyButton("$eyeTag:$k", k, hoveredKey, boundsMap, onKey, Modifier.weight(1f))
        }
    }
}

@Composable
private fun KeyButton(
    id: String,
    label: String,
    hoveredKey: String?,
    boundsMap: MutableMap<String, Rect>,
    onKey: (String) -> Unit,
    modifier: Modifier
) {
    val isHover = (hoveredKey == id)
    val bg = if (isHover) Color(0xAAFFFFFF) else Color(0x33FFFFFF)
    val fg = if (isHover) Color.Black else Color.White

    Box(
        modifier = modifier
            .height(44.dp)
            .background(bg, RoundedCornerShape(14.dp))
            .onGloballyPositioned { coords -> boundsMap[id] = coords.boundsInWindow() }
            .clickable { onKey(id) },
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = label,
            style = TextStyle(color = fg, fontWeight = FontWeight.Bold)
        )
    }
}

private fun performKey(
    id: String,
    onText: (String) -> Unit,
    onKeyTap: (String) -> Unit,
    onClose: () -> Unit
) {
    val label = id.substringAfter(":")
    when (label) {
        "CLOSE" -> onClose()
        "SPACE" -> onKeyTap("SPACE")
        "BS" -> onKeyTap("BACKSPACE")
        "ENTER" -> onKeyTap("ENTER")
        else -> onText(label.lowercase())
    }
}
