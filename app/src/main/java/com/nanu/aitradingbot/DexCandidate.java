package com.nanu.aitradingbot;

import java.util.ArrayList;
import java.util.List;

public class DexCandidate {
    public String name;
    public String symbol;
    public String tokenAddress;
    public String pairAddress;
    public String chain;           // "bsc" or "solana"
    public double priceUsd;
    public double priceChange1h;
    public double priceChange24h;
    public double volumeUsd24h;
    public double liquidityUsd;
    public long   pairCreatedAtMs;
    public int    buys24h;
    public int    sells24h;
    public int    score;
    public String status;          // QUALIFIED / WATCHING / BLOCKED
    public String blockReason;
    public List<String> patterns = new ArrayList<>();
    public long discoveredAtMs = System.currentTimeMillis();
}
