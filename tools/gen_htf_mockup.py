#!/usr/bin/env python3
"""Generate jforex/docs/htf-fvg-pdh-mockup.svg — mockup of the three proposed
HTF features for TTFMEssence: (1) FVG drawn on upper layers only, (2) x2 candle
width on upper layers, (3) PDH/PDL lines. Deterministic (seeded)."""
import random, math

W, H = 1000, 640
BG, GRID, AXIS = "#1e222d", "#262b36", "#454c5c"
TXT, TXT2 = "#d1d4dc", "#787b86"
BULL, BEAR = "#26a69a", "#ef5350"
PDH_C, PDL_C = "#ff9800", "#29b6f6"
FVG_BULL, FVG_BEAR, FVG_FILL = "76,166,154", "239,83,80", "140,140,140"

# geometry: chart candles cW=10 sW=8 -> slot=18 ; 33 candles in 594px
cW, sW, slot = 10, 8, 18
CHART_X0, CHART_W = 64, 594
PRICE_X1 = CHART_X0 + CHART_W            # 658
P0, P1 = 1.0900, 1.1065                   # price range
Y0, Y1 = 96, 560                          # plot top/bottom
def y(p): return Y1 - (p - P0) / (P1 - P0) * (Y1 - Y0)

rnd = random.Random(42)
# base candles: random walk
closes = [1.1005]
for i in range(32):
    closes.append(closes[-1] + rnd.uniform(-0.0011, 0.00115))
candles = []
c = closes[0]
for i, cl in enumerate(closes):
    o = c
    hi = max(o, cl) + rnd.uniform(0.0001, 0.00055)
    lo = min(o, cl) - rnd.uniform(0.0001, 0.00055)
    candles.append((o, hi, lo, cl))
    c = cl
DAY_START = 22          # index of current model-day start (D boundary)

# upper-layer cluster candles (4H x6, D x3) — hand-tuned so FVGs land right
def mk(o,h,l,cl): return (o,h,l,cl)
H4 = [mk(1.0975,1.0998,1.0962,1.0990), mk(1.0990,1.1002,1.0978,1.0984),
      mk(1.0984,1.0996,1.0966,1.0972), mk(1.0972,1.0990,1.0958,1.0987),
      mk(1.0987,1.1006,1.0974,1.0999), mk(1.0999,1.1012,1.0988,1.1004)]
DD = [mk(1.0990,1.1010,1.0968,1.0975), mk(1.0975,1.0995,1.0952,1.0988),
      mk(1.0988,1.1018,1.0970,1.1002)]
# FVGs on 4H: bull gap [H4[1].h=1.1002? no] use: c0.h=1.0998 < c2.l=1.0966? no.
# Define explicit zone rects (price pairs) + column span for the picture:
fvg4 = [(1.0966, 1.0990, 1, 5, FVG_BULL, 1),   # bull, formed col1 -> right edge, ACTIVE
        (1.0984, 1.1002, 3, 5, FVG_BEAR, 0),   # bear, formed col3, FILLED (gray)
       ]
fvgD = [(1.0975, 1.1000, 1, 2, FVG_BULL, 1)]   # bull on D layer, active
PDH, PDL = 1.1042, 0.9988 if False else 1.0988  # previous D candle hi/lo

s = []
A = s.append
A(f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}" font-family="Consolas,Menlo,monospace">')
A(f'<rect width="{W}" height="{H}" fill="{BG}"/>')
# title
A(f'<text x="20" y="30" font-size="17" fill="{TXT}" font-weight="bold">TTFMEssence — proposed HTF features (mockup, not to scale)</text>')
A(f'<text x="20" y="50" font-size="12" fill="{TXT2}">(1) FVG on upper layers (always) + optional on chart candles   (2) x2 candle width on upper layers   (3) PDH / PDL lines</text>')

# price grid + labels
gp = 1.0910
while gp <= P1:
    yy = y(gp)
    A(f'<line x1="{CHART_X0}" y1="{yy:.1f}" x2="{W-16}" y2="{yy:.1f}" stroke="{GRID}" stroke-width="1"/>')
    A(f'<text x="{W-14}" y="{yy+4:.1f}" font-size="11" fill="{TXT2}" text-anchor="end">{gp:.4f}</text>')
    gp += 0.0020

