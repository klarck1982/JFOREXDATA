#!/usr/bin/env python3
"""Generate jforex/docs/htf-fvg-v2-mockup.svg — side-by-side V1 (as implemented)
vs V2 (proposed cleaner aesthetics) for the HTF FVG + PDH/PDL features.
Deterministic (seed 42). Same base candles in both panes so the difference is
purely visual styling."""
import random

# ---------------- geometry ----------------
W, H = 1344, 700
BG, GRID = "#1e222d", "#262b36"
TXT, TXT2 = "#d1d4dc", "#787b86"
BULL, BEAR = "#26a69a", "#ef5350"
GRAY = "#9aa0a6"
PDH_C, PDL_C = "#ff9800", "#29b6f6"

P0, P1 = 1.0900, 1.1065
Y0, Y1 = 96, 556
def y(p): return Y1 - (p - P0) / (P1 - P0) * (Y1 - Y0)

# panes
PX1, PW = 16, 640          # pane 1 (V1)
PX2 = PX1 + PW + 32        # pane 2 (V2)

# base chart: 22 candles, slot 16 (cW 9 + sW 7)
cW, sW, slot = 9, 7, 16
N = 22
def chart_x0(px): return px + 10
DAY_IDX = 15

rnd = random.Random(42)
closes = [1.0998]
for i in range(N - 1):
    closes.append(closes[-1] + rnd.uniform(-0.0011, 0.00115))
candles = []
c = closes[0]
for cl in closes:
    o = c
    hi = max(o, cl) + rnd.uniform(0.0001, 0.00055)
    lo = min(o, cl) - rnd.uniform(0.0001, 0.00055)
    candles.append((o, hi, lo, cl))
    c = cl

# upper-layer cluster candles (same as v1 mockup)
H4 = [(1.0975,1.0998,1.0962,1.0990),(1.0990,1.1002,1.0978,1.0984),
      (1.0984,1.0996,1.0966,1.0972),(1.0972,1.0990,1.0958,1.0987),
      (1.0987,1.1006,1.0974,1.0999),(1.0999,1.1012,1.0988,1.1004)]
DD = [(1.0990,1.1010,1.0968,1.0975),(1.0975,1.0995,1.0952,1.0988),
      (1.0988,1.1018,1.0970,1.1002)]

# zones (priceLo, priceHi, base-formation-idx, col0, col1, kind)
ZB = (1.0966, 1.0990, 6,  1, 5, "bull")      # 4H active
ZS = (1.0984, 1.1002, 13, 3, 5, "bear")      # 4H filled
ZD = (1.0975, 1.1000, 9,  1, 2, "bullD")     # D active
PDH, PDL = 1.1042, 1.0988

COLW = 17
BODY_OLD, BODY_NEW = cW, min(2 * cW, COLW - 2)   # 9 -> 16

s = []
A = s.append
texts = []   # (x, y, anchor, fs, nchars) for overflow post-check

def text(x, yv, t, fs, fill=TXT2, anchor="start", bold=False, opacity=None):
    t = t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    st = ' font-weight="bold"' if bold else ""
    op = f' opacity="{opacity}"' if opacity is not None else ""
    A(f'<text x="{x:.1f}" y="{yv:.1f}" font-size="{fs}" fill="{fill}" text-anchor="{anchor}"{st}{op}>{t}</text>')
    texts.append((x, yv, anchor, fs, len(t)))

