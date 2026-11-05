package com.nexora.ai.ai

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class ModelDownloadProgress(
    val downloadedBytes: Long,
    val totalBytes: Long,
    val fraction: Float
)

/** Downloads Gemma 4 E2B into app-private storage and rejects incomplete or unexpected files. */
class Gemma4E2BModelDownloader(context: Context) {
    private val appContext = context.applicationContext

    suspend fun download(
        onProgress: (ModelDownloadProgress) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        val modelDirectory = File(appContext.filesDir, "models")
        check(modelDirectory.exists() || modelDirectory.mkdirs()) {
            "Unable to create private model directory."
        }

        val destination = File(modelDirectory, MODEL_FILE_NAME)
        if (destination.isFile && destination.length() == EXPECTED_BYTES) return@withContext destination
        if (destination.exists()) destination.delete()

        val requiredSpace = EXPECTED_BYTES + SAFETY_SPACE_BYTES
        check(appContext.filesDir.usableSpace >= requiredSpace) {
            "Not enough free app storage. Need at least ${requiredSpace / 1_000_000_000.0} GB free."
        }

        val partial = File(modelDirectory, "$MODEL_FILE_NAME.part")
        if (partial.exists()) partial.delete()

        val connection = (URL(MODEL_DOWNLOAD_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 30_000
            readTimeout = 90_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "NEXORA-AI-Android")
        }

        try {
            connection.connect()
            check(connection.responseCode in 200..299) {
                "Model host returned HTTP ${connection.responseCode}."
            }
            val reportedLength = connection.contentLengthLong
            if (reportedLength > 0L) {
                check(reportedLength == EXPECTED_BYTES) {
                    "Model size changed: expected $EXPECTED_BYTES bytes, host reports $reportedLength."
                }
            }

            var downloaded = 0L
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 16)
            connection.inputStream.use { input ->
                FileOutputStream(partial).use { output ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        check(downloaded <= EXPECTED_BYTES) { "Downloaded file is larger than expected." }
                        onProgress(
                            ModelDownloadProgress(
                                downloadedBytes = downloaded,
                                totalBytes = EXPECTED_BYTES,
                                fraction = (downloaded.toDouble() / EXPECTED_BYTES).toFloat().coerceIn(0f, 1f)
                            )
                        )
                    }
                    output.fd.sync()
                }
            }
            check(downloaded == EXPECTED_BYTES) {
                "Incomplete model download: received $downloaded of $EXPECTED_BYTES bytes."
            }
            check(partial.renameTo(destination)) { "Could not finalize downloaded model file." }
            destination
        } catch (error: Throwable) {
            partial.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val MODEL_FILE_NAME = "gemma-4-E2B-it.litertlm"
        const val EXPECTED_BYTES = 2_583_085_056L
        private const val SAFETY_SPACE_BYTES = 150_000_000L
        const val MODEL_DOWNLOAD_URL =
            "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"
    }
}