# base chart candles
for i,(o,h,l,c) in enumerate(candles):
    x = CHART_X0 + i*slot + sW/2
    col = BULL if c>=o else BEAR
    A(f'<line x1="{x:.1f}" y1="{y(h):.1f}" x2="{x:.1f}" y2="{y(l):.1f}" stroke="{col}" stroke-width="1.4"/>')
    top, bot = y(max(o,c)), y(min(o,c))
    A(f'<rect x="{x-cW/2:.1f}" y="{top:.1f}" width="{cW}" height="{max(1,bot-top):.1f}" fill="{col}"/>')
# D-day boundary (model day start) on base chart
dbx = CHART_X0 + DAY_START*slot + sW/2
A(f'<line x1="{dbx:.1f}" y1="{Y0}" x2="{dbx:.1f}" y2="{Y1}" stroke="{TXT2}" stroke-width="1" stroke-dasharray="2,4"/>')
A(f'<text x="{dbx-4}" y="{Y0+12}" font-size="10" fill="{TXT2}" text-anchor="end">model day (D) start</text>')

# ---------- (3) PDH / PDL : dashed lines from day start to right edge ----------
for val, colr, tag in ((PDH, PDH_C, "PDH 1.1042"), (PDL, PDL_C, "PDL 1.0988")):
    yy = y(val)
    A(f'<line x1="{dbx:.1f}" y1="{yy:.1f}" x2="{W-16}" y2="{yy:.1f}" stroke="{colr}" stroke-width="1.6" stroke-dasharray="7,5"/>')
    A(f'<rect x="{W-118}" y="{yy-20:.1f}" width="100" height="16" rx="3" fill="{colr}" opacity="0.9"/>')
    A(f'<text x="{W-68}" y="{yy-8:.1f}" font-size="11" fill="#10141c" font-weight="bold" text-anchor="middle">{tag}</text>')

# ---------- overlay cluster columns (upper layers), right side ----------
OY0 = Y0 + 14
ox = PRICE_X1 + 34
colw = slot  # 18
# 4H cluster: 6 columns ; body NEW width = min(2*cW, colw-2) = 16  (old = 10)
BODY_OLD, BODY_NEW = 10, min(2*cW, colw-2)
A(f'<text x="{ox-8}" y="{OY0-6}" font-size="12" fill="{TXT}" font-weight="bold">4H</text>')
for i,(o,h,l,c) in enumerate(H4):
    x = ox + i*colw + colw/2
    col = BULL if c>=o else BEAR
    A(f'<line x1="{x:.1f}" y1="{y(h):.1f}" x2="{x:.1f}" y2="{y(l):.1f}" stroke="{col}" stroke-width="1.6"/>')
    top, bot = y(max(o,c)), y(min(o,c))
    # ghost old width + new width on first candle to show x2
    if i == 0:
        A(f'<rect x="{x-BODY_OLD/2:.1f}" y="{top-2:.1f}" width="{BODY_OLD}" height="{max(2,bot-top+4):.1f}" fill="none" stroke="{TXT2}" stroke-width="1" stroke-dasharray="2,2"/>')
    A(f'<rect x="{x-BODY_NEW/2:.1f}" y="{top:.1f}" width="{BODY_NEW}" height="{max(1,bot-top):.1f}" fill="{col}"/>')
A(f'<text x="{ox+0}" y="{OY0+24}" font-size="10" fill="{TXT2}">old 10px (dashed ghost) -> new 16px (clamped to column)</text>')

dx = ox + 6*colw + 14
A(f'<text x="{dx-8}" y="{OY0-6}" font-size="12" fill="{TXT}" font-weight="bold">D</text>')
for i,(o,h,l,c) in enumerate(DD):
    x = dx + i*colw + colw/2
    col = BULL if c>=o else BEAR
    A(f'<line x1="{x:.1f}" y1="{y(h):.1f}" x2="{x:.1f}" y2="{y(l):.1f}" stroke="{col}" stroke-width="1.6"/>')
    top, bot = y(max(o,c)), y(min(o,c))
    A(f'<rect x="{x-BODY_NEW/2:.1f}" y="{top:.1f}" width="{BODY_NEW}" height="{max(1,bot-top):.1f}" fill="{col}"/>')

