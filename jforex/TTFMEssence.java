package com.dukascopy.indicators;

/*
 * ============================================================================
 *  TTFMEssence  -  MINIMAL-SETTINGS experimental build of the TTFM Fractal Model
 *  JForex / Dukascopy
 * ============================================================================
 *  Philosophy (user lock 2026-09-08): "only what I need on the chart". Every
 *  decision that was verbally locked in TTFMCore is FROZEN here as a constant.
 *  Correction 2026-09-08b: custom settings WILL grow by agreement; the option
 *  list starts with exactly ONE agreed option ([CISD] Min Wave Length).
 *  CISD valid/invalid states and TP lines stay ON by default (no option).
 *
 *  Mechanics are copied VERBATIM from TTFMCore.java (steps 1-3 locked,
 *  133/133 tests) so behaviour cannot drift; theory source remains official
 *  TTrades (ttrades.com). TTFMCore.java is untouched by this file.
 *
 *  FROZEN DECISIONS (each = a past verbal lock):
 *    L1 = 4H (6 candles, offset 10, right)   [Idea 1 lock]
 *    L2 = Daily (3 candles, offset 5, right) [Idea 1 lock]
 *    L3 = kept in skeleton but DISABLED      [Idea 1 lock]
 *    Layers AUTO-derived from the chart TF (freeze 2026-09-08d, official
 *      fractal pairings + user fix 30m->4H/D):
 *        1m->15m/1H | 5m->1H/4H | 15m->4H/D | 30m->4H/D | 1H->D/W
 *        4H->W/MN | D->MN/MN ; unmapped TFs floor to nearest mapped below
 *      (on a 15m chart this yields EXACTLY the old frozen 4H+D, so all past
 *       visual verifications remain valid)
 *    Anchor layer = Layer 1 (model TF) drives columns/T-Spot/legs [Anchor b]
 *    EQ rule = conditional wick-midpoint     [step-1 lock + official screenshot]
 *    T-Spot zone = [wickEQ(cN) .. open(cN+1)], current candle ALWAYS prints,
 *                  model C2/C3 gate ON       [step locks + user directive]
 *    Bounds/closure lines = DOTTED + BLACK   [user line-style directive]
 *    CISD engine = independent master, protected-swing stop, gray/orange/red
 *                  states, min wave 2, ignore inside bars, London+NY sessions
 *                  [step-3 lock]
 *    Projection ladder = R multiples {1,2,2.5,4,4.5} from manipulation leg
 *                  [official projections guide, projAnchor=0 lock]
 *    Panel time = YOUR live clock in chart timezone [panel-time correction]
 *    Bias = official NEXT-DAY MODEL on the DAILY candle, neutral possible
 *                  [user lock 2026-09-08]
 *    Chart bucketing TZ = Europe/Athens (EET+DST, agreement 2026-09-09),
 *      midnight anchor: == TV FOREX.com grid + JForex Day start EET, summer
 *      identical to the old GMT+3 freeze, winter fixes the 1h latent drift
 *    Display TZ (C labels + panel clock) = frozen New York: immune to the
 *      platform Day start setting, matches the axis + the user's TV (UTC-4)
 *    GRID LOCK (agreement 2026-09-09 - per-symbol TV grids, JForex op point
 *      Day start = EET): US100/US500/US30 + EURUSD/GBPUSD = EET (Athens);
 *      XAUUSD/XAUEUR (XAU/GOLD names) = Brussels. [Bucketing] Grid option
 *      {Auto/EET/Brussels/NY}, default Auto; panel always shows "Grid: X".
 *    SMT ENGINE (agreement 2026-09-10): intermarket divergence, panel-only.
 *      Confirmed fractal pivots (K=2) on CLOSED anchor buckets shared via
 *      SharedSMT.csv; verdicts vs peers (trio NQ/ES/YM, EU/GU, XAU pair =
 *      experimental "?"). [SMT] Detection default OFF = zero IO/state/drawing.
 *      Mixed-anchor peers ignored; v1 mechanical (no size filter - SMT is
 *      confirmation, model context is the filter). Removable as one block
 *      (see REMOVAL note at the SMT ENGINE block).
 *
 *  THE NINE APPROVED ELEMENTS (user lock "approve the list", 2026-09-08;
 *  item 7 removed 2026-09-08b: the orange liquidity tags in the screenshot
 *  were PLATFORM drawings, never indicator output):
 *    1 C-columns: label Cn (HH:mm-HH:mm) + dotted vertical + alternate shade
 *    2 4H candle cluster (right edge, label "4H")
 *    3 Daily candle cluster (right edge, label "D")
 *    4 historical T-Spot zones (green BUY-support / red SELL-support)
 *    5 current-candle T-Spot zone, always printed, grows with the candle
 *    6 dotted zone bounds (open bound + EQ bound)
 *    7 CISD label on chart TF (+/-Cisd) + entry line + dotted protected swing
 *      (correction 2026-09-08c: state colors valid/tighten/failed and TP lines
 *       are OFF - engine still computes them internally, nothing is drawn)
 *    8 projection ladder (5 gray levels + dotted leg-start line)
 *    9 info panel: symbol line, model line, grid line, YOUR live clock, Bias line
 *
 *  [C]=official concept  [J]=Java reference mechanics  [O]=our definition
 *
 *  SESSION OVERLAY (user agreement 2026-09-09 - backtest aid, OFF by default):
 *    [Sessions] Show NY Open Line: dotted BLACK vertical at 08:00 New York on
 *      each weekday + black badge tag "NY 8:00" on its own row below the
 *      C-labels (agreement 2026-09-09A); Sat/Sun skipped.
 *    [Sessions] Show Session in Panel: "Session: Asia/London/NY/Closed" line
 *      appended to the info panel (existing sessionOf on the last bar).
 *    NY time via the real America/New_York calendar (DST-safe, no fixed
 *    offset); EET/4H chart bucketing untouched.
 *
 *  COUNTDOWN TIMER (user agreement 2026-09-10, D6 decision - removable):
 *    [Display] Show Timer (default ON): wall-clock HH:MM:SS countdown above each
 *    cluster's TF label until the CURRENT layer candle closes. 4H and D each get
 *    one, anchored on the cluster's TALLEST candle, white on translucent green/red
 *    of that candle's color. Wall clock (System.currentTimeMillis) per D6: smooth
 *    in live, consistent with the frozen panel clock, position VERBATIM from Core's
 *    Show Timer; NOT drawn in replay (wall clock already past candle close) -
 *    documented caveat, not a bug. Agreed deviation from Core: color follows the
 *    ANCHORED (tallest) candle - Core's Show Timer uses the cluster's first candle.
 *    REMOVAL (if it fails the requirement): delete the showTimer field, the
 *    [Display] Show Timer option (2 lines), the countdown draw block in
 *    drawOutput, countdownText + its tests. Zero residue = green suites.
 *
 *  ================= REFERENCE CLONE: CISD DESK (freeze 2026-09-08e) ========
 *  User lock: "clone the CISD concept with its cards, settings, filters,
 *  storage, sharing, Grade and Info - HigherTFCandles.txt is the ONLY
 *  reference for the CISD idea". Eight subsystems cloned verbatim in spirit:
 *    1 six filters (trend EMA50/200, fib retrace, market structure,
 *      higher-TF confirmation, momentum, volume profile)
 *    2 Grade presets Standard(AND)/Premium(>=60%)/Ultimate(>=75%)
 *    3 storage ring MAX_CISD_STORED=3 + properties save/load
 *    4 cross-chart sharing via SharedCISD.csv + shared alert cards panel
 *    5 sounds alert.wav / retest.wav / None (javax.sound, user.dir)
 *    6 cards: entry line + label + filter badges + journal decision tag
 *    7 journal: HigherTF_Signals.csv + separate CISD_Journal_Decisions.csv
 *    8 the [CISD] option group (15 options, reference names)
 *  ============================================================================
 */

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.Properties;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;

import com.dukascopy.api.IBar;
import com.dukascopy.api.Period;
import com.dukascopy.api.indicators.IIndicator;
import com.dukascopy.api.indicators.IIndicatorContext;
import com.dukascopy.api.indicators.IDrawingIndicator;
import com.dukascopy.api.indicators.IIndicatorDrawingSupport;
import com.dukascopy.api.indicators.IndicatorInfo;
import com.dukascopy.api.indicators.IndicatorResult;
import com.dukascopy.api.indicators.InputParameterInfo;
import com.dukascopy.api.indicators.OutputParameterInfo;

public class TTFMEssence implements IIndicator, IDrawingIndicator {

    // ================== frozen constants ==================
    static final int MAX_CANDLES = 10;
    static final int MAX_LAYERS  = 3;

    static final int    PERIOD_COUNT = 8;
    static final long[] PERIOD_INTERVALS = {
        15*60*1000L, 30*60*1000L, 60*60*1000L,
        4*60*60*1000L, 24*60*60*1000L, 7*60*60*1000L,
        7*24*60*60*1000L, 30*24*60*60*1000L
    };
    static final String[] SHORT_LABELS = {"15m","30m","1H","4H","D","7H","W","MN"};
    static final long W1I  = 7*24*60*60*1000L;
    static final long MN1I = 30*24*60*60*1000L;

    // frozen look
    private static final Color WICK_COLOR   = Color.BLACK;
    private static final Color BORDER_COLOR = Color.BLACK;
    private static final Color BULLISH_BODY_COLOR = new Color(0,180,0);
    private static final Color BEARISH_BODY_COLOR = new Color(30,30,30);
    private static final Color CISD_BULL_COLOR = new Color(0,120,255);
    private static final Color CISD_BEAR_COLOR = new Color(255,60,60);
    private static final Color STATE_VALID   = new Color(150,150,150);
    private static final Color STATE_TIGHTEN = new Color(255,140,0);
    private static final Color STATE_FAILED  = new Color(220,40,40);
    private static final Color CLOSURE_COLOR = Color.BLACK;      // locked: black
    private static final Color BOUND_COLOR   = Color.BLACK;      // locked: black

    // ================== platform-free raw bar ==================
    static final class RB {
        final long time; final double o,h,l,c; final double vol;
        RB(long t,double o,double h,double l,double c,double v){time=t;this.o=o;this.h=h;this.l=l;this.c=c;vol=v;}
    }
    private static RB toRB(IBar b){ return new RB(b.getTime(),b.getOpen(),b.getHigh(),b.getLow(),b.getClose(),b.getVolume()); }

    static final class CandleData {
        long openTime; boolean completed;
        double open,high,low,close;
        CandleData(double o,double h,double l,double c,long ot,boolean done){open=o;high=h;low=l;close=c;openTime=ot;completed=done;}
        double eq(){ return (high+low)/2.0; }
        /** [C, confirmed by official chart] conditional candle EQ (locked step 1). */
        double candleEQ(){
            double body=Math.abs(close-open);
            double lowerWick=Math.min(open,close)-low;
            double upperWick=high-Math.max(open,close);
            if (Math.max(lowerWick,upperWick) <= body) return (high+low)/2.0;
            if (lowerWick > upperWick) return (low+Math.min(open,close))/2.0;
            return (high+Math.max(open,close))/2.0;
        }
    }

    static final class LayerData {
        boolean enabled=false; int periodIndex=0; int candlesToShow=4; int candleOffset=5;
        List<CandleData> historical = new ArrayList<>();
        double curO=Double.NaN,curH=Double.NaN,curL=Double.NaN,curC=Double.NaN;
        long curStart=0; boolean curActive=false;
    }

    // ---------- AUTO layers: official fractal pairings + user 30m fix ----------
    private static final long[] MAP_CHART = {60000L,300000L,900000L,1800000L,3600000L,14400000L,86400000L};
    private static final int[][] MAP_LAYERS = {{0,2},{2,3},{3,4},{3,4},{4,6},{6,7},{7,7}};
    /** {modelLayerIdx, biasLayerIdx} for a chart interval; unmapped floors down. */
    static int[] autoLayers(long chartMs){
        int pick=0;
        for (int i=0;i<MAP_CHART.length;i++) if (MAP_CHART[i]<=chartMs) pick=i;
        return MAP_LAYERS[pick];
    }
    /** column time-range pattern: HH:mm for intraday/daily, dd/MM for W & MN. */
    static String colRangePattern(long periodMs){ return (periodMs>=W1I)?"dd/MM":"HH:mm"; }

    // ================== frozen settings (no option inputs exist) ==================
    private final LayerData[] layers = new LayerData[MAX_LAYERS];
    {
        for (int i=0;i<MAX_LAYERS;i++) layers[i]=new LayerData();
        layers[0].enabled=true;  layers[0].periodIndex=3; layers[0].candlesToShow=6; layers[0].candleOffset=10; // 4H
        layers[1].enabled=true;  layers[1].periodIndex=4; layers[1].candlesToShow=3; layers[1].candleOffset=5;  // D
        layers[2].enabled=false; layers[2].periodIndex=5; layers[2].candlesToShow=3;                            // OFF
    }
    private static final int ANCHOR_LAYER = 1;          // 4H unified reference
    private static final int BIAS_LAYER   = 2;          // Daily = Next-Day Model source
    // Bucketing TZ: EET with EU DST (agreement 2026-09-09, TV match). Summer =
    // UTC+3 (identical to the old GMT+3 freeze, all past verifications stand);
    // winter = UTC+2 (fixes the 1h latent drift vs platform + TV). JForex
    // operating point stays Day start = EET; TV FOREX.com grid proven == EET.
    static final TimeZone BUCKET_TZ = TimeZone.getTimeZone("Europe/Athens");
    // Per-symbol TV grids (agreement 2026-09-09): XAU/GOLD = Brussels, US
    // indices + EUR/GBP majors = EET(Athens); NY exists for manual override.
    static final TimeZone BUCKET_BRUSSELS = TimeZone.getTimeZone("Europe/Brussels");
    static final TimeZone BUCKET_NY = TimeZone.getTimeZone("America/New_York");
    // Display TZ: frozen New York (agreement 2026-09-09). Labels + panel clock
    // no longer follow the platform Day start setting; they match the axis
    // (Time zone = New York) and the user's TV chart (UTC-4).
    private static final TimeZone DISPLAY_TZ = TimeZone.getTimeZone("America/New_York");
    private static final int HTF_ANCHOR = 0;            // midnight of BUCKET_TZ
    private static final boolean CISD_IGNORE_INSIDE = true;
    private static final int ACTIVE_SESSIONS = 1;       // London + NY

    // ================== CISD Desk options (reference group) ==================
    int cisdSensitivity = 1;   // 0=Low(min3) 1=Medium(min2) 2=High(min1)
    static int minWaveFor(int sens){ return (sens==0)?3:(sens==1)?2:1; }
    private interface OptInputSetter { void set(Object v); }
    private static final int[] SENS_VALUES = {0,1,2};
    private static final String[] SENS_NAMES = {"Low (min 3 candles)","Medium (min 2 candles)","High (min 1 candle)"};
    private static final int[] GRID_VALUES = {0,1,2,3};
    private static final String[] GRID_NAMES = {"Auto (symbol-locked)","EET (Athens)","Brussels","New York"};
    private static final int[] BOOLEAN_VALUES = {0,1};
    private static final String[] BOOLEAN_NAMES = {"No","Yes"};
    private static final int[] RESET_VALUES = {0,1};
    private static final String[] RESET_NAMES = {"No","Reset"};
    private static final int[] GRADE_VALUES = {0,1,2};
    private static final String[] GRADE_NAMES = {"Standard","Premium","Ultimate"};
    private static final int[] MOM_VALUES = {0,1,2,3};
    private static final String[] MOM_NAMES = {"Off","Low","Medium","High"};
    private static final int[] TREND_VALUES = {0,1,2};
    private static final String[] TREND_NAMES = {"Off","EMA 50","EMA 200"};
    private static final int[] HTF_CONF_VALUES = {0,1,2,3};
    private static final String[] HTF_CONF_NAMES = {"Off","Layer 1","Layer 2","Layer 3"};
    private static final int[] FIB_VALUES = {0,1,2,3,4};
    private static final String[] FIB_NAMES = {"None","Fib 23.6%","Fib 38.2%","Fib 50%","Fib 61.8%"};
    private static final double[] FIB_RATIOS = {0.0,0.236,0.382,0.50,0.618};
    private static final int[] MS_VALUES = {0,1};
    private static final String[] MS_NAMES = {"Off","On"};
    private static final int[] SOUND_VALUES = {0,1,2};
    private static final String[] SOUND_NAMES = {"alert.wav","retest.wav","None"};
    private static final String[] SOUND_FILES = {"alert.wav","retest.wav","None"};
    private static final int MAX_CISD_STORED = 3;
    private static final Color RETEST_COLOR = new Color(100,150,255);

    // Desk fields (reference names)
    boolean showCISD = true;
    int cisdGrade = 0;              // 0 Standard(manual) 1 Premium 2 Ultimate
    int momentumFilter = 0;
    boolean volumeFilterEnabled = false;
    int trendFilter = 0;
    int higherTFConfirmation = 0;
    int minRetracement = 0;
    int marketStructureFilter = 0;
    double minVolumeRatio = 1.2;
    String cisdAlertSound = "alert.wav";
    String cisdRetestSound = "retest.wav";
    boolean sharedCISDAlerts = true;
    boolean showEntryPrice = true;
    int maxSharedLines = 5;
    boolean saveLoadCISD = true;
    boolean cisdLoaded = false;

