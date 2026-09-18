package com.example.hanoibus.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.widget.Toast
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Listens to device orientation sensor to compute compass azimuth (0° = North, 90° = East, etc.)
 */
@Composable
fun rememberCompassHeading(): State<Float> {
    val context = LocalContext.current
    val heading = remember { mutableFloatStateOf(0f) }

    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val orientationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION)

        var listener: SensorEventListener? = null

        if (rotationSensor != null) {
            listener = object : SensorEventListener {
                private val rotationMatrix = FloatArray(9)
                private val orientationAngles = FloatArray(3)

                override fun onSensorChanged(event: SensorEvent?) {
                    event ?: return
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    SensorManager.getOrientation(rotationMatrix, orientationAngles)
                    val azimuthRad = orientationAngles[0]
                    var azimuthDeg = Math.toDegrees(azimuthRad.toDouble()).toFloat()
                    if (azimuthDeg < 0) azimuthDeg += 360f
                    heading.floatValue = azimuthDeg
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_UI)
        } else if (orientationSensor != null) {
            @Suppress("DEPRECATION")
            listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    event ?: return
                    var azimuthDeg = event.values[0]
                    if (azimuthDeg < 0) azimuthDeg += 360f
                    heading.floatValue = azimuthDeg
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            sensorManager.registerListener(listener, orientationSensor, SensorManager.SENSOR_DELAY_UI)
        }

        onDispose {
            listener?.let { sensorManager.unregisterListener(it) }
        }
    }

    return heading
}

/**
 * Returns Vietnamese cardinal direction name from heading angle.
 */
fun getCardinalDirection(heading: Float): String {
    val norm = ((heading % 360f) + 360f) % 360f
    return when {
        norm >= 337.5f || norm < 22.5f -> "Bắc"
        norm >= 22.5f && norm < 67.5f -> "Đông Bắc"
        norm >= 67.5f && norm < 112.5f -> "Đông"
        norm >= 112.5f && norm < 157.5f -> "Đông Nam"
        norm >= 157.5f && norm < 202.5f -> "Nam"
        norm >= 202.5f && norm < 247.5f -> "Tây Nam"
        norm >= 247.5f && norm < 292.5f -> "Tây"
        else -> "Tây Bắc"
    }
}

/**
 * Compact, elegant Compass Widget showing map bearing relative to North.
 * Tapping resets the map to standard North-up (0°).
 */
@Composable
fun CompassWidget(
    mapBearing: Float = 0f,
    modifier: Modifier = Modifier,
    onResetBearing: (() -> Unit)? = null
) {
    val context = LocalContext.current

    // Smooth continuous rotation handling for map bearing
    var currentContinuousRotation by remember { mutableFloatStateOf(0f) }
    val targetRotation = remember(mapBearing) {
        val target = -mapBearing
        var diff = (target - currentContinuousRotation) % 360f
        if (diff > 180f) diff -= 360f
        if (diff < -180f) diff += 360f
        currentContinuousRotation + diff
    }
    currentContinuousRotation = targetRotation

    val animatedRotation by animateFloatAsState(
        targetValue = targetRotation,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "compassNeedle"
    )

    Surface(
        modifier = modifier
            .size(48.dp)
            .clickable {
                if (onResetBearing != null) {
                    onResetBearing()
                    if (kotlin.math.abs(mapBearing) > 1f) {
                        Toast.makeText(context, "Đã xoay bản đồ về hướng Bắc chuẩn", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "Bản đồ đang ở hướng Bắc chuẩn", Toast.LENGTH_SHORT).show()
                }
            },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 5.dp,
        tonalElevation = 2.dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Rotating Needle pointing to True North
            Canvas(
                modifier = Modifier
                    .size(40.dp)
                    .rotate(animatedRotation)
            ) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val halfW = 5f
                val tipDist = size.height * 0.42f

                // North needle (Red)
                val northPath = Path().apply {
                    moveTo(cx, cy - tipDist)
                    lineTo(cx + halfW, cy)
                    lineTo(cx - halfW, cy)
                    close()
                }
                drawPath(northPath, color = Color(0xFFE53935))

                // South needle (Silver / Light Grey)
                val southPath = Path().apply {
                    moveTo(cx, cy + tipDist)
                    lineTo(cx + halfW, cy)
                    lineTo(cx - halfW, cy)
                    close()
                }
                drawPath(southPath, color = Color(0xFF90A4AE))

                // Center pivot dot
                drawCircle(
                    color = Color(0xFF263238),
                    radius = 3.5f,
                    center = Offset(cx, cy)
                )
                drawCircle(
                    color = Color.White,
                    radius = 1.5f,
                    center = Offset(cx, cy)
                )
            }

            // Fixed label 'B' (Bắc) at top
            Text(
                text = "B",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE53935),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 2.dp)
            )
        }
    }
}

/**
 * My Location recenter button.
 */
@Composable
fun MyLocationButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .size(48.dp)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 5.dp,
        tonalElevation = 2.dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MyLocation,
                contentDescription = "Trở về vị trí hiện tại",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * Floating Controls column combining Compass and My Location button.
 */
@Composable
fun MapFloatingControls(
    mapBearing: Float = 0f,
    onRecenterClick: () -> Unit,
    onResetBearing: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CompassWidget(
            mapBearing = mapBearing,
            onResetBearing = onResetBearing
        )
        MyLocationButton(
            onClick = onRecenterClick
        )
    }
}
