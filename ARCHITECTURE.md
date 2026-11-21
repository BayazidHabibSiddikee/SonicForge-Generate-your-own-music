# SonicForge — System Architecture (`ARCHITECTURE.md`)

## 1. Architectural Pattern
SonicForge follows **MVVM + Clean Repository Layer + Dedicated DSP Modules**:

```text
+-------------------------------------------------------------------+
|                         UI Layer (Jetpack Compose)                |
|  MainActivity -> SonicForgeScreen (NavigationBar / NavigationRail)|
|    ├── Studio Page (Player, Merger, Resources, Library, Projects) |
|    ├── Piano Page  (2-Octave Keyboard, Voice Bank, Chord Synth)   |
|    ├── Guitar Page (6x7 Fretboard, Chord Pads, Strum Synth)       |
|    └── Drums Page  (8 Drum Pads, Kit Resources, 16-Step Grid)     |
+-------------------------------------------------------------------+
                                 │ StateFlow<SonicForgeUiState>
                                 ▼
+-------------------------------------------------------------------+
|                      ViewModel (SonicForgeViewModel)              |
|  - Active navigation & studio section tabs                        |
|  - Piano / Guitar / Drum / Sound Resource synthesis orchestration |
|  - Multi-Track Merger state (stem selection, gains, mixdown)      |
|  - Playback, FX, Sequencer, Metronome, Room Project actions       |
+-------------------------------------------------------------------+
           │                               │
           ▼                               ▼
+---------------------------+   +-----------------------------------+
| Live Audio & DSP Engines  |   |         Data & Persistence        |
| - AudioPlayerController   |   | - GeneratedTrackRepository        |
| - AudioEngine (AudioTrack)|   | - StudioProjectRepository         |
| - LiveInstrumentAudioEng  |   | - ThemePreferencesRepository      |
| - InstrumentSynthDsp      |   | - SonicForgeDatabase (Room v6)    |
| - MultiTrackMixerDsp      |   |   ├── generated_tracks            |
| - StepSequencerEngine     |   |   ├── playlists / cross_refs      |
| - MetronomeEngine         |   |   └── studio_projects / clips     |
| - Mp3AudioExporter        |   |                                   |
+---------------------------+   +-----------------------------------+
```

---

## 2. Package Organization (`com.example`)

### `model/`
- `GeneratedTrack`: Metadata, file path, BPM, key, category, tags, waveform peaks, volume envelope.
- `Playlist`: Ordered collection of tracks linked via Room cross-reference.
- `StudioProject`: Full DAW session snapshot + `StudioProjectCodec` JSON serialization for FX chains, sequencer grid, and metronome config.

### `data/` & `data/local/`
- `SonicForgeDatabase` (Room v6 with explicit migrations `MIGRATION_1_2` through `MIGRATION_5_6`).
- `GeneratedTrackDao`, `PlaylistDao`, `StudioProjectDao`.
- `GeneratedTrackRepository`: Track CRUD, on-device synthesis persistence (Piano, Guitar, Drums, Sound Resources), Multi-Track Merge master rendering, LUFS normalization, trimming, and Jump3r MP3 / WAV / FLAC / MIDI export.
- `StudioProjectRepository`: Atomic transactional save/load/delete of complete studio sessions.
- `ThemePreferencesRepository`: Jetpack DataStore preferences (Theme mode, LUFS target, auto-gain toggle, bottom waveform visibility).

### `audio/`
- **Playback & Real-Time FX Chain (`AudioPlayerController`):**
  1. `PitchShiftDsp` (`RealTimePitchShifter`)
  2. `GraphicEqualizerDsp` (`RealTimeGraphicEqualizer` — 8-band peaking/shelf biquads)
  3. `RoomReverbDsp` / `ConvolutionReverbDsp` (`RealTimeConvolutionReverb` — 1024-pt UPOLS partitioned FFT convolution)
  4. `VolumeEnvelopeDsp` (piecewise gain curve + mute regions)
  5. `LoudnessNormalizerDsp` (ITU-R BS.1770 LUFS target trim)
  6. `CrossfadeDsp` (equal-power dual-deck transitions)
  7. `RealTimeBpmDetector` & `RealTimeSpectrumAnalyzer` (streaming onset & 20-band FFT telemetry)
- **Dedicated Instrument & Clip Playback (`AudioEngine` & DSP):**
  - `AudioEngine`: Focused Android `AudioTrack` (`MODE_STREAM`, 16-bit PCM) playback engine for generated sound clips, files, and raw buffers with transport controls (`playTrack`, `playFile`, `playBuffer`, `pause`, `resume`, `seekTo`, `stop`, `setVolume`, `setLooping`) and reactive `StateFlow<AudioEngineState>`.
  - `InstrumentSynthDsp`: Real-time Piano (additive/FM + ADSR), Guitar (Karplus-Strong plucked string + body filter + overdrive), Drum Pad voices, and `StudioSoundResource` presets.
  - `LiveInstrumentAudioEngine`: Low-latency preview wrapper backed by `AudioEngine` for interactive playing on Piano, Guitar, and Drum pages.
  - `StepSequencerDsp`: 6-lane × 16-step real-time drum & synth sequencer with selectable `DrumKitPreset`, BPM sync, and swing.
  - `MultiTrackMixerDsp`: Sample-rate harmonizing, channel upmixing, loop/duration alignment, per-stem gain scaling, soft-clipping headroom limiting, and LUFS normalization.
  - `Mp3AudioExporter`: Jump3r MPEG-1 Layer III (128–320 kbps), RIFF WAV, FLAC, and SMF MIDI export to MediaStore `Music/SonicForge/`, SAF, or Share sheet.

### `ui/` & `ui/components/`
- `SonicForgeScreen`: Adaptive shell (`NavigationBar` on compact/medium, `NavigationRail` on expanded `>= 840.dp`), `BackHandler` support from instrument pages back to Studio, top studio bar, and modal dialogs.
- `CustomAudioPlayer`: Compact transport, live FFT spectrum, interactive waveform/envelope, and collapsible DSP FX Rack.
- `MultiTrackMergerCard`: Multi-stem mixer allowing users to combine Piano + Guitar + Drum tracks and export MP3 in one step.
- `PianoStudioPage`, `GuitarStudioPage`, `DrumStudioPage`: Dedicated touch-playable instrument pages with resource banks, live recording, preview, and WAV/Library generation.
- `StudioProjectManager`: Room project save/load/delete card and dialog.

---

## 3. Navigation & State Flow
- Navigation state is managed cleanly via `StudioDestination` (`STUDIO`, `PIANO`, `GUITAR`, `DRUMS`) in `SonicForgeViewModel` with `BackHandler` on non-root destinations so system Back always returns cleanly to `STUDIO`.
- Audio rendering and file I/O execute strictly on `Dispatchers.Default` / `Dispatchers.IO`, publishing immutable state updates via `StateFlow` collected with `collectAsStateWithLifecycle()`.
- Minimum 48.dp touch targets (`minimumInteractiveComponentSize()`), edge-to-edge `WindowInsets.safeDrawing` / `WindowInsets.navigationBars`, and Material 3 semantic color tokens are enforced across all pages.