    // Session overlay (agreement 2026-09-09): both OFF by default
    boolean showNyOpenLine = false;
    boolean showSessionInPanel = false;
    // Bucketing grid (agreement 2026-09-09): 0=Auto(symbol) 1=EET 2=Brussels 3=NY
    int gridMode = 0;
    // Countdown timer (agreement 2026-09-10, D6 = wall clock): HH:MM:SS above each
    // cluster's TF label until the current layer candle closes. Default ON.
    // REMOVABLE (user condition 2026-09-10): if it fails the requirement, delete the
    // showTimer field + its option (2 lines) + the countdown draw block in drawOutput
    // + countdownText + its tests -> zero residue, green suites = proof.
    boolean showTimer = true;
    private long sharedFileLastModified = 0;
    private final Object sharedFileLock = new Object();
    private final List<String[]> sharedAlertLines = new ArrayList<>();
    private final Map<String,String> journalDecisions = new java.util.HashMap<>();
    private long journalDecisionsLastModified = 0;
    private long fibCacheWaveStart = -1;
    private double[] fibCacheResult = null;

    static final class PendingCisdSetup {
        boolean active=false; long waveStartTime=0; int waveStartIdx=-1;
        double triggerOpen=Double.NaN; double stopLevel=Double.NaN;
    }
    private final PendingCisdSetup pendingBullish = new PendingCisdSetup();
    private final PendingCisdSetup pendingBearish = new PendingCisdSetup();

    int cisdStoredCount = 0;
    long[] cisdStoredStartTimes = new long[MAX_CISD_STORED];
    long[] cisdStoredEndTimes = new long[MAX_CISD_STORED];
    double[] cisdStoredLevels = new double[MAX_CISD_STORED];
    boolean[] cisdStoredBullish = new boolean[MAX_CISD_STORED];
    double[] cisdStoredStopLevels = new double[MAX_CISD_STORED];
    long[] cisdStoredActivationTime = new long[MAX_CISD_STORED];
    long[] cisdStoredBreakoutTime = new long[MAX_CISD_STORED];
    boolean[] cisdStoredLogged = new boolean[MAX_CISD_STORED];
    boolean[] cisdStoredRetestPlayed = new boolean[MAX_CISD_STORED];
    boolean[] cisdStoredConfirmed = new boolean[MAX_CISD_STORED];
    boolean[] cisdStoredHigherTFAligned = new boolean[MAX_CISD_STORED];
    String[] cisdStoredConfirmingTFLabel = new String[MAX_CISD_STORED];
    boolean[] cisdStoredFibPassed = new boolean[MAX_CISD_STORED];
    String[] cisdStoredFibLabel = new String[MAX_CISD_STORED];
    boolean[] cisdStoredTrendPassed = new boolean[MAX_CISD_STORED];
    boolean[] cisdStoredMomentumPassed = new boolean[MAX_CISD_STORED];
    boolean[] cisdStoredVolumePassed = new boolean[MAX_CISD_STORED];
    boolean[] cisdStoredMarketStructurePassed = new boolean[MAX_CISD_STORED];
    String[] cisdStoredFilterSymbols = new String[MAX_CISD_STORED];
    boolean[] cisdStoredTrendActive = new boolean[MAX_CISD_STORED];
    boolean[] cisdStoredFibActive = new boolean[MAX_CISD_STORED];
    boolean[] cisdStoredMSActive = new boolean[MAX_CISD_STORED];
    boolean[] cisdStoredHTFActive = new boolean[MAX_CISD_STORED];
    boolean[] cisdStoredMomVolActive = new boolean[MAX_CISD_STORED];

    private com.dukascopy.api.indicators.OptInputParameterInfo[] optInfos;
    private OptInputSetter[] optSetters;
    { buildOptions(); }   // instance initializer: options ready without onStart

    // ================== runtime ==================
    private IIndicatorContext context;
    private IBar[][] inputs = new IBar[1][];
    private Object[] outputs;
    private IndicatorInfo indicatorInfo;
    private InputParameterInfo[] inputParameterInfos;
    private OutputParameterInfo[] outputParameterInfos;
    private TimeZone nyTZ = TimeZone.getTimeZone("America/New_York");
    // [reference] NY-time formatters for the journal CSV (declared after nyTZ on purpose)
    private final SimpleDateFormat nyTimeFormat = nyFmt("yyyy-MM-dd HH:mm:ss");
    private final SimpleDateFormat dayFormat = nyFmt("EEE");
    private SimpleDateFormat nyFmt(String pattern){
        SimpleDateFormat f=new SimpleDateFormat(pattern, java.util.Locale.US);
        f.setTimeZone(nyTZ);
        return f;
    }

    private long lastChartPeriodMs = -1;
    private String lastGridId = "";
    private String lastInstrument = "";
    private int currentBias = 0;
    private boolean currentInversion = false;
    private double chartAvgRange = 0;
    private final List<double[]> fvgZones = new ArrayList<>();   // internal POI gate only, NOT drawn
    private int obBullIdx=-1, obBearIdx=-1;
    private int structTrend=0;
    private int c2Idx=-1;
    private long legT1=0,legT2=0,legT3=0; private double legP1=Double.NaN,legP2=Double.NaN,legP3=Double.NaN;
    private final List<double[]> tspotZones=new ArrayList<>();  // {lo,hi,start,end,type,state,current}
    private long chartNowTime = 0;
    private long chartPeriodMs = 0;
    private LayerData drawAnchor = null;
    private long[] drawTimes = null;   // chart bar times for the session overlay

    // ==================================================================
    //  PURE LOGIC CORE (verbatim from locked TTFMCore)
    // ==================================================================
    static long periodStart(long timeMs, long intervalMs, TimeZone tz){
        Calendar cal = Calendar.getInstance(tz);
        cal.setTimeInMillis(timeMs);
        if (intervalMs == 24*60*60*1000L){
            cal.set(Calendar.HOUR_OF_DAY,0);cal.set(Calendar.MINUTE,0);cal.set(Calendar.SECOND,0);cal.set(Calendar.MILLISECOND,0);
            return cal.getTimeInMillis();
        }
        if (intervalMs == 7*60*60*1000L){
            int h = cal.get(Calendar.HOUR_OF_DAY);
            cal.set(Calendar.HOUR_OF_DAY,(h/7)*7);cal.set(Calendar.MINUTE,0);cal.set(Calendar.SECOND,0);cal.set(Calendar.MILLISECOND,0);
            return cal.getTimeInMillis();
        }
        if (intervalMs == W1I){   // calendar week: Monday 00:00 of tz
            cal.setFirstDayOfWeek(Calendar.MONDAY);
            cal.set(Calendar.DAY_OF_WEEK,Calendar.MONDAY);
            cal.set(Calendar.HOUR_OF_DAY,0);cal.set(Calendar.MINUTE,0);cal.set(Calendar.SECOND,0);cal.set(Calendar.MILLISECOND,0);
            return cal.getTimeInMillis();
        }
        if (intervalMs == MN1I){  // calendar month: 1st 00:00 of tz
            cal.set(Calendar.DAY_OF_MONTH,1);
            cal.set(Calendar.HOUR_OF_DAY,0);cal.set(Calendar.MINUTE,0);cal.set(Calendar.SECOND,0);cal.set(Calendar.MILLISECOND,0);
            return cal.getTimeInMillis();
        }
        cal.set(Calendar.HOUR_OF_DAY,0);cal.set(Calendar.MINUTE,0);cal.set(Calendar.SECOND,0);cal.set(Calendar.MILLISECOND,0);
        long dayStart = cal.getTimeInMillis();
        return dayStart + ((timeMs-dayStart)/intervalMs)*intervalMs;
    }

    static boolean isBull(RB b){ return b.c>b.o; }
    static boolean isBear(RB b){ return b.c<b.o; }
    static boolean isInside(RB cur,RB prev){ return cur.h<=prev.h && cur.l>=prev.l; }

    static int[] findWave(RB[] bars,int startIdx,int maxLookback,boolean bearishWave,boolean ignoreInside){
        int waveEnd=-1, waveStart=-1;
        for (int i=startIdx;i>=0 && (startIdx-i)<=maxLookback;i--){
            boolean cond = bearishWave?isBear(bars[i]):isBull(bars[i]);
            if (!cond){
                if (ignoreInside && i>0 && isInside(bars[i],bars[i-1])) continue;
                if (waveEnd!=-1) break;
            } else { if (waveEnd==-1) waveEnd=i; waveStart=i; }
        }
        return (waveEnd!=-1)?new int[]{waveStart,waveEnd}:new int[]{-1,-1};
    }

    static double[] detectSweep(CandleData c1, CandleData c2){
        if (c2.low<c1.low && c2.close>c1.close)  return new double[]{1,c1.low};
        if (c2.high>c1.high && c2.close<c1.close) return new double[]{0,c1.high};
        return null;
    }

    static String sessionOf(long timeMs, TimeZone ny){
        Calendar cal=Calendar.getInstance(ny); cal.setTimeInMillis(timeMs);
        int h=cal.get(Calendar.HOUR_OF_DAY);
        if (h>=18||h<1) return "Asia";
        if (h>=1&&h<8)  return "London";
        if (h>=8&&h<17) return "NY";
        return "Closed";
    }
    static boolean sessionAllowed(String s,int activeSessions){
        if (activeSessions==0) return true;
        switch (activeSessions){
            case 1: return s.equals("London")||s.equals("NY");
            case 2: return s.equals("London");
            case 3: return s.equals("NY");
            default: return true;
        }
    }

    /** [O session overlay] absolute millis of 08:00 New York on the NY-calendar
     *  day containing timeMs. DST-safe: real America/New_York calendar, never a
     *  fixed offset (08:00 itself is never ambiguous on DST switch days). */
    static long nyOpenMillis(long timeMs, TimeZone ny){
        Calendar cal=Calendar.getInstance(ny); cal.setTimeInMillis(timeMs);
        cal.set(Calendar.HOUR_OF_DAY,8);cal.set(Calendar.MINUTE,0);cal.set(Calendar.SECOND,0);cal.set(Calendar.MILLISECOND,0);
        return cal.getTimeInMillis();
    }
    /** [O session overlay] next NY-day 08:00 after the given open (exactly one
     *  NY calendar day ahead: 23/24/25h across DST switches). */
    static long nextNyOpen(long openMillis, TimeZone ny){
        return nyOpenMillis(openMillis+24*60*60*1000L,ny);
    }
    /** [O session overlay] Sat/Sun NY days print no open line (no backtest bars). */
    static boolean isNyWeekend(long openMillis, TimeZone ny){
        Calendar cal=Calendar.getInstance(ny); cal.setTimeInMillis(openMillis);
        int d=cal.get(Calendar.DAY_OF_WEEK);
        return d==Calendar.SATURDAY||d==Calendar.SUNDAY;
    }
    /** [O grid lock 2026-09-09] symbol -> TV grid. XAU/GOLD trade the Brussels
     *  grid; US indices + EUR/GBP majors trade EET (all proven vs TV); anything
     *  unknown defaults to EET (always visible in panel, override exists). */
    static TimeZone autoGrid(String instrument){
        if (instrument!=null){
            String u=instrument.toUpperCase(java.util.Locale.US);
            if (u.contains("XAU")||u.contains("GOLD")) return BUCKET_BRUSSELS;
        }
        return BUCKET_TZ;
    }
    /** [O] effective grid: 0=Auto(symbol), 1=EET, 2=Brussels, 3=NY. */
    static TimeZone resolveGrid(int mode,String instrument){
        if (mode==1) return BUCKET_TZ;
        if (mode==2) return BUCKET_BRUSSELS;
        if (mode==3) return BUCKET_NY;
        return autoGrid(instrument);
    }
    /** [O] short panel tag, e.g. "EET-auto" / "Brussels-manual". */
    static String gridTag(int mode,String instrument){
        TimeZone tz=resolveGrid(mode,instrument);
        String base=tz==BUCKET_BRUSSELS?"Brussels":tz==BUCKET_NY?"NY":"EET";
        return base+(mode==0?"-auto":"-manual");
    }
    /** [O timer 2026-09-10] wall-clock countdown text HH:MM:SS; empty when nothing
     *  is left (remain<=0 - e.g. replay, where the wall clock is already past). */
    static String countdownText(long remainMs){
        if (remainMs<=0) return "";
        long h=remainMs/3600000L, m=(remainMs%3600000L)/60000L, s=(remainMs%60000L)/1000L;
        return String.format(java.util.Locale.US,"%02d:%02d:%02d",h,m,s);
    }

