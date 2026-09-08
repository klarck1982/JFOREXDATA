package com.dukascopy.indicators;

/*
 * ============================================================================
 *  TTFMCore  -  TTrades Fractal Model Core Engine  -  JForex / Dukascopy
 *  v2 = v1 core + State machine + IC-CISD + EQ Inversion + Projections
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
        15*60*1000L, 30*60*1000L, 60*60*60*1000L,
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
    private static final int[] SENS_VALUES = {0,1,2};
    private static final String[] SENS_NAMES = {"Low (min 3)","Medium (min 2)","High (min 1)"};
    private static final int[] SCHEME_VALUES = {0,1};
    private static final String[] SCHEME_NAMES = {"Green + Black","White + Black"};
    private static final int[] SHADE_VALUES = {0,1};
    private static final String[] SHADE_NAMES = {"No","EQ Only"};
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
        double eq(){ return (high+low)/2.0; }   // [J]
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
    private boolean showTimer = true;
    private int candleBodyScale = 100;
    private int colorScheme = 0;
    private boolean showClosures = true;
    private int closureColorIndex = 0;
    private int closureStyleIndex = 1;
    private int closureWidth = 1;
    private int shadeMode = 1;
    private int maxHistoryShades = 3;
    private int eqLineLayer = 0;
    private boolean semanticLabels = false;
    private boolean showCISD = true;
    private int cisdSensitivity = 1;
    private boolean ignoreInsideBars = true;
    private int activeSessions = 1;
    private int chartTimezone = 4;
    private int layerSpacing = 3;
    private boolean saveJournal = true;
    // v2
    private boolean colorStates = true;
    private boolean skipLateIC = false;
    private boolean showBias = true;
    private boolean showTargets = true;

    private final LayerData[] layers = new LayerData[MAX_LAYERS];
    {
        for (int i=0;i<MAX_LAYERS;i++) layers[i]=new LayerData();
        layers[0].enabled=true; layers[0].periodIndex=3; layers[0].candlesToShow=6; layers[0].candleOffset=10;
        layers[1].enabled=true; layers[1].periodIndex=5; layers[1].candlesToShow=3;
        layers[2].enabled=true; layers[2].periodIndex=4; layers[2].candlesToShow=3;
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

    private interface OptInputSetter { void set(Object v); }

    // ==================================================================
    //  PURE LOGIC CORE (platform-free, unit-tested)
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

    static void aggregate(LayerData layer, RB bar, long intervalMs, TimeZone tz){
        long ps = periodStart(bar.time,intervalMs,tz);
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
        opt.add(new OptInputParameterInfo("[Display] Show Timer",OptInputParameterInfo.Type.OTHER,new IntegerListDescription(1,BOOLEAN_VALUES,BOOLEAN_NAMES)));
        set.add(v->showTimer=((Integer)v)==1);
        opt.add(new OptInputParameterInfo("[Display] Candle Body Scale (%)",OptInputParameterInfo.Type.OTHER,new IntegerRangeDescription(100,50,200,10)));
        set.add(v->candleBodyScale=(Integer)v);
        opt.add(new OptInputParameterInfo("[Display] Color Scheme",OptInputParameterInfo.Type.OTHER,new IntegerListDescription(0,SCHEME_VALUES,SCHEME_NAMES)));
        set.add(v->{colorScheme=(Integer)v;applyScheme();});
        opt.add(new OptInputParameterInfo("[Display] Show Closures",OptInputParameterInfo.Type.OTHER,new IntegerListDescription(1,BOOLEAN_VALUES,BOOLEAN_NAMES)));
        set.add(v->showClosures=((Integer)v)==1);
        opt.add(new OptInputParameterInfo("[Display] Closure Color",OptInputParameterInfo.Type.OTHER,new IntegerListDescription(0,colorIndexes(),CLOSURE_COLOR_NAMES)));
        set.add(v->closureColorIndex=(Integer)v);
        opt.add(new OptInputParameterInfo("[Display] Closure Style",OptInputParameterInfo.Type.OTHER,new IntegerListDescription(1,LINE_STYLES,LINE_STYLE_NAMES)));
        set.add(v->closureStyleIndex=(Integer)v);
        opt.add(new OptInputParameterInfo("[Display] Closure Width",OptInputParameterInfo.Type.OTHER,new IntegerRangeDescription(1,1,5,1)));
        set.add(v->closureWidth=(Integer)v);
        opt.add(new OptInputParameterInfo("[EQ] Respected-Half Shade",OptInputParameterInfo.Type.OTHER,new IntegerListDescription(shadeMode,SHADE_VALUES,SHADE_NAMES)));
        set.add(v->shadeMode=(Integer)v);
        opt.add(new OptInputParameterInfo("[EQ] Max History Shades",OptInputParameterInfo.Type.OTHER,new IntegerListDescription(maxHistoryShades,HIST_VALUES,HIST_NAMES)));
        set.add(v->maxHistoryShades=(Integer)v);
        opt.add(new OptInputParameterInfo("[EQ] Show EQ Line",OptInputParameterInfo.Type.OTHER,new IntegerListDescription(eqLineLayer,EQ_LAYER_VALUES,EQ_LAYER_NAMES)));
        set.add(v->eqLineLayer=(Integer)v);
        opt.add(new OptInputParameterInfo("[EQ] Show Bias Label (Inversion)",OptInputParameterInfo.Type.OTHER,new IntegerListDescription(1,BOOLEAN_VALUES,BOOLEAN_NAMES)));
        set.add(v->showBias=((Integer)v)==1);
        opt.add(new OptInputParameterInfo("[Concept] Semantic C1-C4 Labels",OptInputParameterInfo.Type.OTHER,new IntegerListDescription(0,BOOLEAN_VALUES,BOOLEAN_NAMES)));
        set.add(v->semanticLabels=((Integer)v)==1);
        opt.add(new OptInputParameterInfo("[CISD] Detection",OptInputParameterInfo.Type.OTHER,new IntegerListDescription(1,BOOLEAN_VALUES,BOOLEAN_NAMES)));
        set.add(v->showCISD=((Integer)v)==1);
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
        opt.add(new OptInputParameterInfo("[Sessions] Active Sessions",OptInputParameterInfo.Type.OTHER,new IntegerListDescription(activeSessions,SESSION_VALUES,SESSION_NAMES)));
        set.add(v->activeSessions=(Integer)v);
        opt.add(new OptInputParameterInfo("[Signals] Save Journal CSV",OptInputParameterInfo.Type.OTHER,new IntegerListDescription(1,BOOLEAN_VALUES,BOOLEAN_NAMES)));
        set.add(v->saveJournal=((Integer)v)==1);
        opt.add(new OptInputParameterInfo("[Advanced] Chart Timezone",OptInputParameterInfo.Type.OTHER,new IntegerListDescription(chartTimezone,TZ_VALUES,TZ_NAMES)));
        set.add(v->{chartTimezone=(Integer)v;chartCalendar=Calendar.getInstance(getTimezone());});
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
                aggregate(layer,bars[i],interval,tz);
            }
        }

        LayerData primary=primaryLayer(periodMs);
        updateEqBias(primary);

        int detectionIndex=endIndex;
        if (detectionIndex==bars.length-1){
            long barEnd=bars[detectionIndex].time+periodMs;
            if (System.currentTimeMillis()<barEnd) detectionIndex=Math.max(0,detectionIndex-1);
        }
        if (showCISD) detectCisd(bars,detectionIndex,periodMs,primary);
        updateSignalStates(bars,primary);

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
    private void updateEqBias(LayerData primary){
        CandleData last=null;
        for (int i=primary.historical.size()-1;i>=0;i--) if (primary.historical.get(i).completed){ last=primary.historical.get(i); break; }
        if (last==null){ currentBias=0; currentInversion=false; return; }
        CandleData prev=null;
        for (int i=primary.historical.size()-1;i>=0;i--){
            CandleData c=primary.historical.get(i);
            if (c.completed && c!=last){ prev=c; break; }
        }
        double prevEq = (prev!=null)? prev.eq() : last.eq();
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
        long htfStart=periodStart(confirmTime,htfPeriod,getTimezone());
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

            if (eqLineLayer>0&&i==eqLineLayer-1){
                CandleData comp=null;
                for (int k=disp.size()-1;k>=0;k--) if (disp.get(k).completed){comp=disp.get(k);break;}
                if (comp!=null){
                    int yEQ=(int)support.getYForValue(comp.eq());
                    int x1=support.getXForTime(comp.openTime,false);
                    long endT=(layer.curActive&&layer.curStart>comp.openTime)?layer.curStart+periodMs:comp.openTime+periodMs;
                    int x2=support.getXForTime(endT,false);
                    if (x1>=0&&x2>=0&&yEQ>=0&&yEQ<support.getChartHeight()){
                        g2.setColor(CLOSURE_COLORS[closureColorIndex]);
                        g2.setStroke(dashStroke(closureWidth,closureStyleIndex));
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
                    g2.drawString(lbl,lx-g2.getFontMetrics().stringWidth(lbl)/2,ly);
                    if (showTimer){
                        long remain=layer.curStart+periodMs-System.currentTimeMillis();
                        if (remain>0){
                            String t=String.format("%02d:%02d:%02d",remain/3600000,(remain%3600000)/60000,(remain%60000)/1000);
                            g2.setFont(oldFont.deriveFont(Font.BOLD,10f));
                            FontMetrics fm=g2.getFontMetrics();int tw=fm.stringWidth(t);
                            g2.setColor(bull?new Color(0,200,0,100):new Color(200,0,0,100));
                            g2.fillRect(lx-tw/2-3,ly-fm.getAscent()-5,tw+6,fm.getAscent()+4);
                            g2.setColor(Color.WHITE);g2.drawString(t,lx-tw/2,ly-5);
                        }
                    }
                }
            }

            if (i==0&&layer.lsActive&&!Double.isNaN(layer.lsPrice)){
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

            if (showClosures&&i==0){
                int ch=support.getChartHeight();
                int xOpen=support.getXForTime(cd.openTime,false);
                if (xOpen>=0){
                    g2.setColor(CLOSURE_COLORS[closureColorIndex]);g2.setStroke(dashStroke(closureWidth,closureStyleIndex));
                    g2.drawLine(xOpen,0,xOpen,ch-1);
                    SimpleDateFormat lf=new SimpleDateFormat("HH:mm");lf.setTimeZone(support.getJFTimeZone().getTimeZone());
                    String t="HC"+(candleIdx+1)+" ("+lf.format(new Date(cd.openTime))+"-"+lf.format(new Date(cd.openTime+periodMs))+")";
                    g2.setFont(oldFont.deriveFont(Font.BOLD,9f));
                    int tw=g2.getFontMetrics().stringWidth(t);
                    int tx=(xOpen+4+tw>support.getChartWidth())?xOpen-tw-3:xOpen+4;
                    g2.drawString(t,tx,12);
                }
            }

            if (shadeMode==1&&i==firstLayerIndex){
                int total=Math.min(maxHistoryShades,disp.size()-1);
                for (int k=disp.size()-1;k>=1&&(disp.size()-1-k)<total;k--){
                    CandleData curC=disp.get(k),prev=disp.get(k-1);
                    if (curC==null||!curC.completed||prev==null||!prev.completed) continue;
                    int x1=support.getXForTime(curC.openTime,false),x2=support.getXForTime(curC.openTime+periodMs,false);
                    if (x1<0||x2<0) continue;
                    double prevMid=prev.eq(), half=(prev.high-prev.low)/2.0;
                    double extreme=(prev.close>=prev.open)?prevMid-half:prevMid+half;
                    int yT=(int)support.getYForValue(Math.max(prevMid,extreme));
                    int yB=(int)support.getYForValue(Math.min(prevMid,extreme));
                    if (yB-yT>0){
                        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,0.3f));
                        g2.setColor((prev.close>=prev.open)?EQ_SHADE_BULL:EQ_SHADE_BEAR);
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
            drawCisdLines(g2,support,slot,oldFont);
            if (showBias) drawBias(g2,support,oldFont);
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
            Color stateColor=(s.state==2)?STATE_FAILED:(s.state==1)?STATE_TIGHTEN:STATE_VALID;
            Color lineColor=colorStates?stateColor:dirColor;

            g2.setColor(lineColor);
            g2.setStroke(new BasicStroke((s.state==2)?1.2f:2.0f,BasicStroke.CAP_BUTT,BasicStroke.JOIN_MITER,10f,(s.state==2)?new float[]{4f,4f}:null,0f));
            g2.drawLine(x1,y,xEnd,y);

            String lbl=(s.bullish?"+Cisd":"-Cisd")+" ("+tf+")";
            g2.setFont(oldFont.deriveFont(Font.BOLD,10f));
            FontMetrics fm=g2.getFontMetrics();
            int tx=xEnd+4; if (tx+fm.stringWidth(lbl)>support.getChartWidth()) tx=support.getChartWidth()-fm.stringWidth(lbl)-10;
            g2.setColor(dirColor); g2.drawString(lbl,tx,y-4);

            // v2 state tag
            if (colorStates){
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
            if (showTargets){
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
        if (currentBias==0) return;
        String txt=(currentBias>0?"BIAS: LONG":"BIAS: SHORT")+(currentInversion?"  (EQ-INV)":"");
        g2.setFont(oldFont.deriveFont(Font.BOLD,11f));
        FontMetrics fm=g2.getFontMetrics();
        int w=fm.stringWidth(txt);
        int x=support.getChartWidth()-w-12, y=24;
        g2.setColor(new Color(20,20,30,200));
        g2.fillRect(x-6,y-fm.getAscent()-4,w+12,fm.getHeight()+8);
        g2.setColor(currentBias>0?BIAS_LONG:BIAS_SHORT);
        g2.drawString(txt,x,y);
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
