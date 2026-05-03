# Tab ML Box

Offline-first Android app for training a **tiny neural network from scratch** (XOR demo) on your device. No `INTERNET` permission in this build — it cannot open network sockets.

Standalone repo (**not** the Candidateflow WhatsApp project). App id: `dev.tabml.box`.

## In the app

- **Train from scratch** — random weights, 8000 epochs on XOR, then saves `xor_checkpoint.json` in private storage.
- **Continue training (from saved weights)** — 2000 more epochs starting from your file (still offline).
- **Load saved weights & test XOR** — runs inference only; shows last saved loss and time if present.
- **Delete saved weights** — removes the checkpoint after confirmation.

## Open in Android Studio

1. **File → Open** and choose this folder: `tab-ml-box`
2. Let Gradle sync; install **JDK 17** and Android SDK if prompted.
3. **Build → Build Bundle(s) / APK(s) → Build APK(s)**  
   Debug APK path is usually: `app/build/outputs/apk/debug/app-debug.apk`

## Install on Galaxy Tab S8 (your steps)

1. On the tablet: **Settings → Security** (or **Developer options**) → allow installing from your source (USB, Files, or “Install unknown apps” for the app you use to open the APK).
2. Copy `app-debug.apk` to the tablet and open it, or plug in USB and run **Run → Run 'app'** from Android Studio with USB debugging enabled.

## Location

This project was moved out of `candidateflow-whatsapp` so the two stay separate. Path: **`/Users/paulevans/tab-ml-box`**.
