# SonicForge — Real-Time DSP & Synthesis Engine (`AUDIO_ENGINE.md`)

## 1. Deterministic On-Device Sound Synthesis
SonicForge does **not** rely on remote generative ML weights for instrument creation; every sound is synthesized mathematically in 16-bit PCM (`44,100 Hz`) using physical modeling, additive synthesis, FM synthesis, and filtered noise bursts. This guarantees **instant, offline, zero-cost, deterministic sound creation** with real-time tactile preview.

---

## 2. Instrument Synthesis Algorithms

### A. Piano Synthesizer (`PianoVoice`)
Each piano note frequency $f = 440 \cdot 2^{(m - 69)/12}$ is rendered using harmonic additive/FM partials with strike-velocity brightness scaling and exponential string decay:
- **Concert Grand:** 6 harmonic partials with slight inharmonicity stretching ($B = 0.00018 \cdot n^2$), wooden hammer transient burst (first 12 ms), and dual-stage decay envelope.
- **Bright Upright:** Boosted 2nd–5th partials + crisp attack transient.
- **Rhodes Electric Piano:** Fundamental sine body + FM modulator tine ping ($\text{modRatio} = 7.0$, rapid tine decay) + subtle 4.5 Hz stereo/amplitude tremolo.
- **Vintage Wurli:** Asymmetric reed harmonics (odd-order warmth) + soft bell attack.
- **Analog Synth Keys:** Detuned dual oscillator with time-varying low-pass brightness envelope.

### B. Guitar Physical Modeling (`GuitarVoice` — Karplus-Strong)
Plucked guitar strings use the **Karplus-Strong delay-line algorithm**:
1. Initialize a delay ring buffer of length $N = \lfloor f_s / f_0 \rfloor$ with pluck-position-filtered excitation noise (simulating pick/finger position along the string).
2. On each sample step, advance the circular delay line and apply a first-order low-pass damping filter:
   $$y[n] = \rho \cdot \big((1 - \alpha)\, y[n-N] + \alpha\, y[n-N-1]\big)$$
   where $\rho \in [0.991, 0.9985]$ controls sustain and $\alpha$ controls string brightness/nylon damping.
3. Pass output through a woody **body resonance resonator** (for Acoustic Steel / Classical Nylon / Jazz Hollowbody) or **soft-clipping tube overdrive stage** (`tanh(drive * x)`) for Overdrive Rock.

### C. Drum & Rhythm Synthesis (`DrumKitPreset` & `StepSequencerDsp`)
Synthesizes 8 distinct percussive voices tuned dynamically by the active kit resource (*Studio Acoustic*, *TR-808 Analog*, *Lo-Fi Vinyl*, *Cyber Electro*):
- **Kick:** Exponential pitch-swept sine ($165 \rightarrow 44\text{ Hz}$) + click transient + saturation.
- **Snare:** Dual-mode pitched body ($185\text{ Hz}$) + bandpass-shaped white noise snap.
- **Closed / Open Hi-Hat:** Metallic multi-square harmonic cluster + high-pass filtered shimmer noise with tight (`45 ms`) or open (`260 ms`) decay.
- **Clap:** Multi-tap staggered noise bursts (`0, 11, 22, 34 ms`) + diffused tail.
- **Low Tom / Rimshot / Crash:** Tuned membrane sweep, short woody click resonance, and long metallic cymbal shimmer.

---

## 3. Multi-Track Merge Engine (`MultiTrackMixerDsp`)
Merges arbitrary combinations of generated Piano, Guitar, Drum, imported, and studio tracks:
1. **Decode:** Reads each stem WAV/audio file into `RawAudioBuffer`.
2. **Sample-Rate & Channel Harmonization:** Resamples mismatched rates via linear interpolation to `44,100 Hz` and balances mono/stereo channels cleanly.
3. **Duration Alignment:** Determines target length from the longest selected stem. Shorter loops can optionally **auto-loop (`loopToLongest = true`)** with short crossfade edges so a 4-bar drum loop seamlessly backs an 8-bar piano/guitar arrangement.
4. **Gain Mixing & Headroom Limiting:** Scales each stem by its user-configured mixer fader (`0.0x–1.5x`), sums into a `DoubleArray` accumulator, and passes through soft-knee `tanh` limiting + optional **ITU-R BS.1770 LUFS Normalization (`-14 LUFS`)**.

---

## 4. Real-Time Player DSP Chain & MP3 Export
Every loaded or merged track streams through `AudioPlayerController`'s chunked `AudioTrack` loop (`512` frames/chunk):
```text
PCM Buffer ─▶ Pitch Shifter ─▶ 8-Band EQ ─▶ UPOLS Convolution Reverb ─▶ Envelope ─▶ LUFS Trim ─▶ AudioTrack
                                                                               └─▶ Spectrum Analyzer & BPM Detector
```
Final masters export directly to **MP3 (128 / 192 / 256 / 320 kbps)** via **Jump3r LAME**, or **WAV (16/24-bit)**, **FLAC**, and **SMF MIDI**, written safely via Android MediaStore (`Music/SonicForge/`) or SAF/Share Sheet with zero broad storage permissions.