    // ================== SMT ENGINE (optional module; agreement 2026-09-10) ==================
    // Intermarket SMT divergence via SharedSMT.csv (confirmed anchor-TF fractal pivots).
    // Output: panel line only ("SMT: ES\u2193!" / "SMT: \u2014"). showSMT=false (default) =
    // zero file IO, zero state, zero drawing (functionally absent).
    // Pivots: fractal K=2 on CLOSED anchor buckets, strict-left/inclusive-right (same
    // convention as findLastTwoSwings), confirmed-only -> never repaints.
    // Pairing: base swings within +/-2 anchor bars (ms); taker extension fresh (<=3 bars).
    // Peer anchorMs MUST equal own (mixed-TF peers ignored). Gold pair = experimental ("?").
    // REMOVAL (if SMT fails): delete this whole block + "[SMT] Detection" option in
    //   buildOptions (2 lines) + "if (showSMT) updateSmt();" in calculate (1 line) +
    //   "if (showSMT){...}" SMT line in drawInfoPanel (1 line) + SMT test block in
    //   TTFMEssenceTest. Recompile + green suites = proof of zero residue.
    static final int SMT_PIVOT_K = 2;
    static final int SMT_WINDOW_BARS = 2;
    static final int SMT_FRESH_BARS = 3;
    static final int SMT_RING = 30;
    boolean showSMT = false;                       // [SMT] Detection (default OFF)
    String smtPanelLine = "\u2014";
    private final Object smtFileLock = new Object();
    static final class SmtPivot {
        long t; double p; boolean high;
        SmtPivot(long t,double p,boolean high){this.t=t;this.p=p;this.high=high;}
    }
    /** [SMT] instrument -> key: NQ/ES/YM/EU/GU/XAUUSD/XAUEUR, else "". XAU first (XAUEUR contains EUR). */
    static String smtKey(String instrument){
        if (instrument==null) return "";
        String u=instrument.toUpperCase(java.util.Locale.US);
        if (u.contains("XAU")||u.contains("GOLD")) return u.contains("EUR")?"XAUEUR":"XAUUSD";
        if (u.contains("TECH")||u.contains("USTEC")||u.contains("US100")||u.contains("NAS")) return "NQ";
        if (u.contains("500")||u.contains("SPX")) return "ES";
        if (u.contains("US30")||u.contains("USA30")||u.contains("DOW")) return "YM";
        if (u.contains("EUR")&&u.contains("USD")) return "EU";
        if (u.contains("GBP")&&u.contains("USD")) return "GU";
        return "";
    }
    /** [SMT] peers per key (evaluation order). */
    static String[] smtPeers(String key){
        if ("NQ".equals(key)) return new String[]{"ES","YM"};
        if ("ES".equals(key)) return new String[]{"NQ","YM"};
        if ("YM".equals(key)) return new String[]{"NQ","ES"};
        if ("EU".equals(key)) return new String[]{"GU"};
        if ("GU".equals(key)) return new String[]{"EU"};
        if ("XAUUSD".equals(key)) return new String[]{"XAUEUR"};
        if ("XAUEUR".equals(key)) return new String[]{"XAUUSD"};
        return new String[0];
    }
    /** [SMT] gold pair is experimental -> "?" suffix on tokens. */
    static boolean smtExperimental(String key){ return key!=null&&key.startsWith("XAU"); }
    /** [SMT] fractal pivots on parallel arrays (oldest->newest). Indices [K,n-1-K] only
     *  (confirmed, never repaints). Plateau -> leftmost pivot (strict-left/inclusive-right). */
    static List<SmtPivot> smtPivots(long[] times,double[] hi,double[] lo){
        List<SmtPivot> out=new ArrayList<>();
        if (times==null||hi==null||lo==null) return out;
        int n=Math.min(times.length,Math.min(hi.length,lo.length));
        int k=SMT_PIVOT_K;
        if (n<2*k+1) return out;
        for (int i=k;i<=n-1-k;i++){
            boolean h=true,l=true;
            for (int j=i-k;j<=i+k;j++){
                if (j==i) continue;
                if (j<i){ if (hi[j]>=hi[i]) h=false; if (lo[j]<=lo[i]) l=false; }
                else    { if (hi[j]>hi[i])  h=false; if (lo[j]<lo[i])  l=false; }
            }
            if (h) out.add(new SmtPivot(times[i],hi[i],true));
            if (l) out.add(new SmtPivot(times[i],lo[i],false));
        }
        return out;
    }
    /** [SMT] last two pivots of a side, {prior,latest}, or null. */
    static SmtPivot[] smtLastTwo(List<SmtPivot> pivots,boolean sideHigh){
        SmtPivot a=null,b=null;
        if (pivots!=null) for (SmtPivot p:pivots){
            if (p.high!=sideHigh) continue;
            a=b; b=p;
        }
        if (a==null||b==null) return null;
        return new SmtPivot[]{a,b};
    }
    /** [SMT] divergence verdict for one side: +1 bullish (lows), -1 bearish (highs), 0 none.
     *  Base swings must align (|dt|<=2 anchor bars); exactly one side takes out; taker's
     *  extension fresh (<=3 anchor bars old). Both/neither take = agreement = 0. */
    static int smtVerdictSide(List<SmtPivot> own,List<SmtPivot> peer,boolean sideHigh,long anchorMs,long nowMs){
        if (anchorMs<=0) return 0;
        SmtPivot[] o=smtLastTwo(own,sideHigh),p=smtLastTwo(peer,sideHigh);
        if (o==null||p==null) return 0;
        if (Math.abs(o[0].t-p[0].t)>SMT_WINDOW_BARS*anchorMs) return 0;
        boolean ownTook=sideHigh?o[1].p>o[0].p:o[1].p<o[0].p;
        boolean peerTook=sideHigh?p[1].p>p[0].p:p[1].p<p[0].p;
        if (ownTook==peerTook) return 0;
        long takerT=ownTook?o[1].t:p[1].t;
        if (nowMs-takerT>SMT_FRESH_BARS*anchorMs) return 0;
        return sideHigh?-1:1;
    }
    /** [SMT] CSV line: key,side(H/L),openTime,price,anchorMs. */
    static String smtFormatLine(String key,boolean high,long t,double price,long anchorMs){
        return key+","+(high?"H":"L")+","+t+","+price+","+anchorMs;
    }
    /** [SMT] parse peer pivots (time-ascending), same-anchorMs only; corrupt lines ignored. */
    static List<SmtPivot> smtParsePeer(List<String[]> lines,String peerKey,long anchorMs){
        List<SmtPivot> out=new ArrayList<>();
        if (lines==null||peerKey==null||peerKey.isEmpty()) return out;
        for (String[] l:lines){
            if (l==null||l.length<5||!peerKey.equals(l[0])) continue;
            try {
                boolean h="H".equals(l[1]);
                if (!h&&!"L".equals(l[1])) continue;
                long t=Long.parseLong(l[2]); double p=Double.parseDouble(l[3]); long a=Long.parseLong(l[4]);
                if (a!=anchorMs) continue;
                out.add(new SmtPivot(t,p,h));
            } catch (Exception e){ }
        }
        for (int i=1;i<out.size();i++){
            SmtPivot v=out.get(i); int j=i-1;
            while (j>=0&&out.get(j).t>v.t){ out.set(j+1,out.get(j)); j--; }
            out.set(j+1,v);
        }
        return out;
    }
    static long smtLineTime(String[] l){
        if (l==null||l.length<5) return 0;
        try { return Long.parseLong(l[2]); } catch (Exception e){ return 0; }
    }
    private File getSmtPath(){ return new File(filesDir(),"SharedSMT.csv"); }
    private List<String[]> readSmtLinesInternal(){
        List<String[]> lines=new ArrayList<>();
        File f=getSmtPath();
        if (!f.exists()) return lines;
        try (BufferedReader br=new BufferedReader(new FileReader(f))){
            String line;
            while ((line=br.readLine())!=null){
                String[] parts=line.split(",");
                if (parts.length>=5) lines.add(new String[]{parts[0],parts[1],parts[2],parts[3],parts[4]});
            }
        } catch (IOException e){ }
        return lines;
    }
    /** [SMT] share newly-confirmed own pivots (file rewritten only when something new). */
    private void smtShare(String key,List<SmtPivot> own,long anchorMs){
        synchronized (smtFileLock){
            List<String[]> lines=readSmtLinesInternal();
            boolean added=false;
            for (SmtPivot p:own){
                boolean seen=false;
                for (String[] l:lines){
                    if (l.length>=5&&key.equals(l[0])
                        &&((p.high&&"H".equals(l[1]))||(!p.high&&"L".equals(l[1])))
                        &&smtLineTime(l)==p.t){ seen=true; break; }
                }
                if (!seen){
                    lines.add(new String[]{key,p.high?"H":"L",String.valueOf(p.t),
                        String.valueOf(p.p),String.valueOf(anchorMs)});
                    added=true;
                }
            }
            if (!added) return;
            Map<String,List<String[]>> g=new java.util.HashMap<String,List<String[]>>();
            for (String[] l:lines){
                String k=l[0]+"|"+l[1];
                if (!g.containsKey(k)) g.put(k,new ArrayList<String[]>());
                g.get(k).add(l);
            }
            lines.clear();
            for (List<String[]> grp:g.values()){
                for (int i=1;i<grp.size();i++){
                    String[] v=grp.get(i); long vt=smtLineTime(v); int j=i-1;
                    while (j>=0&&smtLineTime(grp.get(j))<vt){ grp.set(j+1,grp.get(j)); j--; }
                    grp.set(j+1,v);
                }
                for (int i=0;i<grp.size()&&i<SMT_RING;i++) lines.add(grp.get(i));
            }
            try (PrintWriter pw=new PrintWriter(new FileWriter(getSmtPath()))){
                for (String[] l:lines) pw.println(l[0]+","+l[1]+","+l[2]+","+l[3]+","+l[4]);
            } catch (Exception e){ }
        }
    }
    /** [SMT] recompute own pivots, share them, evaluate peers -> smtPanelLine. Called only when showSMT. */
    private void updateSmt(){
        smtPanelLine="\u2014";
        String key=smtKey(currentInstrumentName());
        String[] peers=smtPeers(key);
        if (key.isEmpty()||peers.length==0||drawAnchor==null) return;
        long anchorMs=PERIOD_INTERVALS[drawAnchor.periodIndex];
        if (anchorMs<=0) return;
        List<CandleData> hist=drawAnchor.historical;
        int n=hist.size();
        if (n<2*SMT_PIVOT_K+1) return;
        long[] tt=new long[n]; double[] hh=new double[n]; double[] ll=new double[n];
        for (int i=0;i<n;i++){ CandleData c=hist.get(i); tt[i]=c.openTime; hh[i]=c.high; ll[i]=c.low; }
        List<SmtPivot> own=smtPivots(tt,hh,ll);
        smtShare(key,own,anchorMs);
        List<String[]> lines=readSmtLinesInternal();
        StringBuilder sb=new StringBuilder();
        for (String peer:peers){
            List<SmtPivot> pp=smtParsePeer(lines,peer,anchorMs);
            String exp=smtExperimental(peer)?"?":"";
            if (smtVerdictSide(own,pp,true,anchorMs,chartNowTime)!=0) sb.append(peer+"\u2193!"+exp+" ");
            if (smtVerdictSide(own,pp,false,anchorMs,chartNowTime)!=0) sb.append(peer+"\u2191!"+exp+" ");
        }
        if (sb.length()>0) smtPanelLine=sb.toString().trim();
    }
    // ================== end SMT ENGINE ==================

    /** [O session overlay] open time of the chart bar containing t (last bar
     *  time <= t), or -1. Guarantees an EXACT bar time for getXForTime. */
    static long snapToBar(long[] times,long t){
        if (times==null||times.length==0) return -1;
        int lo=0,hi=times.length-1,ans=-1;
        while (lo<=hi){
            int mid=(lo+hi)>>>1;
            if (times[mid]<=t){ ans=mid; lo=mid+1; }
            else hi=mid-1;
        }
        return ans<0?-1:times[ans];
    }

    static void aggregate(LayerData layer, RB bar, long intervalMs, TimeZone tz){
        long ps = periodStart(bar.time,intervalMs,tz);
        if (!layer.curActive || ps!=layer.curStart){
            if (layer.curActive){
                CandleData done=new CandleData(layer.curO,layer.curH,layer.curL,layer.curC,layer.curStart,true);
                if (layer.historical.isEmpty()||layer.historical.get(layer.historical.size()-1).openTime!=layer.curStart)
                    layer.historical.add(done);
                if (layer.historical.size()>layer.candlesToShow+2) layer.historical.remove(0);
            }
            layer.curStart=ps; layer.curO=bar.o; layer.curH=bar.h; layer.curL=bar.l; layer.curC=bar.c; layer.curActive=true;
        } else {
            if (bar.h>layer.curH) layer.curH=bar.h;
            if (bar.l<layer.curL) layer.curL=bar.l;
            layer.curC=bar.c;
        }
    }

    // ---------- locked v2 helpers ----------
    static int legCategory(double legSize, double avgRange){
        if (avgRange<=0) return 1;
        double ratio = legSize/avgRange;
        if (ratio>=2.0) return 2;
        if (ratio>=1.2) return 0;
        return 1;
    }
    static double[] legMultipliers(int cat){
        if (cat==0) return new double[]{-1.0};
        if (cat==2) return new double[]{-4.0,-4.5};
        return new double[]{-2.0,-2.5};
    }
    static boolean icEarly(long confirmTime, long htfStart, long htfPeriod){
        if (htfPeriod<=0) return true;
        double frac = (confirmTime-htfStart)/(double)htfPeriod;
        return frac<=0.5;
    }
    static int signalState(boolean bullish,double entry,double stop,RB[] bars,int fromIdx,boolean htfClosedAfter){
        double R = Math.abs(entry-stop);
        if (R<=0) return 0;
        boolean reached1R=false;
        for (int i=fromIdx;i<bars.length;i++){
            RB b=bars[i];
            if (bullish){
                if (b.c < stop) return 2;
                if (b.h - entry >= R) reached1R=true;
            } else {
                if (b.c > stop) return 2;
                if (entry - b.l >= R) reached1R=true;
            }
        }
        if (reached1R) return 0;
        if (htfClosedAfter) return 1;
        return 0;
    }

    // ---------- locked official Next-Day Model bias ----------
    /** [C official] close outside prev range = continuation; sweep + close inside = reversal; else neutral. */
    static int[] ndmBias(CandleData last, CandleData prev){
        if (last==null) return new int[]{0,0};
        if (prev==null) return new int[]{0,0};
        if (last.close>prev.high) return new int[]{1,0};
        if (last.close<prev.low)  return new int[]{-1,0};
        if (last.high>prev.high)  return new int[]{-1,1};
        if (last.low <prev.low)   return new int[]{1,1};
        return new int[]{0,0};
    }

    // ---------- locked structure helpers (internal POI gate for C2 leg) ----------
    static double[] detectFVG(RB[] bars,int i,boolean bullish){
        if (i<2||i>=bars.length) return null;
        if (bullish){ if (bars[i-2].h < bars[i].l) return new double[]{bars[i-2].h, bars[i].l}; }
        else        { if (bars[i-2].l > bars[i].h) return new double[]{bars[i].h, bars[i-2].l}; }
        return null;
    }
    static int findOrderBlock(RB[] bars,int i,boolean bullish,double avgRange){
        if (i<1||i>=bars.length||avgRange<=0) return -1;
        double body=Math.abs(bars[i].c-bars[i].o);
        boolean disp = bullish ? (isBull(bars[i])&&body>=1.2*avgRange) : (isBear(bars[i])&&body>=1.2*avgRange);
        if (!disp) return -1;
        for (int k=i-1;k>=0&&k>=i-5;k--){
            if (bullish&&isBear(bars[k])) return k;
            if (!bullish&&isBull(bars[k])) return k;
        }
        return -1;
    }
    static double[] findLastTwoSwings(RB[] bars,boolean findHighs,int lookback){
        if (bars==null||bars.length<5) return null;
        int limit=Math.min(lookback,bars.length-1);
        List<Integer> idx=new ArrayList<>(); List<Double> px=new ArrayList<>();
        for (int i=1;i<limit;i++){
            if (findHighs){ if (bars[i].h>bars[i-1].h&&bars[i].h>=bars[i+1].h){idx.add(i);px.add(bars[i].h);} }
            else          { if (bars[i].l<bars[i-1].l&&bars[i].l<=bars[i+1].l){idx.add(i);px.add(bars[i].l);} }
        }
        if (idx.size()<2) return null;
        int n=idx.size();
        return new double[]{idx.get(n-2),px.get(n-2),idx.get(n-1),px.get(n-1)};
    }
    static int[] structureState(RB[] bars,int lookback){
        double[] hl=findLastTwoSwings(bars,false,lookback);
        double[] hh=findLastTwoSwings(bars,true,lookback);
        if (hl==null||hh==null) return new int[]{0,0};
        int trend=(hl[3]>hl[1]&&hh[3]>hh[1])?1:(hl[3]<hl[1]&&hh[3]<hh[1])?-1:0;
        double close=bars[bars.length-1].c; int event=0;
        if (trend==1&&close>hh[3]) event=1;
        else if (trend==-1&&close<hl[3]) event=1;
        else if (trend==-1&&close>hh[3]) event=2;
        else if (trend==1&&close<hl[3]) event=2;
        return new int[]{trend,event};
    }
    static boolean c2SweepClosure(CandleData c1,CandleData c2,boolean bullish){
        double[] sw=detectSweep(c1,c2);
        if (sw==null) return false;
        if (bullish?(sw[0]!=1):(sw[0]!=0)) return false;
        return c2.close>=c1.low&&c2.close<=c1.high;
    }
    static boolean c2Closure(CandleData c1,CandleData c2,boolean bullish,boolean poiInHalf,int structTrend){
        if (!c2SweepClosure(c1,c2,bullish)) return false;
        if (!poiInHalf) return false;
        if (structTrend!=0&&structTrend!=(bullish?1:-1)) return false;
        return true;
    }
    static double c2WickMid(CandleData c2){
        double range=c2.high-c2.low; if (range<=0) return Double.NaN;
        double wick=range-Math.abs(c2.close-c2.open);
        return (wick>=0.5*range)?(c2.high+c2.low)/2.0:Double.NaN;
    }
    static boolean c3Closure(CandleData c2,CandleData c3,boolean bullish){
        if (detectSweep(c2,c3)!=null) return false;
        double top=Math.max(c2.open,c2.close), bot=Math.min(c2.open,c2.close);
        return bullish?(c3.close>top):(c3.close<bot);
    }

    // ---------- locked T-Spot ----------
    static double[] tspotZone(double eq,double open){
        if (Math.abs(eq-open)<=1e-12) return null;
        return new double[]{Math.min(eq,open),Math.max(eq,open)};
    }
    static double tspotEQ(CandleData c){ return c.eq(); }   // wick-to-wick 50% (pixel-verified)
    static int tspotType(double eq,double open){ return open<eq?1:0; }
    static int zoneState(double lo,double hi,int type,RB[] bars,int fromIdx){
        boolean touched=false;
        for (int i=fromIdx;i<bars.length;i++){
            RB b=bars[i];
            if (b.h>=lo&&b.l<=hi) touched=true;
            if (type==1&&b.c<lo) return 2;
            if (type==0&&b.c>hi) return 2;
        }
        return touched?1:0;
    }
    /** [C official guide] T-Spot prints on model closure candles (C2/C3 generators) only. */
    static boolean modelZoneGate(List<CandleData> hist,int g){
        if (g<1||g>=hist.size()) return false;
        CandleData c1=hist.get(g-1), c2=hist.get(g);
        boolean bull=c2.close>=c1.candleEQ();
        if (c2SweepClosure(c1,c2,bull)) return true;
        if (g>=2){
            CandleData c0=hist.get(g-2);
            boolean b0=c1.close>=c0.candleEQ();
            if (c2SweepClosure(c0,c1,b0)&&c3Closure(c1,c2,b0)) return true;
        }
        return false;
    }
    /** [C official] projection ladder: R multiples {1,2,2.5,4,4.5}. */
    static double[] projLadder(double p1,double leg,boolean bull){
        double[] m={1,2,2.5,4,4.5}; double[] out=new double[m.length];
        for (int i=0;i<m.length;i++) out[i]=bull? p1+m[i]*leg : p1-m[i]*leg;
        return out;
    }
    static String shortSym(String sym){
        if (sym.contains("USA500")) return "ES";
        if (sym.contains("USA30"))  return "YM";
        if (sym.contains("USATECH"))return "NQ";
        if (sym.equals("EUR/USD"))  return "EUR";
        if (sym.equals("GBP/USD"))  return "GBP";
        if (sym.contains("XAU"))    return "XAU";
        int d=sym.indexOf('/'); return d>0? sym.substring(0,d) : sym;
    }
    /** [O element 7] formation liquidity = PREVIOUS completed DAILY candle high & low. */
    static double[] formationLiquidity(LayerData daily){
        if (daily==null||daily.historical.isEmpty()) return null;
        CandleData prev=null;
        for (int k=daily.historical.size()-1;k>=0;k--) if (daily.historical.get(k).completed){ prev=daily.historical.get(k); break; }
        if (prev==null) return null;
        return new double[]{prev.high,prev.low};
    }

