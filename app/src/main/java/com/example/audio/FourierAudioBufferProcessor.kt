package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.util.Log
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Raw uncompressed 16-bit PCM audio buffer produced from prompt-driven Fourier harmonic
 * synthesis or decoded from a Hugging Face generative audio bridge.
 */
data class RawAudioBuffer(
  val sampleRate: Int,
  val channelCount: Int,
  val pcmSamples: ShortArray,
  val durationMs: Long,
  val rmsAmplitude: Float,
  val peakAmplitude: Float,
  val fourierSpectrumBins: List<Float>,
  val spectralCentroidHz: Int,
  val detectedBpm: Int = 0,
  val bpmConfidence: Float = 0f,
  val integratedLufs: Float = -14.0f,
  val bridgeSource: String,
  val truePeakDbfs: Float = -1.0f,
) {
  val frameCount: Int
    get() = if (channelCount <= 0) pcmSamples.size else pcmSamples.size / channelCount

  val rmsDb: Float
    get() = if (rmsAmplitude <= 1e-5f) -60f else (20f * log10(rmsAmplitude)).coerceIn(-60f, 0f)

  fun toDiagnostics(): RawBufferDiagnostics = RawBufferDiagnostics(
    sampleRateHz = sampleRate,
    channelCount = channelCount,
    sampleCount = pcmSamples.size,
    durationMs = durationMs,
    rmsDb = rmsDb,
    spectralCentroidHz = spectralCentroidHz,
    fourierSpectrumBins = fourierSpectrumBins,
    detectedBpm = detectedBpm,
    bpmConfidence = bpmConfidence,
    integratedLufs = integratedLufs,
    bridgeSource = bridgeSource,
    truePeakDbfs = truePeakDbfs,
  )
}

data class RawBufferDiagnostics(
  val sampleRateHz: Int,
  val channelCount: Int,
  val sampleCount: Int,
  val durationMs: Long,
  val rmsDb: Float,
  val spectralCentroidHz: Int,
  val fourierSpectrumBins: List<Float>,
  val detectedBpm: Int = 0,
  val bpmConfidence: Float = 0f,
  val integratedLufs: Float = -14.0f,
  val bridgeSource: String,
  val truePeakDbfs: Float = -1.0f,
)

/**
 * Core Fourier DSP & raw PCM buffer engine:
 * - Additive Fourier harmonic series synthesis: sum_{n=1..N} w_n * sin(n * phase)
 * - Radix-2 Cooley-Tukey FFT spectral analysis (Hann-windowed) -> harmonic bins + spectral centroid
 * - Raw 16-bit PCM decoding from WAV & compressed audio formats (via MediaCodec)
 * - Direct AudioTrack PCM buffer streaming & WAV container serialization
 */
object FourierAudioBufferProcessor {

  private const val TAG = "FourierBufferProcessor"
  const val DEFAULT_SAMPLE_RATE = 44_100
  const val FFT_WINDOW_SIZE = 2048
  const val SPECTRUM_BAND_COUNT = 12

  /**
   * Evaluates a normalized band-limited Fourier harmonic series at [phase]:
   * f(phi) = sum_{n=1..harmonicCount} (1 / n^rolloff) * sin(n * phi)
   */
  fun fourierSeriesHarmonicSample(
    phase: Double,
    harmonicCount: Int,
    rolloff: Double,
  ): Double {
    var sum = 0.0
    var norm = 0.0
    for (n in 1..harmonicCount) {
      val weight = 1.0 / n.toDouble().pow(rolloff)
      sum += weight * sin(n * phase)
      norm += weight
    }
    return if (norm > 0.0) sum / norm else 0.0
  }

