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
 *    Anchor layer = Layer 1 (4H) drives columns/T-Spot/legs  [Anchor decision b]
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
 *    Chart bucketing TZ = GMT+3, midnight anchor (matches locked TV match)
 *    SMT = NOT in this build (deferred by user until experiment succeeds)
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
 *    9 info panel: symbol line, model line, YOUR live clock, Bias line
 *
 *  [C]=official concept  [J]=Java reference mechanics  [O]=our definition
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import com.dukascopy.api.IBar;
import com.dukascopy.api.Instrument;
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
    static final int MAX_SIGNALS = 5;

    static final int    PERIOD_COUNT = 6;
    static final long[] PERIOD_INTERVALS = {
        15*60*1000L, 30*60*1000L, 60*60*60*1000L,
        4*60*60*1000L, 24*60*60*1000L, 7*60*60*1000L
    };
    static final String[] SHORT_LABELS = {"15m","30m","1H","4H","D","7H"};

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

    static final class Signal {
        long waveStart, confirmTime; double entry, stop; boolean bullish; String session;
        int state = 0;
        boolean icEarly = true;
        int legCat = 1;
        double t1 = Double.NaN, t2 = Double.NaN;
    }

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
    private static final TimeZone CHART_TZ = TimeZone.getTimeZone("GMT+3:00");  // locked TV match
    private static final int HTF_ANCHOR = 0;            // midnight of CHART_TZ
    private static final boolean CISD_IGNORE_INSIDE = true;
    private static final int ACTIVE_SESSIONS = 1;       // London + NY

    // ================== the ONE agreed option ==================
    int cisdSensitivity = 1;   // raw option value; default Medium (locked step-3)
    /** verbatim from locked core: 0=Low(min3) 1=Medium(min2) 2=High(min1). */
    static int minWaveFor(int sens){ return (sens==0)?3:(sens==1)?2:1; }
    private interface OptInputSetter { void set(Object v); }
    private static final int[] SENS_VALUES = {0,1,2};
    private static final String[] SENS_NAMES = {"Low (min 3)","Medium (min 2)","High (min 1)"};
    private final com.dukascopy.api.indicators.OptInputParameterInfo[] optInfos =
        new com.dukascopy.api.indicators.OptInputParameterInfo[]{
            new com.dukascopy.api.indicators.OptInputParameterInfo("[CISD] Min Wave Length",
                com.dukascopy.api.indicators.OptInputParameterInfo.Type.OTHER,
                new com.dukascopy.api.indicators.IntegerListDescription(1,SENS_VALUES,SENS_NAMES))
        };
    private final OptInputSetter[] optSetters = new OptInputSetter[]{ v->cisdSensitivity=(Integer)v };

    // ================== runtime ==================
    private IIndicatorContext context;
    private IBar[][] inputs = new IBar[1][];
    private Object[] outputs;
    private IndicatorInfo indicatorInfo;
    private InputParameterInfo[] inputParameterInfos;
    private OutputParameterInfo[] outputParameterInfos;
    private TimeZone nyTZ = TimeZone.getTimeZone("America/New_York");

    private final List<Signal> signals = new ArrayList<>();
    private long pendingBullStart=-1, pendingBearStart=-1;
    private double pendingBullTrigger=Double.NaN, pendingBullStop=Double.NaN;
    private double pendingBearTrigger=Double.NaN, pendingBearStop=Double.NaN;
    private long lastChartPeriodMs = -1;
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
    @Override
    public void onStart(IIndicatorContext context){
        this.context=context;
        outputs=new Object[MAX_CANDLES*4];
        inputParameterInfos = new InputParameterInfo[]{ new InputParameterInfo("Chart Bars", InputParameterInfo.Type.BAR) };
        indicatorInfo=new IndicatorInfo("TTFMEssence","TTFM Essence (minimal-settings experiment)","Custom",
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
        if (periodMs!=lastChartPeriodMs){
            lastChartPeriodMs=periodMs;
            signals.clear(); pendingBullStart=pendingBearStart=-1; currentBias=0; currentInversion=false;
            for (LayerData l:layers){ l.historical.clear(); l.curActive=false; l.curO=l.curH=l.curL=l.curC=Double.NaN; l.curStart=0; }
        }
        TimeZone tz=CHART_TZ;

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
        LayerData primary=layers[ANCHOR_LAYER-1];
        drawAnchor=primary;
        updateNdmBias(layers[BIAS_LAYER-1]);

        int detectionIndex=endIndex;
        if (detectionIndex==bars.length-1){
            long barEnd=bars[detectionIndex].time+periodMs;
            if (System.currentTimeMillis()<barEnd) detectionIndex=Math.max(0,detectionIndex-1);
        }
        detectCisd(bars,detectionIndex,periodMs,primary);
        updateSignalStates(bars,primary);
        updateConceptState(bars,primary,periodMs);

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

    // ================== CISD engine (locked step 3) ==================
    private void detectCisd(RB[] bars,int detectionIndex,long periodMs,LayerData primary){
        if (bars.length<2||detectionIndex<0||detectionIndex>=bars.length) return;
        int minRequired=minWaveFor(cisdSensitivity);
        int maxLookback=200;
        RB cur=bars[detectionIndex];

        int[] w=findWave(bars,detectionIndex-1,maxLookback,true,CISD_IGNORE_INSIDE);
        if (w[0]!=-1&&(w[1]-w[0]+1)>=minRequired){
            double firstOpen=bars[w[0]].o; double lowest=Double.MAX_VALUE;
            for (int k=w[0];k<=w[1];k++) lowest=Math.min(lowest,bars[k].l);
            if (!isDuplicate(bars[w[0]].time,firstOpen)){ pendingBullStart=bars[w[0]].time; pendingBullTrigger=firstOpen; pendingBullStop=lowest; }
        }
        w=findWave(bars,detectionIndex-1,maxLookback,false,CISD_IGNORE_INSIDE);
        if (w[0]!=-1&&(w[1]-w[0]+1)>=minRequired){
            double firstOpen=bars[w[0]].o; double highest=-Double.MAX_VALUE;
            for (int k=w[0];k<=w[1];k++) highest=Math.max(highest,bars[k].h);
            if (!isDuplicate(bars[w[0]].time,firstOpen)){ pendingBearStart=bars[w[0]].time; pendingBearTrigger=firstOpen; pendingBearStop=highest; }
        }

        if (pendingBullStart!=-1 && cur.c>pendingBullTrigger){
            if (sessionAllowed(sessionOf(pendingBullStart,nyTZ),ACTIVE_SESSIONS) && Math.abs(pendingBullTrigger-pendingBullStop)>1e-9)
                addSignal(pendingBullStart,cur.time,pendingBullTrigger,pendingBullStop,true,periodMs,primary);
            pendingBullStart=-1;
        }
        if (pendingBearStart!=-1 && cur.c<pendingBearTrigger){
            if (sessionAllowed(sessionOf(pendingBearStart,nyTZ),ACTIVE_SESSIONS) && Math.abs(pendingBearTrigger-pendingBearStop)>1e-9)
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
        long htfPeriod=PERIOD_INTERVALS[primary.periodIndex];
        long htfStart=periodStart(confirmTime,htfPeriod,CHART_TZ);
        s.icEarly=icEarly(confirmTime,htfStart,htfPeriod);
        double leg=Math.abs(entry-stop);
        s.legCat=legCategory(leg,avgHtfRange(primary));
        double[] mult=legMultipliers(s.legCat);
        double dir=bullish?1:-1;
        s.t1=entry+dir*Math.abs(mult[0])*leg;
        s.t2=(mult.length>1)?entry+dir*Math.abs(mult[1])*leg:Double.NaN;
        signals.add(s);
        while (signals.size()>MAX_SIGNALS) signals.remove(0);
    }

    private void updateSignalStates(RB[] bars,LayerData primary){
        for (Signal s:signals){
            int from=-1;
            for (int i=0;i<bars.length;i++) if (bars[i].time>s.confirmTime){ from=i; break; }
            boolean htfClosed=false;
            for (CandleData c:primary.historical) if (c.completed && c.openTime>s.confirmTime){ htfClosed=true; break; }
            s.state = (from<0)?0:signalState(s.bullish,s.entry,s.stop,bars,from,htfClosed);
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
                    g2.drawString(lbl,lx-tw/2,ly);
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
                    SimpleDateFormat lf=new SimpleDateFormat("HH:mm");lf.setTimeZone(support.getJFTimeZone().getTimeZone());
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
            drawCisdLines(g2,support,slot,oldFont); // element 8
            drawTspot(g2,support);               // elements 4,5,6
            drawProjLadder(g2,support);          // element 9
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
    private void drawCisdLines(Graphics2D g2,IIndicatorDrawingSupport support,float slot,Font oldFont){
        if (signals.isEmpty()) return;
        String tf=tfShort(context.getFeedDescriptor().getPeriod().getInterval());
        for (Signal s:signals){
            int x1=support.getXForTime(s.waveStart,false);
            int x2=support.getXForTime(s.confirmTime,false);
            if (x1<0||x2<0) continue;
            int y=(int)support.getYForValue(s.entry);
            if (y<0||y>=support.getChartHeight()) continue;
            int xEnd=(int)(x2+3*slot); if (xEnd>support.getChartWidth()) xEnd=support.getChartWidth();

            Color dirColor=s.bullish?CISD_BULL_COLOR:CISD_BEAR_COLOR;
            g2.setColor(dirColor);
            g2.setStroke(new BasicStroke(2.0f,BasicStroke.CAP_BUTT,BasicStroke.JOIN_MITER,10f,null,0f));
            g2.drawLine(x1,y,xEnd,y);

            String lbl=(s.bullish?"+Cisd":"-Cisd")+" ("+tf+")";
            g2.setFont(oldFont.deriveFont(Font.BOLD,10f));
            FontMetrics fm=g2.getFontMetrics();
            int tx=xEnd+4; if (tx+fm.stringWidth(lbl)>support.getChartWidth()) tx=support.getChartWidth()-fm.stringWidth(lbl)-10;
            g2.setColor(dirColor); g2.drawString(lbl,tx,y-4);

            int ys=(int)support.getYForValue(s.stop);
            if (ys>=0&&ys<support.getChartHeight()){
                g2.setColor(dirColor);
                g2.setStroke(new BasicStroke(1.2f,BasicStroke.CAP_BUTT,BasicStroke.JOIN_MITER,10f,new float[]{2f,3f},0f));
                g2.drawLine(x1,ys,xEnd,ys);
            }
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

    /** element 10: 4-line panel - symbol, model, YOUR live clock (chart TZ), Bias (NDM daily). */
    private void drawInfoPanel(Graphics2D g2,IIndicatorDrawingSupport support,Font oldFont){
        boolean bull=(currentBias!=0)?currentBias>0:structTrend>0;
        String tf=chartPeriodMs>0?tfShort(chartPeriodMs):"-";
        String htf=drawAnchor!=null?tfShort(PERIOD_INTERVALS[drawAnchor.periodIndex]):"-";
        SimpleDateFormat tfmt=new SimpleDateFormat("HH:mm:ss");
        tfmt.setTimeZone(support.getJFTimeZone().getTimeZone());
        String timeS=tfmt.format(new Date(System.currentTimeMillis()));
        List<String> ls=new ArrayList<>(); List<Boolean> big=new ArrayList<>();
        ls.add(chartSymShort()+(bull?"\u2191!":"\u2193!")+" ("+tf+")"); big.add(Boolean.FALSE);
        ls.add(tf+"-"+htf+" Model"); big.add(Boolean.FALSE);
        ls.add(timeS); big.add(Boolean.TRUE);
        String biasS=(currentBias!=0)?(currentBias>0?"Bullish":"Bearish"):"Neutral";
        ls.add("Bias: "+biasS); big.add(Boolean.FALSE);
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
