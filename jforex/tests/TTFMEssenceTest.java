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
    static TTFMEssence.CandleData cd(double o,double h,double l,double c,long t,boolean done){
        return new TTFMEssence.CandleData(o,h,l,c,t,done);
    }
    static long ms(int day,int hour,int min,TimeZone tz){
        Calendar c=Calendar.getInstance(tz);
        c.set(2026,Calendar.SEPTEMBER,day,hour,min,0); c.set(Calendar.MILLISECOND,0);
        return c.getTimeInMillis();
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

        // ---- the ONE agreed option: [CISD] Min Wave Length ----
        TTFMEssence es=new TTFMEssence();
        check("option default = Medium", es.cisdSensitivity==1);
        check("minWaveFor mapping verbatim", TTFMEssence.minWaveFor(0)==3&&TTFMEssence.minWaveFor(1)==2&&TTFMEssence.minWaveFor(2)==1);
        es.setOptInputParameter(0, Integer.valueOf(0));
        check("option Low stored raw 0 -> min 3", es.cisdSensitivity==0&&TTFMEssence.minWaveFor(es.cisdSensitivity)==3);
        es.setOptInputParameter(0, Integer.valueOf(2));
        check("option High stored raw 2 -> min 1", es.cisdSensitivity==2&&TTFMEssence.minWaveFor(es.cisdSensitivity)==1);
        check("option info exposed", es.getOptInputParameterInfo(0)!=null && es.getOptInputParameterInfo(1)==null);

        System.out.println(pass+"/"+(pass+fail)+" PASS");
        if (fail>0) System.exit(1);
    }
}
