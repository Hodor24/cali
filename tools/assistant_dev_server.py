#!/usr/bin/env python3
"""
Minimal OpenAI-style chat endpoint for testing Tab ML Box on a real device.

  POST /v1/chat/completions
  Body: {"model":"...", "messages":[{"role":"system|user|assistant","content":"..."}]}
  Response: {"choices":[{"message":{"role":"assistant","content":"..."}}]}

The Android app only accepts https:// for the base URL. For local dev, run this
server (HTTP on localhost), then terminate TLS with a public HTTPS URL, e.g.:

  brew install cloudflare/cloudflare/cloudflared   # once
  cloudflared tunnel --url http://127.0.0.1:8765

Paste ONLY the origin into the app as "HTTPS base URL" (no path), e.g.
https://abcd-12-34-56-78.ngrok-free.app
Leave bearer token blank unless you add auth on this server.

Expose this HTTP port with a trusted HTTPS URL (pick one):
  ngrok http 8765
  cloudflared tunnel --url http://127.0.0.1:8765
"""
from __future__ import annotations

import json
import sys
from http.server import BaseHTTPRequestHandler, HTTPServer

PORT = 8765


def _stub_assistant_reply(last_user: str, nvq_on: bool) -> str:
    """Short, human-sounding reply that makes the dev/stub role obvious."""
    if not last_user.strip():
        return (
            "Hi—I'm the little test server on your laptop, not a full language model. "
            "Send anything you like and I'll confirm the app reached your machine. "
            "When you're ready for real answers, point the same HTTPS URL at Ollama, vLLM, "
            "LiteLLM, or any OpenAI-compatible `/v1/chat/completions` service."
        )
    preview = last_user.strip().replace("\n", " ")
    if len(preview) > 320:
        preview = preview[:317] + "…"
    assess_note = (
        "\n\n(Assessor mode looks on in the app—your real LLM should handle judgement and AO detail.)"
        if nvq_on
        else ""
    )
    return (
        f"Thanks—that came through. Here's what I heard: “{preview}”\n\n"
        "I'm only the **Tab ML Box dev stub** on this Mac, so I won't try to invent a full answer. "
        "Use this setup to prove https → ngrok → your laptop works; then swap the upstream for a real model."
        f"{assess_note}"
    )


class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt: str, *args: object) -> None:
        sys.stderr.write("[%s] %s\n" % (self.log_date_time_string(), fmt % args))

    def do_POST(self) -> None:
        path = self.path.split("?", 1)[0].rstrip("/") or "/"
        if path != "/v1/chat/completions":
            self.send_error(404, "use POST /v1/chat/completions")
            return
        try:
            n = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            n = 0
        raw = self.rfile.read(n)
        try:
            data = json.loads(raw.decode("utf-8"))
        except json.JSONDecodeError as e:
            self.send_response(400)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"error": str(e)}).encode("utf-8"))
            return

        messages = data.get("messages") or []
        last_user = ""
        for m in messages:
            if m.get("role") == "user":
                last_user = (m.get("content") or "").strip()

        system_blob = "\n".join(
            (m.get("content") or "") for m in messages if m.get("role") == "system"
        )
        nvq_on = "NVQ ASSESSOR BRIEF" in system_blob

        reply = _stub_assistant_reply(last_user, nvq_on)

        out = {
            "choices": [
                {
                    "message": {
                        "role": "assistant",
                        "content": reply,
                    },
                },
            ],
        }
        body = json.dumps(out).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)


def main() -> None:
    # Bind all IPv4 interfaces so tunnels (ngrok/cloudflared) reliably reach the process.
    # 127.0.0.1-only can fail if a tool resolves "localhost" differently (e.g. ::1).
    host = "0.0.0.0"
    print("HTTP dev server: http://127.0.0.1:%d (LAN: http://%s:%d)" % (PORT, host, PORT), file=sys.stderr)
    print("", file=sys.stderr)
    print("Tablet needs HTTPS. In another terminal run one of:", file=sys.stderr)
    print("  ngrok http %d" % PORT, file=sys.stderr)
    print("  cloudflared tunnel --url http://127.0.0.1:%d" % PORT, file=sys.stderr)
    HTTPServer((host, PORT), Handler).serve_forever()


if __name__ == "__main__":
    main()
