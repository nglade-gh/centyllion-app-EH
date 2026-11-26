#!/usr/bin/env python3
"""Sert NetBioDyn et donne à la galerie une maison dans le répertoire du logiciel.

Un navigateur ne peut pas écrire dans un dossier servi : la galerie ne peut donc
pas vivre dans Gallery/ sans une petite aide côté serveur. Ce script remplace
`python3 -m http.server` et ajoute trois routes :

  GET  /api/gallery   -> contenu de Gallery/local-gallery.json ({"models": [...]})
  POST /api/gallery   -> écrit Gallery/local-gallery.json (JSON validé avant écriture)
  POST /api/model     -> écrit le modèle envoyé dans Gallery/local/<id>.nbd2
                         et renvoie {"path": "Gallery/local/<id>.nbd2"}

Le fichier distribué Gallery/gallery.json n'est jamais modifié : les ajouts de
l'utilisateur vont dans des fichiers « local » qu'il peut sauvegarder, copier ou
supprimer sans toucher à la distribution.

Usage :
    python3 serve.py                # port 8081, dossier web/webroot
    python3 serve.py --port 9000
    python3 serve.py --webroot web/webroot --gallery Gallery
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import uuid
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

MAX_MODEL_BYTES = 8 * 1024 * 1024
SAFE_ID = re.compile(r"^[A-Za-z0-9._-]{1,64}$")


class GalleryHandler(SimpleHTTPRequestHandler):
    gallery_dir: Path
    server_version = "NetBioDynGallery/1.0"

    # --- helpers ---------------------------------------------------------

    # The bundle has no content hash (`index.html` loads `/js/centyllion/centyllion.js`),
    # so a browser may keep it for hours under its heuristic freshness rules: in
    # development every served file must be revalidated on each load.
    def end_headers(self):
        self.send_header("Cache-Control", "no-store, must-revalidate")
        super().end_headers()

    def local_db(self) -> Path:
        return self.gallery_dir / "local-gallery.json"

    def local_models(self) -> Path:
        directory = self.gallery_dir / "local"
        directory.mkdir(parents=True, exist_ok=True)
        return directory

    def reply_json(self, status: int, payload: object) -> None:
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def read_body(self, limit: int) -> bytes:
        length = int(self.headers.get("Content-Length") or 0)
        if length <= 0:
            raise ValueError("corps vide")
        if length > limit:
            raise ValueError(f"corps trop volumineux ({length} octets)")
        return self.rfile.read(length)

    # --- routes ----------------------------------------------------------

    def do_GET(self) -> None:  # noqa: N802 (nom imposé par http.server)
        if self.path.split("?")[0] == "/api/gallery":
            db = self.local_db()
            if not db.is_file():
                self.reply_json(200, {"models": []})
                return
            try:
                payload = json.loads(db.read_text(encoding="utf-8"))
            except json.JSONDecodeError as error:
                self.reply_json(500, {"error": f"local-gallery.json illisible : {error}"})
                return
            self.reply_json(200, payload)
            return
        super().do_GET()

    def do_POST(self) -> None:  # noqa: N802
        route = self.path.split("?")[0]
        try:
            if route == "/api/gallery":
                raw = self.read_body(4 * 1024 * 1024)
                payload = json.loads(raw.decode("utf-8"))  # valide avant d'écrire
                if not isinstance(payload, dict) or not isinstance(payload.get("models"), list):
                    self.reply_json(400, {"error": "format attendu : {\"models\": [...]}"})
                    return
                target = self.local_db()
                temporary = target.with_suffix(".json.tmp")
                temporary.write_text(
                    json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
                )
                temporary.replace(target)  # écriture atomique
                self.reply_json(200, {"saved": len(payload["models"]), "path": "Gallery/local-gallery.json"})
                return

            if route == "/api/model":
                raw = self.read_body(MAX_MODEL_BYTES)
                text = raw.decode("utf-8")
                json.loads(text)  # un .nbd2 est du JSON : refuser le reste
                identifier = self.headers.get("X-Model-Id") or uuid.uuid4().hex
                if not SAFE_ID.match(identifier):
                    self.reply_json(400, {"error": "identifiant de modèle invalide"})
                    return
                target = self.local_models() / f"{identifier}.nbd2"
                target.write_text(text, encoding="utf-8")
                self.reply_json(200, {"path": f"Gallery/local/{identifier}.nbd2", "bytes": len(raw)})
                return

            self.reply_json(404, {"error": "route inconnue"})
        except (ValueError, json.JSONDecodeError) as error:
            self.reply_json(400, {"error": str(error)})
        except OSError as error:
            self.reply_json(500, {"error": f"écriture impossible : {error}"})

    def log_message(self, format: str, *args: object) -> None:
        # garder la sortie lisible : une ligne par requête, sans le bruit des en-têtes
        sys.stderr.write("%s - %s\n" % (self.address_string(), format % args))


def main() -> int:
    project = Path(__file__).resolve().parent
    parser = argparse.ArgumentParser(description="Sert NetBioDyn avec la galerie persistante.")
    parser.add_argument("--port", type=int, default=8081)
    parser.add_argument("--bind", default="127.0.0.1")
    parser.add_argument("--webroot", default=str(project / "web" / "webroot"))
    parser.add_argument("--gallery", default=str(project / "Gallery"))
    arguments = parser.parse_args()

    webroot = Path(arguments.webroot).resolve()
    gallery = Path(arguments.gallery).resolve()
    if not webroot.is_dir():
        print(f"webroot introuvable : {webroot}\nLance d'abord ./gradlew :web:syncJs", file=sys.stderr)
        return 2
    gallery.mkdir(parents=True, exist_ok=True)

    GalleryHandler.gallery_dir = gallery

    def handler(*args, **kwargs):
        return GalleryHandler(*args, directory=str(webroot), **kwargs)

    with ThreadingHTTPServer((arguments.bind, arguments.port), handler) as httpd:
        print(f"NetBioDyn  : http://localhost:{arguments.port}/")
        print(f"webroot    : {webroot}")
        print(f"galerie    : {gallery}  (ajouts dans local-gallery.json et local/)")
        try:
            httpd.serve_forever()
        except KeyboardInterrupt:
            print("\narrêt")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
