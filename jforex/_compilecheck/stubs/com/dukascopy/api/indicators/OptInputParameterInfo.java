package com.dukascopy.api.indicators;
public class OptInputParameterInfo {
    public enum Type { OTHER }
    private final String name;
    public OptInputParameterInfo(String n, Type t, Object desc){ this.name=n; }
    public String getName(){ return name; }
}
