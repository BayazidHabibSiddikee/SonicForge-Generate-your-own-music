package com.example.audio

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pure-Kotlin lossless FLAC encoder (Free Lossless Audio Codec bitstream):
 * - Emits valid `"fLaC"` stream marker + `STREAMINFO` block (including MD5 PCM checksum)
 * - Encodes audio frames with FIXED linear predictors (order 0..4) + Golomb-Rice residual
 *   coding or VERBATIM fallback (picking shortest per frame), complete with CRC-8 frame header
 *   checksums and CRC-16 frame footers.
 */
object FlacAudioEncoder {

  private const val BLOCK_SIZE = 4096

  fun encodeBufferToFlacBytes(
    buffer: RawAudioBuffer,
    compressionLevel: Int = 5,
  ): ByteArray {
    if (buffer.pcmSamples.isEmpty() || buffer.frameCount <= 0) {
      throw IllegalArgumentException("No PCM samples available to encode as FLAC.")
    }

    val channels = buffer.channelCount.coerceIn(1, 2)
    val sampleRate = buffer.sampleRate.coerceIn(8_000, 192_000)
    val totalFrames = buffer.frameCount
    val bitsPerSample = 16
    val comp = compressionLevel.coerceIn(0, 8)

    val out = ByteArrayOutputStream(max(4096, buffer.pcmSamples.size))
    // 1. Magic marker
    out.write(byteArrayOf('f'.code.toByte(), 'L'.code.toByte(), 'a'.code.toByte(), 'C'.code.toByte()))

    // 2. STREAMINFO metadata block (last = 1, type = 0, size = 34)
    val streamInfo = buildStreamInfoBlock(
      sampleRate = sampleRate,
      channels = channels,
      bitsPerSample = bitsPerSample,
      totalSamplesPerChannel = totalFrames.toLong(),
      pcmSamples = buffer.pcmSamples,
      totalFrames = totalFrames,
    )
    out.write(0x80) // last metadata block flag + type 0 (STREAMINFO)
    out.write(0x00)
    out.write(0x00)
    out.write(34)
    out.write(streamInfo)

    // 3. Encode audio frames
    var frameNumber = 0
    var frameOffset = 0
    while (frameOffset < totalFrames) {
      val currentBlockSize = min(BLOCK_SIZE, totalFrames - frameOffset)
      val channelSlices = Array(channels) { ch ->
        IntArray(currentBlockSize) { i ->
          buffer.pcmSamples[(frameOffset + i) * channels + ch].toInt()
        }
      }
      val encodedFrame = encodeFrame(
        channelSlices = channelSlices,
        blockSize = currentBlockSize,
        sampleRate = sampleRate,
        channels = channels,
        bitsPerSample = bitsPerSample,
        frameNumber = frameNumber,
        compressionLevel = comp,
      )
      out.write(encodedFrame)
      frameOffset += currentBlockSize
      frameNumber++
    }

    return out.toByteArray()
  }

  private fun buildStreamInfoBlock(
    sampleRate: Int,
    channels: Int,
    bitsPerSample: Int,
    totalSamplesPerChannel: Long,
    pcmSamples: ShortArray,
    totalFrames: Int,
  ): ByteArray {
    val info = ByteArray(34)
    // min block size & max block size
    val minBlk = min(BLOCK_SIZE, totalFrames).coerceAtLeast(16)
    val maxBlk = BLOCK_SIZE.coerceAtLeast(minBlk)
    info[0] = ((minBlk ushr 8) and 0xFF).toByte()
    info[1] = (minBlk and 0xFF).toByte()
    info[2] = ((maxBlk ushr 8) and 0xFF).toByte()
    info[3] = (maxBlk and 0xFF).toByte()
    // minFrameSize (24b) = 0, maxFrameSize (24b) = 0 -> bytes 4..9 remain 0

    // Bytes 10..17 pack: sampleRate(20) | (channels-1)(3) | (bps-1)(5) | totalSamples(36)
    val packedMeta =
      ((sampleRate.toLong() and 0xFFFFFL) shl 44) or
        (((channels - 1).toLong() and 0x7L) shl 41) or
        (((bitsPerSample - 1).toLong() and 0x1FL) shl 36) or
        (totalSamplesPerChannel and 0xFFFFFFFFFL)
    for (i in 0 until 8) {
      info[10 + i] = ((packedMeta ushr ((7 - i) * 8)) and 0xFFL).toByte()
    }

    // 16-byte MD5 digest of little-endian 16-bit interleaved PCM samples
    val md5 = MessageDigest.getInstance("MD5")
    val leChunk = ByteArray(min(8192, pcmSamples.size * 2))
    var offset = 0
    while (offset < pcmSamples.size) {
      var byteCount = 0
      while (offset < pcmSamples.size && byteCount + 1 < leChunk.size) {
        val s = pcmSamples[offset].toInt()
        leChunk[byteCount++] = (s and 0xFF).toByte()
        leChunk[byteCount++] = ((s ushr 8) and 0xFF).toByte()
        offset++
      }
      md5.update(leChunk, 0, byteCount)
    }
    md5.digest().copyInto(info, destinationOffset = 18)
    return info
  }

