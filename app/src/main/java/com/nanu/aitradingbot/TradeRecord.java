package com.nanu.aitradingbot;

import java.util.ArrayList;
import java.util.List;

public class TradeRecord {
    public String id;
    public String tokenName;
    public String tokenSymbol;
    public String tokenAddress;
    public String chain;
    public double entryPrice;
    public double exitPrice;
    public double amountUsd;
    public double pnlUsd;
    public double pnlPercent;
    public long   openTimeMs;
    public long   closeTimeMs;
    public boolean isWin;
    public boolean isOpen;
    public String exitReason;
    public String strategyName;
    public int    confidenceScore;   // DexSafetyPolicy score (0–120+)
    public List<String> patterns = new ArrayList<>();
    public boolean isLive;
    public String buyTxHash  = "";
    public String sellTxHash = "";

    // Algo trading fields
    public int    entryAlgoScore = 0;    // AlgoEngine score at entry (0–100)
    public int    exitAlgoScore  = 0;    // AlgoEngine score at exit  (0–100)
    public String algoSignal     = "";   // RSI_BOUNCE, MACD_CROSS, MULTI_CONFIRM, etc.
    public double peakPrice      = 0;    // highest price seen while open (trailing stop)
}
