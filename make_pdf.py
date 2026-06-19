#!/usr/bin/env python3
"""Generate a two-page PDF guide on emotional regulation for a high-functioning autistic person."""

from reportlab.lib.pagesizes import A4
from reportlab.lib.units import mm
from reportlab.lib.colors import HexColor
from reportlab.lib.enums import TA_LEFT
from reportlab.platypus import (
    BaseDocTemplate, PageTemplate, Frame, Paragraph, Spacer,
    Table, TableStyle, ListFlowable, ListItem, HRFlowable, KeepTogether
)
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle

# ---- Palette ----
INK = HexColor("#1f2933")
ACCENT = HexColor("#2f6f7e")      # calm teal
ACCENT_DK = HexColor("#1d4b56")
SOFT = HexColor("#eef4f5")        # pale panel
SOFT2 = HexColor("#f7f3ec")       # warm panel
MUTED = HexColor("#5a6571")
LINE = HexColor("#c9d6d9")

# ---- Styles ----
ss = getSampleStyleSheet()

def style(name, **kw):
    base = kw.pop("parent", ss["Normal"])
    return ParagraphStyle(name, parent=base, **kw)

H_TITLE = style("HTitle", fontName="Helvetica-Bold", fontSize=20, leading=23,
                textColor=ACCENT_DK, spaceAfter=2)
H_SUB = style("HSub", fontName="Helvetica-Oblique", fontSize=10.5, leading=14,
              textColor=MUTED, spaceAfter=4)
H1 = style("H1", fontName="Helvetica-Bold", fontSize=12.5, leading=15,
           textColor=ACCENT_DK, spaceBefore=8, spaceAfter=3)
BODY = style("Body", fontName="Helvetica", fontSize=9.5, leading=13.2,
             textColor=INK, alignment=TA_LEFT, spaceAfter=4)
BODY_S = style("BodyS", fontName="Helvetica", fontSize=9, leading=12.2,
               textColor=INK)
BULLET = style("Bullet", parent=BODY, fontSize=9.3, leading=12.6, spaceAfter=2)
PANEL_H = style("PanelH", fontName="Helvetica-Bold", fontSize=10.5, leading=13,
                textColor=ACCENT_DK, spaceAfter=3)
PANEL_B = style("PanelB", fontName="Helvetica", fontSize=9, leading=12.4,
                textColor=INK)
QUOTE = style("Quote", fontName="Helvetica-Oblique", fontSize=9.5, leading=13,
              textColor=ACCENT_DK)
FOOT = style("Foot", fontName="Helvetica", fontSize=7.6, leading=10, textColor=MUTED)
TAG = style("Tag", fontName="Helvetica-Bold", fontSize=8, leading=10,
            textColor=HexColor("#ffffff"))


def bullets(items, st=BULLET, leftpad=10):
    return ListFlowable(
        [ListItem(Paragraph(t, st), leftIndent=leftpad, value="•",
                  bulletColor=ACCENT) for t in items],
        bulletType="bullet", start="•", leftIndent=leftpad,
        bulletFontSize=8, spaceBefore=0, spaceAfter=2,
    )


def panel(title, flowables, bg=SOFT, width=None):
    """A soft rounded info panel."""
    inner = [Paragraph(title, PANEL_H)] + flowables
    t = Table([[inner]], colWidths=[width])
    t.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, -1), bg),
        ("LEFTPADDING", (0, 0), (-1, -1), 9),
        ("RIGHTPADDING", (0, 0), (-1, -1), 9),
        ("TOPPADDING", (0, 0), (-1, -1), 7),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 7),
        ("LINEBEFORE", (0, 0), (0, -1), 3, ACCENT),
        ("ROUNDEDCORNERS", [3, 3, 3, 3]),
    ]))
    return t


# ---- Document scaffold ----
PAGE_W, PAGE_H = A4
MARGIN = 16 * mm
FRAME_W = PAGE_W - 2 * MARGIN


