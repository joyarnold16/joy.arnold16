package com.nanu.aitradingbot;

import java.util.ArrayList;
import java.util.List;

public class TradeRecord {
    public String id;
    public String tokenName;
    public String tokenSymbol;
    public String tokenAddress;
    public String pairAddress  = "";   // DEX Screener pair address
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

    // Market snapshot at entry (for post-trade analysis)
    public double liquidityAtEntry = 0;
    public double volumeAtEntry    = 0;
    public double fdvAtEntry       = 0;
    public int    scamScore        = 0;

    // Algo trading fields
    public int    entryAlgoScore  = 0;
    public int    exitAlgoScore   = 0;
    public String algoSignal      = "";
    public double peakPrice       = 0;    // highest price seen while open (trailing stop)

    // Safety snapshot at entry
    public int     chainSafetyScore = 0;  // 0-20 from on-chain checker
    public boolean sellSimOk        = true;
    public double  liquidityLow     = 0;  // lowest liquidity seen during hold (liq-drop monitor)

    // Price-locked trailing SL: always (entryPrice * sl%) below the highest price seen.
    // Initialized at open; ratchets up as peakPrice rises; never moves down.
    public double trailingSl = 0;

    public long holdingTimeMs() {
        return closeTimeMs > 0 && openTimeMs > 0 ? closeTimeMs - openTimeMs : 0;
    }
}
