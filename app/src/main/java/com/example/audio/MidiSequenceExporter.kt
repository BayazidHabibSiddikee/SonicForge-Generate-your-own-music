package com.example.audio

import com.example.model.GeneratedTrack
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import kotlin.math.roundToInt

enum class MidiExportMode(
  val id: String,
  val label: String,
  val summaryTag: String,
) {
  MELODY_ONLY(
    id = "midi_melody",
    label = "Melody Lead",
    summaryTag = "SMF-0 Lead Melody",
  ),
  MELODY_AND_CHORDS(
    id = "midi_melody_chords",
    label = "Melody + Chords",
    summaryTag = "SMF-1 Melody & Chords",
  ),
  FULL_ARRANGEMENT(
    id = "midi_full",
    label = "Full Arrangement",
    summaryTag = "SMF-1 Multi-Track",
  ),
}

data class MidiFileSummary(
  val format: Int,
  val trackCount: Int,
  val divisionPpq: Int,
  val tempoBpm: Int,
  val noteOnCount: Int,
  val trackNames: List<String>,
)

/**
 * Standard MIDI File (`.mid`) generator converting synthesized prompt melody motifs, chord
 * progressions, basslines, and percussion grooves into DAW-ready SMF Type 0 / Type 1 sequences.
 */
object MidiSequenceExporter {

  const val TICKS_PER_QUARTER_NOTE = 480
  private const val TICKS_PER_SIXTEENTH = TICKS_PER_QUARTER_NOTE / 4 // 120 ticks

  private data class TimedMidiMessage(
    val tick: Long,
    val sortPriority: Int, // 0 = meta/program, 1 = note off, 2 = note on
    val payload: ByteArray,
  )

  fun exportTrackToMidiBytes(
    track: GeneratedTrack,
    mode: MidiExportMode = MidiExportMode.FULL_ARRANGEMENT,
  ): ByteArray {
    val seedPrompt = track.prompt.ifBlank { "${track.genre} ${track.title}".trim() }
    val pattern = WavTrackSynthesizer.extractSequencePattern(
      prompt = seedPrompt,
      overrideBpm = track.bpm.takeIf { it in 55..180 },
    )
    return encodePatternToMidiBytes(
      pattern = pattern,
      trackTitle = track.title.ifBlank { "SonicForge Sequence" },
      mode = mode,
    )
  }

