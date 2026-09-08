#!/usr/bin/env python3
"""Measure official TTFM-on-TradingView screenshot (EURAUD 5m/1H, Tue 19 Nov).
Calibrates price axis from right-margin labels, then measures:
 solid/dotted horizontal lines, blue EQ segments, gray T-Spot zones, ladder ticks,
 1H candle OHLC (from body/wick pixels) -> checks official math vs our formulas.
"""
import sys
import numpy as np
from PIL import Image

IMG = sys.argv[1] if len(sys.argv) > 1 else "/home/user/uploads/image_2026-09-07_114022381.png"
im = np.asarray(Image.open(IMG).convert("RGB")).astype(int)
H, W, _ = im.shape
R, G, B = im[:,:,0], im[:,:,1], im[:,:,2]
LUM = (R*299 + G*587 + B*114)//1000
print("size", W, H, "bg@", im[100,100], im[600,300])

# ---------- 1) axis calibration from right-margin label rows ----------
mx = LUM[:, W-55:W-6] < 140
rows = mx.sum(axis=1)
clusters = []
y = 0
while y < H:
    if rows[y] > 0:
        y0 = y
        while y < H and rows[y] > 0: y += 1
        clusters.append((y0+y-1)/2.0)
    y += 1
# keep evenly-spaced run of >=8 clusters
best = None
for i in range(len(clusters)):
    for j in range(i+8, len(clusters)+1):
        cs = clusters[i:j]
        d = np.diff(cs)
        if d.std() < 1.5 and (best is None or len(cs) > len(best)):
            best = cs
cs = best
print("axis clusters n=%d y0=%.1f step=%.2f" % (len(cs), cs[0], np.diff(cs).mean()))
vals = [1.62800 - 0.0005*k for k in range(len(cs))]
A = np.polyfit(cs, vals, 1)
price = lambda y: A[0]*y + A[1]
res = max(abs(price(c)-v) for c, v in zip(cs, vals))
print("calib max residual = %.6f" % res)

X0, X1 = 40, 1900
dark = (LUM < 110)
blue = (B > R+25) & (B > G+15)
gray = (abs(R-G) < 10) & (abs(G-B) < 10) & (R > 200) & (R < 236)

def runs(xs):
    out = []
    for x in xs:
        if out and x == out[-1][1]+1: out[-1][1] = x
        else: out.append([x, x])
    return out

def rowfeat(mask, xmin, xmax, cmin, cmax, runmax=999):
    out = []
    sub = mask[:, xmin:xmax]
    cnt = sub.sum(axis=1)
    for y in range(15, 1015):
        if cmin <= cnt[y] <= cmax:
            rr = [r for r in runs(list(np.nonzero(sub[y])[0] + xmin)) if r[1]-r[0] >= 3]
            if rr and max(r[1]-r[0] for r in rr) <= runmax:
                out.append((y, rr[0][0], rr[-1][1], int(cnt[y])))
    # merge consecutive y
    merged = []
    for y, xa, xb, c in out:
        if merged and y-merged[-1][0] <= 2:
            merged[-1] = (y, min(merged[-1][1], xa), max(merged[-1][2], xb), c)
        else:
            merged.append((y, xa, xb, c))
    return merged

print("\n-- long solid horizontal lines (count>250) --")
for y, xa, xb, c in rowfeat(dark, X0, X1, 250, 20000):
    print("  y=%4d  price=%.5f  x[%d..%d] n=%d" % (y, price(y), xa, xb, c))
print("-- medium horizontal segments (80..250) --")
for y, xa, xb, c in rowfeat(dark, X0, X1, 80, 250):
    print("  y=%4d  price=%.5f  x[%d..%d] n=%d" % (y, price(y), xa, xb, c))
print("-- blue EQ segments --")
for y, xa, xb, c in rowfeat(blue, X0, X1, 20, 20000):
    print("  y=%4d  price=%.5f  x[%d..%d] n=%d" % (y, price(y), xa, xb, c))
print("-- dotted rows (30..120, run<=4) --")
for y, xa, xb, c in rowfeat(dark, X0, X1, 30, 120, runmax=4):
    print("  y=%4d  price=%.5f  x[%d..%d] n=%d" % (y, price(y), xa, xb, c))
print("-- gray zone row-bands --")
zb = rowfeat(gray, X0, X1, 50, 20000)
bands = []
for y, xa, xb, c in zb:
    if bands and y-bands[-1][1] <= 3 and abs(xa-bands[-1][2]) < 25:
        bands[-1][1] = y; bands[-1][3] = min(bands[-1][3], xa); bands[-1][4] = max(bands[-1][4], xb)
    else:
        bands.append([y, y, xa, xa, xb])
for b in bands:
    print("  y[%d..%d] price[%.5f..%.5f] x[%d..%d]" % (b[0], b[1], price(b[0]), price(b[1]), b[3], b[4]))
print("-- ladder ticks x in [960,1080] --")
ticks = rowfeat(dark, 960, 1080, 10, 60, runmax=60)
for y, xa, xb, c in ticks:
    print("  y=%4d  price=%.5f  x[%d..%d] n=%d" % (y, price(y), xa, xb, c))
print("-- vertical HTF boundary columns (col count>500) --")
colcnt = dark[15:1015, :].sum(axis=0)
vx = [x for x in range(X0, X1) if colcnt[x] > 500]
print("  ", vx)