  /**
   * Computes RMS, peak amplitude, Hann-windowed Radix-2 FFT magnitude spectrum bins,
   * and spectral centroid (Hz) directly from raw PCM samples.
   */
  fun analyzeRawPcm(
    pcmSamples: ShortArray,
    sampleRate: Int,
    channelCount: Int,
    bridgeSource: String,
    bandCount: Int = SPECTRUM_BAND_COUNT,
  ): RawAudioBuffer {
    val safeRate = sampleRate.coerceAtLeast(8_000)
    val safeChannels = channelCount.coerceAtLeast(1)
    if (pcmSamples.isEmpty()) {
      return RawAudioBuffer(
        sampleRate = safeRate,
        channelCount = safeChannels,
        pcmSamples = ShortArray(0),
        durationMs = 0L,
        rmsAmplitude = 0f,
        peakAmplitude = 0f,
        fourierSpectrumBins = List(bandCount) { 0.1f },
        spectralCentroidHz = 0,
        bridgeSource = bridgeSource,
      )
    }

    var sumSq = 0.0
    var peak = 0
    val stride = max(1, pcmSamples.size / 16_384)
    var sampledCount = 0
    var idx = 0
    while (idx < pcmSamples.size) {
      val s = pcmSamples[idx].toInt()
      val absVal = abs(s)
      if (absVal > peak) peak = absVal
      val norm = s / 32768.0
      sumSq += norm * norm
      sampledCount++
      idx += stride
    }
    val rms = sqrt(sumSq / sampledCount.coerceAtLeast(1)).toFloat().coerceIn(0f, 1f)
    val peakNorm = (peak / 32768f).coerceIn(0f, 1f)

    val (bins, centroidHz) = computeFftSpectrum(
      pcmSamples = pcmSamples,
      sampleRate = safeRate,
      channelCount = safeChannels,
      bandCount = bandCount,
    )
    val (detectedBpm, bpmConfidence) = detectBpmFromPcm(
      pcmSamples = pcmSamples,
      sampleRate = safeRate,
      channelCount = safeChannels,
    )
    val integratedLufs = LoudnessNormalizerDsp.measureIntegratedLufs(
      pcmSamples = pcmSamples,
      sampleRate = safeRate,
      channelCount = safeChannels,
    )
    val truePeakDbfs = LoudnessNormalizerDsp.computeTruePeakDbfsFromPeakAmplitude(peakNorm)

    val frameCount = pcmSamples.size / safeChannels
    val durationMs = ((frameCount.toDouble() / safeRate) * 1000.0).toLong().coerceAtLeast(1L)

    return RawAudioBuffer(
      sampleRate = safeRate,
      channelCount = safeChannels,
      pcmSamples = pcmSamples,
      durationMs = durationMs,
      rmsAmplitude = rms,
      peakAmplitude = peakNorm,
      fourierSpectrumBins = bins,
      spectralCentroidHz = centroidHz,
      detectedBpm = detectedBpm,
      bpmConfidence = bpmConfidence,
      integratedLufs = integratedLufs,
      bridgeSource = bridgeSource,
      truePeakDbfs = truePeakDbfs,
    )
  }

