# Official JForex Javadoc — Verified Facts (2026-09-10)

Verified directly from the official Javadoc (https://www.dukascopy.com/client/javadoc) on 2026-09-10.
These facts are NOT always visible in the wiki pages; they were checked to validate the SMT/journal
instrument-name handling in TTFMCore / TTFMEssence.

## com.dukascopy.api.Instrument

- `toString()` OVERRIDES `Enum.toString()` and returns the **display symbol in "CUR1/CUR2" format**
  (e.g. `EUR/USD`, `USATECH.IDX/USD`). Evidence:
  - javadoc: `toStringSet(Collection<Instrument>)` — "Returns set of strings, which are instruments
    in 'CUR1/CUR2' format".
  - javadoc: `getPairsSeparator()` — "Returns currency separator" (i.e. toString contains a separator).
  - JForexUtils (juxeii wiki, references the official API): `InstrumentUtil.toStringNoSeparator(Instrument.GBPAUD)`
    → "the instrument string without the slash '/', here it is 'GBPAUD'" — so the default form HAS the slash.
- `static Instrument fromString(String)` — "Returns corresponding instrument for string in
  'CUR1/CUR2' format" (or null if not found). **This is the documented way to map "EUR/USD" → Instrument.**
- `static Instrument fromInvertedString(String)` — inverted form.
- Real enum constants (from the official constant list):
  - Indices: `USA30IDXUSD`, `USA500IDXUSD`, `USATECHIDXUSD` — **no underscores** (NOT `USATECH_IDX_USD`).
  - Gold: `XAUUSD` exists; **`XAUEUR` does NOT exist** (0 occurrences in the official constant list).
  - Pairs: `EURUSD`, `GBPUSD`, ... (plain 6-letter names).
- `valueOf(String)` (inherited) requires the **enum constant name** (e.g. `EURUSD`), not the display form.

### Consequences for the TTFM code (validation results)

- `context.getFeedDescriptor().getInstrument().toString()` → `"EUR/USD"` / `"USATECH.IDX/USD"` style.
- TTFMCore `correlated()` (`.equals("EUR/USD")`, `.contains(".IDX")`) and `shortSym()`
  (`.equals("EUR/USD")`, `contains("USATECH")`) are **consistent with the real display form** → they work.
- TTFMCore `resolveInstrument()` two-stage fallback works for all instruments that exist:
  - `"USATECH.IDX/USD"` → try `USATECH_IDX_USD` (fails, constant is `USATECHIDXUSD`) → fallback
    strip-all → `USATECHIDXUSD` ✓.
  - `"EUR/USD"` → `EUR_USD` fails → `EURUSD` ✓.
  - `"XAU/EUR"` → both fail → null → peer skipped gracefully (XAUEUR doesn't exist anyway).
- Journal column `toString().replace("/","_").replace(".","_")` → `EUR_USD` / `USATECH_IDX_USD` ✓.
- Recommended (doc-compliant) simplification: `Instrument.fromString(sym)` instead of the valueOf chain.

## com.dukascopy.api.indicators.IIndicatorContext (real API)

Methods (official list): `getAccount`, `getChartInstruments`, `getConsole`, `getDataService`,
`getFeedDescriptor`, **`getFilesDir`** — "Returns directory where reading and writing is allowed",
`getHistory` — "Provides access to history from indicators", `getIndicatorChartPanel`,
**`getIndicatorsProvider`**, `getInstrument` (deprecated → use getFeedDescriptor().getInstrument()),
`getOfferSide` (deprecated), `getPeriod` (deprecated).

- The local compile-check stub is incomplete: it lacks `getIndicatorsProvider` (needed only to register
  an `IStopListener` via `addIndicatorStopListener` — see execute-code-on-method-stop.md).
- Our code does not need `onStop` (all IO is try-with-resources; no persistent resources).

## com.dukascopy.api.indicators.IndicatorInfo

- 9-arg constructor: `(name, title, groupName, overChart, overVolumes, unstablePeriod,
  numberOfInputs, numberOfOptionalInputs, numberOfOutputs)` — matches what TTFMEssence/TTFMCore pass.
- Relevant attributes: `unstablePeriod` (false is correct: our pivots are confirmed-only, deterministic),
  `recalculateAll` (default false is correct: incremental == full recompute, same bar order; the forming
  bar never participates in a confirmed pivot), `sparceIndicator` (recommended true for sparse/LEVEL
  outputs; our outputs are sparse — see code review note, optional).

## IHistory (real API)

- `List<IBar> getBars(Instrument, Period, OfferSide, long from, long to)` — available to indicators via
  `IIndicatorContext.getHistory()` (documented: "Provides access to history from indicators").
- Inputs declared with `setInstrument/setPeriod/setSide` have **no guarantee** of covering the same
  time range; empty inputs usually mean a re-calculation after data download (parameter-configuration.md).

## Threading

- "Every strategy runs in its own thread"; `IContext.executeTask` for cross-thread critical operations.
  Chart indicators run `calculate`/`drawOutput` on the platform's chart thread — spawning extra threads
  from an indicator is not an endorsed pattern. Our indicators spawn no threads (timer is display-only;
  `Clip.start()` is non-blocking) → compliant.

## Multiple platform instances (manual: jforex4-desktop/instances)

- Desktop JForex4 supports running **several platform instances** (separate JVMs) on the same computer.
- Indicators have NO official cross-instance/broadcast channel → **file-based coordination
  (SharedSMT.csv / SharedCISD.csv in getFilesDir) is the only viable mechanism** and is therefore
  doc-consistent. Cross-instance locking is impossible from indicator code; our design self-heals
  because every instance re-shares on every calculate.
