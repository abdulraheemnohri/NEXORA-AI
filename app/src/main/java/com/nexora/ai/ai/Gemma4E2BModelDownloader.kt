package com.nexora.ai.ai

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
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

/** Immutable metadata for one pinned model artifact. Internal injection keeps tests deterministic. */
internal data class ModelArtifactSpec(
    val fileName: String,
    val downloadUrl: String,
    val expectedBytes: Long,
    val expectedSha256: String,
    val safetySpaceBytes: Long = 150_000_000L
) {
    init {
        require(fileName.isNotBlank() && !fileName.contains('/') && !fileName.contains('\\')) {
            "Model file name must be a simple file name."
        }
        require(downloadUrl.startsWith("https://")) { "Model URL must use HTTPS." }
        require(expectedBytes > 0L) { "Expected model size must be positive." }
        require(expectedSha256.matches(Regex("[0-9a-f]{64}"))) {
            "Expected model SHA-256 must be a lowercase 64-character hexadecimal digest."
        }
        require(safetySpaceBytes >= 0L) { "Safety storage allowance cannot be negative." }
    }
}

/**
 * Downloads a revision-pinned Gemma bundle into app-private storage.
 * The exact byte count and SHA-256 are checked before an atomic finalization exposes the model.
 */
class Gemma4E2BModelDownloader internal constructor(
    context: Context,
    private val artifact: ModelArtifactSpec = DEFAULT_ARTIFACT,
    private val connectionFactory: (URL) -> HttpURLConnection = { url ->
        url.openConnection() as HttpURLConnection
    }
) {
    private val appContext = context.applicationContext

    suspend fun download(
        onProgress: (ModelDownloadProgress) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        val modelDirectory = File(appContext.filesDir, "models")
        check(modelDirectory.exists() || modelDirectory.mkdirs()) {
            "Unable to create private model directory."
        }

        val destination = File(modelDirectory, artifact.fileName)
        if (destination.isFile &&
            destination.length() == artifact.expectedBytes &&
            sha256(destination) == artifact.expectedSha256
        ) return@withContext destination
        deleteIfPresent(destination, "Could not remove an invalid existing model file.")

        val requiredSpace = artifact.expectedBytes + artifact.safetySpaceBytes
        check(appContext.filesDir.usableSpace >= requiredSpace) {
            "Not enough free app storage. Need at least ${requiredSpace / 1_000_000_000.0} GB free."
        }

        val partial = File(modelDirectory, "${artifact.fileName}.part")
        deleteIfPresent(partial, "Could not remove an incomplete previous download.")

        val connection = connectionFactory(URL(artifact.downloadUrl)).apply {
            requestMethod = "GET"
            connectTimeout = 30_000
            readTimeout = 90_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "NEXORA-AI-Android")
        }

        try {
            connection.connect()
            check(connection.url.protocol.equals("https", ignoreCase = true)) {
                "Model download redirects must remain on HTTPS."
            }
            check(connection.responseCode in 200..299) {
                "Model host returned HTTP ${connection.responseCode}."
            }
            val reportedLength = connection.contentLengthLong
            if (reportedLength > 0L) {
                check(reportedLength == artifact.expectedBytes) {
                    "Model size mismatch: expected ${artifact.expectedBytes} bytes, host reports $reportedLength."
                }
            }

            var downloaded = 0L
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 16)
            connection.inputStream.use { input ->
                FileOutputStream(partial, false).use { output ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                        downloaded += count
                        check(downloaded <= artifact.expectedBytes) {
                            "Downloaded file is larger than expected."
                        }
                        onProgress(
                            ModelDownloadProgress(
                                downloadedBytes = downloaded,
                                totalBytes = artifact.expectedBytes,
                                fraction = (downloaded.toDouble() / artifact.expectedBytes)
                                    .toFloat().coerceIn(0f, 1f)
                            )
                        )
                    }
                    output.fd.sync()
                }
            }

            check(downloaded == artifact.expectedBytes) {
                "Incomplete model download: received $downloaded of ${artifact.expectedBytes} bytes."
            }
            val actualHash = digest.digest().toHexString()
            check(actualHash == artifact.expectedSha256) {
                "Model SHA-256 mismatch. The downloaded artifact was rejected."
            }

            Files.move(
                partial.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
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

    private fun deleteIfPresent(file: File, message: String) {
        check(!file.exists() || file.delete()) { message }
    }

    private fun ByteArray.toHexString(): String =
        joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

    companion object {
        const val MODEL_FILE_NAME = "gemma-4-E2B-it.litertlm"
        const val EXPECTED_BYTES = 2_583_085_056L
        const val EXPECTED_SHA256 =
            "ab7838cdfc8f77e54d8ca45eadceb20452d9f01e4bfade03e5dce27911b27e42"
        const val MODEL_DOWNLOAD_URL =
            "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/7fa1d78473894f7e736a21d920c3aa80f950c0db/gemma-4-E2B-it.litertlm"

        private val DEFAULT_ARTIFACT = ModelArtifactSpec(
            fileName = MODEL_FILE_NAME,
            downloadUrl = MODEL_DOWNLOAD_URL,
            expectedBytes = EXPECTED_BYTES,
            expectedSha256 = EXPECTED_SHA256
        )
    }
}