  /**
   * Detects tempo in Beats Per Minute (BPM) and confidence (0f..1f) from raw PCM samples
   * using a log-compressed short-time onset-strength envelope and normalized lag autocorrelation
   * with sub-harmonic confirmation.
   */
  fun detectBpmFromPcm(
    pcmSamples: ShortArray,
    sampleRate: Int,
    channelCount: Int = 1,
    minBpm: Int = 60,
    maxBpm: Int = 176,
  ): Pair<Int, Float> {
    val safeChannels = channelCount.coerceAtLeast(1)
    val totalFrames = pcmSamples.size / safeChannels
    val windowSize = 1024
    val hopSize = 512
    if (sampleRate < 8_000 || totalFrames < sampleRate * 2 || totalFrames <= windowSize) {
      return 0 to 0f
    }

    val envLen = (totalFrames - windowSize) / hopSize + 1
    if (envLen < 32) return 0 to 0f

    val logEnergy = DoubleArray(envLen)
    for (m in 0 until envLen) {
      val frameStart = m * hopSize
      var sumSq = 0.0
      for (i in 0 until windowSize) {
        val base = (frameStart + i) * safeChannels
        var mono = 0.0
        for (c in 0 until safeChannels) {
          mono += pcmSamples[base + c] / 32768.0
        }
        mono /= safeChannels
        sumSq += mono * mono
      }
      logEnergy[m] = ln(1.0 + 32.0 * (sumSq / windowSize))
    }

    val rawFlux = DoubleArray(envLen)
    for (m in 1 until envLen) {
      rawFlux[m] = max(0.0, logEnergy[m] - logEnergy[m - 1])
    }

    val onset = DoubleArray(envLen)
    var onsetSum = 0.0
    val maRadius = 6
    for (m in 0 until envLen) {
      val lo = max(0, m - maRadius)
      val hi = min(envLen - 1, m + maRadius)
      var localSum = 0.0
      for (k in lo..hi) localSum += rawFlux[k]
      val localMean = localSum / (hi - lo + 1)
      val value = max(0.0, rawFlux[m] - localMean)
      onset[m] = value
      onsetSum += value
    }
    if (onsetSum <= 1e-6) return 0 to 0f

    fun autocorrAtLag(lagExact: Double): Double {
      val lagLow = floor(lagExact).toInt()
      val lagHigh = lagLow + 1
      val frac = lagExact - lagLow
      if (lagLow < 1 || lagHigh >= envLen - 2) return 0.0

      fun discreteCorr(lag: Int): Double {
        val overlap = envLen - lag
        if (overlap <= 4) return 0.0
        var sum = 0.0
        for (t in 0 until overlap) {
          sum += onset[t] * onset[t + lag]
        }
        return sum / overlap
      }

      val cLow = discreteCorr(lagLow)
      val cHigh = discreteCorr(lagHigh)
      return cLow * (1.0 - frac) + cHigh * frac
    }

    val envelopeRate = sampleRate.toDouble() / hopSize
    var bestBpm = 0
    var bestScore = -1.0
    var scoreSum = 0.0
    var candidateCount = 0

    for (bpm in minBpm..maxBpm) {
      val lag = (60.0 / bpm) * envelopeRate
      if (lag >= envLen * 0.65) continue
      val rPrimary = autocorrAtLag(lag)
      val rSubHarmonic = if (lag * 2.0 < envLen * 0.78) autocorrAtLag(lag * 2.0) else 0.0
      val tempoPrior = 1.0 - 0.06 * ((bpm - 115.0) / 65.0).pow(2.0)
      val score = (rPrimary + 0.35 * rSubHarmonic) * tempoPrior
      scoreSum += score
      candidateCount++
      if (score > bestScore) {
        bestScore = score
        bestBpm = bpm
      }
    }

    if (candidateCount == 0 || bestScore <= 1e-9 || bestBpm <= 0) {
      return 0 to 0f
    }
    val meanScore = scoreSum / candidateCount
    val confidence = ((bestScore - meanScore) / bestScore).toFloat().coerceIn(0f, 1f)
    return bestBpm to confidence
  }