    // ==================================================================
    //  JForex lifecycle (ZERO options)
    // ==================================================================
    /** [reference option group] built at construction so it works without onStart. */
    private void buildOptions(){
        List<com.dukascopy.api.indicators.OptInputParameterInfo> opt=new ArrayList<>();
        List<OptInputSetter> set=new ArrayList<>();
        opt.add(new com.dukascopy.api.indicators.OptInputParameterInfo("[CISD] Detection",com.dukascopy.api.indicators.OptInputParameterInfo.Type.OTHER,new com.dukascopy.api.indicators.IntegerListDescription(1,BOOLEAN_VALUES,BOOLEAN_NAMES)));
        set.add(v->showCISD=((Integer)v)==1);
        opt.add(new com.dukascopy.api.indicators.OptInputParameterInfo("[CISD] Min Wave Length",com.dukascopy.api.indicators.OptInputParameterInfo.Type.OTHER,new com.dukascopy.api.indicators.IntegerListDescription(1,SENS_VALUES,SENS_NAMES)));
        set.add(v->cisdSensitivity=(Integer)v);
        opt.add(new com.dukascopy.api.indicators.OptInputParameterInfo("[CISD] Grade",com.dukascopy.api.indicators.OptInputParameterInfo.Type.OTHER,new com.dukascopy.api.indicators.IntegerListDescription(0,GRADE_VALUES,GRADE_NAMES)));
        set.add(v->{cisdGrade=(Integer)v;applyCisdGrade(cisdGrade);});
        opt.add(new com.dukascopy.api.indicators.OptInputParameterInfo("[CISD] Momentum Filter",com.dukascopy.api.indicators.OptInputParameterInfo.Type.OTHER,new com.dukascopy.api.indicators.IntegerListDescription(0,MOM_VALUES,MOM_NAMES)));
        set.add(v->momentumFilter=(Integer)v);
        opt.add(new com.dukascopy.api.indicators.OptInputParameterInfo("[CISD] Volume Filter",com.dukascopy.api.indicators.OptInputParameterInfo.Type.OTHER,new com.dukascopy.api.indicators.IntegerListDescription(0,BOOLEAN_VALUES,BOOLEAN_NAMES)));
        set.add(v->volumeFilterEnabled=((Integer)v)==1);
        opt.add(new com.dukascopy.api.indicators.OptInputParameterInfo("[CISD] Trend Filter",com.dukascopy.api.indicators.OptInputParameterInfo.Type.OTHER,new com.dukascopy.api.indicators.IntegerListDescription(0,TREND_VALUES,TREND_NAMES)));
        set.add(v->trendFilter=(Integer)v);
        opt.add(new com.dukascopy.api.indicators.OptInputParameterInfo("[CISD] Higher TF Confirmation",com.dukascopy.api.indicators.OptInputParameterInfo.Type.OTHER,new com.dukascopy.api.indicators.IntegerListDescription(0,HTF_CONF_VALUES,HTF_CONF_NAMES)));
        set.add(v->higherTFConfirmation=(Integer)v);
        opt.add(new com.dukascopy.api.indicators.OptInputParameterInfo("[CISD] Min Retracement %",com.dukascopy.api.indicators.OptInputParameterInfo.Type.OTHER,new com.dukascopy.api.indicators.IntegerListDescription(0,FIB_VALUES,FIB_NAMES)));
        set.add(v->minRetracement=(Integer)v);
        opt.add(new com.dukascopy.api.indicators.OptInputParameterInfo("[CISD] Market Structure",com.dukascopy.api.indicators.OptInputParameterInfo.Type.OTHER,new com.dukascopy.api.indicators.IntegerListDescription(0,MS_VALUES,MS_NAMES)));
        set.add(v->marketStructureFilter=(Integer)v);
        opt.add(new com.dukascopy.api.indicators.OptInputParameterInfo("[CISD] Alert Sound",com.dukascopy.api.indicators.OptInputParameterInfo.Type.OTHER,new com.dukascopy.api.indicators.IntegerListDescription(0,SOUND_VALUES,SOUND_NAMES)));
        set.add(v->cisdAlertSound=SOUND_FILES[(Integer)v]);
        opt.add(new com.dukascopy.api.indicators.OptInputParameterInfo("[CISD] Retest Sound",com.dukascopy.api.indicators.OptInputParameterInfo.Type.OTHER,new com.dukascopy.api.indicators.IntegerListDescription(1,SOUND_VALUES,SOUND_NAMES)));
        set.add(v->cisdRetestSound=SOUND_FILES[(Integer)v]);
        opt.add(new com.dukascopy.api.indicators.OptInputParameterInfo("[CISD] Shared Alerts",com.dukascopy.api.indicators.OptInputParameterInfo.Type.OTHER,new com.dukascopy.api.indicators.IntegerListDescription(1,BOOLEAN_VALUES,BOOLEAN_NAMES)));
        set.add(v->sharedCISDAlerts=((Integer)v)==1);
        opt.add(new com.dukascopy.api.indicators.OptInputParameterInfo("[CISD] Show Entry Price",com.dukascopy.api.indicators.OptInputParameterInfo.Type.OTHER,new com.dukascopy.api.indicators.IntegerListDescription(1,BOOLEAN_VALUES,BOOLEAN_NAMES)));
        set.add(v->showEntryPrice=((Integer)v)==1);
        opt.add(new com.dukascopy.api.indicators.OptInputParameterInfo("[CISD] Save/Load Signals",com.dukascopy.api.indicators.OptInputParameterInfo.Type.OTHER,new com.dukascopy.api.indicators.IntegerListDescription(1,BOOLEAN_VALUES,BOOLEAN_NAMES)));
        set.add(v->saveLoadCISD=((Integer)v)==1);
        opt.add(new com.dukascopy.api.indicators.OptInputParameterInfo("[CISD] Reset CISD Signals",com.dukascopy.api.indicators.OptInputParameterInfo.Type.OTHER,new com.dukascopy.api.indicators.IntegerListDescription(0,RESET_VALUES,RESET_NAMES)));
        set.add(v->{ if ((Integer)v==1) resetCISDData(); });
        // Session overlay (agreement 2026-09-09): opt-in, OFF by default
        opt.add(new com.dukascopy.api.indicators.OptInputParameterInfo("[Sessions] Show NY Open Line",com.dukascopy.api.indicators.OptInputParameterInfo.Type.OTHER,new com.dukascopy.api.indicators.IntegerListDescription(0,BOOLEAN_VALUES,BOOLEAN_NAMES)));
        set.add(v->showNyOpenLine=((Integer)v)==1);
        opt.add(new com.dukascopy.api.indicators.OptInputParameterInfo("[Sessions] Show Session in Panel",com.dukascopy.api.indicators.OptInputParameterInfo.Type.OTHER,new com.dukascopy.api.indicators.IntegerListDescription(0,BOOLEAN_VALUES,BOOLEAN_NAMES)));
        set.add(v->showSessionInPanel=((Integer)v)==1);
        // Per-symbol TV grid (agreement 2026-09-09): Auto resolves by instrument
        opt.add(new com.dukascopy.api.indicators.OptInputParameterInfo("[Bucketing] Grid",com.dukascopy.api.indicators.OptInputParameterInfo.Type.OTHER,new com.dukascopy.api.indicators.IntegerListDescription(0,GRID_VALUES,GRID_NAMES)));
        set.add(v->gridMode=(Integer)v);
        // SMT divergence engine (agreement 2026-09-10): panel-only, OFF by default
        opt.add(new com.dukascopy.api.indicators.OptInputParameterInfo("[SMT] Detection",com.dukascopy.api.indicators.OptInputParameterInfo.Type.OTHER,new com.dukascopy.api.indicators.IntegerListDescription(0,BOOLEAN_VALUES,BOOLEAN_NAMES)));
        set.add(v->showSMT=((Integer)v)==1);
        // Countdown timer (agreement 2026-09-10, D6 wall clock): default ON; REMOVAL note at draw site
        opt.add(new com.dukascopy.api.indicators.OptInputParameterInfo("[Display] Show Timer",com.dukascopy.api.indicators.OptInputParameterInfo.Type.OTHER,new com.dukascopy.api.indicators.IntegerListDescription(1,BOOLEAN_VALUES,BOOLEAN_NAMES)));
        set.add(v->showTimer=((Integer)v)==1);
        optInfos=opt.toArray(new com.dukascopy.api.indicators.OptInputParameterInfo[0]);
        optSetters=set.toArray(new OptInputSetter[0]);
    }

    @Override
    public void onStart(IIndicatorContext context){
        this.context=context;
        outputs=new Object[MAX_CANDLES*4];
        inputParameterInfos = new InputParameterInfo[]{ new InputParameterInfo("Chart Bars", InputParameterInfo.Type.BAR) };
        buildOptions();
        indicatorInfo=new IndicatorInfo("TTFMEssence","TTFM Essence (CISD Desk experiment)","Custom",
                true,false,false,1,optInfos.length,MAX_CANDLES*4);
        outputParameterInfos=new OutputParameterInfo[MAX_CANDLES*4];
        String[] ohlc={"Open","High","Low","Close"};
        for (int c=0;c<MAX_CANDLES;c++) for (int vt=0;vt<4;vt++){
            int idx=c*4+vt;
            outputParameterInfos[idx]=new OutputParameterInfo("C"+(c+1)+" "+ohlc[vt],OutputParameterInfo.Type.DOUBLE,OutputParameterInfo.DrawingStyle.LINE);
            outputParameterInfos[idx].setDrawnByIndicator(true);
            outputParameterInfos[idx].setShowOutput(vt==0);
            if (vt==0){outputParameterInfos[idx].setColor(new Color(0,0,0,0));outputParameterInfos[idx].setOpacityAlpha(1.0f);}
        }
    }

    @Override public void setOptInputParameter(int index,Object value){ optSetters[index].set(value); }

    // ==================================================================
    //  calculate
    // ==================================================================
    @Override
    public IndicatorResult calculate(int startIndex,int endIndex){
        if (inputs[0]==null||inputs[0].length==0) return new IndicatorResult(0,0);
        IBar[] ibars=inputs[0];
        RB[] bars=new RB[ibars.length];
        for (int i=0;i<ibars.length;i++) bars[i]=toRB(ibars[i]);

        long periodMs=context.getFeedDescriptor().getPeriod().getInterval();
        TimeZone gridTz=activeGridTz();
        String inst=currentInstrumentName();
        if (periodMs!=lastChartPeriodMs||!gridTz.getID().equals(lastGridId)||!inst.equals(lastInstrument)){
            lastChartPeriodMs=periodMs;
            lastGridId=gridTz.getID();
            lastInstrument=inst;
            cisdStoredCount=0; pendingBullish.active=false; pendingBearish.active=false;
            fibCacheWaveStart=-1; fibCacheResult=null;
            sharedAlertLines.clear(); sharedFileLastModified=0; cisdLoaded=false;
            currentBias=0; currentInversion=false;
            for (LayerData l:layers){ l.historical.clear(); l.curActive=false; l.curO=l.curH=l.curL=l.curC=Double.NaN; l.curStart=0; }
        }
        TimeZone tz=gridTz;

        int[] al=autoLayers(periodMs);          // AUTO layers freeze 2026-09-08d
        layers[0].periodIndex=al[0];
        layers[1].periodIndex=al[1];

        for (LayerData layer:layers){
            if (!layer.enabled) continue;
            long interval=PERIOD_INTERVALS[layer.periodIndex];
            for (int i=startIndex;i<=endIndex&&i<bars.length;i++){
                if (bars[i].time<=0) continue;
                aggregate(layer,bars[i],interval,tz);
            }
        }

        chartPeriodMs=periodMs;
        chartNowTime=bars[bars.length-1].time;
        drawTimes=new long[bars.length];
        for (int i=0;i<bars.length;i++) drawTimes[i]=bars[i].time;
        LayerData primary=layers[ANCHOR_LAYER-1];
        drawAnchor=primary;
        updateNdmBias(layers[BIAS_LAYER-1]);
        if (showSMT) updateSmt();   // [SMT] panel-only engine (OFF = zero residue)

        int detectionIndex=endIndex;
        if (detectionIndex==bars.length-1){
            long barEnd=bars[detectionIndex].time+periodMs;
            if (System.currentTimeMillis()<barEnd) detectionIndex=Math.max(0,detectionIndex-1);
        }
        if (saveLoadCISD&&!cisdLoaded){ cisdLoaded=true; loadCisdFromFile(); }
        detectCisd(bars,detectionIndex);
        updateCisdStates(bars);
        checkRetestFrequent(bars[bars.length-1]);
        updateSharedAlertsFromFile();
        updateConceptState(bars,primary,periodMs);

        int length=endIndex-startIndex+1;
        for (int i=0;i<outputs.length;i++){
            double[] arr=(double[])outputs[i];
            if (arr==null||arr.length!=length) outputs[i]=new double[length];
        }
        List<CandleData> disp=displayList(primary); // [review] computed ONCE - identical for every bar index
        for (int idx=startIndex,a=0;idx<=endIndex;idx++,a++){
            for (int c=0;c<MAX_CANDLES;c++){
                CandleData cd=(c<disp.size())?disp.get(c):null;
                ((double[])outputs[c*4])[a]  =(cd!=null)?cd.open:Double.NaN;
                ((double[])outputs[c*4+1])[a]=(cd!=null)?cd.high:Double.NaN;
                ((double[])outputs[c*4+2])[a]=(cd!=null)?cd.low:Double.NaN;
                ((double[])outputs[c*4+3])[a]=(cd!=null)?cd.close:Double.NaN;
            }
        }
        return new IndicatorResult(startIndex,length);
    }

    private List<CandleData> displayList(LayerData layer){
        List<CandleData> d=new ArrayList<>(layer.historical);
        if (layer.curActive) d.add(new CandleData(layer.curO,layer.curH,layer.curL,layer.curC,layer.curStart,false));
        while (d.size()>layer.candlesToShow) d.remove(0);
        return d;
    }

    /** [official Next-Day Model] bias frozen on the DAILY layer. */
    private void updateNdmBias(LayerData daily){
        CandleData last=null, prev=null;
        for (int i=daily.historical.size()-1;i>=0;i--){
            CandleData c=daily.historical.get(i);
            if (!c.completed) continue;
            if (last==null) last=c; else { prev=c; break; }
        }
        if (last==null){ currentBias=0; currentInversion=false; return; }
        int[] nd=ndmBias(last,prev);
        currentBias=nd[0]; currentInversion=nd[1]==1;
    }

    private double avgHtfRange(LayerData primary){
        int n=Math.min(10,primary.historical.size());
        if (n==0) return 0;
        double s=0;
        for (int i=primary.historical.size()-n;i<primary.historical.size();i++)
            s+=primary.historical.get(i).high-primary.historical.get(i).low;
        return s/n;
    }

