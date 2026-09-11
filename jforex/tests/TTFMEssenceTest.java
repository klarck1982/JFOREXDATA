package com.dukascopy.indicators;

/*
 * TTFMEssenceTest - unit tests for the zero-settings experimental build.
 * Same locked rules as TTFMCoreCoreTest; run:
 *   java -cp _compilecheck/out com.dukascopy.indicators.TTFMEssenceTest
 */
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

public class TTFMEssenceTest {
    static int pass=0, fail=0;
    static void check(String name, boolean cond){
        if (cond){ pass++; }
        else { fail++; System.out.println("FAIL: "+name); }
    }
    static void eq(String name, double a, double b, double eps){ check(name, Math.abs(a-b)<=eps); }
    static TTFMEssence.RB rb(long t,double o,double h,double l,double c){ return new TTFMEssence.RB(t,o,h,l,c,0); }
    static TTFMEssence.RB rb(long t,double o,double h,double l,double c,double v){ return new TTFMEssence.RB(t,o,h,l,c,v); }
    static TTFMEssence.CandleData cd(double o,double h,double l,double c,long t,boolean done){
        return new TTFMEssence.CandleData(o,h,l,c,t,done);
    }
    static long ms(int day,int hour,int min,TimeZone tz){
        Calendar c=Calendar.getInstance(tz);
        c.set(2026,Calendar.SEPTEMBER,day,hour,min,0); c.set(Calendar.MILLISECOND,0);
        return c.getTimeInMillis();
    }
    static long ymd(int y,int mon,int day,int hour,int min,TimeZone tz){
        Calendar c=Calendar.getInstance(tz);
        c.set(y,mon,day,hour,min,0); c.set(Calendar.MILLISECOND,0);
        return c.getTimeInMillis();
    }
    static int hourIn(TimeZone tz,long t){
        Calendar c=Calendar.getInstance(tz); c.setTimeInMillis(t);
        return c.get(Calendar.HOUR_OF_DAY);
    }
    static List<TTFMEssence.SmtPivot> peerPivots(boolean high,long t1,double p1,long t2,double p2){
        List<TTFMEssence.SmtPivot> l=new ArrayList<>();
        l.add(new TTFMEssence.SmtPivot(t1,p1,high));
        l.add(new TTFMEssence.SmtPivot(t2,p2,high));
        return l;
    }

