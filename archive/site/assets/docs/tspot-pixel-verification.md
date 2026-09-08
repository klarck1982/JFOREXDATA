# T-Spot Pixel-Space Verification (التحقق العددي من الفيديو)
Method: price->pixel mapping is affine (y = a - b*price), and every term of the conditional-EQ
formula is a MIDPOINT, so the whole formula is affine-invariant: it can be evaluated purely in
pixel coordinates and compared against the official zone rectangle pixels.
Source: `video-frames/frame_14.png` (1920x1080). Detector: sage-fill zones, gray HTF verticals,
green-ring bodies + eroded dark bodies, wick scans. Chart autoscale means comparisons are valid
WITHIN one frame only.

## Measured structure (frame_14)
- HTF verticals at x = 465, 716, 967, 1218 (zones align exactly to these segments).
- Zone A x[719,966] y[732,797] | Zone B x[970,1217] y[500,671] | left zone generator off-screen (skipped).
- Segment aggregates (1H candles composed): SEG0 n=11 yO671 yC683 yHi625 yLo947(965 true C2 tip)
  SEG1 n=14 yO731 yC602 yHi593 yLo862 | SEG2 n=18 yO500 yC375 yHi310 yLo638.

## Results
| Check | Official pixels | Predicted (our formula) | Delta | Verdict |
|---|---|---|---|---|
| Zone A top = open(next HTF) | 732 | 731 | 1px | MATCH |
| Zone B top = open(next HTF) | 500 | 500 | 0px | MATCH |
| Zone A bottom = EQ(prev HTF), wick-to-wick mid with true C2 wick (965) | 797 | (625+965)/2 = 795 | 2px | MATCH |
| Zone B bottom = EQ(prev HTF) | 671 | full-mid 727.5 / cond 796.5 | 56-125px | OPEN |
| Zone width = HTF segment, grows while forming | yes | yes | - | MATCH |
| Solid line on OPEN bound, dotted on EQ bound | yes | yes | - | MATCH |

## The open item (Zone B bottom)
Official 671px equals exactly (segHigh 593 + 749)/2, where 749 is the low of the THIRD 1H candle
of the generator segment - i.e. the official EQ for that zone excludes the later pullback wicks
(765..862) of the same segment. No aggregation variant of the full segment reproduces 671.
Possible explanations (cannot be separated from pixels alone, no price scale exists in the media):
1. The official anchor TF for that zone is NOT the vertical-line TF (verticals may be sessions).
2. The official freezes the zone's EQ from a PARTIAL candle at creation time (zone created early
   in the segment, before the pullback printed) - note zone B pixels moved across frames only via
   autoscale, consistent with a frozen price level.
3. Official EQ anchors to a sequence candle (C3/expansion) instead of the calendar HTF candle.

## Conclusion
Structure [EQ(N) .. Open(N+1)] + growth + boundary lines: CONFIRMED numerically (<=2px).
Conditional-EQ rule: CONFIRMED on Zone A (wick-to-wick mid of the manipulation segment).
Zone B EQ anchor: UNRESOLVED from media; requires on-chart comparison with real price values.
Suggested decisive test: run our indicator beside the official on the same chart/symbol and
compare printed zone lo/hi (our `tspotZoneD` / journal) against the official zone edges.
