#!/usr/bin/env python3
"""T-Spot pixel-space verifier (affine-invariant).
Usage: python3 verify_tspot.py <frame.png> [...]
Measures official zones / HTF verticals / 1H candles from a chart screenshot,
composes HTF candles, computes the conditional EQ in pixel space and checks
official zone == [condEQ(prev HTF) .. open(next HTF)].
"""
import sys
import numpy as np
from PIL import Image
from collections import deque

def comps(mask, H, XMAX, minpx=8):
    seen = np.zeros_like(mask, bool); out = []
    ys, xs = np.nonzero(mask)
    for y0, x0 in zip(ys, xs):
        if seen[y0, x0]: continue
        q = deque([(y0, x0)]); seen[y0, x0] = True; pts = []
        while q:
            y, x = q.popleft(); pts.append((y, x))
            for dy, dx in ((1,0),(-1,0),(0,1),(0,-1)):
                ny, nx = y+dy, x+dx
                if 0 <= ny < mask.shape[0] and 0 <= nx < XMAX and mask[ny, nx] and not seen[ny, nx]:
                    seen[ny, nx] = True; q.append((ny, nx))
        if len(pts) >= minpx:
            py = [p[0] for p in pts]; px = [p[1] for p in pts]
            out.append((min(px), max(px), min(py), max(py), len(pts)))
    return out

def condEQ(O, Hh, L, C):
    body = abs(C-O); lw = min(O,C)-L; uw = Hh-max(O,C)
    if max(lw, uw) <= body: return (Hh+L)/2.0, "full-mid"
    if lw > uw: return (L+min(O,C))/2.0, "lower-wick-mid"
    return (Hh+max(O,C))/2.0, "upper-wick-mid"

def analyze(path):
    a = np.asarray(Image.open(path).convert('RGB')).astype(int)
    H, W, _ = a.shape
    XMAX = 1500
    r, g, b = a[:,:,0], a[:,:,1], a[:,:,2]
    # zones (sage fill)
    zm = (np.abs(r-225)<6)&(np.abs(g-236)<6)&(np.abs(b-225)<6)
    zc = [c for c in comps(zm, H, XMAX, 500)]
    zones = []
    for c in sorted(zc):
        for z in zones:
            if abs(z[2]-c[2]) <= 3 and abs(z[3]-c[3]) <= 3 and c[0]-z[1] <= 12:
                z[1] = max(z[1], c[1]); break
        else: zones.append(list(c[:4]))
    zones = [z for z in zones if z[1]-z[0] > 60]
    # HTF verticals
    gray = (np.abs(r-g)<10)&(np.abs(g-b)<10)&(r>170)&(r<246)
    cc = gray.sum(axis=0)
    darkcol = ((r<80)&(g<80)&(b<80)).sum(axis=0)
    raw = []
    for x in range(2, XMAX-2):
        if cc[x] <= 350: continue
        if max(darkcol[x-2],darkcol[x-1],darkcol[x],darkcol[x+1],darkcol[x+2]) > 60: continue
        raw.append(x)
    vl = []
    for x in raw:
        if vl and x-vl[-1][-1] <= 2: vl[-1].append(x)
        else: vl.append([x])
    vlines = [sum(c)//len(c) for c in vl]
    # candles
    green = (np.abs(r-84)<30)&(np.abs(g-156)<30)&(np.abs(b-104)<30)
    dark = (r<80)&(g<80)&(b<80)
    darkb = dark.copy()
    for s in (1,2): darkb &= np.roll(dark,s,axis=1)&np.roll(dark,-s,axis=1)
    bodies = []
    mg = green.copy(); mg[:,XMAX:] = False; mg[820:995,855:1065] = False
    for (x1,x2,y1,y2,n) in comps(mg,H,XMAX,8):
        if x2-x1 >= 5 and y2-y1 >= 1: bodies.append((x1,x2,y1,y2,'G'))
    md = darkb.copy(); md[:,XMAX:] = False; md[820:995,855:1065] = False
    for (x1,x2,y1,y2,n) in comps(md,H,XMAX,12):
        if x2-x1 >= 2 and y2-y1 >= 3: bodies.append((x1,x2,y1,y2,'B'))
    bodies.sort(key=lambda t: t[0])
    def wick_extent(x1,x2,y1,y2):
        cx = (x1+x2)//2
        def darkat(y,x):
            p = a[y,x]; return p[0]<140 and p[1]<140 and p[2]<140
        yt = y1; y = y1-1
        while y > 0 and (darkat(y,cx) or darkat(y,cx-1) or darkat(y,cx+1)): yt = y; y -= 1
        yb = y2; y = y2+1
        while y < H-1 and (darkat(y,cx) or darkat(y,cx-1) or darkat(y,cx+1)): yb = y; y += 1
        return yt, yb
    candles = []
    for (x1,x2,y1,y2,col) in bodies:
        yt, yb = wick_extent(x1,x2,y1,y2)
        candles.append(dict(cx=(x1+x2)//2, yTop=y1, yBot=y2, yHi=yt, yLo=yb, col=col))
    uniq = {}
    for c in candles: uniq.setdefault(c['cx']//4, c)
    candles = sorted(uniq.values(), key=lambda c: c['cx'])
    def seg_of(cx):
        for i in range(len(vlines)-1):
            if vlines[i] <= cx < vlines[i+1]: return i
        return -1
    segs = {}
    for c in candles:
        s = seg_of(c['cx'])
        if s < 0: continue
        segs.setdefault(s, []).append(c)
    AG = {}
    for s, cs in segs.items():
        cs = sorted(cs, key=lambda c: c['cx'])
        f, l = cs[0], cs[-1]
        yO = f['yBot'] if f['col']=='G' else f['yTop']
        yC = l['yTop'] if l['col']=='G' else l['yBot']
        yHi = min(c['yHi'] for c in cs); yLo = max(c['yLo'] for c in cs)
        AG[s] = dict(O=-yO, C=-yC, H=-yHi, L=-yLo, yO=yO, yC=yC, yHi=yHi, yLo=yLo, n=len(cs))
    rows = []
    for (x1,x2,yT,yB) in zones:
        k = None
        for i in range(len(vlines)-1):
            if vlines[i] <= x1 < vlines[i+1]: k = i
        if k is None or (k-1) not in AG or k not in AG:
            rows.append((x1,x2,yT,yB,None,None,None,None)); continue
        prev, cur = AG[k-1], AG[k]
        eq, mode = condEQ(prev['O'], prev['H'], prev['L'], prev['C'])
        yEq = -eq; yOpen = cur['yO']
        pT, pB = sorted((yEq, yOpen))
        rows.append((x1,x2,yT,yB,pT,pB,mode,k))
    return vlines, AG, rows

if __name__ == '__main__':
    for path in sys.argv[1:]:
        vlines, AG, rows = analyze(path)
        print("== "+path)
        print("   vlines:", vlines, " segs:", {k: v['n'] for k, v in sorted(AG.items())})
        for (x1,x2,yT,yB,pT,pB,mode,k) in rows:
            if pT is None:
                print(f"   zone x[{x1},{x2}]: skip (generator off-screen)")
            else:
                dT, dB = abs(yT-pT), abs(yB-pB)
                verdict = 'MATCH' if dT <= 3 and dB <= 3 else 'DIFF'
                print(f"   zone x[{x1},{x2}] seg{k}: meas T/B={yT}/{yB} pred T/B={pT:.1f}/{pB:.1f} [{mode}] dT={dT:.1f} dB={dB:.1f} {verdict}")