  /**
   * Runs a Hann-windowed 2048-point Radix-2 Cooley-Tukey FFT on a representative PCM segment
   * and returns normalized logarithmic frequency-band magnitudes plus spectral centroid in Hz.
   */
  fun computeFftSpectrum(
    pcmSamples: ShortArray,
    sampleRate: Int,
    channelCount: Int = 1,
    bandCount: Int = SPECTRUM_BAND_COUNT,
  ): Pair<List<Float>, Int> {
    if (pcmSamples.isEmpty()) {
      return List(bandCount) { 0.1f } to 0
    }

    val n = FFT_WINDOW_SIZE
    val safeChannels = channelCount.coerceAtLeast(1)
    val frameCount = pcmSamples.size / safeChannels
    val startFrame = if (frameCount > n * 2) frameCount / 3 else 0

    val real = DoubleArray(n)
    val imag = DoubleArray(n)
    for (i in 0 until n) {
      val frameIdx = startFrame + i
      val sampleNorm = if (frameIdx < frameCount) {
        val sampleIdx = frameIdx * safeChannels
        pcmSamples[sampleIdx] / 32768.0
      } else {
        0.0
      }
      val hann = 0.5 * (1.0 - cos((2.0 * PI * i) / (n - 1)))
      real[i] = sampleNorm * hann
    }

    fftInPlace(real, imag)

    val halfN = n / 2
    val magnitudes = DoubleArray(halfN)
    var weightedFreqSum = 0.0
    var magSum = 0.0
    val nyquist = sampleRate / 2.0

    for (k in 1 until halfN) {
      val mag = sqrt(real[k] * real[k] + imag[k] * imag[k])
      magnitudes[k] = mag
      val freq = (k.toDouble() * sampleRate) / n
      weightedFreqSum += freq * mag
      magSum += mag
    }

    val centroidHz = if (magSum > 1e-9) (weightedFreqSum / magSum).toInt() else 0

    // Group FFT bins across log-spaced musical bands from 40 Hz to min(10 kHz, Nyquist)
    val minHz = 40.0
    val maxHz = min(10_000.0, nyquist).coerceAtLeast(minHz * 2.0)
    val rawBands = DoubleArray(bandCount)
    var maxBand = 1e-9

    for (b in 0 until bandCount) {
      val fStart = minHz * (maxHz / minHz).pow(b.toDouble() / bandCount)
      val fEnd = minHz * (maxHz / minHz).pow((b + 1).toDouble() / bandCount)
      val binStart = floor(fStart * n / sampleRate).toInt().coerceIn(1, halfN - 1)
      val binEnd = floor(fEnd * n / sampleRate).toInt().coerceIn(binStart + 1, halfN)
      var bandPeak = 0.0
      for (k in binStart until binEnd) {
        if (magnitudes[k] > bandPeak) bandPeak = magnitudes[k]
      }
      // Mild logarithmic compression for perceptual dynamic range
      val perceptual = ln(1.0 + bandPeak * 4.0)
      rawBands[b] = perceptual
      if (perceptual > maxBand) maxBand = perceptual
    }

    val normalizedBins = rawBands.map { value ->
      (value / maxBand).toFloat().coerceIn(0.08f, 1.0f)
    }
    return normalizedBins to centroidHz
  }

  /**
   * Decodes any saved WAV or compressed audio file into a [RawAudioBuffer] and extracts
   * waveform peaks for visualization.
   */
  fun decodeFileToRawBuffer(
    file: File,
    bridgeSource: String,
  ): RawAudioBuffer {
    if (!file.exists() || file.length() <= 0L) {
      return analyzeRawPcm(ShortArray(0), DEFAULT_SAMPLE_RATE, 1, bridgeSource)
    }

    val wavBuffer = parseWavFileToRawPcm(file, bridgeSource)
    if (wavBuffer != null) {
      return wavBuffer
    }

    val decodedCodec = decodeCompressedToRawPcm(file, bridgeSource)
    if (decodedCodec != null) {
      return decodedCodec
    }

    val durationMs = readDurationFallbackMs(file).coerceAtLeast(1_000L)
    val bytes = file.inputStream().use { input ->
      val cap = min(32_768L, file.length()).toInt()
      ByteArray(cap).also { input.read(it) }
    }
    val syntheticPreview = ShortArray(bytes.size) { idx ->
      ((bytes[idx].toInt()) shl 7).toShort()
    }
    val fallbackAnalysis = analyzeRawPcm(syntheticPreview, DEFAULT_SAMPLE_RATE, 1, bridgeSource)
    return fallbackAnalysis.copy(durationMs = durationMs)
  }

  /**
   * Computes normalized bar amplitudes from a raw PCM [ShortArray] for canvas waveform rendering.
   */
  fun extractWaveformPeaks(samples: ShortArray, barCount: Int = 56): List<Float> {
    if (samples.isEmpty() || barCount <= 0) return List(56) { 0.2f }
    val chunkSize = max(1, samples.size / barCount)
    val rawPeaks = FloatArray(barCount)
    var maxPeak = 1f

    for (bar in 0 until barCount) {
      val start = bar * chunkSize
      val end = min(samples.size, start + chunkSize)
      var peak = 0
      val stride = max(1, (end - start) / 128)
      var idx = start
      while (idx < end) {
        val v = abs(samples[idx].toInt())
        if (v > peak) peak = v
        idx += stride
      }
      val floatPeak = peak.toFloat()
      rawPeaks[bar] = floatPeak
      if (floatPeak > maxPeak) maxPeak = floatPeak
    }

    return rawPeaks.map { peak ->
      (peak / maxPeak).coerceIn(0.10f, 1.0f)
    }
  }

