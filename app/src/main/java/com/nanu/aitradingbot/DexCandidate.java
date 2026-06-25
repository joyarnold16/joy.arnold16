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

    // Price changes by period
    public double priceChange5m;
    public double priceChange1h;
    public double priceChange6h;
    public double priceChange24h;

    // Volume by period (USD)
    public double volume5m;
    public double volume1h;
    public double volume6h;
    public double volumeUsd24h;

    // Liquidity & market cap
    public double liquidityUsd;
    public double fdv;              // fully diluted valuation
    public double marketCap;

    // DEX metadata
    public String dexName    = "";  // PancakeSwap V2, Raydium, etc.
    public String quoteToken = "";  // WBNB, USDC, etc.

    // Transaction counts by period
    public int buys5m,  sells5m;
    public int buys1h,  sells1h;
    public int buys6h,  sells6h;
    public int buys24h, sells24h;

    // Pair metadata
    public long   pairCreatedAtMs;
    public double pairAgeHours;       // derived: (now - pairCreatedAtMs) / 3_600_000

    // Scoring
    public int    score;
    public int    scamRiskScore;      // 0=safe, 100=likely scam; from on-chain checks
    public String status;             // QUALIFIED / WATCHING / BLOCKED
    public String blockReason;

    // Patterns & algo (set after scanning)
    public List<String> patterns    = new ArrayList<>();
    public long   discoveredAtMs    = System.currentTimeMillis();
    public int    algoScore         = 0;
    public String algoSignal        = "";

    // Computed safety metrics
    public double fdvLiquidityRatio = 0;  // fdv / liquidityUsd; >100x = dangerous

    // Computed ratios (derived in parsePair / scoreAndSort)
    public double volLiqRatio  = 0;   // volumeUsd24h / liquidityUsd
    public double buyPressure  = 0;   // buys24h / (buys24h + sells24h)

    // Data freshness
    public long dataFetchedAtMs = System.currentTimeMillis();

    // On-chain safety flags (populated by SolanaChecker / BscChecker before entry)
    public boolean mintAuthorityRevoked   = false;
    public boolean freezeAuthorityRevoked = false;
    public boolean lpBurned               = false;
    public boolean lpLocked               = false;
    public boolean contractVerified       = false;
    public boolean ownerRenounced         = false;
    public String  onChainNote            = "";
    public String  ownerPowerFlags        = "";  // dangerous owner capabilities (e.g. "BLACKLIST,MINT")
    public int     chainSafetyScore       = 0;   // 0-20 from SolanaChecker / BscChecker

    // Sell simulation result (pre-entry check)
    public boolean sellSimOk     = true;   // default true; false = hard/soft block
    public String  sellSimNote   = "";
    public double  sellImpactPct = 0;      // expected price impact % for sell

    // Stage band (set by DexSafetyPolicy after on-chain checks)
    // REJECT / WATCH / PAPER / SMALL_LIVE / NORMAL_LIVE
    public String stageBand = "REJECT";
}