    public static void main(String[] args){
        TimeZone g3=TimeZone.getTimeZone("GMT+3:00");
        TimeZone ny=TimeZone.getTimeZone("America/New_York");

        // ---- locked conditional EQ (step 1) ----
        eq("eq normal wicks -> full mid", cd(100,113,97,110,0,true).candleEQ(), 105.0, 1e-9);
        eq("eq long lower wick", cd(100,105,80,104,0,true).candleEQ(), (80+100)/2.0, 1e-9);
        eq("eq long upper wick", cd(100,130,95,101,0,true).candleEQ(), (130+101)/2.0, 1e-9);
        eq("eq doji equal wicks -> upper-seg mid (locked core rule)", cd(100,120,80,100,0,true).candleEQ(), 110.0, 1e-9);
        eq("eq full-range helper", cd(10,30,10,20,0,true).eq(), 20.0, 1e-9);

        // ---- official Next-Day Model bias (frozen on Daily) ----
        TTFMEssence.CandleData prev=cd(100,110,90,105,1,true);
        int[] r=TTFMEssence.ndmBias(cd(105,115,104,112,2,true),prev);
        check("ndm bull continuation", r[0]==1&&r[1]==0);
        r=TTFMEssence.ndmBias(cd(105,95,85,88,2,true),prev);
        check("ndm bear continuation", r[0]==-1&&r[1]==0);
        r=TTFMEssence.ndmBias(cd(105,115,95,104,2,true),prev);
        check("ndm swept high close inside -> bearish reversal", r[0]==-1&&r[1]==1);
        r=TTFMEssence.ndmBias(cd(105,104,85,96,2,true),prev);
        check("ndm swept low close inside -> bullish reversal", r[0]==1&&r[1]==1);
        r=TTFMEssence.ndmBias(cd(105,108,92,99,2,true),prev);
        check("ndm inside no sweep -> neutral", r[0]==0&&r[1]==0);
        r=TTFMEssence.ndmBias(null,prev);
        check("ndm null last -> neutral", r[0]==0);
        r=TTFMEssence.ndmBias(prev,null);
        check("ndm null prev -> neutral", r[0]==0);

        // ---- formation liquidity [O element 7] ----
        TTFMEssence.LayerData dl=new TTFMEssence.LayerData();
        check("liq null when empty", TTFMEssence.formationLiquidity(dl)==null);
        dl.historical.add(cd(1,7722.279,7690,7710,10,true));
        dl.historical.add(cd(7710,7715,7682.356,7700,11,false)); // not completed -> skipped
        double[] liq=TTFMEssence.formationLiquidity(dl);
        check("liq prev completed high", liq!=null&&Math.abs(liq[0]-7722.279)<1e-9);
        check("liq prev completed low",  liq!=null&&Math.abs(liq[1]-7690.0)<1e-9);

        // ---- locked T-Spot ----
        check("tspot degenerate null", TTFMEssence.tspotZone(100,100)==null);
        double[] z=TTFMEssence.tspotZone(100,104);
        check("tspot zone ordered", z!=null&&z[0]==100&&z[1]==104);
        check("tspot type sell (open below eq)", TTFMEssence.tspotType(100,96)==1);
        check("tspot type buy (open above eq)", TTFMEssence.tspotType(100,104)==0);
        eq("tspot eq wick-to-wick 50%", TTFMEssence.tspotEQ(cd(100,120,80,110,0,true)), 100.0, 1e-9);
        TTFMEssence.RB[] zb={rb(1,100,105,99,101),rb(2,101,103,98,99),rb(3,99,102,97,98)};
        check("zoneState sell invalidated by close below", TTFMEssence.zoneState(99,103,1,zb,0)==2);
        TTFMEssence.RB[] zbu={rb(1,100,104,99,104.5)};
        check("zoneState buy invalidated by close above", TTFMEssence.zoneState(99,103,0,zbu,0)==2);
        TTFMEssence.RB[] zt={rb(1,100,102,99.5,101),rb(2,101,102.5,100,101.5)};
        check("zoneState touched fresh", TTFMEssence.zoneState(99,103,0,zt,0)==1);
        TTFMEssence.RB[] zf={rb(1,30,40,20,35)};
        check("zoneState untouched+valid", TTFMEssence.zoneState(50,60,0,zf,0)==0);

        // ---- model zone gate (C2/C3 generators) ----
        List<TTFMEssence.CandleData> hist=new ArrayList<>();
        hist.add(cd(100,110,95,108,1,true));                       // c0
        hist.add(cd(108,112,94,96,2,true));                        // c1 sweeps c0 low, closes inside
        hist.add(cd(96,109,95,107,3,true));                        // c2 sweeps c1 low? no: c2.low=95>94 -> check c2 vs c1 sweep
        // build a clean C2: c1 sweeps c0 low & closes inside; c2 = generator after? gate(g) uses c1=hist[g-1], c2=hist[g]
        hist.clear();
        hist.add(cd(100,110,95,105,1,true));
        hist.add(cd(105,108,93,106,2,true));  // sweeps prev low (93<95) closes above prev close, inside range = C2 manip
        check("gate true on C2 sweep closure", TTFMEssence.modelZoneGate(hist,1));
        hist.add(cd(97,104,96,103,3,true));   // plain candle, no sweep of c1
        check("gate false on plain candle", TTFMEssence.modelZoneGate(hist,2)==false);

        // ---- projection ladder ----
        double[] lad=TTFMEssence.projLadder(100,10,true);
        eq("ladder bull -1", lad[0], 110, 1e-9);
        eq("ladder bull -2.5", lad[2], 125, 1e-9);
        eq("ladder bull -4.5", lad[4], 145, 1e-9);
        lad=TTFMEssence.projLadder(100,10,false);
        eq("ladder bear -2", lad[1], 80, 1e-9);

        // ---- leg category & multipliers ----
        check("leg large", TTFMEssence.legCategory(15,10)==0);
        check("leg average", TTFMEssence.legCategory(10,10)==1);
        check("leg expanding", TTFMEssence.legCategory(25,10)==2);
        check("mult large = -1 only", TTFMEssence.legMultipliers(0).length==1);
        check("mult expanding = -4/-4.5", TTFMEssence.legMultipliers(2)[1]==-4.5);

        // ---- IC early/late ----
        check("ic early", TTFMEssence.icEarly(1000+30,1000,100));
        check("ic late", !TTFMEssence.icEarly(1000+80,1000,100));

        // ---- signal state machine (locked step 3) ----
        TTFMEssence.RB[] sb={rb(10,100,101,99,100.5),rb(11,100.5,102,100,101.5),rb(12,101.5,103,101,102.5)};
        check("state valid after 1R", TTFMEssence.signalState(true,100,98,sb,0,false)==0);
        TTFMEssence.RB[] sf={rb(10,100,101,99,100.5),rb(11,100.5,100.2,97.5,97.8)};
        check("state failed on close beyond stop", TTFMEssence.signalState(true,100,98,sf,0,false)==2);
        TTFMEssence.RB[] st2={rb(10,100,100.6,99.4,100.2),rb(11,100.2,100.8,99.6,100.4)};
        check("state tighten when HTF closed w/o 1R", TTFMEssence.signalState(true,100,98,st2,0,true)==1);
        check("wick beyond stop != fail", TTFMEssence.signalState(true,100,98,new TTFMEssence.RB[]{rb(10,100,101,97.5,99.9)},0,false)!=2);

        // ---- sweep & wave ----
        double[] sw=TTFMEssence.detectSweep(cd(100,110,95,105,1,true),cd(105,108,93,106,2,true));
        check("detect bull sweep", sw!=null&&sw[0]==1&&sw[1]==95);
        sw=TTFMEssence.detectSweep(cd(100,110,95,105,1,true),cd(105,112,99,103,2,true));
        check("detect bear sweep", sw!=null&&sw[0]==0&&sw[1]==110);
        check("no sweep", TTFMEssence.detectSweep(cd(100,110,95,105,1,true),cd(105,109,96,108,2,true))==null);
        TTFMEssence.RB[] wb={rb(1,100,102,99,101),rb(2,101,100.5,99.5,100),rb(3,100,99.5,98.5,99),rb(4,99,99.2,98,98.5),rb(5,98.5,101,98,100.8)};
        int[] wv=TTFMEssence.findWave(wb,3,50,true,true);
        check("find bear wave span", wv[0]==1&&wv[1]==3);

        // ---- closures ----
        check("c2 sweep closure bull", TTFMEssence.c2SweepClosure(cd(100,110,95,105,1,true),cd(105,108,93,106,2,true),true));
        check("c2 closure needs POI", !TTFMEssence.c2Closure(cd(100,110,95,105,1,true),cd(105,108,93,106,2,true),true,false,0));
        check("c2 closure struct conflict", !TTFMEssence.c2Closure(cd(100,110,95,105,1,true),cd(105,108,93,106,2,true),true,true,-1));
        check("c3 closure bull over body", TTFMEssence.c3Closure(cd(100,108,96,104,2,true),cd(104,112,103,111,3,true),true));
        check("c3 rejects sweep", !TTFMEssence.c3Closure(cd(100,108,96,104,2,true),cd(104,112,95,111,3,true),true));
        eq("c2 wick mid big wick", TTFMEssence.c2WickMid(cd(100,120,80,102,1,true)), 100.0, 1e-9);
        check("c2 wick mid NaN small wick", Double.isNaN(TTFMEssence.c2WickMid(cd(100,104.5,99.5,104,1,true))));

        // ---- structure ----
        TTFMEssence.RB[] stb=new TTFMEssence.RB[8];
        double[] lows ={100,98,99,97,98,96,97,99};
        double[] highs={102,101,103,100,102,99,101,103};
        for (int i=0;i<8;i++) stb[i]=rb(i,lows[i]+1,highs[i],lows[i],lows[i]+1.5);
        int[] sst=TTFMEssence.structureState(stb,60);
        check("structure returns trend/event pair", sst.length==2);

        // ---- bucketing frozen TZ GMT+3 (locked TV match) ----
        long t=ms(8,5,0,ny);   // NY 05:00 summer = UTC 09:00 = GMT+3 12:00
        long ps=TTFMEssence.periodStart(t,4*3600000L,g3);
        check("4H bucket aligned GMT+3 noon", ps==t);
        long ps2=TTFMEssence.periodStart(t+3600000L,4*3600000L,g3);
        check("inside bucket floors to start", ps2==t);
        long dStart=TTFMEssence.periodStart(t,24*3600000L,g3);
        check("daily bucket = GMT+3 midnight", dStart==ms(8,0,0,g3));

        // ---- bucketing TZ = Europe/Athens (agreement 2026-09-09, TV match) ----
        TimeZone ath=TimeZone.getTimeZone("Europe/Athens");
        check("BUCKET_TZ locked to Europe/Athens", TTFMEssence.BUCKET_TZ.getID().equals("Europe/Athens"));
        // summer (EEST=UTC+3): identical to the old GMT+3 freeze - verifications stand
        long sepT=ymd(2026,Calendar.SEPTEMBER,8,10,30,ath);
        check("summer: Athens == GMT+3 4H bucket",
            TTFMEssence.periodStart(sepT,4*3600000L,ath)==TTFMEssence.periodStart(sepT,4*3600000L,g3));
        check("summer: Athens == GMT+3 daily bucket",
            TTFMEssence.periodStart(sepT,24*3600000L,ath)==TTFMEssence.periodStart(sepT,24*3600000L,g3));
        // winter (EET=UTC+2): 1h fix vs the old freeze - matches platform + TV
        long janT=ymd(2026,Calendar.JANUARY,15,10,30,ath);   // 08:30 UTC
        check("winter: 4H bucket = 08:00 Athens wall",
            TTFMEssence.periodStart(janT,4*3600000L,ath)==ymd(2026,Calendar.JANUARY,15,8,0,ath));
        check("winter: Athens 4H != GMT+3 4H (the 1h fix)",
            TTFMEssence.periodStart(janT,4*3600000L,ath)!=TTFMEssence.periodStart(janT,4*3600000L,g3));
        check("winter: daily = Athens midnight",
            TTFMEssence.periodStart(janT,24*3600000L,ath)==ymd(2026,Calendar.JANUARY,15,0,0,ath));
        // EU DST boundaries 2026: spring Mar-29 (23h day), fall Oct-25 (25h day)
        long marT=ymd(2026,Calendar.MARCH,29,10,30,ath);     // EEST by then
        check("spring-fwd day: 4H grid absolute-stable (06:00Z)",
            TTFMEssence.periodStart(marT,4*3600000L,ath)==ymd(2026,Calendar.MARCH,29,9,0,ath));
        long octT=ymd(2026,Calendar.OCTOBER,25,10,30,ath);   // EET by then
        check("fall-back day: 4H grid absolute-stable (05:00Z)",
            TTFMEssence.periodStart(octT,4*3600000L,ath)==ymd(2026,Calendar.OCTOBER,25,7,0,ath));
        // NY 08:00 open always stands 1h off the EET 4H grid (badge identifies it)
        long nySum=TTFMEssence.nyOpenMillis(ymd(2026,Calendar.JULY,15,12,0,ny),ny);
        check("summer NY open off 4H grid",
            TTFMEssence.periodStart(nySum,4*3600000L,ath)!=nySum);
        long nyWin=TTFMEssence.nyOpenMillis(ymd(2026,Calendar.JANUARY,15,12,0,ny),ny);
        check("winter NY open off 4H grid",
            TTFMEssence.periodStart(nyWin,4*3600000L,ath)!=nyWin);

        // ---- per-symbol grid lock (agreement 2026-09-09) ----
        check("grid constants", TTFMEssence.BUCKET_BRUSSELS.getID().equals("Europe/Brussels")
            && TTFMEssence.BUCKET_NY.getID().equals("America/New_York"));
        check("auto XAU/USD -> Brussels", TTFMEssence.autoGrid("XAU/USD")==TTFMEssence.BUCKET_BRUSSELS);
        check("auto XAU/EUR -> Brussels", TTFMEssence.autoGrid("XAU/EUR")==TTFMEssence.BUCKET_BRUSSELS);
        check("auto GOLD + case-insensitive", TTFMEssence.autoGrid("spot Gold")==TTFMEssence.BUCKET_BRUSSELS
            && TTFMEssence.autoGrid("xauusd")==TTFMEssence.BUCKET_BRUSSELS);
        check("auto USATECH.IDX -> EET", TTFMEssence.autoGrid("USATECH.IDX")==TTFMEssence.BUCKET_TZ);
        check("auto USA500/USA30 -> EET", TTFMEssence.autoGrid("USA500.IDX")==TTFMEssence.BUCKET_TZ
            && TTFMEssence.autoGrid("USA30.IDX")==TTFMEssence.BUCKET_TZ);
        check("auto EUR/USD + GBP/USD -> EET", TTFMEssence.autoGrid("EUR/USD")==TTFMEssence.BUCKET_TZ
            && TTFMEssence.autoGrid("GBP/USD")==TTFMEssence.BUCKET_TZ);
        check("auto unknown/null -> EET default", TTFMEssence.autoGrid("OIL.WTI")==TTFMEssence.BUCKET_TZ
            && TTFMEssence.autoGrid(null)==TTFMEssence.BUCKET_TZ);
        check("resolve manual EET/Brussels/NY",
            TTFMEssence.resolveGrid(1,"XAU/USD")==TTFMEssence.BUCKET_TZ
            && TTFMEssence.resolveGrid(2,"EUR/USD")==TTFMEssence.BUCKET_BRUSSELS
            && TTFMEssence.resolveGrid(3,"EUR/USD")==TTFMEssence.BUCKET_NY);
        check("resolve invalid -> auto", TTFMEssence.resolveGrid(9,"XAU/USD")==TTFMEssence.BUCKET_BRUSSELS);
        check("gridTag auto/manual", TTFMEssence.gridTag(0,"XAU/USD").equals("Brussels-auto")
            && TTFMEssence.gridTag(0,"EUR/USD").equals("EET-auto")
            && TTFMEssence.gridTag(2,"EUR/USD").equals("Brussels-manual")
            && TTFMEssence.gridTag(3,"XAU/USD").equals("NY-manual"));
        // Brussels bucket spot (Sept CEST=UTC+2): 10:30 -> 08:00 wall, 1h off EET
        TimeZone bru=TimeZone.getTimeZone("Europe/Brussels");
        long bT=ymd(2026,Calendar.SEPTEMBER,8,10,30,bru);   // 08:30 UTC
        check("brussels 4H bucket = 08:00 wall",
            TTFMEssence.periodStart(bT,4*3600000L,bru)==ymd(2026,Calendar.SEPTEMBER,8,8,0,bru));
        check("brussels 4H != EET 4H (1h apart)",
            TTFMEssence.periodStart(bT,4*3600000L,bru)!=TTFMEssence.periodStart(bT,4*3600000L,ath));
        // [Bucketing] Grid option idx17, default Auto, togglable
        TTFMEssence eg=new TTFMEssence();
        check("grid option default Auto", eg.gridMode==0);
        check("grid option exposed (idx17)",
            eg.getOptInputParameterInfo(17)!=null
            && eg.getOptInputParameterInfo(17).getName().equals("[Bucketing] Grid"));
        eg.setOptInputParameter(17, Integer.valueOf(2));
        check("grid option toggles Brussels", eg.gridMode==2);
        eg.setOptInputParameter(17, Integer.valueOf(0));
        check("grid option back to Auto", eg.gridMode==0);

        // ---- sessions ----
        check("session NY", TTFMEssence.sessionOf(ms(8,10,0,ny),ny).equals("NY"));
        check("session London", TTFMEssence.sessionOf(ms(8,3,0,ny),ny).equals("London"));
        check("session Asia blocked by default", !TTFMEssence.sessionAllowed("Asia",1));
        check("session NY allowed", TTFMEssence.sessionAllowed("NY",1));

        // ---- symbols ----
        check("shortSym USATECH", TTFMEssence.shortSym("USATECH.IDX/USD").equals("NQ"));
        check("shortSym USA500", TTFMEssence.shortSym("USA500.IDX/USD").equals("ES"));

        // ---- aggregation freezes ----
        TTFMEssence.LayerData L=new TTFMEssence.LayerData();
        L.candlesToShow=6;
        long b0=ms(8,9,0,g3);   // inside the GMT+3 08:00-12:00 bucket
        TTFMEssence.aggregate(L,rb(b0,100,105,99,103),4*3600000L,g3);
        TTFMEssence.aggregate(L,rb(b0+3600000L,103,107,102,106),4*3600000L,g3);
        check("aggregate same bucket updates current", L.historical.isEmpty()&&L.curH==107&&L.curC==106);
        TTFMEssence.aggregate(L,rb(b0+4*3600000L,106,110,105,109),4*3600000L,g3);
        check("aggregate rolls completed candle", L.historical.size()==1&&L.historical.get(0).completed&&L.historical.get(0).high==107);
        for (int k=1;k<=10;k++) TTFMEssence.aggregate(L,rb(b0+(4+k)*3600000L*4,100+k,110+k,99+k,105+k),4*3600000L,g3);
        check("historical capped", L.historical.size()<=L.candlesToShow+2);

        // ---- AUTO layers freeze 2026-09-08d ----
        int[] al=TTFMEssence.autoLayers(60000L);
        check("auto 1m -> 15m/1H", al[0]==0&&al[1]==2);
        al=TTFMEssence.autoLayers(300000L);
        check("auto 5m -> 1H/4H", al[0]==2&&al[1]==3);
        al=TTFMEssence.autoLayers(900000L);
        check("auto 15m -> 4H/D (old freeze)", al[0]==3&&al[1]==4);
        al=TTFMEssence.autoLayers(1800000L);
        check("auto 30m -> 4H/D (user fix)", al[0]==3&&al[1]==4);
        al=TTFMEssence.autoLayers(3600000L);
        check("auto 1H -> D/W", al[0]==4&&al[1]==6);
        al=TTFMEssence.autoLayers(14400000L);
        check("auto 4H -> W/MN", al[0]==6&&al[1]==7);
        al=TTFMEssence.autoLayers(86400000L);
        check("auto D -> MN/MN", al[0]==7&&al[1]==7);
        al=TTFMEssence.autoLayers(180000L);
        check("auto 3m floors to 1m map", al[0]==0&&al[1]==2);
        al=TTFMEssence.autoLayers(7200000L);
        check("auto 2H floors to 1H map", al[0]==4&&al[1]==6);
        al=TTFMEssence.autoLayers(15000L);
        check("auto 15s floors to 1m map", al[0]==0&&al[1]==2);
        check("col pattern intraday", TTFMEssence.colRangePattern(900000L).equals("HH:mm"));
        check("col pattern daily", TTFMEssence.colRangePattern(86400000L).equals("HH:mm"));
        check("col pattern week", TTFMEssence.colRangePattern(TTFMEssence.W1I).equals("dd/MM"));
        // calendar week bucket: Mon 2026-09-07 00:00 GMT+3
        long tue=ms(8,10,0,g3);
        check("week bucket = Monday 00:00", TTFMEssence.periodStart(tue,TTFMEssence.W1I,g3)==ms(7,0,0,g3));
        check("week bucket stable inside week", TTFMEssence.periodStart(ms(11,23,0,g3),TTFMEssence.W1I,g3)==ms(7,0,0,g3));
        // calendar month bucket: 2026-09-01 00:00 GMT+3
        check("month bucket = 1st 00:00", TTFMEssence.periodStart(tue,TTFMEssence.MN1I,g3)==ms(1,0,0,g3));
        check("month bucket stable inside month", TTFMEssence.periodStart(ms(28,5,0,g3),TTFMEssence.MN1I,g3)==ms(1,0,0,g3));

        // ---- CISD Desk option group (freeze 2026-09-08e): 15 options, [CISD] prefix ----
        TTFMEssence es=new TTFMEssence();
        check("option default = Medium", es.cisdSensitivity==1);
        check("minWaveFor mapping verbatim", TTFMEssence.minWaveFor(0)==3&&TTFMEssence.minWaveFor(1)==2&&TTFMEssence.minWaveFor(2)==1);
        es.setOptInputParameter(1, Integer.valueOf(0)); // idx1 = [CISD] Min Wave Length
        check("option Low stored raw 0 -> min 3", es.cisdSensitivity==0&&TTFMEssence.minWaveFor(es.cisdSensitivity)==3);
        es.setOptInputParameter(1, Integer.valueOf(2));
        check("option High stored raw 2 -> min 1", es.cisdSensitivity==2&&TTFMEssence.minWaveFor(es.cisdSensitivity)==1);
        check("option group exposed (22 opts, idx0 Detection, idx1 MinWave)",
            es.getOptInputParameterInfo(0)!=null && es.getOptInputParameterInfo(1)!=null
            && es.getOptInputParameterInfo(17)!=null && es.getOptInputParameterInfo(18)!=null
            && es.getOptInputParameterInfo(19)!=null && es.getOptInputParameterInfo(20)!=null
            && es.getOptInputParameterInfo(21)!=null && es.getOptInputParameterInfo(22)==null
            && es.getOptInputParameterInfo(0).getName().equals("[CISD] Detection")
            && es.getOptInputParameterInfo(1).getName().equals("[CISD] Min Wave Length")
            && es.getOptInputParameterInfo(18).getName().equals("[SMT] Detection")
            && es.getOptInputParameterInfo(19).getName().equals("[Display] Show Timer")
            && es.getOptInputParameterInfo(20).getName().equals("[HTF] FVG on Chart Candles")
            && es.getOptInputParameterInfo(21).getName().equals("[HTF] Show PDH/PDL"));

        // ---- [HTF] FVG + PDH/PDL (agreement 2026-09-11) ----
        check("HTF option defaults ON", es.fvgOnChart && es.showPdhPdl);
        TTFMEssence es2=new TTFMEssence();
        es2.setOptInputParameter(20, Integer.valueOf(0));
        es2.setOptInputParameter(21, Integer.valueOf(0));
        check("HTF options toggle off", !es2.fvgOnChart && !es2.showPdhPdl);
        // FVG detection (pure): none / bull / bear+time / filled / max cap
        TTFMEssence.RB[] f0={ rb(0,1.00,1.10,0.95,1.05), rb(15,1.05,1.15,1.00,1.10), rb(30,1.10,1.20,1.02,1.15) };
        check("FVG none when no gap", TTFMEssence.detectHtfFvgZones(f0,40,6).isEmpty());
        TTFMEssence.RB[] f1={ rb(0,1.00,1.10,0.95,1.05), rb(15,1.05,1.15,1.00,1.08), rb(30,1.08,1.30,1.12,1.25) };
        java.util.List<double[]> z1=TTFMEssence.detectHtfFvgZones(f1,40,6);
        check("FVG bull detected, bounds+time", z1.size()==1 && z1.get(0)[0]==1.10 && z1.get(0)[1]==1.12
            && z1.get(0)[2]==15 && z1.get(0)[3]==1 && z1.get(0)[4]==0);
        TTFMEssence.RB[] f2={ rb(0,1.00,1.10,0.95,1.05), rb(15,1.05,1.15,1.00,1.08),
                              rb(30,1.08,1.30,1.12,1.25), rb(45,1.20,1.22,1.05,1.06) };
        java.util.List<double[]> z2=TTFMEssence.detectHtfFvgZones(f2,40,6);
        check("FVG bull filled by close beyond far edge", z2.size()==1 && z2.get(0)[4]==1);
        TTFMEssence.RB[] f3={ rb(0,1.20,1.25,1.18,1.22), rb(15,1.22,1.24,1.12,1.14), rb(30,1.14,1.10,1.00,1.05) };
        java.util.List<double[]> z3=TTFMEssence.detectHtfFvgZones(f3,40,6);
        check("FVG bear detected, bounds+time", z3.size()==1 && z3.get(0)[3]==0 && z3.get(0)[0]==1.10
            && z3.get(0)[1]==1.18 && z3.get(0)[2]==15);
        TTFMEssence.RB[] f4=new TTFMEssence.RB[40];
        for (int i=0;i<40;i++){ double b=10*i; f4[i]=rb(1000L*i,b+1,b+4,b,b+2); }
        java.util.List<double[]> z4=TTFMEssence.detectHtfFvgZones(f4,40,6);
        check("FVG max cap 6", z4.size()==6);
        // PDH/PDL pick (pure): last COMPLETED D candle + current model-day start
        TTFMEssence.LayerData d1=new TTFMEssence.LayerData();
        d1.enabled=true; d1.periodIndex=4; // D layer
        d1.historical.add(cd(1.00,1.05,0.98,1.02,1000,true));
        d1.historical.add(cd(1.02,1.09,1.01,1.04,9000000,true)); // last completed: hi 1.09 / lo 1.01
        d1.curActive=true; d1.curStart=18000000;
        double[] pp1=TTFMEssence.pdhPdlOf(d1);
        check("PDH/PDL = last completed D candle", pp1[0]==1.09 && pp1[1]==1.01);
        check("PDH/PDL dayStart = curStart while active", pp1[2]==18000000L);
        d1.curActive=false; d1.curStart=0;
        double[] pp2=TTFMEssence.pdhPdlOf(d1);
        check("PDH/PDL dayStart = last completed + 1 day when idle", pp2[2]==9000000L+86400000L);
        TTFMEssence.LayerData dEmpty=new TTFMEssence.LayerData();
        dEmpty.enabled=true; dEmpty.periodIndex=4;
        check("PDH/PDL null on empty layer", TTFMEssence.pdhPdlOf(dEmpty)==null);

        // ---- Desk: Grade presets (reference applyCisdGrade) ----
        TTFMEssence g1=new TTFMEssence();
        g1.applyCisdGrade(1);
        check("grade Premium presets", g1.trendFilter==1&&g1.minRetracement==1&&g1.marketStructureFilter==1
            &&g1.higherTFConfirmation==1&&g1.momentumFilter==1&&!g1.volumeFilterEnabled);
        TTFMEssence g2=new TTFMEssence();
        g2.applyCisdGrade(2);
        check("grade Ultimate presets", g2.trendFilter==2&&g2.minRetracement==3&&g2.marketStructureFilter==1
            &&g2.higherTFConfirmation==1&&g2.momentumFilter==2&&g2.volumeFilterEnabled);
        TTFMEssence g0=new TTFMEssence();
        g0.trendFilter=2; g0.applyCisdGrade(0);
        check("grade Standard keeps manual", g0.trendFilter==2);

        // ---- Desk: eligibility math (reference isSignalEligible) ----
        check("grade0 = AND of actives",
            TTFMEssence.isSignalEligible(0,true,true,true,true,false, false,false,false,false,true)==false
            && TTFMEssence.isSignalEligible(0,true,true,true,true,true, true,true,true,true,true)==true);
        check("grade1 ratio 3/5=0.60 passes, 2/5 fails",
            TTFMEssence.isSignalEligible(1,true,true,true,false,false, true,true,true,true,true)==true
            && TTFMEssence.isSignalEligible(1,true,true,false,false,false, true,true,true,true,true)==false);
        check("grade2 ratio 4/5=0.80 passes, 3/5 fails",
            TTFMEssence.isSignalEligible(2,true,true,true,true,false, true,true,true,true,true)==true
            && TTFMEssence.isSignalEligible(2,true,true,true,false,false, true,true,true,true,true)==false);
        check("no active filters -> eligible", TTFMEssence.isSignalEligible(2,false,false,false,false,false, false,false,false,false,false)==true);
        check("inactive filters always pass (grade0)",
            TTFMEssence.isSignalEligible(0,true,true,true,true,true, false,false,false,false,false)==true);

        // ---- Desk: trend filter EMA (reference passTrendFilter/getEMA) ----
        TTFMEssence.RB[] flat=new TTFMEssence.RB[60];
        for (int i=0;i<60;i++) flat[i]=rb(100+i*60000L,100,101,99,100,1000);
        check("EMA flat series = value", Math.abs(TTFMEssence.getEMA(flat,59,50)-100.0)<1e-9);
        flat[59]=rb(100+59*60000L,100,101,99,105,1000);
        check("trend bullish: close above EMA50", TTFMEssence.passTrendFilter(flat,59,true,1)==true);
        flat[59]=rb(100+59*60000L,100,101,99,95,1000);
        check("trend bullish: close below EMA50 fails", TTFMEssence.passTrendFilter(flat,59,true,1)==false);
        check("trend off always passes", TTFMEssence.passTrendFilter(flat,59,true,0)==true);

        // ---- Desk: momentum + volume (reference passMomentumAndVolumeFilters) ----
        TTFMEssence.RB[] mv=new TTFMEssence.RB[25];
        for (int i=0;i<24;i++) mv[i]=rb(i*60000L,100,101,99,100,1000);
        mv[24]=rb(24*60000L,100,106,99.5,105.5,2000); // big bar, closes in upper half
        check("momentum Low: close in own half", TTFMEssence.passMomentumAndVolumeFilters(mv,24,true,1,false,1.2)==true);
        check("momentum Medium: range>=1.2x avg(prev5)", TTFMEssence.passMomentumAndVolumeFilters(mv,24,true,2,false,1.2)==true);
        check("volume: 2000 >= 1.2x1000", TTFMEssence.passMomentumAndVolumeFilters(mv,24,true,0,true,1.2)==true);
        mv[24]=rb(24*60000L,100,106,99.5,105.5,100); // low volume
        check("volume fails when below ratio", TTFMEssence.passMomentumAndVolumeFilters(mv,24,true,0,true,1.2)==false);
        mv[24]=rb(24*60000L,100,100.8,99.5,99.7,2000); // closes in lower half
        check("momentum Low fails on wrong half", TTFMEssence.passMomentumAndVolumeFilters(mv,24,true,1,false,1.2)==false);

        // ---- Desk: market structure (reference checkMarketStructure) ----
        TTFMEssence.RB[] ms=new TTFMEssence.RB[10];
        for (int i=0;i<10;i++) ms[i]=rb(i*60000L,100,101,99,100,1000);
        ms[2]=rb(2*60000L,100,101,97,100,1000);  // pivot low A
        ms[6]=rb(6*60000L,100,101,98,100,1000);  // pivot low B higher than A
        check("MS bullish: higher lows", TTFMEssence.checkMarketStructure(ms,true)==true);
        check("MS bearish: needs lower highs -> false here", TTFMEssence.checkMarketStructure(ms,false)==false);

        // ---- Desk: storage ring MAX 3 + shift (reference storeCisdSignal) ----
        TTFMEssence ring=new TTFMEssence();
        ring.cisdAlertSound="None"; ring.cisdRetestSound="None"; ring.sharedCISDAlerts=false; ring.saveLoadCISD=false;
        for (int k=1;k<=4;k++)
            ring.storeCisdSignal(1000L*k,2000L*k,100.0+k,99.0+k,true,2000L*k,false,false,null,false,"",true,true,true,true,true,true,true,true);
        check("ring caps at 3", ring.cisdStoredCount==3);
        check("ring shifted out oldest (idx0 = signal#2)", ring.cisdStoredStartTimes[0]==2000L&&ring.cisdStoredLevels[0]==102.0);
        check("ring newest at idx count-1", ring.cisdStoredStartTimes[2]==4000L&&ring.cisdStoredLevels[2]==104.0);
        check("ring flags reset on store", ring.cisdStoredActivationTime[2]==0
            &&!ring.cisdStoredLogged[2]&&!ring.cisdStoredRetestPlayed[2]);
        ring.storeCisdSignal(4000L,2000L*4,104.0,103.0,true,8000L,false,false,null,false,"",true,true,true,true,true,true,true,true);
        check("duplicate not stored twice", ring.cisdStoredCount==3&&ring.cisdStoredEndTimes[2]==8000L);

        // ---- session overlay (agreement 2026-09-09): NY 08:00 DST-safe, no fixed offset ----
        TimeZone utc=TimeZone.getTimeZone("UTC");
        TimeZone eet=TimeZone.getTimeZone("GMT+2:00");   // EET winter (fixed)
        TimeZone eest=TimeZone.getTimeZone("GMT+3:00");  // EEST summer (fixed)
        // winter (EST): NY 08:00 = 13:00 UTC = 15:00 EET
        long wOpen=TTFMEssence.nyOpenMillis(ymd(2026,Calendar.JANUARY,15,10,30,ny),ny);
        check("nyOpen winter exact", wOpen==ymd(2026,Calendar.JANUARY,15,8,0,ny));
        check("nyOpen winter = 13:00 UTC", hourIn(utc,wOpen)==13);
        check("nyOpen winter = 15:00 EET", hourIn(eet,wOpen)==15);
        // summer (EDT): NY 08:00 = 12:00 UTC = 15:00 EEST
        long sOpen=TTFMEssence.nyOpenMillis(ymd(2026,Calendar.JULY,15,20,0,ny),ny);
        check("nyOpen summer exact", sOpen==ymd(2026,Calendar.JULY,15,8,0,ny));
        check("nyOpen summer = 12:00 UTC", hourIn(utc,sOpen)==12);
        check("nyOpen summer = 15:00 EEST", hourIn(eest,sOpen)==15);
        // US spring-forward 2026-03-08 (EU still standard until 03-29): gap week
        long gOpen=TTFMEssence.nyOpenMillis(ymd(2026,Calendar.MARCH,10,14,0,ny),ny);
        check("nyOpen gap-week March exact", gOpen==ymd(2026,Calendar.MARCH,10,8,0,ny));
        check("nyOpen gap-week March = 12:00 UTC (no 1h drift)", hourIn(utc,gOpen)==12);
        // EU back to standard 10-25, US still DST until 11-01: gap week again
        long oOpen=TTFMEssence.nyOpenMillis(ymd(2026,Calendar.OCTOBER,27,9,0,ny),ny);
        check("nyOpen gap-week Oct exact", oOpen==ymd(2026,Calendar.OCTOBER,27,8,0,ny));
        check("nyOpen gap-week Oct = 12:00 UTC", hourIn(utc,oOpen)==12);
        long nOpen=TTFMEssence.nyOpenMillis(ymd(2026,Calendar.NOVEMBER,3,9,0,ny),ny);
        check("nyOpen Nov standard = 13:00 UTC", hourIn(utc,nOpen)==13);
        // stepping: exactly one NY day across DST switches (23h spring / 25h fall)
        long fri=TTFMEssence.nyOpenMillis(ymd(2026,Calendar.MARCH,6,12,0,ny),ny);   // Fri
        long sat=TTFMEssence.nextNyOpen(fri,ny);
        long sun=TTFMEssence.nextNyOpen(sat,ny);   // Sun 03-08 spring-forward day
        long mon=TTFMEssence.nextNyOpen(sun,ny);
        check("nextNyOpen Sat after Fri", sat==ymd(2026,Calendar.MARCH,7,8,0,ny));
        check("nextNyOpen Sun after Sat", sun==ymd(2026,Calendar.MARCH,8,8,0,ny));
        check("nextNyOpen spring gap = 23h", (sun-sat)==23*3600000L);
        check("nextNyOpen Mon after Sun", mon==ymd(2026,Calendar.MARCH,9,8,0,ny));
        check("nextNyOpen back to 24h", (mon-sun)==24*3600000L);
        long fSat=TTFMEssence.nyOpenMillis(ymd(2026,Calendar.OCTOBER,31,12,0,ny),ny);
        long fSun=TTFMEssence.nextNyOpen(fSat,ny);   // Sun 11-01 fall-back day
        check("nextNyOpen fall gap = 25h", (fSun-fSat)==25*3600000L);
        // weekend skip
        check("Sat skipped", TTFMEssence.isNyWeekend(ymd(2026,Calendar.SEPTEMBER,5,8,0,ny),ny));
        check("Sun skipped", TTFMEssence.isNyWeekend(ymd(2026,Calendar.SEPTEMBER,6,8,0,ny),ny));
        check("Mon drawn", !TTFMEssence.isNyWeekend(ymd(2026,Calendar.SEPTEMBER,7,8,0,ny),ny));
        check("Fri drawn", !TTFMEssence.isNyWeekend(ymd(2026,Calendar.SEPTEMBER,4,8,0,ny),ny));
        // panel session samples (sessionOf on last bar)
        check("panel Asia", TTFMEssence.sessionOf(ms(8,20,0,ny),ny).equals("Asia"));
        check("panel Closed", TTFMEssence.sessionOf(ms(8,17,30,ny),ny).equals("Closed"));
        // overlay options: OFF by default, togglable, appended after CISD group
        check("session overlay defaults OFF", !es.showNyOpenLine&&!es.showSessionInPanel);
        check("session overlay options exposed (idx15/16)",
            es.getOptInputParameterInfo(15)!=null && es.getOptInputParameterInfo(16)!=null
            && es.getOptInputParameterInfo(15).getName().equals("[Sessions] Show NY Open Line")
            && es.getOptInputParameterInfo(16).getName().equals("[Sessions] Show Session in Panel"));
        es.setOptInputParameter(15, Integer.valueOf(1));
        es.setOptInputParameter(16, Integer.valueOf(1));
        check("session overlay toggles ON", es.showNyOpenLine&&es.showSessionInPanel);
        es.setOptInputParameter(15, Integer.valueOf(0));
        es.setOptInputParameter(16, Integer.valueOf(0));
        check("session overlay toggles OFF", !es.showNyOpenLine&&!es.showSessionInPanel);

        // ---- snapToBar: exact bar time for getXForTime (fix 2026-09-09) ----
        long[] sbt={1000L,2000L,3000L,5000L};
        check("snap exact hit", TTFMEssence.snapToBar(sbt,3000L)==3000L);
        check("snap mid-bar floors", TTFMEssence.snapToBar(sbt,4500L)==3000L);
        check("snap before first -> -1", TTFMEssence.snapToBar(sbt,500L)==-1);
        check("snap after last -> last", TTFMEssence.snapToBar(sbt,9999L)==5000L);
        check("snap null/empty -> -1", TTFMEssence.snapToBar(null,1000L)==-1&&TTFMEssence.snapToBar(new long[0],1000L)==-1);

        // ---- regression 2026-09-08: 1H slot was 60h (60*60*60*1000) since v1 ----
        check("PERIOD table exact", java.util.Arrays.equals(TTFMEssence.PERIOD_INTERVALS,
            new long[]{900000L,1800000L,3600000L,14400000L,86400000L,25200000L,604800000L,2592000000L}));

        // ---- SMT ENGINE (agreement 2026-09-10): keys/peers, pivots, verdict, parse ----
        // keys: XAU first (XAUEUR contains EUR), trio, majors, unknown
        check("smtKey NQ", TTFMEssence.smtKey("USATECH.IDX/USD").equals("NQ"));
        check("smtKey ES", TTFMEssence.smtKey("USA500.IDX/USD").equals("ES"));
        check("smtKey YM", TTFMEssence.smtKey("USA30.IDX/USD").equals("YM"));
        check("smtKey EU", TTFMEssence.smtKey("EUR/USD").equals("EU"));
        check("smtKey GU", TTFMEssence.smtKey("GBP/USD").equals("GU"));
        check("smtKey XAU/USD -> XAUUSD", TTFMEssence.smtKey("XAU/USD").equals("XAUUSD"));
        check("smtKey XAU/EUR -> XAUEUR (XAU before EUR check)", TTFMEssence.smtKey("XAU/EUR").equals("XAUEUR"));
        check("smtKey EUR/GBP -> unknown", TTFMEssence.smtKey("EUR/GBP").equals(""));
        check("smtKey oil -> unknown", TTFMEssence.smtKey("OIL.WTI").equals(""));
        check("smtKey null -> empty", TTFMEssence.smtKey(null).equals(""));
        // peers: trio all three, EU/GU pair, gold pair, unknown -> none
        check("smtPeers NQ trio", java.util.Arrays.equals(TTFMEssence.smtPeers("NQ"), new String[]{"ES","YM"}));
        check("smtPeers ES trio", java.util.Arrays.equals(TTFMEssence.smtPeers("ES"), new String[]{"NQ","YM"}));
        check("smtPeers YM trio", java.util.Arrays.equals(TTFMEssence.smtPeers("YM"), new String[]{"NQ","ES"}));
        check("smtPeers EU<->GU", java.util.Arrays.equals(TTFMEssence.smtPeers("EU"), new String[]{"GU"})
            && java.util.Arrays.equals(TTFMEssence.smtPeers("GU"), new String[]{"EU"}));
        check("smtPeers gold pair", java.util.Arrays.equals(TTFMEssence.smtPeers("XAUUSD"), new String[]{"XAUEUR"})
            && java.util.Arrays.equals(TTFMEssence.smtPeers("XAUEUR"), new String[]{"XAUUSD"}));
        check("smtPeers unknown empty", TTFMEssence.smtPeers("").length==0);
        // experimental: gold pair only
        check("smtExperimental XAU only", TTFMEssence.smtExperimental("XAUUSD")&&TTFMEssence.smtExperimental("XAUEUR")
            &&!TTFMEssence.smtExperimental("ES")&&!TTFMEssence.smtExperimental("EU")&&!TTFMEssence.smtExperimental(null));
        // pivots: K=2 fractal on confirmed buckets; series has H@3, L@6, H@8, L@10
        long[] st=new long[13]; double[] sh=new double[13], sl=new double[13];
        double[] hArr={10,15,25,30,20,12,10,18,22,14,6,10,16};
        double[] lArr={5,8,12,15,8,6,4,8,10,6,2,5,8};
        for (int i=0;i<13;i++){ st[i]=i*1000L; sh[i]=hArr[i]; sl[i]=lArr[i]; }
        List<TTFMEssence.SmtPivot> pv=TTFMEssence.smtPivots(st,sh,sl);
        check("pivots count = 4 (confirmed only)", pv.size()==4);
        check("pivots order H3,L6,H8,L10",
            pv.get(0).high&&pv.get(0).t==3000L&&pv.get(0).p==30.0
            &&!pv.get(1).high&&pv.get(1).t==6000L&&pv.get(1).p==4.0
            &&pv.get(2).high&&pv.get(2).t==8000L&&pv.get(2).p==22.0
            &&!pv.get(3).high&&pv.get(3).t==10000L&&pv.get(3).p==2.0);
        check("pivots never use unconfirmed edges", pv.get(0).t>=2000L&&pv.get(3).t<=10000L);
        check("pivots short series -> none", TTFMEssence.smtPivots(new long[]{0,1,2},new double[]{2,3,4},new double[]{1,2,3}).isEmpty());
        // last-two per side
        TTFMEssence.SmtPivot[] ph=TTFMEssence.smtLastTwo(pv,true), pl=TTFMEssence.smtLastTwo(pv,false);
        check("lastTwo highs {3,8}", ph!=null&&ph[0].t==3000L&&ph[1].t==8000L);
        check("lastTwo lows {6,10}", pl!=null&&pl[0].t==6000L&&pl[1].t==10000L);
        check("lastTwo single side -> null", TTFMEssence.smtLastTwo(pv,true)!=null
            &&TTFMEssence.smtLastTwo(java.util.Collections.<TTFMEssence.SmtPivot>emptyList(),true)==null);
        // verdict: highs divergence (peer made higher high, own did not) -> -1
        check("verdict highs divergence bear", TTFMEssence.smtVerdictSide(
            pv, peerPivots(true,3000,30,8000,35), true, 1000L, 10000L)==-1);
        check("verdict lows divergence bull", TTFMEssence.smtVerdictSide(
            pv, peerPivots(false,6000,4,10000,5), false, 1000L, 10000L)==1);
        check("verdict both took = agreement 0", TTFMEssence.smtVerdictSide(
            pv, peerPivots(false,6000,4,10000,1), false, 1000L, 10000L)==0);
        check("verdict base misaligned >2 bars -> 0", TTFMEssence.smtVerdictSide(
            pv, peerPivots(true,6000,30,11000,35), true, 1000L, 10000L)==0);
        check("verdict taker stale >3 bars -> 0", TTFMEssence.smtVerdictSide(
            pv, peerPivots(true,3000,30,8000,35), true, 1000L, 10000L+4*1000L)==0);
        check("verdict missing peer pivots -> 0", TTFMEssence.smtVerdictSide(pv, null, true, 1000L, 10000L)==0);
        // parse: same-anchor only, corrupt lines ignored, time-ascending
        List<String[]> lines=new ArrayList<>();
        lines.add(new String[]{"ES","H","8","35.0","1000"});
        lines.add(new String[]{"ES","H","3","30.0","1000"});
        lines.add(new String[]{"ES","L","6","4.0","1000"});
        lines.add(new String[]{"ES","H","9","99.0","2000"});   // different anchor -> dropped
        lines.add(new String[]{"ES","X","1","1.0","1000"});    // bad side -> dropped
        lines.add(new String[]{"NQ","H","1","9.0","1000"});    // other key -> dropped
        lines.add(new String[]{"ES","H","2","abc","1000"});    // bad price -> dropped
        List<TTFMEssence.SmtPivot> pp=TTFMEssence.smtParsePeer(lines,"ES",1000L);
        check("parse keeps 3 valid same-anchor pivots", pp.size()==3
            &&pp.get(0).high&&pp.get(0).t==3&&!pp.get(1).high&&pp.get(1).t==6&&pp.get(2).high&&pp.get(2).t==8);
        check("parse time-ascending", TTFMEssence.smtParsePeer(lines,"ES",1000L).get(0).t==3);
        check("parse anchor mismatch -> empty", TTFMEssence.smtParsePeer(lines,"ES",9999L).isEmpty());
        check("parse format roundtrip", TTFMEssence.smtFormatLine("NQ",true,3000L,30.0,1000L).equals("NQ,H,3000,30.0,1000"));
        // option: default OFF, exposed at idx18, togglable
        check("smt option default OFF", !es.showSMT);
        es.setOptInputParameter(18, Integer.valueOf(1));
        check("smt option toggles ON", es.showSMT);
        es.setOptInputParameter(18, Integer.valueOf(0));
        check("smt option toggles OFF", !es.showSMT);

        // ---- countdown timer (agreement 2026-09-10, D6 wall clock, default ON) ----
        check("timer format 01:02:18", TTFMEssence.countdownText(3738000L).equals("01:02:18"));
        check("timer format 00:59:59", TTFMEssence.countdownText(3599000L).equals("00:59:59"));
        check("timer >24h (weekend D) = 25:00:00", TTFMEssence.countdownText(25*3600000L).equals("25:00:00"));
        check("timer zero -> empty (not drawn)", TTFMEssence.countdownText(0).equals(""));
        check("timer negative (replay) -> empty (not drawn)", TTFMEssence.countdownText(-5000L).equals(""));
        check("timer option default ON + exposed idx19", es.showTimer
            && es.getOptInputParameterInfo(19)!=null
            && es.getOptInputParameterInfo(19).getName().equals("[Display] Show Timer"));
        es.setOptInputParameter(19, Integer.valueOf(0));
        check("timer option toggles OFF", !es.showTimer);
        es.setOptInputParameter(19, Integer.valueOf(1));
        check("timer option toggles ON", es.showTimer);
        System.out.println(pass+"/"+(pass+fail)+" PASS");
        if (fail>0) System.exit(1);
    }
}
