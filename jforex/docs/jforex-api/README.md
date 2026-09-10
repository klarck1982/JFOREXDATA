# JForex Official Indicator API - Saved Documentation

Source: https://www.dukascopy.com/wiki/en (development/indicator-api + practices + threading + JForex4 manuals).
Saved: 2026-09-10 for the full code review (TTFMCore / TTFMEssence).

- `iiindicator-methods.md` — IIndicator Methods (lifecycle, calculate, outputs) (22384 chars)
- `parameter-configuration.md` — Parameter Configuration (inputs/outputs/opt params) (29557 chars)
- `access-historical-data.md` — Access Historical Data (IHistory) (17837 chars)
- `indicators-with-base-period.md` — Indicators with base period (21991 chars)
- `platform-indicator-source-files.md` — Platform Indicator Source Files (18505 chars)
- `draw-outputs-idrawingindicator.md` — IDrawingIndicator - Draw Outputs (20715 chars)
- `execute-code-on-method-stop.md` — IStopListenerOnStop (18283 chars)
- `define-scale-iminmax.md` — IMinMax - scale of outputs (18847 chars)
- `example-simple-indicator.md` — Example: simple indicator (22168 chars)
- `example-indicator-calls-other.md` — Example: indicator calls other indicator (26716 chars)
- `example-uses-output-of-other.md` — Example: uses output of another indicator (21846 chars)
- `example-multiple-inputs.md` — Example: multiple inputs (21782 chars)
- `example-different-periods.md` — Example: inputs of different periods (21164 chars)
- `example-multiple-instruments.md` — Example: indicator for multiple instruments (23625 chars)
- `example-draws-chart-objects.md` — Example: draws chart objects (23652 chars)
- `example-prediction-line.md` — Example: prediction line (19642 chars)
- `indicator-usage-checklist.md` — Indicator Usage Checklist (18949 chars)
- `calculate-arbitrary-indicator.md` — Calculate arbitrary indicator (27462 chars)
- `threading.md` — Threading model (25198 chars)
- `manual-indicators.md` — JForex4 manual: Indicators (27248 chars)
- `manual-editor.md` — JForex4 manual: Code Editor (25964 chars)

- `multiple-instances.md` — JForex4 manual: Multiple instances (separate platform JVMs)
- `javadoc-verified-facts.md` — **Verified facts from the official Javadoc** (Instrument.toString format, real enum constants, IIndicatorContext methods) — the key reference for validating the SMT instrument-name mapping.

## Key pages for the code review (read first)

1. `javadoc-verified-facts.md` — verified API surface (Instrument, IIndicatorContext, IndicatorInfo).
2. `iiindicator-methods.md` — onStart / calculate / IndicatorResult / NaN-gap contract.
3. `parameter-configuration.md` — IndicatorInfo attributes (unstablePeriod, recalculateAll, sparceIndicator), OptInputParameterInfo list/range descriptions, OutputParameterInfo (drawnByIndicator, shift, ...).
4. `draw-outputs-idrawingindicator.md` — drawOutput contract, IIndicatorDrawingSupport methods (getXForTime, getYForValue semantics).
5. `access-historical-data.md` + `example-multiple-instruments.md` — IHistory access in indicators; official multi-instrument input pattern.
6. `indicator-usage-checklist.md` — official troubleshooting checklist (index 0 = unfinished bar, base period effect, timezones).
7. `execute-code-on-method-stop.md` — IStopListener registration (optional; not needed for us).
8. `threading.md` + `multiple-instances.md` — execution model; why file-based cross-chart sharing is the only official channel.
