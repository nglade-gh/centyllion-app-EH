#!/usr/bin/env python3
"""Construit l'archive de déploiement d'une version de Centyllion.

Le script vit dans le dépôt d'Endless Bloom et connaît les deux versions
publiées :

- **Endless Bloom** : le dépôt qui le contient (branche ``main``) ;
- **NetBioDyn Forever** : le dépôt voisin ``../NetBioDyn_Forever``
  (branche ``NetBioDyn_Forever``), gelé mais encore déployé.

Il demande quelle version déployer (ou l'accepte en argument), construit le
bundle Kotlin/JS puis fabrique l'archive du site statique, prête à être
dézippée dans le répertoire servi par le serveur web (NetBioDyn Forever est
déployé depuis un serveur universitaire).

    python3 tools/version_deployment.py              # menu interactif
    python3 tools/version_deployment.py bloom        # Endless Bloom
    python3 tools/version_deployment.py forever      # NetBioDyn Forever
    python3 tools/version_deployment.py all          # les deux

Options :
    --label <texte>   étiquette de version dans le nom de l'archive
                      (défaut : ``web/webroot/version.json``, sinon la version
                      de repli déclarée plus bas)
    --no-build        ne reconstruit pas le bundle, archive l'existant
    --out <dir>       répertoire de sortie (défaut : ``<version>/dist``)

L'archive contient le contenu de ``web/webroot`` **à la racine** (pas de
dossier ``webroot/``). ``certs/`` (clés privées), les ``test_*.json`` et les
``.DS_Store`` sont exclus. Aucune dépendance externe (bibliothèque standard).

Après déploiement, vérifier la date affichée dans le menu About : le navigateur
ressert l'ancienne version si les en-têtes de cache ne sont pas en ``no-cache``.
"""
import argparse
import hashlib
import json
import os
import subprocess
import sys
import zipfile

# Le script est dans <Endless_Bloom>/tools, les deux dépôts sont voisins.
BLOOM_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CENTYLLION = os.path.dirname(BLOOM_ROOT)

VERSIONS = {
    "bloom": {
        "project": BLOOM_ROOT,
        "slug": "netbiodyn-bloom",
        "branch": "main",
        "title": "Endless Bloom",
        "fallback_label": "2.0.0",
    },
    "forever": {
        "project": os.path.join(CENTYLLION, "NetBioDyn_Forever"),
        "slug": "netbiodyn-forever",
        "branch": "NetBioDyn_Forever",
        "title": "NetBioDyn Forever",
        "fallback_label": "1.0",
    },
}

EXCLUDED_DIRS = {"certs"}
EXCLUDED_PREFIXES = ("test_",)
EXCLUDED_NAMES = {".DS_Store"}
REQUIRED = ("index.html", "js/centyllion/centyllion.js", "version.json")


def git(project, *args):
    """Runs git in the project, or returns None when git/the repository is unavailable."""
    try:
        out = subprocess.run(
            ["git", "-c", "safe.directory=*", *args],
            cwd=project, capture_output=True, text=True, timeout=30,
        )
    except (OSError, subprocess.SubprocessError):
        return None
    return out.stdout.strip() if out.returncode == 0 else None


def label_for(project, version, requested):
    if requested:
        return requested
    path = os.path.join(project, "web", "webroot", "version.json")
    try:
        with open(path, encoding="utf-8") as stream:
            written = str(json.load(stream).get("version") or "").strip()
    except (OSError, ValueError):
        written = ""
    # a version.json still carrying the placeholder is not a usable label
    if written and written not in {"0.0.1", "dev"}:
        return written
    return version["fallback_label"]


def build(project):
    gradlew = os.path.join(project, "gradlew")
    if not os.path.isfile(gradlew):
        sys.exit(f"gradlew absent dans {project}")
    base = [gradlew, ":web:jsBrowserProductionWebpack", ":web:syncJs"]
    print("  construction du bundle…")
    for options in (["--offline"], []):
        result = subprocess.run(base + options, cwd=project)
        if result.returncode == 0:
            return
        print(f"  échec avec {' '.join(options) or 'accès réseau'}, nouvel essai…")
    sys.exit("la construction du bundle a échoué")