  /**
   * Crops a [RawAudioBuffer] to `[startMs, endMs]` aligned to frame/channel boundaries,
   * applies a short anti-click linear fade at both edges, and recomputes FFT spectral diagnostics.
   */
  fun trimRawBuffer(
    source: RawAudioBuffer,
    startMs: Long,
    endMs: Long,
    bridgeSource: String = source.bridgeSource,
  ): RawAudioBuffer {
    if (source.pcmSamples.isEmpty() || source.channelCount <= 0) {
      throw IllegalArgumentException("Audio track has no PCM samples available to trim.")
    }
    val channels = source.channelCount.coerceAtLeast(1)
    val totalFrames = source.pcmSamples.size / channels
    if (totalFrames < 2) {
      throw IllegalArgumentException("Audio track is too short to trim.")
    }

    val clampedStartMs = startMs.coerceIn(0L, source.durationMs)
    val clampedEndMs = endMs.coerceIn(clampedStartMs + 100L, max(source.durationMs, clampedStartMs + 100L))
    if (clampedEndMs <= clampedStartMs) {
      throw IllegalArgumentException("Crop end time must be after start time.")
    }

    val startFrame = ((clampedStartMs / 1000.0) * source.sampleRate).toInt()
      .coerceIn(0, max(0, totalFrames - 2))
    val endFrame = ((clampedEndMs / 1000.0) * source.sampleRate).toInt()
      .coerceIn(startFrame + 1, totalFrames)

    val startSample = startFrame * channels
    val endSample = endFrame * channels
    val cropped = source.pcmSamples.copyOfRange(startSample, endSample)

    // Apply subtle 10 ms anti-click ramp at crop boundaries
    val croppedFrames = cropped.size / channels
    val fadeFrames = min(croppedFrames / 6, (source.sampleRate * 0.01).toInt()).coerceAtLeast(0)
    for (f in 0 until fadeFrames) {
      val fadeInGain = f.toDouble() / fadeFrames.coerceAtLeast(1)
      val fadeOutGain = (fadeFrames - 1 - f).toDouble() / fadeFrames.coerceAtLeast(1)
      val inBase = f * channels
      val outBase = (croppedFrames - 1 - f) * channels
      for (c in 0 until channels) {
        cropped[inBase + c] = (cropped[inBase + c] * fadeInGain).toInt().toShort()
        cropped[outBase + c] = (cropped[outBase + c] * fadeOutGain).toInt().toShort()
      }
    }

    return analyzeRawPcm(
      pcmSamples = cropped,
      sampleRate = source.sampleRate,
      channelCount = channels,
      bridgeSource = bridgeSource,
    )
  }

  /**
   * Serializes a [RawAudioBuffer] to a standard RIFF PCM `.wav` file.
   */
  fun writeWavFile(file: File, buffer: RawAudioBuffer) {
    val pcmSamples = buffer.pcmSamples
    val bitsPerSample = 16
    val dataByteCount = pcmSamples.size * 2
    val totalDataLen = dataByteCount + 36
    val byteRate = buffer.sampleRate * buffer.channelCount * bitsPerSample / 8
    val blockAlign = buffer.channelCount * bitsPerSample / 8

    val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
      put("RIFF".toByteArray(Charsets.US_ASCII))
      putInt(totalDataLen)
      put("WAVE".toByteArray(Charsets.US_ASCII))
      put("fmt ".toByteArray(Charsets.US_ASCII))
      putInt(16)
      putShort(1)
      putShort(buffer.channelCount.toShort())
      putInt(buffer.sampleRate)
      putInt(byteRate)
      putShort(blockAlign.toShort())
      putShort(bitsPerSample.toShort())
      put("data".toByteArray(Charsets.US_ASCII))
      putInt(dataByteCount)
    }

    val sampleBytes = ByteBuffer.allocate(dataByteCount).order(ByteOrder.LITTLE_ENDIAN)
    for (sample in pcmSamples) {
      sampleBytes.putShort(sample)
    }

