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
    public int    confidenceScore;
    public List<String> patterns = new ArrayList<>();
    public boolean isLive;
    public String buyTxHash  = "";
    public String sellTxHash = "";
}