    // ================== CISD Desk engine (REFERENCE CLONE) ==================
    /** [reference] Grade presets auto-configure the six filters. */
    void applyCisdGrade(int grade){
        if (grade==0) return;                       // Standard: manual, untouched
        if (grade==1){                              // Premium: moderate
            trendFilter=1; minRetracement=1; marketStructureFilter=1;
            higherTFConfirmation=1; momentumFilter=1; volumeFilterEnabled=false;
        } else if (grade==2){                       // Ultimate: strong
            trendFilter=2; minRetracement=3; marketStructureFilter=1;
            higherTFConfirmation=1; momentumFilter=2; volumeFilterEnabled=true;
        }
    }
    /** [reference] Standard=AND of active; Premium>=60%; Ultimate>=75%. */
    static boolean isSignalEligible(int grade,boolean trendPass,boolean fibPass,boolean msPass,
            boolean htfPass,boolean momVolPass,boolean trendActive,boolean fibActive,
            boolean msActive,boolean htfActive,boolean momVolActive){
        if (grade==0) return trendPass&&momVolPass&&htfPass&&fibPass&&msPass;
        int total=0, passed=0;
        if (trendActive){total++; if(trendPass)passed++;}
        if (fibActive){total++; if(fibPass)passed++;}
        if (msActive){total++; if(msPass)passed++;}
        if (htfActive){total++; if(htfPass)passed++;}
        if (momVolActive){total++; if(momVolPass)passed++;}
        if (total==0) return true;
        double ratio=(double)passed/total;
        if (grade==1) return ratio>=0.6;
        if (grade==2) return ratio>=0.75;
        return ratio>=0.6;
    }
    static double getEMA(RB[] bars,int endIndex,int period){
        if (endIndex<period-1||period<=0) return bars[endIndex].c;
        double multiplier=2.0/(period+1);
        double ema=bars[endIndex-period+1].c;
        for (int i=endIndex-period+2;i<=endIndex;i++) ema=(bars[i].c-ema)*multiplier+ema;
        return ema;
    }
    static boolean passTrendFilter(RB[] bars,int index,boolean bullish,int trendFilter){
        if (trendFilter==0) return true;
        int period=(trendFilter==1)?50:200;
        if (index<period-1) return true;
        double ema=getEMA(bars,index,period);
        double close=bars[index].c;
        return bullish? close>ema : close<ema;
    }
    static boolean passMomentumAndVolumeFilters(RB[] bars,int index,boolean bullish,int momentumFilter,boolean volumeFilterEnabled,double minVolumeRatio){
        if (momentumFilter==0&&!volumeFilterEnabled) return true;
        RB breakoutBar=bars[index];
        double range=breakoutBar.h-breakoutBar.l;
        double closeLocation=range>0?(breakoutBar.c-breakoutBar.l)/range:0.5;
        if (momentumFilter>0){
            if (momentumFilter>=1){
                if (bullish&&closeLocation<0.5) return false;
                if (!bullish&&closeLocation>0.5) return false;
            }
            if (momentumFilter>=2){
                double avgRange=0; int count=0;
                for (int i=Math.max(0,index-5);i<index;i++){ avgRange+=bars[i].h-bars[i].l; count++; }
                avgRange/=(count>0?count:1);
                if (range<avgRange*1.2) return false;
            }
        }
        if (volumeFilterEnabled){
            double vol=breakoutBar.vol;
            double avgVol=0; int count=Math.min(20,index);
            for (int i=index-count;i<index;i++) avgVol+=bars[i].vol;
            avgVol/=(count>0?count:1);
            if (vol<avgVol*minVolumeRatio) return false;
        }
        return true;
    }
    /** [reference] 30-row volume profile; entry row must hold >=50% of max row volume. */
    static boolean isVolumeProfileConfirmed(RB[] bars,int index,double entryPrice){
        int lookback=Math.min(100,index+1);
        int start=index-lookback+1; if (start<0) start=0;
        double high=-Double.MAX_VALUE, low=Double.MAX_VALUE;
        for (int i=start;i<=index;i++){ if (bars[i].h>high) high=bars[i].h; if (bars[i].l<low) low=bars[i].l; }
        if (high==low) return false;
        int rows=30; double step=(high-low)/rows;
        double[] volumes=new double[rows];
        for (int i=start;i<=index;i++){
            double barHigh=bars[i].h, barLow=bars[i].l;
            int rowFrom=(int)Math.min(Math.floor((high-barHigh)/step),rows-1);
            int rowTo=(int)Math.min(Math.floor((high-barLow)/step),rows-1);
            for (int r=rowFrom;r<=rowTo;r++){
                if (barHigh==barLow) volumes[r]+=bars[i].vol;
                else {
                    double maxPrice=Math.min(high-r*step,barHigh);
                    double minPrice=Math.max(high-(r+1)*step,barLow);
                    volumes[r]+=bars[i].vol*(maxPrice-minPrice)/(barHigh-barLow);
                }
            }
        }
        double maxVol=0; for (double v:volumes) if (v>maxVol) maxVol=v;
        if (maxVol==0) return false;
        int entryRow=(int)Math.min(Math.floor((high-entryPrice)/step),rows-1);
        if (entryRow<0) entryRow=0;
        return (volumes[entryRow]/maxVol)>=0.50;
    }
    /** [reference] last completed candle of the confirming layer agrees in direction. */
    private boolean checkHigherTFAlignment(boolean bullish){
        if (higherTFConfirmation==0) return false;
        int layerIdx=higherTFConfirmation-1;
        if (layerIdx<0||layerIdx>=MAX_LAYERS) return false;
        LayerData layer=layers[layerIdx];
        if (!layer.enabled||layer.historical.isEmpty()) return false;
        CandleData lastCandle=layer.historical.get(layer.historical.size()-1);
        if (lastCandle==null||!lastCandle.completed) return false;
        boolean candleBullish=lastCandle.close>lastCandle.open;
        return bullish==candleBullish;
    }
    /** [reference] retrace depth of the wave vs the opposite wave length, cached per wave. */
    private double[] computeFibRetracement(RB[] bars,int waveStartIdx,int waveEndIdx,boolean isBearishWave){
        if (waveStartIdx<0||waveStartIdx>=bars.length||waveEndIdx<0||waveEndIdx>=bars.length) return new double[]{-1,0};
        long currentWaveStartTime=bars[waveStartIdx].time;
        if (currentWaveStartTime==fibCacheWaveStart&&fibCacheResult!=null) return fibCacheResult;
        int oppositeEnd=waveStartIdx-1;
        if (oppositeEnd<0){ fibCacheResult=new double[]{-1,0}; fibCacheWaveStart=currentWaveStartTime; return fibCacheResult; }
        int[] oppositeWave=findWave(bars,oppositeEnd,200,!isBearishWave,CISD_IGNORE_INSIDE);
        if (oppositeWave[0]==-1){ fibCacheResult=new double[]{-1,0}; fibCacheWaveStart=currentWaveStartTime; return fibCacheResult; }
        double oppositeHigh=-Double.MAX_VALUE, oppositeLow=Double.MAX_VALUE;
        for (int i=oppositeWave[0];i<=oppositeWave[1];i++){
            oppositeHigh=Math.max(oppositeHigh,bars[i].h);
            oppositeLow=Math.min(oppositeLow,bars[i].l);
        }
        double oppositeLength=oppositeHigh-oppositeLow;
        if (oppositeLength<=0){ fibCacheResult=new double[]{-1,0}; fibCacheWaveStart=currentWaveStartTime; return fibCacheResult; }
        double currentWaveOpen=bars[waveStartIdx].o;
        double currentWaveExtreme=isBearishWave?Double.MAX_VALUE:-Double.MAX_VALUE;
        for (int i=waveStartIdx;i<=waveEndIdx;i++){
            if (isBearishWave) currentWaveExtreme=Math.min(currentWaveExtreme,bars[i].l);
            else currentWaveExtreme=Math.max(currentWaveExtreme,bars[i].h);
        }
        double retracementDepth=isBearishWave?(currentWaveOpen-currentWaveExtreme):(currentWaveExtreme-currentWaveOpen);
        if (retracementDepth<0){ fibCacheResult=new double[]{-1,0}; fibCacheWaveStart=currentWaveStartTime; return fibCacheResult; }
        fibCacheResult=new double[]{retracementDepth/oppositeLength,oppositeLength};
        fibCacheWaveStart=currentWaveStartTime;
        return fibCacheResult;
    }
    /** [reference] last two pivots of the last 50 bars make higher-lows / lower-highs. */
    static boolean checkMarketStructure(RB[] bars,boolean bullish){
        int recent=Math.min(50,bars.length);
        List<Integer> pivots=new ArrayList<>();
        for (int i=2;i<bars.length-2&&i<recent;i++){
            if (bullish){ if (bars[i].l<bars[i-1].l&&bars[i].l<bars[i+1].l) pivots.add(i); }
            else        { if (bars[i].h>bars[i-1].h&&bars[i].h>bars[i+1].h) pivots.add(i); }
        }
        if (pivots.size()<2) return false;
        int idx1=pivots.get(pivots.size()-2), idx2=pivots.get(pivots.size()-1);
        return bullish? bars[idx2].l>bars[idx1].l : bars[idx2].h<bars[idx1].h;
    }

    private void detectCisd(RB[] bars,int detectionIndex){
        if (!showCISD||bars.length<2||detectionIndex<0||detectionIndex>=bars.length) return;
        RB currentBar=bars[detectionIndex];
        int minRequired=minWaveFor(cisdSensitivity);
        int maxLookback=200;

        boolean trendActive=trendFilter>0;
        boolean fibActive=minRetracement>0;
        boolean msActive=marketStructureFilter>0;
        boolean htfActive=higherTFConfirmation>0;
        boolean momVolActive=momentumFilter>0||volumeFilterEnabled;
        double fibThreshold=FIB_RATIOS[minRetracement];

        int[] wave=findWave(bars,detectionIndex-1,maxLookback,true,CISD_IGNORE_INSIDE);
        if (wave[0]!=-1&&(wave[1]-wave[0]+1)>=minRequired){
            double firstOpen=bars[wave[0]].o; double lowest=Double.MAX_VALUE;
            for (int k=wave[0];k<=wave[1];k++) lowest=Math.min(lowest,bars[k].l);
            if (!isDuplicate(bars[wave[0]].time,firstOpen)){
                if (!pendingBullish.active||bars[wave[0]].time>pendingBullish.waveStartTime||
                    (bars[wave[0]].time==pendingBullish.waveStartTime&&lowest<pendingBullish.stopLevel)){
                    pendingBullish.active=true; pendingBullish.triggerOpen=firstOpen;
                    pendingBullish.stopLevel=lowest; pendingBullish.waveStartTime=bars[wave[0]].time;
                    pendingBullish.waveStartIdx=wave[0];
                }
            }
        }
        wave=findWave(bars,detectionIndex-1,maxLookback,false,CISD_IGNORE_INSIDE);
        if (wave[0]!=-1&&(wave[1]-wave[0]+1)>=minRequired){
            double firstOpen=bars[wave[0]].o; double highest=-Double.MAX_VALUE;
            for (int k=wave[0];k<=wave[1];k++) highest=Math.max(highest,bars[k].h);
            if (!isDuplicate(bars[wave[0]].time,firstOpen)){
                if (!pendingBearish.active||bars[wave[0]].time>pendingBearish.waveStartTime||
                    (bars[wave[0]].time==pendingBearish.waveStartTime&&highest>pendingBearish.stopLevel)){
                    pendingBearish.active=true; pendingBearish.triggerOpen=firstOpen;
                    pendingBearish.stopLevel=highest; pendingBearish.waveStartTime=bars[wave[0]].time;
                    pendingBearish.waveStartIdx=wave[0];
                }
            }
        }

        double close=currentBar.c;
        if (pendingBullish.active&&close>pendingBullish.triggerOpen){
            boolean trendPass=!trendActive||passTrendFilter(bars,detectionIndex,true,trendFilter);
            boolean momVolPass=!momVolActive||passMomentumAndVolumeFilters(bars,detectionIndex,true,momentumFilter,volumeFilterEnabled,minVolumeRatio);
            boolean htfPass=!htfActive||checkHigherTFAlignment(true);
            boolean fibPass=!fibActive; String fibLabel="";
            if (fibActive){
                double[] fibResult=computeFibRetracement(bars,pendingBullish.waveStartIdx,detectionIndex-1,true);
                if (fibResult[0]>=0&&fibResult[0]>=fibThreshold){ fibPass=true; fibLabel=String.format(java.util.Locale.US,"Fib %.0f%%",fibResult[0]*100); }
            }
            boolean msPass=!msActive||checkMarketStructure(bars,true);
            if (isSignalEligible(cisdGrade,trendPass,fibPass,msPass,htfPass,momVolPass,trendActive,fibActive,msActive,htfActive,momVolActive)){
                double entry=pendingBullish.triggerOpen, stop=pendingBullish.stopLevel;
                if (sessionAllowed(sessionOf(pendingBullish.waveStartTime,nyTZ),ACTIVE_SESSIONS)&&Math.abs(entry-stop)>1e-9){
                    String confirmingLabel=null;
                    if (htfPass&&higherTFConfirmation>0) confirmingLabel=SHORT_LABELS[layers[higherTFConfirmation-1].periodIndex];
                    boolean confirmed=isVolumeProfileConfirmed(bars,detectionIndex,entry);
                    storeCisdSignal(pendingBullish.waveStartTime,currentBar.time,entry,stop,true,currentBar.time,
                            confirmed,htfPass,confirmingLabel,fibPass,fibLabel,trendPass,momVolPass,msPass,
                            trendActive,fibActive,msActive,htfActive,momVolActive);
                }
            }
            pendingBullish.active=false;
        }
        if (pendingBearish.active&&close<pendingBearish.triggerOpen){
            boolean trendPass=!trendActive||passTrendFilter(bars,detectionIndex,false,trendFilter);
            boolean momVolPass=!momVolActive||passMomentumAndVolumeFilters(bars,detectionIndex,false,momentumFilter,volumeFilterEnabled,minVolumeRatio);
            boolean htfPass=!htfActive||checkHigherTFAlignment(false);
            boolean fibPass=!fibActive; String fibLabel="";
            if (fibActive){
                double[] fibResult=computeFibRetracement(bars,pendingBearish.waveStartIdx,detectionIndex-1,false);
                if (fibResult[0]>=0&&fibResult[0]>=fibThreshold){ fibPass=true; fibLabel=String.format(java.util.Locale.US,"Fib %.0f%%",fibResult[0]*100); }
            }
            boolean msPass=!msActive||checkMarketStructure(bars,false);
            if (isSignalEligible(cisdGrade,trendPass,fibPass,msPass,htfPass,momVolPass,trendActive,fibActive,msActive,htfActive,momVolActive)){
                double entry=pendingBearish.triggerOpen, stop=pendingBearish.stopLevel;
                if (sessionAllowed(sessionOf(pendingBearish.waveStartTime,nyTZ),ACTIVE_SESSIONS)&&Math.abs(entry-stop)>1e-9){
                    String confirmingLabel=null;
                    if (htfPass&&higherTFConfirmation>0) confirmingLabel=SHORT_LABELS[layers[higherTFConfirmation-1].periodIndex];
                    boolean confirmed=isVolumeProfileConfirmed(bars,detectionIndex,entry);
                    storeCisdSignal(pendingBearish.waveStartTime,currentBar.time,entry,stop,false,currentBar.time,
                            confirmed,htfPass,confirmingLabel,fibPass,fibLabel,trendPass,momVolPass,msPass,
                            trendActive,fibActive,msActive,htfActive,momVolActive);
                }
            }
            pendingBearish.active=false;
        }
    }

    private boolean isDuplicate(long waveStart,double entry){
        for (int i=0;i<cisdStoredCount;i++)
            if (cisdStoredStartTimes[i]==waveStart&&Math.abs(cisdStoredLevels[i]-entry)<1e-6) return true;
        return false;
    }

    /** [reference] ring store + alert sound + shared line + CSV + save. */
    void storeCisdSignal(long startTime,long endTime,double entry,double stop,boolean bullish,
                                 long breakoutTime,boolean confirmed,boolean higherTFAligned,String confirmingLabel,
                                 boolean fibPassed,String fibLabel,boolean trendPassed,boolean momentumPassed,
                                 boolean marketStructurePassed,boolean trendActive,boolean fibActive,boolean msActive,
                                 boolean htfActive,boolean momVolActive){
        if (isDuplicate(startTime,entry)) return;
        if (cisdStoredCount<MAX_CISD_STORED) cisdStoredCount++;
        else {
            for (int i=0;i<MAX_CISD_STORED-1;i++){
                cisdStoredStartTimes[i]=cisdStoredStartTimes[i+1];
                cisdStoredEndTimes[i]=cisdStoredEndTimes[i+1];
                cisdStoredLevels[i]=cisdStoredLevels[i+1];
                cisdStoredBullish[i]=cisdStoredBullish[i+1];
                cisdStoredStopLevels[i]=cisdStoredStopLevels[i+1];
                cisdStoredActivationTime[i]=cisdStoredActivationTime[i+1];
                cisdStoredBreakoutTime[i]=cisdStoredBreakoutTime[i+1];
                cisdStoredLogged[i]=cisdStoredLogged[i+1];
                cisdStoredRetestPlayed[i]=cisdStoredRetestPlayed[i+1];
                cisdStoredConfirmed[i]=cisdStoredConfirmed[i+1];
                cisdStoredHigherTFAligned[i]=cisdStoredHigherTFAligned[i+1];
                cisdStoredConfirmingTFLabel[i]=cisdStoredConfirmingTFLabel[i+1];
                cisdStoredFibPassed[i]=cisdStoredFibPassed[i+1];
                cisdStoredFibLabel[i]=cisdStoredFibLabel[i+1];
                cisdStoredTrendPassed[i]=cisdStoredTrendPassed[i+1];
                cisdStoredMomentumPassed[i]=cisdStoredMomentumPassed[i+1];
                cisdStoredVolumePassed[i]=cisdStoredVolumePassed[i+1];
                cisdStoredMarketStructurePassed[i]=cisdStoredMarketStructurePassed[i+1];
                cisdStoredFilterSymbols[i]=cisdStoredFilterSymbols[i+1];
                cisdStoredTrendActive[i]=cisdStoredTrendActive[i+1];
                cisdStoredFibActive[i]=cisdStoredFibActive[i+1];
                cisdStoredMSActive[i]=cisdStoredMSActive[i+1];
                cisdStoredHTFActive[i]=cisdStoredHTFActive[i+1];
                cisdStoredMomVolActive[i]=cisdStoredMomVolActive[i+1];
            }
        }
        int idx=cisdStoredCount-1;
        cisdStoredStartTimes[idx]=startTime;
        cisdStoredEndTimes[idx]=endTime;
        cisdStoredLevels[idx]=entry;
        cisdStoredBullish[idx]=bullish;
        cisdStoredStopLevels[idx]=stop;
        cisdStoredActivationTime[idx]=0;
        cisdStoredBreakoutTime[idx]=breakoutTime;
        cisdStoredLogged[idx]=false;
        cisdStoredRetestPlayed[idx]=false;
        cisdStoredConfirmed[idx]=confirmed;
        cisdStoredHigherTFAligned[idx]=higherTFAligned;
        cisdStoredConfirmingTFLabel[idx]=confirmingLabel;
        cisdStoredFibPassed[idx]=fibPassed;
        cisdStoredFibLabel[idx]=fibLabel;
        cisdStoredTrendPassed[idx]=trendPassed;
        cisdStoredMomentumPassed[idx]=momentumPassed;
        cisdStoredVolumePassed[idx]=volumeFilterEnabled;
        cisdStoredMarketStructurePassed[idx]=marketStructurePassed;
        cisdStoredTrendActive[idx]=trendActive;
        cisdStoredFibActive[idx]=fibActive;
        cisdStoredMSActive[idx]=msActive;
        cisdStoredHTFActive[idx]=htfActive;
        cisdStoredMomVolActive[idx]=momVolActive;
        if (!cisdAlertSound.equals("None")) playSound(cisdAlertSound);
        if (context!=null){ // files need the JForex context (tests run headless)
            writeSharedCISDLine(bullish,entry,breakoutTime,confirmed);
            writeCisdSignalToCsv(idx);
            if (saveLoadCISD) saveCisdToFile();
        }
    }

