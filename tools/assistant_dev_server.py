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
        system_snip = ""
        last_user = ""
        for m in messages:
            role = m.get("role")
            content = (m.get("content") or "").strip()
            if role == "system" and content:
                system_snip = content[:400] + ("…" if len(content) > 400 else "")
            if role == "user":
                last_user = content

        nvq_on = "NVQ ASSESSOR BRIEF" in "\n".join(
            (m.get("content") or "") for m in messages if m.get("role") == "system"
        )

        lines = [
            "[Tab ML Box dev server — stub LLM]",
            f"NVQ assessor brief in system message: {'yes' if nvq_on else 'no'}",
            "",
            "Last user message:",
            last_user[:8000] or "(empty)",
        ]
        if system_snip:
            lines += ["", "System prompt (first 400 chars):", system_snip]

        reply = "\n".join(lines)

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
