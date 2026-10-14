package com.kirankotla.connectfour.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class BoxDemoProjectionTest {
    @Test
    fun projectPoint_keeps_origin_in_center() {
        val projected = projectPoint(
            point = Point3D(0f, 0f, 0f),
            centerX = 360f,
            centerY = 640f,
            scale = 1f,
            cameraDistance = 5f
        )

        assertEquals(360f, projected.x, 0.01f)
        assertEquals(640f, projected.y, 0.01f)
    }

    @Test
    fun rotatePoint_turns_x_axis_after_yaw() {
        val rotated = rotatePoint(Point3D(1f, 0f, 0f), rotationX = 0f, rotationY = 90f)

        assertTrue(abs(rotated.x) < 0.01f)
        assertTrue(abs(rotated.z - 1f) < 0.01f)
    }
}
