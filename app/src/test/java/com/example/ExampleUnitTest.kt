package com.example

import com.example.ai.OfflineMusicArranger
import com.example.audio.DrumKitPreset
import com.example.audio.GuitarVoice
import com.example.audio.OfflineMusicLibrary
import com.example.audio.OfflineMusicSynthesizerDsp
import com.example.audio.OfflineSampleCategory
import com.example.audio.PianoVoice
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM unit tests verifying the 100% on-device Offline Music Arranger and Built-In Offline
 * Music & Sample Synthesizer.
 */
class ExampleUnitTest {

  @Test
  fun offlineMusicArranger_analyzesPromptAndSelectsMatchingInstruments() = runBlocking {
    val arranger = OfflineMusicArranger()

    val lofiPlan = arranger.generateArrangement("Rainy midnight lo-fi study beat with rhodes 86 bpm")
    assertEquals("Offline Model", lofiPlan.engineLabel)
    assertEquals(86, lofiPlan.bpm)
    assertEquals(PianoVoice.RHODES_EP, lofiPlan.pianoVoice)
    assertEquals(DrumKitPreset.LOFI_VINYL, lofiPlan.drumKit)
    assertTrue(lofiPlan.suggestedSampleQueries.isNotEmpty())

    val rockPlan = arranger.generateArrangement("Heavy energetic overdrive rock anthem 132 bpm")
    assertEquals(132, rockPlan.bpm)
    assertEquals(GuitarVoice.OVERDRIVE_ROCK, rockPlan.guitarVoice)
  }

  @Test
  fun offlineMusicLibrary_searchesCategoriesAndSynthesizesNonSilentAudio() {
    assertEquals(16, OfflineMusicLibrary.allAssets.size)
    assertEquals(8, OfflineMusicLibrary.search("", OfflineSampleCategory.FULL_MUSIC).size)
    assertEquals(4, OfflineMusicLibrary.search("", OfflineSampleCategory.TEXTURES).size)
    assertEquals(4, OfflineMusicLibrary.search("", OfflineSampleCategory.STEMS).size)

    val rainResults = OfflineMusicLibrary.search("midnight rain")
    assertTrue(rainResults.isNotEmpty())

    // Verify full loop, texture, and stem all render non-empty, non-silent 16-bit PCM audio
    val sampleIds = listOf("lofi_midnight_rain", "tex_vinyl_crackle", "stem_rhodes_chords")
    for (id in sampleIds) {
      val asset = OfflineMusicLibrary.findById(id)!!
      val pcm = OfflineMusicSynthesizerDsp.renderAssetPcm(asset)
      assertTrue("Expected > 2s PCM for $id", pcm.size > 44100 * 2)
      val peak = pcm.maxOf { kotlin.math.abs(it.toInt()) }
      assertTrue("Expected audible waveform peak for $id, got $peak", peak > 500)
    }
  }