def header_footer(canvas, doc):
    canvas.saveState()
    # top accent band
    canvas.setFillColor(ACCENT)
    canvas.rect(0, PAGE_H - 8 * mm, PAGE_W, 8 * mm, fill=1, stroke=0)
    canvas.setFillColor(ACCENT_DK)
    canvas.rect(0, PAGE_H - 9.4 * mm, PAGE_W, 1.4 * mm, fill=1, stroke=0)
    # footer
    canvas.setFillColor(MUTED)
    canvas.setFont("Helvetica", 7.6)
    canvas.drawString(MARGIN, 9 * mm,
                      "A self-understanding guide — general information, not a substitute for professional support.")
    canvas.drawRightString(PAGE_W - MARGIN, 9 * mm, f"Page {doc.page} of 2")
    canvas.setStrokeColor(LINE)
    canvas.setLineWidth(0.5)
    canvas.line(MARGIN, 11.5 * mm, PAGE_W - MARGIN, 11.5 * mm)
    canvas.restoreState()


doc = BaseDocTemplate(
    "/home/user/Test/Emotional_Regulation_Guide.pdf",
    pagesize=A4,
    leftMargin=MARGIN, rightMargin=MARGIN,
    topMargin=14 * mm, bottomMargin=14 * mm,
    title="Understanding & Handling Emotional Regulation",
    author="Emotional Regulation Guide",
)
frame = Frame(MARGIN, 13 * mm, FRAME_W, PAGE_H - 26 * mm, id="main",
              leftPadding=0, rightPadding=0, topPadding=0, bottomPadding=0)
doc.addPageTemplates([PageTemplate(id="all", frames=[frame], onPage=header_footer)])

story = []

# ===================== PAGE 1 =====================
story.append(Paragraph("Emotional Regulation", H_TITLE))
story.append(Paragraph(
    "A practical guide for a capable, articulate autistic adult &mdash; "
    "for the times when feelings build up before you notice them.", H_SUB))
story.append(HRFlowable(width="100%", thickness=1, color=LINE, spaceBefore=2, spaceAfter=6))

story.append(Paragraph("Why this can catch you off guard", H1))
story.append(Paragraph(
    "Being &ldquo;high-functioning&rdquo; means you can mask, reason and hold things together in the "
    "moment &mdash; which also means distress can grow quietly in the background while your thinking mind "
    "is busy coping. Many autistic people experience <b>interoception</b> differently: the internal signals "
    "that say <i>&ldquo;I am getting overwhelmed&rdquo;</i> arrive faint, late, or not at all. So the first time "
    "you consciously notice something is wrong, you may already be near the edge. This is not a personal "
    "failing or lack of willpower &mdash; it is a difference in how your nervous system reports its own state.",
    BODY))

# Two-column: build-up curve + meltdown/shutdown
left_col = [
    Paragraph("The build-up usually has stages", PANEL_H),
    Paragraph(
        "<b>1. Baseline (Green)</b> &mdash; calm, flexible, curious.<br/>"
        "<b>2. Rumbling (Amber)</b> &mdash; small irritations stack up; you feel &ldquo;off&rdquo; "
        "but can&rsquo;t always name it.<br/>"
        "<b>3. Edge (Orange)</b> &mdash; less patience, urge to leave, snappy, foggy.<br/>"
        "<b>4. Crisis (Red)</b> &mdash; meltdown or shutdown; logic goes offline.<br/>"
        "<b>5. Recovery</b> &mdash; drained, fragile, easily re-triggered for hours.",
        PANEL_B),
]
right_col = [
    Paragraph("Two ways it can show up", PANEL_H),
    Paragraph(
        "<b>Meltdown</b> &mdash; an outward overflow: irritation, raised voice, tears, "
        "pacing, needing to escape, intense reaction to a &ldquo;small&rdquo; trigger.<br/><br/>"
        "<b>Shutdown</b> &mdash; an inward collapse: going quiet and flat, words won&rsquo;t form, "
        "withdrawing, staring, feeling numb or far away. Easy to mistake for &ldquo;being fine&rdquo; "
        "or just tired &mdash; it isn&rsquo;t.",
        PANEL_B),
]
colw = (FRAME_W - 6) / 2
two = Table([[left_col, right_col]], colWidths=[colw, colw])
two.setStyle(TableStyle([
    ("BACKGROUND", (0, 0), (0, 0), SOFT),
    ("BACKGROUND", (1, 0), (1, 0), SOFT2),
    ("VALIGN", (0, 0), (-1, -1), "TOP"),
    ("LEFTPADDING", (0, 0), (-1, -1), 9),
    ("RIGHTPADDING", (0, 0), (-1, -1), 9),
    ("TOPPADDING", (0, 0), (-1, -1), 7),
    ("BOTTOMPADDING", (0, 0), (-1, -1), 7),
    ("LINEBEFORE", (0, 0), (0, 0), 3, ACCENT),
    ("LINEBEFORE", (1, 0), (1, 0), 3, HexColor("#c79a4b")),
]))
story.append(Spacer(1, 4))
story.append(two)

