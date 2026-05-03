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

### Real LLM on your Mac (Ollama + ngrok)

Cali is already a “real” client: it sends **OpenAI-compatible** chat to **your** server. The Python stub is optional for connectivity tests. To use an actual model:

1. **Install [Ollama](https://ollama.com)** on the Mac and pull a model, e.g.  
   `ollama pull llama3.2`
2. **Leave Ollama running** (menu bar app). It exposes an OpenAI-compatible API on **`http://localhost:11434`** (`POST /v1/chat/completions`).
3. **Tunnel HTTPS to the tablet** (release builds require **https://**):  
   `ngrok http 11434`  
   Copy the **https://…ngrok…** origin (no path).
4. In **Tab ML Box** → Cali → **Your server settings**:
   - **Assistant base URL** = that origin only, e.g. `https://abc123.ngrok-free.app`
   - **Model name** = the Ollama model id, e.g. `llama3.2` (same as in `ollama list`)
   - **Bearer token** = leave empty unless you added auth in front of Ollama
   - On the main screen: **Allow HTTPS downloads** = **on**
5. Turn **off** “Run LLM on this tablet” if you only want the remote Ollama path.

Other stacks work the same way if they implement **`POST {baseUrl}/v1/chat/completions`** (vLLM, LiteLLM, OpenAI, etc.). **Debug** APKs also allow `http://` base URLs (e.g. `http://10.0.0.5:11434`) if the tablet can reach the Mac on LAN—**release** builds do not.

If you see **HTTP 403** with an **ngrok** URL, the app already sends `ngrok-skip-browser-warning` and browser-like headers for ngrok hosts. A **403 with an empty body** often means the **public ngrok URL is stale** (old tunnel), **ngrok is not running**, or **ngrok’s edge** rejected the request before it reached your Mac — not an Ollama error. Checklist:

1. On the Mac, `curl -sS http://127.0.0.1:11434/v1/tags` (or `/v1/models`) should return JSON while Ollama is running.
2. Run **`ngrok http 11434`** (or `127.0.0.1:11434`) and set the app’s **Assistant base URL** to the **current** `https://…ngrok…` origin from that session (each new ngrok run can change the hostname on free tier).
3. On the tablet, open that **same https origin** once in **Chrome**, tap **Visit site** if an interstitial appears, then try Cali again.
4. On the Mac, open [http://127.0.0.1:4040](http://127.0.0.1:4040): if requests from the tablet **never appear**, the tunnel or URL is wrong; if they appear with 403, inspect response headers.

Alternative tunnel (no ngrok): **`cloudflared tunnel --url http://127.0.0.1:11434`** — use the printed `https://…trycloudflare.com` origin as the base URL. **Debug** APKs can also use **`http://<laptop-LAN-ip>:11434`** if the tablet can reach the Mac on Wi‑Fi.

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
