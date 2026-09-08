#!/usr/bin/env python3
"""Part-3: per-5m candle measurement -> 1H aggregation -> official math checks."""
import numpy as np
from PIL import Image

im = np.asarray(Image.open('/home/user/uploads/image_2026-09-07_114022381.png').convert('RGB')).astype(int)
H, W, _ = im.shape
R, G, B = im[:,:,0], im[:,:,1], im[:,:,2]
LUM = (R*299+G*587+B*114)//1000
INK = LUM < 110
GRN = (G > R+12) & (G > B+8) & (LUM < 225)
LIGHT = (LUM > 218) & (LUM < 240)

mx = LUM[:, 2:62] < 200
rows = mx.sum(axis=1); clusters=[]; y=0
while y < H:
    if rows[y] > 0:
        y0=y
        while y < H and rows[y] > 0: y += 1
        clusters.append((y0+y-1)/2.0)
    y += 1
best=None
for i in range(len(clusters)):
    for j in range(i+8, len(clusters)+1):
        cs=clusters[i:j]; d=np.diff(cs)
        if d.std() < 1.5 and (best is None or len(cs) > len(best)): best=cs
cs=best
vals=[1.62800-0.0005*k for k in range(len(cs))]
cf=np.polyfit(cs,vals,1); price=lambda yy: cf[0]*yy+cf[1]; yof=lambda p:(p-cf[1])/cf[0]
print("CALIB n=%d resid=%.6f  px/0.0005=%.2f" % (len(cs), max(abs(price(c)-v) for c,v in zip(cs,vals)), np.diff(cs).mean()))

def runs(xs):
    out=[]
    for x in xs:
        if out and x==out[-1][1]+1: out[-1][1]=x
        else: out.append([x,x])
    return out

# ---- 5m candles: cluster body columns ----
mask = INK | GRN
body_cols = {}
for x in range(70, 1830):
    ys = np.nonzero(mask[15:1015, x])[0]
    if len(ys)==0: continue
    rr = runs(list(ys+15))
    lg = max(rr, key=lambda r: r[1]-r[0])
    if lg[1]-lg[0] >= 5: body_cols[x] = lg
candles5 = []
xs = sorted(body_cols)
cur = [xs[0]]
for x in xs[1:]:
    if x - cur[-1] <= 2: cur.append(x)
    else: candles5.append(cur); cur=[x]
candles5.append(cur)
five = []
for cols in candles5:
    if len(cols) < 4: continue
    bt = min(body_cols[c][0] for c in cols); bb = max(body_cols[c][1] for c in cols)
    xc = cols[len(cols)//2]
    ys = np.nonzero(mask[15:1015, xc])[0]+15
    wt, wb = ys.min(), ys.max()
    green = bool(GRN[(bt+bb)//2, xc])
    five.append(dict(x0=cols[0], x1=cols[-1], bt=bt, bb=bb, wt=wt, wb=wb, green=green))
print("5m candles detected:", len(five))

def agg(f):
    O = price(f[0]['bb']) if f[0]['green'] else price(f[0]['bt'])
    C = price(f[-1]['bt']) if f[-1]['green'] else price(f[-1]['bb'])
    Hh = price(min(c['wt'] for c in f)); L = price(max(c['wb'] for c in f))
    return O, Hh, L, C

def condEQ(O,Hh,L,C):
    body=abs(C-O); lw=min(O,C)-L; uw=Hh-max(O,C)
    if max(lw,uw)<=body: return (Hh+L)/2
    return (L+min(O,C))/2 if lw>uw else (Hh+max(O,C))/2

V = [826, 946, 1067, 1188, 1309]
names = ['C1','C2','C3','C4','C5']
hour = {}
for i in range(len(V)-1):
    f = [c for c in five if c['x0'] >= V[i]-3 and c['x1'] <= V[i+1]+3]
    if len(f) < 6: print(names[i], "span few candles:", len(f)); continue
    O,Hh,L,C = agg(f)
    hour[names[i]] = (O,Hh,L,C)
    print("%s x[%d..%d] n=%d O=%.5f H=%.5f L=%.5f C=%.5f %s condEQ=%.5f" %
          (names[i], V[i], V[i+1], len(f), O, Hh, L, C, 'bull' if C>=O else 'bear', condEQ(O,Hh,L,C)))

Z1 = (price(265), price(428)); Z2 = (price(443), price(613))
print("\nzone1 measured [%.5f..%.5f]" % Z1)
print("zone2 measured [%.5f..%.5f]" % Z2)
for zn, a, b in ((Z1,'C3','C4'), (Z2,'C4','C5')):
    if a in hour and b in hour:
        O,Hh,L,C = hour[a]; On = hour[b][0]
        pred = sorted([condEQ(O,Hh,L,C), On])
        meas = sorted(zn)
        print("  CHECK %s: pred[%.5f..%.5f] vs meas[%.5f..%.5f]  dpx=%.1f / %.1f" %
              (a, pred[0], pred[1], meas[0], meas[1], abs(yof(pred[0])-yof(meas[0])), abs(yof(pred[1])-yof(meas[1]))))

print("\nblue EQ prices: 1.62653 (x230-1017), 1.62620 (x273-873)")
for n in names:
    if n in hour:
        O,Hh,L,C = hour[n]
        print("  condEQ(%s)=%.5f   (H+maxOC)/2=%.5f  (L+minOC)/2=%.5f  (H+L)/2=%.5f" %
              (n, condEQ(O,Hh,L,C), (Hh+max(O,C))/2, (L+min(O,C))/2, (Hh+L)/2))

print("\nladder ticks (light gray):")
sub = LIGHT[:, 990:1060]; cnt = sub.sum(axis=1)
tk = [y for y in range(300, 1010) if cnt[y] >= 12]
merged=[]
for y in tk:
    if merged and y-merged[-1][-1] <= 2: merged[-1].append(y)
    else: merged.append([y])
tp = [price(sum(m)/len(m)) for m in merged]
for m, p in zip(merged, tp): print("  y=%6.1f price=%.5f" % (sum(m)/len(m), p))
if len(tp) >= 4:
    ks = [-1,-2,-2.5,-4,-4.5][:len(tp)]
    Afit = np.polyfit(ks, tp, 1)
    resid = max(abs(Afit[0]*k+Afit[1]-p) for k,p in zip(ks,tp))
    print("  FIT anchor=%.5f leg=%.5f resid_px=%.2f" % (Afit[1], Afit[0], abs(yof(Afit[1])+0)-abs(yof(Afit[1])) or resid))
    print("  resid in px:", ["%.1f" % abs(yof(Afit[0]*k+Afit[1])-yof(p)) for k,p in zip(ks,tp)])