story.append(Paragraph("Early signs you may be missing", H1))
story.append(Paragraph(
    "Because the emotion itself can be hard to feel, it often helps to watch for "
    "<b>body and behaviour clues</b> instead. These tend to appear <i>before</i> you consciously feel upset:",
    BODY))
sign_l = bullets([
    "Tight jaw, shoulders, chest; clenched hands; shallow breathing",
    "Sudden need for silence, dark, or to be alone",
    "Sounds, light, textures or touch feel &ldquo;too loud&rdquo; all at once",
    "Re-reading the same line; thoughts feel sticky or slow",
])
sign_r = bullets([
    "Snapping at people you care about over something minor",
    "Stimming more (rocking, tapping, pacing) or going very still",
    "A flat &ldquo;I just need to go&rdquo; urge with no clear reason",
    "Appetite, words, or humour quietly switching off",
])
signs = Table([[sign_l, sign_r]], colWidths=[colw, colw])
signs.setStyle(TableStyle([
    ("VALIGN", (0, 0), (-1, -1), "TOP"),
    ("LEFTPADDING", (0, 0), (-1, -1), 0),
    ("RIGHTPADDING", (0, 0), (-1, -1), 6),
]))
story.append(signs)

story.append(Spacer(1, 4))
story.append(panel(
    "One sentence to keep",
    [Paragraph(
        "&ldquo;If my body feels tense, my patience is thin, or the world feels too loud &mdash; "
        "I may be dysregulating, even if I can&rsquo;t feel the emotion yet. That is my cue to act early, "
        "not to push harder.&rdquo;", QUOTE)],
    bg=SOFT, width=FRAME_W))

# ===================== PAGE 2 =====================
from reportlab.platypus import PageBreak
story.append(PageBreak())

story.append(Paragraph("Handling It — What Actually Helps", H_TITLE))
story.append(Paragraph(
    "Three timeframes: catch it early, ride out the peak, and recover well. "
    "Pick one or two ideas from each and make them your defaults.", H_SUB))
story.append(HRFlowable(width="100%", thickness=1, color=LINE, spaceBefore=2, spaceAfter=6))

# In the moment
story.append(Paragraph("In the moment (Amber / Orange)", H1))
story.append(Paragraph(
    "The goal is not to &ldquo;calm down and carry on&rdquo; &mdash; it is to <b>lower the load</b> "
    "so your nervous system has less to process.", BODY))
story.append(bullets([
    "<b>Reduce input first.</b> Leave the room, dim lights, headphones / earplugs, sunglasses, fewer people. Sensory load is usually the real driver.",
    "<b>Slow the breath out.</b> Breathe in for 4, out for 6&ndash;8. A longer exhale tells the body it is safe.",
    "<b>Use your body.</b> Press feet into the floor, push against a wall, hold something cold or weighted, walk. Movement discharges the build-up.",
    "<b>Drop demands.</b> Postpone the decision, conversation or task. &ldquo;I&rsquo;ll come back to this in 20 minutes&rdquo; is a complete strategy.",
    "<b>One channel only.</b> Pick a single, low-demand input (a known song, a repetitive task, a special interest) instead of many.",
]))

