package com.example.audio

import java.io.File
import java.io.RandomAccessFile
import java.net.URI
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

enum class AudioDownloadMode {
  SEGMENTED_HTTP,
  ARIA2_JSON_RPC,
}

data class AudioDownloadProgress(
  val downloadedBytes: Long,
  val totalBytes: Long,
  val connectionsUsed: Int,
  val statusMessage: String,
) {
  val fraction: Float
    get() = if (totalBytes > 0L) (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
}

data class DownloadedAudioFile(
  val file: File,
  val displayTitle: String,
  val extension: String,
  val connectionsUsed: Int,
  val totalBytes: Long,
  val transportLabel: String,
)

/**
 * Play-policy compliant multi-connection HTTP range downloader (aria2-inspired parallel
 * segmented transfers) plus optional client integration for a user-hosted aria2 JSON-RPC
 * daemon (`aria2c --enable-rpc`).
 */
class SegmentedAudioDownloader(
  private val client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .followRedirects(true)
    .build(),
) {

  suspend fun downloadDirectAudioUrl(
    rawUrl: String,
    outputDir: File,
    maxConnections: Int = 4,
    onProgress: (AudioDownloadProgress) -> Unit = {},
  ): DownloadedAudioFile = withContext(Dispatchers.IO) {
    val cleanUrl = validateAudioUrl(rawUrl)
    if (!outputDir.exists()) {
      outputDir.mkdirs()
    }

    val probe = probeRemoteAudio(cleanUrl)
    val ext = inferExtension(cleanUrl, probe.contentType, probe.fileName)
    val baseName = (probe.fileName ?: inferFileNameFromUrl(cleanUrl))
      .substringBeforeLast('.')
      .ifBlank { "Downloaded Audio" }
    val targetFile = File(outputDir, "dl_${System.currentTimeMillis()}.$ext")

    val safeConnections = maxConnections.coerceIn(1, 8)
    val useSegmented = probe.acceptsRanges &&
      probe.contentLength >= MIN_SEGMENTED_BYTES &&
      safeConnections > 1

    if (useSegmented) {
      downloadSegmentedParallel(
        url = cleanUrl,
        totalBytes = probe.contentLength,
        connections = safeConnections,
        destFile = targetFile,
        onProgress = onProgress,
      )
    } else {
      downloadSingleStream(
        url = cleanUrl,
        expectedBytes = probe.contentLength,
        destFile = targetFile,
        onProgress = onProgress,
      )
    }

    if (!targetFile.exists() || targetFile.length() == 0L) {
      targetFile.delete()
      throw IllegalStateException("Downloaded audio file was empty.")
    }

    DownloadedAudioFile(
      file = targetFile,
      displayTitle = baseName,
      extension = ext,
      connectionsUsed = if (useSegmented) safeConnections else 1,
      totalBytes = targetFile.length(),
      transportLabel = if (useSegmented) "Multi-HTTP x$safeConnections" else "Direct HTTP",
    )
  }

  /**
   * Submits a URL to a user-hosted aria2 JSON-RPC endpoint (`aria2.addUri`) and monitors
   * progress (`aria2.tellStatus`). Note: Remote files stored on another host still require
   * an HTTP-accessible audio URL to import locally into SonicForge.
   */
  suspend fun submitToAria2JsonRpc(
    rpcEndpointUrl: String,
    sourceUrl: String,
    rpcSecret: String = "",
    connections: Int = 4,
    onProgress: (AudioDownloadProgress) -> Unit = {},
  ): String = withContext(Dispatchers.IO) {
    val cleanRpc = rpcEndpointUrl.trim()
    require(cleanRpc.startsWith("http://") || cleanRpc.startsWith("https://")) {
      "aria2 RPC URL must start with http:// or https://"
    }
    val cleanSource = validateAudioUrl(sourceUrl, allowGenericStreams = true)

    val params = JSONArray().apply {
      if (rpcSecret.isNotBlank()) {
        put("token:${rpcSecret.trim()}")
      }
      put(JSONArray().put(cleanSource))
      put(
        JSONObject().apply {
          put("split", connections.coerceIn(1, 16).toString())
          put("max-connection-per-server", connections.coerceIn(1, 16).toString())
        }
      )
    }
    val payload = JSONObject().apply {
      put("jsonrpc", "2.0")
      put("id", "sonicforge_add")
      put("method", "aria2.addUri")
      put("params", params)
    }

    onProgress(AudioDownloadProgress(0L, 0L, connections, "Submitting to aria2 RPC…"))
    val gid = executeJsonRpc(cleanRpc, payload).getString("result")

    // Poll aria2.tellStatus up to 20 seconds so user sees real RPC progress
    repeat(20) {
      val statusParams = JSONArray().apply {
        if (rpcSecret.isNotBlank()) {
          put("token:${rpcSecret.trim()}")
        }
        put(gid)
      }
      val statusReq = JSONObject().apply {
        put("jsonrpc", "2.0")
        put("id", "sonicforge_status")
        put("method", "aria2.tellStatus")
        put("params", statusParams)
      }
      val statusObj = executeJsonRpc(cleanRpc, statusReq).optJSONObject("result")
      val state = statusObj?.optString("status").orEmpty()
      val totalLen = statusObj?.optString("totalLength")?.toLongOrNull() ?: 0L
      val completedLen = statusObj?.optString("completedLength")?.toLongOrNull() ?: 0L

      onProgress(
        AudioDownloadProgress(
          downloadedBytes = completedLen,
          totalBytes = totalLen,
          connectionsUsed = connections,
          statusMessage = "aria2 GID $gid: $state",
        )
      )
      if (state == "complete") {
        return@withContext gid
      }
      if (state == "error") {
        val errMsg = statusObj?.optString("errorMessage").orEmpty().ifBlank { "aria2 task failed" }
        throw IllegalStateException("aria2 RPC error: $errMsg")
      }
      delay(750L)
    }
    gid
  }

  private fun executeJsonRpc(rpcUrl: String, payload: JSONObject): JSONObject {
    val request = Request.Builder()
      .url(rpcUrl)
      .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
      .build()
    client.newCall(request).execute().use { response ->
      val body = response.body?.string().orEmpty()
      if (!response.isSuccessful) {
        throw IllegalStateException("aria2 RPC returned HTTP ${response.code}: ${body.take(140)}")
      }
      val json = JSONObject(body)
      if (json.has("error") && !json.isNull("error")) {
        val msg = json.getJSONObject("error").optString("message", "Unknown JSON-RPC error")
        throw IllegalStateException("aria2 RPC error: $msg")
      }
      return json
    }
  }

  private data class RemoteProbe(
    val contentLength: Long,
    val acceptsRanges: Boolean,
    val contentType: String?,
    val fileName: String?,
  )

  private fun probeRemoteAudio(url: String): RemoteProbe {
    val headRequest = Request.Builder().url(url).head().build()
    return try {
      client.newCall(headRequest).execute().use { response ->
        if (!response.isSuccessful) {
          RemoteProbe(-1L, false, null, null)
        } else {
          val len = response.header("Content-Length")?.toLongOrNull() ?: -1L
          val acceptRanges = response.header("Accept-Ranges")
            ?.contains("bytes", ignoreCase = true) == true
          val contentType = response.header("Content-Type")
          val disposition = response.header("Content-Disposition")
          val fileName = disposition?.let { parseFileNameFromDisposition(it) }
          RemoteProbe(len, acceptRanges, contentType, fileName)
        }
      }
    } catch (_: Exception) {
      RemoteProbe(-1L, false, null, null)
    }
  }

  private suspend fun downloadSegmentedParallel(
    url: String,
    totalBytes: Long,
    connections: Int,
    destFile: File,
    onProgress: (AudioDownloadProgress) -> Unit,
  ) = coroutineScope {
    RandomAccessFile(destFile, "rw").use { raf ->
      raf.setLength(totalBytes)
    }

    val downloadedCounter = AtomicLong(0L)
    val chunkSize = totalBytes / connections

    val jobs = (0 until connections).map { index ->
      val start = index * chunkSize
      val end = if (index == connections - 1) totalBytes - 1L else ((index + 1) * chunkSize - 1L)
      async(Dispatchers.IO) {
        val partFile = File(destFile.parentFile, "${destFile.name}.part$index")
        val request = Request.Builder()
          .url(url)
          .addHeader("Range", "bytes=$start-$end")
          .get()
          .build()

        client.newCall(request).execute().use { response ->
          if (response.code != 206 && response.code != 200) {
            throw IllegalStateException("HTTP ${response.code} while downloading segment $index")
          }
          val stream = response.body?.byteStream()
            ?: throw IllegalStateException("Empty body for segment $index")
          val buffer = ByteArray(16 * 1024)
          partFile.outputStream().use { out ->
            while (true) {
              val read = stream.read(buffer)
              if (read <= 0) break
              out.write(buffer, 0, read)
              val totalRead = downloadedCounter.addAndGet(read.toLong())
              onProgress(
                AudioDownloadProgress(
                  downloadedBytes = totalRead.coerceAtMost(totalBytes),
                  totalBytes = totalBytes,
                  connectionsUsed = connections,
                  statusMessage = "Downloading ($connections parallel streams)…",
                )
              )
            }
          }
        }
        Triple(index, start, partFile)
      }
    }

    val partResults = jobs.awaitAll().sortedBy { it.first }
    RandomAccessFile(destFile, "rw").use { raf ->
      for ((_, offset, partFile) in partResults) {
        raf.seek(offset)
        partFile.inputStream().use { input ->
          val buffer = ByteArray(16 * 1024)
          while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            raf.write(buffer, 0, read)
          }
        }
        partFile.delete()
      }
    }
  }

  private fun downloadSingleStream(
    url: String,
    expectedBytes: Long,
    destFile: File,
    onProgress: (AudioDownloadProgress) -> Unit,
  ) {
    val request = Request.Builder().url(url).get().build()
    client.newCall(request).execute().use { response ->
      if (!response.isSuccessful) {
        throw IllegalStateException("Server returned HTTP ${response.code}")
      }
      val total = response.body?.contentLength()?.takeIf { it > 0L } ?: expectedBytes
      val stream = response.body?.byteStream()
        ?: throw IllegalStateException("Server returned an empty audio stream.")
      var received = 0L
      val buffer = ByteArray(16 * 1024)
      destFile.outputStream().use { out ->
        while (true) {
          val read = stream.read(buffer)
          if (read <= 0) break
          out.write(buffer, 0, read)
          received += read
          onProgress(
            AudioDownloadProgress(
              downloadedBytes = received,
              totalBytes = total,
              connectionsUsed = 1,
              statusMessage = "Downloading audio stream…",
            )
          )
        }
      }
    }
  }

  companion object {
    private const val MIN_SEGMENTED_BYTES = 64 * 1024L
    private val RESTRICTED_VIDEO_HOSTS = setOf(
      "youtube.com",
      "www.youtube.com",
      "m.youtube.com",
      "youtu.be",
      "music.youtube.com",
    )

    fun validateAudioUrl(rawUrl: String, allowGenericStreams: Boolean = false): String {
      val trimmed = rawUrl.trim()
      require(trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        "Enter a valid http:// or https:// audio URL."
      }
      val parsed = try {
        URI(trimmed)
      } catch (_: Exception) {
        throw IllegalArgumentException("Malformed URL format.")
      }
      val host = parsed.host?.lowercase(Locale.US).orEmpty()
      require(host.isNotBlank()) { "URL is missing a hostname." }
      if (!allowGenericStreams && host in RESTRICTED_VIDEO_HOSTS) {
        throw IllegalArgumentException(
          "Direct YouTube ripping is blocked by Google Play policy. Use a direct audio URL (.wav, .mp3, .flac, .ogg) or a self-hosted aria2 RPC endpoint."
        )
      }
      return trimmed
    }

    internal fun parseFileNameFromDisposition(disposition: String): String? {
      val match = Regex("""filename="?([^";]+)"?""", RegexOption.IGNORE_CASE).find(disposition)
      return match?.groupValues?.getOrNull(1)?.trim()
    }

    internal fun inferFileNameFromUrl(url: String): String {
      val path = try {
        URI(url).path.orEmpty()
      } catch (_: Exception) {
        ""
      }
      return path.substringAfterLast('/').substringBefore('?').ifBlank { "audio_track.wav" }
    }

    internal fun inferExtension(url: String, contentType: String?, fileName: String?): String {
      val candidate = (fileName ?: inferFileNameFromUrl(url)).substringAfterLast('.', "").lowercase(Locale.US)
      if (candidate in setOf("wav", "mp3", "flac", "ogg", "m4a", "aac")) {
        return candidate
      }
      val mime = contentType?.lowercase(Locale.US).orEmpty()
      return when {
        mime.contains("wav") -> "wav"
        mime.contains("mpeg") || mime.contains("mp3") -> "mp3"
        mime.contains("flac") -> "flac"
        mime.contains("ogg") -> "ogg"
        mime.contains("mp4") || mime.contains("aac") || mime.contains("m4a") -> "m4a"
        else -> "wav"
      }
    }
  }
}