  @Test
  fun roomTracksStorageExporter_exportsMultipleTracksToDirectoryWithUniqueNames() {
    val srcDir = kotlin.io.path.createTempDirectory("sf_src_").toFile()
    val exportDir = kotlin.io.path.createTempDirectory("sf_export_").toFile()
    try {
      // Synthesize two real WAV tracks with identical titles to verify deduplication & real MP3 encoding
      val track1 = com.example.audio.WavTrackSynthesizer.synthesizeToFile("Warm lo-fi chill 84 bpm", srcDir)
        .copy(title = "Midnight Session")
      val track2 = com.example.audio.WavTrackSynthesizer.synthesizeToFile("Bright acoustic folk 102 bpm", srcDir)
        .copy(title = "Midnight Session")

      val progressTicks = mutableListOf<Int>()
      val result = com.example.audio.RoomTracksStorageExporter.exportTracksToLocalDirectory(
        tracks = listOf(track1, track2),
        targetDir = exportDir,
        config = com.example.audio.AudioExportConfig(
          format = com.example.audio.AudioExportFormat.MP3,
          bitrateKbps = 128,
        ),
        onProgress = { progressTicks.add(it.exportedCount) },
      )

      assertEquals(2, result.exportedCount)
      assertEquals(0, result.failedCount)
      assertEquals(0, result.skippedCount)
      assertEquals(2, result.exportedFileNames.distinct().size)
      assertTrue(result.totalBytes > 2048L)
      assertTrue(progressTicks.isNotEmpty())
      result.exportedFileNames.forEach { fileName ->
        val file = java.io.File(exportDir, fileName)
        assertTrue("Expected exported file $fileName to exist", file.exists())
        assertTrue("Expected exported file $fileName to be non-empty", file.length() > 1024L)
        assertTrue("Expected MP3 extension for $fileName", fileName.endsWith(".mp3"))
      }

      assertEquals(
        "Music/SonicForge",
        com.example.audio.RoomTracksStorageExporter.formatTreeUriLabel("primary:Music/SonicForge"),
      )
    } finally {
      srcDir.deleteRecursively()
      exportDir.deleteRecursively()
    }
  }

  @Test
  fun threeBandEqualizer_updatesGainsCurveAndProcessesStereoAudioByFrequency() {
    val initial = com.example.audio.EqualizerSettings()
    assertEquals(0f, initial.lowGainDb, 0.01f)
    assertEquals(0f, initial.midGainDb, 0.01f)
    assertEquals(0f, initial.highGainDb, 0.01f)

    // Adjusting Low, Mid, High auto-enables EQ, snaps to 0.5 dB, and round-trips cleanly
    val shaped = initial.withThreeBandGains(lowDb = 6.2f, midDb = -3.1f, highDb = 4.6f)
    assertTrue(shaped.isEnabled)
    assertTrue(shaped.isActive)
    assertEquals(6.0f, shaped.lowGainDb, 0.01f)
    assertEquals(-3.0f, shaped.midGainDb, 0.01f)
    assertEquals(4.5f, shaped.highGainDb, 0.01f)
    assertEquals("L +6.0 · M -3.0 · H +4.5 dB", com.example.audio.GraphicEqualizerDsp.formatThreeBandSummary(shaped))

    // Analytical frequency-response curve reflects low boost, mid cut, and high boost
    val curve = com.example.audio.GraphicEqualizerDsp.computeFrequencyCurvePoints(shaped, samplePoints = 48)
    assertEquals(48, curve.size)
    val lowIdx = (com.example.audio.GraphicEqualizerDsp.frequencyToCurveX(120) * 47).toInt()
    val midIdx = (com.example.audio.GraphicEqualizerDsp.frequencyToCurveX(1000) * 47).toInt()
    val highIdx = (com.example.audio.GraphicEqualizerDsp.frequencyToCurveX(8000) * 47).toInt()
    assertTrue("Expected positive low curve response, got ${curve[lowIdx]}", curve[lowIdx] > 0.25f)
    assertTrue("Expected negative mid curve response, got ${curve[midIdx]}", curve[midIdx] < -0.15f)
    assertTrue("Expected positive high curve response, got ${curve[highIdx]}", curve[highIdx] > 0.2f)

    // Helper to synthesize stereo sine wave PCM and compute steady-state RMS
    fun stereoSine(freqHz: Double, sampleRate: Int = 44100, frames: Int = 4410): ShortArray {
      val pcm = ShortArray(frames * 2)
      for (i in 0 until frames) {
        val sample = (kotlin.math.sin(2.0 * kotlin.math.PI * freqHz * i / sampleRate) * 6000.0).toInt().toShort()
        pcm[i * 2] = sample
        pcm[i * 2 + 1] = sample
      }
      return pcm
    }

    fun steadyRms(pcm: ShortArray): Double {
      val start = pcm.size / 3
      var sumSq = 0.0
      var count = 0
      for (i in start until pcm.size) {
        val v = pcm[i].toDouble()
        sumSq += v * v
        count++
      }
      return kotlin.math.sqrt(sumSq / count)
    }

    val lowTone = stereoSine(120.0)
    val midTone = stereoSine(1000.0)
    val highTone = stereoSine(8000.0)

    // Boosting LOW boosts 120 Hz significantly while leaving 8 kHz near unity
    val lowBoostEq = com.example.audio.EqualizerSettings().withThreeBandGain(
      com.example.audio.ThreeBandEqBand.LOW,
      9.0f,
    )
    val lowBoosted120 = com.example.audio.GraphicEqualizerDsp.processPcmOffline(lowTone, 44100, 2, lowBoostEq)
    val lowBoosted8k = com.example.audio.GraphicEqualizerDsp.processPcmOffline(highTone, 44100, 2, lowBoostEq)
    val lowRatio = steadyRms(lowBoosted120) / steadyRms(lowTone)
    val highControlRatio = steadyRms(lowBoosted8k) / steadyRms(highTone)
    assertTrue("Expected 120 Hz gain > 2.0x, got $lowRatio", lowRatio > 2.0)
    assertTrue("Expected 8 kHz near unity, got $highControlRatio", highControlRatio in 0.85..1.15)

    // Boosting MID boosts 1 kHz on stereo audio cleanly (verifies band indexing fix for bands 4..7)
    val midBoostEq = com.example.audio.EqualizerSettings().withThreeBandGain(
      com.example.audio.ThreeBandEqBand.MID,
      9.0f,
    )
    val midBoosted1k = com.example.audio.GraphicEqualizerDsp.processPcmOffline(midTone, 44100, 2, midBoostEq)
    val midRatio = steadyRms(midBoosted1k) / steadyRms(midTone)
    assertTrue("Expected 1 kHz gain > 2.0x, got $midRatio", midRatio > 2.0)

    // Boosting HIGH boosts 8 kHz on stereo audio cleanly
    val highBoostEq = com.example.audio.EqualizerSettings().withThreeBandGain(
      com.example.audio.ThreeBandEqBand.HIGH,
      9.0f,
    )
    val highBoosted8k = com.example.audio.GraphicEqualizerDsp.processPcmOffline(highTone, 44100, 2, highBoostEq)
    val highRatio = steadyRms(highBoosted8k) / steadyRms(highTone)
    assertTrue("Expected 8 kHz gain > 2.0x, got $highRatio", highRatio > 2.0)
  }

