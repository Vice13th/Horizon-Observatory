package horizon.observatory.astronomy.time

import horizon.observatory.core.time.TimestampEngine

/**
 * Production [DeviceClockUtc]. Delegates to TimestampEngine, whose UTC value is the device wall
 * clock sampled once at process start plus elapsed real time. It is NOT GNSS-verified UTC.
 */
object TimestampEngineDeviceClock : DeviceClockUtc {
    override fun nowUtcMillis(): Long = TimestampEngine.currentUtcMillis()
}