  fun encodePatternToMidiBytes(
    pattern: SynthesizedSequencePattern,
    trackTitle: String = "SonicForge Sequence",
    mode: MidiExportMode = MidiExportMode.FULL_ARRANGEMENT,
  ): ByteArray {
    val bpm = pattern.bpm.coerceIn(40, 220)
    val mpqn = (60_000_000.0 / bpm).roundToInt().coerceAtLeast(1)

    val leadProgram = when (pattern.profile) {
      WavTrackSynthesizer.StyleProfile.SYNTHWAVE -> 81 // Sawtooth Lead
      WavTrackSynthesizer.StyleProfile.LOFI_CHILL -> 4 // Electric Piano 1
      WavTrackSynthesizer.StyleProfile.AMBIENT_DRIFT -> 89 // Warm Pad
      WavTrackSynthesizer.StyleProfile.TECHNO_PULSE -> 80 // Square Lead
      WavTrackSynthesizer.StyleProfile.PIANO_CINEMA -> 0 // Acoustic Grand Piano
    }
    val chordProgram = when (pattern.profile) {
      WavTrackSynthesizer.StyleProfile.LOFI_CHILL -> 5 // Electric Piano 2
      WavTrackSynthesizer.StyleProfile.PIANO_CINEMA -> 48 // String Ensemble 1
      else -> 89 // Warm Pad
    }
    val bassProgram = when (pattern.profile) {
      WavTrackSynthesizer.StyleProfile.LOFI_CHILL,
      WavTrackSynthesizer.StyleProfile.PIANO_CINEMA -> 33 // Fingered Electric Bass
      else -> 38 // Synth Bass 1
    }

    val tracks = ArrayList<ByteArray>()
    val midiFormat: Int

    if (mode == MidiExportMode.MELODY_ONLY) {
      midiFormat = 0
      val singleEvents = ArrayList<TimedMidiMessage>()
      singleEvents.add(metaTextEvent(0L, 0x03, "$trackTitle - Lead Melody"))
      singleEvents.add(tempoMetaEvent(0L, mpqn))
      singleEvents.add(timeSignatureMetaEvent(0L, numerator = pattern.beatsPerBar, denominatorPow2 = 2))
      singleEvents.add(programChangeEvent(0L, channel = 0, program = leadProgram))
      appendNoteEvents(singleEvents, pattern.melodyNotes, channel = 0)
      tracks.add(buildTrackChunk(singleEvents))
    } else {
      midiFormat = 1
      // Track 0: Conductor / Tempo & Time Signature map
      val tempoEvents = ArrayList<TimedMidiMessage>().apply {
        add(metaTextEvent(0L, 0x03, trackTitle))
        add(tempoMetaEvent(0L, mpqn))
        add(timeSignatureMetaEvent(0L, numerator = pattern.beatsPerBar, denominatorPow2 = 2))
      }
      tracks.add(buildTrackChunk(tempoEvents))

      // Track 1: Lead Melody (Channel 0)
      val leadEvents = ArrayList<TimedMidiMessage>().apply {
        add(metaTextEvent(0L, 0x03, "Lead Melody"))
        add(programChangeEvent(0L, channel = 0, program = leadProgram))
        appendNoteEvents(this, pattern.melodyNotes, channel = 0)
      }
      tracks.add(buildTrackChunk(leadEvents))

      // Track 2: Chord Progression (Channel 1)
      val chordEvents = ArrayList<TimedMidiMessage>().apply {
        add(metaTextEvent(0L, 0x03, "Chord Pad"))
        add(programChangeEvent(0L, channel = 1, program = chordProgram))
        appendNoteEvents(this, pattern.chordNotes, channel = 1, trimTailTicks = 12L)
      }
      tracks.add(buildTrackChunk(chordEvents))

      if (mode == MidiExportMode.FULL_ARRANGEMENT) {
        // Track 3: Bassline (Channel 2)
        val bassEvents = ArrayList<TimedMidiMessage>().apply {
          add(metaTextEvent(0L, 0x03, "Bassline"))
          add(programChangeEvent(0L, channel = 2, program = bassProgram))
          appendNoteEvents(this, pattern.bassNotes, channel = 2)
        }
        tracks.add(buildTrackChunk(bassEvents))

        // Track 4: GM Percussion (Channel 9) if profile uses drums
        if (pattern.drumHits.isNotEmpty()) {
          val drumEvents = ArrayList<TimedMidiMessage>().apply {
            add(metaTextEvent(0L, 0x03, "Drums (GM Ch 10)"))
            appendNoteEvents(this, pattern.drumHits, channel = 9, trimTailTicks = 20L)
          }
          tracks.add(buildTrackChunk(drumEvents))
        }
      }
    }

    val out = ByteArrayOutputStream(2048)
    val dos = DataOutputStream(out)
    // Header chunk MThd (6 bytes)
    dos.writeBytes("MThd")
    dos.writeInt(6)
    dos.writeShort(midiFormat)
    dos.writeShort(tracks.size)
    dos.writeShort(TICKS_PER_QUARTER_NOTE)

    for (chunk in tracks) {
      dos.write(chunk)
    }
    dos.flush()
    return out.toByteArray()
  }

  /**
   * Lightweight SMF validator/inspector for verifying generated MIDI sequences.
   */
  fun parseMidiSummary(bytes: ByteArray): MidiFileSummary {
    require(bytes.size >= 14) { "Invalid MIDI header size." }
    val headerTag = String(bytes, 0, 4, Charsets.US_ASCII)
    require(headerTag == "MThd") { "Missing MThd signature." }

    fun readInt32(offset: Int): Int =
      ((bytes[offset].toInt() and 0xFF) shl 24) or
        ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
        ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
        (bytes[offset + 3].toInt() and 0xFF)

    fun readInt16(offset: Int): Int =
      ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)

    val headerLen = readInt32(4)
    val format = readInt16(8)
    val declaredTracks = readInt16(10)
    val divisionPpq = readInt16(12)

