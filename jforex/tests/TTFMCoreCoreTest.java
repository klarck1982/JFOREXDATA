package com.dukascopy.indicators;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Unit tests for the platform-free core of TTFMCore.
 * Proves our engine reproduces the EXACT formulas of the reference
 * HigherTFCandles.java ([J]) so the two sources agree by construction.
 * Run:  javac -encoding UTF-8 -d out -sourcepath stubs TTFMCore.java tests/... ; java ...
 */
public class TTFMCoreCoreTest {

    static int pass=0, fail=0;
    static void check(String name, boolean cond){
        if (cond){ pass++; System.out.println("  PASS  "+name); }
        else     { fail++; System.out.println("  FAIL  "+name); }
    }
    static void eq(String name, long a, long b){ check(name+"  ("+a+" == "+b+")", a==b); }
    static void eqd(String name, double a, double b){ check(name+"  ("+a+" == "+b+")", Math.abs(a-b)<1e-9); }

    static long mk(TimeZone tz, int y,int mo,int d,int h,int mi){
        Calendar c=Calendar.getInstance(tz);
        c.set(y,mo-1,d,h,mi,0); c.set(Calendar.MILLISECOND,0);
        return c.getTimeInMillis();
    }
    static TTFMCore.RB bar(long t,double o,double h,double l,double c){ return new TTFMCore.RB(t,o,h,l,c,1); }

