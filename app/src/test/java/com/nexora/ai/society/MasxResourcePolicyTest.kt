package com.nexora.ai.society

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MasxResourcePolicyTest {
    private fun snapshot(
        memoryBytes: Long = MasxResourcePolicy.MIN_AVAILABLE_MEMORY_BYTES,
        lowMemory: Boolean = false,
        thermalStatus: Int? = 0
    ) = DeviceResourceSnapshot(
        availableMemoryBytes = memoryBytes,
        lowMemory = lowMemory,
        batteryPercent = 80,
        powerSaveMode = false,
        thermalStatus = thermalStatus,
        capturedAt = 1L
    )

    @Test fun enforcesThe512MiBReserveAtItsBoundary() {
        assertFalse(
            MasxResourcePolicy.canStartInference(
                snapshot(memoryBytes = MasxResourcePolicy.MIN_AVAILABLE_MEMORY_BYTES - 1)
            ).first
        )
        assertTrue(
            MasxResourcePolicy.canStartInference(
                snapshot(memoryBytes = MasxResourcePolicy.MIN_AVAILABLE_MEMORY_BYTES)
            ).first
        )
    }

    @Test fun operatingSystemLowMemoryFlagBlocksInferenceEvenAboveReserve() {
        assertFalse(MasxResourcePolicy.canStartInference(snapshot(lowMemory = true)).first)
    }

    @Test fun severeThermalStateBlocksButUnknownAndModerateStatesDoNot() {
        assertTrue(MasxResourcePolicy.canStartInference(snapshot(thermalStatus = null)).first)
        assertTrue(MasxResourcePolicy.canStartInference(snapshot(thermalStatus = 3)).first)
        assertFalse(MasxResourcePolicy.canStartInference(snapshot(thermalStatus = 4)).first)
        assertFalse(MasxResourcePolicy.canStartInference(snapshot(thermalStatus = 5)).first)
    }
}
