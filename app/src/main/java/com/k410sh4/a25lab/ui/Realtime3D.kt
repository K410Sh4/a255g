package com.k410sh4.a25lab.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.k410sh4.a25lab.util.Quaternion
import com.k410sh4.a25lab.util.QuaternionMath
import com.k410sh4.a25lab.util.ThreeDMath
import com.k410sh4.a25lab.util.Vec3

@Composable
fun PhonePose3D(
    current: Quaternion,
    reference: Quaternion,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val surface = MaterialTheme.colorScheme.surfaceVariant
    val outline = MaterialTheme.colorScheme.outlineVariant
    val onSurface = MaterialTheme.colorScheme.onSurface

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp)
            .background(surface, RoundedCornerShape(28.dp)),
    ) {
        Canvas(Modifier.matchParentSize()) {
            val rotation = QuaternionMath.relative(reference, current)
            val halfW = 0.74f
            val halfH = 1.48f
            val halfD = 0.11f

            val baseVertices = listOf(
                Vec3(-halfW, -halfH, -halfD),
                Vec3(halfW, -halfH, -halfD),
                Vec3(halfW, halfH, -halfD),
                Vec3(-halfW, halfH, -halfD),
                Vec3(-halfW, -halfH, halfD),
                Vec3(halfW, -halfH, halfD),
                Vec3(halfW, halfH, halfD),
                Vec3(-halfW, halfH, halfD),
            )
            val rotated = baseVertices.map { QuaternionMath.rotate(it, rotation) }

            val cameraDistance = 5.8f
            val scale = size.minDimension * 0.22f
            fun project(point: Vec3): Offset {
                val depth = (cameraDistance - point.z).coerceAtLeast(1.8f)
                val p = cameraDistance / depth
                return Offset(
                    x = size.width / 2f + point.x * scale * p,
                    y = size.height / 2f - point.y * scale * p,
                )
            }

            fun projectRaw(point: Vec3): Offset =
                project(QuaternionMath.rotate(point, rotation))

            drawPerspectiveGrid(
                center = Offset(size.width / 2f, size.height * 0.74f),
                width = size.width * 0.72f,
                height = size.height * 0.26f,
                color = outline,
            )

            data class Face(
                val indices: IntArray,
                val fill: Color,
                val stroke: Color,
            )

            val faces = listOf(
                Face(intArrayOf(0, 1, 2, 3), surface.copy(alpha = 0.55f), outline),
                Face(intArrayOf(4, 5, 6, 7), primary.copy(alpha = 0.16f), primary),
                Face(intArrayOf(0, 1, 5, 4), secondary.copy(alpha = 0.11f), secondary),
                Face(intArrayOf(3, 2, 6, 7), tertiary.copy(alpha = 0.10f), tertiary),
                Face(intArrayOf(0, 3, 7, 4), primary.copy(alpha = 0.08f), outline),
                Face(intArrayOf(1, 2, 6, 5), primary.copy(alpha = 0.08f), outline),
            ).sortedBy { face ->
                face.indices.map { rotated[it].z }.average()
            }

            faces.forEach { face ->
                val path = Path()
                face.indices.forEachIndexed { index, vertexIndex ->
                    val p = project(rotated[vertexIndex])
                    if (index == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                }
                path.close()
                drawPath(path = path, color = face.fill)
                drawPath(
                    path = path,
                    color = face.stroke,
                    style = Stroke(width = 4.5f),
                )
            }

            val frontCenter = projectRaw(Vec3(0f, 0f, halfD + 0.001f))
            val camera = projectRaw(Vec3(0f, halfH * 0.84f, halfD + 0.002f))
            drawCircle(
                color = onSurface.copy(alpha = 0.72f),
                radius = 7f,
                center = camera,
            )
            drawCircle(
                color = primary.copy(alpha = 0.18f),
                radius = 14f,
                center = frontCenter,
                style = Stroke(width = 3f),
            )

            val origin = Offset(size.width * 0.16f, size.height * 0.82f)
            val axis = size.minDimension * 0.11f
            drawLine(primary, origin, Offset(origin.x + axis, origin.y), 6f, cap = StrokeCap.Round)
            drawLine(secondary, origin, Offset(origin.x, origin.y - axis), 6f, cap = StrokeCap.Round)
            drawLine(tertiary, origin, Offset(origin.x - axis * 0.68f, origin.y + axis * 0.42f), 6f, cap = StrokeCap.Round)
        }
    }
}

