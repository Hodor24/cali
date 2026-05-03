# Tab ML Box

Android app for **on-device XOR training** (Kotlin) and **TensorFlow Lite inference**, with **optional HTTPS** downloads you explicitly enable.

Standalone repo (**not** the Candidateflow WhatsApp project). App id: `dev.tabml.box`.

## In the app

- **Train from scratch** — random weights, 8000 epochs on XOR, then saves `xor_checkpoint.json` in private storage.
- **Continue training (from saved weights)** — 2000 more epochs starting from your file (still offline for this path).
- **Load saved weights & test XOR** — inference only; shows last saved loss and time if present.
- **TensorFlow Lite XOR** — runs a `.tflite` model; **bundled** `assets/models/xor_mlp.tflite` or a **downloaded** file you pulled via HTTPS when the network toggle is on.
- **Allow HTTPS downloads** — when **off**, the app does **not** use the network. When **on**, you can download a **`.tflite`** (replaces on-disk inference model) or an **APK** (opens the system installer so you can install a newer build with updated **native** TF Lite `.so` libraries).

Manifest: `INTERNET`, `ACCESS_NETWORK_STATE`, `REQUEST_INSTALL_PACKAGES`. **Cleartext HTTP is disabled** (`usesCleartextTraffic="false"`).

## Updating “libs”

- **Model / graph:** download a new **`https://…/*.tflite`** with *Download .tflite & use for inference*.
- **Native TensorFlow Lite runtime:** ship updated `libtensorflowlite_jni.so` (etc.) inside a **new APK** you build; use *Download APK & open installer* to install that APK from a URL **you trust**.

Regenerate the bundled XOR model: `python3 tools/export_xor_tflite.py` (after `pip install tensorflow`).

## TensorFlow Lite (Gradle)

`org.tensorflow:tensorflow-lite`, `org.tensorflow:tensorflow-lite-support`.

## Open in Android Studio

1. **File → Open** and choose this folder: `tab-ml-box`
2. Create **`local.properties`** with `sdk.dir=…` if needed.
3. **Build → Build APK(s)** — debug output: `app/build/outputs/apk/debug/app-debug.apk`

## Install on device

Allow **USB debugging** or sideload the APK; for APK-from-URL installs, allow **install unknown apps** for Tab ML Box when Android prompts.

## Location

Path: **`/Users/paulevans/tab-ml-box`**.
