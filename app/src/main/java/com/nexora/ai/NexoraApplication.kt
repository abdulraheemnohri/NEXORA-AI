package com.nexora.ai

import android.app.Application
import com.nexora.ai.ai.LiteRtLocalModelRuntime
import com.nexora.ai.ai.SharedInferenceScheduler
import com.nexora.ai.society.MasxDatabase

/** Single process-wide owner for the model runtime and inference gate. */
class NexoraApplication : Application() {
    lateinit var localModelRuntime: LiteRtLocalModelRuntime
        private set
    lateinit var inferenceScheduler: SharedInferenceScheduler
        private set
    lateinit var masxDatabase: MasxDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        localModelRuntime = LiteRtLocalModelRuntime(cacheDir.resolve("litertlm"))
        inferenceScheduler = SharedInferenceScheduler(maxWaiting = 8)
        masxDatabase = MasxDatabase(this)
    }
}
