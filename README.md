# Tab ML Box

Android app for **on-device XOR training** (Kotlin) and **TensorFlow Lite inference**, with **optional HTTPS** downloads you explicitly enable.

Standalone repo (**not** the Candidateflow WhatsApp project). App id: `dev.tabml.box`.

## In the app

- **Open assistant chat** — prominent button under the title bar; HTTPS assistant that talks only to **your** configured server.
- **Train from scratch** — random weights, 8000 epochs on XOR, then saves `xor_checkpoint.json` in private storage.
- **Continue training (from saved weights)** — 2000 more epochs starting from your file (still offline for this path).
- **Load saved weights & test XOR** — inference only; shows last saved loss and time if present.
- **TensorFlow Lite XOR** — runs a `.tflite` model; **bundled** `assets/models/xor_mlp.tflite` or a **downloaded** file via HTTPS when the network toggle is on.
- **Allow HTTPS downloads** — when **off**, the app does **not** use the network. When **on**, downloads, assistant chat, and APK installs from URLs you enter are allowed.

Manifest: `INTERNET`, `ACCESS_NETWORK_STATE`, `REQUEST_INSTALL_PACKAGES`. **Cleartext HTTP is disabled** (`usesCleartextTraffic="false"`).

## Assistant chat

Uses **your** HTTPS base URL (no default third-party host). The app sends `POST /v1/chat/completions` JSON to that origin. **Bearer token** is optional (encrypted when stored). Messages are not sent anywhere except the URL you save under **Your server settings**.

The assistant can append a `###ACTION` block with `download_tflite` only for real `https://` model URLs. Gradle lines are suggestions to paste in Android Studio, not auto-installed.

Dependencies: **OkHttp**, **androidx.security:security-crypto** for the optional bearer token.

## Updating artifacts

- **Model / graph:** download **`https://…/*.tflite`** or use **Install suggested .tflite** when your assistant reply includes one.
- **Native TensorFlow Lite:** ship updated JNI in a **new APK** you build; use **Download APK** to install from a URL you trust.

Regenerate the bundled XOR model: `python3 tools/export_xor_tflite.py` (with `pip install tensorflow` on a dev machine).

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
