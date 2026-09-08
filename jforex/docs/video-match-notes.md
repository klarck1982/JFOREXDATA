# TTFM Official Video — Match Notes (المطابقة مع الفيديو الرسمي)
Source: `uploads/TTFM-Fractal-Example.txt` (MP4 renamed) · 17.37s · 1920x1080 · 24fps · **audio track SILENT (rms=0, no narration)**
Frames: `docs/video-frames/frame_01..17.png` (1 fps) · `transcript.txt` (empty — silence)

## Observed official on-chart elements (all frames consistent)
1. **Info text block** (bottom-center, monospace):
   - `NQ↑! (1H)` → instrument + bias arrow + `!` + chart TF in parens
   - `1H-1D Model` → pairing model (chartTF-HTF)
   - `15:00:00` / `18:00:00` / `21:00:00` / `01:00:00` / `09:00:00` → current bar time (large)
   - `Bias: Bullish`
   - `SMT(Auto): ES↑!, YM↑!` → auto SMT cracks: correlated instrument + arrow + `!`
2. **Vertical gray lines** = HTF candle boundaries; **green zones span exactly one HTF segment** and GROW bar-by-bar while that HTF candle forms (zone width = elapsed part of current HTF candle).
3. **Green zone geometry** = `[ EQ(prev HTF candle) .. open(current HTF candle) ]` = our locked T-Spot definition. Verified on 3 zones:
   - zone top = open of new HTF candle (= close/high of prev), zone bottom = conditional EQ of prev candle; dotted line extends right from zone BOTTOM (EQ level), thin solid gray line extends right from zone TOP (open level).
   - In ALL 3 observed cases open > EQ (zone ABOVE EQ) and price pulled back INTO the zone then rejected UP (BUY-support). Official color in all 3 = **GREEN**.
4. **Orange polyline (V shape)**: swing point → C2 low → recovery swing point; each leg carries an SMT crack label (`YM↑!` twice). `C2` label sits under the manipulation candle low (V bottom).
5. **`C4` label** at bottom-left corner of the 3rd zone (the zone where the continuation closure printed; the C4 candle itself closes above zone top later).
6. **Projection level `-1`**: small label + short line mid-chart; dotted horizontal line at the swing high (range top). Full ladder (-1,-2,-2.5,-4,-4.5) seen in the separate official screenshot `uploads/indicator-example-smt.png`.
7. Right-hand magnified candles = video author zoom (NOT an indicator feature).

## Conflict found (needs user decision)
- User-locked color rule: green = zone BELOW EQ (SELL-support), red = zone ABOVE EQ (BUY-support).
- Official video: zone ABOVE EQ (BUY-support) drawn **GREEN** (3/3 cases, bullish bias).
- Reconciliation proposal: color by SUPPORT DIRECTION, not by side: **GREEN = BUY-support (zone above EQ)**, **RED = SELL-support (zone below EQ)**. This keeps user's PO3 wick-formation semantics (above-EQ → lower wick → reject up → BUY) AND matches official pixels.

## Vs our current build (status)
| Official element | Our build | Gap |
|---|---|---|
| T-Spot zone [EQ(N),open(N+1)] + lifecycle | implemented | color mapping (see conflict); zone should grow with forming HTF candle; extend boundary level lines (solid top / dotted bottom) |
| Info panel format (5 lines incl. SMT(Auto)) | option exists OFF | adopt exact 5-line format when enabled |
| SMT crack labels on legs + orange polyline | SMT OFF | draw polyline swing→C2→recovery with per-leg crack labels |
| C2 label under manipulation low | C2 computed, unlabeled | add label |
| C4 label at zone corner | C4 not implemented | step 3+ |
| Projection ladder -1..-4.5 + leg line | T1/T2 only | draw full ladder + orange leg (separate agreement) |
| HTF boundary vertical lines | not drawn | add when zones enabled |

