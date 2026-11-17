package com.example.audio

import android.util.Log
import com.example.model.GeneratedTrack
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

enum class HfMusicSpace(
  val displayName: String,
  val spaceId: String,
  val baseUrl: String,
  val fallbackBaseUrls: List<String> = emptyList(),
  val modelFamily: String,
) {
  AUDIO_LDM(
    displayName = "HF AudioLDM",
    spaceId = "haoheliu/audioldm-text-to-audio-generation",
    baseUrl = "https://haoheliu-audioldm-text-to-audio-generation.hf.space",
    fallbackBaseUrls = listOf("https://cvssp-audioldm-s-full-v2.hf.space"),
    modelFamily = "Diffusion + STFT",
  ),
  MUSICGEN(
    displayName = "HF MusicGen",
    spaceId = "facebook/MusicGen",
    baseUrl = "https://facebook-musicgen.hf.space",
    modelFamily = "Transformer Codec",
  ),
  STABLE_AUDIO(
    displayName = "HF Stable Audio",
    spaceId = "artificialguybr/Stable-Audio-Open-Zero",
    baseUrl = "https://artificialguybr-stable-audio-open-zero.hf.space",
    modelFamily = "Latent Diffusion",
  ),
}

data class HfBridgeResult(
  val track: GeneratedTrack,
  val rawBuffer: RawAudioBuffer,
)

/**
 * Zero-API-key client for public Hugging Face Spaces running Gradio REST + SSE inference.
 * Inspects `/gradio_api/info` dynamically so parameter order and endpoint names stay accurate.
 */
class HuggingFaceSpaceClient {

  private val client = OkHttpClient.Builder()
    .connectTimeout(25, TimeUnit.SECONDS)
    .writeTimeout(25, TimeUnit.SECONDS)
    .readTimeout(120, TimeUnit.SECONDS)
    .callTimeout(150, TimeUnit.SECONDS)
    .build()

  suspend fun generateMusic(
    prompt: String,
    space: HfMusicSpace,
    outputDir: File,
    targetLufs: Float = LoudnessNormalizerDsp.DEFAULT_TARGET_LUFS,
    applyPostGain: Boolean = true,
  ): GeneratedTrack = generateMusicWithRawBuffer(prompt, space, outputDir, targetLufs, applyPostGain).track

  suspend fun generateMusicWithRawBuffer(
    prompt: String,
    space: HfMusicSpace,
    outputDir: File,
    targetLufs: Float = LoudnessNormalizerDsp.DEFAULT_TARGET_LUFS,
    applyPostGain: Boolean = true,
  ): HfBridgeResult = withContext(Dispatchers.IO) {
    if (!outputDir.exists()) {
      outputDir.mkdirs()
    }

    val cleanPrompt = prompt.trim().ifEmpty { "Lo-fi hip hop beat with soothing piano melody" }

    // 1. Discover endpoint & parameter schema from primary or fallback Gradio Space info
    val (activeBaseUrl, apiPrefix, infoJson) = fetchGradioInfoWithFallback(space)
    val (endpointName, dataArray) = buildGradioPayloadFromInfo(infoJson, cleanPrompt)

    // 2. Submit queued prediction via POST {activeBaseUrl}{apiPrefix}/call{endpointName}
    val callUrl = "$activeBaseUrl$apiPrefix/call$endpointName"
    val requestBody = JSONObject().apply { put("data", dataArray) }.toString()
      .toRequestBody("application/json; charset=utf-8".toMediaType())

    val postRequest = Request.Builder()
      .url(callUrl)
      .post(requestBody)
      .build()

    val eventId = client.newCall(postRequest).execute().use { response ->
      val body = response.body?.string().orEmpty()
      if (!response.isSuccessful) {
        throw IllegalStateException(
          "Space ${space.spaceId} returned HTTP ${response.code}: ${body.take(160)}"
        )
      }
      JSONObject(body).optString("event_id").takeIf { it.isNotBlank() }
        ?: throw IllegalStateException("Space ${space.spaceId} did not return an event_id.")
    }

    // 3. Stream SSE completion result via GET {callUrl}/{eventId}
    val sseRequest = Request.Builder()
      .url("$callUrl/$eventId")
      .addHeader("Accept", "text/event-stream")
      .get()
      .build()

    val audioUrl = client.newCall(sseRequest).execute().use { response ->
      if (!response.isSuccessful) {
        val err = response.body?.string().orEmpty()
        throw IllegalStateException(
          "Space ${space.spaceId} SSE error (HTTP ${response.code}): ${err.take(160)}"
        )
      }
      val stream = response.body?.byteStream()
        ?: throw IllegalStateException("Empty SSE stream from ${space.spaceId}.")

      var currentEvent = ""
      var completedDataLine: String? = null
      var errorDataLine: String? = null

      BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
        while (true) {
          val line = reader.readLine() ?: break
          when {
            line.startsWith("event:") -> currentEvent = line.removePrefix("event:").trim()
            line.startsWith("data:") -> {
              val payload = line.removePrefix("data:").trim()
              if (currentEvent == "complete") {
                completedDataLine = payload
                break
              } else if (currentEvent == "error") {
                errorDataLine = payload
                break
              }
            }
          }
        }
      }

      if (errorDataLine != null) {
        val parsedErr = parseGradioError(errorDataLine!!)
        throw IllegalStateException("Space ${space.spaceId} failed: $parsedErr")
      }

      val dataStr = completedDataLine
        ?: throw IllegalStateException("Space ${space.spaceId} closed stream without audio output.")

      extractAudioUrlFromSseData(dataStr, activeBaseUrl, apiPrefix)
        ?: throw IllegalStateException("No audio URL found in ${space.spaceId} response.")
    }

