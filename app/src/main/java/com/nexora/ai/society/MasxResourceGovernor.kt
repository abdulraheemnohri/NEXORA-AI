package com.nexora.ai.society

import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DeviceResourceSnapshot(
    val availableMemoryBytes: Long,
    val lowMemory: Boolean,
    val batteryPercent: Int?,
    val powerSaveMode: Boolean,
    val thermalStatus: Int?,
    val capturedAt: Long
) {
    val availableMemoryMb: Long get() = availableMemoryBytes / (1024 * 1024)
}

/** A fresh admission decision is required immediately before a society inference starts. */
fun interface MasxInferenceAdmission {
    suspend fun requireAllowed()
}

/** Pure resource policy so the device thresholds can be tested without Android services. */
object MasxResourcePolicy {
    const val MIN_AVAILABLE_MEMORY_BYTES = 512L * 1024L * 1024L
    // Android defines THERMAL_STATUS_SEVERE as 3; 4 is already CRITICAL.
    private const val SEVERE_THERMAL_STATUS = PowerManager.THERMAL_STATUS_SEVERE

    fun canStartInference(snapshot: DeviceResourceSnapshot): Pair<Boolean, String> {
        if (snapshot.lowMemory || snapshot.availableMemoryBytes < MIN_AVAILABLE_MEMORY_BYTES) {
            return false to "Insufficient available memory for another inference. Close other apps or unload the model."
        }
        if (snapshot.thermalStatus != null && snapshot.thermalStatus >= SEVERE_THERMAL_STATUS) {
            return false to "Device thermal state is severe. Wait for the device to cool before starting inference."
        }
        return true to "Resource gate passed."
    }
}

/** Conservative Android resource gate. It reports only OS-provided measurements. */
class MasxResourceGovernor(context: Context) : MasxInferenceAdmission {
    private val app = context.applicationContext

    fun snapshot(): DeviceResourceSnapshot {
        val activity = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo().also(activity::getMemoryInfo)
        val battery = app.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            .takeIf { it in 0..100 }
        val power = app.getSystemService(Context.POWER_SERVICE) as PowerManager
        val thermal = if (Build.VERSION.SDK_INT >= 29) power.currentThermalStatus else null
        return DeviceResourceSnapshot(
            availableMemoryBytes = memory.availMem,
            lowMemory = memory.lowMemory,
            batteryPercent = level,
            powerSaveMode = power.isPowerSaveMode,
            thermalStatus = thermal,
            capturedAt = System.currentTimeMillis()
        )
    }

    fun canStartInference(snapshot: DeviceResourceSnapshot = snapshot()): Pair<Boolean, String> =
        MasxResourcePolicy.canStartInference(snapshot)

    /** Re-sample on IO after the shared inference slot has been acquired. */
    override suspend fun requireAllowed() {
        val (allowed, reason) = withContext(Dispatchers.IO) {
            canStartInference(snapshot())
        }
        check(allowed) { reason }
    }

    companion object {
        const val MIN_AVAILABLE_MEMORY_BYTES = MasxResourcePolicy.MIN_AVAILABLE_MEMORY_BYTES
    }
}
