package com.nexora.ai.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class Gemma4E2BModelDownloaderInstrumentedTest {
    private lateinit var context: Context
    private lateinit var modelDirectory: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        modelDirectory = File(context.filesDir, "models")
        modelDirectory.deleteRecursively()
    }

    @After
    fun tearDown() {
        modelDirectory.deleteRecursively()
    }

    @Test
    fun validArtifactIsVerifiedAndAtomicallyInstalled() = runBlocking {
        val payload = "verified-model-fixture".toByteArray()
        val downloader = downloader(payload)
        val progress = mutableListOf<ModelDownloadProgress>()

        val installed = downloader.download { progress.add(it) }

        assertTrue(installed.isFile)
        assertArrayEquals(payload, installed.readBytes())
        assertEquals(payload.size.toLong(), progress.last().downloadedBytes)
        assertEquals(1f, progress.last().fraction, 0f)
        assertFalse(File(modelDirectory, "${TEST_FILE_NAME}.part").exists())
    }

    @Test
    fun checksumMismatchNeverExposesDestinationFile() = runBlocking {
        val payload = "tampered-model-fixture".toByteArray()
        val downloader = downloader(payload, expectedHash = "0".repeat(64))

        val failure = runCatching { downloader.download() }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertFalse(File(modelDirectory, TEST_FILE_NAME).exists())
        assertFalse(File(modelDirectory, "${TEST_FILE_NAME}.part").exists())
    }

    @Test
    fun truncatedResponseIsRejectedAndPartialFileIsRemoved() = runBlocking {
        val payload = "short".toByteArray()
        val downloader = downloader(payload, expectedBytes = payload.size.toLong() + 3L, reportedLength = -1L)

        val failure = runCatching { downloader.download() }.exceptionOrNull()

        assertTrue(failure?.message.orEmpty().contains("Incomplete model download"))
        assertFalse(File(modelDirectory, TEST_FILE_NAME).exists())
        assertFalse(File(modelDirectory, "${TEST_FILE_NAME}.part").exists())
    }

    @Test
    fun oversizedResponseIsRejectedAndPartialFileIsRemoved() = runBlocking {
        val payload = "too-large".toByteArray()
        val downloader = downloader(payload, expectedBytes = payload.size.toLong() - 1L, reportedLength = -1L)

        val failure = runCatching { downloader.download() }.exceptionOrNull()

        assertTrue(failure?.message.orEmpty().contains("larger than expected"))
        assertFalse(File(modelDirectory, TEST_FILE_NAME).exists())
        assertFalse(File(modelDirectory, "${TEST_FILE_NAME}.part").exists())
    }

    @Test
    fun invalidExistingArtifactIsReplacedOnlyAfterNewArtifactPassesVerification() = runBlocking {
        modelDirectory.mkdirs()
        val destination = File(modelDirectory, TEST_FILE_NAME)
        destination.writeText("corrupt-old-file")
        val payload = "replacement-model".toByteArray()
        val downloader = downloader(payload)

        val installed = downloader.download()

        assertEquals(destination, installed)
        assertArrayEquals(payload, installed.readBytes())
    }

    @Test
    fun redirectedHttpUrlIsRejected() = runBlocking {
        val payload = "model".toByteArray()
        val downloader = downloader(payload, finalUrl = URL("http://example.test/model"))

        val failure = runCatching { downloader.download() }.exceptionOrNull()

        assertTrue(failure?.message.orEmpty().contains("remain on HTTPS"))
        assertFalse(File(modelDirectory, TEST_FILE_NAME).exists())
    }

    private fun downloader(
        payload: ByteArray,
        expectedBytes: Long = payload.size.toLong(),
        expectedHash: String = payload.sha256(),
        finalUrl: URL = URL("https://cdn.example.test/model"),
        reportedLength: Long = payload.size.toLong()
    ): Gemma4E2BModelDownloader {
        val spec = ModelArtifactSpec(
            fileName = TEST_FILE_NAME,
            downloadUrl = "https://huggingface.co/model",
            expectedBytes = expectedBytes,
            expectedSha256 = expectedHash,
            safetySpaceBytes = 0L
        )
        return Gemma4E2BModelDownloader(context, spec) { _ ->
            FakeConnection(finalUrl, payload, reportedLength)
        }
    }

    private class FakeConnection(
        url: URL,
        private val payload: ByteArray,
        private val reportedLength: Long
    ) : HttpURLConnection(url) {
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = false
        override fun getResponseCode(): Int = HTTP_OK
        override fun getContentLengthLong(): Long = reportedLength
        override fun getInputStream(): InputStream = ByteArrayInputStream(payload)
    }

    private fun ByteArray.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(this)
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private companion object {
        const val TEST_FILE_NAME = "nexora-test-model.litertlm"
    }
}
