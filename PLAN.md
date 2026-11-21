# SonicForge Studio — System Plan & Roadmap

## 1. Product Vision & Shift to Real-Time On-Device Studio
SonicForge is a **minimized, real-time on-device digital audio workstation (DAW)** for Android built with **Kotlin**, **Jetpack Compose (Material 3)**, **Room**, and a pure-Kotlin/Java **16-bit PCM DSP engine** with **Jump3r LAME MP3** encoding.

Rather than relying on external cloud music-generation models, every sound in SonicForge is **synthesized deterministically on-device in real time** from acoustic physical models, harmonic additive/FM oscillators, drum voices, and configurable **Sound Resources**—then mixed, processed through the live DSP rack, merged across tracks, and saved directly as **MP3** (plus WAV, FLAC, and MIDI).

---

## 2. Multi-Page Studio Layout (Rearranged & Minimized)

To eliminate single-screen clutter and behave like a focused hardware/software studio, SonicForge is organized into **4 dedicated pages** connected via a Material 3 `NavigationBar` (phones) or `NavigationRail` (tablets/foldables):

| Page | Purpose | Key Capabilities |
| :--- | :--- | :--- |
| **1. Studio** | Main Hub, Player, Stem Merger & MP3 Export | Active Track Player, Live FFT Spectrum & BPM, Collapsible FX Rack (8-Band EQ, Convolution Reverb, Pitch Shift, LUFS Normalization, Crossfade, Metronome), **Multi-Track Merger** ("Merge All"), **Sound Resources** quick-synth shelf, Library & Room Projects tabs, and **One-Tap Save as MP3** |
| **2. Piano** | Polyphonic Keyboard Synthesizer | Interactive 2-octave playable keyboard (white + black keys), 5 selectable Piano Sound Resources (Concert Grand, Bright Upright, Rhodes EP, Vintage Wurli, Analog Keys), Octave `-2..+2`, Live Note Recorder, Preset Chord Progressions, Live Preview & **Generate Piano Track** |
| **3. Guitar** | Plucked-String Fretboard & Strum Studio | Interactive 6-string × 7-fret fretboard (`E2–E4`), Karplus-Strong physical string synthesis, 5 Guitar Sound Resources (Acoustic Steel, Nylon Classical, Clean Electric, Overdrive Rock, Jazz Hollowbody), Chord Pad shapes, Strum/Fingerstyle Patterns, Live Fret Recorder, Preview & **Generate Guitar Track** |
| **4. Drums** | Drum Pad & 16-Step Sequencer Studio | 8 Live Velocity Drum Pads (Kick, Snare, Closed Hat, Open Hat, Clap, Low Tom, Rimshot, Crash), 4 Drum Kit Sound Resources (Studio Acoustic, TR-808 Analog, Lo-Fi Vinyl, Cyber Electro), 6×16 Step Sequencer Grid, BPM Sync & Swing, and **Generate Drum Track** |

---

## 3. Core Workflow: Generate All → Merge All → Save MP3

1. **Configure & Generate Instrument Stems**
   - Open **Piano**, choose a Piano voice & chord/melody or record live notes, and tap **Generate Piano Track**.
   - Open **Guitar**, choose Acoustic/Electric/Nylon, select chords or record fretboard plucks, and tap **Generate Guitar Track**.
   - Open **Drums**, pick a Drum Kit resource, tap live pads or program the 16-step grid, and tap **Generate Drum Track**.
   - Optionally trigger built-in **Studio Sound Resources** (Basslines, Pads, Arps, Lo-Fi Chords) directly from the Studio page.
2. **Merge All Stems in the Studio**
   - In the **Studio** page's **Multi-Track Merger**, select the generated Piano, Guitar, Drum, and/or Studio tracks.
   - Adjust individual stem gain sliders (`0%–150%`), toggle loop-to-match duration and `-14 LUFS` Auto-Leveling.
   - Tap **Merge All** to render a unified master track into the Studio Library, or **Merge & Save MP3** to mix down and export directly to `Music/SonicForge/`.
3. **Shape & Save Studio Work**
   - Apply real-time **8-Band Graphic EQ**, **UPOLS FFT Convolution Reverb** (Room Scale & Surface Dampening), **Phase-Vocoder Pitch Shift**, and **Volume Envelope**.
   - Save complete studio sessions (clips, effects, sequencer pattern, metronome) into the **Room Database** (`StudioProjectsCard`) and reload anytime.

---

## 4. Implementation Checklist
- [x] Real-time DSP playback engine (`AudioPlayerController` + `AudioTrack` PCM streaming)
- [x] 8-Band Graphic EQ, Pitch Shifter, LUFS Gain Normalizer, Crossfade, Real-Time FFT Spectrum & BPM Detector
- [x] Configurable UPOLS FFT Convolution Reverb (Room Scale & Dampening)
- [x] Multi-Format Exporter (MP3 via Jump3r LAME, WAV, FLAC, SMF MIDI)
- [x] Room Database v6 Persistence (`GeneratedTrack`, `Playlist`, `StudioProject`)
- [x] Dedicated **Piano**, **Guitar**, and **Drums** synthesis engines + Live Polyphonic `AudioTrack` preview
- [x] Expanded **On-Device Sound Resources** across Studio, Piano, Guitar, and Drums
- [x] **Multi-Track Merger (`MultiTrackMixerDsp`)** with stem gain control, resampling, LUFS leveling, and MP3 export
- [x] Minimized **4-Page Navigation Architecture** (`Studio`, `Piano`, `Guitar`, `Drums`)
- [x] Robolectric unit & Compose UI test coverage across all synthesis, merge, export, persistence, and navigation flows
