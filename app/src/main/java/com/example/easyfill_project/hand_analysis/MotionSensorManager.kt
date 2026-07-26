//package com.example.easyfill_project.hand_analysis
//
//import android.content.Context
//import android.hardware.Sensor
//import android.hardware.SensorEvent
//import android.hardware.SensorEventListener
//import android.hardware.SensorManager
//import android.util.Log
//
//// Connects to Android sensors, starts/stops listening, and sends sensor data into MotionAnalyzer.
//class MotionSensorManager(
//    private val context: Context
//) : SensorEventListener {
//
//    private val sensorManager =
//        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
//
//    private val accelerometer =
//        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
//
//    private val gyroscope =
//        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
//
//    private val analyzer = MotionAnalyzer()
//
//    fun start() {
//        analyzer.start()
//
//        accelerometer?.let {
//            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
//        }
//
//        gyroscope?.let {
//            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
//        }
//
//        Log.d("MOTION", "Motion tracking started")
//    }
//
//    fun stopAndAnalyze(): MotionAnalysisResult {
//        sensorManager.unregisterListener(this)
//
//        val result = analyzer.analyze()
//        Log.d("MOTION_ANALYSIS", result.toString())
//
//        return result
//    }
//
//    override fun onSensorChanged(event: SensorEvent?) {
//        event ?: return
//
//        val x = event.values[0]
//        val y = event.values[1]
//        val z = event.values[2]
//
//        when (event.sensor.type) {
//            Sensor.TYPE_ACCELEROMETER -> analyzer.addAccelerometer(x, y, z)
//            Sensor.TYPE_GYROSCOPE -> analyzer.addGyroscope(x, y, z)
//        }
//    }
//
//    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
//}


///////////////////////////////////////////////

package com.example.easyfill_project.hand_analysis

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log

/*
 * Connects to the Android accelerometer and gyroscope and
 * forwards their samples to MotionAnalyzer.
 *
 * The same continuous sensor collection supports:
 *
 * - live five-second tremor analysis
 * - the ten-second accumulated baseline candidate
 */
