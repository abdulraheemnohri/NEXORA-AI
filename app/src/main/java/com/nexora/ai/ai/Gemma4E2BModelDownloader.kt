package com.nexora.ai.ai

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class ModelDownloadProgress(
    val downloadedBytes: Long,
    val totalBytes: Long,
    val fraction: Float
)

/**
 * Downloads a revision-pinned Gemma bundle into app-private storage.
 * Both the exact byte count and SHA-256 are checked before the file is exposed to the runtime.
 */
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
        if (destination.isFile &&
            destination.length() == EXPECTED_BYTES &&
            sha256(destination) == EXPECTED_SHA256
        ) return@withContext destination
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
                    "Model size mismatch: expected $EXPECTED_BYTES bytes, host reports $reportedLength."
                }
            }

            var downloaded = 0L
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 16)
            connection.inputStream.use { input ->
                FileOutputStream(partial).use { output ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
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
            val actualHash = digest.digest().toHexString()
            check(actualHash == EXPECTED_SHA256) {
                "Model SHA-256 mismatch. The downloaded artifact was rejected."
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

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 16)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().toHexString()
    }

    private fun ByteArray.toHexString(): String =
        joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

    companion object {
        const val MODEL_FILE_NAME = "gemma-4-E2B-it.litertlm"
        const val EXPECTED_BYTES = 2_583_085_056L
        const val EXPECTED_SHA256 =
            "ab7838cdfc8f77e54d8ca45eadceb20452d9f01e4bfade03e5dce27911b27e42"
        private const val SAFETY_SPACE_BYTES = 150_000_000L
        const val MODEL_DOWNLOAD_URL =
            "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/7fa1d78473894f7e736a21d920c3aa80f950c0db/gemma-4-E2B-it.litertlm"
    }
}