    /** [reference] activation tracking for retest sound (entry touched after breakout). */
    private void updateCisdStates(RB[] bars){
        if (cisdStoredCount==0) return;
        for (int i=0;i<cisdStoredCount;i++){
            if (cisdStoredActivationTime[i]==0){
                for (RB bar:bars){
                    if (bar.time<=cisdStoredBreakoutTime[i]) continue;
                    boolean touchedEntry=cisdStoredBullish[i]? (bar.l<=cisdStoredLevels[i]) : (bar.h>=cisdStoredLevels[i]);
                    if (touchedEntry){ cisdStoredActivationTime[i]=bar.time; break; }
                }
            }
        }
    }
    private void checkRetestFrequent(RB currentBar){
        if (cisdStoredCount==0||currentBar==null||cisdRetestSound.equals("None")) return;
        long currentBarTime=currentBar.time;
        for (int i=0;i<cisdStoredCount;i++){
            if (cisdStoredRetestPlayed[i]) continue;
            if (currentBarTime<=cisdStoredBreakoutTime[i]) continue;
            if (cisdStoredActivationTime[i]!=0){
                playSound(cisdRetestSound);
                cisdStoredRetestPlayed[i]=true;
                updateSharedRetestLine(i);
                continue;
            }
            boolean touched=cisdStoredBullish[i]? (currentBar.l<=cisdStoredLevels[i]) : (currentBar.h>=cisdStoredLevels[i]);
            if (touched){
                cisdStoredActivationTime[i]=currentBarTime;
                playSound(cisdRetestSound);
                cisdStoredRetestPlayed[i]=true;
                updateSharedRetestLine(i);
                sharedFileLastModified=0;
            }
        }
    }
    private void resetCISDData(){
        try {
            cisdStoredCount=0;
            sharedAlertLines.clear();
            File sharedFile=getSharedCISDPath(); if (sharedFile.exists()) sharedFile.delete();
            File signalFile=new File(filesDir(),"HigherTF_Signals.csv"); if (signalFile.exists()) signalFile.delete();
            File decisionsFile=getJournalDecisionsPath(); if (decisionsFile.exists()) decisionsFile.delete();
            File cisdFile=getCisdFilePath(); if (cisdFile.exists()) cisdFile.delete();
            journalDecisions.clear(); journalDecisionsLastModified=0;
        } catch (Exception e){ /* best-effort */ }
    }
    /** [review 2026-09-10] resolves the sanctioned getFilesDir() first, then user.dir
     *  (compatibility); reuses ONE Clip per file (no native audio-line leak);
     *  every failure path is surfaced once in Messages (no silent audio). */
    private final java.util.Map<String,Clip> soundClips=new java.util.HashMap<>();
    private boolean soundFileWarned=false;
    private boolean soundPlayWarned=false;
    private void warnSoundFile(String msg){
        if (soundFileWarned) return; soundFileWarned=true;
        try { context.getConsole().getWarn().println(msg); } catch (Exception ignore){}
    }
    private void warnSoundPlay(String msg){
        if (soundPlayWarned) return; soundPlayWarned=true;
        try { context.getConsole().getWarn().println(msg); } catch (Exception ignore){}
    }
    private void playSound(String filename){
        if (filename.equals("None")||context==null) return;
        try {
            File soundFile=resolveSoundFile(filename);
            if (soundFile==null){
                File fd=null; try { fd=filesDir(); } catch (Exception ignore){}
                warnSoundFile("TTFMEssence: sound file '"+filename+"' not found in "+fd+" or "+System.getProperty("user.dir")
                    +" - copy alert.wav / retest.wav there (they ship in jforex/sounds/)");
                return;
            }
            Clip clip=soundClips.get(filename);
            if (clip==null){ clip=AudioSystem.getClip(); soundClips.put(filename,clip); }
            if (clip.isRunning()) clip.stop();
            AudioInputStream audioIn=AudioSystem.getAudioInputStream(soundFile);
            clip.open(audioIn); clip.start();
        } catch (Exception e){
            warnSoundPlay("TTFMEssence: sound playback failed ("+filename+"): "+e);
        }
    }
    private File resolveSoundFile(String filename){
        File fd=null;
        try { fd=filesDir(); } catch (Exception ignore){}
        if (fd!=null){ File a=new File(fd,filename); if (a.exists()) return a; }
        File b=new File(System.getProperty("user.dir"),filename);
        if (b.exists()) return b;
        return null;
    }

    // ---------------- files: journal CSV / properties / shared / decisions ----------------
    private File filesDir(){ return context.getFilesDir(); }   // real JForex API returns File
    private String currentInstrumentName(){
        try { return context.getFeedDescriptor().getInstrument().toString(); }
        catch (Exception e){ return ""; }
    }
    private TimeZone activeGridTz(){ return resolveGrid(gridMode,currentInstrumentName()); }
    private String currentTfShort(){ return tfShort(context.getFeedDescriptor().getPeriod().getInterval()); }
    private long currentPeriodMs(){ return context.getFeedDescriptor().getPeriod().getInterval(); }

    /** [reference] HigherTF_Signals.csv - appended at signal time, decisions stay separate. */
    private void writeCisdSignalToCsv(int index){
        File path=new File(filesDir(),"HigherTF_Signals.csv");
        boolean fileExists=path.exists();
        String instrument=context.getFeedDescriptor().getInstrument().toString();
        String chartTf=currentTfShort();
        String direction=cisdStoredBullish[index]?"+Cisd":"-Cisd";
        String directionId=cisdStoredBullish[index]?"BUY":"SELL";
        long waveStartMillis=cisdStoredStartTimes[index];
        long signalMillis=cisdStoredEndTimes[index]+currentPeriodMs();
        String waveStartTime=nyTimeFormat.format(new java.util.Date(waveStartMillis));
        String signalTime=nyTimeFormat.format(new java.util.Date(signalMillis));
        String day=dayFormat.format(new java.util.Date(signalMillis));
        String session=sessionOf(signalMillis,nyTZ);
        String cleanInstrument=instrument.replace("/","_").replace(".","_");
        String signalId=cleanInstrument+"_"+chartTf+"_"+directionId+"_"+cisdStoredEndTimes[index];
        String grade=cisdGrade==0?"Standard":(cisdGrade==1?"Premium":"Ultimate");
        int total=0,passed=0;
        if (cisdStoredTrendActive[index]){total++; if(cisdStoredTrendPassed[index])passed++;}
        if (cisdStoredFibActive[index]){total++; if(cisdStoredFibPassed[index])passed++;}
        if (cisdStoredMSActive[index]){total++; if(cisdStoredMarketStructurePassed[index])passed++;}
        if (cisdStoredHTFActive[index]){total++; if(cisdStoredHigherTFAligned[index])passed++;}
        if (cisdStoredMomVolActive[index]){total++; if(cisdStoredMomentumPassed[index]||cisdStoredVolumePassed[index])passed++;}
        String score=total>0?passed+"/"+total:"-";
        try (PrintWriter pw=new PrintWriter(new FileWriter(path,true))){
            if (!fileExists)
                pw.println("SignalID,SignalTimeNY,WaveStartTimeNY,Date,Day,Session,Instrument,TF,Direction,Grade,Score,Trend,Fib,MS,HTF,MomVol,Confirmed");
            pw.printf(java.util.Locale.US,"%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s%n",
                signalId,signalTime,waveStartTime,signalTime.substring(0,10),day,session,instrument,chartTf,direction,grade,score,
                cisdStoredTrendActive[index]?(cisdStoredTrendPassed[index]?"1":"0"):"-",
                cisdStoredFibActive[index]?(cisdStoredFibPassed[index]?"1":"0"):"-",
                cisdStoredMSActive[index]?(cisdStoredMarketStructurePassed[index]?"1":"0"):"-",
                cisdStoredHTFActive[index]?(cisdStoredHigherTFAligned[index]?"1":"0"):"-",
                cisdStoredMomVolActive[index]?((cisdStoredMomentumPassed[index]||cisdStoredVolumePassed[index])?"1":"0"):"-",
                cisdStoredConfirmed[index]?"1":"0");
        } catch (IOException e){ /* console only in reference; silent here */ }
    }

    private File getCisdFilePath(){
        String instrument=context.getFeedDescriptor().getInstrument().toString().replace("/","_");
        return new File(filesDir(),"TTFMEssence_cisd_"+instrument+"_"+currentTfShort()+".properties");
    }
    private void saveCisdToFile(){
        Properties p=new Properties();
        int count=0;
        for (int i=0;i<cisdStoredCount;i++){
            if (cisdStoredActivationTime[i]==0){
                String pre="cisd."+count+".";
                p.setProperty(pre+"startTime",String.valueOf(cisdStoredStartTimes[i]));
                p.setProperty(pre+"endTime",String.valueOf(cisdStoredEndTimes[i]));
                p.setProperty(pre+"entry",String.valueOf(cisdStoredLevels[i]));
                p.setProperty(pre+"stop",String.valueOf(cisdStoredStopLevels[i]));
                p.setProperty(pre+"bullish",String.valueOf(cisdStoredBullish[i]));
                p.setProperty(pre+"breakoutTime",String.valueOf(cisdStoredBreakoutTime[i]));
                p.setProperty(pre+"confirmed",String.valueOf(cisdStoredConfirmed[i]));
                p.setProperty(pre+"higherTFAligned",String.valueOf(cisdStoredHigherTFAligned[i]));
                p.setProperty(pre+"confirmingTFLabel",cisdStoredConfirmingTFLabel[i]!=null?cisdStoredConfirmingTFLabel[i]:"");
                p.setProperty(pre+"fibPassed",String.valueOf(cisdStoredFibPassed[i]));
                p.setProperty(pre+"fibLabel",cisdStoredFibLabel[i]!=null?cisdStoredFibLabel[i]:"");
                p.setProperty(pre+"trendPassed",String.valueOf(cisdStoredTrendPassed[i]));
                p.setProperty(pre+"momentumPassed",String.valueOf(cisdStoredMomentumPassed[i]));
                p.setProperty(pre+"volumePassed",String.valueOf(cisdStoredVolumePassed[i]));
                p.setProperty(pre+"marketStructurePassed",String.valueOf(cisdStoredMarketStructurePassed[i]));
                p.setProperty(pre+"trendActive",String.valueOf(cisdStoredTrendActive[i]));
                p.setProperty(pre+"fibActive",String.valueOf(cisdStoredFibActive[i]));
                p.setProperty(pre+"msActive",String.valueOf(cisdStoredMSActive[i]));
                p.setProperty(pre+"htfActive",String.valueOf(cisdStoredHTFActive[i]));
                p.setProperty(pre+"momVolActive",String.valueOf(cisdStoredMomVolActive[i]));
                count++;
            }
        }
        p.setProperty("cisd.count",String.valueOf(count));
        try (FileOutputStream fos=new FileOutputStream(getCisdFilePath())){
            p.store(fos,"TTFMEssence CISD");
        } catch (IOException e){ }
    }
    private void loadCisdFromFile(){
        File f=getCisdFilePath();
        if (!f.exists()) return;
        Properties p=new Properties();
        try (FileInputStream fis=new FileInputStream(f)){
            p.load(fis);
            int count=Integer.parseInt(p.getProperty("cisd.count","0"));
            for (int i=0;i<count&&cisdStoredCount<MAX_CISD_STORED;i++){
                String pre="cisd."+i+".";
                int idx=cisdStoredCount;
                cisdStoredStartTimes[idx]=Long.parseLong(p.getProperty(pre+"startTime","0"));
                cisdStoredEndTimes[idx]=Long.parseLong(p.getProperty(pre+"endTime","0"));
                cisdStoredLevels[idx]=Double.parseDouble(p.getProperty(pre+"entry","0"));
                cisdStoredStopLevels[idx]=Double.parseDouble(p.getProperty(pre+"stop","0"));
                cisdStoredBullish[idx]=Boolean.parseBoolean(p.getProperty(pre+"bullish","false"));
                cisdStoredBreakoutTime[idx]=Long.parseLong(p.getProperty(pre+"breakoutTime","0"));
                cisdStoredActivationTime[idx]=0;
                cisdStoredLogged[idx]=false;
                cisdStoredRetestPlayed[idx]=false;
                cisdStoredConfirmed[idx]=Boolean.parseBoolean(p.getProperty(pre+"confirmed","false"));
                cisdStoredHigherTFAligned[idx]=Boolean.parseBoolean(p.getProperty(pre+"higherTFAligned","false"));
                cisdStoredConfirmingTFLabel[idx]=p.getProperty(pre+"confirmingTFLabel",null);
                cisdStoredFibPassed[idx]=Boolean.parseBoolean(p.getProperty(pre+"fibPassed","false"));
                cisdStoredFibLabel[idx]=p.getProperty(pre+"fibLabel",null);
                cisdStoredTrendPassed[idx]=Boolean.parseBoolean(p.getProperty(pre+"trendPassed","false"));
                cisdStoredMomentumPassed[idx]=Boolean.parseBoolean(p.getProperty(pre+"momentumPassed","false"));
                cisdStoredVolumePassed[idx]=Boolean.parseBoolean(p.getProperty(pre+"volumePassed","false"));
                cisdStoredMarketStructurePassed[idx]=Boolean.parseBoolean(p.getProperty(pre+"marketStructurePassed","false"));
                cisdStoredTrendActive[idx]=Boolean.parseBoolean(p.getProperty(pre+"trendActive","false"));
                cisdStoredFibActive[idx]=Boolean.parseBoolean(p.getProperty(pre+"fibActive","false"));
                cisdStoredMSActive[idx]=Boolean.parseBoolean(p.getProperty(pre+"msActive","false"));
                cisdStoredHTFActive[idx]=Boolean.parseBoolean(p.getProperty(pre+"htfActive","false"));
                cisdStoredMomVolActive[idx]=Boolean.parseBoolean(p.getProperty(pre+"momVolActive","false"));
                cisdStoredCount++;
            }
        } catch (IOException e){ }
    }

    private File getJournalDecisionsPath(){ return new File(filesDir(),"CISD_Journal_Decisions.csv"); }
    private void updateJournalDecisionsFromFile(){
        File file=getJournalDecisionsPath();
        long modified=file.exists()?file.lastModified():0;
        if (modified==journalDecisionsLastModified) return;
        journalDecisionsLastModified=modified;
        journalDecisions.clear();
        if (!file.exists()) return;
        try (BufferedReader reader=new BufferedReader(new FileReader(file))){
            String line; boolean header=true;
            while ((line=reader.readLine())!=null){
                if (header){header=false;continue;}
                String[] parts=line.replace("\"","").split(",",4);
                if (parts.length>=2&&parts[0].trim().length()>0)
                    journalDecisions.put(parts[0].trim(),parts[1].trim().toUpperCase());
            }
        } catch (IOException ignored){ }
    }
    private String getSignalId(int index){
        String instrument=context.getFeedDescriptor().getInstrument().toString().replace("/","_").replace(".","_");
        return instrument+"_"+currentTfShort()+"_"+(cisdStoredBullish[index]?"BUY":"SELL")+"_"+cisdStoredEndTimes[index];
    }
    private String findJournalDecision(int index){
        String exact=journalDecisions.get(getSignalId(index));
        if (exact!=null) return exact;
        String instrument=context.getFeedDescriptor().getInstrument().toString().replace("/","_").replace(".","_");
        String suffix="_"+(cisdStoredBullish[index]?"BUY":"SELL")+"_"+cisdStoredEndTimes[index];
        for (Map.Entry<String,String> en:journalDecisions.entrySet())
            if (en.getKey().startsWith(instrument+"_")&&en.getKey().endsWith(suffix)) return en.getValue();
        return null;
    }

