#!/usr/bin/env python3
"""Generate jforex/docs/htf-theme-check.svg — the FINAL v2 styling (no borders)
rendered on a DARK theme and a LIGHT theme, same candles/zones/alphas, to show
how the overlays actually read on each chart color system. Deterministic (seed 42)."""
import random

BULL, BEAR = "#26a69a", "#ef5350"
PDH_C, PDL_C = "#ff9800", "#29b6f6"

P0, P1 = 1.0900, 1.1065
Y0, Y1 = 110, 560
def y(p): return Y1 - (p - P0) / (P1 - P0) * (Y1 - Y0)

PW, GAP = 620, 40
W = 32 + PW * 2 + GAP
H = 700
cW, sW, slot = 9, 7, 16
N = 22
DAY_IDX = 15
COLW = 17
BODY_NEW = min(2 * cW, COLW - 2)

ZB = (1.0966, 1.0990, 6, 1, 5)
ZS = (1.0984, 1.1002, 13, 3, 5)
ZD = (1.0975, 1.1000, 9, 1, 2)
PDH, PDL = 1.1042, 1.0988

rnd = random.Random(42)
closes = [1.0998]
for i in range(N - 1):
    closes.append(closes[-1] + rnd.uniform(-0.0011, 0.00115))
candles = []
c = closes[0]
for cl in closes:
    o = c
    candles.append((o, max(o, cl) + rnd.uniform(0.0001, 0.00055),
                    min(o, cl) - rnd.uniform(0.0001, 0.00055), cl))
    c = cl
H4 = [(1.0975,1.0998,1.0962,1.0990),(1.0990,1.1002,1.0978,1.0984),
      (1.0984,1.0996,1.0966,1.0972),(1.0972,1.0990,1.0958,1.0987),
      (1.0987,1.1006,1.0974,1.0999),(1.0999,1.1012,1.0988,1.1004)]
DD = [(1.0990,1.1010,1.0968,1.0975),(1.0975,1.0995,1.0952,1.0988),
      (1.0988,1.1018,1.0970,1.1002)]

def fmt(v): return f"{v:.4f}"

s = []
A = s.append
texts = []
def text(x, yv, t, fs, fill, anchor="start", bold=False, opacity=None):
    t = t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    st = ' font-weight="bold"' if bold else ""
    op = f' opacity="{opacity}"' if opacity is not None else ""
    A(f'<text x="{x:.1f}" y="{yv:.1f}" font-size="{fs}" fill="{fill}" text-anchor="{anchor}"{st}{op}>{t}</text>')
    texts.append((x, yv, anchor, fs, len(t)))

def scene(px, th):
    """th: (bg, grid, txt, txt2, tagDark)"""
    BG, GRID, TXT, TXT2, TAGD = th
    x0 = px + 10
    right = px + PW - 14
    A(f'<rect x="{px}" y="{Y0-40}" width="{PW}" height="{Y1-Y0+90}" rx="8" fill="{BG}" stroke="{GRID}"/>')
    gp = 1.0910
    while gp <= P1:
        yy = y(gp)
        A(f'<line x1="{x0}" y1="{yy:.1f}" x2="{right}" y2="{yy:.1f}" stroke="{GRID}" stroke-width="1"/>')
        text(right, yy + 3, f"{gp:.4f}", 9, TXT2, "end")
        gp += 0.0020
    dbx = x0 + DAY_IDX * slot + sW / 2
    A(f'<line x1="{dbx:.1f}" y1="{Y0}" x2="{dbx:.1f}" y2="{Y1}" stroke="{TXT2}" stroke-width="1" stroke-dasharray="2,4" opacity="0.7"/>')
    text(dbx - 4, Y0 + 11, "D day", 9, TXT2, "end")
    for i, (o, h, l, ccl) in enumerate(candles):
        x = x0 + i * slot + sW / 2
        col = BULL if ccl >= o else BEAR
        A(f'<line x1="{x:.1f}" y1="{y(h):.1f}" x2="{x:.1f}" y2="{y(l):.1f}" stroke="{col}" stroke-width="1.3"/>')
        top, bot = y(max(o, ccl)), y(min(o, ccl))
        A(f'<rect x="{x-cW/2:.1f}" y="{top:.1f}" width="{cW}" height="{max(1,bot-top):.1f}" fill="{col}"/>')
    # ---- FVG chart bands: gradient, NO borders (same alphas as the Java code) ----
    def chart_band(z, active, gid):
        zlo, zhi, zidx = z[0], z[1], z[2]
        y1, y2 = y(zhi), y(zlo)
        bx = x0 + zidx * slot + sW / 2
        if active:
            A(f'<rect x="{bx:.1f}" y="{y2:.1f}" width="{right-bx:.1f}" height="{y1-y2:.1f}" fill="url(#{gid})"/>')
            text(right - 2, (y1 + y2) / 2 + 3, "FVG 4H", 8, BULL if gid == "gb" else BEAR, "end", opacity=0.7)
        else:
            A(f'<rect x="{bx:.1f}" y="{y2:.1f}" width="{right-bx:.1f}" height="{y1-y2:.1f}" fill="rgba(140,140,140,0.05)"/>')
    chart_band(ZB, True, "gb")
    chart_band(ZS, False, "gr")
    # ---- clusters: 4H + D, x2 bodies, soft lane fills only ----
    ox = x0 + N * slot + 14
    dx = ox + 6 * COLW + 12
    def lane(oxl, c0, c1, zlo, zhi, active):
        y1, y2 = y(zhi), y(zlo)
        x0l = oxl + c0 * COLW + 2
        x1l = oxl + (c1 + 1) * COLW - 2
        A(f'<rect x="{x0l:.1f}" y="{y2:.1f}" width="{x1l-x0l:.1f}" height="{y1-y2:.1f}" fill="rgba(38,166,154,{0.16 if active else 0.06})"/>')
    def cluster(xbase, data, label, lanes):
        text(xbase - 4, Y0 + 2, label, 10, TXT, "start", bold=True)
        for i, (o, h, l, ccl) in enumerate(data):
            x = xbase + i * COLW + COLW / 2
            col = BULL if ccl >= o else BEAR
            top, bot = y(max(o, ccl)), y(min(o, ccl))
            A(f'<line x1="{x:.1f}" y1="{y(h):.1f}" x2="{x:.1f}" y2="{y(l):.1f}" stroke="{col}" stroke-width="1.5"/>')
            A(f'<rect x="{x-BODY_NEW/2:.1f}" y="{top:.1f}" width="{BODY_NEW}" height="{max(1,bot-top):.1f}" fill="{col}"/>')
        for (c0, c1, zlo, zhi, active) in lanes:
            lane(xbase, c0, c1, zlo, zhi, active)
    cluster(ox, H4, "4H", [(ZB[3], ZB[4], ZB[0], ZB[1], True), (ZS[3], ZS[4], ZS[0], ZS[1], False)])
    cluster(dx, DD, "D", [(ZD[3], ZD[4], ZD[0], ZD[1], True)])
    # ---- PDH/PDL: solid lines + right-edge price tags ----
    def pdhpx(val, colr, name):
        yy = y(val)
        A(f'<line x1="{dbx:.1f}" y1="{yy:.1f}" x2="{right}" y2="{yy:.1f}" stroke="{colr}" stroke-width="1" opacity="0.8"/>')
        t = f"{name} {fmt(val)}"
        tw = 0.62 * 8 * len(t) + 12
        A(f'<rect x="{right-tw:.1f}" y="{yy-11:.1f}" width="{tw:.1f}" height="14" rx="3" fill="{colr}"/>')
        text(right - tw / 2, yy + 3, t, 8, TAGD, "middle", bold=True)
    pdhpx(PDH, PDH_C, "PDH")
    pdhpx(PDL, PDL_C, "PDL")

