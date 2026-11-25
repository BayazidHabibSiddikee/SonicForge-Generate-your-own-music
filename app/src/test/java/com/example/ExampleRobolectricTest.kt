package com.example

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.audio.AudioPlaybackState
import com.example.audio.WavTrackSynthesizer
import com.example.model.GeneratedTrack
import com.example.ui.components.CustomAudioPlayer
import com.example.ui.components.WaveformVisualizer
import com.example.ui.theme.SonicForgeTheme
import java.io.File
import androidx.room.Room
import com.example.data.local.SonicForgeDatabase
import com.example.data.local.toDomain
import com.example.data.local.toEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `app name matches SonicForge`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("SonicForge", appName)
  }

  @Test
  fun `wav synthesizer creates valid non-empty wav file and waveform`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val tempDir = File(context.cacheDir, "synth_test_${System.currentTimeMillis()}")
    try {
      val track = WavTrackSynthesizer.synthesizeToFile("Warm lo-fi chill beat 84 bpm", tempDir)
      val wavFile = File(track.filePath)
      assertTrue(wavFile.exists())
      assertTrue(wavFile.length() > 44L)
      assertEquals("Lo-Fi Chill", track.genre)
      assertEquals(84, track.bpm)
      assertTrue(track.waveformPeaks.isNotEmpty())
    } finally {
      tempDir.deleteRecursively()
    }
  }

  @Test
  fun `room dao inserts observes and deletes generated tracks`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = Room.inMemoryDatabaseBuilder(context, SonicForgeDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    try {
      val dao = db.generatedTrackDao()
      val track = GeneratedTrack(
        id = "room-track-1",
        title = "Synthwave Test",
        prompt = "Chill synth beat",
        genre = "Synthwave",
        bpm = 100,
        musicalKey = "C Min",
        durationMs = 10_000L,
        filePath = "/tmp/synth.wav",
        fileSizeBytes = 250_000L,
        waveformPeaks = listOf(0.2f, 0.5f, 0.9f),
        createdAtEpochMs = 2_000L,
        sourceLabel = "On-Device DSP",
        category = "Synthwave",
        tags = listOf("Night", "Focus"),
      )

      dao.insertTrack(track.toEntity())
      val saved = dao.observeAllTracks().first().map { it.toDomain() }
      assertEquals(1, saved.size)
      assertEquals("Synthwave", saved.first().category)
      assertEquals(listOf("Night", "Focus"), saved.first().tags)

      val byCategory = dao.observeTracksByCategory("Synthwave").first()
      assertEquals(1, byCategory.size)

      val byTagSearch = dao.searchTracks("Focus").first()
      assertEquals(1, byTagSearch.size)

      dao.updateTrackOrganization("room-track-1", "Sleep", "Deep,Ambient")
      val updated = dao.observeTracksByCategory("Sleep").first().map { it.toDomain() }
      assertEquals(1, updated.size)
      assertEquals(listOf("Deep", "Ambient"), updated.first().tags)

      dao.deleteTrackById("room-track-1")
      val afterDelete = dao.observeAllTracks().first()
      assertTrue(afterDelete.isEmpty())
    } finally {
      db.close()
    }
  }

  @Test
  fun `custom audio player renders controls and triggers callbacks`() {
    var playClicked = false
    var muteClicked = false
    val sampleTrack = GeneratedTrack(
      id = "track-1",
      title = "Neon Midnight Drive",
      prompt = "Neon midnight synthwave drive 112 bpm",
      genre = "Synthwave",
      bpm = 112,
      musicalKey = "C Min",
      durationMs = 17_000L,
      filePath = "/tmp/sample.wav",
      fileSizeBytes = 1_500_000L,
      waveformPeaks = List(32) { 0.5f },
      createdAtEpochMs = 1_000L,
    )

    var stopClicked = false

    composeTestRule.setContent {
      SonicForgeTheme {
        Box(modifier = Modifier.verticalScroll(rememberScrollState())) {
          CustomAudioPlayer(
            track = sampleTrack,
            playbackState = AudioPlaybackState(
              currentTrackId = sampleTrack.id,
              isPrepared = true,
              positionMs = 4_000L,
              durationMs = 17_000L,
              volume = 0.75f,
            ),
            canSkipPrevious = true,
            canSkipNext = true,
            onTogglePlayPause = { playClicked = true },
            onStop = { stopClicked = true },
            onBeginSeek = {},
            onPreviewSeek = {},
            onCommitSeek = {},
            onSeekBy = {},
            onSkipPrevious = {},
            onSkipNext = {},
            onToggleLoop = {},
            onVolumeChange = {},
            onToggleMute = { muteClicked = true },
          )
        }
      }
    }

    composeTestRule.onNodeWithTag("playback_play_button").performScrollTo().assertIsDisplayed().performClick()
    composeTestRule.onNodeWithTag("playback_pause_button").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("playback_stop_button").performScrollTo().assertIsDisplayed().performClick()
    composeTestRule.onNodeWithTag("player_seek_slider").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("player_volume_slider").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("player_mute_button").performScrollTo().assertIsDisplayed().performClick()

    assertTrue(playClicked)
    assertTrue(stopClicked)
    assertTrue(muteClicked)
  }

  @Test
  fun `waveform visualizer renders and triggers scrub callback`() {
    var scrubbedMs = -1L
    composeTestRule.setContent {
      SonicForgeTheme {
        WaveformVisualizer(
          peaks = List(40) { idx -> (idx % 5 + 1) * 0.2f },
          positionMs = 5_000L,
          durationMs = 20_000L,
          isPlaying = true,
          enabled = true,
          onScrubEnd = { scrubbedMs = it },
          modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .testTag("standalone_waveform"),
        )
      }
    }

    composeTestRule.onNodeWithTag("standalone_waveform").assertIsDisplayed().performClick()
    assertTrue(scrubbedMs in 0L..20_000L)
  }

  @Test
  fun `playback foreground service starts and stops cleanly`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    com.example.audio.PlaybackForegroundService.syncNotification(context)
    com.example.audio.PlaybackForegroundService.stopService(context)
    assertEquals("sonicforge_playback_channel", com.example.audio.PlaybackForegroundService.CHANNEL_ID)
  }

  @Test
  fun `theme selector switches between light and dark modes`() {
    var currentMode = com.example.ui.theme.ThemeMode.DARK
    composeTestRule.setContent {
      SonicForgeTheme(darkTheme = currentMode == com.example.ui.theme.ThemeMode.DARK) {
        com.example.ui.ThemeSelectorRow(
          selectedMode = currentMode,
          onSelectThemeMode = { currentMode = it },
        )
      }
    }

    composeTestRule.onNodeWithTag("theme_option_light").assertIsDisplayed().performClick()
    assertEquals(com.example.ui.theme.ThemeMode.LIGHT, currentMode)

    composeTestRule.onNodeWithTag("theme_option_dark").assertIsDisplayed().performClick()
    assertEquals(com.example.ui.theme.ThemeMode.DARK, currentMode)

    composeTestRule.onNodeWithTag("theme_option_system").assertIsDisplayed().performClick()
    assertEquals(com.example.ui.theme.ThemeMode.SYSTEM, currentMode)
  }

  @Test
  fun `huggingface space client builds gradio payload and parses sse audio url`() {
    val infoJson = org.json.JSONObject(
      """
      {
        "named_endpoints": {
          "/text2audio": {
            "parameters": [
              {
                "parameter_name": "text",
                "label": "Input prompt",
                "type": { "type": "string" },
                "parameter_default": null
              },
              {
                "parameter_name": "duration",
                "label": "Duration (seconds)",
                "type": { "type": "number" },
                "parameter_default": 10.0
              },
              {
                "parameter_name": "guidance_scale",
                "label": "Guidance",
                "type": { "type": "number" },
                "parameter_default": 2.5
              }
            ]
          }
        }
      }
      """.trimIndent()
    )

    val (endpoint, data) = com.example.audio.HuggingFaceSpaceClient.buildGradioPayloadFromInfo(
      infoJson = infoJson,
      prompt = "Lush ambient Fourier pad 80 bpm",
    )
    assertEquals("/text2audio", endpoint)
    assertEquals("Lush ambient Fourier pad 80 bpm", data.getString(0))
    assertEquals(10.0, data.getDouble(1), 0.01)
    assertEquals(2.5, data.getDouble(2), 0.01)

    val sseUrlData = """[{"url":"https://haoheliu-audioldm-text-to-audio-generation.hf.space/gradio_api/file=/tmp/out.wav"}]"""
    val extractedDirect = com.example.audio.HuggingFaceSpaceClient.extractAudioUrlFromSseData(
      sseData = sseUrlData,
      baseUrl = "https://haoheliu-audioldm-text-to-audio-generation.hf.space",
      apiPrefix = "/gradio_api",
    )
    assertEquals(
      "https://haoheliu-audioldm-text-to-audio-generation.hf.space/gradio_api/file=/tmp/out.wav",
      extractedDirect,
    )

    val ssePathData = """[{"path":"/tmp/gradio/music.wav"}]"""
    val extractedRelative = com.example.audio.HuggingFaceSpaceClient.extractAudioUrlFromSseData(
      sseData = ssePathData,
      baseUrl = "https://facebook-musicgen.hf.space",
      apiPrefix = "/gradio_api",
    )
    assertEquals(
      "https://facebook-musicgen.hf.space/gradio_api/file=tmp/gradio/music.wav",
      extractedRelative,
    )
  }

  @Test
  fun `fourier audio buffer processor computes accurate fft centroid and decodes raw pcm wav`() {
    val sampleRate = 44_100
    val targetFreqHz = 1_000.0
    val testPcm = ShortArray(4096) { i ->
      val sample = kotlin.math.sin(2.0 * kotlin.math.PI * targetFreqHz * i / sampleRate)
      (sample * 16_000.0).toInt().toShort()
    }

    val rawBuffer = com.example.audio.FourierAudioBufferProcessor.analyzeRawPcm(
      pcmSamples = testPcm,
      sampleRate = sampleRate,
      channelCount = 1,
      bridgeSource = "FFT Unit Test",
    )

    assertEquals(4096, rawBuffer.pcmSamples.size)
    assertEquals(12, rawBuffer.fourierSpectrumBins.size)
    assertTrue("Expected spectral centroid near 1000 Hz but was ${rawBuffer.spectralCentroidHz}",
      rawBuffer.spectralCentroidHz in 850..1150)

    val context = ApplicationProvider.getApplicationContext<Context>()
    val tempDir = File(context.cacheDir, "raw_buf_test_${System.currentTimeMillis()}")
    try {
      val (track, synthBuffer) = WavTrackSynthesizer.synthesizeWithRawBuffer(
        prompt = "Cyber techno Fourier lead 124 bpm",
        outputDir = tempDir,
      )
      assertTrue(synthBuffer.pcmSamples.size > 44_100)
      assertTrue(synthBuffer.rmsAmplitude > 0.01f)

      val decoded = com.example.audio.FourierAudioBufferProcessor.decodeFileToRawBuffer(
        file = File(track.filePath),
        bridgeSource = track.sourceLabel,
      )
      assertEquals(synthBuffer.pcmSamples.size, decoded.pcmSamples.size)
      assertEquals(44_100, decoded.sampleRate)
      assertEquals(12, decoded.fourierSpectrumBins.size)
    } finally {
      tempDir.deleteRecursively()
    }
  }

  @Test
  fun `trim raw buffer crops sample range accurately and trim dialog confirms crop`() {
    val sampleRate = 44_100
    val fourSecondPcm = ShortArray(sampleRate * 4) { idx ->
      (kotlin.math.sin(2.0 * kotlin.math.PI * 440.0 * idx / sampleRate) * 14_000.0).toInt().toShort()
    }
    val sourceBuffer = com.example.audio.FourierAudioBufferProcessor.analyzeRawPcm(
      pcmSamples = fourSecondPcm,
      sampleRate = sampleRate,
      channelCount = 1,
      bridgeSource = "Trim Test",
    )

    val cropped = com.example.audio.FourierAudioBufferProcessor.trimRawBuffer(
      source = sourceBuffer,
      startMs = 1_000L,
      endMs = 2_500L,
    )
    // Expect 1.5 seconds of audio at 44.1 kHz (~66,150 samples)
    assertEquals(66_150, cropped.pcmSamples.size)
    assertTrue(cropped.durationMs in 1_450L..1_550L)

    var confirmedStart = -1L
    var confirmedEnd = -1L
    var confirmedCopy = false
    val sampleTrack = GeneratedTrack(
      id = "trim-ui-1",
      title = "Cyber Pulse",
      prompt = "Cyber techno pulse",
      genre = "Electronic",
      bpm = 128,
      musicalKey = "WAV",
      durationMs = 12_000L,
      filePath = "/tmp/cyber.wav",
      fileSizeBytes = 500_000L,
      waveformPeaks = List(40) { 0.6f },
      createdAtEpochMs = 5_000L,
    )

    composeTestRule.setContent {
      SonicForgeTheme {
        com.example.ui.components.TrackTrimDialog(
          track = sampleTrack,
          isTrimming = false,
          onDismiss = {},
          onConfirmTrim = { startMs, endMs, saveAsCopy ->
            confirmedStart = startMs
            confirmedEnd = endMs
            confirmedCopy = saveAsCopy
          },
        )
      }
    }

    composeTestRule.onNodeWithTag("track_trim_dialog").assertIsDisplayed()
    composeTestRule.onNodeWithTag("trim_range_slider").assertIsDisplayed()
    composeTestRule.onNodeWithTag("trim_save_copy_switch").performClick()
    composeTestRule.onNodeWithTag("confirm_trim_button").assertIsDisplayed().performClick()

    assertEquals(0L, confirmedStart)
    assertEquals(12_000L, confirmedEnd)
    assertEquals(false, confirmedCopy)
  }

  @Test
  fun `bpm detector accurately identifies tempo from rhythmic pcm pulses`() {
    val sampleRate = 44_100
    val durationSec = 8

    fun generateBeatTrain(targetBpm: Int): ShortArray {
      val totalSamples = sampleRate * durationSec
      val samples = ShortArray(totalSamples)
      val beatIntervalSamples = ((60.0 / targetBpm) * sampleRate).toInt()
      var beatStart = 0
      while (beatStart < totalSamples) {
        val hitLen = kotlin.math.min(totalSamples - beatStart, (0.09 * sampleRate).toInt())
        for (k in 0 until hitLen) {
          val t = k.toDouble() / sampleRate
          val env = kotlin.math.exp(-t * 32.0)
          val wave = kotlin.math.sin(2.0 * kotlin.math.PI * 95.0 * t)
          samples[beatStart + k] = (wave * env * 22_000.0).toInt().toShort()
        }
        beatStart += beatIntervalSamples
      }
      return samples
    }

    val (bpm120, conf120) = com.example.audio.FourierAudioBufferProcessor.detectBpmFromPcm(
      pcmSamples = generateBeatTrain(120),
      sampleRate = sampleRate,
    )
    assertTrue("Expected ~120 BPM, got $bpm120", bpm120 in 118..122)
    assertTrue("Expected positive beat confidence, got $conf120", conf120 > 0.35f)

    val (bpm92, conf92) = com.example.audio.FourierAudioBufferProcessor.detectBpmFromPcm(
      pcmSamples = generateBeatTrain(92),
      sampleRate = sampleRate,
    )
    assertTrue("Expected ~92 BPM, got $bpm92", bpm92 in 90..94)
    assertTrue("Expected positive beat confidence, got $conf92", conf92 > 0.35f)
  }

  @Test
  fun `mp3 exporter encodes pcm to valid mpeg layer 3 bytes and export dialog triggers actions`() {
    val sampleRate = 44_100
    val twoSecondPcm = ShortArray(sampleRate * 2) { idx ->
      (kotlin.math.sin(2.0 * kotlin.math.PI * 440.0 * idx / sampleRate) * 16_000.0).toInt().toShort()
    }
    val buffer = com.example.audio.FourierAudioBufferProcessor.analyzeRawPcm(
      pcmSamples = twoSecondPcm,
      sampleRate = sampleRate,
      channelCount = 1,
      bridgeSource = "MP3 Test",
    )

    val mp3Bytes = com.example.audio.Mp3AudioExporter.encodeRawBufferToMp3Bytes(buffer)
    assertTrue("Expected non-trivial MP3 output, got ${mp3Bytes.size} bytes", mp3Bytes.size > 4_000)
    // MPEG-1 frame sync header starts with 0xFF 0xFB / 0xFA / 0xF3 / 0xF2 or ID3
    val b0 = mp3Bytes[0].toInt() and 0xFF
    val b1 = mp3Bytes[1].toInt() and 0xFF
    assertTrue("Expected valid MPEG audio sync header, got %02X %02X".format(b0, b1),
      (b0 == 0xFF && (b1 and 0xE0) == 0xE0) || String(mp3Bytes, 0, 3, Charsets.US_ASCII) == "ID3")

    var musicClicked = false
    var folderClicked = false
    var shareClicked = false
    val sampleTrack = GeneratedTrack(
      id = "mp3-ui-1",
      title = "Solar Echoes",
      prompt = "Ambient pad",
      genre = "Ambient",
      bpm = 82,
      musicalKey = "WAV",
      durationMs = 8_000L,
      filePath = "/tmp/solar.wav",
      fileSizeBytes = 300_000L,
      waveformPeaks = List(32) { 0.4f },
      createdAtEpochMs = 4_000L,
    )

    composeTestRule.setContent {
      SonicForgeTheme {
        com.example.ui.components.TrackExportDialog(
          track = sampleTrack,
          isExporting = false,
          exportStatusMessage = "Saved MP3 (192 KB) to Music/SonicForge/Solar_Echoes.mp3",
          exportErrorMessage = null,
          onDismiss = {},
          onSaveToMusic = { musicClicked = true },
          onChooseFolder = { folderClicked = true },
          onShareMp3 = { shareClicked = true },
        )
      }
    }

    composeTestRule.onNodeWithTag("export_mp3_dialog").assertIsDisplayed()
    composeTestRule.onNodeWithTag("export_mp3_music_button").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("export_mp3_folder_button").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("share_mp3_button").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("export_mp3_status_banner").performScrollTo().assertIsDisplayed()

    assertTrue(musicClicked)
    assertTrue(folderClicked)
    assertTrue(shareClicked)
  }

  @Test
  fun `real-time pitch shifter transposes frequency accurately without altering duration and player ui triggers pitch actions`() {
    val sampleRate = 44_100
    val twoSecSamples = sampleRate * 2
    val baseFreqHz = 440.0
    val sourcePcm = ShortArray(twoSecSamples) { idx ->
      (kotlin.math.sin(2.0 * kotlin.math.PI * baseFreqHz * idx / sampleRate) * 14_000.0).toInt().toShort()
    }

    // Shift +12 semitones (octave up -> ~880 Hz) and verify duration & sample count remain identical
    val octaveUp = com.example.audio.PitchShiftDsp.processPcmOffline(
      pcmSamples = sourcePcm,
      sampleRate = sampleRate,
      channelCount = 1,
      semitones = 12f,
    )
    assertEquals(sourcePcm.size, octaveUp.size)
    val upAnalysis = com.example.audio.FourierAudioBufferProcessor.analyzeRawPcm(
      pcmSamples = octaveUp,
      sampleRate = sampleRate,
      channelCount = 1,
      bridgeSource = "Pitch +12 Test",
    )
    assertTrue(
      "Expected spectral centroid near 880 Hz after +12 st shift, got ${upAnalysis.spectralCentroidHz} Hz",
      upAnalysis.spectralCentroidHz in 760..1020,
    )

    // Shift -12 semitones (octave down -> ~220 Hz)
    val octaveDown = com.example.audio.PitchShiftDsp.processPcmOffline(
      pcmSamples = sourcePcm,
      sampleRate = sampleRate,
      channelCount = 1,
      semitones = -12f,
    )
    assertEquals(sourcePcm.size, octaveDown.size)
    val downAnalysis = com.example.audio.FourierAudioBufferProcessor.analyzeRawPcm(
      pcmSamples = octaveDown,
      sampleRate = sampleRate,
      channelCount = 1,
      bridgeSource = "Pitch -12 Test",
    )
    assertTrue(
      "Expected spectral centroid near 220 Hz after -12 st shift, got ${downAnalysis.spectralCentroidHz} Hz",
      downAnalysis.spectralCentroidHz in 160..320,
    )

    // Key transposition helper checks
    assertEquals("D Min", com.example.audio.PitchShiftDsp.transposeKeyLabel("C Min", 2f))
    assertEquals("G# Maj", com.example.audio.PitchShiftDsp.transposeKeyLabel("F Maj", 3f))
    assertEquals("+2 st", com.example.audio.PitchShiftDsp.formatSemitoneBadge(2f))

    // UI verification on CustomAudioPlayer pitch DSP card
    var steppedDelta = 0
    var resetClicked = false
    val sampleTrack = GeneratedTrack(
      id = "pitch-ui-1",
      title = "Prism Keys",
      prompt = "Lush Rhodes 90 bpm",
      genre = "Lo-Fi Chill",
      bpm = 90,
      musicalKey = "C Min",
      durationMs = 10_000L,
      filePath = "/tmp/prism.wav",
      fileSizeBytes = 250_000L,
      waveformPeaks = List(32) { 0.5f },
      createdAtEpochMs = 9_000L,
    )

    composeTestRule.setContent {
      SonicForgeTheme {
        Box(
          modifier = Modifier.verticalScroll(rememberScrollState()),
        ) {
          CustomAudioPlayer(
            track = sampleTrack,
            playbackState = AudioPlaybackState(
              currentTrackId = sampleTrack.id,
              isPrepared = true,
              positionMs = 2_000L,
              durationMs = 10_000L,
              pitchSemitones = 2f,
            ),
            canSkipPrevious = false,
            canSkipNext = false,
            onTogglePlayPause = {},
            onBeginSeek = {},
            onPreviewSeek = {},
            onCommitSeek = {},
            onSeekBy = {},
            onSkipPrevious = {},
            onSkipNext = {},
            onToggleLoop = {},
            onVolumeChange = {},
            onToggleMute = {},
            onStepPitch = { steppedDelta = it },
            onResetPitch = { resetClicked = true },
          )
        }
      }
    }

    composeTestRule.onNodeWithTag("player_pitch_dsp_card").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("player_pitch_slider").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("player_pitch_up_button").performScrollTo().performClick()
    assertEquals(1, steppedDelta)
    composeTestRule.onNodeWithTag("player_pitch_down_button").performScrollTo().performClick()
    assertEquals(-1, steppedDelta)
    composeTestRule.onNodeWithTag("player_pitch_reset_button").performScrollTo().assertIsDisplayed().performClick()
    assertTrue(resetClicked)
  }

  @Test
  fun `room reverb dsp creates decaying stereo spatial tail and player ui triggers reverb actions`() {
    val sampleRate = 44_100
    val channels = 2
    val burstFrames = (sampleRate * 0.12).toInt()
    val silenceFrames = (sampleRate * 0.88).toInt()
    val totalFrames = burstFrames + silenceFrames
    val burstSignal = ShortArray(totalFrames * channels)
    for (f in 0 until burstFrames) {
      val s = (kotlin.math.sin(2.0 * kotlin.math.PI * 330.0 * f / sampleRate) * 18_000.0).toInt().toShort()
      burstSignal[f * 2] = s
      burstSignal[f * 2 + 1] = s
    }

    // Dry bypass check: trailing silence remains 0
    val dryOut = com.example.audio.RoomReverbDsp.processPcmOffline(
      pcmSamples = burstSignal,
      sampleRate = sampleRate,
      channelCount = channels,
      settings = com.example.audio.ReverbSettings(),
    )
    assertEquals(burstSignal.size, dryOut.size)
    val dryTailPeak = (burstFrames until totalFrames).maxOf { f ->
      kotlin.math.abs(dryOut[f * 2].toInt())
    }
    assertEquals(0, dryTailPeak)

    // Hall preset check: generates decaying stereo reverb tail + optional tail extension
    val hallSettings = com.example.audio.ReverbSettings().withPreset(com.example.audio.ReverbPreset.HALL)
    val hallOut = com.example.audio.RoomReverbDsp.processPcmOffline(
      pcmSamples = burstSignal,
      sampleRate = sampleRate,
      channelCount = channels,
      settings = hallSettings,
      tailDurationMs = 500L,
    )
    assertTrue(hallOut.size > burstSignal.size)

    fun rmsWindow(startFrame: Int, endFrame: Int): Double {
      var sum = 0.0
      for (f in startFrame until endFrame) {
        val norm = hallOut[f * 2] / 32768.0
        sum += norm * norm
      }
      return kotlin.math.sqrt(sum / (endFrame - startFrame))
    }

    val earlyTailRms = rmsWindow(burstFrames + 2_000, burstFrames + 12_000)
    val lateTailRms = rmsWindow(burstFrames + 24_000, burstFrames + 34_000)
    assertTrue("Expected audible early reverb tail RMS, got $earlyTailRms", earlyTailRms > 0.008)
    assertTrue("Expected reverb tail to decay ($earlyTailRms -> $lateTailRms)", earlyTailRms > lateTailRms)

    // Stereo spatial decorrelation check (L and R delay offsets produce distinct spatial reflections)
    val stereoDiff = (burstFrames + 3_000 until burstFrames + 6_000).sumOf { f ->
      kotlin.math.abs(hallOut[f * 2].toInt() - hallOut[f * 2 + 1].toInt())
    }
    assertTrue("Expected stereo spatial decorrelation between L/R channels", stereoDiff > 0)

    // UI verification on CustomAudioPlayer Room Reverb card
    var selectedPreset: com.example.audio.ReverbPreset? = null
    var resetClicked = false
    var saveCopyClicked = false
    val sampleTrack = GeneratedTrack(
      id = "reverb-ui-1",
      title = "Cathedral Drift",
      prompt = "Lush ambient synth pad 76 bpm",
      genre = "Ambient",
      bpm = 76,
      musicalKey = "D Min",
      durationMs = 12_000L,
      filePath = "/tmp/drift.wav",
      fileSizeBytes = 320_000L,
      waveformPeaks = List(32) { 0.4f },
      createdAtEpochMs = 10_000L,
    )

    composeTestRule.setContent {
      SonicForgeTheme {
        Box(
          modifier = Modifier.verticalScroll(rememberScrollState()),
        ) {
          CustomAudioPlayer(
            track = sampleTrack,
            playbackState = AudioPlaybackState(
              currentTrackId = sampleTrack.id,
              isPrepared = true,
              positionMs = 3_000L,
              durationMs = 12_000L,
              reverb = hallSettings,
            ),
            canSkipPrevious = false,
            canSkipNext = false,
            onTogglePlayPause = {},
            onBeginSeek = {},
            onPreviewSeek = {},
            onCommitSeek = {},
            onSeekBy = {},
            onSkipPrevious = {},
            onSkipNext = {},
            onToggleLoop = {},
            onVolumeChange = {},
            onToggleMute = {},
            onSelectReverbPreset = { selectedPreset = it },
            onResetReverb = { resetClicked = true },
            onSaveReverbCopy = { saveCopyClicked = true },
          )
        }
      }
    }

    composeTestRule.onNodeWithTag("player_reverb_dsp_card").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("player_reverb_mix_slider").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("player_reverb_size_slider").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("reverb_preset_studio").performScrollTo().performClick()
    assertEquals(com.example.audio.ReverbPreset.STUDIO, selectedPreset)
    composeTestRule.onNodeWithTag("player_reverb_reset_button").performScrollTo().performClick()
    assertTrue(resetClicked)
    composeTestRule.onNodeWithTag("reverb_save_copy_button").performScrollTo().performClick()
    assertTrue(saveCopyClicked)
  }

  @Test
  fun `favorites boolean flag persists in room dao and library view toggles all vs favorites filter`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = Room.inMemoryDatabaseBuilder(context, SonicForgeDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    try {
      val dao = db.generatedTrackDao()
      val trackA = GeneratedTrack(
        id = "fav-track-1",
        title = "Midnight Vinyl",
        prompt = "Lo-fi beat",
        genre = "Lo-Fi Chill",
        bpm = 84,
        musicalKey = "A Min",
        durationMs = 9_000L,
        filePath = "/tmp/midnight.wav",
        fileSizeBytes = 120_000L,
        waveformPeaks = listOf(0.3f, 0.7f),
        createdAtEpochMs = 2_000L,
        category = "Lo-Fi Chill",
        tags = listOf("Study"),
        isFavorite = false,
      )
      val trackB = trackA.copy(
        id = "fav-track-2",
        title = "Neon Skyline",
        genre = "Synthwave",
        category = "Synthwave",
        tags = listOf("Drive"),
        createdAtEpochMs = 1_000L,
        isFavorite = true,
      )

      dao.insertTracks(listOf(trackA.toEntity(), trackB.toEntity()))

      val initialFavs = dao.observeFavoriteTracks().first().map { it.toDomain() }
      assertEquals(1, initialFavs.size)
      assertEquals("fav-track-2", initialFavs.first().id)

      dao.updateFavoriteStatus("fav-track-1", true)
      val updatedFavs = dao.observeFavoriteTracks().first().map { it.toDomain() }
      assertEquals(2, updatedFavs.size)
      assertTrue(updatedFavs.all { it.isFavorite })

      val byFavCategory = dao.observeFavoriteTracksByCategory("Lo-Fi Chill").first()
      assertEquals(1, byFavCategory.size)

      val byFavSearch = dao.searchFavoriteTracks("Study").first()
      assertEquals(1, byFavSearch.size)

      dao.updateFavoriteStatus("fav-track-2", false)
      val afterUnfav = dao.observeFavoriteTracks().first()
      assertEquals(1, afterUnfav.size)
      assertEquals("fav-track-1", afterUnfav.first().id)
    } finally {
      db.close()
    }

    var showFavoritesOnly = false
    var toggledFavoriteId: String? = null
    val uiTrack = GeneratedTrack(
      id = "ui-fav-1",
      title = "Aurora Pad",
      prompt = "Ambient synth pad",
      genre = "Ambient",
      bpm = 74,
      musicalKey = "C Maj",
      durationMs = 11_000L,
      filePath = "/tmp/aurora.wav",
      fileSizeBytes = 210_000L,
      waveformPeaks = List(24) { 0.5f },
      createdAtEpochMs = 5_000L,
      isFavorite = false,
    )

    composeTestRule.setContent {
      SonicForgeTheme {
        androidx.compose.foundation.layout.Column {
          com.example.ui.LibraryOrganizationControls(
            categories = listOf("All", "Ambient"),
            selectedCategory = "All",
            showFavoritesOnly = showFavoritesOnly,
            totalTrackCount = 2,
            favoriteTrackCount = 1,
            searchQuery = "",
            onSelectCategory = {},
            onSetShowFavoritesOnly = { showFavoritesOnly = it },
            onSearchQueryChanged = {},
          )
          com.example.ui.GeneratedTrackRow(
            track = uiTrack,
            isSelected = false,
            isPlaying = false,
            onClick = {},
            onToggleFavorite = { toggledFavoriteId = uiTrack.id },
            onEditOrganization = {},
            onTrimTrack = {},
            onExportMp3 = {},
            onDelete = {},
          )
        }
      }
    }

    composeTestRule.onNodeWithTag("filter_favorites_tracks").assertIsDisplayed().performClick()
    assertEquals(true, showFavoritesOnly)

    composeTestRule.onNodeWithTag("filter_all_tracks").assertIsDisplayed().performClick()
    assertEquals(false, showFavoritesOnly)

    composeTestRule.onNodeWithTag("favorite_track_ui-fav-1").assertIsDisplayed().performClick()
    assertEquals("ui-fav-1", toggledFavoriteId)
  }

  @Test
  fun `room playlist schema groups multiple tracks and playlist ui creates and selects playlists`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = Room.inMemoryDatabaseBuilder(context, SonicForgeDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    try {
      val trackDao = db.generatedTrackDao()
      val playlistDao = db.playlistDao()

      val track1 = GeneratedTrack(
        id = "pl-track-1",
        title = "Late Night Rhodes",
        prompt = "Lo-fi rhodes",
        genre = "Lo-Fi Chill",
        bpm = 84,
        musicalKey = "C Min",
        durationMs = 12_000L,
        filePath = "/tmp/rhodes.wav",
        fileSizeBytes = 140_000L,
        waveformPeaks = listOf(0.4f, 0.7f),
        createdAtEpochMs = 2_000L,
      )
      val track2 = track1.copy(
        id = "pl-track-2",
        title = "Neon Arp Drive",
        genre = "Synthwave",
        bpm = 112,
        durationMs = 15_000L,
        createdAtEpochMs = 3_000L,
      )
      trackDao.insertTracks(listOf(track1.toEntity(), track2.toEntity()))

      val playlistEntity = com.example.data.local.PlaylistEntity(
        id = "playlist-1",
        name = "Focus Session",
        description = "Deep coding beats",
        createdAtEpochMs = 5_000L,
      )
      playlistDao.insertPlaylist(playlistEntity)
      playlistDao.addTracksToPlaylist(
        listOf(
          com.example.data.local.PlaylistTrackCrossRef("playlist-1", "pl-track-1", 5_001L),
          com.example.data.local.PlaylistTrackCrossRef("playlist-1", "pl-track-2", 5_002L),
        )
      )

      val savedPlaylists = playlistDao.observePlaylistsWithTracks().first()
      assertEquals(1, savedPlaylists.size)
      assertEquals("Focus Session", savedPlaylists.first().playlist.name)
      assertEquals(2, savedPlaylists.first().tracks.size)

      val domainPlaylist = savedPlaylists.first().toDomain(requireExistingFiles = false)
      assertEquals(2, domainPlaylist.trackCount)
      assertEquals(27_000L, domainPlaylist.totalDurationMs)

      playlistDao.removeTrackFromPlaylist("playlist-1", "pl-track-1")
      val afterRemoveTrack = playlistDao.getPlaylistWithTracksOnce("playlist-1")
      assertEquals(1, afterRemoveTrack?.tracks?.size)
      assertEquals("pl-track-2", afterRemoveTrack?.tracks?.first()?.id)

      // Deleting a playlist must preserve the underlying generated tracks in the library
      playlistDao.deletePlaylistById("playlist-1")
      assertTrue(playlistDao.getAllPlaylistsOnce().isEmpty())
      assertEquals(2, trackDao.getAllTracksOnce().size)
    } finally {
      db.close()
    }

    var createdName = ""
    var createdDesc = ""
    var createdTrackIds: List<String> = emptyList()
    val sampleTracks = listOf(
      GeneratedTrack(
        id = "ui-pl-t1",
        title = "Amber Echoes",
        prompt = "Ambient pad",
        genre = "Ambient",
        bpm = 76,
        musicalKey = "D Min",
        durationMs = 10_000L,
        filePath = "/tmp/amber.wav",
        fileSizeBytes = 100_000L,
        waveformPeaks = List(16) { 0.4f },
        createdAtEpochMs = 1_000L,
      ),
      GeneratedTrack(
        id = "ui-pl-t2",
        title = "Pulse Runner",
        prompt = "Synthwave bass",
        genre = "Synthwave",
        bpm = 118,
        musicalKey = "E Min",
        durationMs = 14_000L,
        filePath = "/tmp/pulse.wav",
        fileSizeBytes = 160_000L,
        waveformPeaks = List(16) { 0.6f },
        createdAtEpochMs = 2_000L,
      ),
    )
    var selectedPlaylistId: String? = null
    var playedPlaylistId: String? = null

    composeTestRule.setContent {
      SonicForgeTheme {
        androidx.compose.foundation.layout.Column {
          com.example.ui.components.PlaylistsShelfSection(
            playlists = listOf(
              com.example.model.Playlist(
                id = "shelf-pl-1",
                name = "Studio Mix",
                createdAtEpochMs = 1_000L,
                tracks = sampleTracks,
              )
            ),
            selectedPlaylistId = selectedPlaylistId,
            onCreatePlaylistClick = {},
            onSelectPlaylist = { selectedPlaylistId = it },
            onPlayPlaylist = { playedPlaylistId = it.id },
            onDeletePlaylist = {},
          )
          com.example.ui.components.CreatePlaylistDialog(
            availableTracks = sampleTracks,
            onDismiss = {},
            onCreatePlaylist = { name, desc, ids ->
              createdName = name
              createdDesc = desc
              createdTrackIds = ids
            },
          )
        }
      }
    }

    composeTestRule.onNodeWithTag("playlist_card_shelf-pl-1").assertIsDisplayed().performClick()
    assertEquals("shelf-pl-1", selectedPlaylistId)
    composeTestRule.onNodeWithTag("play_playlist_shelf-pl-1").performClick()
    assertEquals("shelf-pl-1", playedPlaylistId)

    composeTestRule.onNodeWithTag("playlist_name_input").performScrollTo().performTextInput("Night Set")
    composeTestRule.onNodeWithTag("create_playlist_track_ui-pl-t1").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("create_playlist_track_ui-pl-t2").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("confirm_create_playlist_button").performClick()
    composeTestRule.waitForIdle()

    assertEquals("Night Set", createdName)
    assertEquals("", createdDesc)
    assertEquals(listOf("ui-pl-t1", "ui-pl-t2"), createdTrackIds)
  }

  @Test
  fun `loudness normalizer measures lufs normalizes quiet and loud tracks and player ui triggers actions`() {
    val sampleRate = 44_100
    val durationSamples = sampleRate * 2

    // 1. Quiet 1 kHz sine (~2000 peak -> roughly -27 LUFS) normalized up to -14 LUFS
    val quietPcm = ShortArray(durationSamples) { i ->
      (kotlin.math.sin(2.0 * kotlin.math.PI * 1_000.0 * i / sampleRate) * 2_000.0).toInt().toShort()
    }
    val quietNorm = com.example.audio.LoudnessNormalizerDsp.normalizePcm(
      pcmSamples = quietPcm,
      sampleRate = sampleRate,
      channelCount = 1,
      targetLufs = -14.0f,
    )
    assertTrue("Expected quiet input LUFS < -20, got ${quietNorm.inputLufs}", quietNorm.inputLufs < -20.0f)
    assertTrue(
      "Expected normalized quiet output near -14.0 LUFS, got ${quietNorm.outputLufs}",
      quietNorm.outputLufs in -15.5f..-12.5f,
    )
    assertTrue(
      "Expected true peak <= -0.9 dBFS, got ${quietNorm.truePeakDbfs}",
      quietNorm.truePeakDbfs <= -0.9f,
    )

    // 2. Loud 1 kHz sine (~30000 peak) normalized down to -16 LUFS
    val loudPcm = ShortArray(durationSamples) { i ->
      (kotlin.math.sin(2.0 * kotlin.math.PI * 1_000.0 * i / sampleRate) * 30_000.0).toInt().toShort()
    }
    val loudNorm = com.example.audio.LoudnessNormalizerDsp.normalizePcm(
      pcmSamples = loudPcm,
      sampleRate = sampleRate,
      channelCount = 1,
      targetLufs = -16.0f,
    )
    assertTrue("Expected negative gain applied to loud track, got ${loudNorm.appliedGainDb}", loudNorm.appliedGainDb < 0f)
    assertTrue(
      "Expected normalized loud output near -16.0 LUFS, got ${loudNorm.outputLufs}",
      loudNorm.outputLufs in -17.5f..-14.5f,
    )
    assertTrue(
      "Expected true peak <= -0.9 dBFS, got ${loudNorm.truePeakDbfs}",
      loudNorm.truePeakDbfs <= -0.9f,
    )

    // 3. Post-processed synthesized tracks of contrasting genres stay consistently matched
    val context = ApplicationProvider.getApplicationContext<Context>()
    val tempDir = File(context.cacheDir, "lufs_synth_test_${System.currentTimeMillis()}")
    try {
      val (_, ambientBuffer) = WavTrackSynthesizer.synthesizeWithRawBuffer(
        prompt = "Deep space ambient meditation pad 72 bpm",
        outputDir = tempDir,
      )
      val (_, technoBuffer) = WavTrackSynthesizer.synthesizeWithRawBuffer(
        prompt = "Energetic cyber techno bassline 128 bpm",
        outputDir = tempDir,
      )
      assertTrue(
        "Expected ambient track near -14 LUFS, got ${ambientBuffer.integratedLufs}",
        ambientBuffer.integratedLufs in -16.5f..-11.5f,
      )
      assertTrue(
        "Expected techno track near -14 LUFS, got ${technoBuffer.integratedLufs}",
        technoBuffer.integratedLufs in -16.5f..-11.5f,
      )
      assertTrue(
        "Expected consistent volume across contrasting tracks, diff=${kotlin.math.abs(ambientBuffer.integratedLufs - technoBuffer.integratedLufs)}",
        kotlin.math.abs(ambientBuffer.integratedLufs - technoBuffer.integratedLufs) < 3.0f,
      )
    } finally {
      tempDir.deleteRecursively()
    }

    // 4. CustomAudioPlayer Loudness Normalization DSP UI controls
    var selectedTarget: com.example.audio.LoudnessTarget? = null
    var normalizeTrackClicked = false
    var normalizeLibraryClicked = false
    val sampleTrack = GeneratedTrack(
      id = "lufs-ui-1",
      title = "Balanced Master",
      prompt = "Warm synthwave drive",
      genre = "Synthwave",
      bpm = 112,
      musicalKey = "A Min",
      durationMs = 10_000L,
      filePath = "/tmp/balanced.wav",
      fileSizeBytes = 250_000L,
      waveformPeaks = List(32) { 0.5f },
      createdAtEpochMs = 11_000L,
    )

    composeTestRule.setContent {
      SonicForgeTheme {
        Box(
          modifier = Modifier.verticalScroll(rememberScrollState()),
        ) {
          CustomAudioPlayer(
            track = sampleTrack,
            playbackState = AudioPlaybackState(
              currentTrackId = sampleTrack.id,
              isPrepared = true,
              positionMs = 1_000L,
              durationMs = 10_000L,
            ),
            canSkipPrevious = false,
            canSkipNext = false,
            onTogglePlayPause = {},
            onBeginSeek = {},
            onPreviewSeek = {},
            onCommitSeek = {},
            onSeekBy = {},
            onSkipPrevious = {},
            onSkipNext = {},
            onToggleLoop = {},
            onVolumeChange = {},
            onToggleMute = {},
            loudnessTarget = com.example.audio.LoudnessTarget.STREAMING_14,
            isNormalizing = false,
            onSelectLoudnessTarget = { selectedTarget = it },
            onNormalizeActiveTrack = { normalizeTrackClicked = true },
            onNormalizeLibrary = { normalizeLibraryClicked = true },
            bufferDiagnostics = com.example.audio.RawBufferDiagnostics(
              sampleRateHz = 44_100,
              channelCount = 1,
              sampleCount = 44_100,
              durationMs = 1_000L,
              rmsDb = -12.0f,
              spectralCentroidHz = 950,
              fourierSpectrumBins = List(12) { 0.4f },
              detectedBpm = 112,
              bpmConfidence = 0.85f,
              integratedLufs = -14.1f,
              bridgeSource = "Fourier Synth",
            ),
          )
        }
      }
    }

    composeTestRule.onNodeWithTag("player_loudness_dsp_card").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("player_lufs_badge").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("lufs_target_mobile_16").performScrollTo().performClick()
    assertEquals(com.example.audio.LoudnessTarget.MOBILE_16, selectedTarget)
    composeTestRule.onNodeWithTag("normalize_track_button").performScrollTo().performClick()
    assertTrue(normalizeTrackClicked)
    composeTestRule.onNodeWithTag("normalize_library_button").performScrollTo().performClick()
    assertTrue(normalizeLibraryClicked)
  }

  @Test
  fun `bulk rename formats prefix and timestamp patterns updates room titles and ui selects and renames tracks`() = runBlocking {
    val track1 = GeneratedTrack(
      id = "br-1",
      title = "Neon Drive",
      prompt = "Synthwave drive",
      genre = "Synthwave",
      bpm = 112,
      musicalKey = "C Min",
      durationMs = 10_000L,
      filePath = "/tmp/neon.wav",
      fileSizeBytes = 100_000L,
      waveformPeaks = List(16) { 0.5f },
      createdAtEpochMs = 1_700_000_000_000L,
    )
    val track2 = track1.copy(
      id = "br-2",
      title = "Rainy Beat",
      genre = "Lo-Fi Chill",
      createdAtEpochMs = 1_700_000_060_000L,
    )

    // 1. Prefix + Title formatting
    val prefixPreviews = com.example.model.TrackBulkRenameFormatter.generatePreviews(
      tracks = listOf(track1, track2),
      config = com.example.model.BulkRenameConfig(
        mode = com.example.model.BulkRenameMode.PREFIX_TITLE,
        prefix = "Album",
      ),
    )
    assertEquals("Album - Neon Drive", prefixPreviews[0].newTitle)
    assertEquals("Album - Rainy Beat", prefixPreviews[1].newTitle)

    // 2. Prefix + Numbered formatting
    val numberedPreviews = com.example.model.TrackBulkRenameFormatter.generatePreviews(
      tracks = listOf(track1, track2),
      config = com.example.model.BulkRenameConfig(
        mode = com.example.model.BulkRenameMode.PREFIX_NUMBERED,
        prefix = "Session",
      ),
    )
    assertEquals("Session_01", numberedPreviews[0].newTitle)
    assertEquals("Session_02", numberedPreviews[1].newTitle)

    // 3. Timestamp pattern formatting with collision disambiguation
    val sameDayPreviews = com.example.model.TrackBulkRenameFormatter.generatePreviews(
      tracks = listOf(track1, track2),
      config = com.example.model.BulkRenameConfig(
        mode = com.example.model.BulkRenameMode.TIMESTAMP,
        prefix = "Mix",
        timestampPattern = "yyyy-MM-dd",
      ),
    )
    assertTrue(sameDayPreviews[0].newTitle.startsWith("Mix_"))
    assertTrue(sameDayPreviews[0].newTitle.endsWith("_01"))
    assertTrue(sameDayPreviews[1].newTitle.endsWith("_02"))
    assertEquals(
      false,
      com.example.model.TrackBulkRenameFormatter.isValidTimestampPattern("invalid_qqq_zzz"),
    )

    // 4. Room DAO updateTrackTitle preserves playlists and updates titles
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = Room.inMemoryDatabaseBuilder(context, SonicForgeDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    try {
      val trackDao = db.generatedTrackDao()
      val playlistDao = db.playlistDao()
      trackDao.insertTracks(listOf(track1.toEntity(), track2.toEntity()))
      playlistDao.insertPlaylist(
        com.example.data.local.PlaylistEntity(
          id = "br-pl-1",
          name = "Master Set",
          createdAtEpochMs = 10_000L,
        )
      )
      playlistDao.addTrackToPlaylist(
        com.example.data.local.PlaylistTrackCrossRef("br-pl-1", "br-1", 10_001L)
      )

      trackDao.updateTrackTitle("br-1", "Album - Neon Drive")
      trackDao.updateTrackTitle("br-2", "Album - Rainy Beat")

      assertEquals("Album - Neon Drive", trackDao.getTrackById("br-1")?.title)
      assertEquals("Album - Rainy Beat", trackDao.getTrackById("br-2")?.title)
      val playlistWithTracks = playlistDao.getPlaylistWithTracksOnce("br-pl-1")
      assertEquals(1, playlistWithTracks?.tracks?.size)
      assertEquals("Album - Neon Drive", playlistWithTracks?.tracks?.first()?.title)
    } finally {
      db.close()
    }

    // 5. Compose UI verification for multi-select toolbar, checkboxes, and BulkRenameDialog
    val selectedIds = androidx.compose.runtime.mutableStateOf(setOf<String>())
    val showDialog = androidx.compose.runtime.mutableStateOf(false)
    var confirmedConfig: com.example.model.BulkRenameConfig? = null
    val uiTracks = listOf(
      track1.copy(id = "ui-br-1", title = "First Track"),
      track2.copy(id = "ui-br-2", title = "Second Track"),
    )

    composeTestRule.setContent {
      SonicForgeTheme {
        androidx.compose.foundation.layout.Column {
          com.example.ui.components.BulkSelectionBar(
            selectedCount = selectedIds.value.size,
            allVisibleSelected = selectedIds.value.size == uiTracks.size,
            onToggleSelectAll = {
              selectedIds.value = uiTracks.map { it.id }.toSet()
            },
            onOpenBulkRename = { showDialog.value = true },
            onExitSelectionMode = {},
          )
          uiTracks.forEach { t ->
            com.example.ui.GeneratedTrackRow(
              track = t,
              isSelected = false,
              isPlaying = false,
              isSelectionMode = true,
              isBulkSelected = t.id in selectedIds.value,
              onToggleBulkSelect = {
                selectedIds.value = if (t.id in selectedIds.value) {
                  selectedIds.value - t.id
                } else {
                  selectedIds.value + t.id
                }
              },
              onClick = {},
              onEditOrganization = {},
              onTrimTrack = {},
              onExportMp3 = {},
              onDelete = {},
            )
          }
          if (showDialog.value) {
            com.example.ui.components.BulkRenameDialog(
              selectedTracks = uiTracks.filter { it.id in selectedIds.value },
              isRenaming = false,
              onDismiss = { showDialog.value = false },
              onConfirmRename = {
                confirmedConfig = it
                showDialog.value = false
              },
            )
          }
        }
      }
    }

    composeTestRule.onNodeWithTag("select_track_checkbox_ui-br-1").assertIsDisplayed().performClick()
    composeTestRule.onNodeWithTag("select_track_checkbox_ui-br-2").assertIsDisplayed().performClick()
    composeTestRule.onNodeWithTag("bulk_rename_open_button").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("bulk_rename_dialog").assertIsDisplayed()
    composeTestRule.onNodeWithTag("bulk_rename_prefix_input").performScrollTo().performTextInput("LiveSet")
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("bulk_rename_preview_list").performScrollTo().assertIsDisplayed()

    composeTestRule.onNodeWithTag("rename_mode_timestamp").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("bulk_rename_pattern_input").performScrollTo().assertIsDisplayed()

    composeTestRule.onNodeWithTag("rename_mode_prefix_numbered").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("confirm_bulk_rename_button").performClick()
    composeTestRule.waitForIdle()

    assertEquals(com.example.model.BulkRenameMode.PREFIX_NUMBERED, confirmedConfig?.mode)
    assertEquals("LiveSet", confirmedConfig?.prefix)
  }

  @Test
  fun `ai auto tagger analyzes acoustic characteristics and suggests ambient upbeat and lofi categories with firebase notice`(): Unit = runBlocking {
    val tagger = com.example.ai.PlaceholderTrackAutoTagger()

    val ambientResult = tagger.analyzeTrack(
      com.example.ai.TrackAutoTagInput(
        title = "Deep Orbit",
        prompt = "Spacious drift pad",
        genre = "Electronic",
        bpm = 68,
        musicalKey = "D Min",
        durationMs = 12_000L,
        spectralCentroidHz = 620,
        rmsDb = -18.0f,
        integratedLufs = -16.0f,
        fourierSpectrumBins = listOf(0.8f, 0.7f, 0.5f, 0.2f, 0.1f, 0.1f),
      )
    )
    assertEquals("Ambient", ambientResult.suggestedCategory)
    assertTrue(ambientResult.suggestedTags.contains("ambient"))
    assertTrue(ambientResult.isPlaceholder)
    assertEquals(
      com.example.ai.PlaceholderTrackAutoTagger.FIREBASE_REQUIRED_NOTICE,
      ambientResult.noticeMessage,
    )

    val upbeatResult = tagger.analyzeTrack(
      com.example.ai.TrackAutoTagInput(
        title = "Pulse Surge",
        prompt = "Festival club groove",
        genre = "Electronic",
        bpm = 128,
        musicalKey = "F Min",
        durationMs = 10_000L,
        spectralCentroidHz = 1_850,
        rmsDb = -12.0f,
        integratedLufs = -13.5f,
        fourierSpectrumBins = listOf(0.5f, 0.5f, 0.6f, 0.7f, 0.8f, 0.75f),
      )
    )
    assertEquals("Upbeat", upbeatResult.suggestedCategory)
    assertTrue(upbeatResult.suggestedTags.contains("upbeat"))
    assertTrue(upbeatResult.isPlaceholder)

    val lofiResult = tagger.analyzeTrack(
      com.example.ai.TrackAutoTagInput(
        title = "Rainy Tape",
        prompt = "Warm rhodes study loop",
        genre = "General",
        bpm = 84,
        musicalKey = "A Min",
        durationMs = 11_000L,
        spectralCentroidHz = 890,
        rmsDb = -15.0f,
        integratedLufs = -14.2f,
        fourierSpectrumBins = listOf(0.85f, 0.8f, 0.7f, 0.3f, 0.2f, 0.15f),
      )
    )
    assertEquals("Lo-Fi", lofiResult.suggestedCategory)
    assertTrue(lofiResult.suggestedTags.contains("lo-fi"))

    val uiTrack = GeneratedTrack(
      id = "ai-tag-ui-1",
      title = "Rainy Tape",
      prompt = "Warm rhodes study loop",
      genre = "General",
      bpm = 84,
      musicalKey = "A Min",
      durationMs = 11_000L,
      filePath = "/tmp/rainy.wav",
      fileSizeBytes = 120_000L,
      waveformPeaks = List(16) { 0.4f },
      createdAtEpochMs = 10_000L,
      category = "General",
      tags = listOf("Draft"),
    )

    composeTestRule.setContent {
      SonicForgeTheme {
        androidx.compose.foundation.layout.Column {
          com.example.ui.AiAutoTagNoticeCard(
            result = ambientResult,
            onDismiss = {},
          )
          com.example.ui.TrackOrganizationDialog(
            track = uiTrack,
            isSuggestingTags = false,
            onSuggestAutoTags = { callback -> callback(lofiResult) },
            onDismiss = {},
            onSave = { _, _ -> },
          )
        }
      }
    }

    composeTestRule.onNodeWithTag("ai_autotag_banner").assertIsDisplayed()
    composeTestRule.onNodeWithTag("ai_autotag_notice").assertIsDisplayed()
    composeTestRule.onNodeWithTag("ai_suggest_tags_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("org_ai_autotag_notice").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun `segmented audio downloader downloads multi-range http wav across parallel connections and url dialog triggers actions`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val tempDir = File(context.cacheDir, "dl_test_${System.currentTimeMillis()}")
    tempDir.mkdirs()

    // Create a real >= 96 KB WAV payload to exercise 4-segment HTTP range reassembly
    val payloadBytes = ByteArray(98_304) { idx ->
      when (idx) {
        0 -> 'R'.code.toByte()
        1 -> 'I'.code.toByte()
        2 -> 'F'.code.toByte()
        3 -> 'F'.code.toByte()
        8 -> 'W'.code.toByte()
        9 -> 'A'.code.toByte()
        10 -> 'V'.code.toByte()
        11 -> 'E'.code.toByte()
        else -> (idx and 0x7F).toByte()
      }
    }

    val serverSocket = java.net.ServerSocket(0)
    val port = serverSocket.localPort
    val serverRunning = java.util.concurrent.atomic.AtomicBoolean(true)
    val serverThread = Thread {
      while (serverRunning.get() && !serverSocket.isClosed) {
        try {
          val client = serverSocket.accept()
          Thread {
            client.use { socket ->
              val reader = java.io.BufferedReader(java.io.InputStreamReader(socket.getInputStream()))
              val requestLine = reader.readLine() ?: return@use
              val headers = mutableMapOf<String, String>()
              while (true) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) break
                val colon = line.indexOf(':')
                if (colon > 0) {
                  headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
                }
              }

              val out = socket.getOutputStream()
              if (requestLine.startsWith("HEAD ")) {
                val resp = buildString {
                  append("HTTP/1.1 200 OK\r\n")
                  append("Content-Length: ${payloadBytes.size}\r\n")
                  append("Accept-Ranges: bytes\r\n")
                  append("Content-Type: audio/wav\r\n")
                  append("Connection: close\r\n\r\n")
                }
                out.write(resp.toByteArray())
                out.flush()
              } else {
                val rangeHeader = headers["range"]
                if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                  val parts = rangeHeader.removePrefix("bytes=").split("-")
                  val start = parts[0].toInt()
                  val end = parts[1].toInt().coerceAtMost(payloadBytes.size - 1)
                  val length = end - start + 1
                  val resp = buildString {
                    append("HTTP/1.1 206 Partial Content\r\n")
                    append("Content-Length: $length\r\n")
                    append("Content-Range: bytes $start-$end/${payloadBytes.size}\r\n")
                    append("Content-Type: audio/wav\r\n")
                    append("Connection: close\r\n\r\n")
                  }
                  out.write(resp.toByteArray())
                  out.write(payloadBytes, start, length)
                  out.flush()
                } else {
                  val resp = buildString {
                    append("HTTP/1.1 200 OK\r\n")
                    append("Content-Length: ${payloadBytes.size}\r\n")
                    append("Content-Type: audio/wav\r\n")
                    append("Connection: close\r\n\r\n")
                  }
                  out.write(resp.toByteArray())
                  out.write(payloadBytes)
                  out.flush()
                }
              }
            }
          }.start()
        } catch (_: Exception) {
          break
        }
      }
    }
    serverThread.start()

    try {
      // Policy check: YouTube ripping links must be rejected cleanly
      var blockedYoutube = false
      try {
        com.example.audio.SegmentedAudioDownloader.validateAudioUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
      } catch (_: IllegalArgumentException) {
        blockedYoutube = true
      }
      assertTrue("Expected YouTube URL to be rejected by Play policy validator", blockedYoutube)

      val downloader = com.example.audio.SegmentedAudioDownloader()
      val downloaded = downloader.downloadDirectAudioUrl(
        rawUrl = "http://127.0.0.1:$port/studio_loop.wav",
        outputDir = tempDir,
        maxConnections = 4,
      )
      assertEquals(4, downloaded.connectionsUsed)
      assertEquals("wav", downloaded.extension)
      assertEquals("studio_loop", downloaded.displayTitle)
      assertEquals(payloadBytes.size.toLong(), downloaded.totalBytes)
      assertTrue(payloadBytes.contentEquals(downloaded.file.readBytes()))
    } finally {
      serverRunning.set(false)
      serverSocket.close()
      tempDir.deleteRecursively()
    }

    // Compose UI verification of AudioUrlDownloadDialog
    var submittedUrl = ""
    var submittedConnections = 0
    composeTestRule.setContent {
      SonicForgeTheme {
        com.example.ui.components.AudioUrlDownloadDialog(
          isDownloading = false,
          progress = null,
          statusMessage = null,
          errorMessage = null,
          onDismiss = {},
          onStartDirectDownload = { url, conn ->
            submittedUrl = url
            submittedConnections = conn
          },
          onSubmitAria2Rpc = { _, _, _, _ -> },
        )
      }
    }

    composeTestRule.onNodeWithTag("audio_url_download_dialog").assertIsDisplayed()
    composeTestRule.onNodeWithTag("download_mode_aria2_rpc").performClick()
    composeTestRule.onNodeWithTag("aria2_rpc_endpoint_input").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("download_mode_segmented").performClick()
    composeTestRule.onNodeWithTag("download_url_input")
      .performScrollTo()
      .performTextInput("https://cdn.example.com/tracks/ambient_pad.wav")
    composeTestRule.onNodeWithTag("download_conn_x8").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("confirm_download_url_button").performClick()
    composeTestRule.waitForIdle()

    assertEquals("https://cdn.example.com/tracks/ambient_pad.wav", submittedUrl)
    assertEquals(8, submittedConnections)
  }

  @Test
  fun `crossfade dsp blends equal power dual deck audio resamples mismatched channels and player ui triggers crossfade controls`() {
    // 1. Constant equal-power gain curve check: out^2 + in^2 == 1.0 across entire transition
    listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { p ->
      val (outG, inG) = com.example.audio.CrossfadeDsp.computeEqualPowerGains(p)
      val powerSum = outG * outG + inG * inG
      assertTrue("Expected equal-power sum ~= 1.0 at p=$p, got $powerSum", kotlin.math.abs(powerSum - 1f) < 0.001f)
    }
    val (midOut, midIn) = com.example.audio.CrossfadeDsp.computeEqualPowerGains(0.5f)
    assertTrue(kotlin.math.abs(midOut - 0.7071f) < 0.01f)
    assertTrue(kotlin.math.abs(midIn - 0.7071f) < 0.01f)

    // 2. Effective crossfade duration caps safely on short tracks and returns 0 when disabled
    assertEquals(
      0L,
      com.example.audio.CrossfadeDsp.computeEffectiveCrossfadeMs(
        com.example.audio.CrossfadeSettings(enabled = false, durationSec = 4f),
        outgoingDurationMs = 20_000L,
        incomingDurationMs = 20_000L,
      ),
    )
    assertEquals(
      2_700L,
      com.example.audio.CrossfadeDsp.computeEffectiveCrossfadeMs(
        com.example.audio.CrossfadeSettings(enabled = true, durationSec = 8f),
        outgoingDurationMs = 6_000L,
        incomingDurationMs = 15_000L,
      ),
    )
    assertEquals(
      4_000L,
      com.example.audio.CrossfadeDsp.computeEffectiveCrossfadeMs(
        com.example.audio.CrossfadeSettings(enabled = true, durationSec = 4f),
        outgoingDurationMs = 20_000L,
        incomingDurationMs = 20_000L,
      ),
    )

    // 3. Dual-deck chunk mixing transitions smoothly from deck A amplitude to deck B amplitude
    val frames = 128
    val channels = 2
    val outgoingChunk = ShortArray(frames * channels) { 10_000 }
    val incomingChunk = ShortArray(frames * channels) { -10_000 }
    val mixedChunk = ShortArray(frames * channels)
    com.example.audio.CrossfadeDsp.mixDualDeckChunk(
      outgoing = outgoingChunk,
      incoming = incomingChunk,
      frameCount = frames,
      channelCount = channels,
      progressStart = 0f,
      progressEnd = 1f,
      masterGain = 1f,
      output = mixedChunk,
    )
    assertTrue("Expected start of crossfade to match outgoing (+10000), got ${mixedChunk[0]}", mixedChunk[0] > 9_500)
    assertTrue("Expected end of crossfade to match incoming (-10000), got ${mixedChunk.last()}", mixedChunk.last() < -9_500)

    // 4. Mono-to-stereo & sample-rate chunk extraction keeps stereo L/R coherent
    val monoBuffer = com.example.audio.RawAudioBuffer(
      sampleRate = 22_050,
      channelCount = 1,
      pcmSamples = ShortArray(100) { i -> (i * 100).toShort() },
      durationMs = 5L,
      rmsAmplitude = 0.2f,
      peakAmplitude = 0.4f,
      fourierSpectrumBins = List(12) { 0.2f },
      spectralCentroidHz = 600,
      bridgeSource = "Test",
    )
    val stereoDest = ShortArray(64 * 2)
    val consumed = com.example.audio.CrossfadeDsp.extractResampledChunk(
      sourceBuffer = monoBuffer,
      sourceFrameStart = 0,
      targetFrames = 64,
      targetSampleRate = 44_100,
      targetChannels = 2,
      destination = stereoDest,
    )
    assertTrue(consumed in 30..35)
    assertEquals(stereoDest[10], stereoDest[11])

    // 5. AudioPlayerController crossfade state mutators
    val context = ApplicationProvider.getApplicationContext<Context>()
    val controller = com.example.audio.AudioPlayerController.getInstance(context)
    controller.selectCrossfadePreset(6f)
    assertTrue(controller.state.value.crossfade.isActive)
    assertEquals(6f, controller.state.value.crossfade.durationSec)
    controller.setCrossfadeEnabled(false)
    assertEquals(false, controller.state.value.crossfade.isActive)

    // 6. CustomAudioPlayer Crossfade UI controls & active transition banner
    var enabledToggled: Boolean? = null
    var presetSelected: Float? = null
    val sampleTrack = GeneratedTrack(
      id = "cf-ui-1",
      title = "Solaris Fade",
      prompt = "Ambient synthwave",
      genre = "Ambient",
      bpm = 96,
      musicalKey = "A Min",
      durationMs = 15_000L,
      filePath = "/tmp/solaris.wav",
      fileSizeBytes = 250_000L,
      waveformPeaks = List(24) { 0.4f },
      createdAtEpochMs = 5_000L,
    )

    composeTestRule.setContent {
      SonicForgeTheme {
        Box(modifier = Modifier.verticalScroll(rememberScrollState())) {
          CustomAudioPlayer(
            track = sampleTrack,
            playbackState = AudioPlaybackState(
              currentTrackId = sampleTrack.id,
              isPrepared = true,
              isPlaying = true,
              positionMs = 12_500L,
              durationMs = 15_000L,
              crossfade = com.example.audio.CrossfadeSettings(enabled = true, durationSec = 4f),
              isCrossfading = true,
              crossfadeProgress = 0.62f,
              crossfadeNextTrackTitle = "Midnight Echoes",
            ),
            canSkipPrevious = true,
            canSkipNext = true,
            onTogglePlayPause = {},
            onBeginSeek = {},
            onPreviewSeek = {},
            onCommitSeek = {},
            onSeekBy = {},
            onSkipPrevious = {},
            onSkipNext = {},
            onToggleLoop = {},
            onVolumeChange = {},
            onToggleMute = {},
            onCrossfadeEnabledChange = { enabledToggled = it },
            onSelectCrossfadePreset = { presetSelected = it },
          )
        }
      }
    }

    composeTestRule.onNodeWithTag("player_crossfade_card").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("player_crossfade_badge").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("player_crossfade_active_banner").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("player_crossfade_slider").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("crossfade_preset_6").performScrollTo().performClick()
    assertEquals(6f, presetSelected)
    composeTestRule.onNodeWithTag("player_crossfade_toggle").performScrollTo().performClick()
    assertEquals(false, enabledToggled)
  }

  @Test
  fun `volume envelope dsp shapes gain curves mutes segments persists in room and switches visual editing modes`() = runBlocking {
    // 1. Paint points & interpolate envelope gain
    var env = com.example.audio.TrackAudioEnvelope()
    env = com.example.audio.VolumeEnvelopeDsp.paintEnvelopePoint(env, 0.2f, 0.5f)
    env = com.example.audio.VolumeEnvelopeDsp.paintEnvelopePoint(env, 0.6f, 1.5f)
    assertTrue(env.isEdited)
    assertEquals(0.5f, com.example.audio.VolumeEnvelopeDsp.sampleEnvelopeGainAt(env, 0.2f), 0.01f)
    assertEquals(1.0f, com.example.audio.VolumeEnvelopeDsp.sampleEnvelopeGainAt(env, 0.4f), 0.01f)
    assertEquals(1.5f, com.example.audio.VolumeEnvelopeDsp.sampleEnvelopeGainAt(env, 0.6f), 0.01f)

    // 2. Add, merge, and tap-toggle muted segments
    env = com.example.audio.VolumeEnvelopeDsp.addOrMergeMutedSegment(env, 0.70f, 0.80f)
    env = com.example.audio.VolumeEnvelopeDsp.addOrMergeMutedSegment(env, 0.78f, 0.90f)
    assertEquals(1, env.mutedSegments.size)
    assertEquals(0.70f, env.mutedSegments.first().startFraction, 0.005f)
    assertEquals(0.90f, env.mutedSegments.first().endFraction, 0.005f)
    assertEquals(0f, com.example.audio.VolumeEnvelopeDsp.sampleEnvelopeGainAt(env, 0.80f), 0.001f)

    // Tapping inside a muted region unmutes it cleanly
    val afterUntap = com.example.audio.VolumeEnvelopeDsp.toggleMuteAtTap(env, 0.82f)
    assertTrue(afterUntap.mutedSegments.isEmpty())

    // 3. Apply envelope + mute to PCM buffer chunk
    val totalFrames = 100
    val pcm = ShortArray(totalFrames) { 10_000 }
    com.example.audio.VolumeEnvelopeDsp.applyEnvelopeToChunk(
      samples = pcm,
      frameCount = totalFrames,
      channelCount = 1,
      startFrame = 0,
      totalFrames = totalFrames,
      envelope = env,
    )
    assertEquals(5_000, pcm[20].toInt())
    assertEquals(15_000, pcm[60].toInt())
    assertEquals(0, pcm[80].toInt())

    // 4. JSON roundtrip & Room DAO persistence
    val decoded = com.example.audio.TrackAudioEnvelope.fromJsonString(env.toJsonString())
    assertEquals(env.points.size, decoded.points.size)
    assertEquals(env.mutedSegments.size, decoded.mutedSegments.size)

    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = Room.inMemoryDatabaseBuilder(context, SonicForgeDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    try {
      val dao = db.generatedTrackDao()
      val track = GeneratedTrack(
        id = "env-track-1",
        title = "Envelope Synth",
        prompt = "Warm synthwave",
        genre = "Synthwave",
        bpm = 110,
        musicalKey = "A Min",
        durationMs = 10_000L,
        filePath = "/tmp/env.wav",
        fileSizeBytes = 100_000L,
        waveformPeaks = List(32) { 0.5f },
        createdAtEpochMs = 1_000L,
      )
      dao.insertTrack(track.toEntity())
      dao.updateAudioEnvelope("env-track-1", env.toJsonString())
      val loaded = dao.getTrackById("env-track-1")!!.toDomain()
      assertTrue(loaded.audioEnvelope.isEdited)
      assertEquals(2, loaded.audioEnvelope.points.size)
      assertEquals(1, loaded.audioEnvelope.mutedSegments.size)
    } finally {
      db.close()
    }

    // 5. CustomAudioPlayer mode switch & reset button
    var selectedMode = com.example.audio.WaveformInteractionMode.SCRUB
    var resetCalled = false
    val uiTrack = GeneratedTrack(
      id = "env-ui-1",
      title = "Curve Master",
      prompt = "Ambient synth",
      genre = "Ambient",
      bpm = 80,
      musicalKey = "C Maj",
      durationMs = 12_000L,
      filePath = "/tmp/curve.wav",
      fileSizeBytes = 120_000L,
      waveformPeaks = List(24) { 0.6f },
      createdAtEpochMs = 2_000L,
      audioEnvelope = env,
    )

    composeTestRule.setContent {
      SonicForgeTheme {
        Box(modifier = Modifier.verticalScroll(rememberScrollState())) {
          CustomAudioPlayer(
            track = uiTrack,
            playbackState = AudioPlaybackState(
              currentTrackId = uiTrack.id,
              isPrepared = true,
              positionMs = 2_000L,
              durationMs = 12_000L,
              activeEnvelope = env,
            ),
            canSkipPrevious = false,
            canSkipNext = false,
            onTogglePlayPause = {},
            onBeginSeek = {},
            onPreviewSeek = {},
            onCommitSeek = {},
            onSeekBy = {},
            onSkipPrevious = {},
            onSkipNext = {},
            onToggleLoop = {},
            onVolumeChange = {},
            onToggleMute = {},
            interactionMode = selectedMode,
            onSelectInteractionMode = { selectedMode = it },
            onResetAudioEnvelope = { resetCalled = true },
          )
        }
      }
    }

    composeTestRule.onNodeWithTag("waveform_mode_envelope").performScrollTo().performClick()
    assertEquals(com.example.audio.WaveformInteractionMode.ENVELOPE, selectedMode)
    composeTestRule.onNodeWithTag("waveform_mode_mute").performScrollTo().performClick()
    assertEquals(com.example.audio.WaveformInteractionMode.MUTE, selectedMode)
    composeTestRule.onNodeWithTag("waveform_reset_envelope_button").performScrollTo().performClick()
    assertTrue(resetCalled)
  }

  @Test
  fun `bottom waveform bar renders persistently across screen and can be eliminated and restored`() {
    val sampleTrack = GeneratedTrack(
      id = "bw-track-1",
      title = "Floating Horizon",
      prompt = "Ambient drift",
      genre = "Ambient",
      bpm = 82,
      musicalKey = "F Maj",
      durationMs = 14_000L,
      filePath = "/tmp/horizon.wav",
      fileSizeBytes = 180_000L,
      waveformPeaks = List(32) { 0.55f },
      createdAtEpochMs = 3_000L,
    )
    val showBottomState = androidx.compose.runtime.mutableStateOf(true)

    composeTestRule.setContent {
      SonicForgeTheme {
        com.example.ui.SonicForgeScreen(
          uiState = com.example.ui.SonicForgeUiState(
            tracks = listOf(sampleTrack),
            allTracks = listOf(sampleTrack),
            isInitializing = false,
            showBottomWaveformBar = showBottomState.value,
            playback = AudioPlaybackState(
              currentTrackId = sampleTrack.id,
              isPrepared = true,
              isPlaying = false,
              positionMs = 3_500L,
              durationMs = 14_000L,
            ),
          ),
          onSelectThemeMode = {},
          onSelectEngine = {},
          onPromptChanged = {},
          onApplyPreset = {},
          onGenerateTrack = {},
          onImportAudioClick = {},
          onSelectCategory = {},
          onSearchQueryChanged = {},
          onEditOrganizationClick = {},
          onDismissEditOrganization = {},
          onSaveOrganization = { _, _, _ -> },
          onTrimTrackClick = {},
          onDismissTrimDialog = {},
          onConfirmTrim = { _, _, _, _ -> },
          onExportMp3Click = {},
          onDismissExportDialog = {},
          onExportMp3ToMusic = {},
          onExportMp3ToFolder = {},
          onShareMp3 = {},
          onRequestNotificationPermission = {},
          onOpenNotificationSettings = {},
          onSelectTrack = {},
          onDeleteTrack = {},
          onTogglePlayPause = {},
          onBeginSeek = {},
          onPreviewSeek = {},
          onCommitSeek = {},
          onSeekBy = {},
          onSkipPrevious = {},
          onSkipNext = {},
          onToggleLoop = {},
          onVolumeChange = {},
          onToggleMute = {},
          onSetBottomWaveformVisible = { showBottomState.value = it },
        )
      }
    }

    composeTestRule.onNodeWithTag("bottom_waveform_bar").assertIsDisplayed()
    composeTestRule.onNodeWithTag("bottom_bar_waveform").assertIsDisplayed()

    // User can eliminate the persistent bottom waveform bar at any time
    composeTestRule.onNodeWithTag("eliminate_bottom_waveform_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("bottom_waveform_bar").assertDoesNotExist()

    // User can optionally bring it back from the header toggle
    composeTestRule.onNodeWithTag("restore_bottom_waveform_button").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("bottom_waveform_bar").assertIsDisplayed()
  }

  @Test
  fun `eight band graphic equalizer boosts targeted frequencies with presets and updates player ui`() {
    val sampleRate = 44_100
    val frames = sampleRate // 1 second
    val lowTone60Hz = ShortArray(frames) { i ->
      (kotlin.math.sin(2.0 * kotlin.math.PI * 60.0 * i / sampleRate) * 6_000.0).toInt().toShort()
    }
    val highTone6kHz = ShortArray(frames) { i ->
      (kotlin.math.sin(2.0 * kotlin.math.PI * 6_000.0 * i / sampleRate) * 6_000.0).toInt().toShort()
    }
    val vocalTone1kHz = ShortArray(frames) { i ->
      (kotlin.math.sin(2.0 * kotlin.math.PI * 1_000.0 * i / sampleRate) * 6_000.0).toInt().toShort()
    }

    fun rms(samples: ShortArray): Double {
      val start = samples.size / 4
      var sum = 0.0
      for (i in start until samples.size) {
        val v = samples[i] / 32768.0
        sum += v * v
      }
      return kotlin.math.sqrt(sum / (samples.size - start))
    }

    // 1. Bass Boost amplifies 60 Hz substantially while leaving 6 kHz virtually untouched
    val bassBoost = com.example.audio.EqualizerSettings().withPreset(com.example.audio.EqPreset.BASS_BOOST)
    val lowBassOut = com.example.audio.GraphicEqualizerDsp.processPcmOffline(lowTone60Hz, sampleRate, 1, bassBoost)
    val highBassOut = com.example.audio.GraphicEqualizerDsp.processPcmOffline(highTone6kHz, sampleRate, 1, bassBoost)
    val lowBoostRatio = rms(lowBassOut) / rms(lowTone60Hz)
    val highRatioInBass = rms(highBassOut) / rms(highTone6kHz)
    assertTrue("Expected 60 Hz gain boost > 1.8x, got $lowBoostRatio", lowBoostRatio > 1.8)
    assertTrue("Expected 6 kHz near unity under Bass Boost, got $highRatioInBass", highRatioInBass in 0.90..1.15)

    // 2. Treble Boost amplifies 6 kHz strongly while leaving 60 Hz near unity
    val trebleBoost = com.example.audio.EqualizerSettings().withPreset(com.example.audio.EqPreset.TREBLE_BOOST)
    val highTrebleOut = com.example.audio.GraphicEqualizerDsp.processPcmOffline(highTone6kHz, sampleRate, 1, trebleBoost)
    val lowTrebleOut = com.example.audio.GraphicEqualizerDsp.processPcmOffline(lowTone60Hz, sampleRate, 1, trebleBoost)
    assertTrue("Expected 6 kHz gain boost > 1.7x", rms(highTrebleOut) / rms(highTone6kHz) > 1.7)
    assertTrue("Expected 60 Hz near unity under Treble Boost", (rms(lowTrebleOut) / rms(lowTone60Hz)) in 0.90..1.15)

    // 3. Vocal Enhancer amplifies 1 kHz vocal band and attenuates 60 Hz rumble
    val vocalBoost = com.example.audio.EqualizerSettings().withPreset(com.example.audio.EqPreset.VOCAL_ENHANCER)
    val vocal1kOut = com.example.audio.GraphicEqualizerDsp.processPcmOffline(vocalTone1kHz, sampleRate, 1, vocalBoost)
    val vocalLowOut = com.example.audio.GraphicEqualizerDsp.processPcmOffline(lowTone60Hz, sampleRate, 1, vocalBoost)
    assertTrue("Expected 1 kHz vocal boost > 1.6x", rms(vocal1kOut) / rms(vocalTone1kHz) > 1.6)
    assertTrue("Expected 60 Hz attenuated under Vocal Enhancer", rms(vocalLowOut) < rms(lowTone60Hz))

    // 4. Custom band adjustment transitions preset cleanly to CUSTOM
    val customEq = vocalBoost.withBandGain(0, 9.0f)
    assertEquals(com.example.audio.EqPreset.CUSTOM, customEq.preset)
    assertEquals(9.0f, customEq.normalizedBands[0], 0.01f)

    // 5. AudioPlayerController EQ mutators
    val context = ApplicationProvider.getApplicationContext<Context>()
    val controller = com.example.audio.AudioPlayerController.getInstance(context)
    controller.selectEqualizerPreset(com.example.audio.EqPreset.TREBLE_BOOST)
    assertTrue(controller.state.value.equalizer.isActive)
    assertEquals(com.example.audio.EqPreset.TREBLE_BOOST, controller.state.value.equalizer.preset)
    controller.setEqualizerBandGain(2, -4.5f)
    assertEquals(com.example.audio.EqPreset.CUSTOM, controller.state.value.equalizer.preset)
    controller.resetEqualizer()
    assertEquals(false, controller.state.value.equalizer.isActive)

    // 6. CustomAudioPlayer 8-band EQ card UI verification
    var pickedPreset: com.example.audio.EqPreset? = null
    var eqResetClicked = false
    var eqEnabledToggled: Boolean? = null
    val sampleTrack = GeneratedTrack(
      id = "eq-ui-1",
      title = "Crystal Spectrum",
      prompt = "Synthwave master",
      genre = "Synthwave",
      bpm = 114,
      musicalKey = "E Min",
      durationMs = 12_000L,
      filePath = "/tmp/crystal.wav",
      fileSizeBytes = 250_000L,
      waveformPeaks = List(24) { 0.5f },
      createdAtEpochMs = 4_000L,
    )

    composeTestRule.setContent {
      SonicForgeTheme {
        Box(modifier = Modifier.verticalScroll(rememberScrollState())) {
          CustomAudioPlayer(
            track = sampleTrack,
            playbackState = AudioPlaybackState(
              currentTrackId = sampleTrack.id,
              isPrepared = true,
              positionMs = 2_000L,
              durationMs = 12_000L,
              equalizer = bassBoost,
            ),
            canSkipPrevious = false,
            canSkipNext = false,
            onTogglePlayPause = {},
            onBeginSeek = {},
            onPreviewSeek = {},
            onCommitSeek = {},
            onSeekBy = {},
            onSkipPrevious = {},
            onSkipNext = {},
            onToggleLoop = {},
            onVolumeChange = {},
            onToggleMute = {},
            onEqualizerEnabledChange = { eqEnabledToggled = it },
            onSelectEqualizerPreset = { pickedPreset = it },
            onResetEqualizer = { eqResetClicked = true },
          )
        }
      }
    }

    composeTestRule.onNodeWithTag("player_eq_dsp_card").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("player_eq_label").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("eq_band_slider_0").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("eq_band_slider_7").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("eq_preset_vocal_enhancer").performScrollTo().performClick()
    assertEquals(com.example.audio.EqPreset.VOCAL_ENHANCER, pickedPreset)
    composeTestRule.onNodeWithTag("player_eq_reset_button").performScrollTo().performClick()
    assertTrue(eqResetClicked)
    composeTestRule.onNodeWithTag("player_eq_toggle").performScrollTo().performClick()
    assertEquals(false, eqEnabledToggled)
  }

  @Test
  fun `multi format audio exporter encodes mp3 wav flac with bitrate selection and updates export dialog`() {
    val sampleRate = 44_100
    val totalSamples = sampleRate * 2
    val smoothHarmonicPcm = ShortArray(totalSamples) { i ->
      val t = i.toDouble() / sampleRate
      val wave = 0.6 * kotlin.math.sin(2.0 * kotlin.math.PI * 220.0 * t) +
        0.3 * kotlin.math.sin(2.0 * kotlin.math.PI * 440.0 * t)
      (wave * 18_000.0).toInt().toShort()
    }
    val buffer = com.example.audio.FourierAudioBufferProcessor.analyzeRawPcm(
      pcmSamples = smoothHarmonicPcm,
      sampleRate = sampleRate,
      channelCount = 1,
      bridgeSource = "MultiFormat Test",
    )

    // 1. WAV 16-bit vs 24-bit header & byte sizes
    val wav16 = com.example.audio.FlacAudioEncoder.encodeBufferToWavBytes(buffer, bitsPerSample = 16)
    val wav24 = com.example.audio.FlacAudioEncoder.encodeBufferToWavBytes(buffer, bitsPerSample = 24)
    assertEquals("RIFF", String(wav16, 0, 4, Charsets.US_ASCII))
    assertEquals("WAVE", String(wav24, 8, 4, Charsets.US_ASCII))
    assertEquals(16, (wav16[34].toInt() and 0xFF) or ((wav16[35].toInt() and 0xFF) shl 8))
    assertEquals(24, (wav24[34].toInt() and 0xFF) or ((wav24[35].toInt() and 0xFF) shl 8))
    assertEquals(44 + totalSamples * 2, wav16.size)
    assertEquals(44 + totalSamples * 3, wav24.size)

    // 2. FLAC lossless compression: starts with "fLaC" + STREAMINFO, strictly smaller than 16-bit WAV
    val flacFast = com.example.audio.FlacAudioEncoder.encodeBufferToFlacBytes(buffer, compressionLevel = 0)
    val flacMax = com.example.audio.FlacAudioEncoder.encodeBufferToFlacBytes(buffer, compressionLevel = 8)
    assertEquals("fLaC", String(flacMax, 0, 4, Charsets.US_ASCII))
    assertEquals(0x80, flacMax[4].toInt() and 0xFF) // last metadata block = 1, type = 0 (STREAMINFO)
    assertEquals(34, flacMax[7].toInt() and 0xFF) // STREAMINFO length
    assertTrue("Expected compressed FLAC (${flacMax.size}) < WAV 16-bit (${wav16.size})", flacMax.size < wav16.size)
    assertTrue("Expected level-8 FLAC (${flacMax.size}) <= level-0 FLAC (${flacFast.size})", flacMax.size <= flacFast.size)

    // 3. MP3 bitrate scaling: 320 kbps > 128 kbps
    val mp3Low = com.example.audio.Mp3AudioExporter.encodeRawBufferToMp3Bytes(buffer, bitrateKbps = 128)
    val mp3High = com.example.audio.Mp3AudioExporter.encodeRawBufferToMp3Bytes(buffer, bitrateKbps = 320)
    assertTrue("Expected 320 kbps MP3 (${mp3High.size}) > 128 kbps MP3 (${mp3Low.size})", mp3High.size > mp3Low.size)

    // 4. End-to-end trackToExportBytes with real WAV track
    val context = ApplicationProvider.getApplicationContext<Context>()
    val tempDir = File(context.cacheDir, "export_fmt_test_${System.currentTimeMillis()}")
    try {
      val track = WavTrackSynthesizer.synthesizeToFile("Warm rainy lo-fi rhodes 84 bpm", tempDir)
      val outFlac = com.example.audio.Mp3AudioExporter.trackToExportBytes(
        track,
        com.example.audio.AudioExportConfig.defaultFor(com.example.audio.AudioExportFormat.FLAC),
      )
      assertEquals("fLaC", String(outFlac, 0, 4, Charsets.US_ASCII))
      val outWav = com.example.audio.Mp3AudioExporter.trackToExportBytes(
        track,
        com.example.audio.AudioExportConfig(
          format = com.example.audio.AudioExportFormat.WAV,
          bitrateKbps = 2116,
          wavBitsPerSample = 24,
        ),
      )
      assertEquals("RIFF", String(outWav, 0, 4, Charsets.US_ASCII))
      assertEquals(24, outWav[34].toInt() and 0xFF)
    } finally {
      tempDir.deleteRecursively()
    }

    // 5. TrackExportDialog interactive format & bitrate switching
    val configState = androidx.compose.runtime.mutableStateOf(com.example.audio.AudioExportConfig())
    val uiTrack = GeneratedTrack(
      id = "fmt-ui-1",
      title = "Velvet Horizon",
      prompt = "Ambient synthwave",
      genre = "Ambient",
      bpm = 96,
      musicalKey = "A Min",
      durationMs = 10_000L,
      filePath = "/tmp/velvet.wav",
      fileSizeBytes = 250_000L,
      waveformPeaks = List(24) { 0.4f },
      createdAtEpochMs = 4_000L,
    )

    composeTestRule.setContent {
      SonicForgeTheme {
        com.example.ui.components.TrackExportDialog(
          track = uiTrack,
          exportConfig = configState.value,
          isExporting = false,
          exportStatusMessage = null,
          exportErrorMessage = null,
          onSelectFormat = { fmt ->
            configState.value = com.example.audio.AudioExportConfig.defaultFor(fmt)
          },
          onSelectBitrateOption = { opt ->
            configState.value = configState.value.withOption(opt)
          },
          onDismiss = {},
          onSaveToMusic = {},
          onChooseFolder = {},
          onShareMp3 = {},
        )
      }
    }

    composeTestRule.onNodeWithTag("export_format_mp3").assertIsDisplayed()
    composeTestRule.onNodeWithTag("export_bitrate_mp3_320").performClick()
    assertEquals(320, configState.value.bitrateKbps)

    composeTestRule.onNodeWithTag("export_format_flac").performClick()
    assertEquals(com.example.audio.AudioExportFormat.FLAC, configState.value.format)
    composeTestRule.onNodeWithTag("export_bitrate_flac_8").performClick()
    assertEquals(8, configState.value.flacCompressionLevel)

    composeTestRule.onNodeWithTag("export_format_wav").performClick()
    assertEquals(com.example.audio.AudioExportFormat.WAV, configState.value.format)
    composeTestRule.onNodeWithTag("export_bitrate_wav_24").performClick()
    assertEquals(24, configState.value.wavBitsPerSample)
  }

  @Test
  fun `real time bpm detector tracks streamed synthesis chunks accurately and updates studio player readout`() {
    val sampleRate = 44_100
    val chunkFrames = 512

    fun streamBeatTrain(
      detector: com.example.audio.RealTimeBpmDetector,
      targetBpm: Int,
      seconds: Int,
    ): Pair<com.example.audio.RealTimeBpmAnalysisResult, Float> {
      val totalFrames = sampleRate * seconds
      val signal = ShortArray(totalFrames)
      val beatInterval = ((60.0 / targetBpm) * sampleRate).toInt()
      var beatStart = 0
      while (beatStart < totalFrames) {
        val hitLen = kotlin.math.min(totalFrames - beatStart, (0.09 * sampleRate).toInt())
        for (k in 0 until hitLen) {
          val t = k.toDouble() / sampleRate
          val env = kotlin.math.exp(-t * 32.0)
          val wave = kotlin.math.sin(2.0 * kotlin.math.PI * 95.0 * t)
          signal[beatStart + k] = (wave * env * 22_000.0).toInt().toShort()
        }
        beatStart += beatInterval
      }

      var lastResult = com.example.audio.RealTimeBpmAnalysisResult()
      var maxPulse = 0f
      val chunk = ShortArray(chunkFrames)
      var offset = 0
      var sawAnalyzingBeforeLock = false
      while (offset + chunkFrames <= totalFrames) {
        signal.copyInto(chunk, 0, offset, offset + chunkFrames)
        lastResult = detector.processChunk(chunk, chunkFrames, sampleRate, 1)
        if (lastResult.beatPulse > maxPulse) maxPulse = lastResult.beatPulse
        if (offset < sampleRate && lastResult.isAnalyzing) {
          sawAnalyzingBeforeLock = true
        }
        offset += chunkFrames
      }
      assertTrue("Expected detector to report isAnalyzing during initial window", sawAnalyzingBeforeLock)
      return lastResult to maxPulse
    }

    val detector = com.example.audio.RealTimeBpmDetector(sampleRate, 1)

    // 1. Lock onto 96 BPM stream
    val (res96, pulse96) = streamBeatTrain(detector, targetBpm = 96, seconds = 5)
    assertTrue("Expected ~96 BPM, got ${res96.bpm}", res96.bpm in 94..98)
    assertTrue("Expected confidence > 0.35f, got ${res96.confidence}", res96.confidence > 0.35f)
    assertTrue("Expected beat pulse peaks > 0.5f, got $pulse96", pulse96 > 0.5f)
    assertTrue(res96.hasSignal)

    // 2. Reset and lock onto 128 BPM stream
    detector.reset()
    assertEquals(0, detector.currentSnapshot().bpm)
    val (res128, pulse128) = streamBeatTrain(detector, targetBpm = 128, seconds = 5)
    assertTrue("Expected ~128 BPM, got ${res128.bpm}", res128.bpm in 126..130)
    assertTrue("Expected confidence > 0.35f, got ${res128.confidence}", res128.confidence > 0.35f)
    assertTrue("Expected beat pulse peaks > 0.5f, got $pulse128", pulse128 > 0.5f)

    // 3. Prolonged silence clears active tempo cleanly
    val silenceChunk = ShortArray(chunkFrames)
    var silentRes = res128
    repeat(150) {
      silentRes = detector.processChunk(silenceChunk, chunkFrames, sampleRate, 1)
    }
    assertEquals(0, silentRes.bpm)
    assertEquals(false, silentRes.hasSignal)

    // 4. Studio player displays live BPM pill, beat pulse dot, and Fourier live readout
    val sampleTrack = GeneratedTrack(
      id = "live-bpm-ui-1",
      title = "Pulse Telemetry",
      prompt = "Electronic pulse 128 bpm",
      genre = "Electronic",
      bpm = 120,
      musicalKey = "A Min",
      durationMs = 15_000L,
      filePath = "/tmp/pulse.wav",
      fileSizeBytes = 250_000L,
      waveformPeaks = List(32) { 0.5f },
      createdAtEpochMs = 6_000L,
    )

    composeTestRule.setContent {
      SonicForgeTheme {
        Box(modifier = Modifier.verticalScroll(rememberScrollState())) {
          CustomAudioPlayer(
            track = sampleTrack,
            playbackState = AudioPlaybackState(
              currentTrackId = sampleTrack.id,
              isPrepared = true,
              isPlaying = true,
              positionMs = 4_000L,
              durationMs = 15_000L,
              liveBpm = 128,
              liveBpmConfidence = 0.88f,
              liveBeatPulse = 0.92f,
              isBpmAnalyzing = false,
            ),
            canSkipPrevious = false,
            canSkipNext = false,
            onTogglePlayPause = {},
            onBeginSeek = {},
            onPreviewSeek = {},
            onCommitSeek = {},
            onSeekBy = {},
            onSkipPrevious = {},
            onSkipNext = {},
            onToggleLoop = {},
            onVolumeChange = {},
            onToggleMute = {},
            bufferDiagnostics = com.example.audio.RawBufferDiagnostics(
              sampleRateHz = 44_100,
              channelCount = 1,
              sampleCount = 44_100,
              durationMs = 1_000L,
              rmsDb = -11.0f,
              spectralCentroidHz = 980,
              fourierSpectrumBins = List(12) { 0.45f },
              detectedBpm = 120,
              bpmConfidence = 0.70f,
              integratedLufs = -14.0f,
              bridgeSource = "Fourier Synth",
            ),
          )
        }
      }
    }

    composeTestRule.onNodeWithTag("player_bpm_pill").assertIsDisplayed()
    composeTestRule.onNodeWithTag("player_beat_pulse_dot").assertIsDisplayed()
    composeTestRule.onNodeWithTag("fourier_bpm_readout").assertIsDisplayed()
  }

  @Test
  fun `midi sequence exporter generates valid smf midi tracks from synthesized melody patterns and updates export dialog`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val tempDir = File(context.cacheDir, "midi_export_test_${System.currentTimeMillis()}")
    try {
      val prompt = "Neon midnight synthwave arpeggio 116 bpm"
      val track = WavTrackSynthesizer.synthesizeToFile(prompt, tempDir)
      val pattern = WavTrackSynthesizer.extractSequencePattern(prompt, overrideBpm = track.bpm)
      assertTrue(pattern.melodyNotes.isNotEmpty())
      assertTrue(pattern.chordNotes.isNotEmpty())
      assertEquals(116, pattern.bpm)

      // 1. Melody-only SMF-0 sequence
      val melodyMidi = com.example.audio.MidiSequenceExporter.exportTrackToMidiBytes(
        track = track,
        mode = com.example.audio.MidiExportMode.MELODY_ONLY,
      )
      assertEquals("MThd", String(melodyMidi, 0, 4, Charsets.US_ASCII))
      val melodySummary = com.example.audio.MidiSequenceExporter.parseMidiSummary(melodyMidi)
      assertEquals(0, melodySummary.format)
      assertEquals(1, melodySummary.trackCount)
      assertEquals(480, melodySummary.divisionPpq)
      assertEquals(116, melodySummary.tempoBpm)
      assertEquals(pattern.melodyNotes.size, melodySummary.noteOnCount)

      // 2. Full arrangement SMF-1 multi-track sequence (Conductor + Lead + Chords + Bass + Drums)
      val fullMidi = com.example.audio.Mp3AudioExporter.trackToExportBytes(
        track = track,
        config = com.example.audio.AudioExportConfig.defaultFor(com.example.audio.AudioExportFormat.MIDI),
      )
      val fullSummary = com.example.audio.MidiSequenceExporter.parseMidiSummary(fullMidi)
      assertEquals(1, fullSummary.format)
      assertEquals(5, fullSummary.trackCount)
      assertEquals(116, fullSummary.tempoBpm)
      val expectedFullNotes = pattern.melodyNotes.size + pattern.chordNotes.size +
        pattern.bassNotes.size + pattern.drumHits.size
      assertEquals(expectedFullNotes, fullSummary.noteOnCount)
      assertTrue(fullSummary.trackNames.contains("Lead Melody"))
      assertTrue(fullSummary.trackNames.contains("Chord Pad"))
      assertTrue(fullSummary.trackNames.contains("Bassline"))
      assertTrue(
        com.example.audio.Mp3AudioExporter.suggestedFileName(
          track.title,
          com.example.audio.AudioExportFormat.MIDI,
        ).endsWith(".mid")
      )

      // 3. TrackExportDialog MIDI format & sequence track scope selection
      val configState = androidx.compose.runtime.mutableStateOf(com.example.audio.AudioExportConfig())
      composeTestRule.setContent {
        SonicForgeTheme {
          com.example.ui.components.TrackExportDialog(
            track = track,
            exportConfig = configState.value,
            isExporting = false,
            exportStatusMessage = null,
            exportErrorMessage = null,
            onSelectFormat = { fmt ->
              configState.value = com.example.audio.AudioExportConfig.defaultFor(fmt)
            },
            onSelectBitrateOption = { opt ->
              configState.value = configState.value.withOption(opt)
            },
            onDismiss = {},
            onSaveToMusic = {},
            onChooseFolder = {},
            onShareMp3 = {},
          )
        }
      }

      composeTestRule.onNodeWithTag("export_format_midi").performScrollTo().performClick()
      assertEquals(com.example.audio.AudioExportFormat.MIDI, configState.value.format)
      assertEquals(com.example.audio.MidiExportMode.FULL_ARRANGEMENT, configState.value.midiExportMode)

      composeTestRule.onNodeWithTag("export_bitrate_midi_melody").performScrollTo().performClick()
      assertEquals(com.example.audio.MidiExportMode.MELODY_ONLY, configState.value.midiExportMode)

      composeTestRule.onNodeWithTag("export_bitrate_midi_melody_chords").performScrollTo().performClick()
      assertEquals(com.example.audio.MidiExportMode.MELODY_AND_CHORDS, configState.value.midiExportMode)
    } finally {
      tempDir.deleteRecursively()
    }
  }

  @Test
  fun `step sequencer programs drum and rhythmic cells syncs to app detected bpm and renders wav pattern`() {
    // 1. Swung 16th-note timing preserves exact quarter/eighth-note duration
    val baseEighthMs = 60_000.0 / 128.0 / 2.0
    val evenStepMs = com.example.audio.StepSequencerDsp.computeStepDurationMs(
      bpm = 128,
      stepIndex = 0,
      swingPercent = 30,
    )
    val oddStepMs = com.example.audio.StepSequencerDsp.computeStepDurationMs(
      bpm = 128,
      stepIndex = 1,
      swingPercent = 30,
    )
    assertTrue("Expected swung even step > straight 16th", evenStepMs > oddStepMs)
    assertEquals(baseEighthMs, evenStepMs + oddStepMs, 0.01)

    // 2. StepSequencerEngine edits grid cells, applies presets, syncs to detected app BPM, and detaches on manual tempo change
    val engine = com.example.audio.StepSequencerEngine()
    engine.clearPattern()
    assertEquals(0, engine.state.value.activeStepCount)

    engine.toggleStep(com.example.audio.SequencerLane.KICK, 0)
    engine.toggleStep(com.example.audio.SequencerLane.KICK, 8)
    engine.toggleStep(com.example.audio.SequencerLane.SYNTH, 2)
    assertEquals(3, engine.state.value.activeStepCount)
    assertTrue(engine.state.value.isStepActive(com.example.audio.SequencerLane.KICK, 0))
    assertTrue(engine.state.value.isStepActive(com.example.audio.SequencerLane.SYNTH, 2))

    engine.applyPreset(com.example.audio.SequencerPreset.SYNTHWAVE_ARP)
    assertEquals(com.example.audio.SequencerPreset.SYNTHWAVE_ARP.label, engine.state.value.selectedPresetName)
    assertTrue(engine.state.value.activeStepCount > 10)

    // Sync to detected BPM from app player
    assertTrue(engine.state.value.isBpmSynced)
    engine.updateDetectedAppBpm(128, "Live Detected")
    assertEquals(128, engine.state.value.bpm)
    assertEquals(128, engine.state.value.detectedAppBpm)
    assertEquals("Live Detected", engine.state.value.bpmSyncSourceLabel)

    // Manual BPM nudge detaches sync cleanly
    engine.stepManualBpm(2)
    assertEquals(false, engine.state.value.isBpmSynced)
    assertEquals(130, engine.state.value.bpm)

    // Re-enabling BPM sync snaps tempo back to detected app BPM
    engine.toggleBpmSync()
    assertTrue(engine.state.value.isBpmSynced)
    assertEquals(128, engine.state.value.bpm)

    // 3. Render programmed 4-bar pattern to LUFS-normalized audio buffer at synced 128 BPM
    val renderedBuffer = com.example.audio.StepSequencerDsp.renderPatternToRawBuffer(
      state = engine.state.value,
      bars = 4,
      targetLufs = -14.0f,
    )
    val expected4BarsMs = (4.0 * 4.0 * 60_000.0 / 128.0).toLong()
    assertTrue(
      "Expected 4-bar duration near $expected4BarsMs ms, got ${renderedBuffer.durationMs}",
      kotlin.math.abs(renderedBuffer.durationMs - expected4BarsMs) < 120L,
    )
    assertTrue("Expected non-silent rendered peaks", renderedBuffer.peakAmplitude > 0.1f)
    assertTrue(
      "Expected Fourier detected BPM near 128 BPM, got ${renderedBuffer.detectedBpm}",
      renderedBuffer.detectedBpm in 122..134,
    )

    // 4. Compose UI verification on StepSequencerCard
    val uiEngine = com.example.audio.StepSequencerEngine()
    uiEngine.updateDetectedAppBpm(124, "Buffer Detected")
    var playStopClicks = 0
    var renderClicks = 0

    composeTestRule.setContent {
      SonicForgeTheme {
        val seqState by uiEngine.state.collectAsStateWithLifecycle()
        Box(modifier = Modifier.verticalScroll(rememberScrollState())) {
          com.example.ui.components.StepSequencerCard(
            state = seqState,
            onToggleStep = uiEngine::toggleStep,
            onTogglePlayStop = { playStopClicks++ },
            onClearPattern = uiEngine::clearPattern,
            onApplyPreset = uiEngine::applyPreset,
            onStepBpm = uiEngine::stepManualBpm,
            onToggleBpmSync = uiEngine::toggleBpmSync,
            onCycleSwing = uiEngine::cycleSwing,
            onRenderToLibrary = { renderClicks++ },
          )
        }
      }
    }

    composeTestRule.onNodeWithTag("step_sequencer_card").assertIsDisplayed()
    composeTestRule.onNodeWithTag("sequencer_bpm_value").assertIsDisplayed()
    composeTestRule.onNodeWithTag("sequencer_bpm_sync_chip").assertIsDisplayed()

    val kickBefore = uiEngine.state.value.isStepActive(com.example.audio.SequencerLane.KICK, 0)
    composeTestRule.onNodeWithTag("seq_cell_kick_0").performClick()
    assertEquals(!kickBefore, uiEngine.state.value.isStepActive(com.example.audio.SequencerLane.KICK, 0))

    composeTestRule.onNodeWithTag("seq_cell_synth_1").performScrollTo().performClick()
    assertTrue(uiEngine.state.value.isStepActive(com.example.audio.SequencerLane.SYNTH, 1))

    composeTestRule.onNodeWithTag("sequencer_preset_lofi_boom_bap").performScrollTo().performClick()
    assertEquals(com.example.audio.SequencerPreset.LOFI_BOOM_BAP.label, uiEngine.state.value.selectedPresetName)

    composeTestRule.onNodeWithTag("sequencer_swing_chip").performScrollTo().performClick()
    assertEquals(15, uiEngine.state.value.swingPercent)

    composeTestRule.onNodeWithTag("sequencer_render_track_button").performClick()
    composeTestRule.onNodeWithTag("sequencer_play_pause_button").performClick()
    assertEquals(1, renderClicks)
    assertEquals(1, playStopClicks)
  }

  @Test
  fun `built-in metronome synthesizes pitch and volume scaled clicks syncs with active bpm and updates ui`() {
    fun countZeroCrossings(samples: FloatArray): Int {
      var crossings = 0
      for (i in 1 until samples.size) {
        if ((samples[i - 1] < 0f && samples[i] >= 0f) || (samples[i - 1] >= 0f && samples[i] < 0f)) {
          crossings++
        }
      }
      return crossings
    }

    // 1. Beat interval & tap tempo math
    assertEquals(
      500.0,
      com.example.audio.MetronomeDsp.computeBeatIntervalMs(
        bpm = 120,
        timeSignature = com.example.audio.MetronomeTimeSignature.FOUR_FOUR,
      ),
      0.01,
    )
    assertEquals(
      250.0,
      com.example.audio.MetronomeDsp.computeBeatIntervalMs(
        bpm = 120,
        timeSignature = com.example.audio.MetronomeTimeSignature.SIX_EIGHT,
      ),
      0.01,
    )
    assertEquals(
      120,
      com.example.audio.MetronomeDsp.computeTapTempoBpm(listOf(1_000L, 1_500L, 2_000L, 2_500L)),
    )

    // 2. Click DSP synthesis respects volume, pitch, and downbeat accenting
    val silentClick = com.example.audio.MetronomeDsp.synthesizeClickSamples(
      pitchHz = 880f,
      volume = 0f,
      isAccentBeat = true,
    )
    assertTrue(silentClick.all { it == 0f })

    val lowVolClick = com.example.audio.MetronomeDsp.synthesizeClickSamples(
      pitchHz = 880f,
      volume = 0.25f,
      isAccentBeat = false,
    )
    val highVolClick = com.example.audio.MetronomeDsp.synthesizeClickSamples(
      pitchHz = 880f,
      volume = 0.95f,
      isAccentBeat = false,
    )
    assertTrue(highVolClick.maxOf { kotlin.math.abs(it) } > lowVolClick.maxOf { kotlin.math.abs(it) } * 2.5f)

    val lowPitchClick = com.example.audio.MetronomeDsp.synthesizeClickSamples(
      pitchHz = 440f,
      volume = 0.85f,
      isAccentBeat = false,
    )
    val highPitchClick = com.example.audio.MetronomeDsp.synthesizeClickSamples(
      pitchHz = 1320f,
      volume = 0.85f,
      isAccentBeat = false,
    )
    assertTrue(
      "Expected higher pitch click to have more zero crossings",
      countZeroCrossings(highPitchClick) > countZeroCrossings(lowPitchClick) * 2,
    )

    val regularBeat = com.example.audio.MetronomeDsp.synthesizeClickSamples(
      pitchHz = 800f,
      volume = 0.80f,
      isAccentBeat = false,
    )
    val accentBeat = com.example.audio.MetronomeDsp.synthesizeClickSamples(
      pitchHz = 800f,
      volume = 0.80f,
      isAccentBeat = true,
    )
    assertTrue(accentBeat.maxOf { kotlin.math.abs(it) } > regularBeat.maxOf { kotlin.math.abs(it) })
    assertTrue(countZeroCrossings(accentBeat) > countZeroCrossings(regularBeat))

    // 3. MetronomeEngine clamps volume/pitch, syncs to detected/sequencer BPM, detaches on manual step, and re-syncs
    val engine = com.example.audio.MetronomeEngine()
    engine.setVolume(1.5f)
    assertEquals(1.0f, engine.state.value.volume, 0.001f)
    engine.setPitchHz(1450f)
    assertEquals(1450f, engine.state.value.pitchHz, 0.1f)

    assertTrue(engine.state.value.isBpmSynced)
    engine.updateSyncedAppBpm(126, "Sequencer")
    assertEquals(126, engine.state.value.bpm)
    assertEquals("Sequencer", engine.state.value.bpmSyncSourceLabel)

    engine.stepManualBpm(4)
    assertEquals(false, engine.state.value.isBpmSynced)
    assertEquals(130, engine.state.value.bpm)

    engine.toggleBpmSync()
    assertTrue(engine.state.value.isBpmSynced)
    assertEquals(126, engine.state.value.bpm)

    // Tap tempo sets custom tempo cleanly
    engine.tapTempo(10_000L)
    engine.tapTempo(10_600L)
    assertEquals(100, engine.state.value.bpm)
    assertEquals(false, engine.state.value.isBpmSynced)
    engine.toggleBpmSync()
    assertEquals(126, engine.state.value.bpm)

    // 4. Compose UI verification on MetronomeCard
    val uiEngine = com.example.audio.MetronomeEngine()
    uiEngine.updateSyncedAppBpm(118, "Live Detected")
    var playStopClicks = 0

    composeTestRule.setContent {
      SonicForgeTheme {
        val metroState by uiEngine.state.collectAsStateWithLifecycle()
        Box(modifier = Modifier.verticalScroll(rememberScrollState())) {
          com.example.ui.components.MetronomeCard(
            state = metroState,
            onTogglePlayStop = { playStopClicks++ },
            onStepBpm = uiEngine::stepManualBpm,
            onToggleBpmSync = uiEngine::toggleBpmSync,
            onVolumeChange = uiEngine::setVolume,
            onPitchHzChange = uiEngine::setPitchHz,
            onSelectToneStyle = uiEngine::selectToneStyle,
            onSelectTimeSignature = uiEngine::selectTimeSignature,
            onToggleAccentDownbeat = uiEngine::toggleAccentDownbeat,
            onTapTempo = {},
          )
        }
      }
    }

    composeTestRule.onNodeWithTag("metronome_card").assertIsDisplayed()
    composeTestRule.onNodeWithTag("metronome_bpm_value").assertIsDisplayed()
    composeTestRule.onNodeWithTag("metronome_volume_slider").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("metronome_pitch_slider").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("metronome_beat_indicator_0").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("metronome_beat_indicator_3").performScrollTo().assertIsDisplayed()

    composeTestRule.onNodeWithTag("metronome_tone_woodblock").performScrollTo().performClick()
    assertEquals(com.example.audio.MetronomeTone.WOODBLOCK, uiEngine.state.value.toneStyle)

    composeTestRule.onNodeWithTag("metronome_sig_3_4").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertEquals(com.example.audio.MetronomeTimeSignature.THREE_FOUR, uiEngine.state.value.timeSignature)
    composeTestRule.onNodeWithTag("metronome_beat_indicator_2").performScrollTo().assertIsDisplayed()

    composeTestRule.onNodeWithTag("metronome_accent_chip").performScrollTo().performClick()
    assertEquals(false, uiEngine.state.value.accentDownbeat)

    composeTestRule.onNodeWithTag("metronome_bpm_up").performScrollTo().performClick()
    assertEquals(120, uiEngine.state.value.bpm)
    assertEquals(false, uiEngine.state.value.isBpmSynced)

    composeTestRule.onNodeWithTag("metronome_bpm_sync_chip").performScrollTo().performClick()
    assertTrue(uiEngine.state.value.isBpmSynced)
    assertEquals(118, uiEngine.state.value.bpm)

    composeTestRule.onNodeWithTag("metronome_play_pause_button").performScrollTo().performClick()
    assertEquals(1, playStopClicks)
  }

  @Test
  fun `post processing gain normalization levels synthesized and exported outputs consistently`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val tempDir = File(context.cacheDir, "post_gain_test_${System.currentTimeMillis()}")
    tempDir.mkdirs()

    try {
      // 1. Auto post-gain levels disparate synthesis styles (quiet ambient vs energetic techno) + sequencer output
      val (ambientTrack, ambientBuf) = WavTrackSynthesizer.synthesizeWithRawBuffer(
        prompt = "Deep space ambient meditation pad 72 bpm",
        outputDir = tempDir,
        targetLufs = -14.0f,
        applyPostGain = true,
      )
      val (technoTrack, technoBuf) = WavTrackSynthesizer.synthesizeWithRawBuffer(
        prompt = "Energetic cyber techno bassline 128 bpm",
        outputDir = tempDir,
        targetLufs = -14.0f,
        applyPostGain = true,
      )

      val seqState = com.example.audio.StepSequencerState(
        bpm = 122,
        pattern = com.example.audio.SequencerPreset.SYNTHWAVE_ARP.buildGrid(),
      )
      val seqBuf = com.example.audio.StepSequencerDsp.renderPatternToRawBuffer(
        state = seqState,
        bars = 2,
        targetLufs = -14.0f,
        applyPostGain = true,
      )

      assertEquals(-14.0f, ambientBuf.integratedLufs, 1.5f)
      assertEquals(-14.0f, technoBuf.integratedLufs, 1.5f)
      assertEquals(-14.0f, seqBuf.integratedLufs, 1.5f)
      assertTrue("Expected ambient true peak <= -0.9 dBTP, got ${ambientBuf.truePeakDbfs}", ambientBuf.truePeakDbfs <= -0.9f)
      assertTrue("Expected techno true peak <= -0.9 dBTP, got ${technoBuf.truePeakDbfs}", technoBuf.truePeakDbfs <= -0.9f)
      assertTrue("Expected ambient and techno LUFS within 1.5 LU", kotlin.math.abs(ambientBuf.integratedLufs - technoBuf.integratedLufs) <= 1.5f)

      // 2. Disabling applyPostGain skips forced normalization while preserving valid PCM synthesis
      val (_, rawNoNormBuf) = WavTrackSynthesizer.synthesizeWithRawBuffer(
        prompt = "Deep space ambient meditation pad 72 bpm",
        outputDir = tempDir,
        targetLufs = -14.0f,
        applyPostGain = false,
      )
      assertTrue(rawNoNormBuf.pcmSamples.isNotEmpty())
      assertTrue(
        "Expected un-normalized ambient LUFS (${rawNoNormBuf.integratedLufs}) to differ from -14 LUFS",
        kotlin.math.abs(rawNoNormBuf.integratedLufs - (-14.0f)) > 0.5f,
      )

      // 3. Export post-processing levels quiet WAV to target LUFS (-16 LUFS) across WAV, FLAC, and MP3
      val sampleRate = 44_100
      val quietSamples = ShortArray(sampleRate * 2) { i ->
        (kotlin.math.sin(2.0 * kotlin.math.PI * 220.0 * i / sampleRate) * 1_600.0).toInt().toShort()
      }
      val quietBuffer = com.example.audio.FourierAudioBufferProcessor.analyzeRawPcm(
        pcmSamples = quietSamples,
        sampleRate = sampleRate,
        channelCount = 1,
        bridgeSource = "Quiet Master",
      )
      assertTrue("Expected quiet source < -24 LUFS, got ${quietBuffer.integratedLufs}", quietBuffer.integratedLufs < -24.0f)

      val quietWavFile = File(tempDir, "quiet_master.wav")
      com.example.audio.FourierAudioBufferProcessor.writeWavFile(quietWavFile, quietBuffer)
      val quietTrack = ambientTrack.copy(
        id = "quiet-export-1",
        title = "Quiet Master",
        filePath = quietWavFile.absolutePath,
        fileSizeBytes = quietWavFile.length(),
      )

      val exportedWav = com.example.audio.Mp3AudioExporter.trackToExportBytes(
        track = quietTrack,
        config = com.example.audio.AudioExportConfig(
          format = com.example.audio.AudioExportFormat.WAV,
          bitrateKbps = 1411,
          wavBitsPerSample = 16,
          normalizeOutputGain = true,
          targetOutputLufs = -16.0f,
        ),
      )
      val exportedWavFile = File(tempDir, "exported_leveled.wav").apply { writeBytes(exportedWav) }
      val decodedExportedWav = com.example.audio.FourierAudioBufferProcessor.decodeFileToRawBuffer(
        file = exportedWavFile,
        bridgeSource = "Exported WAV",
      )
      assertEquals(-16.0f, decodedExportedWav.integratedLufs, 1.5f)
      assertTrue(decodedExportedWav.truePeakDbfs <= -0.9f)

      val exportedFlac = com.example.audio.Mp3AudioExporter.trackToExportBytes(
        track = quietTrack,
        config = com.example.audio.AudioExportConfig(
          format = com.example.audio.AudioExportFormat.FLAC,
          bitrateKbps = 750,
          flacCompressionLevel = 5,
          normalizeOutputGain = true,
          targetOutputLufs = -16.0f,
        ),
      )
      assertEquals("fLaC", String(exportedFlac, 0, 4, Charsets.US_ASCII))

      val unnormalizedFlac = com.example.audio.Mp3AudioExporter.trackToExportBytes(
        track = quietTrack,
        config = com.example.audio.AudioExportConfig(
          format = com.example.audio.AudioExportFormat.FLAC,
          bitrateKbps = 750,
          flacCompressionLevel = 5,
          normalizeOutputGain = false,
          targetOutputLufs = -16.0f,
        ),
      )
      assertTrue("Expected leveled FLAC bytes to differ from unleveled FLAC", !exportedFlac.contentEquals(unnormalizedFlac))

      val exportedMp3 = com.example.audio.Mp3AudioExporter.trackToExportBytes(
        track = quietTrack,
        config = com.example.audio.AudioExportConfig(
          format = com.example.audio.AudioExportFormat.MP3,
          bitrateKbps = 192,
          normalizeOutputGain = true,
          targetOutputLufs = -16.0f,
        ),
      )
      assertTrue(exportedMp3.size > 1_000)

      // 4. Playback trim computation respects -1.0 dBTP headroom ceiling & updates AudioPlayerController
      val boostGain = com.example.audio.LoudnessNormalizerDsp.computePlaybackGainLinear(
        measuredLufs = -20.0f,
        targetLufs = -14.0f,
        peakAmplitude = 0.25f,
      )
      assertTrue("Expected boost > 1.8x for quiet track with headroom, got $boostGain", boostGain > 1.8f)

      val cappedGain = com.example.audio.LoudnessNormalizerDsp.computePlaybackGainLinear(
        measuredLufs = -20.0f,
        targetLufs = -14.0f,
        peakAmplitude = 0.85f,
      )
      assertTrue("Expected gain capped <= 1.06x to protect -1 dBTP ceiling, got $cappedGain", cappedGain <= 1.06f)

      val cutGain = com.example.audio.LoudnessNormalizerDsp.computePlaybackGainLinear(
        measuredLufs = -10.0f,
        targetLufs = -16.0f,
        peakAmplitude = 0.90f,
      )
      assertEquals(0.501f, cutGain, 0.03f)

      val controller = com.example.audio.AudioPlayerController.getInstance(context)
      controller.setAutoPostGain(enabled = false, targetLufs = -18.0f)
      assertEquals(false, controller.state.value.autoPostGainEnabled)
      assertEquals(-18.0f, controller.state.value.targetLoudnessLufs, 0.01f)
      controller.setAutoPostGain(enabled = true, targetLufs = -14.0f)
      assertTrue(controller.state.value.autoPostGainEnabled)
      assertEquals(-14.0f, controller.state.value.targetLoudnessLufs, 0.01f)

      // 5. CustomAudioPlayer Auto-Level UI & TrackExportDialog Auto-Level chip
      var toggledAutoPostGain: Boolean? = null
      var exportNormalizeVal = true
      val showExportDialog = androidx.compose.runtime.mutableStateOf(false)
      composeTestRule.setContent {
        SonicForgeTheme {
          if (showExportDialog.value) {
            com.example.ui.components.TrackExportDialog(
              track = quietTrack,
              exportConfig = com.example.audio.AudioExportConfig(
                format = com.example.audio.AudioExportFormat.WAV,
                bitrateKbps = 1411,
                wavBitsPerSample = 16,
                normalizeOutputGain = exportNormalizeVal,
                targetOutputLufs = -16.0f,
              ),
              isExporting = false,
              exportStatusMessage = null,
              exportErrorMessage = null,
              onToggleNormalizeGain = { exportNormalizeVal = it },
              onDismiss = { showExportDialog.value = false },
              onSaveToMusic = {},
              onChooseFolder = {},
              onShareMp3 = {},
            )
          }
          Box(modifier = Modifier.verticalScroll(rememberScrollState())) {
            CustomAudioPlayer(
              track = technoTrack,
              playbackState = AudioPlaybackState(
                currentTrackId = technoTrack.id,
                isPrepared = true,
                durationMs = technoTrack.durationMs,
                autoPostGainEnabled = true,
                targetLoudnessLufs = -14.0f,
                autoGainTrimDb = 1.8f,
              ),
              canSkipPrevious = false,
              canSkipNext = false,
              onTogglePlayPause = {},
              onBeginSeek = {},
              onPreviewSeek = {},
              onCommitSeek = {},
              onSeekBy = {},
              onSkipPrevious = {},
              onSkipNext = {},
              onToggleLoop = {},
              onVolumeChange = {},
              onToggleMute = {},
              loudnessTarget = com.example.audio.LoudnessTarget.STREAMING_14,
              autoPostGainEnabled = true,
              onAutoPostGainEnabledChange = { toggledAutoPostGain = it },
              bufferDiagnostics = technoBuf.toDiagnostics(),
            )
          }
        }
      }

      composeTestRule.onNodeWithTag("player_loudness_dsp_card").performScrollTo().assertIsDisplayed()
      composeTestRule.onNodeWithTag("player_lufs_badge").performScrollTo().assertIsDisplayed()
      composeTestRule.onNodeWithTag("player_auto_post_gain_status").performScrollTo().assertIsDisplayed()
      composeTestRule.onNodeWithTag("player_auto_post_gain_toggle").performScrollTo().performClick()
      assertEquals(false, toggledAutoPostGain)

      showExportDialog.value = true
      composeTestRule.waitForIdle()
      composeTestRule.onNodeWithTag("export_normalize_gain_toggle").assertIsDisplayed().performClick()
      assertEquals(false, exportNormalizeVal)
    } finally {
      tempDir.deleteRecursively()
    }
  }

  @Test
  fun `real time spectrum analyzer tracks streaming frequency bands and updates studio player visualization`() {
    val sampleRate = 44_100
    val analyzer = com.example.audio.RealTimeSpectrumAnalyzer(
      initialSampleRate = sampleRate,
      initialChannelCount = 1,
    )

    fun feedTone(freqHz: Double, chunks: Int = 6, amp: Double = 24_000.0): com.example.audio.RealTimeSpectrumSnapshot {
      var snap = analyzer.currentSnapshot()
      val frames = 512
      var sampleOffset = 0
      repeat(chunks) {
        val pcm = ShortArray(frames) { idx ->
          val t = (sampleOffset + idx).toDouble() / sampleRate
          (kotlin.math.sin(2.0 * kotlin.math.PI * freqHz * t) * amp).toInt().toShort()
        }
        sampleOffset += frames
        snap = analyzer.processChunk(pcm = pcm, frameCount = frames, sampleRate = sampleRate, channelCount = 1)
      }
      return snap
    }

    // 1. 80 Hz sub/bass tone -> Low energy dominates
    val bassSnap = feedTone(freqHz = 80.0)
    assertTrue(bassSnap.isActive)
    assertEquals(com.example.audio.RealTimeSpectrumAnalyzer.BAND_COUNT, bassSnap.bandLevels.size)
    assertTrue("Expected lowEnergy > 0.35 for 80 Hz bass, got ${bassSnap.lowEnergy}", bassSnap.lowEnergy > 0.35f)
    assertTrue("Expected lowEnergy > highEnergy for 80 Hz bass", bassSnap.lowEnergy > bassSnap.highEnergy + 0.25f)
    assertTrue("Expected peakFrequencyHz near 80 Hz, got ${bassSnap.peakFrequencyHz}", bassSnap.peakFrequencyHz in 40..150)

    // 2. 1000 Hz midrange tone -> Mid energy dominates and centroid shifts up
    analyzer.reset()
    val midSnap = feedTone(freqHz = 1000.0)
    assertTrue(midSnap.isActive)
    assertTrue("Expected midEnergy > lowEnergy for 1 kHz, got mid=${midSnap.midEnergy} low=${midSnap.lowEnergy}", midSnap.midEnergy > midSnap.lowEnergy)
    assertTrue("Expected peakFrequencyHz near 1000 Hz, got ${midSnap.peakFrequencyHz}", midSnap.peakFrequencyHz in 850..1150)

    // 3. 6000 Hz treble tone -> High energy dominates
    analyzer.reset()
    val highSnap = feedTone(freqHz = 6000.0)
    assertTrue(highSnap.isActive)
    val peakHighBand = highSnap.bandLevels.drop(14).maxOrNull() ?: 0f
    assertTrue("Expected peak high band > 0.70 for 6 kHz, got $peakHighBand", peakHighBand > 0.70f)
    assertTrue("Expected highEnergy > lowEnergy for 6 kHz, got high=${highSnap.highEnergy} low=${highSnap.lowEnergy}", highSnap.highEnergy > highSnap.lowEnergy + 0.10f)
    assertTrue("Expected peakFrequencyHz near 6000 Hz, got ${highSnap.peakFrequencyHz}", highSnap.peakFrequencyHz in 5500..6500)

    // 4. Peak hold retains peaks during initial silence frames, then decays cleanly to idle
    val silentFrame = ShortArray(512)
    val postSilenceHold = analyzer.processChunk(pcm = silentFrame, frameCount = 512)
    assertTrue(
      "Expected peak hold to remain higher than decayed level after 1 silent chunk",
      postSilenceHold.peakHoldLevels.maxOrNull()!! >= postSilenceHold.bandLevels.maxOrNull()!!,
    )
    var settled = postSilenceHold
    repeat(48) {
      settled = analyzer.processChunk(pcm = silentFrame, frameCount = 512)
    }
    assertEquals(false, settled.isActive)
    assertTrue("Expected bands decayed near 0 after sustained silence", settled.bandLevels.all { it == 0f })

    // 5. Resampling / downsampling helpers
    val downsampled = com.example.audio.RealTimeSpectrumAnalyzer.downsampleToBins(highSnap.bandLevels, 12)
    assertEquals(12, downsampled.size)
    val resampled = com.example.audio.RealTimeSpectrumAnalyzer.resampleStaticBins(listOf(0.2f, 0.8f, 0.4f), 20)
    assertEquals(20, resampled.size)

    // 6. Studio player UI renders real-time FrequencySpectrumAnalyzerCard & toggles Bars/Curve + Peak Hold
    val dummyTrack = GeneratedTrack(
      id = "spec-ui-1",
      title = "Live Spectrum Test",
      prompt = "Electro bass 120 bpm",
      genre = "Electronic",
      tags = listOf("electro", "fft"),
      filePath = "/tmp/non_existent_spec.wav",
      durationMs = 12_000L,
      fileSizeBytes = 192_000L,
      sourceLabel = "Fourier Synth",
      bpm = 120,
      musicalKey = "Am",
      waveformPeaks = List(36) { 0.5f },
      createdAtEpochMs = System.currentTimeMillis(),
    )

    composeTestRule.setContent {
      SonicForgeTheme {
        Box(modifier = Modifier.verticalScroll(rememberScrollState())) {
          CustomAudioPlayer(
            track = dummyTrack,
            playbackState = AudioPlaybackState(
              currentTrackId = dummyTrack.id,
              isPlaying = true,
              isPrepared = true,
              durationMs = dummyTrack.durationMs,
              positionMs = 4_000L,
              liveSpectrum = highSnap,
            ),
            canSkipPrevious = false,
            canSkipNext = false,
            onTogglePlayPause = {},
            onBeginSeek = {},
            onPreviewSeek = {},
            onCommitSeek = {},
            onSeekBy = {},
            onSkipPrevious = {},
            onSkipNext = {},
            onToggleLoop = {},
            onVolumeChange = {},
            onToggleMute = {},
            bufferDiagnostics = com.example.audio.RawBufferDiagnostics(
              sampleRateHz = sampleRate,
              channelCount = 1,
              sampleCount = sampleRate * 4,
              durationMs = 4_000L,
              rmsDb = -14.2f,
              integratedLufs = -14.0f,
              spectralCentroidHz = 6_000,
              detectedBpm = 120,
              bpmConfidence = 0.92f,
              fourierSpectrumBins = List(12) { 0.45f },
              bridgeSource = "Fourier Synth",
            ),
          )
        }
      }
    }

    composeTestRule.onNodeWithTag("player_spectrum_analyzer_card").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("player_spectrum_canvas").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("spectrum_mode_badge").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("spectrum_zone_summary").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("spectrum_centroid_badge").performScrollTo().assertIsDisplayed()

    composeTestRule.onNodeWithTag("spectrum_style_toggle").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("player_spectrum_canvas").assertIsDisplayed()
    composeTestRule.onNodeWithTag("spectrum_peak_hold_toggle").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("player_spectrum_canvas").assertIsDisplayed()
  }

  @Test
  fun `configurable convolution reverb scales room sizes absorbs highs via dampening and updates ui controls`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val sampleRate = 44_100

    // 1. IR synthesis scales RT60 & early reflections smoothly across Room Size presets
    val smallIr = com.example.audio.ConvolutionReverbDsp.generateStereoImpulseResponse(
      sampleRate = sampleRate,
      roomSize = com.example.audio.ReverbRoomSizeOption.SMALL.roomSize,
      damping = com.example.audio.ReverbDampingOption.BALANCED.damping,
    )
    val largeIr = com.example.audio.ConvolutionReverbDsp.generateStereoImpulseResponse(
      sampleRate = sampleRate,
      roomSize = com.example.audio.ReverbRoomSizeOption.CATHEDRAL.roomSize,
      damping = com.example.audio.ReverbDampingOption.BALANCED.damping,
    )
    assertTrue("Expected larger room IR to have longer RT60", largeIr.rt60Seconds > smallIr.rt60Seconds * 2.0f)
    assertTrue("Expected larger room IR to have more PCM frames", largeIr.frameCount > smallIr.frameCount * 2)
    assertTrue("Expected larger room IR early reflections spread farther", largeIr.earlyReflectionMs > smallIr.earlyReflectionMs)

    // 2. Room size comparison on real convolved signal: Large room produces stronger late tail than Small room
    val burstFrames = (sampleRate * 0.10).toInt()
    val silenceFrames = (sampleRate * 0.90).toInt()
    val totalFrames = burstFrames + silenceFrames
    val channels = 2
    val lowBurst = ShortArray(totalFrames * channels)
    for (f in 0 until burstFrames) {
      val s = (kotlin.math.sin(2.0 * kotlin.math.PI * 260.0 * f / sampleRate) * 18_000.0).toInt().toShort()
      lowBurst[f * 2] = s
      lowBurst[f * 2 + 1] = s
    }

    val smallConvolved = com.example.audio.RoomReverbDsp.processPcmOffline(
      pcmSamples = lowBurst,
      sampleRate = sampleRate,
      channelCount = channels,
      settings = com.example.audio.ReverbSettings(
        preset = com.example.audio.ReverbPreset.HALL,
        wetMix = 0.65f,
        roomSize = com.example.audio.ReverbRoomSizeOption.SMALL.roomSize,
        damping = 0.35f,
        engineMode = com.example.audio.ReverbEngineMode.CONVOLUTION,
      ),
    )
    val largeConvolved = com.example.audio.RoomReverbDsp.processPcmOffline(
      pcmSamples = lowBurst,
      sampleRate = sampleRate,
      channelCount = channels,
      settings = com.example.audio.ReverbSettings(
        preset = com.example.audio.ReverbPreset.CATHEDRAL,
        wetMix = 0.65f,
        roomSize = com.example.audio.ReverbRoomSizeOption.CATHEDRAL.roomSize,
        damping = 0.35f,
        engineMode = com.example.audio.ReverbEngineMode.CONVOLUTION,
      ),
    )

    fun stereoRmsWindow(pcm: ShortArray, startFrame: Int, endFrame: Int): Double {
      var sum = 0.0
      for (f in startFrame until endFrame) {
        val l = pcm[f * 2] / 32768.0
        sum += l * l
      }
      return kotlin.math.sqrt(sum / (endFrame - startFrame))
    }

    val lateStart = burstFrames + (sampleRate * 0.32).toInt()
    val lateEnd = burstFrames + (sampleRate * 0.60).toInt()
    val smallLateRms = stereoRmsWindow(smallConvolved, lateStart, lateEnd)
    val largeLateRms = stereoRmsWindow(largeConvolved, lateStart, lateEnd)
    assertTrue(
      "Expected Cathedral convolution late tail RMS ($largeLateRms) > Small room ($smallLateRms)",
      largeLateRms > smallLateRms * 2.5,
    )

    // 3. Dampening level absorbs high frequencies cleanly: Bright (0.14) vs Damped (0.88) on a 5 kHz burst
    val highBurst = ShortArray(totalFrames * channels)
    for (f in 0 until burstFrames) {
      val s = (kotlin.math.sin(2.0 * kotlin.math.PI * 5_000.0 * f / sampleRate) * 18_000.0).toInt().toShort()
      highBurst[f * 2] = s
      highBurst[f * 2 + 1] = s
    }

    val brightOut = com.example.audio.RoomReverbDsp.processPcmOffline(
      pcmSamples = highBurst,
      sampleRate = sampleRate,
      channelCount = channels,
      settings = com.example.audio.ReverbSettings(
        preset = com.example.audio.ReverbPreset.HALL,
        wetMix = 0.65f,
        roomSize = 0.78f,
        damping = com.example.audio.ReverbDampingOption.BRIGHT.damping,
        engineMode = com.example.audio.ReverbEngineMode.CONVOLUTION,
      ),
    )
    val dampedOut = com.example.audio.RoomReverbDsp.processPcmOffline(
      pcmSamples = highBurst,
      sampleRate = sampleRate,
      channelCount = channels,
      settings = com.example.audio.ReverbSettings(
        preset = com.example.audio.ReverbPreset.HALL,
        wetMix = 0.65f,
        roomSize = 0.78f,
        damping = com.example.audio.ReverbDampingOption.DARK.damping,
        engineMode = com.example.audio.ReverbEngineMode.CONVOLUTION,
      ),
    )
    val tailStart = burstFrames + 2_000
    val tailEnd = burstFrames + 18_000
    val brightTailRms = stereoRmsWindow(brightOut, tailStart, tailEnd)
    val dampedTailRms = stereoRmsWindow(dampedOut, tailStart, tailEnd)
    assertTrue(
      "Expected bright HF convolution tail ($brightTailRms) > heavily damped tail ($dampedTailRms)",
      brightTailRms > dampedTailRms * 1.35,
    )

    // 4. AudioPlayerController exposes room size & dampening updates and keeps Convolution IR default
    val controller = com.example.audio.AudioPlayerController.getInstance(context)
    controller.resetReverb()
    assertEquals(com.example.audio.ReverbEngineMode.CONVOLUTION, controller.state.value.reverb.engineMode)
    controller.setReverbDamping(com.example.audio.ReverbDampingOption.DARK.damping)
    assertTrue(controller.state.value.reverb.isActive)
    assertEquals(0.88f, controller.state.value.reverb.damping, 0.01f)
    controller.setReverbRoomSize(com.example.audio.ReverbRoomSizeOption.LARGE.roomSize)
    assertEquals(0.78f, controller.state.value.reverb.roomSize, 0.01f)
    controller.resetReverb()

    // 5. Studio Player UI renders Convolution Reverb controls and dispatches room size, dampening, and mode events
    var changedRoomSize: Float? = null
    var changedDamping: Float? = null
    var changedEngineMode: com.example.audio.ReverbEngineMode? = null
    val sampleTrack = GeneratedTrack(
      id = "conv-reverb-ui-1",
      title = "Convolution Chamber",
      prompt = "Warm concert hall strings 88 bpm",
      genre = "Cinematic",
      bpm = 88,
      musicalKey = "G Maj",
      durationMs = 14_000L,
      filePath = "/tmp/conv_chamber.wav",
      fileSizeBytes = 280_000L,
      waveformPeaks = List(32) { 0.45f },
      createdAtEpochMs = 12_000L,
    )

    composeTestRule.setContent {
      SonicForgeTheme {
        Box(modifier = Modifier.verticalScroll(rememberScrollState())) {
          CustomAudioPlayer(
            track = sampleTrack,
            playbackState = AudioPlaybackState(
              currentTrackId = sampleTrack.id,
              isPrepared = true,
              durationMs = sampleTrack.durationMs,
              reverb = com.example.audio.ReverbSettings(
                preset = com.example.audio.ReverbPreset.STUDIO,
                wetMix = 0.45f,
                roomSize = 0.54f,
                damping = 0.40f,
                engineMode = com.example.audio.ReverbEngineMode.CONVOLUTION,
              ),
            ),
            canSkipPrevious = false,
            canSkipNext = false,
            onTogglePlayPause = {},
            onBeginSeek = {},
            onPreviewSeek = {},
            onCommitSeek = {},
            onSeekBy = {},
            onSkipPrevious = {},
            onSkipNext = {},
            onToggleLoop = {},
            onVolumeChange = {},
            onToggleMute = {},
            onReverbRoomSizeChange = { changedRoomSize = it },
            onReverbDampingChange = { changedDamping = it },
            onReverbEngineModeChange = { changedEngineMode = it },
          )
        }
      }
    }

    composeTestRule.onNodeWithTag("player_reverb_ir_badge").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("reverb_impulse_canvas").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("player_reverb_damping_slider").performScrollTo().assertIsDisplayed()

    composeTestRule.onNodeWithTag("reverb_size_chip_large").performScrollTo().performClick()
    assertEquals(com.example.audio.ReverbRoomSizeOption.LARGE.roomSize, changedRoomSize!!, 0.005f)

    composeTestRule.onNodeWithTag("reverb_damp_chip_dark").performScrollTo().performClick()
    assertEquals(com.example.audio.ReverbDampingOption.DARK.damping, changedDamping!!, 0.005f)

    composeTestRule.onNodeWithTag("reverb_engine_mode_chip").performScrollTo().performClick()
    assertEquals(com.example.audio.ReverbEngineMode.SCHROEDER, changedEngineMode)
  }

  @Test
  fun `room studio project system persists clips effects sequencer state and restores studio ui`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val tempDir = File(context.cacheDir, "studio_proj_test_${System.currentTimeMillis()}")
    val db = Room.inMemoryDatabaseBuilder(context, SonicForgeDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    try {
      tempDir.mkdirs()
      val clip1 = WavTrackSynthesizer.synthesizeToFile("Neon midnight synthwave drive 112 bpm", tempDir)
      val clip2 = WavTrackSynthesizer.synthesizeToFile("Rainy lo-fi rhodes study beat 84 bpm", tempDir)
      val trackDao = db.generatedTrackDao()
      trackDao.insertTracks(listOf(clip1.toEntity(), clip2.toEntity()))

      val projectRepo = com.example.data.StudioProjectRepository(db)

      val customReverb = com.example.audio.ReverbSettings(
        preset = com.example.audio.ReverbPreset.CATHEDRAL,
        wetMix = 0.52f,
        roomSize = 0.90f,
        damping = 0.68f,
        engineMode = com.example.audio.ReverbEngineMode.CONVOLUTION,
      )
      val customEq = com.example.audio.EqualizerSettings(
        isEnabled = true,
        preset = com.example.audio.EqPreset.BASS_BOOST,
        bandGainsDb = listOf(6.0f, 5.5f, 3.0f, 0.5f, -0.5f, 0.0f, 1.5f, 2.0f),
      )
      val customCrossfade = com.example.audio.CrossfadeSettings(enabled = true, durationSec = 5.5f)
      val customEnvelope = com.example.audio.TrackAudioEnvelope(
        points = listOf(
          com.example.audio.EnvelopePoint(0.0f, 0.8f),
          com.example.audio.EnvelopePoint(0.5f, 0.45f),
          com.example.audio.EnvelopePoint(1.0f, 1.0f),
        ),
        mutedSegments = listOf(com.example.audio.MutedSegment(0.20f, 0.35f)),
      )
      val customPattern = com.example.audio.SequencerLane.entries.associateWith { lane ->
        when (lane) {
          com.example.audio.SequencerLane.KICK -> List(16) { idx -> idx % 4 == 0 }
          com.example.audio.SequencerLane.SNARE -> List(16) { idx -> idx == 4 || idx == 12 }
          com.example.audio.SequencerLane.SYNTH -> List(16) { idx -> idx == 2 || idx == 6 || idx == 10 || idx == 14 }
          else -> List(16) { false }
        }
      }

      val saved = projectRepo.saveProject(
        com.example.model.StudioProject(
          id = "proj-session-1",
          name = "Midnight Synth Jam",
          notes = "Cathedral convolution reverb + bass boost",
          activeTrackId = clip1.id,
          activeClipTitle = clip1.title,
          clipIds = listOf(clip1.id, clip2.id),
          playbackPositionMs = 3_450L,
          volume = 0.72f,
          isMuted = false,
          isLooping = true,
          pitchSemitones = 3.0f,
          reverbSettings = customReverb,
          equalizerSettings = customEq,
          crossfadeSettings = customCrossfade,
          autoPostGainEnabled = true,
          loudnessTarget = com.example.audio.LoudnessTarget.MOBILE_16,
          activeEnvelope = customEnvelope,
          sequencerPattern = customPattern,
          sequencerBpm = 124,
          sequencerSwingPercent = 15,
          sequencerBpmSynced = false,
          sequencerPresetName = "Custom Jam",
          metronomeBpm = 124,
          metronomeBpmSynced = false,
          metronomeVolume = 0.65f,
          metronomePitchHz = 1_250f,
          metronomeToneStyle = com.example.audio.MetronomeTone.WOODBLOCK,
          metronomeTimeSignature = com.example.audio.MetronomeTimeSignature.THREE_FOUR,
          metronomeAccentDownbeat = true,
        )
      )

      assertEquals("proj-session-1", saved.id)
      assertEquals(2, saved.clips.size)
      assertEquals(clip1.id, saved.activeTrackId)

      val reloaded = projectRepo.getProjectById("proj-session-1", requireExistingFiles = true)!!
      assertEquals("Midnight Synth Jam", reloaded.name)
      assertEquals("Cathedral convolution reverb + bass boost", reloaded.notes)
      assertEquals(2, reloaded.clips.size)
      assertEquals(3_450L, reloaded.playbackPositionMs)
      assertEquals(0.72f, reloaded.volume, 0.005f)
      assertEquals(true, reloaded.isLooping)
      assertEquals(3.0f, reloaded.pitchSemitones, 0.01f)
      assertEquals(com.example.audio.ReverbPreset.CATHEDRAL, reloaded.reverbSettings.preset)
      assertEquals(0.90f, reloaded.reverbSettings.roomSize, 0.005f)
      assertEquals(0.68f, reloaded.reverbSettings.damping, 0.005f)
      assertEquals(com.example.audio.ReverbEngineMode.CONVOLUTION, reloaded.reverbSettings.engineMode)
      assertEquals(com.example.audio.EqPreset.BASS_BOOST, reloaded.equalizerSettings.preset)
      assertEquals(6.0f, reloaded.equalizerSettings.bandGainsDb[0], 0.01f)
      assertEquals(5.5f, reloaded.crossfadeSettings.durationSec, 0.01f)
      assertEquals(com.example.audio.LoudnessTarget.MOBILE_16, reloaded.loudnessTarget)
      assertTrue(reloaded.activeEnvelope.isEdited)
      assertEquals(1, reloaded.activeEnvelope.mutedSegments.size)
      assertEquals(10, reloaded.activeSequencerSteps)
      assertEquals(124, reloaded.sequencerBpm)
      assertEquals(15, reloaded.sequencerSwingPercent)
      assertEquals("Custom Jam", reloaded.sequencerPresetName)
      assertEquals(com.example.audio.MetronomeTone.WOODBLOCK, reloaded.metronomeToneStyle)
      assertEquals(com.example.audio.MetronomeTimeSignature.THREE_FOUR, reloaded.metronomeTimeSignature)

      // 2. Restore settings into live engines after altering them, verifying exact state restoration
      val playerController = com.example.audio.AudioPlayerController.getInstance(context)
      playerController.resetReverb()
      playerController.resetEqualizer()
      playerController.setPitchSemitones(0f)

      val sequencerEngine = com.example.audio.StepSequencerEngine()
      sequencerEngine.clearPattern()
      assertEquals(0, sequencerEngine.state.value.activeStepCount)

      val metronomeEngine = com.example.audio.MetronomeEngine()
      metronomeEngine.selectToneStyle(com.example.audio.MetronomeTone.STUDIO_BELL)

      playerController.restoreProjectSettings(
        volume = reloaded.volume,
        isMuted = reloaded.isMuted,
        isLooping = reloaded.isLooping,
        pitchSemitones = reloaded.pitchSemitones,
        reverb = reloaded.reverbSettings,
        equalizer = reloaded.equalizerSettings,
        crossfade = reloaded.crossfadeSettings,
        autoPostGainEnabled = reloaded.autoPostGainEnabled,
        targetLoudnessLufs = reloaded.loudnessTarget.lufs,
        activeEnvelope = reloaded.activeEnvelope,
      )
      sequencerEngine.restoreState(
        pattern = reloaded.sequencerPattern,
        bpm = reloaded.sequencerBpm,
        swingPercent = reloaded.sequencerSwingPercent,
        isBpmSynced = reloaded.sequencerBpmSynced,
        selectedPresetName = reloaded.sequencerPresetName,
      )
      metronomeEngine.restoreState(
        bpm = reloaded.metronomeBpm,
        isBpmSynced = reloaded.metronomeBpmSynced,
        volume = reloaded.metronomeVolume,
        pitchHz = reloaded.metronomePitchHz,
        toneStyle = reloaded.metronomeToneStyle,
        timeSignature = reloaded.metronomeTimeSignature,
        accentDownbeat = reloaded.metronomeAccentDownbeat,
      )

      assertEquals(3.0f, playerController.state.value.pitchSemitones, 0.01f)
      assertEquals(0.90f, playerController.state.value.reverb.roomSize, 0.01f)
      assertEquals(com.example.audio.EqPreset.BASS_BOOST, playerController.state.value.equalizer.preset)
      assertEquals(10, sequencerEngine.state.value.activeStepCount)
      assertEquals(124, sequencerEngine.state.value.bpm)
      assertEquals(com.example.audio.MetronomeTone.WOODBLOCK, metronomeEngine.state.value.toneStyle)

      // 3. Delete project removes Room project entry while preserving underlying tracks
      projectRepo.deleteProject("proj-session-1")
      assertTrue(projectRepo.projectsFlow.first().isEmpty())
      assertEquals(2, trackDao.getAllTracksOnce().size)

      // 4. StudioProjectsCard + SaveStudioProjectDialog Compose UI verification
      val showDialog = androidx.compose.runtime.mutableStateOf(false)
      var quickSaveClicked = false
      var loadedProjectId: String? = null
      var deletedProjectId: String? = null
      var savedDialogName = ""
      var savedDialogClips: List<String> = emptyList()

      composeTestRule.setContent {
        SonicForgeTheme {
          if (showDialog.value) {
            com.example.ui.components.SaveStudioProjectDialog(
              defaultName = "Midnight Synth Jam",
              defaultNotes = "Live session",
              availableTracks = listOf(clip1, clip2),
              defaultSelectedClipIds = setOf(clip1.id),
              isSaving = false,
              onDismiss = { showDialog.value = false },
              onConfirmSave = { name, _, selectedClips ->
                savedDialogName = name
                savedDialogClips = selectedClips
                showDialog.value = false
              },
            )
          }
          Box(modifier = Modifier.verticalScroll(rememberScrollState())) {
            com.example.ui.components.StudioProjectsCard(
              projects = listOf(reloaded),
              activeProjectId = reloaded.id,
              currentTrack = clip1,
              allTracks = listOf(clip1, clip2),
              playbackState = playerController.state.value,
              sequencerState = sequencerEngine.state.value,
              isSavingProject = false,
              statusMessage = "Saved \"Midnight Synth Jam\"",
              onOpenSaveDialog = { showDialog.value = true },
              onQuickSaveProject = { quickSaveClicked = true },
              onLoadProject = { loadedProjectId = it },
              onDeleteProject = { deletedProjectId = it },
              onDismissStatus = {},
            )
          }
        }
      }

      composeTestRule.onNodeWithTag("studio_projects_card").performScrollTo().assertIsDisplayed()
      composeTestRule.onNodeWithTag("project_active_badge").performScrollTo().assertIsDisplayed()
      composeTestRule.onNodeWithTag("project_status_banner").performScrollTo().assertIsDisplayed()
      composeTestRule.onNodeWithTag("project_item_${reloaded.id}").performScrollTo().assertIsDisplayed()

      composeTestRule.onNodeWithTag("project_quick_save_button").performScrollTo().performClick()
      assertTrue(quickSaveClicked)

      composeTestRule.onNodeWithTag("project_load_button_${reloaded.id}").performScrollTo().performClick()
      assertEquals(reloaded.id, loadedProjectId)

      composeTestRule.onNodeWithTag("project_delete_button_${reloaded.id}").performScrollTo().performClick()
      assertEquals(reloaded.id, deletedProjectId)

      composeTestRule.onNodeWithTag("project_save_as_button").performScrollTo().performClick()
      composeTestRule.waitForIdle()
      composeTestRule.onNodeWithTag("save_project_dialog").assertIsDisplayed()
      composeTestRule.onNodeWithTag("project_clip_select_${clip2.id}").performScrollTo().performClick()
      composeTestRule.onNodeWithTag("confirm_save_project_button").performClick()
      composeTestRule.waitForIdle()

      assertEquals("Midnight Synth Jam", savedDialogName)
      assertEquals(listOf(clip1.id, clip2.id), savedDialogClips)
    } finally {
      db.close()
      tempDir.deleteRecursively()
    }
  }

  @Test
  fun `piano guitar drum synthesizers merge into normalized stereo stem and encode valid mp3`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val tempDir = File(context.cacheDir, "multi_instrument_test_${System.currentTimeMillis()}")
    tempDir.mkdirs()
    try {
      // 1. Piano single note & multi-bar progression + recorded melody synthesis
      val singleNote = com.example.audio.InstrumentSynthDsp.synthesizePianoNoteSamples(
        midiNote = 60,
        durationSec = 0.5,
        voice = com.example.audio.PianoVoice.RHODES_EP,
      )
      assertTrue(singleNote.isNotEmpty())
      assertTrue(singleNote.any { kotlin.math.abs(it) > 0.02f })

      val pianoBuffer = com.example.audio.InstrumentSynthDsp.synthesizePianoTrackToBuffer(
        voice = com.example.audio.PianoVoice.CONCERT_GRAND,
        progression = com.example.audio.PianoProgressionPreset.POP_CLASSIC,
        recordedNotes = emptyList(),
        bpm = 104,
        bars = 2,
      )
      assertTrue(pianoBuffer.pcmSamples.isNotEmpty())
      val pianoPeaks = com.example.audio.FourierAudioBufferProcessor.extractWaveformPeaks(pianoBuffer.pcmSamples)
      assertTrue(pianoPeaks.isNotEmpty())

      // 2. Guitar Karplus-Strong string pluck, chord strum, and rhythm track
      val guitarStrum = com.example.audio.InstrumentSynthDsp.synthesizeGuitarChordStrumSamples(
        frets = com.example.audio.GuitarChordShape.C_MAJ.frets,
        voice = com.example.audio.GuitarVoice.ACOUSTIC_STEEL,
        durationSec = 0.7,
      )
      assertTrue(guitarStrum.isNotEmpty())
      assertTrue(guitarStrum.any { kotlin.math.abs(it) > 0.02f })

      val guitarBuffer = com.example.audio.InstrumentSynthDsp.synthesizeGuitarTrackToBuffer(
        voice = com.example.audio.GuitarVoice.CLEAN_ELECTRIC,
        chordSequence = listOf(
          com.example.audio.GuitarChordShape.C_MAJ,
          com.example.audio.GuitarChordShape.G_MAJ,
        ),
        strumStyle = com.example.audio.GuitarStrumStyle.FINGERSTYLE_ARP,
        recordedPlucks = emptyList(),
        bpm = 104,
        bars = 2,
      )
      assertTrue(guitarBuffer.pcmSamples.isNotEmpty())
      val guitarPeaks = com.example.audio.FourierAudioBufferProcessor.extractWaveformPeaks(guitarBuffer.pcmSamples)

      // 3. Drum pad synthesis + kit-differentiated StepSequencerDsp
      val acousticKick = com.example.audio.InstrumentSynthDsp.synthesizeDrumPadHitSamples(
        pad = com.example.audio.DrumPadVoice.KICK,
        kit = com.example.audio.DrumKitPreset.STUDIO_ACOUSTIC,
      )
      val punchKick = com.example.audio.InstrumentSynthDsp.synthesizeDrumPadHitSamples(
        pad = com.example.audio.DrumPadVoice.KICK,
        kit = com.example.audio.DrumKitPreset.TR_808,
      )
      assertTrue(acousticKick.isNotEmpty() && punchKick.isNotEmpty())
      assertTrue(
        "Different drum kits must synthesize distinct waveforms",
        acousticKick.zip(punchKick).any { (a, b) -> kotlin.math.abs(a - b) > 0.01f }
      )

      val seqEngine = com.example.audio.StepSequencerEngine()
      seqEngine.selectDrumKit(com.example.audio.DrumKitPreset.TR_808)
      seqEngine.applyPreset(com.example.audio.SequencerPreset.FOUR_ON_FLOOR)
      val drumBuffer = com.example.audio.StepSequencerDsp.renderPatternToRawBuffer(
        state = seqEngine.state.value,
        bars = 2,
      )
      assertTrue(drumBuffer.pcmSamples.isNotEmpty())
      val drumPeaks = com.example.audio.FourierAudioBufferProcessor.extractWaveformPeaks(drumBuffer.pcmSamples)

      // 4. Write all 3 stems to WAV and merge them via MultiTrackMixerDsp
      val pianoFile = File(tempDir, "piano_stem.wav")
      val guitarFile = File(tempDir, "guitar_stem.wav")
      val drumFile = File(tempDir, "drum_stem.wav")
      com.example.audio.FourierAudioBufferProcessor.writeWavFile(pianoFile, pianoBuffer)
      com.example.audio.FourierAudioBufferProcessor.writeWavFile(guitarFile, guitarBuffer)
      com.example.audio.FourierAudioBufferProcessor.writeWavFile(drumFile, drumBuffer)

      val pianoTrack = GeneratedTrack(
        id = "stem-piano",
        title = "Concert Grand Theme",
        prompt = "Piano",
        genre = "Piano",
        bpm = 104,
        musicalKey = "WAV",
        durationMs = pianoBuffer.durationMs,
        filePath = pianoFile.absolutePath,
        fileSizeBytes = pianoFile.length(),
        waveformPeaks = pianoPeaks,
        createdAtEpochMs = 1_000L,
        sourceLabel = "Piano · Concert Grand",
        category = "Piano",
      )
      val guitarTrack = pianoTrack.copy(
        id = "stem-guitar",
        title = "Clean Electric Arp",
        genre = "Guitar",
        durationMs = guitarBuffer.durationMs,
        filePath = guitarFile.absolutePath,
        fileSizeBytes = guitarFile.length(),
        waveformPeaks = guitarPeaks,
        sourceLabel = "Guitar · Clean Electric",
        category = "Guitar",
      )
      val drumTrack = pianoTrack.copy(
        id = "stem-drums",
        title = "TR-808 Analog Beat",
        genre = "Drums",
        durationMs = drumBuffer.durationMs,
        filePath = drumFile.absolutePath,
        fileSizeBytes = drumFile.length(),
        waveformPeaks = drumPeaks,
        sourceLabel = "Drums · TR-808 Analog",
        category = "Drums",
      )

      val merged = com.example.audio.MultiTrackMixerDsp.mergeTracksToRawBuffer(
        stems = listOf(
          com.example.audio.MixStemSpec(pianoTrack, gain = 1.0f, loopToMatch = true),
          com.example.audio.MixStemSpec(guitarTrack, gain = 0.85f, loopToMatch = true),
          com.example.audio.MixStemSpec(drumTrack, gain = 1.1f, loopToMatch = true),
        ),
      )
      assertEquals(com.example.audio.MultiTrackMixerDsp.TARGET_SAMPLE_RATE, merged.sampleRate)
      assertEquals(com.example.audio.MultiTrackMixerDsp.TARGET_CHANNELS, merged.channelCount)
      assertTrue(merged.pcmSamples.size >= pianoBuffer.pcmSamples.size)
      assertTrue(merged.fourierSpectrumBins.any { it > 0.01f })

      // 5. Encode merged stereo master buffer to MP3 bytes
      val mp3Bytes = com.example.audio.Mp3AudioExporter.encodeRawBufferToMp3Bytes(
        buffer = merged,
        bitrateKbps = 192,
        trackTitle = "Full Band Master Mix",
      )
      assertTrue("Encoded MP3 must be substantial", mp3Bytes.size > 4096)
    } finally {
      tempDir.deleteRecursively()
    }
  }

  @Test
  fun `four page studio navigation and piano guitar drum merger ui trigger real callbacks`() {
    val destinationState = androidx.compose.runtime.mutableStateOf(com.example.ui.StudioDestination.STUDIO)
    val pianoState = androidx.compose.runtime.mutableStateOf(com.example.ui.PianoStudioState())
    val guitarState = androidx.compose.runtime.mutableStateOf(com.example.ui.GuitarStudioState())
    val sequencerState = androidx.compose.runtime.mutableStateOf(com.example.audio.StepSequencerState())

    val pianoTrack = GeneratedTrack(
      id = "ui-piano-1",
      title = "Grand Piano Take",
      prompt = "Piano",
      genre = "Piano",
      bpm = 96,
      musicalKey = "WAV",
      durationMs = 8_000L,
      filePath = "/tmp/piano.wav",
      fileSizeBytes = 128_000L,
      waveformPeaks = listOf(0.3f, 0.7f, 0.5f),
      createdAtEpochMs = 1_000L,
      sourceLabel = "Piano · Concert Grand",
      category = "Piano",
    )
    val guitarTrack = pianoTrack.copy(
      id = "ui-guitar-1",
      title = "Steel Strum Take",
      genre = "Guitar",
      category = "Guitar",
      sourceLabel = "Guitar · Acoustic Steel",
    )
    val drumTrack = pianoTrack.copy(
      id = "ui-drums-1",
      title = "808 Groove",
      genre = "Drums",
      category = "Drums",
      sourceLabel = "Drums · TR-808 Analog",
    )

    val mergeState = androidx.compose.runtime.mutableStateOf(
      com.example.ui.MergeMixerState(
        selectedTrackIds = setOf(pianoTrack.id, guitarTrack.id),
      )
    )

    var quickSavedMp3Track: GeneratedTrack? = null
    var generatedResource: com.example.audio.StudioSoundResource? = null
    var selectedPianoVoice: com.example.audio.PianoVoice? = null
    var playedPianoMidi: Int? = null
    var pianoGenerateClicked = false
    var selectedGuitarVoice: com.example.audio.GuitarVoice? = null
    var pluckedString: Pair<Int, Int>? = null
    var strummedChord = false
    var guitarGenerateClicked = false
    var selectedDrumKit: com.example.audio.DrumKitPreset? = null
    var triggeredDrumPad: com.example.audio.DrumPadVoice? = null
    var drumGenerateClicked = false
    var autoPickedBand = false
    var mergeAndSaveMp3Clicked = false

    composeTestRule.setContent {
      SonicForgeTheme {
        val currentUiState = com.example.ui.SonicForgeUiState(
          tracks = listOf(pianoTrack, guitarTrack, drumTrack),
          allTracks = listOf(pianoTrack, guitarTrack, drumTrack),
          activeDestination = destinationState.value,
          studioSectionTab = com.example.ui.StudioSectionTab.RESOURCES,
          pianoState = pianoState.value,
          guitarState = guitarState.value,
          stepSequencer = sequencerState.value,
          mergeMixer = mergeState.value,
        )
        com.example.ui.SonicForgeScreen(
          uiState = currentUiState,
          onSelectThemeMode = {},
          onSelectEngine = {},
          onPromptChanged = {},
          onApplyPreset = {},
          onGenerateTrack = {},
          onImportAudioClick = {},
          onSelectCategory = {},
          onSearchQueryChanged = {},
          onEditOrganizationClick = {},
          onDismissEditOrganization = {},
          onSaveOrganization = { _, _, _ -> },
          onTrimTrackClick = {},
          onDismissTrimDialog = {},
          onConfirmTrim = { _, _, _, _ -> },
          onExportMp3Click = {},
          onDismissExportDialog = {},
          onExportMp3ToMusic = {},
          onExportMp3ToFolder = {},
          onShareMp3 = {},
          onRequestNotificationPermission = {},
          onOpenNotificationSettings = {},
          onSelectTrack = {},
          onDeleteTrack = {},
          onTogglePlayPause = {},
          onBeginSeek = {},
          onPreviewSeek = {},
          onCommitSeek = {},
          onSeekBy = {},
          onSkipPrevious = {},
          onSkipNext = {},
          onToggleLoop = {},
          onVolumeChange = {},
          onToggleMute = {},
          onSelectDestination = { destinationState.value = it },
          onQuickSaveCurrentMp3 = { quickSavedMp3Track = it },
          onGenerateSoundResource = { generatedResource = it },
          onSelectPianoVoice = {
            selectedPianoVoice = it
            pianoState.value = pianoState.value.copy(selectedVoice = it)
          },
          onPlayPianoKey = { playedPianoMidi = it },
          onTogglePianoRecording = {
            pianoState.value = pianoState.value.copy(isRecording = !pianoState.value.isRecording)
          },
          onGeneratePianoTrack = { pianoGenerateClicked = true },
          onSelectGuitarVoice = {
            selectedGuitarVoice = it
            guitarState.value = guitarState.value.copy(selectedVoice = it)
          },
          onPluckGuitarString = { s, f -> pluckedString = s to f },
          onStrumGuitarChord = { strummedChord = true },
          onGenerateGuitarTrack = { guitarGenerateClicked = true },
          onSelectDrumKit = {
            selectedDrumKit = it
            sequencerState.value = sequencerState.value.copy(drumKit = it)
          },
          onTriggerDrumPad = { triggeredDrumPad = it },
          onGenerateDrumTrack = { drumGenerateClicked = true },
          onAutoPickBandStems = { autoPickedBand = true },
          onMergeAndSaveMp3 = { mergeAndSaveMp3Clicked = true },
        )
      }
    }

    // 1. Studio page: 4-destination navigation + quick MP3 + merger + sound resources
    composeTestRule.onNodeWithTag("nav_studio").assertIsDisplayed()
    composeTestRule.onNodeWithTag("nav_piano").assertIsDisplayed()
    composeTestRule.onNodeWithTag("nav_guitar").assertIsDisplayed()
    composeTestRule.onNodeWithTag("nav_drums").assertIsDisplayed()

    composeTestRule.onNodeWithTag("quick_save_mp3_button").performClick()
    assertEquals(pianoTrack.id, quickSavedMp3Track?.id)

    composeTestRule.onNodeWithTag("studio_page_scroll")
      .performScrollToNode(hasTestTag("multi_track_merger_card"))
    composeTestRule.onNodeWithTag("merger_auto_pick_button").performScrollTo().performClick()
    assertTrue(autoPickedBand)
    composeTestRule.onNodeWithTag("merger_merge_mp3_button").performScrollTo().performClick()
    assertTrue(mergeAndSaveMp3Clicked)

    composeTestRule.onNodeWithTag("studio_page_scroll")
      .performScrollToNode(hasTestTag("sound_resources_card"))
    composeTestRule.onNodeWithTag("sound_resource_btn_res_rhodes_lofi").performScrollTo().performClick()
    assertEquals(com.example.audio.StudioSoundResource.RHODES_LOFI_CHORDS, generatedResource)

    // 2. Piano page
    composeTestRule.onNodeWithTag("nav_piano").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("piano_studio_page").assertIsDisplayed()
    composeTestRule.onNodeWithTag("piano_voice_rhodes_ep").performScrollTo().performClick()
    assertEquals(com.example.audio.PianoVoice.RHODES_EP, selectedPianoVoice)
    composeTestRule.onNodeWithTag("piano_record_button").performScrollTo().performClick()
    assertTrue(pianoState.value.isRecording)
    composeTestRule.onNodeWithTag("piano_key_60").performScrollTo().performClick()
    assertEquals(60, playedPianoMidi)
    composeTestRule.onNodeWithTag("generate_piano_track_button").performScrollTo().performClick()
    assertTrue(pianoGenerateClicked)

    // 3. Guitar page
    composeTestRule.onNodeWithTag("nav_guitar").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("guitar_studio_page").assertIsDisplayed()
    composeTestRule.onNodeWithTag("guitar_voice_classical_nylon").performScrollTo().performClick()
    assertEquals(com.example.audio.GuitarVoice.CLASSICAL_NYLON, selectedGuitarVoice)
    composeTestRule.onNodeWithTag("guitar_fret_0_3").performScrollTo().performClick()
    assertEquals(0 to 3, pluckedString)
    composeTestRule.onNodeWithTag("guitar_strum_now_button").performScrollTo().performClick()
    assertTrue(strummedChord)
    composeTestRule.onNodeWithTag("generate_guitar_track_button").performScrollTo().performClick()
    assertTrue(guitarGenerateClicked)

    // 4. Drums page
    composeTestRule.onNodeWithTag("nav_drums").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("drum_studio_page").assertIsDisplayed()
    composeTestRule.onNodeWithTag("drum_kit_tr_808").performScrollTo().performClick()
    assertEquals(com.example.audio.DrumKitPreset.TR_808, selectedDrumKit)
    composeTestRule.onNodeWithTag("drum_pad_kick").performScrollTo().performClick()
    assertEquals(com.example.audio.DrumPadVoice.KICK, triggeredDrumPad)
    composeTestRule.onNodeWithTag("generate_drum_track_button").performScrollTo().performClick()
    assertTrue(drumGenerateClicked)
  }

  @Test
  fun `audio engine streams generated sound clips via AudioTrack with pause seek volume and stop controls`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val tempDir = File(context.cacheDir, "audio_engine_test_${System.currentTimeMillis()}")
    tempDir.mkdirs()

    var playCount = 0
    var pauseCount = 0
    var flushCount = 0
    var stopCount = 0
    var releaseCount = 0
    var lastVolume = -1f
    var writtenShortsCount = 0

    val fakeFactory = com.example.audio.AudioTrackFactory { _, _, _ ->
      object : com.example.audio.AudioTrackHandle {
        override fun setVolume(gain: Float): Int {
          lastVolume = gain
          return android.media.AudioTrack.SUCCESS
        }

        override fun play() {
          playCount++
        }

        override fun pause() {
          pauseCount++
        }

        override fun flush() {
          flushCount++
        }

        override fun writeBlocking(samples: ShortArray, offsetInShorts: Int, sizeInShorts: Int): Int {
          writtenShortsCount += sizeInShorts
          Thread.sleep(5L)
          return sizeInShorts
        }

        override fun stop() {
          stopCount++
        }

        override fun release() {
          releaseCount++
        }
      }
    }

    val engine = com.example.audio.AudioEngine(trackFactory = fakeFactory)
    try {
      // 1. Missing file produces ERROR state cleanly
      val missingFile = File(tempDir, "does_not_exist.wav")
      engine.playFile(missingFile, clipId = "missing", title = "Missing Clip")
      assertEquals(com.example.audio.AudioEngineStatus.ERROR, engine.state.value.status)

      // 2. Real synthesized clip written to WAV decodes and streams to completion via AudioTrack
      val noteBuf = com.example.audio.InstrumentSynthDsp.synthesizePianoTrackToBuffer(
        voice = com.example.audio.PianoVoice.CONCERT_GRAND,
        progression = com.example.audio.PianoProgressionPreset.POP_CLASSIC,
        recordedNotes = emptyList(),
        bpm = 120,
        bars = 2,
      )
      val wavFile = File(tempDir, "studio_clip.wav")
      com.example.audio.FourierAudioBufferProcessor.writeWavFile(wavFile, noteBuf)

      val completed = java.util.concurrent.CountDownLatch(1)
      engine.setVolume(0.65f)
      engine.playFile(
        file = wavFile,
        clipId = "clip-1",
        title = "Piano Clip",
        looping = false,
        onCompleted = { completed.countDown() },
      )

      assertTrue("Playback should finish streaming the generated clip", completed.await(10, java.util.concurrent.TimeUnit.SECONDS))
      assertEquals(com.example.audio.AudioEngineStatus.COMPLETED, engine.state.value.status)
      assertEquals("clip-1", engine.state.value.activeClipId)
      assertEquals("Piano Clip", engine.state.value.activeClipTitle)
      assertTrue("All PCM samples must be written to AudioTrack", writtenShortsCount >= noteBuf.pcmSamples.size)
      assertEquals(0.65f, lastVolume, 0.01f)
      assertTrue(playCount >= 1)
      assertTrue(stopCount >= 1)
      assertTrue(releaseCount >= 1)

      // 3. Looping float clip supports pause, resume, seekTo, setVolume, and stop
      val longClip = FloatArray(44_100) { idx ->
        kotlin.math.sin(2.0 * kotlin.math.PI * 440.0 * idx / 44_100.0).toFloat() * 0.5f
      }
      engine.playFloatSamples(
        samples = longClip,
        sampleRate = 44_100,
        channelCount = 1,
        clipId = "loop-1",
        title = "440Hz Tone",
        looping = true,
      )

      var waitPlayingTries = 0
      while (engine.state.value.status != com.example.audio.AudioEngineStatus.PLAYING && waitPlayingTries < 60) {
        kotlinx.coroutines.delay(15L)
        waitPlayingTries++
      }
      assertEquals(com.example.audio.AudioEngineStatus.PLAYING, engine.state.value.status)
      assertTrue(engine.state.value.isLooping)

      engine.pause()
      assertEquals(com.example.audio.AudioEngineStatus.PAUSED, engine.state.value.status)
      assertTrue(pauseCount >= 1)

      engine.seekTo(500L)
      assertEquals(500L, engine.state.value.positionMs)
      assertTrue(flushCount >= 1)

      engine.setVolume(0.4f)
      assertEquals(0.4f, engine.state.value.volume, 0.01f)
      assertEquals(0.4f, lastVolume, 0.01f)

      engine.resume()
      assertEquals(com.example.audio.AudioEngineStatus.PLAYING, engine.state.value.status)

      engine.stop()
      assertEquals(com.example.audio.AudioEngineStatus.IDLE, engine.state.value.status)
      assertEquals(0L, engine.state.value.positionMs)
    } finally {
      engine.release()
      tempDir.deleteRecursively()
    }
  }

  @Test
  fun `music prompt generator card accepts prompt text switches API engines and triggers generate button`() {
    val promptState = androidx.compose.runtime.mutableStateOf("")
    val engineState = androidx.compose.runtime.mutableStateOf(com.example.ui.GenerationEngine.HF_MUSICGEN)
    val isGeneratingState = androidx.compose.runtime.mutableStateOf(false)
    var generatedPromptSnapshot = ""
    var generatedEngineSnapshot: com.example.ui.GenerationEngine? = null
    var generateCallCount = 0

    composeTestRule.setContent {
      SonicForgeTheme {
        com.example.ui.components.MusicPromptGeneratorCard(
          promptInput = promptState.value,
          selectedEngine = engineState.value,
          isGenerating = isGeneratingState.value,
          errorMessage = null,
          onPromptChanged = { promptState.value = it },
          onSelectEngine = { engineState.value = it },
          onApplyPreset = { promptState.value = it },
          onGenerateClick = {
            generateCallCount++
            generatedPromptSnapshot = promptState.value
            generatedEngineSnapshot = engineState.value
          },
        )
      }
    }

    composeTestRule.onNodeWithTag("music_prompt_generator_card").assertIsDisplayed()
    composeTestRule.onNodeWithTag("music_prompt_generate_button").assertIsNotEnabled()

    // Apply preset then clear it
    composeTestRule.onNodeWithTag("music_prompt_preset_0").performClick()
    composeTestRule.waitForIdle()
    assertTrue(promptState.value.isNotBlank())
    composeTestRule.onNodeWithTag("music_prompt_clear_button").performClick()
    composeTestRule.waitForIdle()
    assertEquals("", promptState.value)

    // Switch API engine & type custom music prompt
    composeTestRule.onNodeWithTag("engine_chip_audioldm").performClick()
    composeTestRule.waitForIdle()
    assertEquals(com.example.ui.GenerationEngine.HF_AUDIO_LDM, engineState.value)

    composeTestRule.onNodeWithTag("music_prompt_input_field")
      .performTextInput("Atmospheric cyberpunk synth bass 118 BPM")
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("music_prompt_generate_button")
      .assertIsEnabled()
      .performClick()
    composeTestRule.waitForIdle()

    assertEquals(1, generateCallCount)
    assertEquals("Atmospheric cyberpunk synth bass 118 BPM", generatedPromptSnapshot)
    assertEquals(com.example.ui.GenerationEngine.HF_AUDIO_LDM, generatedEngineSnapshot)
  }

  @Test
  fun `playback control bar manages play pause and stop states for generated track playback`() {
    val isPlaying = androidx.compose.runtime.mutableStateOf(false)
    val positionMs = androidx.compose.runtime.mutableStateOf(0L)
    var playClicks = 0
    var pauseClicks = 0
    var stopClicks = 0

    composeTestRule.setContent {
      SonicForgeTheme {
        com.example.ui.components.PlaybackControlBar(
          isPlaying = isPlaying.value,
          isPreparing = false,
          hasTrack = true,
          positionMs = positionMs.value,
          onPlay = {
            playClicks++
            isPlaying.value = true
            if (positionMs.value == 0L) positionMs.value = 3_500L
          },
          onPause = {
            pauseClicks++
            isPlaying.value = false
          },
          onStop = {
            stopClicks++
            isPlaying.value = false
            positionMs.value = 0L
          },
        )
      }
    }

    // 1. Initially stopped: Play enabled, Pause & Stop disabled
    composeTestRule.onNodeWithTag("playback_control_bar").assertIsDisplayed()
    composeTestRule.onNodeWithTag("playback_play_button").assertIsEnabled()
    composeTestRule.onNodeWithTag("playback_pause_button").assertIsNotEnabled()
    composeTestRule.onNodeWithTag("playback_stop_button").assertIsNotEnabled()

    // 2. Click Play -> Playing state: Play disabled, Pause & Stop enabled
    composeTestRule.onNodeWithTag("playback_play_button").performClick()
    composeTestRule.waitForIdle()
    assertEquals(1, playClicks)
    composeTestRule.onNodeWithTag("playback_play_button").assertIsNotEnabled()
    composeTestRule.onNodeWithTag("playback_pause_button").assertIsEnabled()
    composeTestRule.onNodeWithTag("playback_stop_button").assertIsEnabled()

    // 3. Click Pause -> Paused state at 3.5s: Play & Stop enabled, Pause disabled
    composeTestRule.onNodeWithTag("playback_pause_button").performClick()
    composeTestRule.waitForIdle()
    assertEquals(1, pauseClicks)
    composeTestRule.onNodeWithTag("playback_play_button").assertIsEnabled()
    composeTestRule.onNodeWithTag("playback_pause_button").assertIsNotEnabled()
    composeTestRule.onNodeWithTag("playback_stop_button").assertIsEnabled()

    // 4. Click Stop -> Stopped state reset to 0ms: Play enabled, Pause & Stop disabled
    composeTestRule.onNodeWithTag("playback_stop_button").performClick()
    composeTestRule.waitForIdle()
    assertEquals(1, stopClicks)
    assertEquals(0L, positionMs.value)
    composeTestRule.onNodeWithTag("playback_play_button").assertIsEnabled()
    composeTestRule.onNodeWithTag("playback_pause_button").assertIsNotEnabled()
    composeTestRule.onNodeWithTag("playback_stop_button").assertIsNotEnabled()
  }

  @Test
  fun `room track history database persists generated tracks filters searches increments play counts and clears cleanly`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = androidx.room.Room.inMemoryDatabaseBuilder(
      context,
      com.example.data.local.SonicForgeDatabase::class.java,
    ).allowMainThreadQueries().build()

    val tempDir = File(context.cacheDir, "history_repo_test_${System.currentTimeMillis()}")
    tempDir.mkdirs()

    try {
      val repo = com.example.data.TrackHistoryRepository(
        historyDao = db.trackHistoryDao(),
        trackDao = db.generatedTrackDao(),
      )

      val pianoFile = File(tempDir, "piano_take.wav").apply { writeBytes(ByteArray(128) { 1 }) }
      val guitarFile = File(tempDir, "guitar_take.wav").apply { writeBytes(ByteArray(128) { 2 }) }
      val promptFile = File(tempDir, "cyberpunk_prompt.wav").apply { writeBytes(ByteArray(128) { 3 }) }

      val pianoTrack = com.example.model.GeneratedTrack(
        id = "track-piano-1",
        title = "Midnight Grand Piano",
        prompt = "Concert Grand • Pop Classic • 120 BPM",
        filePath = pianoFile.absolutePath,
        durationMs = 8_000L,
        bpm = 120,
        musicalKey = "C Major",
        genre = "Piano Studio",
        fileSizeBytes = pianoFile.length(),
        waveformPeaks = listOf(0.4f, 0.7f, 0.5f),
        sourceLabel = "Piano Studio",
        createdAtEpochMs = 1_000L,
      )
      val guitarTrack = com.example.model.GeneratedTrack(
        id = "track-guitar-1",
        title = "Sunset Acoustic Strum",
        prompt = "Steel Acoustic • C-G-Am-F • 110 BPM",
        filePath = guitarFile.absolutePath,
        durationMs = 9_000L,
        bpm = 110,
        musicalKey = "G Major",
        genre = "Guitar Studio",
        fileSizeBytes = guitarFile.length(),
        waveformPeaks = listOf(0.5f, 0.8f, 0.4f),
        sourceLabel = "Guitar Studio",
        createdAtEpochMs = 2_000L,
      )
      val promptTrack = com.example.model.GeneratedTrack(
        id = "track-prompt-1",
        title = "Neon Synthwave Pulse",
        prompt = "Retro cyberpunk synthwave bassline 124 BPM",
        filePath = promptFile.absolutePath,
        durationMs = 10_000L,
        bpm = 124,
        musicalKey = "A Minor",
        genre = "Synthwave",
        fileSizeBytes = promptFile.length(),
        waveformPeaks = listOf(0.6f, 0.9f, 0.5f),
        sourceLabel = "HF MusicGen",
        createdAtEpochMs = 3_000L,
      )

      repo.recordGeneratedTrack(pianoTrack, instrumentType = com.example.data.local.TrackHistoryTypes.PIANO)
      repo.recordGeneratedTrack(guitarTrack, instrumentType = com.example.data.local.TrackHistoryTypes.GUITAR)
      repo.recordGeneratedTrack(promptTrack, instrumentType = com.example.data.local.TrackHistoryTypes.PROMPT)

      val all = repo.allHistoryFlow.first()
      assertEquals(3, all.size)
      assertEquals("track-prompt-1", all.first().trackId) // newest first
      assertTrue(all.all { it.fileExists })

      // Filter by instrument type
      val pianoOnly = repo.observeFilteredHistory(
        instrumentType = com.example.data.local.TrackHistoryTypes.PIANO,
      ).first()
      assertEquals(1, pianoOnly.size)
      assertEquals("Midnight Grand Piano", pianoOnly.first().title)

      // Search by prompt text
      val searchResults = repo.observeFilteredHistory(query = "cyberpunk").first()
      assertEquals(1, searchResults.size)
      assertEquals("track-prompt-1", searchResults.first().trackId)

      // Record playback increments play count & records timestamp
      repo.recordTrackPlayed("track-piano-1")
      repo.recordTrackPlayed("track-piano-1")
      val updatedPiano = db.trackHistoryDao().getByTrackId("track-piano-1")
      org.junit.Assert.assertNotNull(updatedPiano)
      assertEquals(2, updatedPiano!!.playCount)
      assertTrue(updatedPiano.lastPlayedAtEpochMs > 0L)

      // Sync title on rename
      repo.syncTrackTitle("track-guitar-1", "Golden Hour Folk Guitar")
      val renamedGuitar = db.trackHistoryDao().getByTrackId("track-guitar-1")
      assertEquals("Golden Hour Folk Guitar", renamedGuitar?.title)

      // Delete individual entry
      repo.deleteHistoryEntry(updatedPiano.id)
      assertEquals(2, repo.historyCountFlow.first())

      // Clear all history
      repo.clearAllHistory()
      assertEquals(0, repo.historyCountFlow.first())

      // Backfill populates history from existing generated_tracks table when history is empty
      db.generatedTrackDao().insertTrack(
        com.example.data.local.GeneratedTrackEntity(
          id = promptTrack.id,
          title = promptTrack.title,
          prompt = promptTrack.prompt,
          filePath = promptTrack.filePath,
          durationMs = promptTrack.durationMs,
          bpm = promptTrack.bpm,
          musicalKey = promptTrack.musicalKey,
          genre = promptTrack.genre,
          fileSizeBytes = promptTrack.fileSizeBytes,
          waveformPeaksCsv = "0.6,0.9,0.5",
          sourceLabel = promptTrack.sourceLabel,
          createdAtEpochMs = promptTrack.createdAtEpochMs,
        ),
      )
      repo.backfillFromExistingTracksIfEmpty()
      val backfilled = repo.allHistoryFlow.first()
      assertEquals(1, backfilled.size)
      assertEquals("track-prompt-1", backfilled.first().trackId)
    } finally {
      db.close()
      tempDir.deleteRecursively()
    }
  }

  @Test
  fun `track history card renders past creations filters by source triggers play reuse delete and clear confirmation`() {
    val itemsState = androidx.compose.runtime.mutableStateOf(
      listOf(
        com.example.data.local.TrackHistoryItem(
          id = "hist-1",
          trackId = "t-1",
          title = "Warm Rhodes Chords",
          prompt = "Rhodes EP • Neo Soul • 96 BPM",
          filePath = "/tmp/rhodes.wav",
          durationMs = 8_000L,
          bpm = 96,
          genre = "Piano Studio",
          instrumentType = com.example.data.local.TrackHistoryTypes.PIANO,
          engineOrSource = "Piano Studio",
          createdAtEpochMs = 1_700_000_000_000L,
          lastPlayedAtEpochMs = 1_700_000_005_000L,
          playCount = 3,
          fileExists = true,
        ),
        com.example.data.local.TrackHistoryItem(
          id = "hist-2",
          trackId = "t-2",
          title = "Cyberpunk Bassline",
          prompt = "Dark synthwave bassline 124 BPM",
          filePath = "/tmp/bass.wav",
          durationMs = 10_000L,
          bpm = 124,
          genre = "Synthwave",
          instrumentType = com.example.data.local.TrackHistoryTypes.PROMPT,
          engineOrSource = "HF MusicGen",
          createdAtEpochMs = 1_700_000_010_000L,
          lastPlayedAtEpochMs = 0L,
          playCount = 1,
          fileExists = true,
        ),
      ),
    )
    val filterState = androidx.compose.runtime.mutableStateOf(com.example.data.local.TrackHistoryTypes.ALL)
    val queryState = androidx.compose.runtime.mutableStateOf("")
    var playedItemId: String? = null
    var reusedPrompt: String? = null
    var deletedEntryId: String? = null
    var clearedAll = false

    composeTestRule.setContent {
      SonicForgeTheme {
        androidx.compose.foundation.layout.Column(
          modifier = androidx.compose.ui.Modifier.verticalScroll(
            androidx.compose.foundation.rememberScrollState(),
          ),
        ) {
          com.example.ui.components.TrackHistoryCard(
            historyItems = itemsState.value,
            totalHistoryCount = itemsState.value.size,
            selectedTypeFilter = filterState.value,
            searchQuery = queryState.value,
            currentTrackId = null,
            isPlaying = false,
            onSelectTypeFilter = { filterState.value = it },
            onSearchQueryChanged = { queryState.value = it },
            onPlayHistoryItem = { playedItemId = it.id },
            onReusePrompt = { reusedPrompt = it },
            onDeleteHistoryItem = { deletedEntryId = it },
            onClearAllHistory = {
              clearedAll = true
              itemsState.value = emptyList()
            },
          )
        }
      }
    }

    composeTestRule.onNodeWithTag("track_history_card").assertIsDisplayed()
    composeTestRule.onNodeWithTag("history_count_badge").assertIsDisplayed()
    composeTestRule.onNodeWithTag("history_entry_hist-1").assertIsDisplayed()
    composeTestRule.onNodeWithTag("history_plays_hist-1").assertIsDisplayed()

    // Filter chip & search input
    composeTestRule.onNodeWithTag("history_filter_piano").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertEquals(com.example.data.local.TrackHistoryTypes.PIANO, filterState.value)

    composeTestRule.onNodeWithTag("history_search_input").performScrollTo().performTextInput("Rhodes")
    composeTestRule.waitForIdle()
    assertEquals("Rhodes", queryState.value)

    // Play, Reuse Prompt, Delete
    composeTestRule.onNodeWithTag("history_play_hist-1").performScrollTo().performClick()
    assertEquals("hist-1", playedItemId)

    composeTestRule.onNodeWithTag("history_reuse_hist-2").performScrollTo().performClick()
    assertEquals("Dark synthwave bassline 124 BPM", reusedPrompt)

    composeTestRule.onNodeWithTag("history_delete_hist-2").performScrollTo().performClick()
    assertEquals("hist-2", deletedEntryId)

    // Clear history confirmation dialog
    composeTestRule.onNodeWithTag("history_clear_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("history_confirm_clear_button").performClick()
    composeTestRule.waitForIdle()
    assertTrue(clearedAll)
    composeTestRule.onNodeWithTag("history_empty_state").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun `fixed piano guitar and drum studio pages support quick chords decoupled progression builder muted strings and live drum pad takes`() {
    // 1. Verify fractional-delay Karplus-Strong guitar synthesis at high fret produces valid audible audio
    val highFretSamples = com.example.audio.InstrumentSynthDsp.synthesizeGuitarStringSamples(
      midiNote = 69, // A4 (440 Hz, High E fret 5)
      durationSec = 0.6,
      voice = com.example.audio.GuitarVoice.ACOUSTIC_STEEL,
    )
    assertTrue(highFretSamples.isNotEmpty())
    assertTrue(highFretSamples.all { it.isFinite() })
    val peakGuitar = highFretSamples.maxOf { kotlin.math.abs(it) }
    assertTrue("Synthesized guitar string should be audible (peak=$peakGuitar)", peakGuitar > 0.15f)

    // 2. Verify live drum pad take synthesis renders audible PCM buffer
    val drumTakeBuf = com.example.audio.InstrumentSynthDsp.synthesizeDrumPadTakeToBuffer(
      kit = com.example.audio.DrumKitPreset.TR_808,
      recordedHits = listOf(
        com.example.audio.RecordedDrumHit(com.example.audio.DrumPadVoice.KICK, 0L),
        com.example.audio.RecordedDrumHit(com.example.audio.DrumPadVoice.SNARE, 500L),
        com.example.audio.RecordedDrumHit(com.example.audio.DrumPadVoice.CLOSED_HAT, 750L),
      ),
      bpm = 120,
    )
    assertTrue(drumTakeBuf.pcmSamples.isNotEmpty())
    assertTrue(drumTakeBuf.durationMs >= 2_000L)
    val peakDrum = drumTakeBuf.pcmSamples.maxOf { kotlin.math.abs(it.toInt()) }
    assertTrue("Synthesized drum pad take should have strong audible peaks (peak=$peakDrum)", peakDrum > 4_000)

    // 3. Verify Piano, Guitar, and Drum UI fixes
    var playedQuickChord: IntArray? = null
    val guitarState = androidx.compose.runtime.mutableStateOf(
      com.example.ui.GuitarStudioState(
        selectedChord = com.example.audio.GuitarChordShape.D_MIN, // has muted strings 0 & 1
        chordSequence = listOf(
          com.example.audio.GuitarChordShape.C_MAJ,
          com.example.audio.GuitarChordShape.G_MAJ,
        ),
      ),
    )
    var appliedPreset: com.example.audio.GuitarProgressionPreset? = null
    val drumState = androidx.compose.runtime.mutableStateOf(
      com.example.ui.DrumStudioState(
        isRecordingPads = false,
        recordedHits = listOf(
          com.example.audio.RecordedDrumHit(com.example.audio.DrumPadVoice.KICK, 0L),
        ),
      ),
    )
    var previewedDrumTake = false
    var clearedDrumTake = false

    val activePage = androidx.compose.runtime.mutableStateOf(0)

    composeTestRule.setContent {
      SonicForgeTheme {
        androidx.compose.foundation.layout.Column(
          modifier = androidx.compose.ui.Modifier.verticalScroll(
            androidx.compose.foundation.rememberScrollState(),
          ),
        ) {
          when (activePage.value) {
            0 -> com.example.ui.components.PianoStudioPage(
              state = com.example.ui.PianoStudioState(
                recordedNotes = listOf(
                  com.example.audio.RecordedNoteEvent(midiNote = 60, offsetMs = 0L),
                  com.example.audio.RecordedNoteEvent(midiNote = 64, offsetMs = 300L),
                ),
              ),
              onSelectVoice = {},
              onSelectProgression = {},
              onShiftOctave = {},
              onStepBpm = {},
              onPlayKey = {},
              onPlayChord = { playedQuickChord = it },
              onToggleRecording = {},
              onClearRecording = {},
              onPreviewPattern = {},
              onGenerateTrack = {},
            )
            1 -> com.example.ui.components.GuitarStudioPage(
              state = guitarState.value,
              onSelectVoice = {},
              onSelectChord = { chord ->
                guitarState.value = guitarState.value.copy(selectedChord = chord)
              },
              onAddChordToSequence = { chord ->
                guitarState.value = guitarState.value.copy(
                  chordSequence = guitarState.value.chordSequence + chord,
                )
              },
              onRemoveChordFromSequenceAt = { idx ->
                guitarState.value = guitarState.value.copy(
                  chordSequence = guitarState.value.chordSequence.filterIndexed { i, _ -> i != idx },
                )
              },
              onApplyProgressionPreset = { preset ->
                appliedPreset = preset
                guitarState.value = guitarState.value.copy(chordSequence = preset.chords)
              },
              onSelectStrumStyle = {},
              onStepBpm = {},
              onPluckString = { _, _ -> },
              onStrumChord = {},
              onToggleRecording = {},
              onClearRecording = {},
              onPreviewPattern = {},
              onGenerateTrack = {},
            )
            else -> com.example.ui.components.DrumStudioPage(
              sequencerState = com.example.audio.StepSequencerState(),
              metronomeState = com.example.audio.MetronomeState(),
              drumState = drumState.value,
              onSelectDrumKit = {},
              onTriggerDrumPad = {},
              onToggleDrumPadRecording = {
                drumState.value = drumState.value.copy(isRecordingPads = !drumState.value.isRecordingPads)
              },
              onClearDrumPadRecording = { clearedDrumTake = true },
              onPreviewDrumPadTake = { previewedDrumTake = true },
              onToggleSequencerStep = { _, _ -> },
              onToggleSequencerPlayStop = {},
              onClearSequencerPattern = {},
              onApplySequencerPreset = {},
              onStepSequencerBpm = {},
              onToggleSequencerBpmSync = {},
              onCycleSequencerSwing = {},
              onGenerateDrumTrack = {},
              onToggleMetronomePlayStop = {},
              onStepMetronomeBpm = {},
              onToggleMetronomeBpmSync = {},
              onMetronomeVolumeChange = {},
              onMetronomePitchHzChange = {},
              onSelectMetronomeToneStyle = {},
              onSelectMetronomeTimeSignature = {},
              onToggleMetronomeAccentDownbeat = {},
              onTapMetronomeTempo = {},
            )
          }
        }
      }
    }

    // Piano: quick chord pad + recorded trail
    composeTestRule.onNodeWithTag("piano_quick_chord_0").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    org.junit.Assert.assertNotNull(playedQuickChord)
    assertTrue(playedQuickChord!!.size >= 3)
    composeTestRule.onNodeWithTag("piano_recorded_trail").performScrollTo().assertIsDisplayed()

    // Guitar: muted string X badge on Dm, decoupled chord audition vs progression builder
    activePage.value = 1
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("guitar_muted_string_0").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("guitar_chord_g_maj").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertEquals(com.example.audio.GuitarChordShape.G_MAJ, guitarState.value.selectedChord)
    assertEquals(2, guitarState.value.chordSequence.size) // clicking G did NOT delete G from sequence!

    composeTestRule.onNodeWithTag("guitar_add_to_seq_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertEquals(3, guitarState.value.chordSequence.size)

    composeTestRule.onNodeWithTag("guitar_seq_remove_0").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertEquals(2, guitarState.value.chordSequence.size)

    composeTestRule.onNodeWithTag("generate_guitar_track_button").performScrollTo()
    composeTestRule.onNodeWithTag("guitar_preset_minor").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertEquals(com.example.audio.GuitarProgressionPreset.INDIE_MINOR, appliedPreset)
    assertEquals(4, guitarState.value.chordSequence.size)

    // Drums: live pad recording controls
    activePage.value = 2
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("drum_record_pads_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    assertTrue(drumState.value.isRecordingPads)

    composeTestRule.onNodeWithTag("drum_preview_pads_button").performScrollTo().performClick()
    assertTrue(previewedDrumTake)

    composeTestRule.onNodeWithTag("drum_clear_pads_button").performScrollTo().performClick()
    assertTrue(clearedDrumTake)
  }

  @Test
  fun `gemini json parser offline arranger and offline music library produce valid plans and audio`() = runBlocking {
    val structuredJson = """
      {
        "title": "Rainy Neon Rhodes",
        "summary": "Warm late-night Rhodes chords over fingerpicked acoustic guitar and dusty boom-bap drums.",
        "genre": "Lo-Fi Chillhop",
        "musicalKey": "A Minor",
        "bpm": 88,
        "pianoVoice": "rhodes_ep",
        "pianoProgression": "lofi_sevenths",
        "guitarVoice": "classical_nylon",
        "guitarChords": ["a_min", "f_maj", "c_maj", "e_min"],
        "guitarStrumStyle": "fingerstyle_arp",
        "drumKit": "lofi_vinyl",
        "drumPreset": "lofi_boom_bap",
        "reverbPreset": "STUDIO",
        "pianoGain": 0.95,
        "guitarGain": 0.80,
        "drumGain": 0.92,
        "sampleGain": 0.60,
        "suggestedSampleQueries": ["vinyl crackle", "midnight rain", "rhodes chords"]
      }
    """.trimIndent()

    val plan = com.example.ai.GeminiMusicArranger.parseArrangementJson(
      jsonText = structuredJson,
      sourcePrompt = "Rainy midnight lo-fi study beat",
    )
    assertEquals("Rainy Neon Rhodes", plan.title)
    assertEquals(88, plan.bpm)
    assertEquals(com.example.audio.PianoVoice.RHODES_EP, plan.pianoVoice)
    assertEquals(com.example.audio.GuitarVoice.CLASSICAL_NYLON, plan.guitarVoice)
    assertEquals(4, plan.guitarChords.size)
    assertEquals(com.example.audio.GuitarStrumStyle.FINGERSTYLE_ARP, plan.guitarStrumStyle)
    assertEquals(com.example.audio.ReverbPreset.STUDIO, plan.reverbPreset)
    assertEquals(3, plan.suggestedSampleQueries.size)
    assertEquals("vinyl crackle", plan.suggestedSampleQueries.first())

    // Offline on-device arranger produces coordinated plan without network or API keys
    val offlineArranger = com.example.ai.OfflineMusicArranger()
    val offlinePlan = offlineArranger.generateArrangement("Neon 80s synthwave driving beat 118 bpm")
    assertEquals("Offline Model", offlinePlan.engineLabel)
    assertEquals(118, offlinePlan.bpm)
    assertEquals("Synthwave", offlinePlan.genre)
    assertTrue(offlinePlan.suggestedSampleQueries.isNotEmpty())

    // Built-in offline library searches & synthesizes non-empty PCM audio
    val vinylMatches = com.example.audio.OfflineMusicLibrary.search("vinyl crackle")
    assertTrue(vinylMatches.isNotEmpty())
    assertEquals("tex_vinyl_crackle", vinylMatches.first().id)
    val pcm = com.example.audio.OfflineMusicSynthesizerDsp.renderAssetPcm(vinylMatches.first())
    assertTrue(pcm.size > 44100)
  }

  @Test
  fun `ai producer and built-in offline music library card switches engine arranges and triggers preview and import`() {
    val samplePlan = com.example.ai.MusicArrangementPlan(
      title = "Golden Hour Acoustic",
      summary = "Warm dreadnought strums paired with bright piano chords and live studio drums.",
      genre = "Indie Folk",
      musicalKey = "G Major",
      bpm = 104,
      pianoVoice = com.example.audio.PianoVoice.CONCERT_GRAND,
      pianoProgression = com.example.audio.PianoProgressionPreset.POP_CLASSIC,
      guitarVoice = com.example.audio.GuitarVoice.ACOUSTIC_STEEL,
      guitarChords = listOf(
        com.example.audio.GuitarChordShape.G_MAJ,
        com.example.audio.GuitarChordShape.C_ADD9,
        com.example.audio.GuitarChordShape.E_MIN,
        com.example.audio.GuitarChordShape.D_MIN,
      ),
      guitarStrumStyle = com.example.audio.GuitarStrumStyle.ACOUSTIC_STRUM,
      drumKit = com.example.audio.DrumKitPreset.STUDIO_ACOUSTIC,
      drumPreset = com.example.audio.SequencerPreset.FOUR_ON_FLOOR,
      reverbPreset = com.example.audio.ReverbPreset.STUDIO,
      pianoGain = 0.9f,
      guitarGain = 0.9f,
      drumGain = 0.95f,
      sampleGain = 0.65f,
      suggestedSampleQueries = listOf("shaker tambourine", "vinyl crackle"),
      sourcePrompt = "Golden hour acoustic folk",
      engineLabel = "Offline Model",
    )
    val offlineAsset = com.example.audio.OfflineMusicLibrary.allAssets.first()

    val aiState = androidx.compose.runtime.mutableStateOf(com.example.ui.AiProducerState())
    val libState = androidx.compose.runtime.mutableStateOf(
      com.example.ui.OfflineMusicStudioState(items = listOf(offlineAsset)),
    )
    var renderedFullMix = false
    var appliedToInstruments = false
    var filteredQuery: String? = null
    var previewedAssetId: String? = null
    var importedAssetId: String? = null

    composeTestRule.setContent {
      SonicForgeTheme {
        androidx.compose.foundation.layout.Column(
          modifier = androidx.compose.ui.Modifier.verticalScroll(
            androidx.compose.foundation.rememberScrollState(),
          ),
        ) {
          com.example.ui.components.AiProducerAndSamplesCard(
            aiState = aiState.value,
            offlineMusicState = libState.value,
            onSelectAiEngine = { aiState.value = aiState.value.copy(selectedEngine = it) },
            onAiPromptChanged = { aiState.value = aiState.value.copy(promptInput = it) },
            onArrangeTrack = {
              aiState.value = aiState.value.copy(currentPlan = samplePlan)
            },
            onRenderAiFullMix = { renderedFullMix = true },
            onApplyPlanToInstruments = { appliedToInstruments = true },
            onDetachSampleFromAiMix = {
              aiState.value = aiState.value.copy(attachedSampleTrackId = null, attachedSampleTitle = null)
            },
            onOfflineQueryChanged = {
              filteredQuery = it
              libState.value = libState.value.copy(searchQuery = it)
            },
            onSelectOfflineCategory = { libState.value = libState.value.copy(selectedCategory = it) },
            onPreviewOfflineAsset = { previewedAssetId = it.id },
            onImportOfflineAsset = {
              importedAssetId = it.id
              libState.value = libState.value.copy(importedAssetIds = setOf(it.id))
            },
          )
        }
      }
    }

    composeTestRule.onNodeWithTag("ai_producer_card").assertIsDisplayed()
    composeTestRule.onNodeWithTag("ai_engine_offline_model").assertIsDisplayed()
    composeTestRule.onNodeWithTag("ai_engine_gemini_cloud").performClick()
    assertEquals(com.example.ai.AiProducerEngine.GEMINI_CLOUD, aiState.value.selectedEngine)
    composeTestRule.onNodeWithTag("ai_engine_offline_model").performClick()
    assertEquals(com.example.ai.AiProducerEngine.OFFLINE_MODEL, aiState.value.selectedEngine)
    composeTestRule.onNodeWithTag("ai_arrange_button").assertIsNotEnabled()

    // Tap preset -> Arrange -> Blueprint renders
    composeTestRule.onNodeWithTag("ai_producer_preset_0").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("ai_arrange_button").assertIsEnabled().performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("ai_arrangement_plan_card").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("ai_render_full_mix_button").performScrollTo().performClick()
    assertTrue(renderedFullMix)

    composeTestRule.onNodeWithTag("ai_apply_to_instruments_button").performScrollTo().performClick()
    assertTrue(appliedToInstruments)

    // Tap suggested offline layer chip
    composeTestRule.onNodeWithTag("ai_sample_query_0").performScrollTo().performClick()
    assertEquals("shaker tambourine", filteredQuery)

    // Preview & Import built-in offline asset
    composeTestRule.onNodeWithTag("offline_music_library_card").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("offline_preview_${offlineAsset.id}").performScrollTo().performClick()
    assertEquals(offlineAsset.id, previewedAssetId)

    composeTestRule.onNodeWithTag("offline_import_${offlineAsset.id}").performScrollTo().performClick()
    assertEquals(offlineAsset.id, importedAssetId)
  }

  @Test
  fun `room storage export card switches format and triggers music and folder directory export`() {
    val exportState = androidx.compose.runtime.mutableStateOf(com.example.ui.RoomStorageExportState())
    var exportedToMusic = false
    var choseDirectory = false

    composeTestRule.setContent {
      SonicForgeTheme {
        com.example.ui.components.RoomStorageExportCard(
          state = exportState.value,
          roomTrackCount = 3,
          onSelectFormat = { exportState.value = exportState.value.copy(format = it) },
          onExportToDeviceMusic = { exportedToMusic = true },
          onChooseStorageDirectory = { choseDirectory = true },
          onDismissStatus = {
            exportState.value = exportState.value.copy(statusMessage = null, errorMessage = null)
          },
        )
      }
    }

    composeTestRule.onNodeWithTag("room_storage_export_card").assertIsDisplayed()
    composeTestRule.onNodeWithTag("room_export_format_wav").performClick()
    assertEquals(com.example.audio.AudioExportFormat.WAV, exportState.value.format)

    composeTestRule.onNodeWithTag("room_export_to_music_button").assertIsEnabled().performClick()
    assertTrue(exportedToMusic)

    composeTestRule.onNodeWithTag("room_export_choose_folder_button").assertIsEnabled().performClick()
    assertTrue(choseDirectory)
  }
}



