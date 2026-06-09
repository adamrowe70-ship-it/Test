#!/usr/bin/env python3
"""Optional local server for GamePrice.

You normally DON'T need this — the app is a single static page (docs/index.html)
that talks to the free CheapShark API directly from your browser, so it runs
fine on GitHub Pages or by just opening the file. This little zero-dependency
server is only here for convenience if you'd rather run it locally:

    python3 app.py
    # then open http://localhost:8000

It simply serves the docs/ folder.
"""

from __future__ import annotations

import os
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer

HOST = os.environ.get("HOST", "127.0.0.1")
PORT = int(os.environ.get("PORT", "8000"))
DOCS_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "docs")


def main() -> None:
    handler = partial(SimpleHTTPRequestHandler, directory=DOCS_DIR)
    server = ThreadingHTTPServer((HOST, PORT), handler)
    print(f"GamePrice running at http://{HOST}:{PORT}  (Ctrl+C to stop)")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nShutting down.")
        server.shutdown()


if __name__ == "__main__":
    main()
