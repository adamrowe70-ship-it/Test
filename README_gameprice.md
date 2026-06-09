# GamePrice 🎮💰

Type the name of a PC game and instantly see the **cheapest place to buy it**,
with a direct purchase link. Works on your phone — it's just a web page.

Prices come from the free [CheapShark](https://www.cheapshark.com/api/) deals
API, which tracks live prices across **~30 stores** — Steam, GOG, Humble,
Fanatical, GreenManGaming, Epic and more — so you don't have to check each store
yourself.

## 📱 Open it on your phone

**One-time setup** (turns on free GitHub Pages hosting):

1. Go to **https://github.com/adamrowe70-ship-it/Test/settings/pages**
2. Under **Build and deployment → Source**, choose **Deploy from a branch**.
3. Set **Branch** to `claude/game-price-scraper-k1c9l4` and the folder to
   **`/docs`**, then tap **Save**.
4. Wait ~1 minute. Your app is then live at:

   **https://adamrowe70-ship-it.github.io/Test/**

Tap that link any time — search a game, pick the match, tap **Buy →**. Add it
to your home screen for an app-like icon. It auto-updates whenever the `docs/`
folder changes on that branch.

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

Once GitHub Pages is pointed at the `/docs` folder (see setup above), it
re-publishes automatically whenever that folder changes — no build step needed.

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
