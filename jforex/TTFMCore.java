package com.dukascopy.indicators;

/*
 * ============================================================================
 *  TTFMCore  -  TTrades Fractal Model Core Engine  -  JForex / Dukascopy
 *  v3-FULL = v1 + v2 + Structure/PD-arrays + C2/C3 closures + Reversal sequence
 *            + session profiles + TF pairings + SMT + T-Spot (documented approximation)
 * ============================================================================
 *  Built on BOTH sources (agreed):
 *    [C] = official TTrades concept (PDF/articles)      -> THEORY
 *    [J] = HigherTFCandles.java (JForex, open source)   -> MECHANICS
 *    [B] = agreed by both
 *    [O] = our operational definition (rule published nowhere; we define it
 *          explicitly and test it - flagged for honesty)
 *  On conflict: official for theory, Java for hidden mechanics (case-by-case).
 *
 *  -- v1 (already shipped) --
 *   HTF aggregation + period bounds + TZ + 7H bucketing ....... [J]
 *   EQ = (high+low)/2 of last COMPLETED candle ................ [J]
 *   Respected-half (Discount/Premium) shading ................. [C] meaning / [J] draw
 *   Closure vertical boundaries ............................... [J]
 *   C1..C4 semantics + tagging ................................ [C]
 *   Liquidity Sweep ........................................... [J]
 *   CISD trigger = close beyond open of first wave bar ........ [B]
 *   CISD Stop = wave extreme (Protected Swing) ................ [B]
 *   CISD confirmation = body close on CLOSED bar (wick != confirmation) [C]
 *   Wave min-length 3/2/1 ..................................... [J]
 *   Session hours Asia/London/NY (NY TZ) ...................... [J]
 *
 *  -- v2 (this release) --
 *   Color state machine gray(valid)/orange(tighten)/red(failed)  [C] semantics / [O] thresholds
 *   IC-CISD early=strong / late=skip (position within HTF candle) [C] semantics / [O] 50% split
 *   EQ Inversion bias (respect half -> bias; sweep+close opposite -> inversion) [C]
 *   Projections: Fib on manipulation leg, leg size -> targets
 *       large -> -1 | average -> -2,-2.5 | expanding -> -4,-4.5   [C] multipliers / [O] category thresholds
 *
 *  -- deferred v3/v4/v5 --
 *   FVG/OB/Breaker + full C2/C3 + reversal sequence | cross-TF pairings +
 *   session-anchored 7H profiles + SMT | T-Spot (documented approximation only)
 *
 *  All pure logic runs on a platform-free Record (RB): portable & unit-testable.
 * ============================================================================
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
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import com.dukascopy.api.IBar;
import com.dukascopy.api.IHistory;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;
import com.dukascopy.api.indicators.IIndicator;
import com.dukascopy.api.indicators.IIndicatorContext;
import com.dukascopy.api.indicators.IDrawingIndicator;
import com.dukascopy.api.indicators.IIndicatorDrawingSupport;
import com.dukascopy.api.indicators.IndicatorInfo;
import com.dukascopy.api.indicators.IndicatorResult;
import com.dukascopy.api.indicators.InputParameterInfo;
import com.dukascopy.api.indicators.IntegerListDescription;
import com.dukascopy.api.indicators.IntegerRangeDescription;
import com.dukascopy.api.indicators.OptInputParameterInfo;
import com.dukascopy.api.indicators.OutputParameterInfo;

public class TTFMCore implements IIndicator, IDrawingIndicator {

    // ================== Constants ==================
    static final int MAX_CANDLES = 10;
    static final int MAX_LAYERS  = 3;
    static final int MAX_SIGNALS = 5;

    static final int    PERIOD_COUNT = 6;
    static final long[] PERIOD_INTERVALS = {
        15*60*1000L, 30*60*1000L, 60*60*1000L,
        4*60*60*1000L, 24*60*60*1000L, 7*60*60*1000L
    };
    static final String[] PERIOD_NAMES = {"15 Mins","30 Mins","1 Hour","4 Hours","Daily","7 Hours"};
    static final String[] SHORT_LABELS = {"15m","30m","1H","4H","D","7H"};

    private static final int[] POSITION_VALUES = {0,1,2};
    private static final String[] POSITION_NAMES = {"Left","Overlap","Right"};
    private static final int[] BOOLEAN_VALUES = {0,1};
    private static final String[] BOOLEAN_NAMES = {"No","Yes"};
    private static final int[] SESSION_VALUES = {0,1,2,3};
    private static final String[] SESSION_NAMES = {"All","London + NY","London Only","NY Only"};
    private static final int[] TZ_VALUES = {0,1,2,3,4};
    private static final String[] TZ_NAMES = {"GMT","EET","New York","Brussels","GMT+3 (TradingView)"};
    private static final int[] SESS_ANCHOR_VALUES = {0,1,2};
    private static final String[] SESS_ANCHOR_NAMES = {"Midnight of Timezone (default)","NY 17:00 Auto (TV FX/CFD)","NY 18:00 Auto (TV CME/Indices)"};
    private static final int[] TSPOT_DRAW_VALUES = {0,1,2};
    private static final String[] TSPOT_DRAW_NAMES = {"Chart (default)","HTF Candles","Both"};
    private static final int[] PROJ_ANCHOR_VALUES = {0,1};
    private static final String[] PROJ_ANCHOR_NAMES = {"Manipulation Leg (Java ref, default)","Manip-Candle EQ (official TV)"};
    private static final int[] PANEL_TIME_VALUES = {0,1,2};
    private static final String[] PANEL_TIME_NAMES = {"Live Clock Chart TZ (default)","Live Clock New York","Countdown HTF Close (official TV)"};
    private static final int[] BIAS_MODEL_VALUES = {0,1};
    private static final String[] BIAS_MODEL_NAMES = {"EQ Close vs Prev EQ (locked default)","Next-Day Model (official)"};
    private static final int[] NDM_LAYER_VALUES = {0,1,2,3};
    private static final String[] NDM_LAYER_NAMES = {"Follow Anchor (locked default)","Layer 1 (4H)","Layer 2 (Daily)","Layer 3"};
    private static final int[] SENS_VALUES = {0,1,2};
    private static final String[] SENS_NAMES = {"Low (min 3)","Medium (min 2)","High (min 1)"};
    private static final int[] SCHEME_VALUES = {0,1};
    private static final String[] SCHEME_NAMES = {"Green + Black","White + Black"};
    private static final int[] SHADE_VALUES = {0,1};
    private static final String[] SHADE_NAMES = {"No","EQ Only"};
    private static final int[] ANCHOR_VALUES = {1,2,3};
    private static final String[] ANCHOR_NAMES = {"Layer 1 (4H)","Layer 2 (D)","Layer 3"};
    private static final int[] EQ_LAYER_VALUES = {0,1,2,3};
    private static final String[] EQ_LAYER_NAMES = {"None","Layer 1","Layer 2","Layer 3"};
    private static final int[] LINE_STYLES = {0,1,2};
    private static final String[] LINE_STYLE_NAMES = {"Solid","Dashed","Dotted"};
    private static final int[] HIST_VALUES = {1,2,3,4,5,6};
    private static final String[] HIST_NAMES = {"1","2","3","4","5","6"};

    private static final Color[] CLOSURE_COLORS = {
        Color.BLUE, Color.RED, Color.GREEN, Color.GRAY, Color.BLACK,
        Color.WHITE, Color.ORANGE, Color.MAGENTA, Color.CYAN, Color.PINK,
        Color.YELLOW, Color.DARK_GRAY, Color.LIGHT_GRAY, new Color(128,0,128), new Color(0,128,128)
    };
    private static final String[] CLOSURE_COLOR_NAMES = {
        "Blue","Red","Green","Gray","Black","White","Orange","Magenta","Cyan","Pink",
        "Yellow","Dark Gray","Light Gray","Purple","Teal"
    };

    private static final Color WICK_COLOR   = Color.BLACK;
    private static final Color BORDER_COLOR = Color.BLACK;
    private Color BULLISH_BODY_COLOR = new Color(0,180,0);
    private Color BEARISH_BODY_COLOR = new Color(30,30,30);
    private static final Color CISD_BULL_COLOR = new Color(0,120,255);
    private static final Color CISD_BEAR_COLOR = new Color(255,60,60);
    private static final Color SWEEP_BULL_COLOR = new Color(0,150,255);
    private static final Color SWEEP_BEAR_COLOR = new Color(255,80,80);
    private static final Color EQ_SHADE_BULL = new Color(0,255,0,10);
    private static final Color EQ_SHADE_BEAR = new Color(255,0,0,10);
    private static final Color SEMANTIC_COLOR = new Color(255,215,0);
    // v2 colors
    private static final Color STATE_VALID   = new Color(150,150,150);   // gray
    private static final Color STATE_TIGHTEN = new Color(255,140,0);     // orange
    private static final Color STATE_FAILED  = new Color(220,40,40);     // red
    private static final Color TP_COLOR      = new Color(0,180,0);
    private static final Color BIAS_LONG     = new Color(0,200,80);
    private static final Color BIAS_SHORT    = new Color(255,80,80);

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
        double eq(){ return (high+low)/2.0; }   // [J] full-range mid (kept for reference)
        /** [C, confirmed by official chart] conditional candle EQ:
         *  normal wicks -> full-range mid; long LOWER wick -> mid of [L..min(O,C)]; long UPPER wick -> mid of [max(O,C)..H]. */
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
        boolean enabled=false; int periodIndex=0; int candlesToShow=4; int positionOption=2; int candleOffset=5;
        List<CandleData> historical = new ArrayList<>();
        double curO=Double.NaN,curH=Double.NaN,curL=Double.NaN,curC=Double.NaN;
        long curStart=0; boolean curActive=false;
        boolean lsActive=false; boolean lsBullish=true; double lsPrice=Double.NaN; long lsStart=0,lsEnd=0;
    }

    // ================== Signal (v1 + v2 fields) ==================
    static final class Signal {
        long waveStart, confirmTime; double entry, stop; boolean bullish; String session;
        // v2
        int state = 0;              // 0 gray valid, 1 orange tighten, 2 red failed
        boolean icEarly = true;     // [C] early=strong
        int legCat = 1;             // 0 large, 1 average, 2 expanding
        double t1 = Double.NaN, t2 = Double.NaN;   // projection targets
        int biasAtSignal = 0;       // +1 long, -1 short
    }

    // ================== Settings ==================
    private boolean showTimer = false;   // step-build: off until agreed
    private int candleBodyScale = 100;
    private int colorScheme = 0;
    private boolean showClosures = true;   // step-2 agreed: closures ON
    private int closureColorIndex = 4;   // user: closure verticals BLACK default
    private int closureStyleIndex = 2;   // user: closure verticals DOTTED default
    private int closureWidth = 1;
    private int eqLineColorIndex = 0;    // EQ equilibrium line keeps its own look (blue)
    private int eqLineStyleIndex = 1;
    private int shadeMode = 1;          // step-2 agreed: respected-half shade ON (EQ Only)
    private int maxHistoryShades = 3;
    private boolean showEQLine = true;  // agreed: EQ line visible
    private int anchorLayer = 1;        // agreed: UNIFIED reference layer (default 4H) drives EQ/bias/shade/T-Spot/C2C3
    private boolean semanticLabels = false;
    private boolean showCISD = true;    // step-3 agreed: CISD engine ON (independent master)
    private boolean addOns = true;        // user: Add-Ons master (targets+states) independent of CISD engine
    private int cisdSensitivity = 1;
    private boolean ignoreInsideBars = true;
    private int activeSessions = 1;
    private int chartTimezone = 4;
    private int htfAnchor = 0;   // 0=midnight of timezone (default), 1=NY 17:00 auto (TV FX/CFD), 2=NY 18:00 auto (TV CME/indices)
    private int layerSpacing = 3;
    private boolean saveJournal = false; // step-build: off until agreed
    // v2
    private boolean colorStates = true;  // step-3 agreed: gray/orange/red states ON
    private boolean skipLateIC = false;
    private boolean showBias = false;   // step-build: off until agreed
    private boolean showTargets = true;    // official video match: projection ladder ON
    // v3/v4/v5
    private boolean showFvg = true;     // agreed: FVG ON, sourced from Layer-1 candles
    private int fvgSourceLayer = 1;      // 0=Chart TF, 1=Layer1 (anchor) candles
    private boolean showOb = false;     // step-build: off until agreed
    private boolean showSweep = false;  // step-build: sweep line off until agreed
    private boolean showTspot = true;
    private boolean tspotBull = true;    // [official T-Spot setting] show bullish (BUY) zones
    private boolean tspotBear = true;    // [official T-Spot setting] show bearish (SELL) zones
    private int tspotDraw = 0;           // [official "Drawing Type"] 0=Chart, 1=HTF Candles, 2=Both
    private int biasModel = 0;             // [Bias] model: 0=EQ close vs prev EQ (locked), 1=Next-Day Model (official article)
    private int ndmLayer = 0;              // [Bias] NDM source layer: 0=follow anchor (locked), 1/2/3=explicit layer
    private int panelTimeMode = 0;         // [Panel] time line: 0=live clock chart TZ (user's time), 1=live clock NY, 2=countdown (official TV)
    private int projAnchor = 0;            // [Proj] ladder anchor: 0=manipulation leg (Java ref), 1=manip-candle EQ (official TV)
    private double eqAnchor=Double.NaN, eqLeg=Double.NaN;
    private boolean showInfoPanel = true;   // official video match: 5-line model panel ON
    private boolean smtEnabled = true;      // official video match: SMT(Auto) ON
    private boolean tspotModelOnly = true;  // official guide: T-Spot prints on model C2/C3 candles only
    private boolean showPrevEqOpen = true;   // official guide: Previous EQ + HTF Open lines on the CURRENT candle
    private boolean tspotCurrentAlways = true; // user directive: the watch-now zone on the CURRENT candle always prints
    private int boundStyleIndex = 2;           // user: boundary lines DOTTED by default
    private int boundColorIndex = 4;           // user: boundary lines BLACK by default (palette idx 4)
    private long chartNowTime = 0;           // last bar time (for growing current-candle drawings)
    private boolean showHtfBounds = true;   // official video match: HTF boundary verticals
    private long chartPeriodMs = 0;         // chart TF interval (official panel line 1-2)
    private LayerData drawAnchor = null;    // anchor layer reference for drawing
    private long c2LblTime=0; private double c2LblPrice=Double.NaN; private boolean c2LblBull=true;
    private long c4RawTime=0; private long c4LblTime=0; private double c4LblPrice=Double.NaN;
    private long legT1=0,legT2=0,legT3=0; private double legP1=Double.NaN,legP2=Double.NaN,legP3=Double.NaN;
    private String smtCracks=null;          // official crack list e.g. "ES..!, YM..!" 

    private final LayerData[] layers = new LayerData[MAX_LAYERS];
    {
        for (int i=0;i<MAX_LAYERS;i++) layers[i]=new LayerData();
        layers[0].enabled=true;  layers[0].periodIndex=3; layers[0].candlesToShow=6; layers[0].candleOffset=10; // 4H (agreed)
        layers[1].enabled=true;  layers[1].periodIndex=4; layers[1].candlesToShow=3;                            // D  (agreed)
        layers[2].enabled=false; layers[2].periodIndex=5; layers[2].candlesToShow=3;                            // 7H kept but OFF
    }

    // ================== runtime ==================
    private IIndicatorContext context;
    private IBar[][] inputs = new IBar[1][];
    private Object[] outputs;
    private IndicatorInfo indicatorInfo;
    private InputParameterInfo[] inputParameterInfos;
    private OptInputParameterInfo[] optInputParameterInfos;
    private OutputParameterInfo[] outputParameterInfos;
    private OptInputSetter[] optSetters;
    private Calendar chartCalendar;
    private TimeZone nyTZ = TimeZone.getTimeZone("America/New_York");
    private SimpleDateFormat nyFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private final List<Signal> signals = new ArrayList<>();
    private long pendingBullStart=-1, pendingBearStart=-1;
    private double pendingBullTrigger=Double.NaN, pendingBullStop=Double.NaN;
    private double pendingBearTrigger=Double.NaN, pendingBearStop=Double.NaN;
    private long lastChartPeriodMs = -1;
    private int currentBias = 0;      // v2 EQ bias of primary layer
    private boolean currentInversion = false;
    // v3/v4/v5 computed state
    private double chartAvgRange = 0;
    private final List<double[]> fvgZones = new ArrayList<>();   // {lo,hi,bull(1/0),time}
    private int obBullIdx=-1, obBearIdx=-1;
    private boolean obBullBroken=false, obBearBroken=false;
    private int structTrend=0, structEvent=0;
    private int c2Idx=-1, c3Idx=-1; private double c2Entry=Double.NaN;
    private int revBullMask=0, revBearMask=0;
    private String profileName=""; private long profileStartMs=0;
    private long entryPairInterval=-1, eqPairInterval=-1;
    private final List<double[]> tspotZones=new ArrayList<>();  // {lo,hi,start,end}
    private double[] tspotZoneD=null;
    private String smtLabel=null;

    private interface OptInputSetter { void set(Object v); }

    // ==================================================================
    //  PURE LOGIC CORE (platform-free, unit-tested)
    // ==================================================================
    static long periodStart(long timeMs, long intervalMs, TimeZone tz){ return periodStart(timeMs,intervalMs,tz,0); }
    /** anchorMode: 0 = midnight of tz (legacy default); 1 = NY 17:00 daily session start (TradingView FX/CFD
     *  convention, DST-aware); 2 = NY 18:00 daily session start (TradingView CME futures/indices, DST-aware). */
    static long periodStart(long timeMs, long intervalMs, TimeZone tz, int anchorMode){
        if (anchorMode==1||anchorMode==2){
            Calendar ny = Calendar.getInstance(TimeZone.getTimeZone("America/New_York"));
            ny.setTimeInMillis(timeMs);
            ny.set(Calendar.HOUR_OF_DAY,(anchorMode==1)?17:18);
            ny.set(Calendar.MINUTE,0);ny.set(Calendar.SECOND,0);ny.set(Calendar.MILLISECOND,0);
            long base = ny.getTimeInMillis();
            return base + Math.floorDiv(timeMs-base, intervalMs)*intervalMs;
        }
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

    static void aggregate(LayerData layer, RB bar, long intervalMs, TimeZone tz){ aggregate(layer,bar,intervalMs,tz,0); }
    static void aggregate(LayerData layer, RB bar, long intervalMs, TimeZone tz, int anchorMode){
        long ps = periodStart(bar.time,intervalMs,tz,anchorMode);
        if (!layer.curActive || ps!=layer.curStart){
            if (layer.curActive){
                CandleData done=new CandleData(layer.curO,layer.curH,layer.curL,layer.curC,layer.curStart,true);
                if (layer.historical.isEmpty()||layer.historical.get(layer.historical.size()-1).openTime!=layer.curStart)
                    layer.historical.add(done);
                if (layer.historical.size()>layer.candlesToShow) layer.historical.remove(0);
                if (layer.historical.size()>=2) refreshSweep(layer);
            }
            layer.curStart=ps; layer.curO=bar.o; layer.curH=bar.h; layer.curL=bar.l; layer.curC=bar.c; layer.curActive=true;
        } else {
            if (bar.h>layer.curH) layer.curH=bar.h;
            if (bar.l<layer.curL) layer.curL=bar.l;
            layer.curC=bar.c;
        }
    }
    static void refreshSweep(LayerData layer){
        if (layer.historical.size()<2){ layer.lsActive=false; return; }
        CandleData c1=layer.historical.get(layer.historical.size()-2);
        CandleData c2=layer.historical.get(layer.historical.size()-1);
        double[] s=detectSweep(c1,c2);
        if (s!=null){ layer.lsActive=true; layer.lsBullish=s[0]==1; layer.lsPrice=s[1]; layer.lsStart=c1.openTime; layer.lsEnd=c2.openTime; }
        else layer.lsActive=false;
    }

    // ---------- v2 pure helpers ----------

    /** [O] classify manipulation-leg size vs average HTF range: 0=large,1=average,2=expanding. */
    static int legCategory(double legSize, double avgRange){
        if (avgRange<=0) return 1;
        double ratio = legSize/avgRange;
        if (ratio>=2.0) return 2;      // expanding
        if (ratio>=1.2) return 0;      // large
        return 1;                      // average
    }
    /** [C] projection multipliers per category. */
    static double[] legMultipliers(int cat){
        if (cat==0) return new double[]{-1.0};
        if (cat==2) return new double[]{-4.0,-4.5};
        return new double[]{-2.0,-2.5};
    }
    /** [C] EQ bias side of a completed candle vs the PREVIOUS candle's EQ (the divider): close above -> +1 (long). */
    static int eqSide(CandleData c, double prevEq){ return c.close>=prevEq ? 1 : -1; }
    /** [C] EQ inversion vs PREVIOUS EQ: swept one half (wick beyond prev EQ) then CLOSED in the opposite half.
     *  A candle that stays entirely in one half = respect (no inversion). */
    static boolean eqInversion(CandleData c, double prevEq){
        boolean sweptLower = c.low  < prevEq;
        boolean sweptUpper = c.high > prevEq;
        return (sweptLower && c.close>prevEq) || (sweptUpper && c.close<prevEq);
    }
    /** [C/O] IC-CISD early if confirmation lands in first half of its HTF candle. */
    static boolean icEarly(long confirmTime, long htfStart, long htfPeriod){
        if (htfPeriod<=0) return true;
        double frac = (confirmTime-htfStart)/(double)htfPeriod;
        return frac<=0.5;
    }
    /** [C/O] signal state: 0 gray valid, 1 orange tighten, 2 red failed. */
    static int signalState(boolean bullish,double entry,double stop,RB[] bars,int fromIdx,boolean htfClosedAfter){
        double R = Math.abs(entry-stop);
        if (R<=0) return 0;
        boolean reached1R=false;
        for (int i=fromIdx;i<bars.length;i++){
            RB b=bars[i];
            if (bullish){
                if (b.c < stop) return 2;                       // closed beyond stop -> failed
                if (b.h - entry >= R) reached1R=true;
            } else {
                if (b.c > stop) return 2;
                if (entry - b.l >= R) reached1R=true;
            }
        }
        if (reached1R) return 0;                                 // working -> still valid
        if (htfClosedAfter) return 1;                            // HTF candle passed w/o 1R -> tighten
        return 0;
    }


    // ---------- v3: structure & PD arrays (pure) ----------
    /** [C] Fair Value Gap (3-bar imbalance). bull zone [high(i-2), low(i)]; bear zone [high(i), low(i-2)]. */
    static double[] detectFVG(RB[] bars,int i,boolean bullish){
        if (i<2||i>=bars.length) return null;
        if (bullish){ if (bars[i-2].h < bars[i].l) return new double[]{bars[i-2].h, bars[i].l}; }
        else        { if (bars[i-2].l > bars[i].h) return new double[]{bars[i].h, bars[i-2].l}; }
        return null;
    }
    /** [C] Order Block = last opposite-color candle before a displacement candle. index or -1. */
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
    /** [C] Breaker = an OB fully closed through later (failed OB flips role). */
    static boolean obBreached(RB[] bars,int obIdx,boolean bullish){
        if (obIdx<0||obIdx>=bars.length) return false;
        double lo=bars[obIdx].l, hi=bars[obIdx].h;
        for (int k=obIdx+1;k<bars.length;k++){
            if (bullish&&bars[k].c<lo) return true;
            if (!bullish&&bars[k].c>hi) return true;
        }
        return false;
    }
    /** [J/C] last two swing pivots -> {i1,p1,i2,p2} or null. */
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
    /** [C] structure: {trend +1/-1/0, event 0 none,1 BOS,2 CHoCH}. */
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
    /** [C, official indicator prints C2 on the closure itself] pure C2 closure =
     *  sweep of c1 + close back inside c1 range. No setup filters. */
    static boolean c2SweepClosure(CandleData c1,CandleData c2,boolean bullish){
        double[] sw=detectSweep(c1,c2);
        if (sw==null) return false;
        if (bullish?(sw[0]!=1):(sw[0]!=0)) return false;
        return c2.close>=c1.low&&c2.close<=c1.high;
    }
    /** [C] C2 closure = sweep + close back inside c1 + POI in respected half + structure aligned. */
    static boolean c2Closure(CandleData c1,CandleData c2,boolean bullish,boolean poiInHalf,int structTrend){
        if (!c2SweepClosure(c1,c2,bullish)) return false;
        if (!poiInHalf) return false;
        if (structTrend!=0&&structTrend!=(bullish?1:-1)) return false;
        return true;
    }
    /** [C] big-wick C2 -> entry at wick midpoint, else NaN. */
    static double c2WickMid(CandleData c2){
        double range=c2.high-c2.low; if (range<=0) return Double.NaN;
        double wick=range-Math.abs(c2.close-c2.open);
        return (wick>=0.5*range)?(c2.high+c2.low)/2.0:Double.NaN;
    }
    /** [C] C3 closure = NO sweep + close over/under C2 BODY. */
    static boolean c3Closure(CandleData c2,CandleData c3,boolean bullish){
        if (detectSweep(c2,c3)!=null) return false;
        double top=Math.max(c2.open,c2.close), bot=Math.min(c2.open,c2.close);
        return bullish?(c3.close>top):(c3.close<bot);
    }
    /** [C] reversal sequence bitmask: 1 turtleSoup,2 inversion,4 cisdOrOB,8 fvg,16 breaker. */
    static int reversalStages(RB[] bars,int i,boolean bullish,double avgRange,boolean cisdFired){
        int mask=0;
        if (i>=2){
            double[] sw=findLastTwoSwings(bars,!bullish,50);
            if (sw!=null){
                double lvl=sw[3];
                boolean swept=bullish?(bars[i].l<lvl&&bars[i].c>lvl):(bars[i].h>lvl&&bars[i].c<lvl);
                if (swept) mask|=1;
            }
        }
        if (i>=1){
            double prevEq=(bars[i-1].h+bars[i-1].l)/2.0;
            boolean inv=bullish?(bars[i].l<prevEq&&bars[i].c>prevEq):(bars[i].h>prevEq&&bars[i].c<prevEq);
            if (inv) mask|=2;
        }
        int ob=findOrderBlock(bars,i,bullish,avgRange);
        if (cisdFired||ob>=0) mask|=4;
        if (detectFVG(bars,i,bullish)!=null) mask|=8;
        for (int k=Math.max(2,i-30);k<i;k++){
            int obk=findOrderBlock(bars,k,bullish,avgRange);
            if (obk>=0&&obBreached(bars,obk,bullish)){ mask|=16; break; }
        }
        return mask;
    }

    // ---------- v4: profiles, pairings, SMT (pure) ----------
    /** [C] session-anchored 7H profiles (NOT equal buckets): Asia 18:00 / London 01:00 / NY 08:00 (NY TZ). */
    static String profileOf(long timeMs,TimeZone ny){
        Calendar cal=Calendar.getInstance(ny); cal.setTimeInMillis(timeMs);
        int h=cal.get(Calendar.HOUR_OF_DAY);
        if (h>=18||h<1) return "Asia";
        if (h>=1&&h<8)  return "London";
        return "NY";
    }
    static long profileStart(long timeMs,TimeZone ny){
        Calendar cal=Calendar.getInstance(ny); cal.setTimeInMillis(timeMs);
        int h=cal.get(Calendar.HOUR_OF_DAY);
        int anchor=(h>=18||h<1)?18:(h>=1&&h<8)?1:8;
        cal.set(Calendar.HOUR_OF_DAY,anchor);cal.set(Calendar.MINUTE,0);cal.set(Calendar.SECOND,0);cal.set(Calendar.MILLISECOND,0);
        long t=cal.getTimeInMillis();
        if (t>timeMs) t-=24*3600000L;
        return t;
    }
    private static final long S15=15000L,M1=60000L,M3=180000L,M5=300000L,M15I=900000L,M30I=1800000L,H1I=3600000L,H4I=14400000L,D1I=86400000L,W1I=604800000L,MN1I=2592000000L;
    /** [C] entry-TF pairing for a bias/HTF interval (15s-5m | 1m-15m | 3m-30m | 5m-1H | 15m-4H | 1H-1D | 4H-1W | 1D-1M). */
    static long entryPairFor(long htf){
        if (htf==M5) return S15;
        if (htf==M15I) return M1;
        if (htf==M30I) return M3;
        if (htf==H1I) return M5;
        if (htf==H4I) return M15I;
        if (htf==D1I) return H1I;
        if (htf==W1I) return H4I;
        if (htf==MN1I) return D1I;
        return -1;
    }
    /** [C] EQ pairing (D->1H | 4H->15m | 1H->5m | 15m->1m). */
    static long eqPairFor(long bias){
        if (bias==D1I) return H1I;
        if (bias==H4I) return M15I;
        if (bias==H1I) return M5;
        if (bias==M15I) return M1;
        return -1;
    }
    /** [J/C] pivot-based SMT divergence between two series. */
    static boolean smtDivergence(RB[] mine,RB[] other,boolean bullish,int lookback){
        if (mine==null||other==null) return false;
        if (bullish){
            double[] m=findLastTwoSwings(mine,false,lookback), o=findLastTwoSwings(other,false,lookback);
            return m!=null&&o!=null&&m[3]<m[1]&&o[3]>o[1];
        } else {
            double[] m=findLastTwoSwings(mine,true,lookback), o=findLastTwoSwings(other,true,lookback);
            return m!=null&&o!=null&&m[3]>m[1]&&o[3]<o[1];
        }
    }

    // ---------- v5: T-Spot documented approximation (pure) ----------
    /** [C, agreed] T-Spot zone = between conditional EQ of candle N and OPEN of candle N+1; null when degenerate. */
    static double[] tspotZone(double eq,double open){
        if (Math.abs(eq-open)<=1e-12) return null;
        return new double[]{Math.min(eq,open),Math.max(eq,open)};
    }
    static double[] tspotZone(CandleData cN,CandleData cN1){
        if (cN==null||cN1==null) return null;
        return tspotZone(tspotEQ(cN),cN1.open);
    }
    /** [C] respected half of a candle = the half (vs its conditional EQ) containing its close:
     *  close>=EQ -> upper [eq..high]; else lower [low..eq]. */
    static double[] respectedHalf(CandleData c){
        double eq=c.candleEQ();
        return (c.close>=eq)? new double[]{eq,c.high} : new double[]{c.low,eq};
    }
    /** [C, PO3] zone type by wick-formation logic (NOT discount/premium entry):
     *  1 = zone BELOW EQ  -> new candle opens below EQ, its UPPER wick (manipulation) forms up into the zone then rejects DOWN  -> supports SELL (green).
     *  0 = zone ABOVE EQ  -> new candle opens above EQ, its LOWER wick forms down into the zone then rejects UP               -> supports BUY  (red). */
    /** [C, official guide+video] T-Spot zones print on model closure candles only:
     *  generator candle is C2 (manipulation closure) or C3 (expansion closure). */
    static boolean modelZoneGate(List<CandleData> hist,int g,int structTrend){
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
    private boolean modelZoneGen(List<CandleData> hist,int g){
        if (!tspotModelOnly) return true;
        return modelZoneGate(hist,g,structTrend);
    }
    /** [C, official multi-frame pixel-verified] T-Spot zone EQ = FULL-RANGE wick-to-wick 50%
     *  of the anchor candle (TTrades fib rule: always wick-high to wick-low).
     *  The conditional candleEQ() remains for the EQ LINE drawn on single candles. */
    static double tspotEQ(CandleData c){ return c.eq(); }
    /** [C] C4 continuation closure = close beyond C3 extreme (official video: close over zone top). */
    static boolean c4Closure(CandleData c3,CandleData c4,boolean bull){
        return bull ? c4.close>c3.high : c4.close<c3.low;
    }
    /** [C] official projection ladder: R-multiples {1,2,2.5,4,4.5} from leg start, opposite manipulation. */
    static double[] projLadder(double p1,double leg,boolean bull){
        double[] m={1,2,2.5,4,4.5}; double[] out=new double[m.length];
        for (int i=0;i<m.length;i++) out[i]=bull? p1+m[i]*leg : p1-m[i]*leg;
        return out;
    }
    /** official short root symbols for panel + SMT(Auto) line. */
    static String shortSym(String sym){
        if (sym.contains("USA500")) return "ES";
        if (sym.contains("USA30"))  return "YM";
        if (sym.contains("USATECH"))return "NQ";
        if (sym.equals("EUR/USD"))  return "EUR";
        if (sym.equals("GBP/USD"))  return "GBP";
        if (sym.equals("XAU/EUR"))  return "XAE";
        if (sym.contains("XAU"))    return "XAU";
        int d=sym.indexOf('/'); return d>0? sym.substring(0,d) : sym;
    }
    static int tspotType(double eq,double open){ return open<eq?1:0; }
    /** [C] zone lifecycle: 0 fresh, 1 retested (touched), 2 invalidated (close beyond far bound). */
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

    // ==================================================================
    //  JForex lifecycle
    // ==================================================================
    @Override
    public void onStart(IIndicatorContext context){
        this.context=context;
        outputs=new Object[MAX_CANDLES*4];
        nyFormat.setTimeZone(nyTZ);

        inputParameterInfos = new InputParameterInfo[]{ new InputParameterInfo("Chart Bars", InputParameterInfo.Type.BAR) };

        List<OptInputParameterInfo> opt=new ArrayList<>();
        List<OptInputSetter> set=new ArrayList<>();
        for (int l=0;l<MAX_LAYERS;l++){
            final int li=l, ln=l+1;
            opt.add(new OptInputParameterInfo("[Layer "+ln+"] Enable",OptInputParameterInfo.Type.OTHER,new IntegerListDescription(layers[l].enabled?1:0,BOOLEAN_VALUES,BOOLEAN_NAMES)));
            set.add(v->layers[li].enabled=((Integer)v)==1);
            opt.add(new OptInputParameterInfo("[Layer "+ln+"] Timeframe",OptInputParameterInfo.Type.OTHER,new IntegerListDescription(layers[l].periodIndex,periodIndexes(),PERIOD_NAMES)));
            set.add(v->layers[li].periodIndex=(Integer)v);
            opt.add(new OptInputParameterInfo("[Layer "+ln+"] Candles",OptInputParameterInfo.Type.OTHER,new IntegerRangeDescription(layers[l].candlesToShow,1,MAX_CANDLES,1)));
            set.add(v->layers[li].candlesToShow=(Integer)v);
            opt.add(new OptInputParameterInfo("[Layer "+ln+"] Position",OptInputParameterInfo.Type.OTHER,new IntegerListDescription(layers[l].positionOption,POSITION_VALUES,POSITION_NAMES)));
            set.add(v->layers[li].positionOption=(Integer)v);
            opt.add(new OptInputParameterInfo("[Layer "+ln+"] Offset",OptInputParameterInfo.Type.OTHER,new IntegerRangeDescription(layers[l].candleOffset,0,50,1)));
            set.add(v->layers[li].candleOffset=(Integer)v);
        }
        opt.add(new OptInputParameterInfo("[Display] Show Timer",OptInputParameterInfo.Type.OTHER,new IntegerListDescription(0,BOOLEAN_VALUES,BOOLEAN_NAMES)));
        set.add(v->showTimer=((Integer)v)==1);
        opt.add(new OptInputParameterInfo("[Display] Candle Body Scale (%)",OptInputParameterInfo.Type.OTHER,new IntegerRangeDescription(100,50,200,10)));
        set.add(v->candleBodyScale=(Integer)v);
        opt.add(new OptInputParameterInfo("[Display] Color Scheme",OptInputParameterInfo.Type.OTHER,new IntegerListDescription(0,SCHEME_VALUES,SCHEME_NAMES)));
        set.add(v->{colorScheme=(Integer)v;applyScheme();});
        opt.add(new OptInputParameterInfo("[Display] Show Closures",OptInputParameterInfo.Type.OTHER,new IntegerListDescription(1,BOOLEAN_VALUES,BOOLEAN_NAMES)));
        set.add(v->showClosures=((Integer)v)==1);
        opt.add(new OptInputParameterInfo("[Display] Closure Color",OptInputParameterInfo.Type.OTHER,new IntegerListDescription(4,colorIndexes(),CLOSURE_COLOR_NAMES)));
        set.add(v->closureColorIndex=(Integer)v);
        opt.add(new OptInputParameterInfo("[Display] Closure Style",OptInputParameterInfo.Type.OTHER,new IntegerListDescription(2,LINE_STYLES,LINE_STYLE_NAMES)));
        set.add(v->closureStyleIndex=(Integer)v);
        opt.add(new OptInputParameterInfo("[Display] Closure Width",OptInputParameterInfo.Type.OTHER,new IntegerRangeDescription(1,1,5,1)));
        set.add(v->closureWidth=(Integer)v);
        opt.add(new OptInputParameterInfo("[EQ] Respected-Half Shade",OptInputParameterInfo.Type.OTHER,new IntegerListDescription(shadeMode,SHADE_VALUES,SHADE_NAMES)));
        set.add(v->shadeMode=(Integer)v);
        opt.add(new OptInputParameterInfo("[EQ] Max History Shades",OptInputParameterInfo.Type.OTHER,new IntegerListDescription(maxHistoryShades,HIST_VALUES,HIST_NAMES)));
        set.add(v->maxHistoryShades=(Integer)v);
        opt.add(new OptInputParameterInfo("[EQ] Show EQ Line",OptInputParameterInfo.Type.OTHER,new IntegerListDescription(1,BOOLEAN_VALUES,BOOLEAN_NAMES)));
        set.add(v->showEQLine=((Integer)v)==1);
        opt.add(new OptInputParameterInfo("[EQ] Line Color",OptInputParameterInfo.Type.OTHER,new IntegerListDescription(0,colorIndexes(),CLOSURE_COLOR_NAMES)));
        set.add(v->eqLineColorIndex=(Integer)v);
        opt.add(new OptInputParameterInfo("[EQ] Line Style",OptInputParameterInfo.Type.OTHER,new IntegerListDescription(1,LINE_STYLES,LINE_STYLE_NAMES)));
        set.add(v->eqLineStyleIndex=(Integer)v);
        opt.add(new OptInputParameterInfo("[Anchor] Reference Layer (EQ+Bias+TSpot)",OptInputParameterInfo.Type.OTHER,new IntegerListDescription(anchorLayer,ANCHOR_VALUES,ANCHOR_NAMES)));
        set.add(v->anchorLayer=(Integer)v);
        opt.add(new OptInputParameterInfo("[EQ] Show Bias Label (Inversion)",OptInputParameterInfo.Type.OTHER,new IntegerListDescription(0,BOOLEAN_VALUES,BOOLEAN_NAMES)));
        set.add(v->showBias=((Integer)v)==1);
        opt.add(new OptInputParameterInfo("[Concept] Semantic C1-C4 Labels",OptInputParameterInfo.Type.OTHER,new IntegerListDescription(0,BOOLEAN_VALUES,BOOLEAN_NAMES)));
        set.add(v->semanticLabels=((Integer)v)==1);
        opt.add(new OptInputParameterInfo("[CISD] Detection",OptInputParameterInfo.Type.OTHER,new IntegerListDescription(1,BOOLEAN_VALUES,BOOLEAN_NAMES)));
        set.add(v->showCISD=((Integer)v)==1);
        opt.add(new OptInputParameterInfo("[AddOns] Targets+States Master",OptInputParameterInfo.Type.OTHER,new IntegerListDescription(1,BOOLEAN_VALUES,BOOLEAN_NAMES)));
        set.add(v->addOns=((Integer)v)==1);
        opt.add(new OptInputParameterInfo("[CISD] Min Wave Length",OptInputParameterInfo.Type.OTHER,new IntegerListDescription(cisdSensitivity,SENS_VALUES,SENS_NAMES)));
        set.add(v->cisdSensitivity=(Integer)v);
        opt.add(new OptInputParameterInfo("[CISD] Ignore Inside Bars",OptInputParameterInfo.Type.OTHER,new IntegerListDescription(1,BOOLEAN_VALUES,BOOLEAN_NAMES)));
        set.add(v->ignoreInsideBars=((Integer)v)==1);
        opt.add(new OptInputParameterInfo("[IC] Skip Late IC-CISD",OptInputParameterInfo.Type.OTHER,new IntegerListDescription(0,BOOLEAN_VALUES,BOOLEAN_NAMES)));
        set.add(v->skipLateIC=((Integer)v)==1);
        opt.add(new OptInputParameterInfo("[State] Color States (gray/orange/red)",OptInputParameterInfo.Type.OTHER,new IntegerListDescription(1,BOOLEAN_VALUES,BOOLEAN_NAMES)));
        set.add(v->colorStates=((Integer)v)==1);
        opt.add(new OptInputParameterInfo("[Proj] Show Targets",OptInputParameterInfo.Type.OTHER,new IntegerListDescription(1,BOOLEAN_VALUES,BOOLEAN_NAMES)));
        set.add(v->showTargets=((Integer)v)==1);
        opt.add(new OptInputParameterInfo("[Struct] Show FVG Zones",OptInputParameterInfo.Type.OTHER,new IntegerListDescription(1,BOOLEAN_VALUES,BOOLEAN_NAMES)));
        set.add(v->showFvg=((Integer)v)==1);
        opt.add(new OptInputParameterInfo("[Struct] FVG Source",OptInputParameterInfo.Type.OTHER,new IntegerListDescription(1,new int[]{0,1},new String[]{"Chart TF","Layer 1 (Anchor)"})));
        set.add(v->fvgSourceLayer=(Integer)v);
        opt.add(new OptInputParameterInfo("[Struct] Show OB / Breaker",OptInputParameterInfo.Type.OTHER,new IntegerListDescription(0,BOOLEAN_VALUES,BOOLEAN_NAMES)));
        set.add(v->showOb=((Integer)v)==1);
        opt.add(new OptInputParameterInfo("[Struct] Show Liquidity Sweep",OptInputParameterInfo.Type.OTHER,new IntegerListDescription(0,BOOLEAN_VALUES,BOOLEAN_NAMES)));
        set.add(v->showSweep=((Integer)v)==1);
        opt.add(new OptInputParameterInfo("[TSpot] Show T-Spot Zones",OptInputParameterInfo.Type.OTHER,new IntegerListDescription(1,BOOLEAN_VALUES,BOOLEAN_NAMES)));
        set.add(v->showTspot=((Integer)v)==1);
        opt.add(new OptInputParameterInfo("[Struct] HTF Boundary Lines",OptInputParameterInfo.Type.OTHER,new IntegerListDescription(1,BOOLEAN_VALUES,BOOLEAN_NAMES)));
        set.add(v->showHtfBounds=((Integer)v)==1);
        opt.add(new OptInputParameterInfo("[TSpot] Model C2/C3 Zones Only",OptInputParameterInfo.Type.OTHER,new IntegerListDescription(1,BOOLEAN_VALUES,BOOLEAN_NAMES)));
        set.add(v->tspotModelOnly=((Integer)v)==1);
        opt.add(new OptInputParameterInfo("[HTF] Prev EQ + Open On Current",OptInputParameterInfo.Type.OTHER,new IntegerListDescription(1,BOOLEAN_VALUES,BOOLEAN_NAMES)));
        set.add(v->showPrevEqOpen=((Integer)v)==1);
        opt.add(new OptInputParameterInfo("[TSpot] Current-Candle Zone Always",OptInputParameterInfo.Type.OTHER,new IntegerListDescription(1,BOOLEAN_VALUES,BOOLEAN_NAMES)));
        set.add(v->tspotCurrentAlways=((Integer)v)==1);
        opt.add(new OptInputParameterInfo("[TSpot] Bounds Style",OptInputParameterInfo.Type.OTHER,new IntegerListDescription(2,LINE_STYLES,LINE_STYLE_NAMES)));
        set.add(v->boundStyleIndex=(Integer)v);
        opt.add(new OptInputParameterInfo("[TSpot] Bounds Color",OptInputParameterInfo.Type.OTHER,new IntegerListDescription(4,colorIndexes(),CLOSURE_COLOR_NAMES)));
        set.add(v->boundColorIndex=(Integer)v);
        opt.add(new OptInputParameterInfo("[TSpot] Show Bullish",OptInputParameterInfo.Type.OTHER,new IntegerListDescription(1,BOOLEAN_VALUES,BOOLEAN_NAMES)));
        set.add(v->tspotBull=((Integer)v)==1);
        opt.add(new OptInputParameterInfo("[TSpot] Show Bearish",OptInputParameterInfo.Type.OTHER,new IntegerListDescription(1,BOOLEAN_VALUES,BOOLEAN_NAMES)));
        set.add(v->tspotBear=((Integer)v)==1);
        opt.add(new OptInputParameterInfo("[TSpot] Drawing",OptInputParameterInfo.Type.OTHER,new IntegerListDescription(tspotDraw,TSPOT_DRAW_VALUES,TSPOT_DRAW_NAMES)));
        set.add(v->tspotDraw=(Integer)v);
        opt.add(new OptInputParameterInfo("[Proj] Ladder Anchor",OptInputParameterInfo.Type.OTHER,new IntegerListDescription(projAnchor,PROJ_ANCHOR_VALUES,PROJ_ANCHOR_NAMES)));
        set.add(v->projAnchor=(Integer)v);
        opt.add(new OptInputParameterInfo("[Panel] Time Line",OptInputParameterInfo.Type.OTHER,new IntegerListDescription(panelTimeMode,PANEL_TIME_VALUES,PANEL_TIME_NAMES)));
        set.add(v->panelTimeMode=(Integer)v);
        opt.add(new OptInputParameterInfo("[Bias] Model",OptInputParameterInfo.Type.OTHER,new IntegerListDescription(biasModel,BIAS_MODEL_VALUES,BIAS_MODEL_NAMES)));
        set.add(v->biasModel=(Integer)v);
        opt.add(new OptInputParameterInfo("[Bias] NDM Layer",OptInputParameterInfo.Type.OTHER,new IntegerListDescription(ndmLayer,NDM_LAYER_VALUES,NDM_LAYER_NAMES)));
        set.add(v->ndmLayer=(Integer)v);
        opt.add(new OptInputParameterInfo("[SMT] Detection (Pivot-based)",OptInputParameterInfo.Type.OTHER,new IntegerListDescription(1,BOOLEAN_VALUES,BOOLEAN_NAMES)));
        set.add(v->smtEnabled=((Integer)v)==1);
        opt.add(new OptInputParameterInfo("[Info] Show Concept Panel",OptInputParameterInfo.Type.OTHER,new IntegerListDescription(1,BOOLEAN_VALUES,BOOLEAN_NAMES)));
        set.add(v->showInfoPanel=((Integer)v)==1);
        opt.add(new OptInputParameterInfo("[Sessions] Active Sessions",OptInputParameterInfo.Type.OTHER,new IntegerListDescription(activeSessions,SESSION_VALUES,SESSION_NAMES)));
        set.add(v->activeSessions=(Integer)v);
        opt.add(new OptInputParameterInfo("[Signals] Save Journal CSV",OptInputParameterInfo.Type.OTHER,new IntegerListDescription(0,BOOLEAN_VALUES,BOOLEAN_NAMES)));
        set.add(v->saveJournal=((Integer)v)==1);
        opt.add(new OptInputParameterInfo("[Advanced] Chart Timezone",OptInputParameterInfo.Type.OTHER,new IntegerListDescription(chartTimezone,TZ_VALUES,TZ_NAMES)));
        set.add(v->{chartTimezone=(Integer)v;chartCalendar=Calendar.getInstance(getTimezone());});
        opt.add(new OptInputParameterInfo("[Advanced] HTF Session Anchor",OptInputParameterInfo.Type.OTHER,new IntegerListDescription(htfAnchor,SESS_ANCHOR_VALUES,SESS_ANCHOR_NAMES)));
        set.add(v->htfAnchor=(Integer)v);
        opt.add(new OptInputParameterInfo("[Advanced] Layer Spacing",OptInputParameterInfo.Type.OTHER,new IntegerRangeDescription(3,0,20,1)));
        set.add(v->layerSpacing=(Integer)v);

        optInputParameterInfos=opt.toArray(new OptInputParameterInfo[0]);
        optSetters=set.toArray(new OptInputSetter[0]);

        indicatorInfo=new IndicatorInfo("TTFMCore","TTFM Core Engine (v2)","Custom",
                true,false,false,1,optInputParameterInfos.length,MAX_CANDLES*4);

        outputParameterInfos=new OutputParameterInfo[MAX_CANDLES*4];
        String[] ohlc={"Open","High","Low","Close"};
        for (int c=0;c<MAX_CANDLES;c++) for (int vt=0;vt<4;vt++){
            int idx=c*4+vt;
            outputParameterInfos[idx]=new OutputParameterInfo("C"+(c+1)+" "+ohlc[vt],OutputParameterInfo.Type.DOUBLE,OutputParameterInfo.DrawingStyle.LINE);
            outputParameterInfos[idx].setDrawnByIndicator(true);
            outputParameterInfos[idx].setShowOutput(vt==0);
            if (vt==0){outputParameterInfos[idx].setColor(new Color(0,0,0,0));outputParameterInfos[idx].setOpacityAlpha(1.0f);}
        }
        chartCalendar=Calendar.getInstance(getTimezone());
        applyScheme();
    }

    private TimeZone getTimezone(){
        switch (chartTimezone){
            case 0: return TimeZone.getTimeZone("GMT");
            case 1: return TimeZone.getTimeZone("EET");
            case 2: return TimeZone.getTimeZone("America/New_York");
            case 3: return TimeZone.getTimeZone("Europe/Brussels");
            default: return TimeZone.getTimeZone("GMT+3:00");
        }
    }
    private void applyScheme(){
        if (colorScheme==0){BULLISH_BODY_COLOR=new Color(0,180,0);BEARISH_BODY_COLOR=new Color(30,30,30);}
        else {BULLISH_BODY_COLOR=Color.WHITE;BEARISH_BODY_COLOR=new Color(30,30,30);}
    }
    private static int[] periodIndexes(){int[] a=new int[PERIOD_COUNT];for(int i=0;i<PERIOD_COUNT;i++)a[i]=i;return a;}
    private static int[] colorIndexes(){int[] a=new int[CLOSURE_COLORS.length];for(int i=0;i<a.length;i++)a[i]=i;return a;}

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
        if (periodMs!=lastChartPeriodMs){
            lastChartPeriodMs=periodMs;
            signals.clear(); pendingBullStart=pendingBearStart=-1; currentBias=0; currentInversion=false;
            for (LayerData l:layers){ l.historical.clear(); l.curActive=false; l.lsActive=false; l.curO=l.curH=l.curL=l.curC=Double.NaN; l.curStart=0; }
        }
        TimeZone tz=getTimezone();

        for (LayerData layer:layers){
            if (!layer.enabled) continue;
            long interval=PERIOD_INTERVALS[layer.periodIndex];
            for (int i=startIndex;i<=endIndex&&i<bars.length;i++){
                if (bars[i].time<=0) continue;
                aggregate(layer,bars[i],interval,tz,htfAnchor);
            }
        }

        chartPeriodMs=periodMs;
        chartNowTime=bars[bars.length-1].time;
        LayerData primary=anchorData(periodMs);   // unified anchor (default 4H)
        drawAnchor=primary;
        updateEqBias((biasModel==1)?ndmSource(periodMs):primary);

        int detectionIndex=endIndex;
        if (detectionIndex==bars.length-1){
            long barEnd=bars[detectionIndex].time+periodMs;
            if (System.currentTimeMillis()<barEnd) detectionIndex=Math.max(0,detectionIndex-1);
        }
        if (showCISD) detectCisd(bars,detectionIndex,periodMs,primary);
        updateSignalStates(bars,primary);
        updateConceptState(bars,primary,periodMs);
        fetchSmt(periodMs);

        int length=endIndex-startIndex+1;
        for (int i=0;i<outputs.length;i++){
            double[] arr=(double[])outputs[i];
            if (arr==null||arr.length!=length) outputs[i]=new double[length];
        }
        for (int idx=startIndex,a=0;idx<=endIndex;idx++,a++){
            List<CandleData> disp=displayList(primary);
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

    private LayerData anchorData(long periodMs){
        int idx=anchorLayer-1;
        if (idx>=0&&idx<MAX_LAYERS&&layers[idx].enabled) return layers[idx];
        return primaryLayer(periodMs);
    }
    private LayerData primaryLayer(long periodMs){
        for (LayerData l:layers) if (l.enabled&&PERIOD_INTERVALS[l.periodIndex]>periodMs) return l;
        return layers[0];
    }
    private List<CandleData> displayList(LayerData layer){
        List<CandleData> d=new ArrayList<>(layer.historical);
        if (layer.curActive) d.add(new CandleData(layer.curO,layer.curH,layer.curL,layer.curC,layer.curStart,false));
        while (d.size()>layer.candlesToShow) d.remove(0);
        return d;
    }

    /** [C] update EQ bias + inversion flag from last completed primary candle. */
    /** [official Next-Day Model] bias from prev candle HIGH/LOW: close outside = continuation,
     *  sweep + close back inside = reversal (opposite side), no sweep & close inside = neutral.
     *  Returns {bias(-1/0/+1), reversalFlag}. */
    static int[] ndmBias(CandleData last, CandleData prev){
        if (last==null) return new int[]{0,0};
        if (prev==null) return new int[]{0,0};
        if (last.close>prev.high) return new int[]{1,0};   // bullish continuation
        if (last.close<prev.low)  return new int[]{-1,0};  // bearish continuation
        if (last.high>prev.high)  return new int[]{-1,1};  // swept high, closed inside -> bearish reversal
        if (last.low <prev.low)   return new int[]{1,1};   // swept low,  closed inside -> bullish reversal
        return new int[]{0,0};                             // inside, no sweep -> neutral
    }
    /** NDM bias source: explicit layer if enabled & above chart TF, else the unified anchor. */
    private LayerData ndmSource(long chartMs){
        if (ndmLayer>=1&&ndmLayer<=MAX_LAYERS){
            LayerData l=layers[ndmLayer-1];
            if (l.enabled&&PERIOD_INTERVALS[l.periodIndex]>chartMs) return l;
        }
        return anchorData(chartMs);
    }
    private void updateEqBias(LayerData primary){
        CandleData last=null, prev=null;
        for (int i=primary.historical.size()-1;i>=0;i--){
            CandleData c=primary.historical.get(i);
            if (!c.completed) continue;
            if (last==null) last=c; else { prev=c; break; }
        }
        if (last==null){ currentBias=0; currentInversion=false; return; }
        if (biasModel==1){
            int[] nd=ndmBias(last,prev);
            currentBias=nd[0]; currentInversion=nd[1]==1;
            return;
        }
        double prevEq = (prev!=null)? prev.candleEQ() : last.candleEQ();
        currentBias=eqSide(last,prevEq);
        currentInversion = (prev!=null) && eqInversion(last,prevEq);
    }

    private double avgHtfRange(LayerData primary){
        int n=Math.min(10,primary.historical.size());
        if (n==0) return 0;
        double s=0;
        for (int i=primary.historical.size()-n;i<primary.historical.size();i++)
            s+=primary.historical.get(i).high-primary.historical.get(i).low;
        return s/n;
    }

    // ================== CISD engine (v1 + v2 enrichment) ==================
    private void detectCisd(RB[] bars,int detectionIndex,long periodMs,LayerData primary){
        if (bars.length<2||detectionIndex<0||detectionIndex>=bars.length) return;
        int minRequired=(cisdSensitivity==0)?3:(cisdSensitivity==1)?2:1;
        int maxLookback=200;
        RB cur=bars[detectionIndex];

        int[] w=findWave(bars,detectionIndex-1,maxLookback,true,ignoreInsideBars);
        if (w[0]!=-1&&(w[1]-w[0]+1)>=minRequired){
            double firstOpen=bars[w[0]].o; double lowest=Double.MAX_VALUE;
            for (int k=w[0];k<=w[1];k++) lowest=Math.min(lowest,bars[k].l);
            if (!isDuplicate(bars[w[0]].time,firstOpen)){ pendingBullStart=bars[w[0]].time; pendingBullTrigger=firstOpen; pendingBullStop=lowest; }
        }
        w=findWave(bars,detectionIndex-1,maxLookback,false,ignoreInsideBars);
        if (w[0]!=-1&&(w[1]-w[0]+1)>=minRequired){
            double firstOpen=bars[w[0]].o; double highest=-Double.MAX_VALUE;
            for (int k=w[0];k<=w[1];k++) highest=Math.max(highest,bars[k].h);
            if (!isDuplicate(bars[w[0]].time,firstOpen)){ pendingBearStart=bars[w[0]].time; pendingBearTrigger=firstOpen; pendingBearStop=highest; }
        }

        if (pendingBullStart!=-1 && cur.c>pendingBullTrigger){
            if (sessionAllowed(sessionOf(pendingBullStart,nyTZ),activeSessions) && Math.abs(pendingBullTrigger-pendingBullStop)>1e-9)
                addSignal(pendingBullStart,cur.time,pendingBullTrigger,pendingBullStop,true,periodMs,primary);
            pendingBullStart=-1;
        }
        if (pendingBearStart!=-1 && cur.c<pendingBearTrigger){
            if (sessionAllowed(sessionOf(pendingBearStart,nyTZ),activeSessions) && Math.abs(pendingBearTrigger-pendingBearStop)>1e-9)
                addSignal(pendingBearStart,cur.time,pendingBearTrigger,pendingBearStop,false,periodMs,primary);
            pendingBearStart=-1;
        }
    }

    private boolean isDuplicate(long waveStart,double entry){
        for (Signal s:signals) if (s.waveStart==waveStart&&Math.abs(s.entry-entry)<1e-6) return true;
        return false;
    }

    private void addSignal(long waveStart,long confirmTime,double entry,double stop,boolean bullish,long periodMs,LayerData primary){
        Signal s=new Signal();
        s.waveStart=waveStart; s.confirmTime=confirmTime; s.entry=entry; s.stop=stop; s.bullish=bullish;
        s.session=sessionOf(confirmTime+periodMs,nyTZ);
        s.biasAtSignal=currentBias;

        // [C/O] IC-CISD early/late within its HTF candle
        long htfPeriod=PERIOD_INTERVALS[primary.periodIndex];
        long htfStart=periodStart(confirmTime,htfPeriod,getTimezone(),htfAnchor);
        s.icEarly=icEarly(confirmTime,htfStart,htfPeriod);
        if (skipLateIC && !s.icEarly) return;   // [C] late = skip

        // [C/O] Projections: manipulation leg = |entry-stop| = R; classify vs avg HTF range
        double leg=Math.abs(entry-stop);
        s.legCat=legCategory(leg,avgHtfRange(primary));
        double[] mult=legMultipliers(s.legCat);
        double dir=bullish?1:-1;
        s.t1=entry+dir*Math.abs(mult[0])*leg;
        s.t2=(mult.length>1)?entry+dir*Math.abs(mult[1])*leg:Double.NaN;

        signals.add(s);
        while (signals.size()>MAX_SIGNALS) signals.remove(0);
        if (saveJournal) writeJournal(s,periodMs);
    }

    /** [C/O] recompute gray/orange/red for each signal against latest bars. */
    private void updateSignalStates(RB[] bars,LayerData primary){
        for (Signal s:signals){
            int from=-1;
            for (int i=0;i<bars.length;i++) if (bars[i].time>s.confirmTime){ from=i; break; }
            boolean htfClosed=false;
            for (CandleData c:primary.historical) if (c.completed && c.openTime>s.confirmTime){ htfClosed=true; break; }
            s.state = (from<0)?0:signalState(s.bullish,s.entry,s.stop,bars,from,htfClosed);
        }
    }

    private void writeJournal(Signal s,long periodMs){
        try {
            String path=context.getFilesDir()+File.separator+"TTFMCore_Signals.csv";
            File f=new File(path); boolean exists=f.exists();
            String tf=tfShort(periodMs);
            String dir=s.bullish?"BUY":"SELL";
            String inst=context.getFeedDescriptor().getInstrument().toString().replace("/","_").replace(".","_");
            String id=inst+"_"+tf+"_"+dir+"_"+s.confirmTime;
            try (PrintWriter pw=new PrintWriter(new FileWriter(path,true))){
                if (!exists) pw.println("SignalID,SignalTimeNY,WaveStartNY,Instrument,TF,Direction,Entry,Stop,Session,ICEarly,LegCat,T1,T2,Bias");
                pw.printf(Locale.US,"%s,%s,%s,%s,%s,%s,%.6f,%.6f,%s,%s,%d,%.6f,%.6f,%d%n",
                        id,nyFormat.format(new Date(s.confirmTime+periodMs)),nyFormat.format(new Date(s.waveStart)),
                        inst,tf,dir,s.entry,s.stop,s.session,s.icEarly?"early":"late",s.legCat,s.t1,s.t2,s.biasAtSignal);
            }
        } catch (IOException e){ /* best-effort */ }
    }
    private String tfShort(long interval){
        for (int i=0;i<PERIOD_COUNT;i++) if (PERIOD_INTERVALS[i]==interval) return SHORT_LABELS[i];
        long h=interval/3600000L,m=(interval%3600000L)/60000L;
        return (h>0)?h+"h":m+"m";
    }

    // ==================================================================
    //  Drawing
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
        TimeZone tz=getTimezone();
        int spacing=Math.round(layerSpacing*(candleBodyScale/100.0f));

        int[] base=new int[MAX_LAYERS]; int right=0,left=0,overlap=0;
        for (int i=0;i<MAX_LAYERS;i++){ LayerData l=layers[i];
            if (!l.enabled||PERIOD_INTERVALS[l.periodIndex]<=chartInterval) continue;
            if (l.positionOption==2){int st=Math.max(right,l.candleOffset);base[i]=st;right=st+l.candlesToShow+spacing;} }
        for (int i=0;i<MAX_LAYERS;i++){ LayerData l=layers[i];
            if (!l.enabled||PERIOD_INTERVALS[l.periodIndex]<=chartInterval) continue;
            if (l.positionOption==0){int d=Math.max(left,l.candleOffset);base[i]=-(d+l.candlesToShow-1);left=d+l.candlesToShow+spacing;} }
        for (int i=0;i<MAX_LAYERS;i++){ LayerData l=layers[i];
            if (!l.enabled||PERIOD_INTERVALS[l.periodIndex]<=chartInterval) continue;
            if (l.positionOption==1){base[i]=-(overlap+l.candlesToShow-1);overlap+=l.candlesToShow+spacing;} }

        int firstLayerIndex=-1;
        for (int i=0;i<MAX_LAYERS;i++) if (layers[i].enabled&&PERIOD_INTERVALS[layers[i].periodIndex]>chartInterval){firstLayerIndex=i;break;}

        for (int i=0;i<MAX_LAYERS;i++){
            LayerData layer=layers[i];
            if (!layer.enabled||PERIOD_INTERVALS[layer.periodIndex]<=chartInterval) continue;
            List<CandleData> disp=displayList(layer);
            if (candleIdx>=disp.size()) continue;
            CandleData cd=disp.get(candleIdx);
            if (cd==null||!validOHLC(cd)) continue;
            long periodMs=PERIOD_INTERVALS[layer.periodIndex];

            int xCenter=(layer.positionOption==0)
                ? (int)(support.getMiddleOfCandle(first)+slot*(base[i]+candleIdx))
                : (int)(support.getMiddleOfCandle(last)+slot*(base[i]+candleIdx));

            float maxWidth=Math.max(1,slot-2);
            int bodyWidth=(int)Math.max(1,Math.min(cW*(candleBodyScale/100.0f),maxWidth));
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

            if (showEQLine&&i==anchorLayer-1){
                CandleData comp=null;
                for (int k=disp.size()-1;k>=0;k--) if (disp.get(k).completed){comp=disp.get(k);break;}
                if (comp!=null){
                    int yEQ=(int)support.getYForValue(comp.candleEQ());
                    int x1=support.getXForTime(comp.openTime,false);
                    long endT=(layer.curActive&&layer.curStart>comp.openTime)?layer.curStart+periodMs:comp.openTime+periodMs;
                    int x2=support.getXForTime(endT,false);
                    if (x1>=0&&x2>=0&&yEQ>=0&&yEQ<support.getChartHeight()){
                        g2.setColor(CLOSURE_COLORS[eqLineColorIndex]);
                        g2.setStroke(dashStroke(closureWidth,eqLineStyleIndex));
                        g2.drawLine(x1,yEQ,x2,yEQ);
                    }
                }
            }

            if (candleIdx==0){
                double hh=-Double.MAX_VALUE;int hi=0;
                for (int k=0;k<disp.size();k++) if (disp.get(k)!=null&&disp.get(k).high>hh){hh=disp.get(k).high;hi=k;}
                if (hh!=-Double.MAX_VALUE){
                    int lx=(int)(support.getMiddleOfCandle(layer.positionOption==0?first:last)+slot*(base[i]+hi));
                    int ly=(int)support.getYForValue(disp.get(hi).high)-4;
                    String lbl=SHORT_LABELS[layer.periodIndex];
                    g2.setFont(oldFont.deriveFont(Font.BOLD,9f));
                    g2.setColor(new Color(100,100,100));
                    int a9=g2.getFontMetrics().getAscent();
                    g2.drawString(lbl,lx-g2.getFontMetrics().stringWidth(lbl)/2,ly);
                    if (showTimer){
                        long remain=layer.curStart+periodMs-System.currentTimeMillis();
                        if (remain>0){
                            String t=String.format("%02d:%02d:%02d",remain/3600000,(remain%3600000)/60000,(remain%60000)/1000);
                            g2.setFont(oldFont.deriveFont(Font.BOLD,10f));
                            FontMetrics fm=g2.getFontMetrics();int tw=fm.stringWidth(t);
                            int ty=ly-a9-8;   // clear vertical gap: countdown sits fully ABOVE the TF label
                            g2.setColor(bull?new Color(0,200,0,100):new Color(200,0,0,100));
                            g2.fillRect(lx-tw/2-3,ty-fm.getAscent()-3,tw+6,fm.getAscent()+6);
                            g2.setColor(Color.WHITE);g2.drawString(t,lx-tw/2,ty);
                        }
                    }
                }
            }

            if (showSweep&&i==anchorLayer-1&&layer.lsActive&&!Double.isNaN(layer.lsPrice)){
                int x1=support.getXForTime(layer.lsStart,false);
                int x2=support.getXForTime(layer.lsEnd+periodMs,false);
                if (x1>=0&&x2>=0&&x2>x1){
                    int y=(int)support.getYForValue(layer.lsPrice);
                    Color sc=layer.lsBullish?SWEEP_BULL_COLOR:SWEEP_BEAR_COLOR;
                    g2.setColor(sc);g2.setStroke(new BasicStroke(1.0f,BasicStroke.CAP_BUTT,BasicStroke.JOIN_MITER,10f,new float[]{4f,3f},0f));
                    g2.drawLine(x1,y,x2,y);
                    String t="L.S-"+SHORT_LABELS[layer.periodIndex];
                    g2.setFont(oldFont.deriveFont(Font.BOLD,9f));
                    FontMetrics fm=g2.getFontMetrics();
                    g2.setColor(new Color(255,255,200,180));
                    g2.fillRect((x1+x2)/2-fm.stringWidth(t)/2-2,y-4-fm.getAscent(),fm.stringWidth(t)+4,fm.getHeight());
                    g2.setColor(sc);g2.drawString(t,(x1+x2)/2-fm.stringWidth(t)/2,y-4);
                }
            }

            if (showClosures&&i==anchorLayer-1){
                int ch=support.getChartHeight();
                int xOpen=support.getXForTime(cd.openTime,false);
                if (xOpen>=0){
                    // C-column matched to chart timing: band spans this candle's real time range on the chart axis
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
                    g2.setColor(CLOSURE_COLORS[closureColorIndex]);g2.setStroke(dashStroke(closureWidth,closureStyleIndex));
                    g2.drawLine(xOpen,0,xOpen,ch-1);
                    SimpleDateFormat lf=new SimpleDateFormat("HH:mm");lf.setTimeZone(support.getJFTimeZone().getTimeZone());
                    String t="C"+(candleIdx+1)+" ("+lf.format(new Date(cd.openTime))+"-"+lf.format(new Date(cd.openTime+periodMs))+")";
                    g2.setFont(oldFont.deriveFont(Font.BOLD,9f));
                    int tw=g2.getFontMetrics().stringWidth(t);
                    int tx=(xOpen+4+tw>support.getChartWidth())?xOpen-tw-3:xOpen+4;
                    g2.drawString(t,tx,12);
                }
            }

            if (shadeMode==1&&i==anchorLayer-1){
                int total=Math.min(maxHistoryShades,disp.size()-1);
                for (int k=disp.size()-1;k>=1&&(disp.size()-1-k)<total;k--){
                    CandleData curC=disp.get(k),prev=disp.get(k-1);
                    if (curC==null||!curC.completed||prev==null||!prev.completed) continue;
                    int x1=support.getXForTime(curC.openTime,false),x2=support.getXForTime(curC.openTime+periodMs,false);
                    if (x1<0||x2<0) continue;
                    double[] rh=respectedHalf(prev);
                    int yT=(int)support.getYForValue(rh[1]);
                    int yB=(int)support.getYForValue(rh[0]);
                    if (yB-yT>0){
                        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,0.3f));
                        g2.setColor((prev.close>=prev.candleEQ())?EQ_SHADE_BULL:EQ_SHADE_BEAR);
                        g2.fillRect(x1,yT,x2-x1,yB-yT);
                        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,1.0f));
                    }
                }
            }

            if (semanticLabels&&i==firstLayerIndex){
                int manip=-1;
                for (int k=1;k<disp.size();k++)
                    if (disp.get(k).completed&&disp.get(k-1).completed&&detectSweep(disp.get(k-1),disp.get(k))!=null){manip=k;break;}
                if (manip>=0){
                    String[] roles={"C1 Accumulation","C2 Manipulation","C3 Distribution","C4 Expansion"};
                    for (int r=0;r<4;r++){
                        int idx=manip-1+r;
                        if (idx<0||idx>=disp.size()||!disp.get(idx).completed) continue;
                        int cx=(int)(support.getMiddleOfCandle(layer.positionOption==0?first:last)+slot*(base[i]+idx));
                        int cy=(int)support.getYForValue(disp.get(idx).low)+12;
                        g2.setFont(oldFont.deriveFont(Font.BOLD,8f));
                        g2.setColor(SEMANTIC_COLOR);
                        g2.drawString(roles[r],cx-20,cy);
                    }
                }
            }
        }

        if (outputIdx==0){
            if (showHtfBounds) drawHtfBounds(g2,support);
            if (showPrevEqOpen) drawPrevEqOpen(g2,support);
            drawCisdLines(g2,support,slot,oldFont);
            if (showBias) drawBias(g2,support,oldFont);
            if (showFvg) drawFvgZones(g2,support,slot,base,first,last,cW);
            if (showOb) drawObZones(g2,support);
            if (showTspot&&tspotDraw!=1) drawTspot(g2,support);
            if (showTspot&&tspotDraw!=0) drawTspotHtf(g2,support,slot,base,first,last,cW);
            if (showClosures) drawModelLabels(g2,support);
            if (showTargets) drawProjLadder(g2,support);
            if (showInfoPanel) drawInfoPanel(g2,support,oldFont);
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

    private void drawCisdLines(Graphics2D g2,IIndicatorDrawingSupport support,float slot,Font oldFont){
        if (!showCISD||signals.isEmpty()) return;
        String tf=tfShort(context.getFeedDescriptor().getPeriod().getInterval());
        for (Signal s:signals){
            int x1=support.getXForTime(s.waveStart,false);
            int x2=support.getXForTime(s.confirmTime,false);
            if (x1<0||x2<0) continue;
            int y=(int)support.getYForValue(s.entry);
            if (y<0||y>=support.getChartHeight()) continue;
            int xEnd=(int)(x2+3*slot); if (xEnd>support.getChartWidth()) xEnd=support.getChartWidth();

            Color dirColor=s.bullish?CISD_BULL_COLOR:CISD_BEAR_COLOR;
            boolean stOn=addOns&&colorStates;
            Color stateColor=(s.state==2)?STATE_FAILED:(s.state==1)?STATE_TIGHTEN:STATE_VALID;
            Color lineColor=stOn?stateColor:dirColor;

            g2.setColor(lineColor);
            g2.setStroke(new BasicStroke((stOn&&s.state==2)?1.2f:2.0f,BasicStroke.CAP_BUTT,BasicStroke.JOIN_MITER,10f,(stOn&&s.state==2)?new float[]{4f,4f}:null,0f));
            g2.drawLine(x1,y,xEnd,y);

            String lbl=(s.bullish?"+Cisd":"-Cisd")+" ("+tf+")";
            g2.setFont(oldFont.deriveFont(Font.BOLD,10f));
            FontMetrics fm=g2.getFontMetrics();
            int tx=xEnd+4; if (tx+fm.stringWidth(lbl)>support.getChartWidth()) tx=support.getChartWidth()-fm.stringWidth(lbl)-10;
            g2.setColor(dirColor); g2.drawString(lbl,tx,y-4);

            // v2 state tag
            if (stOn){
                String st=(s.state==2)?"FAILED":(s.state==1)?"TIGHTEN":"VALID";
                String ic=s.icEarly?"":"  [IC-late]";
                g2.setFont(oldFont.deriveFont(Font.BOLD,8f));
                g2.setColor(stateColor);
                g2.drawString(st+ic,tx,y+10);
            }

            // stop (protected swing)
            int ys=(int)support.getYForValue(s.stop);
            if (ys>=0&&ys<support.getChartHeight()){
                g2.setColor(lineColor);
                g2.setStroke(new BasicStroke(1.2f,BasicStroke.CAP_BUTT,BasicStroke.JOIN_MITER,10f,new float[]{2f,3f},0f));
                g2.drawLine(x1,ys,xEnd,ys);
            }

            // v2 projection targets
            if (addOns&&showTargets){
                drawTarget(g2,support,s.t1,"T1",x1,xEnd);
                if (!Double.isNaN(s.t2)) drawTarget(g2,support,s.t2,"T2",x1,xEnd);
            }
        }
    }
    private void drawTarget(Graphics2D g2,IIndicatorDrawingSupport support,double price,String label,int x1,int x2){
        if (Double.isNaN(price)) return;
        int y=(int)support.getYForValue(price);
        if (y<0||y>=support.getChartHeight()) return;
        g2.setColor(TP_COLOR);
        g2.setStroke(new BasicStroke(1.0f,BasicStroke.CAP_BUTT,BasicStroke.JOIN_MITER,10f,new float[]{6f,3f},0f));
        g2.drawLine(x1,y,x2,y);
        g2.setFont(g2.getFont().deriveFont(Font.BOLD,8f));
        g2.drawString(label,x2+3,y-2);
    }

    /** [C] EQ bias + inversion label (top-right). */
    private void drawBias(Graphics2D g2,IIndicatorDrawingSupport support,Font oldFont){
        String txt; Color bc;
        if (currentBias==0){ txt="BIAS: NEUTRAL"; bc=new Color(160,160,160); }
        else {
            txt=(currentBias>0?"BIAS: LONG":"BIAS: SHORT")+(currentInversion?((biasModel==1)?"  (SWEEP-REV)":"  (EQ-INV)"):"");
            bc=currentBias>0?BIAS_LONG:BIAS_SHORT;
        }
        g2.setFont(oldFont.deriveFont(Font.BOLD,11f));
        FontMetrics fm=g2.getFontMetrics();
        int w=fm.stringWidth(txt);
        int x=support.getChartWidth()-w-12, y=24;
        g2.setColor(new Color(20,20,30,200));
        g2.fillRect(x-6,y-fm.getAscent()-4,w+12,fm.getHeight()+8);
        g2.setColor(bc);
        g2.drawString(txt,x,y);
    }


    // ==================================================================
    //  v3/v4/v5 : compute state + drawing
    // ==================================================================
    private static final String[] SMT_INSTRUMENTS = {
        "USATECH.IDX/USD","USA500.IDX/USD","USA30.IDX/USD","EUR/USD","GBP/USD","XAU/USD","XAU/EUR"
    };

    private void updateConceptState(RB[] bars,LayerData primary,long periodMs){
        drawAnchor=primary;
        int n=Math.min(20,bars.length); double ar=0;
        for (int i=bars.length-n;i<bars.length;i++) ar+=bars[i].h-bars[i].l;
        chartAvgRange=(n>0)?ar/n:0;

        fvgZones.clear();
        if (fvgSourceLayer==1 && drawAnchor!=null){
            List<CandleData> h=drawAnchor.historical;
            RB[] hb=new RB[h.size()];
            for (int i=0;i<h.size();i++){
                CandleData c=h.get(i);
                hb[i]=new RB(c.openTime,c.open,c.high,c.low,c.close,0);
            }
            for (int i=Math.max(2,hb.length-40); i<hb.length && fvgZones.size()<6; i++){
                double[] zb=detectFVG(hb,i,true);  if (zb!=null) fvgZones.add(new double[]{zb[0],zb[1],1,hb[i].time});
                double[] zs=detectFVG(hb,i,false); if (zs!=null) fvgZones.add(new double[]{zs[0],zs[1],0,hb[i].time});
            }
        } else {
            for (int i=Math.max(2,bars.length-40); i<bars.length && fvgZones.size()<6; i++){
                double[] zb=detectFVG(bars,i,true);  if (zb!=null) fvgZones.add(new double[]{zb[0],zb[1],1,bars[i].time});
                double[] zs=detectFVG(bars,i,false); if (zs!=null) fvgZones.add(new double[]{zs[0],zs[1],0,bars[i].time});
            }
        }
        obBullIdx=findOrderBlock(bars,bars.length-1,true,chartAvgRange);
        obBearIdx=findOrderBlock(bars,bars.length-1,false,chartAvgRange);
        obBullBroken=obBreached(bars,obBullIdx,true);
        obBearBroken=obBreached(bars,obBearIdx,false);

        int[] st=structureState(bars,60); structTrend=st[0]; structEvent=st[1];

        c2Idx=-1;c3Idx=-1;c2Entry=Double.NaN;
        c2LblTime=0;c2LblPrice=Double.NaN;c4RawTime=0;c4LblTime=0;c4LblPrice=Double.NaN;
        legT1=legT2=legT3=0;legP1=Double.NaN;legP2=Double.NaN;legP3=Double.NaN;eqAnchor=Double.NaN;eqLeg=Double.NaN;
        List<CandleData> hist=primary.historical;
        for (int k=hist.size()-1;k>=1;k--){
            CandleData c2=hist.get(k), c1=hist.get(k-1);
            boolean bull=c2.close>=c1.candleEQ();
            if (c2Closure(c1,c2,bull,poiInRespectedHalf(c1,bull),structTrend)){
                c2Idx=k; c2Entry=c2WickMid(c2);
                c2LblTime=c2.openTime; c2LblBull=bull; c2LblPrice=bull?c2.low:c2.high;
                legT1=c1.openTime; legP1=bull?c1.high:c1.low;
                legT2=c2.openTime; legP2=bull?c2.low:c2.high;
                eqAnchor=c2.candleEQ(); eqLeg=bull?(c2.high-eqAnchor):(eqAnchor-c2.low);
                legT3=legT2; legP3=legP2;
                if (k+1<hist.size()&&c3Closure(c2,hist.get(k+1),bull)){
                    c3Idx=k+1;
                    CandleData c3c=hist.get(c3Idx);
                    legT3=c3c.openTime; legP3=bull?c3c.low:c3c.high;
                    for (int q=c3Idx+1;q<hist.size();q++){
                        if (c4Closure(c3c,hist.get(q),bull)){
                            c4RawTime=hist.get(q).openTime;
                            c4LblTime=c4RawTime; c4LblPrice=bull?hist.get(q).low:hist.get(q).high;
                            break;
                        }
                    }
                }
                break;
            }
        }

        revBullMask=reversalStages(bars,bars.length-1,true,chartAvgRange,lastSignalBull());
        revBearMask=reversalStages(bars,bars.length-1,false,chartAvgRange,lastSignalBear());

        long now=bars[bars.length-1].time;
        profileName=profileOf(now,nyTZ); profileStartMs=profileStart(now,nyTZ);
        entryPairInterval=entryPairFor(periodMs); eqPairInterval=eqPairFor(periodMs);

        tspotZones.clear(); tspotZoneD=null;
        long pMs=PERIOD_INTERVALS[primary.periodIndex];
        for (int k=1;k<hist.size();k++){
            CandleData cN=hist.get(k-1), cN1=hist.get(k);
            if (!cN.completed) continue;
            if (!modelZoneGen(hist,k-1)) continue;   // official: model C2/C3 candles only
            addTspotZone(tspotEQ(cN),cN1.open,cN1.openTime,cN1.openTime+pMs,bars,false);
        }
        if (primary.curActive && hist.size()>=1){
            CandleData cN=hist.get(hist.size()-1);
            if (cN.completed && (tspotCurrentAlways || modelZoneGen(hist,hist.size()-1))){
                long nowT=bars[bars.length-1].time;   // official: zone GROWS with the forming HTF candle
                long end=Math.max(primary.curStart+1,Math.min(nowT,primary.curStart+pMs));
                addTspotZone(tspotEQ(cN),primary.curO,primary.curStart,end,bars,true);
            }
        }
        if (!tspotZones.isEmpty()){
            double[] lz=tspotZones.get(tspotZones.size()-1); tspotZoneD=new double[]{lz[0],lz[1]};
        }
        if (c4RawTime>0){   // official: C4 label anchors to the T-Spot zone corner containing the C4 candle
            for (double[] z:tspotZones){
                if (c4RawTime>=(long)z[2]&&c4RawTime<(long)z[3]){ c4LblTime=(long)z[2]; c4LblPrice=z[0]; break; }
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
    private boolean lastSignalBull(){ for (int i=signals.size()-1;i>=0;i--) if (signals.get(i).bullish) return true; return false; }
    private boolean lastSignalBear(){ for (int i=signals.size()-1;i>=0;i--) if (!signals.get(i).bullish) return true; return false; }

    // ---- SMT (cross-instrument via history) ----
    private void fetchSmt(long periodMs){
        smtLabel=null; smtCracks=null;
        if (!smtEnabled||context==null) return;
        try {
            String me=context.getFeedDescriptor().getInstrument().toString();
            IHistory hist=context.getHistory();
            Period p=context.getFeedDescriptor().getPeriod();
            long now=System.currentTimeMillis(), start=now-120*periodMs;
            RB[] mine=fetchRB(hist,resolveInstrument(me),p,start,now);
            if (mine==null) return;
            List<String> cracks=new ArrayList<>();
            for (String other:SMT_INSTRUMENTS){
                if (other.equals(me)||!correlated(me,other)) continue;
                RB[] ob=fetchRB(hist,resolveInstrument(other),p,start,now);
                if (ob==null) continue;
                if (smtDivergence(mine,ob,true,50)) cracks.add(shortSym(other)+"\u2191!");
                else if (smtDivergence(mine,ob,false,50)) cracks.add(shortSym(other)+"\u2193!");
            }
            smtCracks=cracks.isEmpty()?null:String.join(", ",cracks);
            smtLabel=smtCracks!=null?("SMT(Auto): "+smtCracks):null;
        } catch (Exception e){ /* best-effort */ }
    }
    private RB[] fetchRB(IHistory h,Instrument inst,Period p,long from,long to){
        if (inst==null||h==null) return null;
        try {
            List<IBar> l=h.getBars(inst,p,OfferSide.BID,from,to);
            if (l==null||l.size()<20) return null;
            RB[] a=new RB[l.size()];
            for (int i=0;i<l.size();i++) a[i]=toRB(l.get(i));
            return a;
        } catch (Exception e){ return null; }
    }
    private Instrument resolveInstrument(String sym){
        try { return Instrument.valueOf(sym.replace("/","_").replace(".","_")); }
        catch (Exception e){
            try { return Instrument.valueOf(sym.replace("/","").replace(".","")); }
            catch (Exception e2){ return null; }
        }
    }
    private boolean correlated(String a,String b){
        if (a.contains("XAU")&&b.contains("XAU")) return true;
        boolean fa=a.equals("EUR/USD")||a.equals("GBP/USD"), fb=b.equals("EUR/USD")||b.equals("GBP/USD");
        if (fa&&fb) return true;
        return a.contains(".IDX")&&b.contains(".IDX");
    }

    // ---- drawing: geometric zones ----
    /** FVG zones painted ONLY on the anchor-layer HTF candle columns (no extension to the chart).
     *  Each zone spans the columns of its 3-candle pattern (clamped to the visible overlay range). */
    private void drawFvgZones(Graphics2D g2,IIndicatorDrawingSupport support,float slot,int[] base,int first,int last,float cW){
        if (drawAnchor==null||!drawAnchor.enabled) return;
        int ai=anchorLayer-1;
        if (ai<0||ai>=MAX_LAYERS) return;
        long chartInterval=context.getFeedDescriptor().getPeriod().getInterval();
        if (PERIOD_INTERVALS[drawAnchor.periodIndex]<=chartInterval) return;   // anchor has no overlay columns
        List<CandleData> disp=displayList(drawAnchor);
        if (disp==null||disp.isEmpty()) return;
        long periodMs=PERIOD_INTERVALS[drawAnchor.periodIndex];
        int bodyWidth=(int)Math.max(1,Math.min(cW*(candleBodyScale/100.0f),Math.max(1,slot-2)));
        int halfBody=Math.max(1,bodyWidth/2);
        int side=(drawAnchor.positionOption==0)?first:last;
        for (double[] z:fvgZones){
            long t=(long)z[3];
            int j=-1;
            for (int k=0;k<disp.size();k++){
                CandleData c=disp.get(k);
                if (c!=null&&t>=c.openTime&&t<c.openTime+periodMs){j=k;break;}
            }
            if (j<0) continue;                       // pattern candle outside the visible overlay range
            int j0=Math.max(0,j-2);                  // span the 3-candle pattern (clamped)
            int xA=(int)(support.getMiddleOfCandle(side)+slot*(base[ai]+j0));
            int xB=(int)(support.getMiddleOfCandle(side)+slot*(base[ai]+j));
            int x1=Math.min(xA,xB)-halfBody, x2=Math.max(xA,xB)+halfBody;
            int yT=(int)support.getYForValue(z[1]), yB=(int)support.getYForValue(z[0]);
            if (yB<=yT||x2<=x1) continue;
            Color fc=z[2]==1?new Color(0,180,0):new Color(220,0,0);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,0.30f));
            g2.setColor(fc);
            g2.fillRect(x1,yT,x2-x1,yB-yT);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,0.9f));
            g2.setStroke(new BasicStroke(1f));
            g2.drawRect(x1,yT,x2-x1,yB-yT);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,1.0f));
        }
    }
    private void drawObZones(Graphics2D g2,IIndicatorDrawingSupport support){
        drawOneOb(g2,support,obBullIdx,obBullBroken,true);
        drawOneOb(g2,support,obBearIdx,obBearBroken,false);
    }
    private void drawOneOb(Graphics2D g2,IIndicatorDrawingSupport support,int idx,boolean broken,boolean bull){
        if (idx<0||inputs[0]==null||idx>=inputs[0].length) return;
        IBar b=inputs[0][idx];
        int x=support.getXForTime(b.getTime(),false);
        if (x<0) return;
        int yT=(int)support.getYForValue(b.getHigh()), yB=(int)support.getYForValue(b.getLow());
        if (yB<=yT) return;
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,0.18f));
        g2.setColor(broken?new Color(255,140,0):(bull?new Color(0,120,255):new Color(200,0,120)));
        g2.fillRect(x,yT,Math.max(8,support.getChartWidth()-x),yB-yT);
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,1.0f));
    }
    /** [official guide] "Previous EQ" (dotted, prev candle 50%) + "HTF Open" (solid, current open)
     *  plotted over the CURRENT HTF candle span, independent of the T-Spot gate; both grow with it. */
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
        g2.setColor(CLOSURE_COLORS[boundColorIndex]);
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,0.9f));
        int yO=(int)support.getYForValue(drawAnchor.curO);
        g2.setStroke(dashStroke(1,boundStyleIndex));
        g2.drawLine(x1,yO,x2,yO);
        if (prev!=null){
            int yE=(int)support.getYForValue(tspotEQ(prev));
            g2.setStroke(dashStroke(1,boundStyleIndex));
            g2.drawLine(x1,yE,x2,yE);
        }
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,1.0f));
    }
    /** official video match: thin gray verticals at anchor-layer candle boundaries. */
    private void drawHtfBounds(Graphics2D g2,IIndicatorDrawingSupport support){
        if (drawAnchor==null) return;
        g2.setColor(new Color(205,205,205)); g2.setStroke(new BasicStroke(0.8f));
        List<CandleData> h=drawAnchor.historical;
        for (int k=Math.max(0,h.size()-80);k<h.size();k++){
            int x=support.getXForTime(h.get(k).openTime,false);
            if (x>=0) g2.drawLine(x,0,x,support.getChartHeight());
        }
        if (drawAnchor.curActive){
            int x=support.getXForTime(drawAnchor.curStart,false);
            if (x>=0) g2.drawLine(x,0,x,support.getChartHeight());
        }
    }
    /** official video match: orange manipulation legs (swing->C2->C3) + C2/C4 labels + SMT leg tags. */
    private void drawModelLabels(Graphics2D g2,IIndicatorDrawingSupport support){
        if (!Double.isNaN(legP1)&&!Double.isNaN(legP2)){
            int x1=support.getXForTime(legT1,false),x2=support.getXForTime(legT2,false);
            int y1=(int)support.getYForValue(legP1),y2=(int)support.getYForValue(legP2);
            if (x1>=0&&x2>=0&&y1>=0&&y2>=0){
                g2.setColor(new Color(232,164,62)); g2.setStroke(new BasicStroke(1f));
                g2.drawLine(x1,y1,x2,y2);
                int x3=support.getXForTime(legT3,false);
                int y3=Double.isNaN(legP3)?y2:(int)support.getYForValue(legP3);
                boolean has3=(x3>=0&&y3>=0&&legT3!=legT2);
                if (has3) g2.drawLine(x2,y2,x3,y3);
                if (smtCracks!=null){
                    g2.setFont(g2.getFont().deriveFont(Font.BOLD,7f));
                    g2.drawString(smtCracks,(x1+x2)/2-10,(y1+y2)/2);
                    if (has3) g2.drawString(smtCracks,(x2+x3)/2-10,(y2+y3)/2+8);
                }
            }
        }
        g2.setFont(g2.getFont().deriveFont(Font.BOLD,8f));
        g2.setColor(new Color(70,70,70));
        if (c2LblTime>0&&!Double.isNaN(c2LblPrice)){
            int x=support.getXForTime(c2LblTime,false),y=(int)support.getYForValue(c2LblPrice);
            if (x>=0&&y>=0) g2.drawString("C2",x-4,c2LblBull?y+12:y-6);
        }
        if (c4LblTime>0&&!Double.isNaN(c4LblPrice)){
            int x=support.getXForTime(c4LblTime,false),y=(int)support.getYForValue(c4LblPrice);
            if (x>=0&&y>=0) g2.drawString("C4",x+2,y+10);
        }
    }
    /** official video match: projection ladder -1..-4.5 (R multiples from leg start) + dotted swing line. */
    private void drawProjLadder(Graphics2D g2,IIndicatorDrawingSupport support){
        if (Double.isNaN(legP1)||Double.isNaN(legP2)) return;
        double leg=Math.abs(legP2-legP1); if (leg<=0) return;
        boolean bull=legP2<legP1;
        double p1=legP1;
        if (projAnchor==1&&!Double.isNaN(eqAnchor)&&eqLeg>0){ p1=eqAnchor; leg=eqLeg; }   // official TV: anchor at manip-candle EQ
        double[] lv=projLadder(p1,leg,bull);
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
        int ys=(int)support.getYForValue(p1);
        if (ys>=0&&ys<=support.getChartHeight()){
            g2.setColor(new Color(90,90,90));
            g2.setStroke(new BasicStroke(0.8f,BasicStroke.CAP_BUTT,BasicStroke.JOIN_MITER,10f,new float[]{1.5f,2.5f},0f));
            g2.drawLine(0,ys,support.getChartWidth(),ys);
        }
    }
    /** [C, PO3 + official video match] T-Spot = wick-formation (manipulation) zone; zone EQ bound = full-range 50%.
     *  Official colors follow SUPPORT DIRECTION: GREEN = BUY-support (zone ABOVE EQ,
     *  lower wick forms then rejects up), RED = SELL-support (zone BELOW EQ, upper wick
     *  forms then rejects down). Flat translucent fill; solid line on the OPEN bound,
     *  dotted line on the EQ bound; no border, no zone text (official look). */
    private void drawTspot(Graphics2D g2,IIndicatorDrawingSupport support){
        for (double[] z:tspotZones){
            double lo=z[0],hi=z[1]; int type=(int)z[4],state=(int)z[5];
            if (type==0&&!tspotBull) continue;
            if (type==1&&!tspotBear) continue;
            int yT=(int)support.getYForValue(hi), yB=(int)support.getYForValue(lo);
            if (yB<=yT) continue;
            int x1=support.getXForTime((long)z[2],false);
            int x2=support.getXForTime((long)z[3],false);
            if (x1<0) continue;
            if (x2<0||x2<=x1) x2=x1+40;
            boolean cur=z[6]==1;
            Color base=(type==0)?new Color(0,120,60):new Color(190,0,0);   // type0=BUY green / type1=SELL red
            Color fill=(state==2&&!cur)?new Color(140,140,140):base;       // invalidated HISTORICAL -> gray
            float alpha=(state==2&&!cur)?0.15f:0.18f;                      // official match: invalidated gray clearly visible
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,alpha));
            g2.setColor(fill);
            g2.fillRect(x1,yT,x2-x1,yB-yT);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,0.9f));
            g2.setColor(CLOSURE_COLORS[boundColorIndex]);
            int yOpen=(type==0)?yT:yB, yEq=(type==0)?yB:yT;                // type0: open=top; type1: open=bottom
            g2.setStroke(dashStroke(1,boundStyleIndex));
            g2.drawLine(x1,yOpen,x2,yOpen);                                // OPEN bound (T-Spot edge)
            g2.setStroke(dashStroke(1,boundStyleIndex));
            g2.drawLine(x1,yEq,x2,yEq);                                    // EQ bound
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,1.0f));
        }
    }

    /** [official "Drawing Type" = HTF Candles / Both] T-Spot zones painted on the anchor-layer overlay columns. */
    private void drawTspotHtf(Graphics2D g2,IIndicatorDrawingSupport support,float slot,int[] base,int first,int last,float cW){
        if (drawAnchor==null||!drawAnchor.enabled) return;
        int ai=anchorLayer-1;
        if (ai<0||ai>=MAX_LAYERS) return;
        long chartInterval=context.getFeedDescriptor().getPeriod().getInterval();
        if (PERIOD_INTERVALS[drawAnchor.periodIndex]<=chartInterval) return;
        List<CandleData> disp=displayList(drawAnchor);
        if (disp==null||disp.isEmpty()) return;
        long periodMs=PERIOD_INTERVALS[drawAnchor.periodIndex];
        int bodyWidth=(int)Math.max(1,Math.min(cW*(candleBodyScale/100.0f),Math.max(1,slot-2)));
        int halfBody=Math.max(1,bodyWidth/2);
        int side=(drawAnchor.positionOption==0)?first:last;
        for (double[] z:tspotZones){
            double lo=z[0],hi=z[1]; int type=(int)z[4],state=(int)z[5];
            if (type==0&&!tspotBull) continue;
            if (type==1&&!tspotBear) continue;
            long t=(long)z[2];
            int j=-1;
            for (int k=0;k<disp.size();k++){
                CandleData c=disp.get(k);
                if (c!=null&&t>=c.openTime&&t<c.openTime+periodMs){j=k;break;}
            }
            if (j<0) continue;
            int xC=(int)(support.getMiddleOfCandle(side)+slot*(base[ai]+j));
            int x1=xC-halfBody, x2=xC+halfBody;
            int yT=(int)support.getYForValue(hi), yB=(int)support.getYForValue(lo);
            if (yB<=yT) continue;
            boolean cur=z[6]==1;
            Color baseC=(type==0)?new Color(0,120,60):new Color(190,0,0);
            Color fill=(state==2&&!cur)?new Color(140,140,140):baseC;
            float alpha=(state==2&&!cur)?0.15f:0.30f;
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,alpha));
            g2.setColor(fill);
            g2.fillRect(x1,yT,x2-x1,yB-yT);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,0.9f));
            g2.setColor(CLOSURE_COLORS[boundColorIndex]);
            g2.setStroke(dashStroke(1,boundStyleIndex));
            g2.drawLine(x1,yT,x2,yT);
            g2.drawLine(x1,yB,x2,yB);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,1.0f));
        }
    }

    // ---- drawing: official 5-line model panel (100% video match, no box, monospace) ----
    private String chartSymShort(){
        try { return shortSym(context.getFeedDescriptor().getInstrument().toString()); }
        catch (Exception e){ return "CHART"; }
    }
    private void drawInfoPanel(Graphics2D g2,IIndicatorDrawingSupport support,Font oldFont){
        boolean bull=(currentBias!=0)?currentBias>0:structTrend>0;
        String tf=chartPeriodMs>0?tfShort(chartPeriodMs):"-";
        String htf=drawAnchor!=null?tfShort(PERIOD_INTERVALS[drawAnchor.periodIndex]):"-";
        String timeS="--:--:--";
        if (panelTimeMode==2&&drawAnchor!=null&&drawAnchor.curActive){
            // [official TV mode] countdown to anchor HTF candle close (HH:MM:SS)
            long remain=drawAnchor.curStart+PERIOD_INTERVALS[drawAnchor.periodIndex]-System.currentTimeMillis();
            if (remain<0) remain=0;
            timeS=String.format("%02d:%02d:%02d",remain/3600000,(remain%3600000)/60000,(remain%60000)/1000);
        } else {
            // default: YOUR live clock, ticking on every repaint, in the timezone you work with
            TimeZone tz=(panelTimeMode==1)?TimeZone.getTimeZone("America/New_York"):support.getJFTimeZone().getTimeZone();
            SimpleDateFormat tfmt=new SimpleDateFormat("HH:mm:ss");
            tfmt.setTimeZone(tz);
            timeS=tfmt.format(new java.util.Date(System.currentTimeMillis()));
        }
        List<String> ls=new ArrayList<>(); List<Boolean> big=new ArrayList<>();
        ls.add(chartSymShort()+(bull?"\u2191!":"\u2193!")+" ("+tf+")"); big.add(Boolean.FALSE);
        ls.add(tf+"-"+htf+" Model"); big.add(Boolean.FALSE);
        ls.add(timeS); big.add(Boolean.TRUE);
        String biasS=(currentBias!=0)?(currentBias>0?"Bullish":"Bearish"):((biasModel==1)?"Neutral":(structTrend>0?"Bullish":"Bearish"));
        ls.add("Bias: "+biasS); big.add(Boolean.FALSE);
        if (smtCracks!=null){ ls.add("SMT(Auto): "+smtCracks); big.add(Boolean.FALSE); }
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

    // ==================================================================
    @Override public IndicatorInfo getIndicatorInfo(){ return indicatorInfo; }
    @Override public InputParameterInfo getInputParameterInfo(int i){ return inputParameterInfos[i]; }
    @Override public OptInputParameterInfo getOptInputParameterInfo(int i){ return i<optInputParameterInfos.length?optInputParameterInfos[i]:null; }
    @Override public OutputParameterInfo getOutputParameterInfo(int i){ return outputParameterInfos[i]; }
    @Override public void setInputParameter(int i,Object o){ inputs[i]=(IBar[])o; }
    @Override public void setOutputParameter(int i,Object o){ outputs[i]=o; }
    @Override public int getLookback(){ return 0; }
    @Override public int getLookforward(){ return 0; }
}