# ---------------- shared base scene ----------------
def base_scene(px, v2):
    x0 = chart_x0(px)
    # grid
    gp = 1.0910
    while gp <= P1:
        yy = y(gp)
        A(f'<line x1="{x0}" y1="{yy:.1f}" x2="{px+PW-8}" y2="{yy:.1f}" stroke="{GRID}" stroke-width="1"/>')
        text(px + PW - 10, yy + 3, f"{gp:.4f}", 9, TXT2, "end")
        gp += 0.0020
    # candles
    for i, (o, h, l, ccl) in enumerate(candles):
        x = x0 + i * slot + sW / 2
        col = BULL if ccl >= o else BEAR
        A(f'<line x1="{x:.1f}" y1="{y(h):.1f}" x2="{x:.1f}" y2="{y(l):.1f}" stroke="{col}" stroke-width="1.3"/>')
        top, bot = y(max(o, ccl)), y(min(o, ccl))
        A(f'<rect x="{x-cW/2:.1f}" y="{top:.1f}" width="{cW}" height="{max(1,bot-top):.1f}" fill="{col}"/>')
    # model-day boundary
    dbx = x0 + DAY_IDX * slot + sW / 2
    A(f'<line x1="{dbx:.1f}" y1="{Y0}" x2="{dbx:.1f}" y2="{Y1}" stroke="{TXT2}" stroke-width="1" stroke-dasharray="2,4"/>')
    text(dbx - 4, Y0 + 11, "D day", 9, TXT2, "end")
    # clusters: 4H then D, x2 bodies + ghost on first 4H candle
    ox = x0 + N * slot + 14
    dx = ox + 6 * COLW + 12
    text(ox - 4, Y0 + 2, "4H", 10, TXT, "start", bold=True)
    for i, (o, h, l, ccl) in enumerate(H4):
        x = ox + i * COLW + COLW / 2
        col = BULL if ccl >= o else BEAR
        top, bot = y(max(o, ccl)), y(min(o, ccl))
        A(f'<line x1="{x:.1f}" y1="{y(h):.1f}" x2="{x:.1f}" y2="{y(l):.1f}" stroke="{col}" stroke-width="1.5"/>')
        if i == 0:
            A(f'<rect x="{x-BODY_OLD/2:.1f}" y="{top-2:.1f}" width="{BODY_OLD}" height="{max(2,bot-top+4):.1f}" fill="none" stroke="{TXT2}" stroke-width="1" stroke-dasharray="2,2"/>')
        A(f'<rect x="{x-BODY_NEW/2:.1f}" y="{top:.1f}" width="{BODY_NEW}" height="{max(1,bot-top):.1f}" fill="{col}"/>')
    text(dx - 4, Y0 + 2, "D", 10, TXT, "start", bold=True)
    for i, (o, h, l, ccl) in enumerate(DD):
        x = dx + i * COLW + COLW / 2
        col = BULL if ccl >= o else BEAR
        top, bot = y(max(o, ccl)), y(min(o, ccl))
        A(f'<line x1="{x:.1f}" y1="{y(h):.1f}" x2="{x:.1f}" y2="{y(l):.1f}" stroke="{col}" stroke-width="1.5"/>')
        A(f'<rect x="{x-BODY_NEW/2:.1f}" y="{top:.1f}" width="{BODY_NEW}" height="{max(1,bot-top):.1f}" fill="{col}"/>')
    text(ox, Y0 + 22, "body x2 (old dashed ghost)", 9, TXT2)
    return ox, dx

def fmt(v): return f"{v:.4f}"

# ---------------- V1 styles (as implemented) ----------------
def v1_column_band(ox, c0, c1, zlo, zhi, kind):
    y1, y2 = y(zhi), y(zlo)
    active = kind != "bear"
    rgb = (38, 166, 154) if kind.startswith("bull") else (239, 83, 80)
    colr = ("#26a69a" if kind.startswith("bull") else "#ef5350") if active else GRAY
    x0 = ox + c0 * COLW + 2
    x1 = ox + (c1 + 1) * COLW - 2
    A(f'<rect x="{x0:.1f}" y="{y2:.1f}" width="{x1-x0:.1f}" height="{y1-y2:.1f}" fill="rgba({rgb},0.22)" stroke="{colr}" stroke-width="1" opacity="{1 if active else 0.55}"/>')
    text((x0 + x1) / 2, (y1 + y2) / 2 + 3, "FVG" + ("" if active else " filled"), 9, colr, "middle", opacity=1 if active else 0.6)

def v1_chart_band(px, zidx, zlo, zhi, kind):
    y1, y2 = y(zhi), y(zlo)
    active = kind != "bear"
    rgb = (38, 166, 154) if kind.startswith("bull") else (140, 140, 140)
    colr = "#26a69a" if kind.startswith("bull") else GRAY
    x0 = chart_x0(px) + zidx * slot + sW / 2
    A(f'<rect x="{x0:.1f}" y="{y2:.1f}" width="{px+PW-14-x0:.1f}" height="{y1-y2:.1f}" fill="rgba({rgb},0.12)" opacity="{1 if active else 0.5}"/>')
    A(f'<line x1="{x0:.1f}" y1="{y1:.1f}" x2="{px+PW-14}" y2="{y1:.1f}" stroke="{colr}" stroke-width="1" stroke-dasharray="5,4" opacity="{0.9 if active else 0.5}"/>')
    A(f'<line x1="{x0:.1f}" y1="{y2:.1f}" x2="{px+PW-14}" y2="{y2:.1f}" stroke="{colr}" stroke-width="1" stroke-dasharray="5,4" opacity="{0.9 if active else 0.5}"/>')
    text(x0 + 6, y1 + 11, "FVG 4H" if kind != "bullD" else "FVG D", 9, colr, "start", opacity=1 if active else 0.6)