class MotionSensorManager(
    context: Context
) : SensorEventListener {

    companion object {
        private const val TAG =
            "MOTION"
    }

    private val sensorManager =
        context.getSystemService(
            Context.SENSOR_SERVICE
        ) as SensorManager

    private val accelerometer =
        sensorManager.getDefaultSensor(
            Sensor.TYPE_ACCELEROMETER
        )

    private val gyroscope =
        sensorManager.getDefaultSensor(
            Sensor.TYPE_GYROSCOPE
        )

    private val analyzer =
        MotionAnalyzer()

    /*
     * Sensor callbacks and snapshot requests may arrive from
     * different threads.
     *
     * This lock prevents MotionAnalyzer lists from being read
     * while a sensor event is modifying them.
     */
    private val analyzerLock =
        Any()

    /*
     * Set to false before unregistering the listener so
     * already queued sensor events are ignored.
     */
    @Volatile
    private var acceptingSamples =
        false

    /*
     * Starts one discrete ten-second collection.
     *
     * This is used only when no saved baseline exists and an
     * initial personal baseline must be created.
     */
    fun start() {
        unregisterSensors()

        synchronized(analyzerLock) {
            analyzer.start()
        }

        registerSensors(
            modeName =
                "discrete"
        )
    }

    /*
     * Stops the discrete collection and analyzes every sample
     * collected since start().
     */
    fun stopAndAnalyze():
            MotionAnalysisResult {

        unregisterSensors()

        val result =
            synchronized(analyzerLock) {
                analyzer.analyze()
            }

        Log.d(
            "MOTION_ANALYSIS",
            """
            Discrete motion analysis completed.
            reliable=${result.isReliable}
            duration=${result.durationSeconds}
            accelerationSamples=${result.accelerationValues.size}
            gyroscopeSamples=${result.gyroscopeValues.size}
            """.trimIndent()
        )

        return result
    }

    /*
     * Starts one uninterrupted rolling collection.
     *
     * The controller can request snapshot(5.0) for current
     * tremor detection and snapshot(10.0) for the accumulated
     * baseline candidate without restarting the sensors.
     */
    fun startContinuous() {
        unregisterSensors()

        synchronized(analyzerLock) {
            analyzer.startContinuous()
        }

        registerSensors(
            modeName =
                "continuous"
        )
    }

    /*
     * Returns a rolling motion window without clearing or
     * interrupting the continuous sensor collection.
     */
    fun snapshot(
        windowSeconds: Double
    ): MotionAnalysisResult {

        if (windowSeconds <= 0.0) {
            Log.e(
                TAG,
                "Snapshot duration must be positive"
            )

            return synchronized(analyzerLock) {
                analyzer.snapshot(
                    windowSeconds = 0.0
                )
            }
        }

        return synchronized(analyzerLock) {
            analyzer.snapshot(
                windowSeconds =
                    windowSeconds
            )
        }
    }

    /*
     * Stops continuous collection.
     *
     * This method is also safe when the sensors are already
     * stopped.
     */
    fun stopContinuous() {
        unregisterSensors()

        Log.d(
            TAG,
            "Continuous motion tracking stopped"
        )
    }

    /*
     * Registers both required sensors.
     *
     * FASTEST requests the highest sampling rate available on
     * the current device. The analyzer still uses the real
     * SensorEvent timestamps and does not assume a fixed rate.
     */
    private fun registerSensors(
        modeName: String
    ) {
        if (accelerometer == null) {
            Log.e(
                TAG,
                "Accelerometer is not available"
            )
        }

        if (gyroscope == null) {
            Log.e(
                TAG,
                "Gyroscope is not available"
            )
        }

        /*
         * The analyzer has already been reset, so callbacks may
         * now safely be accepted.
         */
        acceptingSamples =
            true

        val accelerometerRegistered =
            accelerometer?.let { sensor ->

                sensorManager.registerListener(
                    this,
                    sensor,
                    SensorManager.SENSOR_DELAY_FASTEST
                )
            } ?: false

        val gyroscopeRegistered =
            gyroscope?.let { sensor ->

                sensorManager.registerListener(
                    this,
                    sensor,
                    SensorManager.SENSOR_DELAY_FASTEST
                )
            } ?: false

        /*
         * Both sensors are required for a reliable hand
         * measurement.
         */
        if (
            !accelerometerRegistered ||
            !gyroscopeRegistered
        ) {
            Log.e(
                TAG,
                """
                Could not register every required motion sensor.
                accelerometerRegistered=$accelerometerRegistered
                gyroscopeRegistered=$gyroscopeRegistered
                """.trimIndent()
            )
        }

        /*
         * Stop accepting events when neither sensor could be
         * registered.
         */
        if (
            !accelerometerRegistered &&
            !gyroscopeRegistered
        ) {
            acceptingSamples =
                false
        }

        Log.d(
            TAG,
            """
            Motion tracking started.
            mode=$modeName
            accelerometerRegistered=$accelerometerRegistered
            gyroscopeRegistered=$gyroscopeRegistered
            """.trimIndent()
        )
    }

    /*
     * Prevents additional samples from entering the analyzer
     * and removes the listener from every registered sensor.
     */
    private fun unregisterSensors() {
        acceptingSamples =
            false

        sensorManager.unregisterListener(
            this
        )
    }

    override fun onSensorChanged(
        event: SensorEvent?
    ) {
        if (
            event == null ||
            !acceptingSamples ||
            event.values.size < 3
        ) {
            return
        }

        val x =
            event.values[0]

        val y =
            event.values[1]

        val z =
            event.values[2]

        /*
         * SensorEvent.timestamp is monotonic and measured in
         * nanoseconds.
         *
         * It is required for:
         *
         * - the filters' real dt
         * - time-based window slicing
         * - fixed-rate resampling before FFT
         */
        synchronized(analyzerLock) {
            when (event.sensor.type) {

                Sensor.TYPE_ACCELEROMETER -> {
                    analyzer.addAccelerometer(
                        x = x,
                        y = y,
                        z = z,
                        timestampNs =
                            event.timestamp
                    )
                }

                Sensor.TYPE_GYROSCOPE -> {
                    analyzer.addGyroscope(
                        x = x,
                        y = y,
                        z = z,
                        timestampNs =
                            event.timestamp
                    )
                }
            }
        }
    }

    override fun onAccuracyChanged(
        sensor: Sensor?,
        accuracy: Int
    ) {
        Log.d(
            TAG,
            """
            Sensor accuracy changed.
            sensor=${sensor?.name}
            accuracy=$accuracy
            """.trimIndent()
        )
    }
}