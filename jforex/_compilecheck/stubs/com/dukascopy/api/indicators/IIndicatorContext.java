package com.dukascopy.api.indicators;
import com.dukascopy.api.IConsole; import com.dukascopy.api.IFeedDescriptor; import com.dukascopy.api.IHistory;
public interface IIndicatorContext { String getFilesDir(); IConsole getConsole(); IFeedDescriptor getFeedDescriptor(); IHistory getHistory(); }