    // 4. Download the generated audio file locally
    val downloadRequest = Request.Builder().url(audioUrl).get().build()
    val (audioBytes, extension) = client.newCall(downloadRequest).execute().use { dlResponse ->
      if (!dlResponse.isSuccessful) {
        throw IllegalStateException("Failed to download generated audio (HTTP ${dlResponse.code}).")
      }
      val bytes = dlResponse.body?.bytes() ?: ByteArray(0)
      if (bytes.size < 128) {
        throw IllegalStateException("Downloaded audio file from ${space.spaceId} was empty.")
      }
      val contentType = dlResponse.header("Content-Type").orEmpty().lowercase()
      bytes to detectExtension(audioUrl, contentType, bytes)
    }

    val id = UUID.randomUUID().toString()
    val downloadedFile = File(outputDir, "hf_${space.name.lowercase()}_$id.$extension")
    downloadedFile.writeBytes(audioBytes)

    val decodedBuffer = FourierAudioBufferProcessor.decodeFileToRawBuffer(
      file = downloadedFile,
      bridgeSource = "${space.displayName} (${space.modelFamily})",
    )
    // Post-process decoded audio with LUFS loudness normalization for consistent library volume
    val (finalFile, finalExt, rawBuffer) = if (applyPostGain && decodedBuffer.pcmSamples.isNotEmpty()) {
      val norm = LoudnessNormalizerDsp.normalizePcm(
        pcmSamples = decodedBuffer.pcmSamples,
        sampleRate = decodedBuffer.sampleRate,
        channelCount = decodedBuffer.channelCount,
        targetLufs = targetLufs,
      )
      val analyzedNorm = FourierAudioBufferProcessor.analyzeRawPcm(
        pcmSamples = norm.normalizedSamples,
        sampleRate = decodedBuffer.sampleRate,
        channelCount = decodedBuffer.channelCount,
        bridgeSource = decodedBuffer.bridgeSource,
      )
      val wavOut = File(outputDir, "hf_${space.name.lowercase()}_$id.wav")
      FourierAudioBufferProcessor.writeWavFile(wavOut, analyzedNorm)
      if (downloadedFile.absolutePath != wavOut.absolutePath) {
        downloadedFile.delete()
      }
      Triple(wavOut, "WAV", analyzedNorm)
    } else {
      Triple(downloadedFile, extension.uppercase(), decodedBuffer)
    }

