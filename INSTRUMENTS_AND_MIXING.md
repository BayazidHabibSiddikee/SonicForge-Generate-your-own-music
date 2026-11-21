# SonicForge — Instruments, Sound Resources & Mixing Guide (`INSTRUMENTS_AND_MIXING.md`)

## 1. Studio Navigation Overview
Use the bottom navigation bar (or left rail on wide screens) to move freely between the four studio workspaces:

1. **Studio** — Central DAW workspace: Play tracks, inspect live FFT spectrum & BPM, tweak real-time FX (EQ, Convolution Reverb, Pitch, Loudness, Metronome), **Merge Multiple Tracks**, browse **Sound Resources**, manage **Library/Playlists**, save **Room Projects**, and export **MP3**.
2. **Piano** — Playable keyboard, Piano voices, chord progression & phrase builder, live note recording, and WAV stem generation.
3. **Guitar** — Playable 6-string fretboard, Guitar voices (Karplus-Strong physical modeling), chord fingerings, strumming/fingerstyle builder, and WAV stem generation.
4. **Drums** — Live 8-pad velocity drum kit, 4 selectable Kit resources, and 16-step rhythmic sequencer with BPM sync & WAV stem generation.

---

## 2. Built-In Sound Resources

### Studio Sound Resources (`StudioSoundResource`)
Configurable one-tap real-time studio loops & stems ready to load, layer, or export:
- **Rhodes Lo-Fi Chords** (84 BPM · Warm electric keys + dusty pocket)
- **Warm Acoustic Groove** (96 BPM · Guitar-ready chord bed + soft pulse)
- **Neon Synthwave Arp** (112 BPM · Analog 16th arpeggio + sub bass)
- **808 Sub & Crisp Hats** (132 BPM · Deep sub foundation + rhythmic hats)
- **Ambient Cathedral Pad** (72 BPM · Sustained harmonic wash for reverb)
- **Cinematic Grand Theme** (94 BPM · Expressive minor piano + warm strings)

### Instrument Pages
- **Piano Voices:** `Concert Grand`, `Bright Upright`, `Rhodes EP`, `Vintage Wurli`, `Analog Keys`
- **Guitar Voices:** `Acoustic Steel`, `Classical Nylon`, `Clean Electric`, `Overdrive Rock`, `Jazz Hollowbody`
- **Drum Kits:** `Studio Acoustic`, `TR-808 Analog`, `Lo-Fi Vinyl`, `Cyber Electro`

---

## 3. Merging Piano + Guitar + Drums & Saving as MP3

1. Generate your **Piano** stem from the **Piano** tab (choose a voice & chord progression or record your own notes → tap **Generate Piano Track**).
2. Generate your **Guitar** stem from the **Guitar** tab (pick a voice & chord progression or pluck strings live → tap **Generate Guitar Track**).
3. Generate your **Drum** stem from the **Drums** tab (select a Drum Kit, program steps or choose a preset → tap **Generate Drum Track**).
4. Switch to the **Studio** tab and open **Merge & Mixdown Studio**:
   - Check the Piano, Guitar, and Drum tracks you want to combine.
   - Balance each stem's **Gain fader** (`0%–150%`).
   - Tap **Merge All to Master** to save the combined mix into your Room library, or tap **Merge & Save MP3** to mix down and write an MP3 file directly to `Music/SonicForge/`.
5. Any track in the Studio (solo instrument, resource loop, or merged master) also has a direct **Save MP3** button in the player and library rows.