  private fun encodeFrame(
    channelSlices: Array<IntArray>,
    blockSize: Int,
    sampleRate: Int,
    channels: Int,
    bitsPerSample: Int,
    frameNumber: Int,
    compressionLevel: Int,
  ): ByteArray {
    val writer = FlacBitWriter(max(256, blockSize * channels * 2 + 64))

    // Sync code 0x3FFE (14 bits) + reserved (1 bit = 0) + blocking strategy (1 bit = 0 fixed)
    writer.writeBits(0xFFF8, 16)

    // Block size code (4 bits) & sample rate code (4 bits)
    val blockSizeCode = when (blockSize) {
      4096 -> 0xC
      2048 -> 0xB
      1024 -> 0xA
      512 -> 0x9
      256 -> 0x8
      else -> 0x7 // uncommon 16-bit (blockSize - 1) appended after frame number
    }
    val sampleRateCode = when (sampleRate) {
      88_200 -> 0x1
      176_400 -> 0x2
      192_000 -> 0x3
      8_000 -> 0x4
      16_000 -> 0x5
      22_050 -> 0x6
      24_000 -> 0x7
      32_000 -> 0x8
      44_100 -> 0x9
      48_000 -> 0xA
      96_000 -> 0xB
      else -> 0x0 // Read from STREAMINFO
    }
    writer.writeBits((blockSizeCode shl 4) or sampleRateCode, 8)

    // Channel assignment (4 bits): 0=mono, 1=independent stereo L+R
    val channelAssignment = if (channels == 1) 0x0 else 0x1
    // Sample size code (3 bits): 0x4 = 16-bit; + reserved 0 bit
    val sampleSizeCode = if (bitsPerSample == 16) 0x4 else 0x0
    writer.writeBits((channelAssignment shl 4) or (sampleSizeCode shl 1), 8)

    // UTF-8 coded frame number
    writer.writeUtf8UInt(frameNumber.toLong())

    if (blockSizeCode == 0x7) {
      writer.writeBits(blockSize - 1, 16)
    }

    // Header CRC-8 over header bytes written so far
    val crc8 = writer.computeCrc8SoFar()
    writer.writeBits(crc8, 8)

    // Subframes per channel
    for (ch in 0 until channels) {
      encodeSubframe(
        writer = writer,
        samples = channelSlices[ch],
        blockSize = blockSize,
        bitsPerSample = bitsPerSample,
        compressionLevel = compressionLevel,
      )
    }

    // Align to byte boundary & append CRC-16 over entire frame
    writer.alignToByte()
    val crc16 = writer.computeCrc16SoFar()
    writer.writeBits(crc16, 16)
    return writer.toByteArray()
  }

