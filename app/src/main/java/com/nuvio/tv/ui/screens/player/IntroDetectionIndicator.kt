package com.nuvio.tv.ui.screens.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.tv.material3.Text
import com.nuvio.tv.R

/** Non-focusable, absolute top-right in both Hebrew and English layouts. */
@Composable
internal fun IntroDetectionIndicator(visible: Boolean) {
    if (!visible) return
    val inset = with(LocalDensity.current) { 24.dp.roundToPx() }
    val transition = rememberInfiniteTransition(label = "intro_detection")
    val angle by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart),
        label = "intro_detection_ring"
    )
    Popup(
        alignment = AbsoluteAlignment.TopRight,
        offset = IntOffset(-inset, inset),
        properties = PopupProperties(focusable = false)
    ) {
        Row(
            modifier = Modifier.background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(32.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Canvas(Modifier.size(28.dp)) {
                val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                drawCircle(Color.White.copy(alpha = 0.2f), radius = size.minDimension / 2 - stroke.width / 2, style = stroke)
                drawArc(
                    Color.White, angle, 250f, false,
                    topLeft = Offset(stroke.width / 2, stroke.width / 2),
                    size = Size(size.width - stroke.width, size.height - stroke.width),
                    style = stroke
                )
            }
            Text(stringResource(R.string.yp_intro_searching), color = Color.White)
        }
    }
}
