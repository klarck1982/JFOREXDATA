#!/usr/bin/env python3
"""Part-2: official EURAUD screenshot math audit.
Measures candles/zones/EQ/ladder, then checks official pixel math against our formulas:
  zone == [condEQ(cN), open(cN+1)] ; blue == condEQ(candle) ; ladder linear in {-1,-2,-2.5,-4,-4.5}.
"""
import numpy as np
from PIL import Image

im = np.asarray(Image.open('/home/user/uploads/image_2026-09-07_114022381.png').convert('RGB')).astype(int)
H, W, _ = im.shape
R, G, B = im[:,:,0], im[:,:,1], im[:,:,2]
LUM = (R*299+G*587+B*114)//1000
INK = LUM < 110
MID = (LUM >= 110) & (LUM < 215)
BLUE = (B > R+25) & (B > G+15)
FILL = np.abs(R-228) <= 6
GRN = (G > R+12) & (G > B+8) & (LUM < 225)

# ---- calibration ----
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
vals=[1.62800-0.0005*k for k in range(len(cs))]  # left axis: top label 1.62800, step 0.0005
cf=np.polyfit(cs,vals,1); price=lambda yy: cf[0]*yy+cf[1]
yof=lambda p: (p-cf[1])/cf[0]
print("CALIB n=%d step=%.2f resid=%.6f" % (len(cs), np.diff(cs).mean(), max(abs(price(c)-v) for c,v in zip(cs,vals))))

def runs(xs):
    out=[]
    for x in xs:
        if out and x==out[-1][1]+1: out[-1][1]=x
        else: out.append([x,x])
    return out
def hfeat(mask, xmin, xmax, cmin, cmax, runmax=999, runmin=3):
    sub=mask[:, xmin:xmax]; cnt=sub.sum(axis=1); out=[]
    for y in range(15,1015):
        if cmin<=cnt[y]<=cmax:
            rr=[r for r in runs(list(np.nonzero(sub[y])[0]+xmin)) if r[1]-r[0]>=runmin]
            if rr and max(r[1]-r[0] for r in rr)<=runmax:
                out.append([y, rr[0][0], rr[-1][1], int(cnt[y])])
    m=[]
    for f in out:
        if m and f[0]-m[-1][0]<=2: m[-1][1]=f[0]; m[-1][2]=min(m[-1][2],f[1]); m[-1][3]=max(m[-1][3],f[2])
        else: m.append(f)
    return m

print("\nLINES black long:", [(f[0], round(price(f[0]),5), f[1], f[2]) for f in hfeat(INK,40,1900,250,20000)])
print("LINES black med :", [(f[0], round(price(f[0]),5), f[1], f[2]) for f in hfeat(INK,40,1900,60,250)])
print("DOTS  black     :", [(f[0], round(price(f[0]),5), f[1], f[2]) for f in hfeat(INK,40,1900,25,120,runmax=4)])
print("LINES gray med  :", [(f[0], round(price(f[0]),5), f[1], f[2]) for f in hfeat(MID,40,1900,60,400,runmin=20)])
print("BLUE segments   :", [(f[0], round(price(f[0]),5), f[1], f[2]) for f in hfeat(BLUE,40,1900,15,20000)])
print("TICKS gray      :", [(f[0], round(price(f[0]),5), f[1], f[2], f[3]) for f in hfeat(MID,995,1050,15,60,runmin=10)])

colcnt=(LUM[15:1015,:]<215).sum(axis=0)
vx=[x for x in range(40,1900) if colcnt[x]>700]
vg=[]
for x in vx:
    if vg and x-vg[-1][1]<=2: vg[-1][1]=x
    else: vg.append([x,x])
vlines=[(a+b)//2 for a,b in vg]
print("VERTICALS       :", vlines)

# ---- gray zone bands ----
zf=hfeat(FILL,40,1900,50,20000,runmin=30)
bands=[]
for f in zf:
    if bands and f[0]-bands[-1][1]<=3 and abs(f[1]-bands[-1][2])<25:
        bands[-1][1]=f[0]; bands[-1][2]=min(bands[-1][2],f[1]); bands[-1][3]=max(bands[-1][3],f[2])
    else: bands.append([f[0],f[0],f[1],f[2]])
print("ZONES gray      :", [(b[0],b[1],round(price(b[0]),5),round(price(b[1]),5),b[2],b[3]) for b in bands])

# ---- candle OHLC in an x-window ----
def candle(xa,xb):
    xa+=3; xb-=3
    mask=INK|GRN
    body_cols=[]
    for x in range(xa,xb+1):
        ys=np.nonzero(mask[15:1015,x])[0]
        if len(ys)==0: continue
        rr=runs(list(ys+15))
        lg=max(rr,key=lambda r:r[1]-r[0])
        if lg[1]-lg[0]>=6: body_cols.append((x,lg[0],lg[1]))
    if not body_cols: return None
    bt=min(b[1] for b in body_cols); bb=max(b[2] for b in body_cols)
    ys,xs=np.nonzero(mask[15:1015,xa:xb+1]); ys=ys+15
    wt,wb=ys.min(),ys.max()
    xm=[b[0] for b in body_cols][len(body_cols)//2]
    green=bool(GRN[(bt+bb)//2, xm])
    O,C=(price(bb),price(bt)) if green else (price(bt),price(bb))
    return dict(O=O,H=price(wt),L=price(wb),C=C,bull=green,yt=wt,yb=wb)

def condEQ(O,Hh,L,C):
    body=abs(C-O); lw=min(O,C)-L; uw=Hh-max(O,C)
    if max(lw,uw)<=body: return (Hh+L)/2
    return (L+min(O,C))/2 if lw>uw else (Hh+max(O,C))/2

spans=[]
for i in range(len(vlines)-1):
    if vlines[i+1]-vlines[i]>60: spans.append((vlines[i],vlines[i+1]))
print("\nCANDLES (1H spans between verticals):")
cands={}
for a,b in spans:
    c=candle(a,b)
    if c: cands[(a,b)]=c; print("  x[%d..%d] O=%.5f H=%.5f L=%.5f C=%.5f %s  condEQ=%.5f" % (a,b,c['O'],c['H'],c['L'],c['C'],'bull' if c['bull'] else 'bear', condEQ(c['O'],c['H'],c['L'],c['C'])))

print("\nCHECKS:")
for b in bands:
    zt,zb,xa,xb=b[0],b[1],b[2],b[3]
    host=None
    for (a,bb) in cands:
        if a<=xa<=bb: host=(a,bb)
    nxt=None
    for (a,bb) in cands:
        if a>=xb-2 and (host is None or a>host[0]): nxt=(a,bb); break
    if host and nxt:
        c=cands[host]; pred=sorted([condEQ(c['O'],c['H'],c['L'],c['C']), cands[nxt]['O']])
        meas=sorted([price(zt),price(zb)])
        print("  zone x[%d..%d]: measured[%.5f..%.5f] predicted[%.5f..%.5f] dpx=%.1f/%.1f" %
              (xa,xb,meas[0],meas[1],pred[0],pred[1],abs(yof(meas[0])-yof(pred[0])),abs(yof(meas[1])-yof(pred[1]))))