  private fun encodeSubframe(
    writer: FlacBitWriter,
    samples: IntArray,
    blockSize: Int,
    bitsPerSample: Int,
    compressionLevel: Int,
  ) {
    // Check constant subframe
    val first = samples[0]
    var allEqual = true
    for (i in 1 until blockSize) {
      if (samples[i] != first) {
        allEqual = false
        break
      }
    }
    if (allEqual) {
      // Subframe header: 0 (pad) | 000000 (CONSTANT) | 0 (no wasted bits)
      writer.writeBits(0x00, 8)
      writer.writeSignedBits(first, bitsPerSample)
      return
    }

    if (compressionLevel <= 1 || blockSize < 16) {
      writeVerbatimSubframe(writer, samples, blockSize, bitsPerSample)
      return
    }

    // Try FIXED linear predictors (order 0..maxOrder) & compare total bit cost against VERBATIM
    val maxOrder = if (compressionLevel >= 6) 4 else 2
    var bestOrder = -1
    var bestRiceParam = 0
    var bestBits = blockSize * bitsPerSample + 8
    val residuals = IntArray(blockSize)

    for (order in 0..min(maxOrder, 4)) {
      if (blockSize <= order) continue
      val residualCount = blockSize - order
      var absSum = 0L
      for (i in order until blockSize) {
        val pred = when (order) {
          0 -> 0
          1 -> samples[i - 1]
          2 -> 2 * samples[i - 1] - samples[i - 2]
          3 -> 3 * samples[i - 1] - 3 * samples[i - 2] + samples[i - 3]
          else -> 4 * samples[i - 1] - 6 * samples[i - 2] + 4 * samples[i - 3] - samples[i - 4]
        }
        val err = samples[i] - pred
        residuals[i - order] = err
        absSum += abs(err.toLong())
      }

      // Estimate optimal Rice parameter k (0..14)
      val meanAbs = (absSum.toDouble() / residualCount.coerceAtLeast(1)).coerceAtLeast(0.5)
      val k = (ln(meanAbs * 0.693147) / ln(2.0)).roundToInt().coerceIn(0, 14)

      var totalResidualBits = 0
      var exceedsSignedLimit = false
      for (j in 0 until residualCount) {
        val unsigned = signedToZigZag(residuals[j])
        val q = unsigned ushr k
        if (q > 10_000) {
          exceedsSignedLimit = true
          break
        }
        totalResidualBits += q + 1 + k
      }
      if (!exceedsSignedLimit) {
        val candidateBits = 8 + (order * bitsPerSample) + 2 + 4 + 4 + totalResidualBits
        if (candidateBits < bestBits) {
          bestBits = candidateBits
          bestOrder = order
          bestRiceParam = k
        }
      }
    }

    if (bestOrder < 0) {
      writeVerbatimSubframe(writer, samples, blockSize, bitsPerSample)
      return
    }

    // Recompute residuals for chosen bestOrder
    val residualCount = blockSize - bestOrder
    for (i in bestOrder until blockSize) {
      val pred = when (bestOrder) {
        0 -> 0
        1 -> samples[i - 1]
        2 -> 2 * samples[i - 1] - samples[i - 2]
        3 -> 3 * samples[i - 1] - 3 * samples[i - 2] + samples[i - 3]
        else -> 4 * samples[i - 1] - 6 * samples[i - 2] + 4 * samples[i - 3] - samples[i - 4]
      }
      residuals[i - bestOrder] = samples[i] - pred
    }

    // Subframe header: 0 (pad) | (001000 + order) | 0 (no wasted bits)
    val subframeType = 0x08 or bestOrder
    writer.writeBits(subframeType shl 1, 8)

    // Warmup samples
    for (w in 0 until bestOrder) {
      writer.writeSignedBits(samples[w], bitsPerSample)
    }

    // Residual coding method = 00 (RICE 4-bit param), partition order = 0000 (1 partition)
    writer.writeBits(0, 2)
    writer.writeBits(0, 4)
    writer.writeBits(bestRiceParam, 4)

    for (j in 0 until residualCount) {
      val folded = signedToZigZag(residuals[j])
      val q = folded ushr bestRiceParam
      val r = folded and ((1 shl bestRiceParam) - 1)
      // Write q zeros followed by 1 stop bit
      for (z in 0 until q) {
        writer.writeBit(0)
      }
      writer.writeBit(1)
      if (bestRiceParam > 0) {
        writer.writeBits(r, bestRiceParam)
      }
    }
  }

  private fun writeVerbatimSubframe(
    writer: FlacBitWriter,
    samples: IntArray,
    blockSize: Int,
    bitsPerSample: Int,
  ) {
    // 0 (pad) | 000001 (VERBATIM) | 0 (no wasted bits) = 0x02
    writer.writeBits(0x02, 8)
    for (i in 0 until blockSize) {
      writer.writeSignedBits(samples[i], bitsPerSample)
    }
  }

  private fun signedToZigZag(value: Int): Int =
    if (value >= 0) value shl 1 else ((-value) shl 1) - 1