@Composable
fun MagneticField3D(
    x: Float,
    y: Float,
    z: Float,
    magnitude: Float,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val surface = MaterialTheme.colorScheme.surfaceVariant
    val outline = MaterialTheme.colorScheme.outlineVariant

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp)
            .background(surface, RoundedCornerShape(28.dp)),
    ) {
        Canvas(Modifier.matchParentSize()) {
            val center = Offset(size.width / 2f, size.height * 0.53f)
            val radius = size.minDimension * 0.31f

            drawCircle(
                color = outline.copy(alpha = 0.55f),
                radius = radius,
                center = center,
                style = Stroke(width = 3f),
            )
            drawOval(
                color = outline.copy(alpha = 0.45f),
                topLeft = Offset(center.x - radius, center.y - radius * 0.34f),
                size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 0.68f),
                style = Stroke(width = 2.5f),
            )
            drawOval(
                color = outline.copy(alpha = 0.30f),
                topLeft = Offset(center.x - radius * 0.36f, center.y - radius),
                size = androidx.compose.ui.geometry.Size(radius * 0.72f, radius * 2f),
                style = Stroke(width = 2.5f),
            )

            val axis = radius * 0.82f
            drawLine(primary.copy(alpha = 0.65f), center, Offset(center.x + axis, center.y), 4f)
            drawLine(secondary.copy(alpha = 0.65f), center, Offset(center.x, center.y - axis), 4f)
            drawLine(
                tertiary.copy(alpha = 0.65f),
                center,
                Offset(center.x - axis * 0.65f, center.y + axis * 0.42f),
                4f,
            )

            val n = ThreeDMath.normalize(Vec3(x, y, z))
            val projected = Offset(
                x = (n.x - n.z * 0.62f) * radius * 0.92f,
                y = (-n.y + n.z * 0.34f) * radius * 0.92f,
            )
            val tip = Offset(center.x + projected.x, center.y + projected.y)

            val strengthScale = (magnitude / 100f).coerceIn(0.18f, 1f)
            drawCircle(
                color = primary.copy(alpha = 0.08f),
                radius = radius * strengthScale,
                center = center,
            )
            drawLine(
                color = primary,
                start = center,
                end = tip,
                strokeWidth = 10f,
                cap = StrokeCap.Round,
            )
            drawCircle(
                color = primary,
                radius = 14f,
                center = tip,
            )
            drawCircle(
                color = primary.copy(alpha = 0.16f),
                radius = 26f,
                center = tip,
                style = Stroke(width = 4f),
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPerspectiveGrid(
    center: Offset,
    width: Float,
    height: Float,
    color: Color,
) {
    val left = center.x - width / 2f
    val right = center.x + width / 2f
    val top = center.y - height / 2f
    val bottom = center.y + height / 2f

    repeat(5) { index ->
        val t = index / 4f
        val xTop = left + width * (0.18f + t * 0.64f)
        val xBottom = left + width * t
        drawLine(
            color = color.copy(alpha = 0.32f),
            start = Offset(xTop, top),
            end = Offset(xBottom, bottom),
            strokeWidth = 2f,
        )
    }
    repeat(4) { index ->
        val t = index / 3f
        val y = top + height * t
        val inset = (1f - t) * width * 0.18f
        drawLine(
            color = color.copy(alpha = 0.32f),
            start = Offset(left + inset, y),
            end = Offset(right - inset, y),
            strokeWidth = 2f,
        )
    }
}
