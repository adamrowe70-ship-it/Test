# GamePrice 🎮💰

Type the name of a PC game and instantly see the **cheapest place to buy it**,
with a direct purchase link.

It's a tiny, **zero-dependency** web app (pure Python standard library + a single
HTML page). Prices come from the free [CheapShark](https://www.cheapshark.com/api/)
deals API, which aggregates live prices across **~30 stores** — Steam, GOG,
Humble, Fanatical, GreenManGaming, Epic, Fanatical, and more — so you don't have
to scrape each store yourself.

## Run it

```bash
python3 app.py
```

Then open **http://localhost:8000** in your browser.

That's it — no `pip install` required (it only uses the Python standard library).

Optional environment variables:

| Variable | Default     | Meaning                     |
|----------|-------------|-----------------------------|
| `HOST`   | `127.0.0.1` | Address to bind             |
| `PORT`   | `8000`      | Port to listen on           |

## How to use

1. Type a game name (e.g. *Elden Ring*) and hit **Search**.
2. Pick the matching game from the list.
3. See every current offer sorted cheapest-first, with the discount and an
   **all-time-low** price for reference.
4. Click **Buy →** to go straight to the store's page for that deal.

## How it works

```
Browser  ──/api/search──►  app.py  ──►  CheapShark /games?title=
         ──/api/deals───►          ──►  CheapShark /games?id=
                                   ──►  CheapShark /stores  (cached 1h)
```

- `app.py` is a small `http.server` backend that proxies and normalises the
  CheapShark API, joins store IDs to store names, and sorts deals by price.
- `static/index.html` is the single-page frontend (search box → results →
  price comparison table).
- Buy links use CheapShark's `redirect?dealID=…` endpoint, which forwards the
  buyer to the actual store page for that specific offer.

## Notes & limitations

- Prices are shown in **USD** (CheapShark is USD-based).
- Only stores CheapShark tracks are included. A game with no current deals will
  show no offers.
- This app reads public pricing data only; it doesn't handle purchases — the
  Buy link hands you off to the store to check out there.