def v1_pdhpx(px, val, colr, name):
    yy = y(val)
    dbx = chart_x0(px) + DAY_IDX * slot + sW / 2
    A(f'<line x1="{dbx:.1f}" y1="{yy:.1f}" x2="{px+PW-14}" y2="{yy:.1f}" stroke="{colr}" stroke-width="1.6" stroke-dasharray="7,5"/>')
    A(f'<rect x="{dbx+2:.1f}" y="{yy-19:.1f}" width="92" height="15" rx="3" fill="#0d1b2a" stroke="{colr}" stroke-width="0.8"/>')
    text(dbx + 8, yy - 7.5, f"{name} {fmt(val)}", 8, colr, "start", bold=True)

# ---------------- V2 styles (proposed) ----------------
def v2_column_band(ox, c0, c1, zlo, zhi, kind):
    y1, y2 = y(zhi), y(zlo)
    active = kind != "bear"
    rgb = (38, 166, 154) if kind.startswith("bull") else (140, 140, 140)
    x0 = ox + c0 * COLW + 2
    x1 = ox + (c1 + 1) * COLW - 2
    # FINAL v2 (user: no borders): pure soft fill, no lines, no inner text
    A(f'<rect x="{x0:.1f}" y="{y2:.1f}" width="{x1-x0:.1f}" height="{y1-y2:.1f}" fill="rgba({rgb},{0.16 if active else 0.06})"/>')

def v2_chart_band(px, gid, zidx, zlo, zhi, kind):
    y1, y2 = y(zhi), y(zlo)
    x0 = chart_x0(px) + zidx * slot + sW / 2
    right = px + PW - 14
    if kind == "bear":  # filled: whisper, no borders, no label
        A(f'<rect x="{x0:.1f}" y="{y2:.1f}" width="{right-x0:.1f}" height="{y1-y2:.1f}" fill="rgba(140,140,140,0.05)"/>')
        return
    colhex = "#26a69a" if kind.startswith("bull") else "#ef5350"
    A(f'<rect x="{x0:.1f}" y="{y2:.1f}" width="{right-x0:.1f}" height="{y1-y2:.1f}" fill="url(#{gid})"/>')
    # FINAL v2 (user: no borders): gradient fill only, no edge lines
    lab = "FVG 4H" if kind != "bullD" else "FVG D"
    text(right - 2, (y1 + y2) / 2 + 3, lab, 8, colhex, "end", opacity=0.7)

def v2_pdhpx(px, val, colr, name):
    yy = y(val)
    dbx = chart_x0(px) + DAY_IDX * slot + sW / 2
    A(f'<line x1="{dbx:.1f}" y1="{yy:.1f}" x2="{px+PW-14}" y2="{yy:.1f}" stroke="{colr}" stroke-width="1" opacity="0.8"/>')
    # right-edge price tag (TradingView-style): colored fill, dark text
    tw, th = 74, 14
    A(f'<rect x="{px+PW-14-tw:.1f}" y="{yy-th/2:.1f}" width="{tw}" height="{th}" rx="3" fill="{colr}"/>')
    text(px + PW - 14 - tw / 2, yy + 3, f"{name} {fmt(val)}", 8, "#10141c", "middle", bold=True)

# ---------------- compose ----------------
A(f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}" font-family="Consolas,Menlo,monospace">')
A(f'<defs>'
  f'<linearGradient id="gb" x1="0" y1="0" x2="1" y2="0">'
  f'<stop offset="0" stop-color="rgba(38,166,154,0.16)"/>'
  f'<stop offset="1" stop-color="rgba(38,166,154,0.03)"/>'
  f'</linearGradient>'
  f'<linearGradient id="gr" x1="0" y1="0" x2="1" y2="0">'
  f'<stop offset="0" stop-color="rgba(239,83,80,0.16)"/>'
  f'<stop offset="1" stop-color="rgba(239,83,80,0.03)"/>'
  f'</linearGradient>'
  f'</defs>')
