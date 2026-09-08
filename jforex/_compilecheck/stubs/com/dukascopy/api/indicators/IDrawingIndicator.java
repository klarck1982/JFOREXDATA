package com.dukascopy.api.indicators;
import java.awt.Graphics; import java.awt.Color; import java.awt.Stroke; import java.awt.Point; import java.awt.Shape;
import java.util.List; import java.util.Map;
public interface IDrawingIndicator {
  Point drawOutput(Graphics g,int outputIdx,Object values,Color color,Stroke stroke,
                   IIndicatorDrawingSupport support,List<Shape> shapes,Map<Color,List<Point>> handles); }
