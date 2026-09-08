package com.dukascopy.api;
import java.util.List;
public interface IHistory { List<IBar> getBars(Instrument i, Period p, OfferSide o, long from, long to); }
