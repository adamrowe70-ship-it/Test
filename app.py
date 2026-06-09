#!/usr/bin/env python3
"""GamePrice — find the cheapest PC game prices across ~30 stores.

A tiny, dependency-free web app. It serves a single-page frontend and a small
JSON API that proxies the free CheapShark deals aggregator
(https://www.cheapshark.com/api/). CheapShark tracks live prices on Steam, GOG,
Humble, Fanatical, GreenManGaming, Epic and many more, so we get reliable,
up-to-date deals without scraping each store individually.

Run:
    python3 app.py
then open http://localhost:8000 in your browser.
"""

from __future__ import annotations

import json
import os
import time
import urllib.parse
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

CHEAPSHARK = "https://www.cheapshark.com/api/1.0"
HOST = os.environ.get("HOST", "127.0.0.1")
PORT = int(os.environ.get("PORT", "8000"))
STATIC_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "static")

# A friendly User-Agent keeps us in good standing with the API.
USER_AGENT = "GamePrice/1.0 (+https://github.com/) python-urllib"

# The store list rarely changes, so fetch it once and cache it in memory.
_stores_cache: dict[str, dict] = {}
_stores_fetched_at: float = 0.0
_STORES_TTL = 60 * 60  # 1 hour


def _api_get(path: str, params: dict) -> object:
    """GET a CheapShark endpoint and return the decoded JSON."""
    url = f"{CHEAPSHARK}/{path}?{urllib.parse.urlencode(params)}"
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=20) as resp:
        return json.loads(resp.read().decode("utf-8"))


def get_stores() -> dict[str, dict]:
    """Return {storeID: store_info}, cached for an hour."""
    global _stores_cache, _stores_fetched_at
    if _stores_cache and (time.time() - _stores_fetched_at) < _STORES_TTL:
        return _stores_cache
    try:
        data = _api_get("stores", {})
        _stores_cache = {s["storeID"]: s for s in data}
        _stores_fetched_at = time.time()
    except Exception:
        # If the refresh fails, keep serving whatever we had last.
        pass
    return _stores_cache


def search_games(title: str, limit: int = 20) -> list[dict]:
    """Search games by title. Returns a trimmed list of matches."""
    title = title.strip()
    if not title:
        return []
    data = _api_get("games", {"title": title, "limit": limit})
    results = []
    for g in data:
        results.append(
            {
                "gameID": g.get("gameID"),
                "name": g.get("external"),
                "thumb": g.get("thumb"),
                "cheapest": g.get("cheapest"),
                "steamAppID": g.get("steamAppID"),
            }
        )
    return results


def get_deals(game_id: str) -> dict:
    """Return all store deals for a game, sorted cheapest-first.

    Each deal includes a CheapShark redirect link that forwards the buyer to
    the actual store page for that offer.
    """
    data = _api_get("games", {"id": game_id})
    info = data.get("info", {})
    stores = get_stores()

    deals = []
    for d in data.get("deals", []):
        store = stores.get(d.get("storeID"), {})
        deal_id = d.get("dealID")
        deals.append(
            {
                "store": store.get("storeName", f"Store {d.get('storeID')}"),
                "storeID": d.get("storeID"),
                "price": d.get("price"),
                "retailPrice": d.get("retailPrice"),
                "savings": round(float(d.get("savings", 0) or 0)),
                "buyURL": f"https://www.cheapshark.com/redirect?dealID={deal_id}",
            }
        )
    deals.sort(key=lambda x: float(x["price"]) if x["price"] else float("inf"))

    return {
        "title": info.get("title"),
        "thumb": info.get("thumb"),
        "steamAppID": info.get("steamAppID"),
        "cheapestPriceEver": (data.get("cheapestPriceEver") or {}).get("price"),
        "deals": deals,
    }


class Handler(BaseHTTPRequestHandler):
    server_version = "GamePrice/1.0"

    def _send_json(self, payload: object, status: int = 200) -> None:
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _send_file(self, path: str, content_type: str) -> None:
        try:
            with open(path, "rb") as fh:
                body = fh.read()
        except FileNotFoundError:
            self.send_error(404, "Not found")
            return
        self.send_response(200)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self) -> None:
        parsed = urllib.parse.urlparse(self.path)
        route = parsed.path
        query = urllib.parse.parse_qs(parsed.query)

        if route in ("/", "/index.html"):
            self._send_file(os.path.join(STATIC_DIR, "index.html"), "text/html; charset=utf-8")
            return

        if route == "/api/search":
            title = (query.get("title") or [""])[0]
            try:
                self._send_json({"results": search_games(title)})
            except Exception as exc:
                self._send_json({"error": str(exc)}, status=502)
            return

        if route == "/api/deals":
            game_id = (query.get("id") or [""])[0]
            if not game_id:
                self._send_json({"error": "missing game id"}, status=400)
                return
            try:
                self._send_json(get_deals(game_id))
            except Exception as exc:
                self._send_json({"error": str(exc)}, status=502)
            return

        self.send_error(404, "Not found")

    def log_message(self, fmt: str, *args) -> None:
        # Quiet, single-line request logging.
        print(f"{self.address_string()} - {fmt % args}")


def main() -> None:
    server = ThreadingHTTPServer((HOST, PORT), Handler)
    print(f"GamePrice running at http://{HOST}:{PORT}  (Ctrl+C to stop)")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nShutting down.")
        server.shutdown()


if __name__ == "__main__":
    main()
