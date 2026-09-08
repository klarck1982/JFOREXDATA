package com.dukascopy.api.indicators;
public interface IIndicator {
  void onStart(IIndicatorContext c); IndicatorResult calculate(int s,int e);
  IndicatorInfo getIndicatorInfo(); InputParameterInfo getInputParameterInfo(int i);
  OptInputParameterInfo getOptInputParameterInfo(int i); OutputParameterInfo getOutputParameterInfo(int i);
  void setInputParameter(int i,Object o); void setOutputParameter(int i,Object o);
  void setOptInputParameter(int i,Object v); int getLookback(); int getLookforward(); }
