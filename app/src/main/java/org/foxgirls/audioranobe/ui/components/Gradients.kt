package org.foxgirls.audioranobe.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas

/** Like background(brush), but dithered: dark gradients otherwise band into visible stripes. */
fun Modifier.ditheredBackground(brush: Brush): Modifier = drawBehind {
    val paint = Paint()
    brush.applyTo(size, paint, 1f)
    paint.asFrameworkPaint().isDither = true
    drawIntoCanvas { it.drawRect(Rect(0f, 0f, size.width, size.height), paint) }
}
