package com.example.easyfill_project.hand_analysis

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log

//Connects to Android sensors, starts/stops listening, and sends sensor data into MotionAnalyzer.
class MotionSensorManager(
    private val context: Context
) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val accelerometer =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val gyroscope =
        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val analyzer = MotionAnalyzer()

    fun start() {
        analyzer.start()

        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }

        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }

        Log.d("MOTION", "Motion tracking started")
    }

    fun stopAndAnalyze(): MotionAnalysisResult {
        sensorManager.unregisterListener(this)

        val result = analyzer.analyze()
        Log.d("MOTION_ANALYSIS", result.toString())

        return result
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> analyzer.addAccelerometer(x, y, z)
            Sensor.TYPE_GYROSCOPE -> analyzer.addGyroscope(x, y, z)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}