def make_archive(project, out):
    webroot = os.path.join(project, "web", "webroot")
    missing = [name for name in REQUIRED if not os.path.isfile(os.path.join(webroot, name))]
    if missing:
        sys.exit("bundle incomplet (construire d'abord le projet) : " + ", ".join(missing))

    os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
    count = 0
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for root, dirs, files in os.walk(webroot):
            dirs[:] = sorted(d for d in dirs if d not in EXCLUDED_DIRS)
            for name in sorted(files):
                if name.startswith(EXCLUDED_PREFIXES) or name in EXCLUDED_NAMES:
                    continue
                path = os.path.join(root, name)
                archive.write(path, os.path.relpath(path, webroot))
                count += 1
    return count


def digest(path):
    sha = hashlib.sha256()
    with open(path, "rb") as stream:
        for chunk in iter(lambda: stream.read(1 << 20), b""):
            sha.update(chunk)
    return sha.hexdigest()


def release(key, args):
    version = VERSIONS[key]
    project = version["project"]
    if not os.path.isdir(project):
        sys.exit(f"répertoire absent : {project}")

    print(f"\n=== {version['title']} ({os.path.basename(project)})")
    state = git(project, "status", "--porcelain")
    branch = git(project, "rev-parse", "--abbrev-ref", "HEAD") or "?"
    head = git(project, "rev-parse", "--short", "HEAD") or "?"
    print(f"  branche {branch} @ {head}")
    if state:
        print(f"  ATTENTION : {len(state.splitlines())} fichier(s) non commité(s) seront archivés")
    if branch not in (version["branch"], "?"):
        print(f"  ATTENTION : la branche attendue est {version['branch']}")

    if not args.no_build:
        build(project)

    label = label_for(project, version, args.label)
    out_dir = args.out or os.path.join(project, "dist")
    out = os.path.join(out_dir, f"{version['slug']}-{label}-web.zip")
    count = make_archive(project, out)

    print(f"  archive : {out}")
    print(f"  {count} fichiers, {os.path.getsize(out) / 1e6:.2f} Mo")
    print(f"  sha256  : {digest(out)}")
    return out


def choose():
    keys = list(VERSIONS)
    print("Quelle version déployer ?")
    for index, key in enumerate(keys, start=1):
        print(f"  {index}. {VERSIONS[key]['title']} ({VERSIONS[key]['branch']})")
    print(f"  {len(keys) + 1}. les deux")
    answer = input("Numéro [1] : ").strip() or "1"
    if answer == str(len(keys) + 1):
        return keys
    try:
        return [keys[int(answer) - 1]]
    except (ValueError, IndexError):
        sys.exit(f"choix invalide : {answer}")


def main():
    parser = argparse.ArgumentParser(
        description="Construit l'archive de déploiement d'une version de Centyllion (Endless Bloom, NetBioDyn Forever).")
    parser.add_argument("version", nargs="?", choices=list(VERSIONS) + ["all"],
                        help="version à déployer (défaut : menu interactif)")
    parser.add_argument("--label", help="étiquette de version dans le nom de l'archive")
    parser.add_argument("--no-build", action="store_true", help="archiver le bundle existant")
    parser.add_argument("--out", help="répertoire de sortie")
    args = parser.parse_args()

    if args.version == "all":
        keys = list(VERSIONS)
    elif args.version:
        keys = [args.version]
    else:
        keys = choose()

    archives = [release(key, args) for key in keys]

    print("\nDéploiement (sur le serveur qui sert le site) :")
    for archive in archives:
        print(f"  unzip -o {os.path.basename(archive)} -d <répertoire_servi>")
    print("  puis vérifier la date affichée dans le menu About (le navigateur garde")
    print("  l'ancienne version si les en-têtes de cache ne sont pas en no-cache).")


if __name__ == "__main__":
    main()