# ---------- (1) FVG bands painted ON the upper-layer columns only (ALWAYS) ----------
def band(x0, x1, pLo, pHi, rgb, active, label):
    y1, y2 = y(pHi), y(pLo)
    colr = ("#26a69a" if active else "#9aa0a6") if rgb == FVG_BULL else ("#ef5350" if active else "#9aa0a6")
    A(f'<rect x="{x0:.1f}" y="{y2:.1f}" width="{x1-x0:.1f}" height="{y1-y2:.1f}" fill="rgba({rgb},0.22)" stroke="{colr}" stroke-width="1" opacity="{1 if active else 0.55}"/>')
    A(f'<text x="{(x0+x1)/2:.1f}" y="{(y1+y2)/2+3:.1f}" font-size="10" fill="{colr}" text-anchor="middle">{label}</text>')
for (pLo, pHi, c0, c1, rgb, active) in fvg4:
    band(ox + c0*colw + 2, ox + (c1+1)*colw - 2, pLo, pHi, rgb, active, "FVG" + ("" if active else " (filled)"))
for (pLo, pHi, c0, c1, rgb, active) in fvgD:
    band(dx + c0*colw + 2, dx + (c1+1)*colw - 2, pLo, pHi, rgb, active, "FVG")

# ---------- (1b) OPTIONAL: same FVG zones extended across the base chart (toggle, default OFF) ----------
def chart_band(x0, pLo, pHi, rgb, active, label):
    y1, y2 = y(pHi), y(pLo)
    colr = ("#26a69a" if active else "#9aa0a6") if rgb == FVG_BULL else ("#ef5350" if active else "#9aa0a6")
    A(f'<rect x="{x0:.1f}" y="{y2:.1f}" width="{PRICE_X1-x0:.1f}" height="{y1-y2:.1f}" fill="rgba({rgb},0.12)" opacity="{1 if active else 0.5}"/>')
    A(f'<line x1="{x0:.1f}" y1="{y1:.1f}" x2="{PRICE_X1}" y2="{y1:.1f}" stroke="{colr}" stroke-width="1" stroke-dasharray="5,4" opacity="{0.9 if active else 0.5}"/>')
    A(f'<line x1="{x0:.1f}" y1="{y2:.1f}" x2="{PRICE_X1}" y2="{y2:.1f}" stroke="{colr}" stroke-width="1" stroke-dasharray="5,4" opacity="{0.9 if active else 0.5}"/>')
    A(f'<text x="{x0+6:.1f}" y="{y1+11:.1f}" font-size="10" fill="{colr}">{label}</text>')
# zone start times (mock): bull formed ~col-1 candle of 4H cluster, bear later
chart_band(300, 1.0966, 1.0990, FVG_BULL, 1, "FVG 4H (chart — optional)")
chart_band(430, 1.0984, 1.1002, FVG_BEAR, 0, "FVG 4H filled (chart — optional)")

A(f'<text x="{CHART_X0}" y="{Y1+18}" font-size="11" fill="{TXT}" font-weight="bold">columns: FVG always visible (no option)  |  chart candles: same zones, show/hide by option (default OFF)</text>')

# ---------- callout badges 1/2/3 ----------
def badge(x, yv, n, msg):
    A(f'<circle cx="{x}" cy="{yv}" r="11" fill="#42a5f5"/>')
    A(f'<text x="{x}" y="{yv+4}" font-size="12" fill="#fff" font-weight="bold" text-anchor="middle">{n}</text>')
    A(f'<text x="{x+16}" y="{yv+4}" font-size="12" fill="{TXT}">{msg}</text>')
badge(ox + 2*colw, y(1.1030), "1", "FVG: layers always + chart optional")
badge(ox + colw/2, y(1.0952), "2", "candle body x2 (upper layers)")
badge(dbx + 90, y(PDH)-34, "3", "PDH/PDL = prev D-candle hi/lo")

# legend
lx, ly = CHART_X0, Y1 + 34
A(f'<text x="{lx}" y="{ly}" font-size="11" fill="{TXT2}">PDH/PDL span: current model day (from D boundary) to right edge.  FVG states: colored = active, gray = filled (price closed beyond).</text>')
A(f'<text x="{lx}" y="{ly+16}" font-size="11" fill="{TXT2}">Options: [HTF] FVG on Chart Candles (default ON, toggleable) + [HTF] Show PDH/PDL (default ON).  Layers FVG = always on.  Candle x2 = direct visual change.</text>')
A('</svg>')

out = "/home/user/JFOREXDATA/jforex/docs/htf-fvg-pdh-mockup.svg"
open(out, "w", encoding="utf-8").write("\n".join(s))
print("wrote", out, len(s), "elements")