    private File getSharedCISDPath(){ return new File(filesDir(),"SharedCISD.csv"); }
    private List<String[]> readSharedCISDLinesInternal(){
        List<String[]> lines=new ArrayList<>();
        File f=getSharedCISDPath();
        if (!f.exists()) return lines;
        try (BufferedReader br=new BufferedReader(new FileReader(f))){
            String line;
            while ((line=br.readLine())!=null){
                String[] parts=line.split(",");
                if (parts.length>=7) lines.add(new String[]{parts[0],parts[1],parts[2],parts[3],parts[4],parts[5],parts[6]});
                else if (parts.length==6) lines.add(new String[]{parts[0],parts[1],parts[2],parts[3],parts[4],parts[5],"false"});
                else if (parts.length==5) lines.add(new String[]{parts[0],parts[1],parts[2],parts[3],parts[4],"0","false"});
                else if (parts.length==4) lines.add(new String[]{parts[0],parts[1],parts[2],parts[3],"false","0","false"});
            }
        } catch (IOException e){ }
        return lines;
    }
    /** [reference] SharedCISD.csv: Instrument,TF,Type,Entry,Retest,BreakoutTime,Confirmed (newest first, max 5). */
    private void writeSharedCISDLine(boolean bullish,double entry,long breakoutTime,boolean confirmed){
        if (!sharedCISDAlerts) return;
        synchronized (sharedFileLock){
            try {
                String instrument=context.getFeedDescriptor().getInstrument().toString();
                String tf=currentTfShort();
                List<String[]> lines=readSharedCISDLinesInternal();
                lines.add(0,new String[]{instrument,tf,(bullish?"+Cisd":"-Cisd"),String.valueOf(entry),"false",String.valueOf(breakoutTime),String.valueOf(confirmed)});
                while (lines.size()>maxSharedLines) lines.remove(lines.size()-1);
                try (PrintWriter pw=new PrintWriter(new FileWriter(getSharedCISDPath()))){
                    for (String[] l:lines) pw.println(l[0]+","+l[1]+","+l[2]+","+l[3]+","+l[4]+","+l[5]+","+l[6]);
                }
            } catch (Exception e){ }
        }
    }
    private void updateSharedRetestLine(int index){
        if (!sharedCISDAlerts) return;
        synchronized (sharedFileLock){
            List<String[]> lines=readSharedCISDLinesInternal();
            boolean changed=false;
            for (String[] parts:lines){
                if (parts.length<6) continue;
                long btTime;
                try { btTime=Long.parseLong(parts[5]); } catch (NumberFormatException e){ continue; }
                if (btTime==cisdStoredBreakoutTime[index]&&!"true".equals(parts[4])){ parts[4]="true"; changed=true; break; }
            }
            if (changed){
                try (PrintWriter pw=new PrintWriter(new FileWriter(getSharedCISDPath()))){
                    for (String[] l:lines)
                        pw.println(l[0]+","+l[1]+","+l[2]+","+l[3]+","+l[4]+","+l[5]+","+(l.length>6?l[6]:"false"));
                } catch (IOException e){ }
            }
        }
    }
    private void updateSharedAlertsFromFile(){
        if (!sharedCISDAlerts) return;
        File sf=getSharedCISDPath();
        long mod=sf.exists()?sf.lastModified():0;
        if (mod!=sharedFileLastModified){
            sharedFileLastModified=mod;
            synchronized (sharedFileLock){
                List<String[]> lines=readSharedCISDLinesInternal();
                sharedAlertLines.clear();
                for (int i=0;i<Math.min(maxSharedLines,lines.size());i++) sharedAlertLines.add(lines.get(i));
            }
        }
    }

    // ==================================================================
    //  concept state: legs for ladder + T-Spot zones (+internal POI gate)
    // ==================================================================
    private void updateConceptState(RB[] bars,LayerData primary,long periodMs){
        drawAnchor=primary;
        int n=Math.min(20,bars.length); double ar=0;
        for (int i=bars.length-n;i<bars.length;i++) ar+=bars[i].h-bars[i].l;
        chartAvgRange=(n>0)?ar/n:0;

        fvgZones.clear();
        List<CandleData> h0=drawAnchor.historical;
        RB[] hb=new RB[h0.size()];
        for (int i=0;i<h0.size();i++){
            CandleData c=h0.get(i);
            hb[i]=new RB(c.openTime,c.open,c.high,c.low,c.close,0);
        }
        for (int i=Math.max(2,hb.length-40); i<hb.length && fvgZones.size()<6; i++){
            double[] zb=detectFVG(hb,i,true);  if (zb!=null) fvgZones.add(new double[]{zb[0],zb[1],1});
            double[] zs=detectFVG(hb,i,false); if (zs!=null) fvgZones.add(new double[]{zs[0],zs[1],0});
        }
        obBullIdx=findOrderBlock(bars,bars.length-1,true,chartAvgRange);
        obBearIdx=findOrderBlock(bars,bars.length-1,false,chartAvgRange);
        int[] st=structureState(bars,60); structTrend=st[0];

        c2Idx=-1;
        legT1=legT2=legT3=0;legP1=Double.NaN;legP2=Double.NaN;legP3=Double.NaN;
        List<CandleData> hist=primary.historical;
        for (int k=hist.size()-1;k>=1;k--){
            CandleData c2=hist.get(k), c1=hist.get(k-1);
            boolean bull=c2.close>=c1.candleEQ();
            if (c2Closure(c1,c2,bull,poiInRespectedHalf(c1,bull),structTrend)){
                c2Idx=k;
                legT1=c1.openTime; legP1=bull?c1.high:c1.low;
                legT2=c2.openTime; legP2=bull?c2.low:c2.high;
                legT3=legT2; legP3=legP2;
                if (k+1<hist.size()&&c3Closure(c2,hist.get(k+1),bull)){
                    CandleData c3c=hist.get(k+1);
                    legT3=c3c.openTime; legP3=bull?c3c.low:c3c.high;
                }
                break;
            }
        }

        tspotZones.clear();
        long pMs=PERIOD_INTERVALS[primary.periodIndex];
        for (int k=1;k<hist.size();k++){
            CandleData cN=hist.get(k-1), cN1=hist.get(k);
            if (!cN.completed) continue;
            if (!modelZoneGate(hist,k-1)) continue;
            addTspotZone(tspotEQ(cN),cN1.open,cN1.openTime,cN1.openTime+pMs,bars,false);
        }
        if (primary.curActive && hist.size()>=1){
            CandleData cN=hist.get(hist.size()-1);
            if (cN.completed){   // frozen: current-candle zone ALWAYS prints
                long nowT=bars[bars.length-1].time;
                long end=Math.max(primary.curStart+1,Math.min(nowT,primary.curStart+pMs));
                addTspotZone(tspotEQ(cN),primary.curO,primary.curStart,end,bars,true);
            }
        }
    }

    private void addTspotZone(double eq,double open,long start,long end,RB[] bars,boolean current){
        double[] z=tspotZone(eq,open);
        if (z==null) return;
        int type=tspotType(eq,open);
        int from=-1;
        for (int i=0;i<bars.length;i++) if (bars[i].time>=start){ from=i; break; }
        int state=(from<0)?0:zoneState(z[0],z[1],type,bars,from);
        tspotZones.add(new double[]{z[0],z[1],start,end,type,state,current?1:0});
    }
    private boolean poiInRespectedHalf(CandleData c1,boolean bull){
        double eq=c1.candleEQ();
        double lo=bull?eq:c1.low, hi=bull?c1.high:eq;
        for (double[] z:fvgZones) if (z[2]==(bull?1:0)&&z[1]>lo&&z[0]<hi) return true;
        if (bull&&obBullIdx>=0) return true;
        if (!bull&&obBearIdx>=0) return true;
        return false;
    }

    // ==================================================================
    //  Drawing - the ten approved elements, nothing else
    // ==================================================================
    @Override
    public java.awt.Point drawOutput(Graphics g,int outputIdx,Object values,Color color,
                                     Stroke stroke,IIndicatorDrawingSupport support,
                                     List<Shape> shapes,Map<Color,List<java.awt.Point>> handles){
        if (outputIdx%4!=0) return null;
        int candleIdx=outputIdx/4;
        if (candleIdx>=MAX_CANDLES) return null;

        Graphics2D g2=(Graphics2D)g;
        Stroke oldStroke=g2.getStroke(); Font oldFont=g2.getFont();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);

        int numCandles=support.getNumberOfCandlesOnScreen();
        if (numCandles<=0) return null;
        int first=support.getIndexOfFirstCandleOnScreen();
        int last=first+numCandles-1;
        float cW=support.getCandleWidthInPixels(0), sW=support.getSpaceBetweenCandlesInPixels(0);
        float slot=cW+sW; if (slot<=0) return null;

        long chartInterval=context.getFeedDescriptor().getPeriod().getInterval();
        int spacing=3;

        // frozen right-side stacking: 4H cluster then D cluster
        int[] base=new int[MAX_LAYERS]; int right=0;
        for (int i=0;i<MAX_LAYERS;i++){ LayerData l=layers[i];
            if (!l.enabled||PERIOD_INTERVALS[l.periodIndex]<=chartInterval) continue;
            int st=Math.max(right,l.candleOffset);base[i]=st;right=st+l.candlesToShow+spacing; }