    public static void main(String[] args){
        TimeZone g3 = TimeZone.getTimeZone("GMT+3:00");
        TimeZone ny = TimeZone.getTimeZone("America/New_York");
        long H=3600000L, D=24*H, H7=7*H;

        System.out.println("== periodStart [J] ==");
        long t = mk(g3,2026,9,6,9,35);
        eq("7H buckets to 07:00",   TTFMCore.periodStart(t,H7,g3), mk(g3,2026,9,6,7,0));
        eq("7H buckets 00:xx->00",  TTFMCore.periodStart(mk(g3,2026,9,6,3,10),H7,g3), mk(g3,2026,9,6,0,0));
        eq("7H buckets 21:xx->21",  TTFMCore.periodStart(mk(g3,2026,9,6,23,10),H7,g3), mk(g3,2026,9,6,21,0));
        eq("Daily -> midnight",     TTFMCore.periodStart(t,D,g3),  mk(g3,2026,9,6,0,0));
        eq("4H aligns 08:00",       TTFMCore.periodStart(t,4*H,g3),mk(g3,2026,9,6,8,0));
        eq("15m aligns 09:30",      TTFMCore.periodStart(t,15*60000L,g3), mk(g3,2026,9,6,9,30));

        System.out.println("== periodStart session anchors [J] ==");
        eq("A17 daily: 10:00 NY -> prev 17:00", TTFMCore.periodStart(mk(ny,2026,9,6,10,0),D,g3,1), mk(ny,2026,9,5,17,0));
        eq("A17 daily: 18:00 NY -> same 17:00", TTFMCore.periodStart(mk(ny,2026,9,6,18,0),D,g3,1), mk(ny,2026,9,6,17,0));
        eq("A17 4H: 20:30 NY -> 17:00",         TTFMCore.periodStart(mk(ny,2026,9,6,20,30),4*H,g3,1), mk(ny,2026,9,6,17,0));
        eq("A17 4H: 00:30 NY -> prev 21:00",    TTFMCore.periodStart(mk(ny,2026,9,6,0,30),4*H,g3,1), mk(ny,2026,9,5,21,0));
        eq("A17 winter daily: 16:30 EST -> prev 17:00 EST", TTFMCore.periodStart(mk(ny,2026,1,6,16,30),D,g3,1), mk(ny,2026,1,5,17,0));
        eq("A18 4H: 17:30 NY -> 14:00",         TTFMCore.periodStart(mk(ny,2026,9,6,17,30),4*H,g3,2), mk(ny,2026,9,6,14,0));
        eq("A18 daily: 17:30 NY -> prev 18:00", TTFMCore.periodStart(mk(ny,2026,9,6,17,30),D,g3,2), mk(ny,2026,9,5,18,0));
        eq("Anchor0 == legacy midnight",        TTFMCore.periodStart(t,4*H,g3,0), TTFMCore.periodStart(t,4*H,g3));

        System.out.println("== Next-Day Model bias [official article] ==");
        TTFMCore.CandleData prevC=new TTFMCore.CandleData(100,110,90,105,0,true);
        int[] r1=TTFMCore.ndmBias(new TTFMCore.CandleData(105,115,104,112,0,true),prevC);
        check("NDM close above prev high -> bull continuation", r1[0]==1&&r1[1]==0);
        int[] r2=TTFMCore.ndmBias(new TTFMCore.CandleData(95,95,87,88,0,true),prevC);
        check("NDM close below prev low -> bear continuation", r2[0]==-1&&r2[1]==0);
        int[] r3=TTFMCore.ndmBias(new TTFMCore.CandleData(100,112,100,106,0,true),prevC);
        check("NDM sweep high close inside -> bear reversal", r3[0]==-1&&r3[1]==1);
        int[] r4=TTFMCore.ndmBias(new TTFMCore.CandleData(100,100,88,95,0,true),prevC);
        check("NDM sweep low close inside -> bull reversal", r4[0]==1&&r4[1]==1);
        int[] r5=TTFMCore.ndmBias(new TTFMCore.CandleData(100,105,95,100,0,true),prevC);
        check("NDM inside no sweep -> neutral", r5[0]==0&&r5[1]==0);

        System.out.println("== sessionOf [J] (NY TZ) ==");
        check("18:00 -> Asia",   "Asia".equals(TTFMCore.sessionOf(mk(ny,2026,9,6,18,0),ny)));
        check("00:30 -> Asia",   "Asia".equals(TTFMCore.sessionOf(mk(ny,2026,9,6,0,30),ny)));
        check("01:00 -> London", "London".equals(TTFMCore.sessionOf(mk(ny,2026,9,6,1,0),ny)));
        check("07:59 -> London", "London".equals(TTFMCore.sessionOf(mk(ny,2026,9,6,7,59),ny)));
        check("08:00 -> NY",     "NY".equals(TTFMCore.sessionOf(mk(ny,2026,9,6,8,0),ny)));
        check("16:59 -> NY",     "NY".equals(TTFMCore.sessionOf(mk(ny,2026,9,6,16,59),ny)));
        check("17:00 -> Closed", "Closed".equals(TTFMCore.sessionOf(mk(ny,2026,9,6,17,0),ny)));
        check("filter All allows Closed", TTFMCore.sessionAllowed("Closed",0));
        check("filter L+NY blocks Asia", !TTFMCore.sessionAllowed("Asia",1));
        check("filter L+NY allows NY",    TTFMCore.sessionAllowed("NY",1));

        System.out.println("== detectSweep [J] ==");
        TTFMCore.CandleData c1 = new TTFMCore.CandleData(10,12,8,11, 1000,true);
        TTFMCore.CandleData bullSweep = new TTFMCore.CandleData(11,11.5,7.5,11.2, 2000,true);
        TTFMCore.CandleData bearSweep = new TTFMCore.CandleData(11,12.5,9,10.5, 2000,true);
        TTFMCore.CandleData inside    = new TTFMCore.CandleData(10.5,11.5,8.5,11, 2000,true);
        double[] s = TTFMCore.detectSweep(c1,bullSweep);
        check("bull sweep detected", s!=null && s[0]==1); eqd("bull sweep level = c1.low", s==null?-1:s[1], 8);
        s = TTFMCore.detectSweep(c1,bearSweep);
        check("bear sweep detected", s!=null && s[0]==0); eqd("bear sweep level = c1.high", s==null?-1:s[1], 12);
        check("inside bar = no sweep", TTFMCore.detectSweep(c1,inside)==null);

        System.out.println("== findWave [J/B] ==");
        TTFMCore.RB[] bars = {
            bar(1, 10,11,9,10.5),   // bull
            bar(2, 10.5,11.5,10,11),// bull
            bar(3, 11,11.2,10.4,10.6),// bear
            bar(4, 10.6,10.8,10.0,10.2),// bear
            bar(5, 10.2,10.4,9.6,9.8)   // bear
        };
        int[] w = TTFMCore.findWave(bars,4,200,true,false);
        check("bearish wave start=2", w[0]==2);
        check("bearish wave end=4",   w[1]==4);
        w = TTFMCore.findWave(bars,1,200,false,false);
        check("bullish wave start=0", w[0]==0);
        check("bullish wave end=1",   w[1]==1);

        System.out.println("== aggregate HTF [J] ==");
        TTFMCore.LayerData layer = new TTFMCore.LayerData();
        layer.candlesToShow=4;
        long base = mk(g3,2026,9,6,8,0);
        // six 15m bars filling 08:00-09:00 then one bar in 09:00
        double p=100;
        for (int i=0;i<6;i++){ TTFMCore.aggregate(layer, bar(base+i*15*60000L, p, p+1, p-1, p+0.5), H, g3); p+=0.5; }
        TTFMCore.aggregate(layer, bar(base+6*15*60000L, p, p+1, p-1, p+0.5), H, g3);
        check("one completed 1H candle", layer.historical.size()==1);
        check("current candle active",   layer.curActive);
        check("completed candle openTime=08:00", layer.historical.get(0).openTime==base);
        check("completed candle marked completed", layer.historical.get(0).completed);

        System.out.println("== v2 legCategory / multipliers [C/O] ==");
        check("leg 2.5x avg -> expanding(2)", TTFMCore.legCategory(2.5,1.0)==2);
        check("leg 1.5x avg -> large(0)",     TTFMCore.legCategory(1.5,1.0)==0);
        check("leg 1.0x avg -> average(1)",   TTFMCore.legCategory(1.0,1.0)==1);
        check("avg 0 -> average(1) safe",     TTFMCore.legCategory(5.0,0.0)==1);
        check("large mult = [-1]",            TTFMCore.legMultipliers(0).length==1 && TTFMCore.legMultipliers(0)[0]==-1.0);
        check("average mult = [-2,-2.5]",     TTFMCore.legMultipliers(1).length==2 && TTFMCore.legMultipliers(1)[1]==-2.5);
        check("expanding mult = [-4,-4.5]",   TTFMCore.legMultipliers(2).length==2 && TTFMCore.legMultipliers(2)[1]==-4.5);

        System.out.println("== v2 EQ bias / inversion [C] ==");
        double PE = 10.0;  // previous candle EQ = the divider
        TTFMCore.CandleData up   = new TTFMCore.CandleData(10,12,10.5,11, 1,true); // stays above, close above
        TTFMCore.CandleData down = new TTFMCore.CandleData(10,9.5,8,9,  1,true);  // stays below, close below
        TTFMCore.CandleData invB = new TTFMCore.CandleData(10,12,7,11, 1,true);   // swept low, closed high
        TTFMCore.CandleData invS = new TTFMCore.CandleData(10,13,8,9,  1,true);   // swept high, closed low
        check("close above prev EQ -> +1",  TTFMCore.eqSide(up,PE)==1);
        check("close below prev EQ -> -1",  TTFMCore.eqSide(down,PE)==-1);
        check("sweep-low+close-high = inversion",  TTFMCore.eqInversion(invB,PE));
        check("sweep-high+close-low = inversion",  TTFMCore.eqInversion(invS,PE));
        check("respect upper (stays above) = no inversion", !TTFMCore.eqInversion(up,PE));
        check("respect lower (stays below) = no inversion", !TTFMCore.eqInversion(down,PE));

        System.out.println("== v2 IC-CISD early/late [C/O] ==");
        check("confirm @30% = early",  TTFMCore.icEarly(30,0,100));
        check("confirm @50% = early",  TTFMCore.icEarly(50,0,100));
        check("confirm @70% = late",   !TTFMCore.icEarly(70,0,100));
        check("period 0 -> early safe",TTFMCore.icEarly(999,0,0));

        System.out.println("== v2 signal state machine [C/O] ==");
        TTFMCore.RB[] failBars = { bar(10,100,100.5,98.5,98.5) };            // close beyond stop(99)
        check("close beyond stop -> FAILED(2)", TTFMCore.signalState(true,100,99,failBars,0,true)==2);
        TTFMCore.RB[] workBars = { bar(10,100,101.2,99.8,100.5) };           // high reaches +1R
        check("reached 1R -> VALID(0)", TTFMCore.signalState(true,100,99,workBars,0,true)==0);
        TTFMCore.RB[] stallBars = { bar(10,100,100.4,99.6,100.2) };          // no 1R
        check("no 1R + HTF closed -> TIGHTEN(1)", TTFMCore.signalState(true,100,99,stallBars,0,true)==1);
        check("no 1R + no HTF close -> VALID(0)", TTFMCore.signalState(true,100,99,stallBars,0,false)==0);
        TTFMCore.RB[] bearFail = { bar(10,100,101.5,99.9,101.5) };           // bear stop=101, close above
        check("bear close beyond stop -> FAILED(2)", TTFMCore.signalState(false,100,101,bearFail,0,true)==2);
        TTFMCore.RB[] bearWork = { bar(10,100,100.2,99.0,99.5) };            // low reaches -1R
        check("bear reached 1R -> VALID(0)", TTFMCore.signalState(false,100,101,bearWork,0,true)==0);

        System.out.println("== v3 FVG / OB / Breaker [C] ==");
        TTFMCore.RB[] fvgB = { bar(1,10,10.2,9.8,10), bar(2,10,10.3,9.9,10.2), bar(3,10.2,11,10.5,10.8) };
        double[] z = TTFMCore.detectFVG(fvgB,2,true);
        check("bull FVG detected", z!=null); if (z!=null){ eqd("bull FVG lo=10.2? (high[i-2]=10.2)", z[0], 10.2); eqd("bull FVG hi=10.5 (low[i])", z[1], 10.5); }
        TTFMCore.RB[] fvgS = { bar(1,10,10.2,9.8,10), bar(2,10,10.1,9.7,9.9), bar(3,9.9,9.6,9.4,9.5) };
        double[] zs2 = TTFMCore.detectFVG(fvgS,2,false);
        check("bear FVG detected", zs2!=null);
        TTFMCore.RB[] obB = { bar(1,10,10.2,9.0,9.2), bar(2,9.2,11.5,9.1,11.3) };  // bear then big bull displacement
        check("OB index = 0 (last bear before displacement)", TTFMCore.findOrderBlock(obB,1,true,1.0)==0);
        check("OB -1 when no displacement", TTFMCore.findOrderBlock(obB,1,true,50.0)==-1);
        TTFMCore.RB[] brk = { bar(1,10,10.2,9.0,9.2), bar(2,9.2,9.6,8.4,8.5) };  // close below OB low -> breached
        check("bull OB breached -> breaker", TTFMCore.obBreached(brk,0,true));
        check("bull OB not breached when holds", !TTFMCore.obBreached(obB,0,true));

        System.out.println("== v3 structure / C2 / C3 [C] ==");
        TTFMCore.CandleData cX = new TTFMCore.CandleData(10,12,8,11, 1,true);
        TTFMCore.CandleData c2 = new TTFMCore.CandleData(11,11.8,7.5,11.5, 2,true); // swept low, closed inside+above
        check("C2 closure true (sweep+inside+poi+struct)", TTFMCore.c2Closure(cX,c2,true,true,1));
        check("C2 false if struct opposed", !TTFMCore.c2Closure(cX,c2,true,true,-1));
        check("C2 false if no poi", !TTFMCore.c2Closure(cX,c2,true,false,1));
        TTFMCore.CandleData bigWick = new TTFMCore.CandleData(10,12,8,10.2, 2,true);
        eqd("big-wick C2 mid = 10", TTFMCore.c2WickMid(bigWick), 10.0);
        TTFMCore.CandleData smallBody = new TTFMCore.CandleData(8.2,12,8,11.9, 2,true);
        check("small-wick C2 -> NaN", Double.isNaN(TTFMCore.c2WickMid(smallBody)));
        TTFMCore.CandleData c3ok = new TTFMCore.CandleData(10,11.6,10.4,11.8, 3,true); // close above c2 body top(11.5)
        check("C3 closure true (no sweep, close over body)", TTFMCore.c3Closure(c2,c3ok,true));
        TTFMCore.CandleData c3sweep = new TTFMCore.CandleData(11,12,7.0,11.6, 3,true); // sweeps c2 low
        check("C3 false when sweep present", !TTFMCore.c3Closure(c2,c3sweep,true));
        TTFMCore.CandleData c3weak = new TTFMCore.CandleData(10,11.0,10.4,11.2, 3,true); // close below body top
        check("C3 false when close not over body", !TTFMCore.c3Closure(c2,c3weak,true));

        System.out.println("== v4 profiles / pairings / SMT [C] ==");
        TimeZone ny2 = TimeZone.getTimeZone("America/New_York");
        check("20:00 NY -> Asia profile",   "Asia".equals(TTFMCore.profileOf(mk(ny2,2026,9,6,20,0),ny2)));
        check("02:00 NY -> London profile", "London".equals(TTFMCore.profileOf(mk(ny2,2026,9,6,2,0),ny2)));
        check("10:00 NY -> NY profile",     "NY".equals(TTFMCore.profileOf(mk(ny2,2026,9,6,10,0),ny2)));
        eq("profileStart 20:00 -> 18:00", TTFMCore.profileStart(mk(ny2,2026,9,6,20,0),ny2), mk(ny2,2026,9,6,18,0));
        eq("profileStart 00:30 -> prev 18:00", TTFMCore.profileStart(mk(ny2,2026,9,7,0,30),ny2), mk(ny2,2026,9,6,18,0));
        eq("profileStart 02:00 -> 01:00", TTFMCore.profileStart(mk(ny2,2026,9,6,2,0),ny2), mk(ny2,2026,9,6,1,0));
        eq("profileStart 10:00 -> 08:00", TTFMCore.profileStart(mk(ny2,2026,9,6,10,0),ny2), mk(ny2,2026,9,6,8,0));
        check("4H entry pair = 15m", TTFMCore.entryPairFor(4*3600000L)==900000L);
        check("1D entry pair = 1H",  TTFMCore.entryPairFor(86400000L)==3600000L);
        check("1D EQ pair = 1H",     TTFMCore.eqPairFor(86400000L)==3600000L);
        check("1H EQ pair = 5m",     TTFMCore.eqPairFor(3600000L)==300000L);
        double[] mLows = {8,6,7,5,6,4,5};   // falling swing lows
        double[] oLows = {4,6,5,7,6,8,7};   // rising swing lows
        TTFMCore.RB[] mineLow  = new TTFMCore.RB[mLows.length];
        TTFMCore.RB[] otherLow = new TTFMCore.RB[oLows.length];
        for (int i=0;i<mLows.length;i++){ mineLow[i]=bar(i+1,mLows[i],mLows[i]+1,mLows[i],mLows[i]+0.5);
                                          otherLow[i]=bar(i+1,oLows[i],oLows[i]+1,oLows[i],oLows[i]+0.5); }
        check("SMT bull (my lows down, other lows up)", TTFMCore.smtDivergence(mineLow,otherLow,true,5));
        check("SMT bear false on same data", !TTFMCore.smtDivergence(mineLow,otherLow,false,5));

        System.out.println("== v6 conditional candleEQ [C, screenshot-confirmed] ==");
        TTFMCore.CandleData bearLW = new TTFMCore.CandleData(1.1000,1.1150,1.0940,1.0950, 1,true);
        eqd("bear long-upper-wick EQ = (H+O)/2", bearLW.candleEQ(), 1.1075);
        TTFMCore.CandleData bullLW = new TTFMCore.CandleData(1.0900,1.0960,1.0750,1.0950, 1,true);
        eqd("bull long-lower-wick EQ = (L+O)/2", bullLW.candleEQ(), 1.0825);
        TTFMCore.CandleData norm = new TTFMCore.CandleData(10,11.4,9.6,11, 1,true);
        eqd("normal EQ = (H+L)/2", norm.candleEQ(), 10.5);
        TTFMCore.CandleData bullUW = new TTFMCore.CandleData(10,11.5,9.9,10.2, 1,true);
        eqd("bull long-UPPER-wick EQ = (H+C)/2", bullUW.candleEQ(), 10.85);

        System.out.println("== v6 T-Spot = [EQ , nextOpen] [C, agreed] ==");
        TTFMCore.CandleData n1 = new TTFMCore.CandleData(1.0900,1.0960,1.0750,1.0950, 1,true); // tspotEQ=full mid 1.0855
        TTFMCore.CandleData n2 = new TTFMCore.CandleData(1.0900,1.0930,1.0880,1.0910, 2,true); // open=1.0900
        double[] tz2 = TTFMCore.tspotZone(n1,n2);
        check("T-Spot zone found", tz2!=null);
        if (tz2!=null){ eqd("T-Spot lo = full-range EQ 1.0855", tz2[0], 1.0855); eqd("T-Spot hi = nextOpen 1.0900", tz2[1], 1.0900); }
        TTFMCore.CandleData n3 = new TTFMCore.CandleData(1.0855,1.0900,1.0800,1.0860, 2,true); // open == tspotEQ -> degenerate
        check("T-Spot null when open == EQ", TTFMCore.tspotZone(n1,n3)==null);
        TTFMCore.CandleData n4 = new TTFMCore.CandleData(1.0500,1.0530,1.0480,1.0510, 2,true); // open below eq
        double[] tz4 = TTFMCore.tspotZone(n1,n4);
        check("T-Spot orders bounds when open below EQ", tz4!=null && Math.abs(tz4[0]-1.0500)<1e-9 && Math.abs(tz4[1]-1.0855)<1e-9);

        System.out.println("== v6 T-Spot type + lifecycle [C] ==");
        check("open below EQ -> SELL-support(1)", TTFMCore.tspotType(1.0900,1.0825)==1);
        check("open above EQ -> BUY-support(0)",  TTFMCore.tspotType(1.0825,1.0900)==0);
        TTFMCore.RB[] freshBars = { bar(1,1.0860,1.0870,1.0850,1.0865) };          // inside zone [1.0825,1.0900], no close beyond
        check("touched, no close beyond -> retested(1)", TTFMCore.zoneState(1.0825,1.0900,1,freshBars,0)==1);
        TTFMCore.RB[] untBars = { bar(1,1.0950,1.0960,1.0940,1.0955) };            // never touches zone
        check("untouched -> fresh(0)", TTFMCore.zoneState(1.0825,1.0900,1,untBars,0)==0);
        TTFMCore.RB[] invD = { bar(1,1.0840,1.0845,1.0800,1.0810) };              // discount closed below lo -> invalidated
        check("discount close below lo -> invalidated(2)", TTFMCore.zoneState(1.0825,1.0900,1,invD,0)==2);
        TTFMCore.RB[] invP = { bar(1,1.0880,1.0930,1.0870,1.0920) };              // premium closed above hi -> invalidated
        check("premium close above hi -> invalidated(2)", TTFMCore.zoneState(1.0825,1.0900,0,invP,0)==2);

        System.out.println("== step2 respectedHalf [C] ==");
        TTFMCore.CandleData rhUp = new TTFMCore.CandleData(9,10.4,8.6,10, 1,true);  // normal wicks, eq=9.5, close above
        double[] rh1 = TTFMCore.respectedHalf(rhUp);
        eqd("close>=EQ -> half lo = EQ", rh1[0], 9.5); eqd("close>=EQ -> half hi = high", rh1[1], 10.4);
        TTFMCore.CandleData rhDn = new TTFMCore.CandleData(10,11,8.6,9, 1,true);    // normal wicks, eq=9.8, close below
        double[] rh2 = TTFMCore.respectedHalf(rhDn);
        eqd("close<EQ -> half lo = low", rh2[0], 8.6); eqd("close<EQ -> half hi = EQ", rh2[1], 9.8);

        System.out.println("== official-match: C4 ladder shortSym [C] ==");
        TTFMCore.CandleData c3c = new TTFMCore.CandleData(10,12,9.5,11.5, 1,true);
        TTFMCore.CandleData c4ok = new TTFMCore.CandleData(11.5,13,11,12.8, 2,true);   // close over c3.high
        TTFMCore.CandleData c4no = new TTFMCore.CandleData(11.5,11.9,11,11.8, 2,true); // inside c3 range
        check("bull C4 close>c3.high -> true",  TTFMCore.c4Closure(c3c,c4ok,true));
        check("bull C4 inside range -> false", !TTFMCore.c4Closure(c3c,c4no,true));
        TTFMCore.CandleData c3b = new TTFMCore.CandleData(10,10.5,8,8.5, 1,true);
        TTFMCore.CandleData c4b = new TTFMCore.CandleData(8.5,9,7.5,7.8, 2,true);      // close under c3.low
        check("bear C4 close<c3.low -> true",  TTFMCore.c4Closure(c3b,c4b,false));
        check("bear C4 inside range -> false", !TTFMCore.c4Closure(c3b,c4no,false));
        double[] lb = TTFMCore.projLadder(100,10,true);
        eqd("ladder bull -1", lb[0],110); eqd("ladder bull -2.5", lb[2],125); eqd("ladder bull -4.5", lb[4],145);
        double[] lr = TTFMCore.projLadder(100,10,false);
        eqd("ladder bear -1", lr[0],90); eqd("ladder bear -4", lr[3],60);
        check("shortSym USA500 -> ES", TTFMCore.shortSym("USA500.IDX/USD").equals("ES"));
        check("shortSym XAU/EUR -> XAE", TTFMCore.shortSym("XAU/EUR").equals("XAE"));

        System.out.println("== official-match: tspotEQ full-range 50% [C] ==");
        TTFMCore.CandleData lwC = new TTFMCore.CandleData(10,12,7,11, 1,true);   // long lower wick
        eqd("tspotEQ = full-range wick-to-wick mid", TTFMCore.tspotEQ(lwC), 9.5);
        eqd("candleEQ stays conditional (wick mid)", lwC.candleEQ(), 8.5);

        System.out.println("== pure C2 closure (no setup filters) [C] ==");
        TTFMCore.CandleData pc1 = new TTFMCore.CandleData(10,12,8,11, 1,true);
        TTFMCore.CandleData pc2 = new TTFMCore.CandleData(11,11.8,7.5,11.5, 2,true);  // swept low, closed inside
        TTFMCore.CandleData pcX = new TTFMCore.CandleData(11,13,10.5,12.5, 2,true);   // no sweep of c1
        TTFMCore.CandleData pcO = new TTFMCore.CandleData(11,11.8,7.5,12.5, 2,true);  // closed outside c1 high
        check("c2SweepClosure true (sweep+inside)", TTFMCore.c2SweepClosure(pc1,pc2,true));
        check("c2SweepClosure false without sweep", !TTFMCore.c2SweepClosure(pc1,pcX,true));
        check("c2SweepClosure false close outside", !TTFMCore.c2SweepClosure(pc1,pcO,true));

        System.out.println("== official-guide: modelZoneGate C2/C3 only [C] ==");
        TTFMCore.CandleData m0 = new TTFMCore.CandleData(10,10.5,9.5,10.2, 0,true);
        TTFMCore.CandleData m1 = new TTFMCore.CandleData(10,12,8,11, 1,true);
        TTFMCore.CandleData m2 = new TTFMCore.CandleData(11,11.8,7.5,11.5, 2,true);   // C2
        TTFMCore.CandleData m3 = new TTFMCore.CandleData(10,11.6,10.4,11.8, 3,true);  // C3
        java.util.List<TTFMCore.CandleData> mh = new java.util.ArrayList<>();
        mh.add(m0); mh.add(m1); mh.add(m2); mh.add(m3);
        check("gate: C2 candle -> zone prints",   TTFMCore.modelZoneGate(mh,2,1));
        check("gate: C3 candle -> zone prints",   TTFMCore.modelZoneGate(mh,3,1));
        check("gate: plain candle -> no zone",   !TTFMCore.modelZoneGate(mh,1,1));
        check("gate: index 0 -> no zone",        !TTFMCore.modelZoneGate(mh,0,1));

        System.out.println();
        System.out.println("RESULT: pass="+pass+"  fail="+fail);
        if (fail>0) System.exit(1);
    }
}
