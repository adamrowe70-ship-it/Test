# GamePrice 🎮💰

Type the name of a PC game and instantly see the **cheapest place to buy it**,
with a direct purchase link. Works on your phone — it's just a web page.

Prices come from the free [CheapShark](https://www.cheapshark.com/api/) deals
API, which tracks live prices across **~30 stores** — Steam, GOG, Humble,
Fanatical, GreenManGaming, Epic and more — so you don't have to check each store
yourself.

## 📱 Open it on your phone

Once GitHub Pages has finished its first deploy, the app lives at:

**https://adamrowe70-ship-it.github.io/Test/**

Just tap that link — search a game, pick the match, tap **Buy →**. Add it to
your home screen for an app-like icon.

## How it works

It's a single static page (`docs/index.html`) with no backend. Your browser
calls the CheapShark API directly (it allows cross-origin requests), so the page
can be hosted for free on GitHub Pages and opened on any device.

```
docs/index.html  ──►  CheapShark /games?title=…   (search)
                 ──►  CheapShark /games?id=…       (all store offers)
                 ──►  CheapShark /stores           (store names)
Buy link  ──►  CheapShark /redirect?dealID=…  ──►  the actual store page
```

Deployment is automatic: the GitHub Actions workflow in
`.github/workflows/deploy-pages.yml` publishes `docs/` to GitHub Pages on every
push to the `claude/game-price-scraper-k1c9l4` branch.

## Run it locally (optional)

You don't need this, but if you want to run it on your own computer:

```bash
python3 app.py          # then open http://localhost:8000
```

No `pip install` required — it only uses the Python standard library.

## Notes & limitations

- Prices are shown in **USD** (CheapShark is USD-based).
- Only stores CheapShark tracks are included; a game with no current deals shows
  no offers.
- The app reads public pricing data only — the **Buy** link hands you off to the
  store to check out there.
