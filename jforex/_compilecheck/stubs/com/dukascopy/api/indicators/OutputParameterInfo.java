package com.dukascopy.api.indicators;
public class OutputParameterInfo { public enum Type { DOUBLE } public enum DrawingStyle { LINE }
  public OutputParameterInfo(String n, Type t, DrawingStyle d){}
  public void setDrawnByIndicator(boolean b){} public void setShowOutput(boolean b){}
  public void setColor(java.awt.Color c){} public void setOpacityAlpha(float f){} }
