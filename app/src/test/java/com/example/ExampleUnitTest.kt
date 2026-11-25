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
}