    val analysis = AudioWaveformExtractor.analyzeFile(finalFile)
    val waveformPeaks = if (rawBuffer.pcmSamples.isNotEmpty()) {
      FourierAudioBufferProcessor.extractWaveformPeaks(rawBuffer.pcmSamples)
    } else {
      analysis.waveformPeaks
    }
    val resolvedBpm = rawBuffer.detectedBpm.takeIf { it in 55..180 }
      ?: extractBpmOrDefault(cleanPrompt)
    val track = GeneratedTrack(
      id = id,
      title = buildTitle(cleanPrompt, space.displayName),
      prompt = cleanPrompt,
      genre = space.modelFamily,
      bpm = resolvedBpm,
      musicalKey = finalExt,
      durationMs = rawBuffer.durationMs.takeIf { it > 1_000L } ?: analysis.durationMs,
      filePath = finalFile.absolutePath,
      fileSizeBytes = finalFile.length(),
      waveformPeaks = waveformPeaks,
      createdAtEpochMs = System.currentTimeMillis(),
      sourceLabel = space.displayName,
      category = space.displayName,
      tags = listOf("ML", space.displayName, space.modelFamily),
    )
    return@withContext HfBridgeResult(track = track, rawBuffer = rawBuffer)
  }

  private fun fetchGradioInfoWithFallback(space: HfMusicSpace): Triple<String, String, JSONObject> {
    val baseUrls = listOf(space.baseUrl) + space.fallbackBaseUrls
    var lastError: String? = null
    for (baseUrl in baseUrls) {
      val candidates = listOf("/gradio_api" to "$baseUrl/gradio_api/info", "" to "$baseUrl/info")
      for ((prefix, url) in candidates) {
        try {
          val req = Request.Builder().url(url).get().build()
          client.newCall(req).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (res.isSuccessful && text.trimStart().startsWith("{")) {
              return Triple(baseUrl, prefix, JSONObject(text))
            }
            lastError = "HTTP ${res.code}"
          }
        } catch (e: Exception) {
          lastError = e.localizedMessage
          Log.w(TAG, "Info probe failed for $url", e)
        }
      }
    }
    throw IllegalStateException(
      "Hugging Face Space is currently asleep or unreachable ($lastError). Try another model or Fourier Synth."
    )
  }

  private fun detectExtension(url: String, contentType: String, bytes: ByteArray): String {
    if (bytes.size >= 4) {
      val header = String(bytes, 0, 4, Charsets.US_ASCII)
      if (header == "RIFF") return "wav"
      if (header == "fLaC") return "flac"
      if (header == "OggS") return "ogg"
      if (header.startsWith("ID3")) return "mp3"
    }
    val urlLower = url.lowercase()
    return when {
      urlLower.contains(".wav") || contentType.contains("wav") -> "wav"
      urlLower.contains(".flac") || contentType.contains("flac") -> "flac"
      urlLower.contains(".mp3") || contentType.contains("mpeg") -> "mp3"
      urlLower.contains(".ogg") || contentType.contains("ogg") -> "ogg"
      else -> "wav"
    }
  }

  private fun parseGradioError(errorData: String): String {
    return try {
      val trimmed = errorData.trim()
      if (trimmed.startsWith("{")) {
        JSONObject(trimmed).optString("error").ifBlank { trimmed }
      } else {
        trimmed.take(180)
      }
    } catch (_: Exception) {
      errorData.take(180)
    }
  }

  private fun extractBpmOrDefault(prompt: String): Int {
    val match = Regex("""(\d{2,3})\s*bpm""", RegexOption.IGNORE_CASE).find(prompt)
    return match?.groupValues?.getOrNull(1)?.toIntOrNull()?.coerceIn(55, 180) ?: 100
  }

  private fun buildTitle(prompt: String, fallback: String): String {
    val words = prompt
      .replace(Regex("""[^A-Za-z0-9\s-]"""), " ")
      .trim()
      .split(Regex("""\s+"""))
      .filter { it.isNotBlank() }
      .take(4)
      .joinToString(" ") { word ->
        word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
      }
    return words.ifBlank { "$fallback Creation" }
  }

  companion object {
    private const val TAG = "HuggingFaceSpaceClient"

    /**
     * Parses a Gradio `/info` JSON payload, selects the primary generation endpoint,
     * and constructs a positional `data` array using parameter defaults + the user's prompt.
     */
    fun buildGradioPayloadFromInfo(
      infoJson: JSONObject,
      prompt: String,
    ): Pair<String, JSONArray> {
      val namedEndpoints = infoJson.optJSONObject("named_endpoints")
        ?: throw IllegalStateException("Gradio Space returned no named_endpoints.")

      val endpointNames = buildList {
        val keys = namedEndpoints.keys()
        while (keys.hasNext()) add(keys.next())
      }
      if (endpointNames.isEmpty()) {
        throw IllegalStateException("Gradio Space has no callable endpoints.")
      }

      val preferred = listOf("/predict", "/generate", "/text2audio", "/generate_audio", "/run")
      val chosenEndpoint = preferred.firstOrNull { it in endpointNames }
        ?: endpointNames.firstOrNull { name ->
          val ep = namedEndpoints.optJSONObject(name)
          val returns = ep?.optJSONArray("returns")
          returns != null && returns.length() > 0
        }
        ?: endpointNames.first()

      val endpointObj = namedEndpoints.getJSONObject(chosenEndpoint)
      val params = endpointObj.optJSONArray("parameters") ?: JSONArray()
      val dataArray = JSONArray()
      var promptInjected = false

      for (i in 0 until params.length()) {
        val p = params.getJSONObject(i)
        val paramName = p.optString("parameter_name").lowercase()
        val label = p.optString("label").lowercase()
        val typeDesc = p.optJSONObject("type")?.optString("type")?.lowercase().orEmpty()
        val hasDefault = p.has("parameter_default") && !p.isNull("parameter_default")
        val defaultVal = if (hasDefault) p.get("parameter_default") else JSONObject.NULL

        val isPromptParam = !promptInjected &&
          (typeDesc == "string" || typeDesc.isEmpty()) &&
          (paramName.contains("prompt") || paramName.contains("text") ||
            label.contains("prompt") || label.contains("describe") || i == 0)

        val isDurationParam =
          (paramName.contains("duration") || paramName.contains("second") ||
            label.contains("duration") || label.contains("seconds")) &&
            (typeDesc == "number" || typeDesc == "integer")

        when {
          isPromptParam -> {
            dataArray.put(prompt)
            promptInjected = true
          }
          isDurationParam -> {
            val defaultNum = (defaultVal as? Number)?.toDouble() ?: 8.0
            dataArray.put(defaultNum.coerceIn(5.0, 12.0))
          }
          hasDefault -> dataArray.put(defaultVal)
          typeDesc == "string" -> dataArray.put("")
          typeDesc == "number" || typeDesc == "integer" -> dataArray.put(0)
          typeDesc == "boolean" -> dataArray.put(false)
          else -> dataArray.put(JSONObject.NULL)
        }
      }

      if (!promptInjected && dataArray.length() == 0) {
        dataArray.put(prompt)
      }

      return chosenEndpoint to dataArray
    }

    /**
     * Extracts an audio download URL from Gradio SSE `complete` JSON data.
     */
    fun extractAudioUrlFromSseData(
      sseData: String,
      baseUrl: String,
      apiPrefix: String,
    ): String? {
      val root = JSONArray(sseData)
      return findAudioUrlInJson(root, baseUrl, apiPrefix)
    }

    private fun findAudioUrlInJson(
      node: Any?,
      baseUrl: String,
      apiPrefix: String,
    ): String? {
      when (node) {
        is JSONArray -> {
          for (i in 0 until node.length()) {
            val found = findAudioUrlInJson(node.get(i), baseUrl, apiPrefix)
            if (found != null) return found
          }
        }
        is JSONObject -> {
          val url = node.optString("url")
          if (url.startsWith("http://") || url.startsWith("https://")) {
            return url
          }
          val path = node.optString("path")
          if (path.isNotBlank()) {
            val cleanPrefix = if (apiPrefix.isNotEmpty()) apiPrefix else ""
            val encodedPath = path.trimStart('/')
            return "$baseUrl$cleanPrefix/file=$encodedPath"
          }
          val keys = node.keys()
          while (keys.hasNext()) {
            val key = keys.next()
            val found = findAudioUrlInJson(node.get(key), baseUrl, apiPrefix)
            if (found != null) return found
          }
        }
        is String -> {
          if (node.startsWith("http://") || node.startsWith("https://")) {
            return node
          }
        }
      }
      return null
    }
  }
}