DARK = ("#1e222d", "#2a3040", "#d1d4dc", "#787b86", "#10141c")
LIGHT = ("#ffffff", "#e3e6ee", "#3a3f4a", "#8a8f9a", "#10141c")

A(f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}" font-family="Consolas,Menlo,monospace">')
A(f'<defs>'
  f'<linearGradient id="gb" x1="0" y1="0" x2="1" y2="0">'
  f'<stop offset="0" stop-color="rgba(38,166,154,0.16)"/><stop offset="1" stop-color="rgba(38,166,154,0.03)"/></linearGradient>'
  f'<linearGradient id="gr" x1="0" y1="0" x2="1" y2="0">'
  f'<stop offset="0" stop-color="rgba(239,83,80,0.16)"/><stop offset="1" stop-color="rgba(239,83,80,0.03)"/></linearGradient>'
  f'</defs>')
A(f'<rect width="{W}" height="{H}" fill="#14171f"/>')
text(32, 34, "Same code, same alphas — how the v2 overlays read on each chart color system", 16, "#d1d4dc", "start", bold=True)
text(32, 54, "no borders at all: gradient FVG (16%->3%) + solid 1px PDH/PDL + right-edge price tags", 11, "#787b86")

px1 = 32
px2 = px1 + PW + GAP
text(px1 + 10, 78, "Dark theme (خلفية داكنة)", 12, "#42a5f5", "start", bold=True)
text(px2 + 10, 78, "Light theme (خلفية بيضاء)", 12, "#42a5f5", "start", bold=True)
scene(px1, DARK)
scene(px2, LIGHT)

ly = Y1 + 66
text(32, ly, "identical values in both: FVG fill 0.16->0.03, column lane 0.16 / filled 0.05-0.06, lines 1px @ 0.8, tags: colored bg + #10141c text", 11, "#787b86")
text(32, ly + 17, "if it reads too faint on YOUR background: raise alphas (say the word and I tune them for your theme)", 11, "#787b86")
A('</svg>')

viol = 0
for (x, yv, anchor, fs, n) in texts:
    w = 0.62 * fs * n
    if anchor == "end": lo, hi = x - w, x
    elif anchor == "middle": lo, hi = x - w / 2, x + w / 2
    else: lo, hi = x, x + w
    if lo < -2 or hi > W + 2 or yv < 10 or yv > H + 2:
        viol += 1
        print("OVERFLOW:", round(x, 1), round(yv, 1), anchor, fs, n)
out = "/home/user/JFOREXDATA/jforex/docs/htf-theme-check.svg"
open(out, "w", encoding="utf-8").write("\n".join(s))
print("wrote", out, len(s), "elements; overflow violations:", viol)