    var pos = 8 + headerLen
    var tempoBpm = 120
    var noteOnCount = 0
    val trackNames = ArrayList<String>()
    var parsedTracks = 0

    while (pos + 8 <= bytes.size && parsedTracks < declaredTracks) {
      val chunkTag = String(bytes, pos, 4, Charsets.US_ASCII)
      val chunkLen = readInt32(pos + 4)
      pos += 8
      val chunkEnd = (pos + chunkLen).coerceAtMost(bytes.size)
      if (chunkTag == "MTrk") {
        parsedTracks++
        var cursor = pos
        var runningStatus = 0
        while (cursor < chunkEnd) {
          // Skip VLQ delta
          while (cursor < chunkEnd) {
            val b = bytes[cursor++].toInt() and 0xFF
            if ((b and 0x80) == 0) break
          }
          if (cursor >= chunkEnd) break

          var status = bytes[cursor].toInt() and 0xFF
          if (status < 0x80) {
            status = runningStatus
          } else {
            cursor++
            if (status < 0xF0) {
              runningStatus = status
            }
          }

          if (status == 0xFF) {
            if (cursor >= chunkEnd) break
            val metaType = bytes[cursor++].toInt() and 0xFF
            var metaLen = 0
            while (cursor < chunkEnd) {
              val b = bytes[cursor++].toInt() and 0xFF
              metaLen = (metaLen shl 7) or (b and 0x7F)
              if ((b and 0x80) == 0) break
            }
            val dataEnd = (cursor + metaLen).coerceAtMost(chunkEnd)
            if (metaType == 0x51 && dataEnd - cursor == 3) {
              val mpqn = ((bytes[cursor].toInt() and 0xFF) shl 16) or
                ((bytes[cursor + 1].toInt() and 0xFF) shl 8) or
                (bytes[cursor + 2].toInt() and 0xFF)
              if (mpqn > 0) {
                tempoBpm = (60_000_000.0 / mpqn).roundToInt()
              }
            } else if (metaType == 0x03 && dataEnd > cursor) {
              trackNames.add(String(bytes, cursor, dataEnd - cursor, Charsets.UTF_8))
            }
            cursor = dataEnd
          } else if (status == 0xF0 || status == 0xF7) {
            var sysLen = 0
            while (cursor < chunkEnd) {
              val b = bytes[cursor++].toInt() and 0xFF
              sysLen = (sysLen shl 7) or (b and 0x7F)
              if ((b and 0x80) == 0) break
            }
            cursor = (cursor + sysLen).coerceAtMost(chunkEnd)
          } else {
            val highNibble = status and 0xF0
            when (highNibble) {
              0xC0, 0xD0 -> cursor = (cursor + 1).coerceAtMost(chunkEnd)
              0x90 -> {
                if (cursor + 1 < chunkEnd) {
                  val vel = bytes[cursor + 1].toInt() and 0xFF
                  if (vel > 0) noteOnCount++
                }
                cursor = (cursor + 2).coerceAtMost(chunkEnd)
              }
              else -> cursor = (cursor + 2).coerceAtMost(chunkEnd)
            }
          }
        }
      }
      pos = chunkEnd
    }

