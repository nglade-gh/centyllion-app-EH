#!/usr/bin/env python3
"""Local save bridge: lets the Centillyon web app open the native OS "save as"
dialog in any browser (Firefox included), where the File System Access API is
not available (it is a Chromium-only API, and also requires a secure context).

Usage:
    python3 save_bridge.py [port]        (default port: 8090)

How it works
------------
The web app's "Save As" falls back to this bridge when the native browser
dialog is unavailable: it POSTs the file content to
    http://127.0.0.1:<port>/save?name=<suggested file name>
This process then opens a native file dialog (tkinter), and answers with JSON:
    {"saved": "<absolute path>"}   when the user saved the file
    {"saved": null}                when the user cancelled the dialog

The bridge must run on the machine where the browser runs, and the app must
be loaded over plain HTTP (an https page cannot fetch http://127.0.0.1 —
mixed-content blocking).

Headless use
------------
Set SAVE_BRIDGE_AUTO=<directory> to skip the dialog and write the file
directly into that directory (useful for servers / tests).

Security
--------
The server binds to 127.0.0.1 only: it is reachable solely from processes
running on this machine, and every save is confirmed by the dialog
(or by the explicit SAVE_BRIDGE_AUTO directory).

Requires: Python 3 with tkinter (standard on Windows and macOS;
`sudo apt install python3-tk` on Debian/Ubuntu).
"""
import json
import os
import sys
import urllib.parse
from http.server import BaseHTTPRequestHandler, HTTPServer

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8090
AUTO_DIR = os.environ.get("SAVE_BRIDGE_AUTO", "")


def pick_save_path(suggested: str):
    """Return the path the user chose (None when cancelled).

    In SAVE_BRIDGE_AUTO mode the dialog is skipped and the file is written
    directly into the auto directory.
    """
    if AUTO_DIR:
        os.makedirs(AUTO_DIR, exist_ok=True)
        base = os.path.basename(suggested) or "model.netbiodyn"
        return os.path.join(AUTO_DIR, base)

    # tkinter must run on the main thread (Windows in particular): the server
    # below is single-threaded and serves requests from the main loop.
    import tkinter as tk
    from tkinter import filedialog

    root = tk.Tk()
    root.withdraw()
    root.attributes("-topmost", True)
    path = filedialog.asksaveasfilename(
        title="Save file",
        defaultextension=".netbiodyn",
        initialfile=suggested,
        filetypes=[("NetBioDyn model", "*.netbiodyn"), ("All files", "*.*")],
    )
    root.destroy()
    return path or None


class Handler(BaseHTTPRequestHandler):
    def _send(self, code: int, payload: dict):
        body = json.dumps(payload).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        # The web page is served from another origin (http://<host>:<port>):
        # allow the cross-origin fetch.
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        # health check: the web app can probe this endpoint
        self._send(200, {"ok": True, "name": "centyllion-save-bridge"})

    def do_POST(self):
        parsed = urllib.parse.urlparse(self.path)
        if parsed.path != "/save":
            self._send(404, {"saved": None, "error": "not found"})
            return
        name = urllib.parse.parse_qs(parsed.query).get("name", ["model.netbiodyn"])[0]
        length = int(self.headers.get("Content-Length", 0))
        content = self.rfile.read(length)
        try:
            path = pick_save_path(name)
        except Exception as e:  # e.g. tkinter missing or no display
            self._send(200, {"saved": None, "error": str(e)})
            return
        if path is None:
            self._send(200, {"saved": None})  # user cancelled the dialog
            return
        try:
            with open(path, "wb") as f:
                f.write(content)
        except OSError as e:
            self._send(500, {"saved": None, "error": str(e)})
            return
        self._send(200, {"saved": path})

    def log_message(self, fmt, *args):
        sys.stderr.write("[save-bridge] " + (fmt % args) + "\n")


def main():
    server = HTTPServer(("127.0.0.1", PORT), Handler)
    # Poll so the (blocking) file dialog always runs on the main thread.
    server.socket.settimeout(0.5)
    mode = f"auto-save into {AUTO_DIR}" if AUTO_DIR else "native dialog"
    print(f"Centillyon save bridge on http://127.0.0.1:{PORT} — {mode} (Ctrl+C to stop)")
    try:
        while True:
            server.handle_request()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
