"""Check Outlook + Teams in Brave, draft replies with Claude for human review.

Setup (run once):
    python -m venv .venv && source .venv/bin/activate
    pip install -r requirements.txt
    playwright install chromium  # needed once for Playwright's runtime even when launching Brave
    export ANTHROPIC_API_KEY=sk-ant-...
    # Optional — override Brave's path if not at the OS default:
    # export BRAVE_PATH=/Applications/Brave\ Browser.app/Contents/MacOS/Brave\ Browser

Run:
    python check_messages.py                  # both services, 5 most recent unread each
    python check_messages.py --service outlook --limit 10
    python check_messages.py --service teams

First run will open Brave and pause for you to log in to Outlook + Teams.
Profile is saved to ./browser_profile/ so subsequent runs reuse the session.
Approved drafts land in ./drafts/<service>/ as .txt files for you to copy
into the real reply box. The script never sends anything itself.
"""

from __future__ import annotations

import argparse
import os
import platform
import re
import sys
from datetime import datetime
from pathlib import Path
from typing import NamedTuple

import anthropic
from playwright.sync_api import Browser, BrowserContext, Page, sync_playwright


REPO_ROOT = Path(__file__).resolve().parent
PROFILE_DIR = REPO_ROOT / "browser_profile"
DRAFTS_DIR = REPO_ROOT / "drafts"


class Message(NamedTuple):
    service: str          # "outlook" or "teams"
    sender: str
    subject: str          # for Teams, this is the chat name
    body: str


def find_brave() -> str:
    override = os.environ.get("BRAVE_PATH")
    if override:
        return override
    system = platform.system()
    candidates = {
        "Darwin": ["/Applications/Brave Browser.app/Contents/MacOS/Brave Browser"],
        "Linux": ["/usr/bin/brave-browser", "/usr/bin/brave", "/snap/bin/brave"],
        "Windows": [
            r"C:\Program Files\BraveSoftware\Brave-Browser\Application\brave.exe",
            r"C:\Program Files (x86)\BraveSoftware\Brave-Browser\Application\brave.exe",
        ],
    }.get(system, [])
    for path in candidates:
        if Path(path).exists():
            return path
    sys.exit(
        f"Could not find Brave on {system}. Set BRAVE_PATH to your Brave executable."
    )


def launch_brave(playwright) -> BrowserContext:
    PROFILE_DIR.mkdir(exist_ok=True)
    return playwright.chromium.launch_persistent_context(
        user_data_dir=str(PROFILE_DIR),
        executable_path=find_brave(),
        headless=False,
        viewport={"width": 1400, "height": 900},
        args=["--disable-blink-features=AutomationControlled"],
    )


def wait_for_login(page: Page, prompt: str) -> None:
    print(f"\n>>> {prompt}")
    print(">>> Press Enter in this terminal once the inbox/chat list is visible...")
    input()


# ---------------------------------------------------------------------------
# Outlook
# ---------------------------------------------------------------------------

def fetch_outlook_messages(page: Page, limit: int) -> list[Message]:
    """Read the N most recent unread items from Outlook web.

    Selectors target the OWA mail list and reading pane. Microsoft tweaks the
    DOM occasionally; if scraping returns nothing, inspect the inbox and adjust
    `row_selector` / `body_selector` below.
    """
    page.goto("https://outlook.office.com/mail/", wait_until="domcontentloaded")
    try:
        page.wait_for_selector('div[role="listbox"], div[aria-label="Message list"]', timeout=20_000)
    except Exception:
        wait_for_login(page, "Outlook didn't load the inbox — log in if needed.")
        page.wait_for_selector('div[role="listbox"], div[aria-label="Message list"]', timeout=60_000)

    # Unread mail rows are <div role="option"> with aria-label containing "Unread"
    row_selector = 'div[role="option"][aria-label*="Unread" i]'
    rows = page.locator(row_selector)
    count = min(rows.count(), limit)
    if count == 0:
        print("Outlook: no unread messages.")
        return []

    messages: list[Message] = []
    for i in range(count):
        row = rows.nth(i)
        try:
            row.scroll_into_view_if_needed()
            row.click()
        except Exception as e:
            print(f"Outlook: skipping row {i} ({e})")
            continue

        # Reading pane
        try:
            page.wait_for_selector('div[role="main"] div[role="heading"]', timeout=10_000)
        except Exception:
            print(f"Outlook: reading pane didn't open for row {i}")
            continue

        subject = _safe_text(page, 'div[role="main"] div[role="heading"]')
        sender = _safe_text(page, 'div[role="main"] span[class*="OZZZK"], div[role="main"] [data-testid="message-header-from"]')
        body = _safe_text(page, 'div[role="main"] div[aria-label="Message body"], div[role="main"] [role="document"]')

        if subject or body:
            messages.append(Message("outlook", sender or "(unknown sender)", subject or "(no subject)", body[:4000]))

    return messages


# ---------------------------------------------------------------------------
# Teams
# ---------------------------------------------------------------------------

