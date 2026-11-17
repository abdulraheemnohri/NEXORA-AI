package com.nexora.ai.society

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MasxModelCompatibilityTest {
    private val model=ModelProfile(
        id="local.test",name="Test",family="Test",runtimeId="litertlm-android-v0.18.1",
        format="litertlm",source="local",localPath="/private/model.litertlm",quantization=null,
        sizeBytes=100,contextLength=null,capabilities=setOf("text-generation"),
        status=ModelInstallStatus.INSTALLED,checksum=null,license="test",updatedAt=1
    )
    private val healthy=DeviceResourceSnapshot(2L*1024*1024*1024,false,80,false,0,1)

    @Test fun compatiblePreflightNeverClaimsHardwareVerification() {
        val result=MasxModelCompatibility.assess(model,healthy,model.runtimeId,true,1_000)
        assertEquals(ModelCompatibilityStatus.COMPATIBLE_WITH_LIMITATIONS,result.status)
        assertFalse(result.hardwareVerified)
    }

    @Test fun rejectsRuntimeMismatch() {
        val result=MasxModelCompatibility.assess(model,healthy,"other-runtime",true,1_000)
        assertEquals(ModelCompatibilityStatus.UNSUPPORTED_RUNTIME,result.status)
    }

    @Test fun blocksLowMemoryAndSevereThermalState() {
        val low=healthy.copy(availableMemoryBytes=100L*1024*1024)
        assertEquals(ModelCompatibilityStatus.INSUFFICIENT_MEMORY,MasxModelCompatibility.assess(model,low,model.runtimeId,true,1_000).status)
        val hot=healthy.copy(thermalStatus=4)
        assertEquals(ModelCompatibilityStatus.THERMAL_BLOCKED,MasxModelCompatibility.assess(model,hot,model.runtimeId,true,1_000).status)
    }
}