    return MidiFileSummary(
      format = format,
      trackCount = parsedTracks,
      divisionPpq = divisionPpq,
      tempoBpm = tempoBpm,
      noteOnCount = noteOnCount,
      trackNames = trackNames,
    )
  }

  private fun appendNoteEvents(
    target: MutableList<TimedMidiMessage>,
    notes: List<SynthesizedNoteEvent>,
    channel: Int,
    trimTailTicks: Long = 10L,
  ) {
    val safeChannel = channel.coerceIn(0, 15)
    for (note in notes) {
      val midiPitch = note.midiNote.coerceIn(0, 127)
      val velocity = note.velocity.coerceIn(1, 127)
      val startTick = note.startStep.toLong() * TICKS_PER_SIXTEENTH
      val rawDurationTicks = (note.durationSteps.toLong() * TICKS_PER_SIXTEENTH).coerceAtLeast(30L)
      val offTick = startTick + (rawDurationTicks - trimTailTicks).coerceAtLeast(24L)

      target.add(
        TimedMidiMessage(
          tick = startTick,
          sortPriority = 2,
          payload = byteArrayOf(
            (0x90 or safeChannel).toByte(),
            midiPitch.toByte(),
            velocity.toByte(),
          ),
        )
      )
      target.add(
        TimedMidiMessage(
          tick = offTick,
          sortPriority = 1,
          payload = byteArrayOf(
            (0x80 or safeChannel).toByte(),
            midiPitch.toByte(),
            0,
          ),
        )
      )
    }
  }

  private fun metaTextEvent(tick: Long, metaType: Int, text: String): TimedMidiMessage {
    val textBytes = text.toByteArray(Charsets.UTF_8)
    val buf = ByteArrayOutputStream(4 + textBytes.size)
    buf.write(0xFF)
    buf.write(metaType and 0x7F)
    writeVlq(buf, textBytes.size.toLong())
    buf.write(textBytes)
    return TimedMidiMessage(tick = tick, sortPriority = 0, payload = buf.toByteArray())
  }

  private fun tempoMetaEvent(tick: Long, microsecondsPerQuarterNote: Int): TimedMidiMessage {
    return TimedMidiMessage(
      tick = tick,
      sortPriority = 0,
      payload = byteArrayOf(
        0xFF.toByte(),
        0x51,
        0x03,
        ((microsecondsPerQuarterNote shr 16) and 0xFF).toByte(),
        ((microsecondsPerQuarterNote shr 8) and 0xFF).toByte(),
        (microsecondsPerQuarterNote and 0xFF).toByte(),
      ),
    )
  }

  private fun timeSignatureMetaEvent(
    tick: Long,
    numerator: Int,
    denominatorPow2: Int,
  ): TimedMidiMessage {
    return TimedMidiMessage(
      tick = tick,
      sortPriority = 0,
      payload = byteArrayOf(
        0xFF.toByte(),
        0x58,
        0x04,
        numerator.coerceIn(1, 16).toByte(),
        denominatorPow2.coerceIn(0, 4).toByte(),
        24, // 24 MIDI clocks per metronome click
        8,  // 8 notated 32nd-notes per quarter note
      ),
    )
  }

  private fun programChangeEvent(tick: Long, channel: Int, program: Int): TimedMidiMessage {
    return TimedMidiMessage(
      tick = tick,
      sortPriority = 0,
      payload = byteArrayOf(
        (0xC0 or channel.coerceIn(0, 15)).toByte(),
        program.coerceIn(0, 127).toByte(),
      ),
    )
  }

  private fun buildTrackChunk(events: List<TimedMidiMessage>): ByteArray {
    val sorted = events.sortedWith(compareBy<TimedMidiMessage> { it.tick }.thenBy { it.sortPriority })
    val trackBody = ByteArrayOutputStream(maxOf(64, sorted.size * 6))
    var prevTick = 0L

    for (event in sorted) {
      val delta = (event.tick - prevTick).coerceAtLeast(0L)
      prevTick = event.tick
      writeVlq(trackBody, delta)
      trackBody.write(event.payload)
    }

    // End of Track meta event (FF 2F 00)
    writeVlq(trackBody, 0L)
    trackBody.write(0xFF)
    trackBody.write(0x2F)
    trackBody.write(0x00)

    val bodyBytes = trackBody.toByteArray()
    val chunk = ByteArrayOutputStream(8 + bodyBytes.size)
    val dos = DataOutputStream(chunk)
    dos.writeBytes("MTrk")
    dos.writeInt(bodyBytes.size)
    dos.write(bodyBytes)
    dos.flush()
    return chunk.toByteArray()
  }

  private fun writeVlq(out: ByteArrayOutputStream, value: Long) {
    var v = value.coerceAtLeast(0L)
    val stack = IntArray(5)
    var count = 0
    stack[count++] = (v and 0x7FL).toInt()
    v = v ushr 7
    while (v > 0L) {
      stack[count++] = ((v and 0x7FL) or 0x80L).toInt()
      v = v ushr 7
    }
    for (i in count - 1 downTo 0) {
      out.write(stack[i])
    }
  }
}