def fetch_teams_messages(page: Page, limit: int) -> list[Message]:
    """Read the most recent messages from Teams chats with unread indicators."""
    page.goto("https://teams.microsoft.com/v2/", wait_until="domcontentloaded")
    try:
        page.wait_for_selector('[data-tid="chat-list"], [aria-label="Chat list"]', timeout=20_000)
    except Exception:
        wait_for_login(page, "Teams didn't load the chat list — log in if needed.")
        page.wait_for_selector('[data-tid="chat-list"], [aria-label="Chat list"]', timeout=120_000)

    # Chats with unread badges
    unread_chats = page.locator('[data-tid="chat-list-item"]:has([aria-label*="unread" i])')
    count = min(unread_chats.count(), limit)
    if count == 0:
        print("Teams: no unread chats.")
        return []

    messages: list[Message] = []
    for i in range(count):
        chat = unread_chats.nth(i)
        try:
            chat_name = chat.get_attribute("aria-label") or "(chat)"
            chat.scroll_into_view_if_needed()
            chat.click()
        except Exception as e:
            print(f"Teams: skipping chat {i} ({e})")
            continue

        try:
            page.wait_for_selector('[data-tid="message-pane"] [data-tid="chat-pane-message"]', timeout=10_000)
        except Exception:
            print(f"Teams: message pane didn't open for chat {i}")
            continue

        bubbles = page.locator('[data-tid="chat-pane-message"]')
        if bubbles.count() == 0:
            continue

        # Last message in this chat
        last = bubbles.last
        sender = _safe_text(last, '[data-tid="message-author-name"]') or "(unknown)"
        body = _safe_text(last, '[data-tid="message-body-content"]') or last.inner_text()
        messages.append(Message("teams", sender, chat_name.strip(), body[:4000]))

    return messages


def _safe_text(scope, selector: str) -> str:
    try:
        loc = scope.locator(selector).first
        if loc.count() == 0:
            return ""
        return loc.inner_text(timeout=3_000).strip()
    except Exception:
        return ""


# ---------------------------------------------------------------------------
# Drafting
# ---------------------------------------------------------------------------

DRAFT_SYSTEM_PROMPT = """You draft reply messages on behalf of the user. The user will review every draft before it is sent — so be willing to commit to a concrete reply rather than hedging.

Guidelines:
- Match the register of the incoming message (formal email vs casual chat).
- Be concise. No "I hope this finds you well" filler.
- If the message asks a question you cannot answer without information you don't have, draft a reply that asks the specific clarifying question.
- If the message is purely informational and no reply is warranted, output exactly: NO_REPLY_NEEDED
- Do not include subject lines, signatures, or "Best regards" — just the body of the reply.
- Use plain text. No markdown."""


def draft_reply(client: anthropic.Anthropic, msg: Message) -> str:
    user_prompt = (
        f"Service: {msg.service}\n"
        f"From: {msg.sender}\n"
        f"Subject/Chat: {msg.subject}\n"
        f"---\n"
        f"{msg.body}\n"
        f"---\n"
        f"Draft a reply."
    )
    response = client.messages.create(
        model="claude-opus-4-7",
        max_tokens=1024,
        system=[
            {
                "type": "text",
                "text": DRAFT_SYSTEM_PROMPT,
                "cache_control": {"type": "ephemeral"},
            }
        ],
        messages=[{"role": "user", "content": user_prompt}],
    )
    for block in response.content:
        if block.type == "text":
            return block.text.strip()
    return ""


# ---------------------------------------------------------------------------
# Review loop
# ---------------------------------------------------------------------------

def slugify(text: str, maxlen: int = 50) -> str:
    s = re.sub(r"[^\w\s-]", "", text).strip().lower()
    s = re.sub(r"[\s-]+", "-", s)
    return s[:maxlen] or "untitled"


def review_and_save(msg: Message, draft: str) -> None:
    print("\n" + "=" * 70)
    print(f"[{msg.service.upper()}] From: {msg.sender}")
    print(f"Subject/Chat: {msg.subject}")
    print("-" * 70)
    print(f"Original (truncated):\n{msg.body[:500]}")
    print("-" * 70)
    if draft == "NO_REPLY_NEEDED":
        print("Claude judged: no reply needed. Skipping.")
        return
    print(f"Draft reply:\n{draft}")
    print("=" * 70)

    while True:
        choice = input("[s]ave / [e]dit / [k]ip > ").strip().lower()
        if choice in ("s", ""):
            _write_draft(msg, draft)
            return
        if choice == "e":
            print("Paste edited reply, then a line containing only END:")
            lines = []
            while True:
                line = input()
                if line.strip() == "END":
                    break
                lines.append(line)
            _write_draft(msg, "\n".join(lines))
            return
        if choice == "k":
            return


def _write_draft(msg: Message, body: str) -> None:
    out_dir = DRAFTS_DIR / msg.service
    out_dir.mkdir(parents=True, exist_ok=True)
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    path = out_dir / f"{timestamp}_{slugify(msg.subject)}.txt"
    header = f"To: {msg.sender}\nRe: {msg.subject}\n\n"
    path.write_text(header + body)
    print(f"Saved -> {path}")


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--service", choices=["outlook", "teams", "both"], default="both")
    parser.add_argument("--limit", type=int, default=5, help="Max messages per service")
    args = parser.parse_args()

    if not os.environ.get("ANTHROPIC_API_KEY"):
        sys.exit("ANTHROPIC_API_KEY not set.")

    claude = anthropic.Anthropic()

    with sync_playwright() as pw:
        context = launch_brave(pw)
        page = context.pages[0] if context.pages else context.new_page()

        all_messages: list[Message] = []
        if args.service in ("outlook", "both"):
            print("Checking Outlook...")
            all_messages.extend(fetch_outlook_messages(page, args.limit))
        if args.service in ("teams", "both"):
            print("Checking Teams...")
            all_messages.extend(fetch_teams_messages(page, args.limit))

        if not all_messages:
            print("No messages to review.")
            context.close()
            return 0

        print(f"\nDrafting replies for {len(all_messages)} message(s)...")
        for msg in all_messages:
            try:
                draft = draft_reply(claude, msg)
            except anthropic.APIError as e:
                print(f"Draft failed for '{msg.subject}': {e}")
                continue
            review_and_save(msg, draft)

        print("\nDone. Drafts saved to ./drafts/")
        input("Press Enter to close the browser...")
        context.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
