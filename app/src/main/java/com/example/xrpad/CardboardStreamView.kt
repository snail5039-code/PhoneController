package com.example.xrpad

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun CardboardStreamView(
    streamUrl: String,
    pointerX: Float,
    pointerY: Float,
    tracking: Boolean,
    pad: Float,
    ipd: Float,
    zoom: Float,
    reticleScale: Float,
    onPadChange: (Float) -> Unit,
    onIpdChange: (Float) -> Unit,
    onZoomChange: (Float) -> Unit,
    onReticleScaleChange: (Float) -> Unit,
    onToggleTuning: () -> Unit,
    tuningOpen: Boolean,
    onStreamSize: (Int, Int) -> Unit = { _, _ -> } // ✅ 추가
) {
    val density = LocalDensity.current
    val frameBytes by rememberMjpegFrames(streamUrl)

    // ✅ 원본 프레임 크기(비율 계산용)
    val (srcW, srcH) = remember(frameBytes) {
        jpegSize(frameBytes) ?: (0 to 0)
    }

    // ✅ XRPadApp으로 전달
    LaunchedEffect(srcW, srcH) {
        if (srcW > 0 && srcH > 0) onStreamSize(srcW, srcH)
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(Color.Black)
    ) {
        val padDp = (maxWidth * pad).coerceAtLeast(0.dp)
        val availW = (maxWidth - padDp * 2).coerceAtLeast(1.dp)
        val eyeW = availW / 2
        val ipdShiftPx = with(density) { (maxWidth * ipd).toPx() }

        // Stream
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = padDp)
        ) {
            EyePane(frameBytes, Modifier.width(eyeW).fillMaxHeight(), zoom, +ipdShiftPx)
            EyePane(frameBytes, Modifier.width(eyeW).fillMaxHeight(), zoom, -ipdShiftPx)
        }

        // Reticle: 영상 rect 안에서만
        Canvas(Modifier.fillMaxSize()) {
            if (!tracking) return@Canvas

            val w = size.width
            val h = size.height
            val padPx = pad * w
            val availPx = (w - 2f * padPx).coerceAtLeast(1f)
            val eyePx = availPx / 2f
            val px = pointerX.coerceIn(0f, 1f)
            val py = pointerY.coerceIn(0f, 1f)

            val rPx = with(density) { 4.dp.toPx() } * reticleScale
            val thick = with(density) { 3.dp.toPx() } * reticleScale
            val thin = with(density) { 2.dp.toPx() } * reticleScale
            val arm = with(density) { 10.dp.toPx() } * reticleScale

            fun drawReticle(eyeStartX: Float, shift: Float) {
                if (srcW <= 0 || srcH <= 0) {
                    val raw = eyeStartX + px * eyePx + shift
                    val cx = raw.coerceIn(eyeStartX, eyeStartX + eyePx)
                    val cy = py * h
                    drawCrosshair(cx, cy, rPx, thick, thin, arm)
                    return
                }

                val fit = min(eyePx / srcW.toFloat(), h / srcH.toFloat())
                val dispW = srcW * fit * zoom
                val dispH = srcH * fit * zoom

                val imgLeft = eyeStartX + (eyePx - dispW) * 0.5f + shift
                val imgTop = (h - dispH) * 0.5f
                val imgRight = imgLeft + dispW
                val imgBottom = imgTop + dispH

                val eyeLeft = eyeStartX
                val eyeRight = eyeStartX + eyePx
                val visLeft = max(eyeLeft, imgLeft)
                val visRight = min(eyeRight, imgRight)
                val visTop = max(0f, imgTop)
                val visBottom = min(h, imgBottom)

                val cx = (imgLeft + px * dispW).coerceIn(visLeft, visRight)
                val cy = (imgTop + py * dispH).coerceIn(visTop, visBottom)

                drawCrosshair(cx, cy, rPx, thick, thin, arm)
            }

            val leftStart = padPx
            val rightStart = padPx + eyePx
            drawReticle(leftStart, +ipdShiftPx)
            drawReticle(rightStart, -ipdShiftPx)
        }

        // TUNE bar
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp)
                .background(Color(0x66000000), RoundedCornerShape(14.dp))
                .clickable { onToggleTuning() }
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            BasicText(
                text = "TUNE  pad=${fmt(pad)}  ipd=${fmt(ipd)}  zoom=${fmt(zoom)}  ret=${fmt(reticleScale)}",
                style = androidx.compose.ui.text.TextStyle(color = Color.White)
            )
        }

        if (tuningOpen) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
                    .background(Color(0xAA000000), RoundedCornerShape(14.dp))
                    .padding(12.dp)
                    .widthIn(max = 380.dp)
            ) {
                Text("TUNING", color = Color.White)
                Spacer(Modifier.height(8.dp))
                TuningSlider("PAD (좌우 여백)", pad, onPadChange, 0.00f, 0.20f)
                TuningSlider("IPD (겹침 보정)", ipd, onIpdChange, -0.10f, 0.10f)
                TuningSlider("ZOOM (확대/축소)", zoom, onZoomChange, 0.80f, 1.40f)
                TuningSlider("RETICLE (커서 크기)", reticleScale, onReticleScaleChange, 0.80f, 3.00f)
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCrosshair(
    cx: Float, cy: Float, rPx: Float, thick: Float, thin: Float, arm: Float
) {
    val c = Offset(cx, cy)
    drawCircle(Color.Black, radius = rPx + thick, center = c)
    drawLine(Color.Black, Offset(cx - arm, cy), Offset(cx + arm, cy), strokeWidth = thick)
    drawLine(Color.Black, Offset(cx, cy - arm), Offset(cx, cy + arm), strokeWidth = thick)

    drawCircle(Color.White, radius = rPx, center = c)
    drawLine(Color.White, Offset(cx - arm, cy), Offset(cx + arm, cy), strokeWidth = thin)
    drawLine(Color.White, Offset(cx, cy - arm), Offset(cx, cy + arm), strokeWidth = thin)
}

@Composable
private fun EyePane(frameBytes: ByteArray?, modifier: Modifier, zoom: Float, shiftPx: Float) {
    if (frameBytes == null) { Box(modifier.background(Color.Black)); return }
    val bmp = remember(frameBytes) { BitmapFactory.decodeByteArray(frameBytes, 0, frameBytes.size) }
    if (bmp == null) { Box(modifier.background(Color.Black)); return }

    val shift = shiftPx.roundToInt()
    Image(
        bitmap = bmp.asImageBitmap(),
        contentDescription = null,
        modifier = modifier
            .offset { IntOffset(shift, 0) }
            .graphicsLayer {
                scaleX = zoom
                scaleY = zoom
                transformOrigin = TransformOrigin(0.5f, 0.5f)
            },
        contentScale = ContentScale.Fit
    )
}

@Composable
private fun TuningSlider(title: String, value: Float, onValueChange: (Float) -> Unit, min: Float, max: Float) {
    Text("$title : ${fmt(value)}", color = Color.White)
    Slider(
        value = value.coerceIn(min, max),
        onValueChange = { onValueChange(it.coerceIn(min, max)) },
        valueRange = min..max
    )
    Spacer(Modifier.height(6.dp))
}

private fun fmt(v: Float): String = ((v * 100).roundToInt() / 100.0).toString()

private fun jpegSize(bytes: ByteArray?): Pair<Int, Int>? {
    if (bytes == null || bytes.isEmpty()) return null
    return try {
        val opt = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opt)
        if (opt.outWidth > 0 && opt.outHeight > 0) opt.outWidth to opt.outHeight else null
    } catch (_: Throwable) { null }
}
