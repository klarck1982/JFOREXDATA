package com.dukascopy.api;
public enum Instrument {
  EURUSD, GBPUSD, XAUUSD, USATECHIDXUSD, USA500IDXUSD, USA30IDXUSD,
  EURGBP, USDJPY, AUDCAD, XAGUSD, XAGEUR, BTCUSD;
  /** Official API: "Returns corresponding instrument for string in CUR1/CUR2 format" (null if not found). */
  public static Instrument fromString(String instrumentAsString) {
    if (instrumentAsString==null) return null;
    String n=instrumentAsString.replace("/","").replace(".","").replace("-","").toUpperCase(java.util.Locale.US);
    try { return Instrument.valueOf(n); } catch (Exception e) { return null; }
  }
}
