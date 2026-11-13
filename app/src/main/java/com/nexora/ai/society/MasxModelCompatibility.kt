package com.nexora.ai.society

enum class ModelCompatibilityStatus {
    COMPATIBLE_WITH_LIMITATIONS, NOT_INSTALLED, UNSUPPORTED_RUNTIME,
    UNSUPPORTED_FORMAT, INSUFFICIENT_MEMORY, INSUFFICIENT_STORAGE, THERMAL_BLOCKED
}

data class ModelCompatibilityResult(
    val status: ModelCompatibilityStatus,
    val reasons: List<String>,
    val hardwareVerified: Boolean
)

/** Conservative preflight. A successful preflight is not a substitute for loading and benchmarking. */
object MasxModelCompatibility {
    fun assess(
        model: ModelProfile,
        device: DeviceResourceSnapshot,
        expectedRuntimeId: String,
        modelFileExists: Boolean,
        availableStorageBytes: Long
    ): ModelCompatibilityResult {
        if (model.runtimeId != expectedRuntimeId) return result(
            ModelCompatibilityStatus.UNSUPPORTED_RUNTIME,"Model runtime identifier does not match the installed adapter."
        )
        if (model.format.lowercase() != "litertlm") return result(
            ModelCompatibilityStatus.UNSUPPORTED_FORMAT,"The configured production adapter currently accepts LiteRT-LM artifacts only."
        )
        if (device.thermalStatus != null && device.thermalStatus >= 4) return result(
            ModelCompatibilityStatus.THERMAL_BLOCKED,"Device thermal state is severe; defer model loading and inference."
        )
        if (device.lowMemory || device.availableMemoryBytes < MasxResourceGovernor.MIN_AVAILABLE_MEMORY_BYTES) return result(
            ModelCompatibilityStatus.INSUFFICIENT_MEMORY,"Available memory is below the conservative 512 MiB inference reserve."
        )
        if (model.sizeBytes != null && availableStorageBytes < model.sizeBytes) return result(
            ModelCompatibilityStatus.INSUFFICIENT_STORAGE,"Available storage is smaller than the declared model artifact."
        )
        if (!modelFileExists) return result(
            ModelCompatibilityStatus.NOT_INSTALLED,"The model artifact is not present in the expected local storage."
        )
        return ModelCompatibilityResult(
            ModelCompatibilityStatus.COMPATIBLE_WITH_LIMITATIONS,
            listOf("Preflight checks passed; actual runtime load, inference, RAM peak and thermal stability still require device verification."),
            hardwareVerified = false
        )
    }

    private fun result(status: ModelCompatibilityStatus,reason: String) =
        ModelCompatibilityResult(status,listOf(reason),hardwareVerified=false)
}
