#!/usr/bin/env python3
"""Serves web/webroot over HTTPS (self-signed certificate).

Why: the File System Access API used by the "Save as" file explorer is only
available in a *secure context* (HTTPS or localhost). Plain HTTP on a remote
host falls back to a plain download. Access the app at https://<host>:PORT
instead (accept the certificate warning once, or add web/certs/cert.pem to
your system trust store to remove it).

Usage:  python3 serve_https.py [port]   (default port: 8099)
"""
import http.server
import os
import socket
import ssl
import subprocess
import sys

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
WEBROOT = os.path.join(BASE_DIR, "web", "webroot")
CERT_DIR = os.path.join(BASE_DIR, "web", "certs")
CERT = os.path.join(CERT_DIR, "cert.pem")
KEY = os.path.join(CERT_DIR, "key.pem")


def local_addresses():
    """Best effort list of (ip, host) pairs to put in the certificate SAN."""
    addrs = [("127.0.0.1", "localhost"), ("172.17.0.1", None), ("172.17.0.2", None),
             (None, "chouette"), (None, "timc-dctm-35")]
    try:
        out = subprocess.check_output(["hostname", "-I"], text=True, stderr=subprocess.DEVNULL)
        for ip in out.split():
            if not any(a[0] == ip for a in addrs):
                addrs.append((ip, None))
    except Exception:
        pass
    try:
        host = socket.gethostname()
        if host and not any(a[1] == host for a in addrs):
            addrs.append((None, host))
    except Exception:
        pass
    return addrs


def ensure_certificate():
    os.makedirs(CERT_DIR, exist_ok=True)
    if os.path.exists(CERT) and os.path.exists(KEY):
        return
    san = []
    for ip, host in local_addresses():
        if ip:
            san.append(f"IP:{ip}")
        if host:
            san.append(f"DNS:{host}")
    subprocess.check_call([
        "openssl", "req", "-x509", "-newkey", "rsa:2048", "-nodes",
        "-keyout", KEY, "-out", CERT, "-days", "825",
        "-subj", "/CN=chouette",
        "-addext", f"subjectAltName={','.join(san)}",
    ])
    print(f"Self-signed certificate created: {CERT}")
    print("  Subject alternative names:", ", ".join(san))


def main():
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8099
    if not os.path.isdir(WEBROOT):
        sys.exit(f"webroot not found: {WEBROOT}")
    ensure_certificate()
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    context.load_cert_chain(CERT, KEY)
    httpd = http.server.ThreadingHTTPServer(("0.0.0.0", port),
                                            lambda *a, **kw: http.server.SimpleHTTPRequestHandler(*a, directory=WEBROOT, **kw))
    httpd.socket = context.wrap_socket(httpd.socket, server_side=True)
    print(f"Serving {WEBROOT}")
    print(f"  HTTPS: https://<host>:{port}/   (File System Access API works)")
    print(f"  Plain HTTP on the same port is not accepted: use https://")
    httpd.serve_forever()


if __name__ == "__main__":
    main()