        // elements 1,2,3: clusters + C-columns
        for (int i=0;i<MAX_LAYERS;i++){
            LayerData layer=layers[i];
            if (!layer.enabled||PERIOD_INTERVALS[layer.periodIndex]<=chartInterval) continue;
            List<CandleData> disp=displayList(layer);
            if (candleIdx>=disp.size()) continue;
            CandleData cd=disp.get(candleIdx);
            if (cd==null||!validOHLC(cd)) continue;
            long periodMs=PERIOD_INTERVALS[layer.periodIndex];

            int xCenter=(int)(support.getMiddleOfCandle(last)+slot*(base[i]+candleIdx));
            float maxWidth=Math.max(1,slot-2);
            int bodyWidth=(int)Math.max(1,Math.min(cW,maxWidth));
            int halfBody=Math.max(1,bodyWidth/2);
            int yO=(int)support.getYForValue(cd.open),yH=(int)support.getYForValue(cd.high);
            int yL=(int)support.getYForValue(cd.low),yC=(int)support.getYForValue(cd.close);
            if (yO<0&&yH<0&&yL<0&&yC<0) continue;
            int top=Math.min(yO,yC),bot=Math.max(yO,yC),bodyH=Math.max(1,bot-top);
            boolean bull=cd.close>=cd.open;

            g2.setColor(WICK_COLOR);g2.setStroke(new BasicStroke(1f));
            g2.drawLine(xCenter,yH,xCenter,top);g2.drawLine(xCenter,bot,xCenter,yL);
            g2.setColor(bull?BULLISH_BODY_COLOR:BEARISH_BODY_COLOR);
            g2.fillRect(xCenter-halfBody,top,bodyWidth,bodyH);
            g2.setColor(BORDER_COLOR);g2.setStroke(new BasicStroke(0.8f));
            g2.drawRect(xCenter-halfBody,top,bodyWidth,bodyH);

            // element 2,3: TF label above the cluster's tallest candle
            if (candleIdx==0){
                double hh=-Double.MAX_VALUE;int hi=0;
                for (int k=0;k<disp.size();k++) if (disp.get(k)!=null&&disp.get(k).high>hh){hh=disp.get(k).high;hi=k;}
                if (hh!=-Double.MAX_VALUE){
                    int lx=(int)(support.getMiddleOfCandle(last)+slot*(base[i]+hi));
                    int ly=(int)support.getYForValue(disp.get(hi).high)-4;
                    String lbl=SHORT_LABELS[layer.periodIndex];
                    g2.setFont(oldFont.deriveFont(Font.BOLD,9f));
                    g2.setColor(new Color(100,100,100));
                    int tw=g2.getFontMetrics().stringWidth(lbl);
                    int a9=g2.getFontMetrics().getAscent();
                    g2.drawString(lbl,lx-tw/2,ly);
                    // [Display] Show Timer (agreement 2026-09-10, D6 wall clock):
                    // countdown until the CURRENT layer candle closes - fully above
                    // the TF label. REMOVAL (if it fails the requirement): delete
                    // this block + showTimer field + its option (2 lines) + tests.
                    if (showTimer){
                        long remain=layer.curStart+periodMs-System.currentTimeMillis();
                        String t=countdownText(remain);
                        if (!t.isEmpty()){
                            g2.setFont(oldFont.deriveFont(Font.BOLD,10f));
                            FontMetrics fm=g2.getFontMetrics();int tw2=fm.stringWidth(t);
                            int ty=ly-a9-8;   // clear vertical gap: countdown sits fully ABOVE the TF label
                            CandleData tall=disp.get(hi);
                            g2.setColor(tall.close>=tall.open?new Color(0,200,0,100):new Color(200,0,0,100));
                            g2.fillRect(lx-tw2/2-3,ty-fm.getAscent()-3,tw2+6,fm.getAscent()+6);
                            g2.setColor(Color.WHITE);g2.drawString(t,lx-tw2/2,ty);
                        }
                    }
                }
            }

            // element 1: C-column band + dotted vertical + label with time range
            if (i==ANCHOR_LAYER-1){
                int ch=support.getChartHeight();
                int xOpen=support.getXForTime(cd.openTime,false);
                if (xOpen>=0){
                    long endT=cd.completed?(cd.openTime+periodMs):Math.max(cd.openTime+1,Math.min(chartNowTime,cd.openTime+periodMs));
                    int xEnd=support.getXForTime(endT,false);
                    if ((xEnd<0||xEnd<=xOpen)&&candleIdx+1<disp.size()) xEnd=support.getXForTime(disp.get(candleIdx+1).openTime,false);
                    if (xEnd<0||xEnd<=xOpen){
                        long barsSpan=(chartPeriodMs>0)?periodMs/chartPeriodMs:8;
                        xEnd=Math.min(support.getChartWidth()-1,xOpen+(int)Math.max(8,slot*barsSpan));
                    }
                    if (xEnd>xOpen){
                        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,(candleIdx%2==0)?0.06f:0.10f));
                        g2.setColor(new Color(160,160,160));
                        g2.fillRect(xOpen,0,xEnd-xOpen,ch);
                        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,1.0f));
                    }
                    g2.setColor(CLOSURE_COLOR);g2.setStroke(dashStroke(1,2));
                    g2.drawLine(xOpen,0,xOpen,ch-1);
                    SimpleDateFormat lf=new SimpleDateFormat(colRangePattern(periodMs));lf.setTimeZone(DISPLAY_TZ);
                    String t="C"+(candleIdx+1)+" ("+lf.format(new Date(cd.openTime))+"-"+lf.format(new Date(cd.openTime+periodMs))+")";
                    g2.setFont(oldFont.deriveFont(Font.BOLD,9f));
                    int tw=g2.getFontMetrics().stringWidth(t);
                    int tx=(xOpen+4+tw>support.getChartWidth())?xOpen-tw-3:xOpen+4;
                    g2.drawString(t,tx,12);
                }
            }
        }

        if (outputIdx==0){
            drawPrevEqOpen(g2,support);          // element 6 (current-candle dotted bounds)
            drawCisdLines(g2,support,slot,oldFont); // element 8 (CISD Desk cards)
            drawSharedAlertsPanel(g2,support,oldFont);
            drawTspot(g2,support);               // elements 4,5,6
            drawProjLadder(g2,support);          // element 9
            drawNyOpenLines(g2,support,oldFont); // session overlay (opt-in, OFF default)
            drawInfoPanel(g2,support,oldFont);   // element 10
        }

        g2.setStroke(oldStroke);g2.setFont(oldFont);
        return null;
    }

    private Stroke dashStroke(int width,int style){
        float[] dash=(style==0)?null:(style==1)?new float[]{6f,4f}:new float[]{1f,3f};
        return new BasicStroke(width,BasicStroke.CAP_BUTT,BasicStroke.JOIN_MITER,10f,dash,0f);
    }
    private boolean validOHLC(CandleData cd){
        return !Double.isNaN(cd.open)&&!Double.isNaN(cd.high)&&!Double.isNaN(cd.low)&&!Double.isNaN(cd.close)
            && cd.high>=Math.max(cd.open,cd.close)-1e-8 && cd.low<=Math.min(cd.open,cd.close)+1e-8;
    }

    /** element 7: CISD detection drawing ONLY - entry line in direction color,
     *  label, and the dotted protected-swing stop (stop stays with the engine,
     *  step-3 lock). State colors and TP lines are OFF by user correction. */
    /** [reference drawCISDLines] card per stored signal: entry line + label + decision + retest tag + filter badges. */
    private void drawCisdLines(Graphics2D g2,IIndicatorDrawingSupport support,float slot,Font oldFont){
        if (!showCISD||cisdStoredCount==0) return;
        String tfShort=currentTfShort();
        updateJournalDecisionsFromFile();
        for (int i=0;i<cisdStoredCount;i++){
            long sigStart=cisdStoredStartTimes[i];
            long sigEnd=cisdStoredEndTimes[i];
            double sigLevel=cisdStoredLevels[i];
            boolean sigBullish=cisdStoredBullish[i];
            int xStart=support.getXForTime(sigStart,false);
            int xBreakout=support.getXForTime(sigEnd,false);
            if (xBreakout<0) continue;
            if (xStart<0) xStart=0; // [review] clip at the screen edge instead of dropping the whole line
            int yLevel=(int)support.getYForValue(sigLevel);
            if (yLevel<0||yLevel>=support.getChartHeight()) continue;
            int xEnd=xBreakout+(int)(3*slot);
            if (xEnd>support.getChartWidth()) xEnd=support.getChartWidth();

            Color lineColor=sigBullish?CISD_BULL_COLOR:CISD_BEAR_COLOR;
            g2.setColor(lineColor);
            g2.setStroke(new BasicStroke(2.0f,BasicStroke.CAP_BUTT,BasicStroke.JOIN_MITER));
            g2.drawLine(xStart,yLevel,xEnd,yLevel);

            String mainLabel=(sigBullish?"+Cisd":"-Cisd")+" ("+tfShort+")";
            g2.setFont(oldFont.deriveFont(Font.BOLD,10f));
            FontMetrics fmMain=g2.getFontMetrics();
            int cisTextX=xEnd+4;
            int cisTextY=yLevel-4;
            if (cisTextX+fmMain.stringWidth(mainLabel)>support.getChartWidth())
                cisTextX=support.getChartWidth()-fmMain.stringWidth(mainLabel)-10;
            g2.setColor(lineColor);
            g2.drawString(mainLabel,cisTextX,cisTextY);
            drawJournalDecision(g2,findJournalDecision(i),cisTextX,cisTextY,sigBullish,oldFont);
            int curX=cisTextX+fmMain.stringWidth(mainLabel)+4;

            if (cisdStoredRetestPlayed[i]){
                Font retestFont=oldFont.deriveFont(Font.ITALIC,8f);
                g2.setFont(retestFont);
                FontMetrics fmRetest=g2.getFontMetrics();
                String retestStr="retest";
                int retestWidth=fmRetest.stringWidth(retestStr);
                Color retestColor=sigBullish?new Color(100,180,255):new Color(255,120,120);
                g2.setColor(retestColor);
                g2.drawString(retestStr,curX,cisTextY);
                curX+=retestWidth+6;
            }

            g2.setFont(oldFont.deriveFont(Font.PLAIN,7f));
            FontMetrics fmBadge=g2.getFontMetrics();
            int badgePadding=2, badgeArc=4;
            if (cisdStoredFibActive[i]&&cisdStoredFibPassed[i]){
                drawBadge(g2,"FIB",curX,cisTextY,fmBadge,badgePadding,badgeArc,new Color(0,180,0,150),Color.WHITE);
                curX+=fmBadge.stringWidth("FIB")+badgePadding*2+3;
            }
            if (cisdStoredMSActive[i]&&cisdStoredMarketStructurePassed[i]){
                drawBadge(g2,"STR",curX,cisTextY,fmBadge,badgePadding,badgeArc,new Color(128,0,128,150),Color.WHITE);
                curX+=fmBadge.stringWidth("STR")+badgePadding*2+3;
            }
            if (cisdStoredTrendActive[i]&&cisdStoredTrendPassed[i]){
                drawBadge(g2,"TREND",curX,cisTextY,fmBadge,badgePadding,badgeArc,new Color(0,100,200,150),Color.WHITE);
                curX+=fmBadge.stringWidth("TREND")+badgePadding*2+3;
            }
            if (cisdStoredHTFActive[i]&&cisdStoredHigherTFAligned[i]){
                drawBadge(g2,"HTF",curX,cisTextY,fmBadge,badgePadding,badgeArc,new Color(218,165,32,150),Color.WHITE);
                curX+=fmBadge.stringWidth("HTF")+badgePadding*2+3;
            }
            if (cisdStoredMomVolActive[i]&&(cisdStoredMomentumPassed[i]||cisdStoredVolumePassed[i])){
                drawBadge(g2,"MOM",curX,cisTextY,fmBadge,badgePadding,badgeArc,new Color(255,140,0,150),Color.WHITE);
                curX+=fmBadge.stringWidth("MOM")+badgePadding*2+3;
            }
            if (cisdStoredConfirmed[i])
                drawBadge(g2,"CONF",curX,cisTextY,fmBadge,badgePadding,badgeArc,new Color(255,215,0,150),Color.BLACK);
        }
    }
    private void drawBadge(Graphics2D g2,String text,int x,int y,FontMetrics fm,int pad,int arc,Color bgColor,Color textColor){
        int textWidth=fm.stringWidth(text);
        int textHeight=fm.getAscent();
        int badgeWidth=textWidth+pad*2;
        int badgeHeight=textHeight+pad*2;
        g2.setColor(bgColor);
        g2.fillRoundRect(x,y-textHeight,badgeWidth,badgeHeight,arc,arc);
        g2.setColor(textColor);
        g2.drawString(text,x+pad,y);
    }
    /** [reference] decision tag from CISD_Journal_Decisions.csv (ENTERED/SKIPPED/IGNORED/REVIEW). */
    private void drawJournalDecision(Graphics2D g2,String decision,int x,int y,boolean bullish,Font oldFont){
        if (decision==null||decision.length()==0) return;
        Color color="ENTERED".equals(decision)?new Color(0,170,80):
                "SKIPPED".equals(decision)?new Color(220,75,75):
                "IGNORED".equals(decision)?new Color(130,130,130):new Color(220,165,0);
        String label="ENTERED".equals(decision)?"\u2713 ENTERED":
                "SKIPPED".equals(decision)?"\u00d7 SKIPPED":
                "IGNORED".equals(decision)?"\u2014 IGNORED":"\u231b REVIEW";
        g2.setFont(oldFont.deriveFont(Font.BOLD,9f));
        g2.setColor(color);
        g2.drawString(label,x,bullish?y+16:y-16);
    }
    /** [reference drawSharedAlertsPanel] top-left cards for signals from ALL open charts (via SharedCISD.csv). */
    private void drawSharedAlertsPanel(Graphics2D g2,IIndicatorDrawingSupport support,Font oldFont){
        if (!sharedCISDAlerts||sharedAlertLines.isEmpty()) return;
        Font cardFont=oldFont.deriveFont(Font.BOLD,8f);
        g2.setFont(cardFont);
        FontMetrics fm=g2.getFontMetrics();
        int cardHeight=fm.getHeight()+4;
        int cardPaddingX=4, cardArc=6;
        int panelX=10, panelY=20;
        for (int i=0;i<sharedAlertLines.size();i++){
            String[] parts=sharedAlertLines.get(i);
            if (parts.length<5) continue;
            String sym=parts[0];
            String tf=parts[1];
            String type=parts[2];
            boolean isBull=type.startsWith("+");
            boolean retest="true".equals(parts[4]);
            Color cardBg;
            if (retest) cardBg=new Color(100,150,255,160);
            else cardBg=isBull?new Color(0,180,0,160):new Color(255,60,60,160);
            String arrow=isBull?"\u25B2":"\u25BC";
            String displayText=arrow+" "+sym+"  ["+tf+"]";
            if (retest) displayText+=" (R)";
            if (showEntryPrice){
                String entry=parts[3];
                try {
                    double ep=Double.parseDouble(entry);
                    displayText+=" @ "+String.format(java.util.Locale.US,"%.5f",ep);
                } catch (NumberFormatException ignored){ displayText+=" @ "+entry; }
            }
            int textWidth=fm.stringWidth(displayText);
            int cardWidth=textWidth+cardPaddingX*2;
            int cardY=panelY+i*(cardHeight+2);
            g2.setColor(cardBg);
            g2.fillRoundRect(panelX,cardY-fm.getAscent()-1,cardWidth,cardHeight,cardArc,cardArc);
            g2.setColor(new Color(255,255,255,40));
            g2.setStroke(new BasicStroke(0.7f));
            g2.drawRoundRect(panelX,cardY-fm.getAscent()-1,cardWidth,cardHeight,cardArc,cardArc);
            g2.setColor(new Color(30,30,30));
            g2.drawString(displayText,panelX+cardPaddingX,cardY);
        }
    }

    /** elements 4,5,6: T-Spot zones on chart time axis; green BUY-support / red SELL-support;
     *  gray when historically invalidated; current zone always prints and grows; dotted bounds. */
    private void drawTspot(Graphics2D g2,IIndicatorDrawingSupport support){
        for (double[] z:tspotZones){
            double lo=z[0],hi=z[1]; int type=(int)z[4],state=(int)z[5];
            int yT=(int)support.getYForValue(hi), yB=(int)support.getYForValue(lo);
            if (yB<=yT) continue;
            int x1=support.getXForTime((long)z[2],false);
            int x2=support.getXForTime((long)z[3],false);
            if (x1<0) continue;
            if (x2<0||x2<=x1) x2=x1+40;
            boolean cur=z[6]==1;
            Color base=(type==0)?new Color(0,120,60):new Color(190,0,0);
            Color fill=(state==2&&!cur)?new Color(140,140,140):base;
            float alpha=(state==2&&!cur)?0.15f:0.18f;
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,alpha));
            g2.setColor(fill);
            g2.fillRect(x1,yT,x2-x1,yB-yT);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,0.9f));
            g2.setColor(BOUND_COLOR);
            g2.setStroke(dashStroke(1,2));
            g2.drawLine(x1,yT,x2,yT);
            g2.drawLine(x1,yB,x2,yB);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,1.0f));
        }
    }

    /** element 6: previous EQ + current open dotted over the forming candle (grows with it). */
    private void drawPrevEqOpen(Graphics2D g2,IIndicatorDrawingSupport support){
        if (drawAnchor==null||!drawAnchor.curActive) return;
        long pMs=PERIOD_INTERVALS[drawAnchor.periodIndex];
        int x1=support.getXForTime(drawAnchor.curStart,false);
        long endT=Math.max(drawAnchor.curStart+1,Math.min(chartNowTime,drawAnchor.curStart+pMs));
        int x2=support.getXForTime(endT,false);
        if (x1<0||x2<0||x2<=x1) return;
        CandleData prev=null;
        List<CandleData> h=drawAnchor.historical;
        for (int k=h.size()-1;k>=0;k--) if (h.get(k).completed){ prev=h.get(k); break; }
        g2.setColor(BOUND_COLOR);
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,0.9f));
        int yO=(int)support.getYForValue(drawAnchor.curO);
        g2.setStroke(dashStroke(1,2));
        g2.drawLine(x1,yO,x2,yO);
        if (prev!=null){
            int yE=(int)support.getYForValue(tspotEQ(prev));
            g2.setStroke(dashStroke(1,2));
            g2.drawLine(x1,yE,x2,yE);
        }
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,1.0f));
    }

    /** element 9: projection ladder {1,2,2.5,4,4.5} from the manipulation leg + dotted leg-start line. */
    private void drawProjLadder(Graphics2D g2,IIndicatorDrawingSupport support){
        if (Double.isNaN(legP1)||Double.isNaN(legP2)) return;
        double leg=Math.abs(legP2-legP1); if (leg<=0) return;
        boolean bull=legP2<legP1;
        double[] lv=projLadder(legP1,leg,bull);
        String[] nm={"-1","-2","-2.5","-4","-4.5"};
        int cx=support.getChartWidth()/2-60;
        g2.setFont(g2.getFont().deriveFont(7f));
        g2.setColor(new Color(120,120,120));
        g2.setStroke(new BasicStroke(0.8f));
        for (int i=0;i<lv.length;i++){
            int y=(int)support.getYForValue(lv[i]);
            if (y<0||y>support.getChartHeight()) continue;
            g2.drawString(nm[i],cx-26,y+2);
            g2.drawLine(cx,y,cx+28,y);
        }
        int ys=(int)support.getYForValue(legP1);
        if (ys>=0&&ys<=support.getChartHeight()){
            g2.setColor(new Color(90,90,90));
            g2.setStroke(new BasicStroke(0.8f,BasicStroke.CAP_BUTT,BasicStroke.JOIN_MITER,10f,new float[]{1.5f,2.5f},0f));
            g2.drawLine(0,ys,support.getChartWidth(),ys);
        }
    }

    /** session overlay (agreement 2026-09-09, opt-in): dotted BLACK vertical at
     *  08:00 New York on each visible weekday + tiny tag on its own row.
     *  Locked line style; nothing drawn when OFF, off-screen, or on weekend.
     *  NOTE (fix 2026-09-09): iterates loaded days and lets getXForTime filter
     *  off-screen itself - screen candle indices are NEVER used as bar-array
     *  indices (the reference never does that; they diverge in replay/scroll). */
    private void drawNyOpenLines(Graphics2D g2,IIndicatorDrawingSupport support,Font oldFont){
        if (!showNyOpenLine||drawTimes==null||drawTimes.length==0||chartNowTime<=0) return;
        long firstT=-1;
        for (long tm:drawTimes){ if (tm>0){ firstT=tm; break; } }
        if (firstT<=0) return;
        int ch=support.getChartHeight();
        int cw=support.getChartWidth();
        g2.setFont(oldFont.deriveFont(Font.BOLD,9f));
        FontMetrics fmTag=g2.getFontMetrics();
        String tag="NY 8:00";
        int badgeW=fmTag.stringWidth(tag)+2*3;
        long open=nyOpenMillis(firstT,nyTZ);
        int guard=0;
        while (open<=chartNowTime&&guard++<1200){
            if (!isNyWeekend(open,nyTZ)){
                long snapped=snapToBar(drawTimes,open);
                if (snapped>0){
                    int x=support.getXForTime(snapped,false);
                    if (x>=0){
                        g2.setColor(CLOSURE_COLOR);
                        g2.setStroke(dashStroke(1,2));
                        g2.drawLine(x,0,x,ch-1);
                        int tx=(x+4+badgeW>cw)?x-badgeW-3:x+4;
                        drawBadge(g2,tag,tx,24,fmTag,3,4,Color.BLACK,Color.WHITE);
                    }
                }
            }
            open=nextNyOpen(open,nyTZ);
        }
    }

    /** element 10: panel - symbol, model, grid (always visible), YOUR live clock
     *  (frozen NY), Bias (NDM daily), Session (opt-in). */
    private void drawInfoPanel(Graphics2D g2,IIndicatorDrawingSupport support,Font oldFont){
        boolean bull=(currentBias!=0)?currentBias>0:structTrend>0;
        String tf=chartPeriodMs>0?tfShort(chartPeriodMs):"-";
        String htf=drawAnchor!=null?tfShort(PERIOD_INTERVALS[drawAnchor.periodIndex]):"-";
        SimpleDateFormat tfmt=new SimpleDateFormat("HH:mm:ss");
        tfmt.setTimeZone(DISPLAY_TZ);
        String timeS=tfmt.format(new Date(System.currentTimeMillis()));
        List<String> ls=new ArrayList<>(); List<Boolean> big=new ArrayList<>();
        ls.add(chartSymShort()+(bull?"\u2191!":"\u2193!")+" ("+tf+")"); big.add(Boolean.FALSE);
        ls.add(tf+"-"+htf+" Model"); big.add(Boolean.FALSE);
        ls.add("Grid: "+gridTag(gridMode,currentInstrumentName())); big.add(Boolean.FALSE);
        ls.add(timeS); big.add(Boolean.TRUE);
        if (showSMT){ ls.add("SMT: "+smtPanelLine); big.add(Boolean.FALSE); }
        String biasS=(currentBias!=0)?(currentBias>0?"Bullish":"Bearish"):"Neutral";
        ls.add("Bias: "+biasS); big.add(Boolean.FALSE);
        if (showSessionInPanel&&chartNowTime>0){
            ls.add("Session: "+sessionOf(chartNowTime,nyTZ)); big.add(Boolean.FALSE);
        }
        Font f11=new Font(Font.MONOSPACED,Font.PLAIN,11);
        Font f16=new Font(Font.MONOSPACED,Font.BOLD,16);
        int w=0,h=0;
        for (int i=0;i<ls.size();i++){
            FontMetrics fm=g2.getFontMetrics(big.get(i)?f16:f11);
            w=Math.max(w,fm.stringWidth(ls.get(i))); h+=fm.getHeight()+2;
        }
        int x=(support.getChartWidth()-w)/2;
        int yy=support.getChartHeight()-30-h;
        g2.setColor(new Color(45,45,45));
        for (int i=0;i<ls.size();i++){
            Font f=big.get(i)?f16:f11; g2.setFont(f);
            yy+=g2.getFontMetrics(f).getHeight()+2;
            g2.drawString(ls.get(i),x,yy-4);
        }
        g2.setFont(oldFont);
    }
    private String chartSymShort(){
        try { return shortSym(context.getFeedDescriptor().getInstrument().toString()); }
        catch (Exception e){ return "CHART"; }
    }
    private String tfShort(long interval){
        for (int i=0;i<PERIOD_COUNT;i++) if (PERIOD_INTERVALS[i]==interval) return SHORT_LABELS[i];
        long h=interval/3600000L,m=(interval%3600000L)/60000L;
        return (h>0)?h+"h":m+"m";
    }

    // ==================================================================
    @Override public IndicatorInfo getIndicatorInfo(){ return indicatorInfo; }
    @Override public InputParameterInfo getInputParameterInfo(int i){ return inputParameterInfos[i]; }
    @Override public com.dukascopy.api.indicators.OptInputParameterInfo getOptInputParameterInfo(int i){ return i<optInfos.length?optInfos[i]:null; }
    @Override public OutputParameterInfo getOutputParameterInfo(int i){ return outputParameterInfos[i]; }
    @Override public void setInputParameter(int i,Object o){ inputs[i]=(IBar[])o; }
    @Override public void setOutputParameter(int i,Object o){ outputs[i]=o; }
    @Override public int getLookback(){ return 0; }
    @Override public int getLookforward(){ return 0; }
}
