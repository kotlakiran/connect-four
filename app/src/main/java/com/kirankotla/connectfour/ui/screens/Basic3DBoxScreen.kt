package com.kirankotla.connectfour.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

private val boxGradient = Brush.verticalGradient(
    listOf(
        Color(0xFF5EEAD4),
        Color(0xFF60A5FA),
        Color(0xFF7C3AED)
    )
)

data class Point3D(
    val x: Float,
    val y: Float,
    val z: Float
)

data class ScreenPoint(
    val x: Float,
    val y: Float
)

fun rotatePoint(point: Point3D, rotationX: Float, rotationY: Float): Point3D {
    val radX = Math.toRadians(rotationX.toDouble()).toFloat()
    val radY = Math.toRadians(rotationY.toDouble()).toFloat()
    val cosX = cos(radX)
    val sinX = sin(radX)
    val cosY = cos(radY)
    val sinY = sin(radY)

    val y1 = point.y * cosX - point.z * sinX
    val z1 = point.y * sinX + point.z * cosX

    val x2 = point.x * cosY - z1 * sinY
    val z2 = point.x * sinY + z1 * cosY

    return Point3D(x2, y1, z2)
}

fun projectPoint(
    point: Point3D,
    centerX: Float,
    centerY: Float,
    scale: Float,
    cameraDistance: Float
): ScreenPoint {
    val perspective = cameraDistance / (cameraDistance + point.z)
    return ScreenPoint(
        x = centerX + point.x * scale * perspective,
        y = centerY - point.y * scale * perspective
    )
}

private fun cubeVertices(size: Float): List<Point3D> = listOf(
    Point3D(-size, -size, size),
    Point3D(size, -size, size),
    Point3D(size, size, size),
    Point3D(-size, size, size),
    Point3D(-size, -size, -size),
    Point3D(size, -size, -size),
    Point3D(size, size, -size),
    Point3D(-size, size, -size)
)

private fun cubeFaces(): List<List<Int>> = listOf(
    listOf(0, 1, 2, 3),
    listOf(1, 5, 6, 2),
    listOf(5, 4, 7, 6),
    listOf(4, 0, 3, 7),
    listOf(3, 2, 6, 7),
    listOf(4, 5, 1, 0)
)

@Composable
fun BoxDemoScreen(onContinue: () -> Unit = {}) {
    var rotationX by remember { mutableFloatStateOf(25f) }
    var rotationY by remember { mutableFloatStateOf(-35f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B1020))
            .pointerInput(Unit) {
                detectDragGestures { _, dragAmount ->
                    rotationY += dragAmount.x * 0.4f
                    rotationX -= dragAmount.y * 0.4f
                    rotationX = rotationX.coerceIn(-80f, 80f)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width / 2f
            val centerY = size.height / 2f + 36f
            val cubeSize = 1.5f
            val scale = minOf(size.width, size.height) * 0.16f
            val cameraDistance = 8f

            val vertices = cubeVertices(cubeSize)
            val faces = cubeFaces().map { faceIndices ->
                faceIndices.map { index ->
                    val rotated = rotatePoint(vertices[index], rotationX, rotationY)
                    projectPoint(rotated, centerX, centerY, scale, cameraDistance)
                }
            }

            faces.forEachIndexed { index, facePoints ->
                val path = Path().apply {
                    moveTo(facePoints[0].x, facePoints[0].y)
                    facePoints.drop(1).forEach { point -> lineTo(point.x, point.y) }
                    close()
                }

                val color = when (index) {
                    0 -> Color(0xFF8CC7FF)
                    1 -> Color(0xFF7CE6C4)
                    2 -> Color(0xFFF7C778)
                    3 -> Color(0xFF9AA7FF)
                    4 -> Color(0xFFF5A3C8)
                    else -> Color(0xFFF7E39A)
                }

                drawPath(path = path, color = color.copy(alpha = 0.86f), style = Fill)
                drawPath(path = path, color = Color(0xFF18314B).copy(alpha = 0.9f), style = Stroke(width = 1.8f))

                val gridColor = Color(0xFF10233D).copy(alpha = 0.7f)
                for (division in 1 until 4) {
                    val fraction = division / 4f
                    val horizontalStart = interpolate(facePoints[0], facePoints[3], fraction)
                    val horizontalEnd = interpolate(facePoints[1], facePoints[2], fraction)
                    val verticalStart = interpolate(facePoints[0], facePoints[1], fraction)
                    val verticalEnd = interpolate(facePoints[3], facePoints[2], fraction)

                    drawLine(
                        color = gridColor,
                        start = Offset(horizontalStart.x, horizontalStart.y),
                        end = Offset(horizontalEnd.x, horizontalEnd.y),
                        strokeWidth = 1.2f
                    )
                    drawLine(
                        color = gridColor,
                        start = Offset(verticalStart.x, verticalStart.y),
                        end = Offset(verticalEnd.x, verticalEnd.y),
                        strokeWidth = 1.2f
                    )
                }
            }

            drawCircle(
                color = Color(0xFF8CC7FF).copy(alpha = 0.22f),
                radius = 12f,
                center = Offset(centerX, centerY)
            )
        }
    }
}

private fun interpolate(start: ScreenPoint, end: ScreenPoint, fraction: Float): ScreenPoint {
    return ScreenPoint(
        x = start.x + (end.x - start.x) * fraction,
        y = start.y + (end.y - start.y) * fraction
    )
}
