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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.k410sh4.a25lab.util.ThreeDMath
import com.k410sh4.a25lab.util.Vec3
import kotlin.math.PI

@Composable
fun OrientationCube3D(
    yawDeg: Float,
    pitchDeg: Float,
    rollDeg: Float,
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val surface = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(230.dp)
            .background(surface, RoundedCornerShape(22.dp)),
    ) {
        Canvas(Modifier.matchParentSize()) {
            val yaw = yawDeg * PI.toFloat() / 180f
            val pitch = pitchDeg * PI.toFloat() / 180f
            val roll = rollDeg * PI.toFloat() / 180f

            val vertices = listOf(
                Vec3(-1f, -1f, -1f),
                Vec3(1f, -1f, -1f),
                Vec3(1f, 1f, -1f),
                Vec3(-1f, 1f, -1f),
                Vec3(-1f, -1f, 1f),
                Vec3(1f, -1f, 1f),
                Vec3(1f, 1f, 1f),
                Vec3(-1f, 1f, 1f),
            ).map { ThreeDMath.rotateEuler(it, yaw, pitch, roll) }

            val scale = size.minDimension * 0.22f
            val cameraDistance = 4.5f
            fun project(p: Vec3): Offset {
                val depth = (cameraDistance - p.z).coerceAtLeast(1.5f)
                val perspective = cameraDistance / depth
                return Offset(
                    x = size.width / 2f + p.x * scale * perspective,
                    y = size.height / 2f - p.y * scale * perspective,
                )
            }

            val projected = vertices.map(::project)
            val edges = listOf(
                0 to 1, 1 to 2, 2 to 3, 3 to 0,
                4 to 5, 5 to 6, 6 to 7, 7 to 4,
                0 to 4, 1 to 5, 2 to 6, 3 to 7,
            )
            edges.forEachIndexed { index, edge ->
                drawLine(
                    color = if (index < 4) secondary else lineColor,
                    start = projected[edge.first],
                    end = projected[edge.second],
                    strokeWidth = 6f,
                    cap = StrokeCap.Round,
                )
            }

            val center = Offset(size.width / 2f, size.height / 2f)
            val axisLength = size.minDimension * 0.18f
            drawLine(
                color = lineColor,
                start = center,
                end = Offset(center.x + axisLength, center.y),
                strokeWidth = 4f,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = secondary,
                start = center,
                end = Offset(center.x, center.y - axisLength),
                strokeWidth = 4f,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
fun MagneticVector3D(
    x: Float,
    y: Float,
    z: Float,
    modifier: Modifier = Modifier,
) {
    val vectorColor = MaterialTheme.colorScheme.primary
    val axisColor = MaterialTheme.colorScheme.outline
    val surface = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(210.dp)
            .background(surface, RoundedCornerShape(22.dp)),
    ) {
        Canvas(Modifier.matchParentSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val axis = size.minDimension * 0.28f

            val xAxis = Offset(axis, axis * 0.35f)
            val yAxis = Offset(0f, -axis)
            val zAxis = Offset(-axis, axis * 0.35f)

            fun endpoint(v: Offset) = Offset(center.x + v.x, center.y + v.y)

            drawLine(axisColor, center, endpoint(xAxis), 3f, cap = StrokeCap.Round)
            drawLine(axisColor, center, endpoint(yAxis), 3f, cap = StrokeCap.Round)
            drawLine(axisColor, center, endpoint(zAxis), 3f, cap = StrokeCap.Round)

            val n = ThreeDMath.normalize(Vec3(x, y, z))
            val screen = Offset(
                x = (n.x - n.z) * axis * 0.95f,
                y = (-n.y + (n.x + n.z) * 0.35f) * axis * 0.95f,
            )
            val tip = Offset(center.x + screen.x, center.y + screen.y)

            drawLine(
                color = vectorColor,
                start = center,
                end = tip,
                strokeWidth = 9f,
                cap = StrokeCap.Round,
            )
            drawCircle(
                color = vectorColor,
                radius = 12f,
                center = tip,
            )
        }
    }
}