  @Test
  fun seamlessLoopDsp_supportsLoopTrackAndLoopSelectionWithClickFreeCrossfade() {
    // 1. Verify TrackLoopSettings mode cycling, clamping, and A/B punch helpers
    val off = com.example.audio.TrackLoopSettings()
    assertEquals(com.example.audio.TrackLoopMode.OFF, off.mode)
    assertEquals(com.example.audio.TrackLoopMode.LOOP_TRACK, off.nextCycledMode())

    val loopTrack = off.withMode(com.example.audio.TrackLoopMode.LOOP_TRACK)
    assertTrue(loopTrack.isLooping)
    assertTrue(loopTrack.isLoopTrack)
    assertEquals(com.example.audio.TrackLoopMode.LOOP_SELECTION, loopTrack.nextCycledMode())

    val loopSel = loopTrack.withSelectionRange(startFraction = 0.25f, endFraction = 0.75f)
    assertTrue(loopSel.isLoopSelection)
    assertEquals(2500L, loopSel.selectionStartMs(10_000L))
    assertEquals(7500L, loopSel.selectionEndMs(10_000L))
    assertEquals(5000L, loopSel.selectionDurationMs(10_000L))
    assertEquals("00:02.5", com.example.audio.SeamlessLoopDsp.formatLoopTime(2500L))

    // Minimum span enforced when A & B are collapsed together
    val collapsed = loopSel.withSelectionRange(startFraction = 0.50f, endFraction = 0.505f)
    assertTrue(
      "Expected minimum span >= ${com.example.audio.SeamlessLoopDsp.MIN_SELECTION_FRACTION}",
      collapsed.selectionEndFraction - collapsed.selectionStartFraction >=
        com.example.audio.SeamlessLoopDsp.MIN_SELECTION_FRACTION - 0.001f,
    )

    // 2. Verify click-free equal-power micro-crossfade across Loop Track seam
    val sampleRate = 44_100
    val totalFrames = 4_000
    val channels = 2
    // Construct a hard step discontinuity between loop tail (+12000) and loop head (-12000)
    val hardStepPcm = ShortArray(totalFrames * channels) { idx ->
      val frame = idx / channels
      if (frame < totalFrames / 2) (-12_000).toShort() else 12_000.toShort()
    }
    val dest = ShortArray(1024 * channels)
    val startNearEnd = totalFrames - 700
    val readFull = com.example.audio.SeamlessLoopDsp.extractSeamlessLoopChunk(
      pcmSamples = hardStepPcm,
      totalFrames = totalFrames,
      sampleRate = sampleRate,
      channelCount = channels,
      cursorFrame = startNearEnd,
      requestedFrames = 1024,
      loopSettings = loopTrack,
      destination = dest,
    )
    assertEquals(1024, readFull.framesRead)
    assertTrue("Expected full-track loop chunk to wrap seamlessly", readFull.didWrap)
    assertTrue("Expected next cursor inside valid range", readFull.nextFrameCursor in 0 until totalFrames)

    // Verify no hard 24,000-amplitude click exists anywhere across the wrapped chunk
    var maxStepJump = 0
    for (f in 1 until readFull.framesRead) {
      val prev = dest[(f - 1) * channels].toInt()
      val curr = dest[f * channels].toInt()
      val jump = kotlin.math.abs(curr - prev)
      if (jump > maxStepJump) maxStepJump = jump
    }
    assertTrue("Expected click-free micro-crossfade jump < 500, got $maxStepJump", maxStepJump < 500)

    // 3. Verify Loop Selection snaps out-of-range cursor into [loopStart, loopEnd) and wraps inside selection
    val selStartFrame = loopSel.resolvedStartFrame(totalFrames)
    val selEndFrame = loopSel.resolvedEndFrame(totalFrames)
    val selDest = ShortArray(512 * channels)
    // Cursor at frame 0 (before selStartFrame=1000) should snap into [1000, 3000)
    val snappedRead = com.example.audio.SeamlessLoopDsp.extractSeamlessLoopChunk(
      pcmSamples = hardStepPcm,
      totalFrames = totalFrames,
      sampleRate = sampleRate,
      channelCount = channels,
      cursorFrame = 0,
      requestedFrames = 512,
      loopSettings = loopSel,
      destination = selDest,
    )
    assertEquals(512, snappedRead.framesRead)
    assertEquals(selStartFrame + 512, snappedRead.nextFrameCursor)

    // Cursor right before selEndFrame should wrap cleanly back inside [selStartFrame, selEndFrame)
    val wrappedSelRead = com.example.audio.SeamlessLoopDsp.extractSeamlessLoopChunk(
      pcmSamples = hardStepPcm,
      totalFrames = totalFrames,
      sampleRate = sampleRate,
      channelCount = channels,
      cursorFrame = selEndFrame - 200,
      requestedFrames = 512,
      loopSettings = loopSel,
      destination = selDest,
    )
    assertEquals(512, wrappedSelRead.framesRead)
    assertTrue("Expected selection loop to wrap", wrappedSelRead.didWrap)
    assertTrue(
      "Expected cursor ${wrappedSelRead.nextFrameCursor} inside [$selStartFrame, $selEndFrame)",
      wrappedSelRead.nextFrameCursor in selStartFrame until selEndFrame,
    )
  }
}