A(f'<rect width="{W}" height="{H}" fill="{BG}"/>')
text(20, 30, "TTFMEssence HTF — visual v2: softer, less clutter (V1 implemented  vs  V2 proposed)", 17, TXT, "start", bold=True)
text(20, 50, "same candles, same zones, same x2 — only the styling changes", 12, TXT2)
text(560, 50, "V1 — as implemented", 12, BULL, "start", bold=True)
text(PX2 + 560, 50, "V2 — proposed", 12, "#42a5f5", "start", bold=True)

# pane frames
A(f'<rect x="{PX1}" y="{Y0-30}" width="{PW}" height="{Y1-Y0+74}" fill="none" stroke="{GRID}" stroke-width="1"/>')
A(f'<rect x="{PX2}" y="{Y0-30}" width="{PW}" height="{Y1-Y0+74}" fill="none" stroke="{GRID}" stroke-width="1"/>')

# ---- V1 pane ----
ox1, dx1 = base_scene(PX1, False)
v1_pdhpx(PX1, PDH, PDH_C, "PDH")
v1_pdhpx(PX1, PDL, PDL_C, "PDL")
v1_chart_band(PX1, ZB[2], ZB[0], ZB[1], ZB[5])
v1_chart_band(PX1, ZS[2], ZS[0], ZS[1], ZS[5])
v1_column_band(ox1, ZB[3], ZB[4], ZB[0], ZB[1], ZB[5])
v1_column_band(ox1, ZS[3], ZS[4], ZS[0], ZS[1], ZS[5])
v1_column_band(dx1, ZD[3], ZD[4], ZD[0], ZD[1], ZD[5])

# ---- V2 pane ----
ox2, dx2 = base_scene(PX2, True)
v2_pdhpx(PX2, PDH, PDH_C, "PDH")
v2_pdhpx(PX2, PDL, PDL_C, "PDL")
v2_chart_band(PX2, "gb", ZB[2], ZB[0], ZB[1], ZB[5])
v2_chart_band(PX2, "gr", ZS[2], ZS[0], ZS[1], ZS[5])
v2_column_band(ox2, ZB[3], ZB[4], ZB[0], ZB[1], ZB[5])
v2_column_band(ox2, ZS[3], ZS[4], ZS[0], ZS[1], ZS[5])
v2_column_band(dx2, ZD[3], ZD[4], ZD[0], ZD[1], ZD[5])

# arrow between panes
ax = PX1 + PW + 12
A(f'<line x1="{ax-8}" y1="{(Y0+Y1)/2}" x2="{ax+8}" y2="{(Y0+Y1)/2}" stroke="#42a5f5" stroke-width="2"/>')
A(f'<polygon points="{ax+8},{(Y0+Y1)/2-5} {ax+16},{(Y0+Y1)/2} {ax+8},{(Y0+Y1)/2+5}" fill="#42a5f5"/>')

# legend
ly = Y1 + 58
text(20, ly, "v2 FINAL (user: no borders at all): (1) chart FVG = gradient fade left->right, pure fill, NO lines, right-edge label on ACTIVE zones only", 11, TXT2)
text(20, ly + 17, "(2) column FVG = soft lane 16% fill only, no borders, no inner text    (3) PDH/PDL = SOLID 1px line (no dashes) + colored price tag at right edge (auto-shift if close)", 11, TXT2)
text(20, ly + 34, "(4) anti-clutter cap: chart extension shows the LAST 4 zones per layer (columns keep all, up to 6)    (5) filled zones = whisper gray 5%, no label", 11, TXT2)

A('</svg>')

# ---------------- overflow post-check ----------------
viol = 0
for (x, yv, anchor, fs, n) in texts:
    w = 0.62 * fs * n
    lo, hi = (x - w, x) if anchor in ("end", "middle") and anchor == "end" else ((x - w/2, x + w/2) if anchor == "middle" else (x, x + w))
    if lo < -2 or hi > W + 2 or yv < 10 or yv > H + 2:
        viol += 1
        print("OVERFLOW:", round(x,1), round(yv,1), anchor, fs, n)
out = "/home/user/JFOREXDATA/jforex/docs/htf-fvg-v2-mockup.svg"
open(out, "w", encoding="utf-8").write("\n".join(s))
print("wrote", out, len(s), "elements; overflow violations:", viol)
