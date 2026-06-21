#!/usr/bin/env python3
"""Generate a one-page minimalist PDF on staying cool in hot weather."""
from fpdf import FPDF

RED = (192, 57, 43)
GREY = (119, 119, 119)
DARK = (26, 26, 26)
LINE = (224, 224, 224)
TABLE_BG = (247, 247, 247)
TABLE_BORDER = (221, 221, 221)

pdf = FPDF(format="A4", unit="mm")
pdf.set_margins(18, 16, 18)
pdf.set_auto_page_break(auto=True, margin=16)
pdf.add_page()
W = pdf.w - pdf.l_margin - pdf.r_margin


def rule():
    pdf.ln(2)
    pdf.set_draw_color(*LINE)
    pdf.set_line_width(0.2)
    y = pdf.get_y()
    pdf.line(pdf.l_margin, y, pdf.w - pdf.r_margin, y)
    pdf.ln(3)


def heading(text):
    pdf.ln(2)
    pdf.set_font("Helvetica", "B", 11)
    pdf.set_text_color(*RED)
    pdf.cell(0, 6, text.upper(), new_x="LMARGIN", new_y="NEXT")
    pdf.ln(1)


# Title
pdf.set_font("Helvetica", "", 26)
pdf.set_text_color(*DARK)
pdf.cell(0, 12, "Staying Cool in Hot Weather", new_x="LMARGIN", new_y="NEXT")
pdf.set_font("Helvetica", "I", 11)
pdf.set_text_color(*GREY)
pdf.cell(0, 6, "Why it matters - and what to do about it.", new_x="LMARGIN", new_y="NEXT")
rule()

# Why It Matters
heading("Why It Matters")
pdf.set_font("Helvetica", "", 11)
pdf.set_text_color(*DARK)
pdf.multi_cell(
    0, 5.5,
    "Heat is the deadliest weather hazard. When the body can't shed heat fast "
    "enough, its core temperature climbs - straining the heart, kidneys, and "
    "brain. The risk is silent and fast: heat exhaustion can tip into "
    "life-threatening heatstroke within minutes.",
)
pdf.ln(1)
pdf.set_font("Helvetica", "B", 11)
pdf.multi_cell(0, 5.5, "Staying cool isn't comfort. It's protection.")

# Warning Signs table
heading("Know the Warning Signs")
col = W / 2
row_h = 7
# header row
pdf.set_font("Helvetica", "B", 10)
pdf.set_fill_color(*TABLE_BG)
pdf.set_draw_color(*TABLE_BORDER)
pdf.set_line_width(0.2)
x0 = pdf.get_x()
y0 = pdf.get_y()
pdf.set_text_color(*DARK)
pdf.cell(col, row_h, "  Heat Exhaustion", border=1, fill=True)
pdf.set_text_color(*RED)
pdf.cell(col, row_h, "  Heatstroke (emergency)", border=1, fill=True,
         new_x="LMARGIN", new_y="NEXT")
rows = [
    ("Heavy sweating", "Hot, dry skin or heavy sweating"),
    ("Cool, clammy skin", "Confusion, slurred speech"),
    ("Dizziness, weakness", "Body temp 40C / 104F+"),
    ("Nausea, headache", "Fainting, seizures"),
]
pdf.set_font("Helvetica", "", 10)
pdf.set_text_color(*DARK)
for left, right in rows:
    pdf.cell(col, row_h, "  " + left, border=1)
    pdf.cell(col, row_h, "  " + right, border=1, new_x="LMARGIN", new_y="NEXT")
pdf.ln(2)
pdf.set_font("Helvetica", "B", 10.5)
pdf.set_text_color(*RED)
pdf.multi_cell(0, 5.5, "Heatstroke is a medical emergency - call for help immediately.")

# Stay Cool, Stay Safe
heading("Stay Cool, Stay Safe")
tips = [
    ("Hydrate", "drink water before you feel thirsty; skip alcohol and sugary drinks."),
    ("Seek shade & AC", "stay indoors during the hottest hours (11am-4pm)."),
    ("Dress light", "loose, pale, breathable clothing; a hat outdoors."),
    ("Slow down", "postpone hard activity to early morning or evening."),
    ("Cool the body", "cool showers, damp cloths, fans, feet in cool water."),
    ("Never leave", "children or pets in a parked car - ever."),
]
for bold, rest in tips:
    pdf.set_x(pdf.l_margin)
    pdf.set_font("Helvetica", "B", 10.5)
    pdf.set_text_color(*RED)
    pdf.cell(4, 5.5, "-")
    pdf.set_text_color(*DARK)
    pdf.cell(pdf.get_string_width(bold) + 1, 5.5, bold)
    pdf.set_font("Helvetica", "", 10.5)
    pdf.multi_cell(0, 5.5, "- " + rest)

# Check on Others
heading("Check on Others")
pdf.set_font("Helvetica", "", 11)
pdf.set_text_color(*DARK)
pdf.multi_cell(
    0, 5.5,
    "The most vulnerable are the elderly, the very young, people who are ill or "
    "isolated, and outdoor workers. A two-minute phone call or visit can save a life.",
)

# Closing quote
pdf.ln(4)
y = pdf.get_y()
pdf.set_draw_color(*RED)
pdf.set_line_width(0.8)
pdf.line(pdf.l_margin, y, pdf.l_margin, y + 8)
pdf.set_x(pdf.l_margin + 4)
pdf.set_font("Helvetica", "I", 13)
pdf.set_text_color(51, 51, 51)
pdf.cell(0, 8, "Cool body. Clear mind. Stay safe.")

pdf.output("/home/user/Test/staying-cool-in-hot-weather.pdf")
print("PDF written")