## IMPLEMENTED (100%-match batch, 2026-09-06) — tests 111/111, javac clean
- Colors flipped to official SUPPORT-DIRECTION rule: GREEN=BUY-support (zone ABOVE EQ), RED=SELL-support (zone BELOW EQ).
- Zone render official: flat alpha 0.18 (invalidated gray 0.08), NO border, NO zone text; solid line on OPEN bound + dotted line on EQ bound.
- Current zone GROWS bar-by-bar with the forming HTF candle (end=min(now, curStart+pMs)).
- HTF boundary verticals (thin gray) at anchor-layer candle opens + current candle start. Option `[Struct] HTF Boundary Lines` default ON.
- Orange manipulation legs polyline swing(c1)->C2->C3 + SMT crack tags on each leg; `C2` label under manipulation low (above high if bear); `C4` label anchored to T-Spot zone corner containing the C4 candle (fallback: C4 candle extreme).
- Projection ladder {-1,-2,-2.5,-4,-4.5} as R-multiples of |leg| from leg start (bull=up/bear=down), label+tick mid-chart + dotted full-width line at leg start (swing). `[Proj] Show Targets` default ON. ASSUMPTION: exact official anchor pixel-unverified; multiples per TTFM docs.
- Official 5-line panel (monospace, centered, no box): `SYM^! (TF)` / `TF-HTF Model` / `HH:MM:SS` (large, chart TZ) / `Bias: Bullish|Bearish` / `SMT(Auto): ES^!, YM^!`. `[Info] Show Concept Panel` + `[SMT] Detection` default ON. Arrows via \u2191/\u2193 escapes (ASCII source kept).
- New pure helpers (tested): c4Closure, projLadder, shortSym.

## DECISION (applied): T-Spot zone EQ = full-range wick-to-wick 50%
- Multi-frame pixel verification (frames 06/10/14/17, tool: tools/verify_tspot.py) showed the
  official zone EQ bound equals the FULL-RANGE mid (zone A within ~2px), while the conditional
  wick-mid did not (18px off). Zone B bottom remains unexplained by any contiguous candle range
  (likely swing/model-range 50% per official docs) - left as documented open item.
- Code: new pure `tspotEQ(CandleData)` = c.eq() (full-range mid) used by zone generation;
  conditional `candleEQ()` unchanged for the EQ line / bias / respected-half shade.
- Tests 113/111->113 pass; javac clean; ASCII pure.

## DECISION (applied): T-Spot zones gated to model C2/C3 candles (official guide)
- Guide: History counts past Fractal MODELS; video shows zones only on segments following
  closure candles (middle zone generator = C2 candle, C4-labeled zone generator = C3 candle).
- Code: `modelZoneGate(hist,g,structTrend)` pure + `modelZoneGen` instance (with real POI check);
  zone loop + current-zone creation gated. Option `[TSpot] Model C2/C3 Zones Only` default ON.
- Old conditional-EQ zone tests updated to full-range EQ expectations. Tests 117/117 pass.

## DECISION (applied): current-candle T-Spot zone always prints (user directive)
- User: the watch-now zone [Previous EQ .. Current Open] must appear within the CURRENT HTF
  candle span (e.g. 4H) because it is the zone to watch; history stays model-gated (C2/C3)
  per official History setting. Note: official video did NOT print a current zone when the
  generator candle was not a model candle (frame_17 segment [1218,..) empty) - documented
  deviation by user choice. Option `[TSpot] Current-Candle Zone Always` default ON.

## STEP 3 LOCKED (user: "اعتمد 3")
- CISD engine + protected-swing stop + gray/orange/red states + AddOns isolation all accepted.
- `[AddOns] Targets+States Master` independent of `[CISD] Detection` (user directive).
- Closure/HC vertical lines: BLACK + DOTTED default (closureColorIndex=4, closureStyleIndex=2).
- EQ equilibrium line decoupled: own `[EQ] Line Color` (blue) + `[EQ] Line Style` (dashed) options.
- Backup: _compilecheck/TTFMCore_step3_locked.java. Next: step 4 = IC early/late + target categories + bias/inversion.
