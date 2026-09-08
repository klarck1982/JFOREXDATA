package com.dukascopy.api.indicators;
import com.dukascopy.api.JFTimeZone;
public interface IIndicatorDrawingSupport {
  int getNumberOfCandlesOnScreen(); int getIndexOfFirstCandleOnScreen();
  float getCandleWidthInPixels(int i); float getSpaceBetweenCandlesInPixels(int i);
  int getMiddleOfCandle(int i); int getYForValue(double v); int getXForTime(long t, boolean b);
  int getChartWidth(); int getChartHeight(); JFTimeZone getJFTimeZone(); }
