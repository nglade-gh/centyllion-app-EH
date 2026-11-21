#!/usr/bin/env python3
"""Fabrique l'archive de déploiement de NetBioDyn Forever (site statique).

L'archive contient le contenu de ``web/webroot`` **à la racine** (pas de dossier
``webroot/``), de sorte qu'elle peut être dézippée directement dans le
répertoire servi par le serveur web :

    python3 tools/make_release_zip.py                    # -> dist/netbiodyn-forever-1.0-web.zip
    python3 tools/make_release_zip.py /tmp/essai.zip     # chemin explicite

Sont exclus : ``webroot/certs`` (clés privées) et ``webroot/test_*.json``
(modèles de test locaux). Le bundle doit avoir été construit avant :

    ./gradlew :web:jsBrowserProductionWebpack :web:syncJs

Aucune dépendance externe (bibliothèque standard uniquement).
"""
import os
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
WEBROOT = os.path.join(ROOT, "web", "webroot")
DEFAULT_OUT = os.path.join(ROOT, "dist", "netbiodyn-forever-1.0-web.zip")

EXCLUDED_DIRS = {"certs"}
EXCLUDED_PREFIXES = ("test_",)
REQUIRED = ("index.html", "js/centyllion/centyllion.js", "version.json")


def main(out=DEFAULT_OUT):
    if not os.path.isdir(WEBROOT):
        sys.exit(f"répertoire absent : {WEBROOT}")

    missing = [f for f in REQUIRED if not os.path.isfile(os.path.join(WEBROOT, f))]
    if missing:
        sys.exit("bundle incomplet (construire d'abord le projet) : " + ", ".join(missing))

    os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
    count = 0
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for root, dirs, files in os.walk(WEBROOT):
            dirs[:] = sorted(d for d in dirs if d not in EXCLUDED_DIRS)
            for name in sorted(files):
                if name.startswith(EXCLUDED_PREFIXES):
                    continue
                path = os.path.join(root, name)
                archive.write(path, os.path.relpath(path, WEBROOT))
                count += 1

    print(f"{out} : {count} fichiers, {os.path.getsize(out) / 1e6:.2f} Mo")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else DEFAULT_OUT)