    BufferedOutputStream(FileOutputStream(file)).use { out ->
      out.write(header.array())
      out.write(sampleBytes.array())
      out.flush()
    }
  }

  /**
   * Builds an Android [AudioTrack] configured for direct raw PCM buffer streaming.
   */
  fun buildRawPcmAudioTrack(buffer: RawAudioBuffer): AudioTrack {
    val channelMask = if (buffer.channelCount >= 2) {
      AudioFormat.CHANNEL_OUT_STEREO
    } else {
      AudioFormat.CHANNEL_OUT_MONO
    }
    val minBufBytes = AudioTrack.getMinBufferSize(
      buffer.sampleRate,
      channelMask,
      AudioFormat.ENCODING_PCM_16BIT,
    ).coerceAtLeast(4096)

    return AudioTrack.Builder()
      .setAudioAttributes(
        AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_MEDIA)
          .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
          .build()
      )
      .setAudioFormat(
        AudioFormat.Builder()
          .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
          .setSampleRate(buffer.sampleRate)
          .setChannelMask(channelMask)
          .build()
      )
      .setTransferMode(AudioTrack.MODE_STREAM)
      .setBufferSizeInBytes(minBufBytes * 2)
      .build()
  }

  private fun parseWavFileToRawPcm(file: File, bridgeSource: String): RawAudioBuffer? {
    return try {
      val bytes = file.readBytes()
      if (bytes.size <= 44) return null
      val riff = String(bytes, 0, 4, Charsets.US_ASCII)
      val wave = String(bytes, 8, 4, Charsets.US_ASCII)
      if (riff != "RIFF" || wave != "WAVE") return null

      val headerBuf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
      var offset = 12
      var channels = 1
      var sampleRate = DEFAULT_SAMPLE_RATE
      var bitsPerSample = 16
      var dataStart = -1
      var dataSize = 0

      while (offset + 8 <= bytes.size) {
        val chunkId = String(bytes, offset, 4, Charsets.US_ASCII)
        val chunkSize = headerBuf.getInt(offset + 4).coerceAtLeast(0)
        val payloadOffset = offset + 8
        if (chunkId == "fmt " && payloadOffset + 16 <= bytes.size) {
          val audioFormat = headerBuf.getShort(payloadOffset).toInt() and 0xFFFF
          if (audioFormat != 1) return null // Not uncompressed PCM
          channels = (headerBuf.getShort(payloadOffset + 2).toInt() and 0xFFFF).coerceAtLeast(1)
          sampleRate = headerBuf.getInt(payloadOffset + 4).coerceAtLeast(8_000)
          bitsPerSample = (headerBuf.getShort(payloadOffset + 14).toInt() and 0xFFFF)
        } else if (chunkId == "data") {
          dataStart = payloadOffset
          dataSize = min(chunkSize, bytes.size - payloadOffset).coerceAtLeast(0)
          break
        }
        offset = payloadOffset + chunkSize + (chunkSize and 1)
      }

      if (dataStart < 0 || dataSize < 2 || bitsPerSample != 16) return null
      val sampleCount = dataSize / 2
      val pcm = ShortArray(sampleCount)
      val dataView = ByteBuffer.wrap(bytes, dataStart, dataSize).order(ByteOrder.LITTLE_ENDIAN)
      for (i in 0 until sampleCount) {
        pcm[i] = dataView.getShort()
      }
      analyzeRawPcm(pcm, sampleRate, channels, bridgeSource)
    } catch (e: Exception) {
      Log.w(TAG, "WAV header parse failed for ${file.name}", e)
      null
    }
  }

  private fun decodeCompressedToRawPcm(file: File, bridgeSource: String): RawAudioBuffer? {
    val extractor = MediaExtractor()
    var codec: MediaCodec? = null
    return try {
      extractor.setDataSource(file.absolutePath)
      var audioTrackIdx = -1
      var format: MediaFormat? = null
      for (i in 0 until extractor.trackCount) {
        val trackFmt = extractor.getTrackFormat(i)
        val mime = trackFmt.getString(MediaFormat.KEY_MIME).orEmpty()
        if (mime.startsWith("audio/")) {
          audioTrackIdx = i
          format = trackFmt
          break
        }
      }
      if (audioTrackIdx < 0 || format == null) return null

      extractor.selectTrack(audioTrackIdx)
      val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
      var outSampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
        format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
      } else {
        DEFAULT_SAMPLE_RATE
      }
      var outChannels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
        format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
      } else {
        1
      }

      codec = MediaCodec.createDecoderByType(mime).apply {
        configure(format, null, null, 0)
        start()
      }

      val collected = ArrayList<Short>(DEFAULT_SAMPLE_RATE * 4)
      val maxSamples = DEFAULT_SAMPLE_RATE * 180 * outChannels
      val bufferInfo = MediaCodec.BufferInfo()
      var inputDone = false
      var outputDone = false

      while (!outputDone && collected.size < maxSamples) {
        if (!inputDone) {
          val inputIdx = codec.dequeueInputBuffer(10_000L)
          if (inputIdx >= 0) {
            val inputBuf = codec.getInputBuffer(inputIdx)
            if (inputBuf != null) {
              val size = extractor.readSampleData(inputBuf, 0)
              if (size < 0) {
                codec.queueInputBuffer(inputIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                inputDone = true
              } else {
                codec.queueInputBuffer(inputIdx, 0, size, extractor.sampleTime, 0)
                extractor.advance()
              }
            }
          }
        }

        val outIdx = codec.dequeueOutputBuffer(bufferInfo, 10_000L)
        if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
          val outFmt = codec.outputFormat
          if (outFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
            outSampleRate = outFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
          }
          if (outFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
            outChannels = outFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
          }
        } else if (outIdx >= 0) {
          val outBuf = codec.getOutputBuffer(outIdx)
          if (outBuf != null && bufferInfo.size > 0) {
            outBuf.position(bufferInfo.offset)
            outBuf.limit(bufferInfo.offset + bufferInfo.size)
            val shortBuf = outBuf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            while (shortBuf.hasRemaining() && collected.size < maxSamples) {
              collected.add(shortBuf.get())
            }
          }
          codec.releaseOutputBuffer(outIdx, false)
          if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            outputDone = true
          }
        }
      }

      if (collected.isEmpty()) return null
      analyzeRawPcm(collected.toShortArray(), outSampleRate, outChannels, bridgeSource)
    } catch (e: Exception) {
      Log.w(TAG, "MediaCodec raw PCM decode skipped for ${file.name}", e)
      null
    } finally {
      try {
        codec?.stop()
        codec?.release()
      } catch (_: Exception) {
      }
      try {
        extractor.release()
      } catch (_: Exception) {
      }
    }
  }

  private fun readDurationFallbackMs(file: File): Long {
    val retriever = MediaMetadataRetriever()
    return try {
      retriever.setDataSource(file.absolutePath)
      retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
    } catch (_: Exception) {
      0L
    } finally {
      try {
        retriever.release()
      } catch (_: Exception) {
      }
    }
  }

  internal fun fftInPlace(real: DoubleArray, imag: DoubleArray) {
    val n = real.size
    var j = 0
    for (i in 1 until n) {
      var bit = n shr 1
      while ((j and bit) != 0) {
        j = j xor bit
        bit = bit shr 1
      }
      j = j xor bit
      if (i < j) {
        val tr = real[i]
        real[i] = real[j]
        real[j] = tr
        val ti = imag[i]
        imag[i] = imag[j]
        imag[j] = ti
      }
    }

    var len = 2
    while (len <= n) {
      val halfLen = len shr 1
      val angle = -2.0 * PI / len
      val wLenReal = cos(angle)
      val wLenImag = sin(angle)
      var i = 0
      while (i < n) {
        var wReal = 1.0
        var wImag = 0.0
        for (k in 0 until halfLen) {
          val evenIdx = i + k
          val oddIdx = evenIdx + halfLen
          val tReal = real[oddIdx] * wReal - imag[oddIdx] * wImag
          val tImag = real[oddIdx] * wImag + imag[oddIdx] * wReal
          real[oddIdx] = real[evenIdx] - tReal
          imag[oddIdx] = imag[evenIdx] - tImag
          real[evenIdx] += tReal
          imag[evenIdx] += tImag

          val nextWReal = wReal * wLenReal - wImag * wLenImag
          wImag = wReal * wLenImag + wImag * wLenReal
          wReal = nextWReal
        }
        i += len
      }
      len = len shl 1
    }
  }
}