  /**
   * Serializes a [RawAudioBuffer] as standard RIFF WAV in 16-bit or 24-bit signed little-endian PCM.
   */
  fun encodeBufferToWavBytes(
    buffer: RawAudioBuffer,
    bitsPerSample: Int = 16,
  ): ByteArray {
    if (buffer.pcmSamples.isEmpty() || buffer.frameCount <= 0) {
      throw IllegalArgumentException("No PCM samples available to encode as WAV.")
    }
    val bps = if (bitsPerSample == 24) 24 else 16
    val channels = buffer.channelCount.coerceIn(1, 2)
    val bytesPerSample = bps / 8
    val dataSize = buffer.pcmSamples.size * bytesPerSample
    val totalFileSize = 44 + dataSize
    val out = ByteBuffer.allocate(totalFileSize).order(ByteOrder.LITTLE_ENDIAN)

    val byteRate = buffer.sampleRate * channels * bytesPerSample
    val blockAlign = (channels * bytesPerSample).toShort()

    out.put("RIFF".toByteArray(Charsets.US_ASCII))
    out.putInt(36 + dataSize)
    out.put("WAVE".toByteArray(Charsets.US_ASCII))
    out.put("fmt ".toByteArray(Charsets.US_ASCII))
    out.putInt(16)
    out.putShort(1.toShort()) // PCM
    out.putShort(channels.toShort())
    out.putInt(buffer.sampleRate)
    out.putInt(byteRate)
    out.putShort(blockAlign)
    out.putShort(bps.toShort())
    out.put("data".toByteArray(Charsets.US_ASCII))
    out.putInt(dataSize)

    if (bps == 16) {
      buffer.pcmSamples.forEach { sample -> out.putShort(sample) }
    } else {
      // Scale 16-bit normalized samples cleanly to signed 24-bit little-endian PCM
      buffer.pcmSamples.forEach { sample ->
        val sample24 = sample.toInt() shl 8
        out.put((sample24 and 0xFF).toByte())
        out.put(((sample24 ushr 8) and 0xFF).toByte())
        out.put(((sample24 ushr 16) and 0xFF).toByte())
      }
    }
    return out.array()
  }
}

private class FlacBitWriter(initialCapacityBytes: Int) {
  private var buffer = ByteArray(initialCapacityBytes)
  private var bytePos = 0
  private var currentByte = 0
  private var bitCount = 0

  private fun ensureCapacity(extraBytes: Int) {
    if (bytePos + extraBytes >= buffer.size) {
      buffer = buffer.copyOf(max(buffer.size * 2, bytePos + extraBytes + 256))
    }
  }

  fun writeBit(bit: Int) {
    currentByte = (currentByte shl 1) or (bit and 1)
    bitCount++
    if (bitCount == 8) {
      ensureCapacity(1)
      buffer[bytePos++] = (currentByte and 0xFF).toByte()
      currentByte = 0
      bitCount = 0
    }
  }

  fun writeBits(value: Int, count: Int) {
    for (shift in (count - 1) downTo 0) {
      writeBit((value ushr shift) and 1)
    }
  }

  fun writeSignedBits(value: Int, count: Int) {
    writeBits(value, count)
  }

  fun writeUtf8UInt(value: Long) {
    when {
      value < 0x80L -> writeBits(value.toInt(), 8)
      value < 0x800L -> {
        writeBits(0xC0 or ((value ushr 6).toInt() and 0x1F), 8)
        writeBits(0x80 or (value.toInt() and 0x3F), 8)
      }
      value < 0x10000L -> {
        writeBits(0xE0 or ((value ushr 12).toInt() and 0x0F), 8)
        writeBits(0x80 or ((value ushr 6).toInt() and 0x3F), 8)
        writeBits(0x80 or (value.toInt() and 0x3F), 8)
      }
      else -> {
        writeBits(0xF0 or ((value ushr 18).toInt() and 0x07), 8)
        writeBits(0x80 or ((value ushr 12).toInt() and 0x3F), 8)
        writeBits(0x80 or ((value ushr 6).toInt() and 0x3F), 8)
        writeBits(0x80 or (value.toInt() and 0x3F), 8)
      }
    }
  }

  fun alignToByte() {
    if (bitCount > 0) {
      writeBits(0, 8 - bitCount)
    }
  }

  fun computeCrc8SoFar(): Int {
    var crc = 0
    for (i in 0 until bytePos) {
      crc = CRC8_TABLE[(crc xor (buffer[i].toInt() and 0xFF)) and 0xFF]
    }
    return crc and 0xFF
  }

  fun computeCrc16SoFar(): Int {
    var crc = 0
    for (i in 0 until bytePos) {
      crc = ((crc shl 8) xor CRC16_TABLE[((crc ushr 8) xor (buffer[i].toInt() and 0xFF)) and 0xFF]) and 0xFFFF
    }
    return crc and 0xFFFF
  }

  fun toByteArray(): ByteArray {
    alignToByte()
    return buffer.copyOf(bytePos)
  }

  companion object {
    private val CRC8_TABLE = IntArray(256) { idx ->
      var crc = idx
      repeat(8) {
        crc = if ((crc and 0x80) != 0) ((crc shl 1) xor 0x07) else (crc shl 1)
      }
      crc and 0xFF
    }

    private val CRC16_TABLE = IntArray(256) { idx ->
      var crc = idx shl 8
      repeat(8) {
        crc = if ((crc and 0x8000) != 0) ((crc shl 1) xor 0x8005) else (crc shl 1)
      }
      crc and 0xFFFF
    }
  }
}