# Prevention
story.append(Paragraph("Prevention (building your Green baseline)", H1))
story.append(bullets([
    "<b>Budget your energy.</b> Masking, socialising and noise all cost &ldquo;spoons.&rdquo; Plan recovery time <i>before</i> you run out, not after.",
    "<b>Protect the basics.</b> Sleep, food, hydration and unstructured downtime are regulation, not luxuries.",
    "<b>Schedule decompression.</b> Quiet gaps after meetings, shops, or social events &mdash; deliberately, in the calendar.",
    "<b>Know your triggers.</b> Track what precedes hard days (noise, heat, change of plan, hunger, too many transitions) so you can see patterns.",
]))

# Recovery panel + Self-awareness panel side by side
rec = [
    Paragraph(
        "After a meltdown or shutdown you are <b>depleted and easily re-triggered</b>. Treat it like "
        "recovering from illness:", PANEL_B),
    Spacer(1, 3),
    bullets([
        "Low light, low noise, low demand for a while",
        "No big decisions or hard talks yet",
        "Gentle, familiar comfort &mdash; food, routine, a special interest",
        "Self-kindness: it was overload, not bad character",
    ], st=PANEL_B, leftpad=8),
]
aware = [
    Paragraph(
        "Build the awareness you can&rsquo;t always feel in real time:", PANEL_B),
    Spacer(1, 3),
    bullets([
        "Keep a simple log: <i>energy / sleep / what happened</i>",
        "Rate yourself Green&ndash;Amber&ndash;Orange&ndash;Red a few times a day",
        "Set phone check-ins: &ldquo;How is my body right now?&rdquo;",
        "Ask a trusted person to gently flag when you seem &lsquo;off&rsquo;",
    ], st=PANEL_B, leftpad=8),
]
recw = (FRAME_W - 6) / 2
rec_tbl = Table([[
    [Paragraph("Recovering afterwards", PANEL_H)] + rec,
    [Paragraph("Noticing it sooner", PANEL_H)] + aware,
]], colWidths=[recw, recw])
rec_tbl.setStyle(TableStyle([
    ("BACKGROUND", (0, 0), (0, 0), SOFT2),
    ("BACKGROUND", (1, 0), (1, 0), SOFT),
    ("VALIGN", (0, 0), (-1, -1), "TOP"),
    ("LEFTPADDING", (0, 0), (-1, -1), 9),
    ("RIGHTPADDING", (0, 0), (-1, -1), 9),
    ("TOPPADDING", (0, 0), (-1, -1), 7),
    ("BOTTOMPADDING", (0, 0), (-1, -1), 7),
    ("LINEBEFORE", (0, 0), (0, 0), 3, HexColor("#c79a4b")),
    ("LINEBEFORE", (1, 0), (1, 0), 3, ACCENT),
]))
story.append(Spacer(1, 2))
story.append(rec_tbl)

# Telling others + when to seek help
story.append(Paragraph("Telling the people around you", H1))
story.append(Paragraph(
    "You don&rsquo;t have to explain in the moment &mdash; agree a signal in advance. For example: "
    "<i>&ldquo;If I say I need a reset, it means I&rsquo;m overloaded and will be back in 20 minutes &mdash; "
    "it isn&rsquo;t about you.&rdquo;</i> Naming it ahead of time removes the pressure to perform calm.",
    BODY))

story.append(Spacer(1, 3))
story.append(panel(
    "When to reach for more support",
    [Paragraph(
        "If meltdowns or shutdowns are frequent, getting more intense, affecting work or relationships, "
        "or you notice thoughts of not wanting to be here &mdash; please talk to your GP or an autism-informed "
        "therapist (OT for sensory and interoception work, or approaches like CBT/DBT adapted for autistic "
        "adults). Needing support is sensible maintenance, not weakness. In a crisis in the UK, call <b>111</b>, "
        "or <b>Samaritans on 116 123</b>, any time.", PANEL_B)],
    bg=SOFT, width=FRAME_W))

doc.build(story)
print("PDF written.")
