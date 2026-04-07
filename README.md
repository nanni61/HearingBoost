# HearingBoost 🎙️

App Android per ipoudenti che amplifica in tempo reale l'audio del microfono,
con equalizzazione centrata sulle frequenze vocali e riduzione del rumore regolabile.

---

## Funzionalità

| Feature | Dettaglio |
|---|---|
| **Guadagno L/R separato** | 0 – 300% per canale (slider indipendenti) |
| **Voice EQ** | Passa-banda biquad: 300 Hz – 3.4 kHz (voce umana) |
| **Noise Gate** | Gate adattivo su RMS + eventuale NoiseSuppressor hardware |
| **Latenza** | Buffer da 1024 frame ~23 ms @ 44100 Hz |
| **Formato audio** | PCM Float 32-bit, 44100 Hz, mono in → stereo out |

---

## Pipeline DSP

```
Microfono (mono, float32, 44100 Hz)
     │
     ▼
┌─────────────────────────────┐
│  Voice EQ (biquad band-pass) │  HP @ 300 Hz  +  LP @ 3400 Hz
└─────────────────────────────┘
     │
     ▼
┌─────────────────────────────┐
│  Noise Gate adattivo         │  Soglia = smoothRMS × ratio × noiseLevel
└─────────────────────────────┘
     │
     ▼
┌─────────────────────────────┐
│  Gain L / Gain R separato    │  0.0× – 3.0× (hard-clip a ±1.0)
└─────────────────────────────┘
     │
     ▼
AudioTrack stereo (cuffie/altoparlante)
```

---

## Requisiti

- Android **8.0 (API 26)** o superiore
- Android Studio **Hedgehog** o più recente
- JDK 17
- Permesso `RECORD_AUDIO`

---

## Build

```bash
# Clona o estrai il progetto, poi:
cd HearingBoost
./gradlew assembleDebug

# APK generato in:
# app/build/outputs/apk/debug/app-debug.apk
```

oppure apri la cartella in **Android Studio → Run** (▶).

---

## Struttura del progetto

```
HearingBoost/
├── app/src/main/
│   ├── java/com/oro/hearingboost/
│   │   ├── AudioProcessor.kt   ← DSP engine (biquad, gate, gain)
│   │   └── MainActivity.kt     ← UI + controlli slider
│   ├── res/
│   │   ├── layout/activity_main.xml
│   │   ├── values/{colors, strings, themes}.xml
│   │   └── drawable/{dot_active, dot_idle, chip_bg, ic_ear}.xml
│   └── AndroidManifest.xml
├── build.gradle
└── settings.gradle
```

---

## Parametri regolabili (costanti in AudioProcessor.kt)

```kotlin
const val VOICE_LOW_HZ  = 300.0    // Freq. taglio HP — abbassa per includere bassi
const val VOICE_HIGH_HZ = 3400.0   // Freq. taglio LP — alza per includere suoni acuti
const val GATE_RATIO    = 0.15f    // Aggressività gate (0.0 = off, 0.5 = forte)
const val BUFFER_FRAMES = 1024     // Latenza: diminuisci per <latenza, >instabilità
const val MAX_GAIN      = 3.0f     // Massimo moltiplicatore (300%)
```

---

## Note tecniche

- Il **NoiseSuppressor hardware** (Android AudioEffect) viene attivato automaticamente
  se disponibile sul dispositivo, in aggiunta al gate software.
- Il guadagno oltre 100% introduce **distorsione armonica** (hard-clip). Per un suono
  più naturale si può sostituire il clip con una soft-saturation (tanh).
- La banda 300–3.4 kHz copre il 90% dell'intelligibilità della voce italiana.
- Per uso con **apparecchi acustici**: verificare che il sistema audio del dispositivo
  non applichi ulteriore elaborazione (es. "Sound alive", Dolby Atmos, ecc.)

---

## Roadmap futura

- [ ] Visualizzatore VU-meter L/R in tempo reale
- [ ] Preset salvabili (es. "TV", "Conversazione", "Telefono")
- [ ] EQ a 5 bande nella gamma vocale
- [ ] Soft-clip (tanh) invece di hard-clip
- [ ] Supporto Bluetooth (routing su cuffie BT)
- [ ] Widget nella status bar per avvio rapido
