package com.nanu.aitradingbot;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class DexActivity extends Activity {

    // —— Colors —————————————————————————————————————————
    private static final int BG      = 0xFF0D1117;
    private static final int CARD    = 0xFF161B22;
    private static final int CYAN    = 0xFF00E5FF;
    private static final int PURPLE  = Color.rgb(138, 60, 220);
    private static final int GREEN   = 0xFF00C853;
    private static final int RED     = 0xFFFF1744;
    private static final int AMBER   = 0xFFFFAB00;
    private static final int WHITE   = 0xFFFFFFFF;
    private static final int GREY    = 0xFF8B949E;
    private static final int DARK_CARD = 0xFF0D1117;

    // —— State —————————————————————————————————————————
    private DexAppStore store;
    private Handler ui;
    private int tab = 0;
    private ScrollView[] tabViews;
    private TextView[] tabBtns;
    private LinearLayout content;
    private List<DexCandidate> lastCandidates = new ArrayList<>();
    private EditText withdrawAddressField;

    // —— Lifecycle ———————————————————————————————————————

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            // Register full BouncyCastle so web3j secp256k1 works on Android
            try {
                java.security.Security.removeProvider("BC");
                java.security.Security.addProvider(
                    new org.bouncycastle.jce.provider.BouncyCastleProvider());
            } catch (Throwable ignore) {}

            ui    = new Handler(Looper.getMainLooper());
            store = new DexAppStore(this);

            if (!store.hasWallet()) createWallet();

            buildRoot();
            showTab(0);
        } catch (Throwable e) {
            android.util.Log.e("DexActivity", "Startup crash", e);
            TextView errView = new TextView(this);
            errView.setText("Startup error: " + e.getClass().getSimpleName() + "\n" + e.getMessage());
            errView.setTextColor(0xFFFFFFFF);
            errView.setBackgroundColor(0xFF0D1117);
            errView.setPadding(32, 64, 32, 32);
            setContentView(errView);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        store.reload();
        TradeService.uiListener = engineListener;
        refreshCurrentTab();
    }

    @Override protected void onPause() {
        super.onPause();
        TradeService.uiListener = null;
    }

    @Override protected void onDestroy() {
        TradeService.uiListener = null;
        super.onDestroy();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // QR scanner result handled here when scanner library is added
    }

    // —— Wallet ———————————————————————————————————————

    private void createWallet() {
        try {
            String m = SecurePrefs.generateMnemonic();
            if (m != null && !m.isEmpty()) {
                store.saveMnemonic(m);
                new AlertDialog.Builder(this)
                    .setTitle("New Wallet Created")
                    .setMessage("Recovery phrase (WRITE IT DOWN):\n\n" + m
                        + "\n\nStore it safely. Never share it.")
                    .setPositiveButton("I Saved It", null)
                    .setCancelable(false)
                    .show();
            }
        } catch (Throwable e) {
            Toast.makeText(this, "Wallet init error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // —— Root layout ———————————————————————————————————

    private void buildRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(0, statusBarHeight(), 0, 0);

        // Header
        LinearLayout header = row(dp(16), dp(12));
        header.setBackgroundColor(BG);
        TextView title = tv("Nanu AI Trading Bot", 20, WHITE);
        title.setTypeface(null, Typeface.BOLD);
        header.addView(title);
        root.addView(header);

        // Tab bar
        root.addView(buildTabBar());

        // Content area
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        content.setLayoutParams(cp);
        root.addView(content);

        // Build all tab scroll views
        String[] tabs = {"Home", "Discover", "History", "Position", "Wallet", "Control"};
        tabViews = new ScrollView[tabs.length];
        for (int i = 0; i < tabs.length; i++) {
            ScrollView sv = new ScrollView(this);
            sv.setBackgroundColor(BG);
            sv.setVisibility(View.GONE);
            content.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
            tabViews[i] = sv;
        }

        setContentView(root);
    }

    private HorizontalScrollView buildTabBar() {
        String[] labels = {"Home","Discover","History","Position","Wallet","Control"};
        tabBtns = new TextView[labels.length];
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setBackgroundColor(CARD);
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(dp(8), dp(6), dp(8), dp(6));
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            TextView t = tv(labels[i], 13, GREY);
            t.setPadding(dp(14), dp(8), dp(14), dp(8));
            t.setGravity(Gravity.CENTER);
            t.setOnClickListener(v -> showTab(idx));
            tabBtns[i] = t;
            bar.addView(t);
        }
        hsv.addView(bar);
        return hsv;
    }

    private void showTab(int idx) {
        tab = idx;
        for (int i = 0; i < tabViews.length; i++)
            tabViews[i].setVisibility(i == idx ? View.VISIBLE : View.GONE);
        for (int i = 0; i < tabBtns.length; i++) {
            tabBtns[i].setTextColor(i == idx ? CYAN : GREY);
            tabBtns[i].setBackgroundResource(0);
            if (i == idx) { GradientDrawable bg = pill(CYAN, 0.15f); tabBtns[i].setBackground(bg); }
        }
        refreshCurrentTab();
    }

    private void refreshCurrentTab() {
        switch (tab) {
            case 0: buildHome();     break;
            case 1: buildDiscover(); break;
            case 2: buildHistory();  break;
            case 3: buildPosition(); break;
            case 4: buildWallet();   break;
            case 5: buildControl();  break;
        }
    }

    // ─────────────────────────────────────────────────────
    // TAB 0: HOME
    // ─────────────────────────────────────────────────────

    private void buildHome() {
        LinearLayout p = page();

        // Mascot
        int mascotId = getResources().getIdentifier("nanu_mascot", "drawable", getPackageName());
        if (mascotId != 0) {
            ImageView img = new ImageView(this);
            img.setImageResource(mascotId);
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            img.setBackgroundColor(BG);
            img.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(240)));
            p.addView(img);
        }

        // Auto Mode banner
        if (store.autoMode) {
            LinearLayout autoBanner = new LinearLayout(this);
            autoBanner.setOrientation(LinearLayout.HORIZONTAL);
            autoBanner.setGravity(Gravity.CENTER);
            autoBanner.setPadding(dp(12), dp(8), dp(12), dp(8));
            GradientDrawable ab = new GradientDrawable();
            ab.setColor(0xFF1A1040); ab.setCornerRadius(dp(8));
            ab.setStroke(dp(1), PURPLE);
            autoBanner.setBackground(ab);
            LinearLayout.LayoutParams abLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            abLp.setMargins(0, 0, 0, dp(8));
            autoBanner.setLayoutParams(abLp);
            TextView autoTv = tv("AUTO MODE  •  ML is fully in control of all trade decisions", 12, PURPLE);
            autoTv.setTypeface(null, Typeface.BOLD);
            autoTv.setGravity(Gravity.CENTER);
            autoBanner.addView(autoTv);
            p.addView(autoBanner);
        }

        // Wallet card
        LinearLayout wCard = card(p);
        tv2(wCard, "BOT WALLET", 11, CYAN, Typeface.BOLD);
        String bnbShort = store.bnbAddress.length() > 12
            ? store.bnbAddress.substring(0, 8) + "..." + store.bnbAddress.substring(store.bnbAddress.length() - 6)
            : store.bnbAddress;
        String solShort = store.solAddress.length() > 12
            ? store.solAddress.substring(0, 8) + "..." + store.solAddress.substring(store.solAddress.length() - 6)
            : store.solAddress;
        tv2(wCard, bnbShort + "  (BNB)", 15, WHITE, Typeface.BOLD);
        tv2(wCard, solShort + "  (SOL)", 13, GREY, Typeface.NORMAL);
        String modeStr = store.liveMode ? "🟢 LIVE MODE ACTIVE" : "Paper scanner active. Mainnet swaps are security-blocked.";
        tv2(wCard, modeStr, 12, store.liveMode ? GREEN : GREY, Typeface.NORMAL);
        wCard.addView(gap(8));
        // Stats row
        LinearLayout stats = row(0, 0);
        stats.addView(statBox("MODE", store.liveMode ? "LIVE" : "PAPER",
            store.liveMode ? GREEN : AMBER));
        stats.addView(statBox("TODAY", store.tradesToday + "/" + store.maxDailyTrades, CYAN));
        stats.addView(statBox("PAPER P/L",
            String.format("%+.2f", store.totalPnlUsd),
            store.totalPnlUsd >= 0 ? GREEN : RED));
        wCard.addView(stats);

        // Scanner card
        LinearLayout sCard = card(p);
        LinearLayout sHead = row(0, 0);
        sHead.addView(tv("MARKET SCANNER", 14, WHITE));
        ((TextView)sHead.getChildAt(0)).setTypeface(null, Typeface.BOLD);
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 0, 1f));
        sHead.addView(spacer);
        TextView scanStatus = tv(TradeService.active ? "SCANNING" : "IDLE", 12,
            TradeService.active ? AMBER : GREY);
        scanStatus.setTypeface(null, Typeface.BOLD);
        sHead.addView(scanStatus);
        sCard.addView(sHead);
        sCard.addView(gap(4));
        int found = lastCandidates.size();
        int bnbC = 0, solC = 0;
        for (DexCandidate c : lastCandidates) {
            if ("bsc".equals(c.chain)) bnbC++; else solC++;
        }
        if (found > 0)
            tv2(sCard, "Found " + found + " candidates (" + bnbC + " BNB, " + solC + " SOL)",
                12, GREY, Typeface.NORMAL);
        tv2(sCard, BotEvolution.summary(store), 12, PURPLE, Typeface.NORMAL);
        sCard.addView(gap(8));
        LinearLayout btns = row(0, 0);
        btns.addView(btn("Start", GREEN, v -> startEngine()));
        btns.addView(gap(8));
        btns.addView(btn("Pause", AMBER, v -> { TradeService.stop(this); buildHome(); }));
        btns.addView(gap(8));
        btns.addView(btn("Panic", RED, v -> panicClose()));
        sCard.addView(btns);

        // Position summary
        List<TradeRecord> openPos = store.getOpenPositions();
        LinearLayout posCard = card(p);
        tv2(posCard, "CURRENT POSITIONS", 14, WHITE, Typeface.BOLD);
        posCard.addView(gap(4));
        if (openPos.isEmpty()) {
            tv2(posCard, "No open positions. Scanner never places a real swap.", 12, GREY, Typeface.NORMAL);
        } else {
            for (TradeRecord r : openPos) {
                String chain = "bsc".equals(r.chain) ? "BNB" : "SOL";
                LinearLayout row = row(0, 4);
                row.addView(chainBadge(chain));
                row.addView(tv(" " + r.tokenSymbol, 13, WHITE));
                View sp = new View(this); sp.setLayoutParams(new LinearLayout.LayoutParams(0,0,1f));
                row.addView(sp);
                row.addView(tv("OPEN", 11, AMBER));
                posCard.addView(row);
                tv2(posCard, "Entry $" + fmt8(r.entryPrice) + " | Conf " + r.confidenceScore,
                    11, GREY, Typeface.NORMAL);
            }
        }

        // Footer
        p.addView(gap(8));
        String mode = store.liveMode ? "Live Mode" : "Paper Mode";
        String modeTag = store.autoMode ? "Auto Mode" : "Manual Mode";
        tv2(p, "NANU AI • BNB + SOL • ML Evolution • " + mode + " • " + modeTag + " • v11.1",
            11, GREY, Typeface.NORMAL).setGravity(Gravity.CENTER);
        set(p);
    }

    // ─────────────────────────────────────────────────────
    // TAB 1: DISCOVER
    // ─────────────────────────────────────────────────────

    private void buildDiscover() {
        LinearLayout p = page();
        LinearLayout header = card(p);
        tv2(header, "DEX DISCOVERY", 16, WHITE, Typeface.BOLD);
        tv2(header, "Scan BNB or SOL individually. Candlestick patterns applied. QUALIFIED is not a scam guarantee.",
            11, GREY, Typeface.NORMAL);
        header.addView(gap(8));

        LinearLayout scanBtns = row(0, 0);
        Button bnbBtn = btn("Scan BNB", AMBER, null);
        Button solBtn = btn("Scan SOL", PURPLE, null);
        Button bothBtn = btn("Scan Both", CYAN, null);

        bnbBtn.setOnClickListener(v -> {
            bnbBtn.setText("Scanning…"); bnbBtn.setEnabled(false);
            DexMarketClient.discoverBnb(store, new DexMarketClient.Callback() {
                public void onResult(java.util.List<DexCandidate> c) {
                    lastCandidates.removeIf(x -> "bsc".equals(x.chain));
                    lastCandidates.addAll(0, c);
                    ui.post(() -> { bnbBtn.setText("Scan BNB"); bnbBtn.setEnabled(true); buildDiscover(); });
                }
                public void onError(String msg) {
                    ui.post(() -> { bnbBtn.setText("Scan BNB"); bnbBtn.setEnabled(true);
                        Toast.makeText(DexActivity.this, "BNB scan: " + msg, Toast.LENGTH_SHORT).show(); });
                }
            });
        });

        solBtn.setOnClickListener(v -> {
            solBtn.setText("Scanning…"); solBtn.setEnabled(false);
            DexMarketClient.discoverSol(store, new DexMarketClient.Callback() {
                public void onResult(java.util.List<DexCandidate> c) {
                    lastCandidates.removeIf(x -> "solana".equals(x.chain));
                    lastCandidates.addAll(c);
                    ui.post(() -> { solBtn.setText("Scan SOL"); solBtn.setEnabled(true); buildDiscover(); });
                }
                public void onError(String msg) {
                    ui.post(() -> { solBtn.setText("Scan SOL"); solBtn.setEnabled(true);
                        Toast.makeText(DexActivity.this, "SOL scan: " + msg, Toast.LENGTH_SHORT).show(); });
                }
            });
        });

        bothBtn.setOnClickListener(v -> {
            bothBtn.setText("Scanning…"); bothBtn.setEnabled(false);
            TradeService.triggerScan(this);
            ui.postDelayed(() -> { bothBtn.setText("Scan Both"); bothBtn.setEnabled(true); buildDiscover(); }, 20_000);
        });

        scanBtns.addView(bnbBtn);
        scanBtns.addView(gap(6));
        scanBtns.addView(solBtn);
        scanBtns.addView(gap(6));
        scanBtns.addView(bothBtn);
        header.addView(scanBtns);

        if (lastCandidates.isEmpty()) {
            LinearLayout empty = card(p);
            tv2(empty, "No results yet. Tap Scan Now.", 13, GREY, Typeface.NORMAL);
        } else {
            for (DexCandidate c : lastCandidates) {
                candidateCard(p, c);
            }
        }
        set(p);
    }

    private void candidateCard(LinearLayout parent, DexCandidate c) {
        LinearLayout card = card(parent);
        // Header row: chain + name + status badge
        LinearLayout top = row(0, 0);
        top.addView(chainBadge("bsc".equals(c.chain) ? "BNB" : "SOL"));
        TextView nameT = tv("  " + c.name + "  $" + fmt8(c.priceUsd), 15, WHITE);
        nameT.setTypeface(null, Typeface.BOLD);
        top.addView(nameT);
        View sp = new View(this); sp.setLayoutParams(new LinearLayout.LayoutParams(0,0,1f));
        top.addView(sp);
        top.addView(statusBadge(c));
        card.addView(top);
        card.addView(gap(2));
        // Liquidity + volume
        tv2(card, "$" + fmtK(c.liquidityUsd) + " liquidity  |  Vol $" + fmtK(c.volumeUsd24h),
            11, GREY, Typeface.NORMAL);
        // Price change
        LinearLayout changes = row(0, 2);
        int h1Color = c.priceChange1h >= 0 ? GREEN : RED;
        int h24Color = c.priceChange24h >= 0 ? GREEN : RED;
        changes.addView(tv(String.format("1h %+.2f%%", c.priceChange1h), 12, h1Color));
        changes.addView(tv("  |  ", 12, GREY));
        changes.addView(tv(String.format("24h %+.2f%%", c.priceChange24h), 12, h24Color));
        card.addView(changes);
        // Patterns
        if (!c.patterns.isEmpty()) {
            LinearLayout patRow = row(0, 4);
            for (int i = 0; i < Math.min(3, c.patterns.size()); i++) {
                boolean bull = CandlePatterns.bullish(Collections.singletonList(c.patterns.get(i)));
                patRow.addView(patternPill(c.patterns.get(i), bull ? GREEN : RED));
                if (i < 2 && i < c.patterns.size() - 1) patRow.addView(gap(4));
            }
            if (c.patterns.size() > 3) {
                patRow.addView(tv(" +" + (c.patterns.size()-3), 10, GREY));
            }
            card.addView(patRow);
        }
        // Reason / warning
        if (c.blockReason != null && !c.blockReason.isEmpty()) {
            int rColor = "BLOCKED".equals(c.status) ? RED : ("QUALIFIED".equals(c.status) ? GREEN : GREY);
            tv2(card, c.blockReason, 11, rColor, Typeface.NORMAL);
        }
    }

    // ─────────────────────────────────────────────────────
    // TAB 2: HISTORY
    // ─────────────────────────────────────────────────────

    private void buildHistory() {
        LinearLayout p = page();
        List<TradeRecord> all = store.loadHistory();
        List<TradeRecord> closed = new ArrayList<>();
        for (TradeRecord r : all) if (!r.isOpen) closed.add(r);

        LinearLayout summary = card(p);
        tv2(summary, "TRADE HISTORY", 16, WHITE, Typeface.BOLD);
        summary.addView(gap(8));
        LinearLayout stats = row(0, 0);
        stats.addView(statBox("TRADES", String.valueOf(closed.size()), CYAN));
        stats.addView(statBox("WIN RATE",
            closed.isEmpty() ? "0%" : String.format("%.0f%%", store.mlWinRate),
            store.mlWinRate >= 50 ? GREEN : RED));
        stats.addView(statBox("TOTAL P/L",
            String.format("%+.2f", store.totalPnlUsd),
            store.totalPnlUsd >= 0 ? GREEN : RED));
        summary.addView(stats);
        summary.addView(gap(8));
        tv2(summary, BotEvolution.summary(store), 12, PURPLE, Typeface.NORMAL);
        summary.addView(gap(8));
        Button pdfBtn = bigBtn("Export PDF to Downloads", CYAN);
        pdfBtn.setOnClickListener(v -> exportPdf(closed));
        summary.addView(pdfBtn);

        if (closed.isEmpty()) {
            LinearLayout empty = card(p);
            tv2(empty, "No closed trades yet. Open positions show in Position tab.",
                13, GREY, Typeface.NORMAL);
        } else {
            for (TradeRecord r : closed) {
                historyCard(p, r);
            }
        }
        set(p);
    }

    private void historyCard(LinearLayout parent, TradeRecord r) {
        LinearLayout card = card(parent);
        LinearLayout top = row(0, 0);
        top.addView(chainBadge("bsc".equals(r.chain) ? "BNB" : "SOL"));
        TextView name = tv("  " + (r.tokenSymbol != null ? r.tokenSymbol : r.tokenName), 15, WHITE);
        name.setTypeface(null, Typeface.BOLD);
        top.addView(name);
        View sp = new View(this); sp.setLayoutParams(new LinearLayout.LayoutParams(0,0,1f));
        top.addView(sp);
        TextView badge = tv(r.isWin ? "WIN" : "LOSS", 12, WHITE);
        badge.setTypeface(null, Typeface.BOLD);
        badge.setPadding(dp(8), dp(4), dp(8), dp(4));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(r.isWin ? GREEN : RED);
        bg.setCornerRadius(dp(12));
        badge.setBackground(bg);
        top.addView(badge);
        card.addView(top);
        card.addView(gap(4));
        tv2(card, "Entry $" + fmt8(r.entryPrice) + "  →  Exit $" + fmt8(r.exitPrice), 12, GREY, Typeface.NORMAL);
        tv2(card, String.format("P/L  %+.4f USD  (%+.2f%%)", r.pnlUsd, r.pnlPercent),
            13, r.pnlUsd >= 0 ? GREEN : RED, Typeface.BOLD);

        // Algo confidence row
        LinearLayout algoRow = row(0, 4);
        String entryLabel = r.entryAlgoScore > 0
            ? "BUY " + r.entryAlgoScore + "% [" + (r.algoSignal.isEmpty() ? "SCALP" : r.algoSignal) + "]"
            : "CONF " + r.confidenceScore;
        String exitLabel = r.exitAlgoScore > 0
            ? "SELL " + r.exitAlgoScore + "%"
            : "";
        TextView entryTag = tv(entryLabel, 11, 0xFF00E5FF);
        entryTag.setPadding(dp(6), dp(3), dp(6), dp(3));
        GradientDrawable etBg = new GradientDrawable(); etBg.setColor(0x2200E5FF); etBg.setCornerRadius(dp(6));
        entryTag.setBackground(etBg);
        algoRow.addView(entryTag);
        if (!exitLabel.isEmpty()) {
            algoRow.addView(tv(" → ", 11, GREY));
            TextView exitTag = tv(exitLabel, 11, r.isWin ? GREEN : RED);
            exitTag.setPadding(dp(6), dp(3), dp(6), dp(3));
            GradientDrawable exBg = new GradientDrawable(); exBg.setColor(r.isWin ? 0x2200C853 : 0x22FF1744); exBg.setCornerRadius(dp(6));
            exitTag.setBackground(exBg);
            algoRow.addView(exitTag);
        }
        card.addView(algoRow);

        String reason = r.exitReason != null ? r.exitReason.replace('_', ' ').toUpperCase() : "";
        String mode   = r.isLive ? " • LIVE" : " • paper";
        tv2(card, reason + mode + " • " + r.strategyName, 10, AMBER, Typeface.NORMAL);
        if (r.buyTxHash != null && !r.buyTxHash.isEmpty())
            tv2(card, "Tx: " + r.buyTxHash.substring(0, Math.min(20, r.buyTxHash.length())) + "…",
                10, GREY, Typeface.NORMAL);
    }

    // ─────────────────────────────────────────────────────
    // TAB 3: POSITION
    // ─────────────────────────────────────────────────────

    private void buildPosition() {
        LinearLayout p = page();
        List<TradeRecord> open = store.getOpenPositions();

        LinearLayout header = card(p);
        tv2(header, "OPEN POSITIONS (" + open.size() + "/" + store.maxPositions + ")",
            16, WHITE, Typeface.BOLD);
        String modeLabel = store.liveMode ? "🟢 LIVE" : "📜 PAPER";
        tv2(header, modeLabel + " | SL " + store.stopLossPercent + "% | TP " + store.takeProfitPercent
            + "% | Max hold " + store.maxHoldMinutes + "min", 12, GREY, Typeface.NORMAL);

        if (open.isEmpty()) {
            LinearLayout empty = card(p);
            tv2(empty, "No open positions. Bot opens trades automatically when QUALIFIED.",
                13, GREY, Typeface.NORMAL);
        } else {
            for (TradeRecord r : open) {
                positionCard(p, r);
            }
        }
        set(p);
    }

    private void positionCard(LinearLayout parent, TradeRecord r) {
        LinearLayout card = card(parent);
        LinearLayout top = row(0, 0);
        top.addView(chainBadge("bsc".equals(r.chain) ? "BNB" : "SOL"));
        TextView name = tv("  " + r.tokenSymbol, 15, WHITE);
        name.setTypeface(null, Typeface.BOLD);
        top.addView(name);
        View sp = new View(this); sp.setLayoutParams(new LinearLayout.LayoutParams(0,0,1f));
        top.addView(sp);
        TextView conf = tv("CONF " + r.confidenceScore, 11, CYAN);
        conf.setPadding(dp(6), dp(3), dp(6), dp(3));
        GradientDrawable cbg = new GradientDrawable(); cbg.setColor(0x22000000+CYAN); cbg.setCornerRadius(dp(8));
        conf.setBackground(cbg);
        top.addView(conf);
        card.addView(top);
        card.addView(gap(4));
        // Algo signal badge
        if (r.entryAlgoScore > 0) {
            String sigLabel = "ALGO " + r.entryAlgoScore + "% • " + (r.algoSignal.isEmpty() ? "SCALP" : r.algoSignal);
            TextView sigTag = tv2(card, sigLabel, 11, 0xFF00E5FF, Typeface.BOLD);
            sigTag.setPadding(0, dp(2), 0, dp(4));
        }
        tv2(card, "Entry: $" + fmt8(r.entryPrice), 12, WHITE, Typeface.NORMAL);
        // Trailing stop display
        if (store.useTrailingStop && r.peakPrice > r.entryPrice * 1.001) {
            double trailPrice = r.peakPrice * (1 - store.trailingStopPct / 100);
            tv2(card, "Peak: $" + fmt8(r.peakPrice) + " | Trail SL: $" + fmt8(trailPrice), 11, AMBER, Typeface.NORMAL);
        }
        tv2(card, "TP: +" + store.takeProfitPercent + "% = $" + fmt8(r.entryPrice * (1 + store.takeProfitPercent/100))
            + "  |  SL: -" + store.stopLossPercent + "% = $" + fmt8(r.entryPrice * (1 - store.stopLossPercent/100)),
            11, GREY, Typeface.NORMAL);
        tv2(card, "Strategy: " + r.strategyName + " | Size: $" + String.format("%.2f", r.amountUsd),
            11, GREY, Typeface.NORMAL);
        long held = (System.currentTimeMillis() - r.openTimeMs) / 60_000L;
        tv2(card, "Open for " + held + " min | Max hold " + store.maxHoldMinutes + " min",
            11, AMBER, Typeface.NORMAL);
        if (!r.patterns.isEmpty())
            tv2(card, "Patterns: " + CandlePatterns.summary(r.patterns), 10, GREY, Typeface.NORMAL);
    }

    // ─────────────────────────────────────────────────────
    // TAB 4: WALLET
    // ─────────────────────────────────────────────────────

    private void buildWallet() {
        LinearLayout p = page();

        // BNB
        walletSection(p, "BNB Chain Wallet", store.bnbAddress, AMBER, "bnb");
        // SOL
        walletSection(p, "Solana Wallet", store.solAddress, PURPLE, "sol");

        // Withdraw
        LinearLayout wdCard = card(p);
        tv2(wdCard, "WITHDRAW", 14, WHITE, Typeface.BOLD);
        tv2(wdCard, "Requires live mode. Signing not yet active in paper mode.",
            11, GREY, Typeface.NORMAL);
        wdCard.addView(gap(8));
        withdrawAddressField = input(wdCard, "Destination address");
        EditText amount = input(wdCard, "Amount");
        amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        wdCard.addView(gap(4));
        Button qrScanBtn = bigBtn("Scan QR Address (coming soon)", CARD);
        qrScanBtn.setOnClickListener(v ->
            Toast.makeText(this, "QR scanner will be added in next update", Toast.LENGTH_SHORT).show());
        wdCard.addView(qrScanBtn);
        wdCard.addView(gap(8));
        Button wdBtn = bigBtn(store.liveMode ? "Withdraw" : "Signing not yet active", GREY);
        wdBtn.setOnClickListener(v -> {
            if (!store.liveMode) {
                alert("Withdraw disabled", "Enable live mode in Control tab first. Paper mode cannot sign transactions.");
            } else {
                alert("Confirm Withdraw",
                    "Send " + amount.getText() + " to:\n" + withdrawAddressField.getText()
                    + "\n\nThis is a real transaction.");
            }
        });
        wdCard.addView(wdBtn);

        // Recovery phrase
        LinearLayout recCard = card(p);
        tv2(recCard, "RECOVERY PHRASE", 14, WHITE, Typeface.BOLD);
        tv2(recCard, "Keep secret. Anyone with it can steal your funds.", 11, RED, Typeface.NORMAL);
        recCard.addView(gap(8));
        Button showBtn = bigBtn("Show Recovery Phrase", RED);
        showBtn.setOnClickListener(v -> {
            String m = store.getMnemonic();
            if (m != null)
                new AlertDialog.Builder(this)
                    .setTitle("Recovery Phrase")
                    .setMessage(m)
                    .setPositiveButton("Copy", (d, w) -> copy(m))
                    .setNegativeButton("Close", null)
                    .show();
        });
        recCard.addView(showBtn);
        set(p);
    }

    private void walletSection(LinearLayout parent, String title, String address, int color, String chain) {
        LinearLayout card = card(parent);
        tv2(card, title, 14, color, Typeface.BOLD);
        card.addView(gap(4));
        if (address == null || address.isEmpty()) {
            tv2(card, "Address not derived yet. Restart app to retry wallet setup.", 12, AMBER, Typeface.NORMAL);
            return;
        }
        TextView addrTv = tv(address, 11, WHITE);
        addrTv.setOnClickListener(v -> copy(address));
        card.addView(addrTv);
        card.addView(gap(4));
        // Balance row
        TextView balTv = tv2(card, "Balance: loading…", 13, GREY, Typeface.NORMAL);
        fetchBalance(chain, address, bal -> ui.post(() -> balTv.setText("Balance: " + bal)));
        card.addView(gap(8));
        // QR code
        Bitmap qr = qrCode(address, dp(200));
        if (qr != null) {
            ImageView iv = new ImageView(this);
            iv.setImageBitmap(qr);
            iv.setLayoutParams(new LinearLayout.LayoutParams(dp(200), dp(200)));
            iv.setPadding(dp(8), dp(8), dp(8), dp(8));
            iv.setBackgroundColor(WHITE);
            card.addView(iv);
        }
        card.addView(gap(4));
        Button copyBtn = bigBtn("Copy Address", CARD);
        copyBtn.setOnClickListener(v -> {
            copy(address);
            Toast.makeText(this, "Address copied!", Toast.LENGTH_SHORT).show();
        });
        card.addView(copyBtn);
    }

    // ─────────────────────────────────────────────────────
    // TAB 5: CONTROL
    // ─────────────────────────────────────────────────────

    private void buildControl() {
        LinearLayout p = page();

        // ── AUTO MODE CARD ─────────────────────────────────────────────
        LinearLayout autoCard = card(p);
        GradientDrawable autoBg = new GradientDrawable();
        autoBg.setColor(store.autoMode ? 0xFF1A1040 : CARD);
        autoBg.setCornerRadius(dp(12));
        autoBg.setStroke(dp(1), store.autoMode ? PURPLE : 0xFF30363D);
        autoCard.setBackground(autoBg);

        LinearLayout autoHead = row(0, 0);
        TextView autoTitle = tv("AUTO MODE", 16, store.autoMode ? PURPLE : WHITE);
        autoTitle.setTypeface(null, Typeface.BOLD);
        autoTitle.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        autoHead.addView(autoTitle);
        android.widget.Switch autoSwitch = new android.widget.Switch(this);
        autoSwitch.setChecked(store.autoMode);
        autoSwitch.setOnCheckedChangeListener((b, checked) -> {
            store.autoMode = checked;
            store.save();
            buildControl();
        });
        autoHead.addView(autoSwitch);
        autoCard.addView(autoHead);
        autoCard.addView(gap(6));

        if (store.autoMode) {
            tv2(autoCard, "ACTIVE — ML is fully in control", 13, PURPLE, Typeface.BOLD);
            tv2(autoCard, "The bot reads all market signals and decides entry, exit, SL, TP, "
                + "and hold time on its own. Everything it learns in paper mode carries over to live mode. "
                + "Manual overrides for SL/TP are locked.", 11, GREY, Typeface.NORMAL);
            autoCard.addView(gap(6));
            tv2(autoCard, "Current ML params (auto-managed):", 11, CYAN, Typeface.BOLD);
            tv2(autoCard, "Strategy: " + store.mlStrategy
                + "  |  Gen: " + store.mlGeneration
                + "  |  WR: " + String.format("%.0f%%", store.mlWinRate), 11, WHITE, Typeface.NORMAL);
            tv2(autoCard, "SL: " + store.stopLossPercent + "%"
                + "  |  TP: " + store.takeProfitPercent + "%"
                + "  |  Hold: " + store.maxHoldMinutes + "min"
                + "  |  AlgoMin: " + store.minAlgoScore, 11, WHITE, Typeface.NORMAL);
        } else {
            tv2(autoCard, "DISABLED — Manual mode active", 13, GREY, Typeface.NORMAL);
            tv2(autoCard, "Enable to let the ML bot decide all trade parameters automatically. "
                + "It evolves with every closed trade in paper mode and the same learned strategy "
                + "applies seamlessly when you switch to live mode.", 11, GREY, Typeface.NORMAL);
        }

        // ML Evolution Status Card
        LinearLayout mlCard = card(p);
        tv2(mlCard, "ML EVOLUTION STATUS", 14, PURPLE, Typeface.BOLD);
        mlCard.addView(gap(4));
        tv2(mlCard, BotEvolution.summary(store), 12, WHITE, Typeface.NORMAL);
        tv2(mlCard, "Last change: " + BotEvolution.lastChange(), 11, GREY, Typeface.NORMAL);
        mlCard.addView(gap(8));
        LinearLayout mlBtns = row(0, 0);
        Button evolveNow = btn("Evolve Now", PURPLE, v -> {
            BotEvolution.evolve(store);
            Toast.makeText(this, "ML evolved! Gen " + store.mlGeneration, Toast.LENGTH_SHORT).show();
            buildControl();
        });
        Button resetMl = btn("Reset ML", AMBER, v ->
            new AlertDialog.Builder(this)
                .setTitle("Reset ML?")
                .setMessage("This resets all ML parameters back to defaults. Continue?")
                .setPositiveButton("Reset", (d, w) -> {
                    BotEvolution.reset(store);
                    Toast.makeText(this, "ML reset to defaults", Toast.LENGTH_SHORT).show();
                    buildControl();
                })
                .setNegativeButton("Cancel", null).show());
        mlBtns.addView(evolveNow); mlBtns.addView(gap(8)); mlBtns.addView(resetMl);
        mlCard.addView(mlBtns);

        // Scanner settings
        LinearLayout scanCard = card(p);
        tv2(scanCard, "SCANNER SETTINGS", 14, CYAN, Typeface.BOLD);
        scanCard.addView(gap(8));
        EditText scanInt = infoRow(scanCard, "Scan interval (min)",
            String.valueOf(store.scanIntervalMin),
            "How often the bot checks DEX Screener for new tokens (minutes). Lower = more frequent but more API calls.");
        EditText minLiq  = infoRow(scanCard, "Min liquidity (USD)",
            String.valueOf((int)store.minLiquidityUsd),
            "Minimum pool liquidity in USD. Tokens below this are too illiquid to trade safely.");
        EditText minAge  = infoRow(scanCard, "Min pair age (hours)",
            String.valueOf(store.minPairAgeHours),
            "Minimum age of the trading pair in hours. Newer pairs = higher rug risk.");
        EditText minMom  = infoRow(scanCard, "Min momentum (% 1h)",
            String.valueOf(store.minMomentumPercent),
            "Minimum 1-hour price movement % to consider a token. Filters out stagnant tokens.");
        EditText maxPos  = infoRow(scanCard, "Max simultaneous positions",
            String.valueOf(store.maxPositions),
            "Maximum number of open paper positions at once. Prevents over-exposure.");
        EditText maxDaily = infoRow(scanCard, "Max trades per day",
            String.valueOf(store.maxDailyTrades),
            "Maximum number of trades the bot opens in one day. Resets at midnight. "
            + "Set lower to limit exposure. 0 = unlimited.");
        scanCard.addView(gap(8));
        Button saveBtn = bigBtn("Save Scanner Settings", CYAN);
        saveBtn.setOnClickListener(v -> {
            try {
                store.scanIntervalMin    = Integer.parseInt(scanInt.getText().toString());
                store.minLiquidityUsd   = Double.parseDouble(minLiq.getText().toString());
                store.minPairAgeHours   = Integer.parseInt(minAge.getText().toString());
                store.minMomentumPercent = Double.parseDouble(minMom.getText().toString());
                store.maxPositions      = Integer.parseInt(maxPos.getText().toString());
                int daily = Integer.parseInt(maxDaily.getText().toString());
                store.maxDailyTrades    = daily <= 0 ? Integer.MAX_VALUE : daily;
                store.save();
                Toast.makeText(this, "Scanner settings saved", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Invalid input", Toast.LENGTH_SHORT).show();
            }
        });
        scanCard.addView(saveBtn);

        // Strategy / Scalping settings
        LinearLayout stratCard = card(p);
        tv2(stratCard, "SCALPING SETTINGS", 14, store.autoMode ? GREY : CYAN, Typeface.BOLD);
        if (store.autoMode) {
            tv2(stratCard, "Locked in Auto Mode — ML manages these automatically.", 11, PURPLE, Typeface.NORMAL);
            stratCard.addView(gap(4));
            tv2(stratCard, "SL: " + store.stopLossPercent + "%  |  TP: " + store.takeProfitPercent
                + "%  |  Hold: " + store.maxHoldMinutes + "min", 13, WHITE, Typeface.BOLD);
        } else {
            tv2(stratCard, "ML evolves these automatically. Manual override here.", 11, GREY, Typeface.NORMAL);
            stratCard.addView(gap(8));
            EditText sl   = infoRow(stratCard, "Stop loss (%)",
                String.valueOf(store.stopLossPercent),
                "Exit position when price drops this % below entry. Protects against large losses.");
            EditText tp   = infoRow(stratCard, "Take profit (%)",
                String.valueOf(store.takeProfitPercent),
                "Exit position when price rises this % above entry. Locks in profit.");
            EditText maxH = infoRow(stratCard, "Max hold (minutes)",
                String.valueOf(store.maxHoldMinutes),
                "Close position after this many minutes even if TP/SL not hit. Prevents dead positions.");
            stratCard.addView(gap(8));
            Button stratSave = bigBtn("Save Strategy Settings", CYAN);
            stratSave.setOnClickListener(v -> {
                try {
                    store.stopLossPercent   = Double.parseDouble(sl.getText().toString());
                    store.takeProfitPercent = Double.parseDouble(tp.getText().toString());
                    store.maxHoldMinutes    = Integer.parseInt(maxH.getText().toString());
                    store.save();
                    Toast.makeText(this, "Strategy settings saved", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(this, "Invalid input", Toast.LENGTH_SHORT).show();
                }
            });
            stratCard.addView(stratSave);
        }

        // TP Targets
        LinearLayout tpCard = card(p);
        tv2(tpCard, "TAKE-PROFIT TARGETS", 14, CYAN, Typeface.BOLD);
        tv2(tpCard, "Set 3 profit targets manually. Bot shows these on position cards for reference.", 11, GREY, Typeface.NORMAL);
        tpCard.addView(gap(8));
        EditText tp1 = infoRow(tpCard, "Target 1 (%)", String.valueOf(store.tp1Percent),
            "First take-profit target. Consider closing 1/3 of position here.");
        EditText tp2 = infoRow(tpCard, "Target 2 (%)", String.valueOf(store.tp2Percent),
            "Second take-profit target. Consider closing another 1/3 here.");
        EditText tp3 = infoRow(tpCard, "Target 3 (%)", String.valueOf(store.tp3Percent),
            "Third take-profit target (moon target). Let the last 1/3 ride.");
        tpCard.addView(gap(8));
        Button tpSave = bigBtn("Save TP Targets", CYAN);
        tpSave.setOnClickListener(v -> {
            try {
                store.tp1Percent = Double.parseDouble(tp1.getText().toString());
                store.tp2Percent = Double.parseDouble(tp2.getText().toString());
                store.tp3Percent = Double.parseDouble(tp3.getText().toString());
                store.save();
                Toast.makeText(this, "TP targets saved", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Invalid input", Toast.LENGTH_SHORT).show();
            }
        });
        tpCard.addView(tpSave);

        // Algo Trading Settings
        LinearLayout algoCard = card(p);
        tv2(algoCard, "ALGO TRADING", 14, store.autoMode ? GREY : CYAN, Typeface.BOLD);
        tv2(algoCard, "RSI, MACD-like signals and volume confirmation filter entries.", 11, GREY, Typeface.NORMAL);
        algoCard.addView(gap(8));
        if (store.autoMode) {
            tv2(algoCard, "Locked in Auto Mode — ML manages algo score and trailing stop.",
                11, PURPLE, Typeface.NORMAL);
            tv2(algoCard, "AlgoMin: " + store.minAlgoScore
                + "  |  Trail: " + store.trailingStopPct + "%"
                + "  |  Trailing stop: " + (store.useTrailingStop ? "ON" : "OFF"),
                13, WHITE, Typeface.BOLD);
        } else {
            EditText minAlgo  = infoRow(algoCard, "Min algo score to enter (0–100)",
                String.valueOf(store.minAlgoScore),
                "Minimum AlgoEngine score (0-100) required to open a position. Higher = more selective, fewer trades.");
            EditText trailPct = infoRow(algoCard, "Trailing stop distance (%)",
                String.valueOf(store.trailingStopPct),
                "Once price rises, stop loss trails this % below the peak price. Locks in unrealised profit.");
            algoCard.addView(gap(4));
            LinearLayout trailRow = row(0, 0);
            TextView trailLabel = tv2(trailRow, "Trailing stop loss", 13, WHITE, Typeface.NORMAL);
            trailLabel.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            android.widget.Switch trailSwitch = new android.widget.Switch(this);
            trailSwitch.setChecked(store.useTrailingStop);
            trailSwitch.setOnCheckedChangeListener((b, checked) -> { store.useTrailingStop = checked; store.save(); });
            trailRow.addView(trailSwitch);
            algoCard.addView(trailRow);
            algoCard.addView(gap(8));
            double rsiEx = AlgoEngine.approximateRsi(2.5, 5.0);
            tv2(algoCard, "Example: RSI≈" + (int)rsiEx + " at +2.5% 1h / +5% 24h", 10, GREY, Typeface.NORMAL);
            algoCard.addView(gap(4));
            Button algoSave = bigBtn("Save Algo Settings", CYAN);
            algoSave.setOnClickListener(v -> {
                try {
                    store.minAlgoScore    = Integer.parseInt(minAlgo.getText().toString());
                    store.trailingStopPct = Double.parseDouble(trailPct.getText().toString());
                    store.save();
                    Toast.makeText(this, "Algo settings saved", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(this, "Invalid input", Toast.LENGTH_SHORT).show();
                }
            });
            algoCard.addView(algoSave);
        }

        // Telegram
        LinearLayout tgCard = card(p);
        tv2(tgCard, "TELEGRAM ALERTS", 14, CYAN, Typeface.BOLD);
        tv2(tgCard, "Get notified on every trade open/close.", 11, GREY, Typeface.NORMAL);
        tgCard.addView(gap(8));
        EditText tgToken = infoRow(tgCard, "Bot Token (from @BotFather)", store.telegramToken,
            "Create a Telegram bot via @BotFather. It gives you a token like 123456:ABC-xyz. Paste it here.");
        EditText tgChat  = infoRow(tgCard, "Chat ID (from @userinfobot)", store.telegramChatId,
            "Message @userinfobot on Telegram to get your chat ID. It's a number like 987654321.");
        tgCard.addView(gap(8));
        LinearLayout tgBtns = row(0, 0);
        Button tgSave = btn("Save", CYAN, v -> {
            store.telegramToken  = tgToken.getText().toString().trim();
            store.telegramChatId = tgChat.getText().toString().trim();
            store.save();
            Toast.makeText(this, "Telegram saved", Toast.LENGTH_SHORT).show();
        });
        tgBtns.addView(tgSave);
        tgBtns.addView(gap(8));
        Button tgTest = btn("Test", GREY, v ->
            TelegramBot.send(store, "🤖 Nanu AI Bot is connected! Telegram alerts are working."));
        tgBtns.addView(tgTest);
        tgCard.addView(tgBtns);

        // Live mode
        LinearLayout liveCard = card(p);
        tv2(liveCard, "🚨 LIVE TRADING MODE", 14, RED, Typeface.BOLD);
        String liveStatus = store.liveMode ? "🟢 ACTIVE — Real money at risk" : "⚪ DISABLED — Paper trading only";
        tv2(liveCard, liveStatus, 12, store.liveMode ? RED : GREEN, Typeface.BOLD);
        liveCard.addView(gap(8));
        EditText tradeAmt = settingRow(liveCard, "Trade amount per position (USD)",
            store.tradeAmountUsd > 0 ? String.valueOf(store.tradeAmountUsd) : "");
        liveCard.addView(gap(6));
        LinearLayout chainRow = row(0, 4);
        CheckBox bnbChk = new CheckBox(this); bnbChk.setText("BNB Chain");
        bnbChk.setTextColor(WHITE); bnbChk.setChecked(store.liveChainBnb);
        CheckBox solChk = new CheckBox(this); solChk.setText("Solana");
        solChk.setTextColor(WHITE); solChk.setChecked(store.liveChainSol);
        chainRow.addView(bnbChk); chainRow.addView(gap(16)); chainRow.addView(solChk);
        liveCard.addView(chainRow);
        liveCard.addView(gap(8));
        if (!store.liveMode) {
            tv2(liveCard, "To enable: enter amount, select chains, type I UNDERSTAND, then confirm.",
                11, GREY, Typeface.NORMAL);
            liveCard.addView(gap(4));
            EditText confirm = input(liveCard, "Type: I UNDERSTAND");
            liveCard.addView(gap(8));
            Button enableBtn = bigBtn("Enable Live Trading", RED);
            enableBtn.setOnClickListener(v -> {
                double amt = 0;
                try { amt = Double.parseDouble(tradeAmt.getText().toString()); } catch (Exception ignored) {}
                if (amt <= 0) { Toast.makeText(this, "Set trade amount first", Toast.LENGTH_SHORT).show(); return; }
                if (!bnbChk.isChecked() && !solChk.isChecked()) { Toast.makeText(this, "Select at least one chain", Toast.LENGTH_SHORT).show(); return; }
                if (!"I UNDERSTAND".equals(confirm.getText().toString().trim())) { Toast.makeText(this, "Type exactly: I UNDERSTAND", Toast.LENGTH_SHORT).show(); return; }
                double finalAmt = amt;
                new AlertDialog.Builder(this)
                    .setTitle("🚨 FINAL WARNING")
                    .setMessage("You are enabling LIVE TRADING.\n\nTrades of $" + finalAmt
                        + " will be signed and broadcast to the blockchain automatically.\n\nThis is real money. You can lose it all.")
                    .setPositiveButton("Yes, Enable Live Mode", (d, w) -> {
                        store.liveMode     = true;
                        store.tradeAmountUsd = finalAmt;
                        store.liveChainBnb = bnbChk.isChecked();
                        store.liveChainSol = solChk.isChecked();
                        store.save();
                        refreshCurrentTab();
                        Toast.makeText(this, "🟢 LIVE MODE ENABLED", Toast.LENGTH_LONG).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            });
            liveCard.addView(enableBtn);
        } else {
            Button disableBtn = bigBtn("Disable Live Mode (back to paper)", GREEN);
            disableBtn.setOnClickListener(v -> {
                store.liveMode = false;
                store.save();
                refreshCurrentTab();
            });
            liveCard.addView(disableBtn);
        }

        set(p);
    }

    // ─────────────────────────────────────────────────────
    // ENGINE LISTENER
    // ─────────────────────────────────────────────────────

    private final DexEngine.Listener engineListener = new DexEngine.Listener() {
        @Override public void onScanStart() {
            ui.post(() -> { if (tab == 0 || tab == 1) refreshCurrentTab(); });
        }
        @Override public void onScanResult(List<DexCandidate> c) {
            lastCandidates = c;
            store.reload();
            ui.post(() -> refreshCurrentTab());
        }
        @Override public void onPositionOpened(TradeRecord r) {
            store.reload();
            ui.post(() -> { if (tab == 0 || tab == 3) refreshCurrentTab(); });
        }
        @Override public void onPositionClosed(TradeRecord r) {
            store.reload();
            ui.post(() -> refreshCurrentTab());
        }
        @Override public void onError(String msg) {
            ui.post(() -> Toast.makeText(DexActivity.this, msg, Toast.LENGTH_SHORT).show());
        }
    };

    // ─────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────

    private void startEngine() {
        TradeService.start(this);
        buildHome();
    }

    private void panicClose() {
        new AlertDialog.Builder(this)
            .setTitle("PANIC CLOSE")
            .setMessage("Close ALL open positions immediately at current price?")
            .setPositiveButton("Yes, close all", (d, w) -> {
                TradeService.panic(this);
                Toast.makeText(this, "All positions closed", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void exportPdf(List<TradeRecord> trades) {
        try {
            android.graphics.pdf.PdfDocument doc = new android.graphics.pdf.PdfDocument();
            android.graphics.Paint paint = new android.graphics.Paint();
            android.graphics.Paint headPaint = new android.graphics.Paint();
            headPaint.setTypeface(Typeface.DEFAULT_BOLD);
            headPaint.setTextSize(13);
            paint.setTextSize(10);

            int pageW = 595, pageH = 842, margin = 36, lineH = 16;
            int pageNum = 1;
            android.graphics.pdf.PdfDocument.PageInfo pageInfo =
                new android.graphics.pdf.PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create();
            android.graphics.pdf.PdfDocument.Page page = doc.startPage(pageInfo);
            android.graphics.Canvas canvas = page.getCanvas();

            headPaint.setColor(android.graphics.Color.BLACK);
            canvas.drawText("NANU AI Trading Bot — Trade History", margin, margin + 20, headPaint);
            paint.setColor(android.graphics.Color.DKGRAY);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
            canvas.drawText("Generated: " + sdf.format(new Date()) + "  |  Total trades: " + trades.size(),
                margin, margin + 36, paint);

            int y = margin + 56;
            paint.setColor(android.graphics.Color.BLACK);

            String[] headers = {"Date", "Token", "Chain", "Entry", "Exit", "P/L USD", "P/L%", "Result", "Strategy"};
            int[] cols = {margin, 110, 195, 235, 295, 355, 415, 460, 510};

            headPaint.setTextSize(9);
            for (int i = 0; i < headers.length; i++)
                canvas.drawText(headers[i], cols[i], y, headPaint);
            y += lineH;
            paint.setColor(android.graphics.Color.LTGRAY);
            canvas.drawLine(margin, y, pageW - margin, y, paint);
            y += 4;
            paint.setColor(android.graphics.Color.BLACK);
            paint.setTextSize(8);

            for (TradeRecord r : trades) {
                if (y > pageH - margin - lineH) {
                    doc.finishPage(page);
                    pageNum++;
                    pageInfo = new android.graphics.pdf.PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create();
                    page = doc.startPage(pageInfo);
                    canvas = page.getCanvas();
                    y = margin + 20;
                }
                String[] row = {
                    sdf.format(new Date(r.openTimeMs)),
                    (r.tokenSymbol != null ? r.tokenSymbol : "?"),
                    "bsc".equals(r.chain) ? "BNB" : "SOL",
                    fmt8(r.entryPrice), fmt8(r.exitPrice),
                    String.format("%+.4f", r.pnlUsd),
                    String.format("%+.2f%%", r.pnlPercent),
                    r.isWin ? "WIN" : "LOSS",
                    r.strategyName != null ? r.strategyName : ""
                };
                paint.setColor(r.isWin ? android.graphics.Color.rgb(0, 120, 60)
                    : android.graphics.Color.rgb(160, 0, 0));
                for (int i = 0; i < row.length && i < cols.length; i++)
                    canvas.drawText(row[i], cols[i], y, paint);
                y += lineH;
            }
            doc.finishPage(page);

            File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (dir == null) dir = getFilesDir();
            File f = new File(dir, "nanu_trades_" +
                new SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(new Date()) + ".pdf");
            doc.writeTo(new FileOutputStream(f));
            doc.close();
            Toast.makeText(this, "PDF saved: " + f.getName(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "PDF export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private Bitmap qrCode(String text, int size) {
        try {
            com.google.zxing.qrcode.QRCodeWriter w = new com.google.zxing.qrcode.QRCodeWriter();
            com.google.zxing.common.BitMatrix m =
                w.encode(text, com.google.zxing.BarcodeFormat.QR_CODE, size, size);
            Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);
            for (int x = 0; x < size; x++)
                for (int y = 0; y < size; y++)
                    bmp.setPixel(x, y, m.get(x, y) ? Color.BLACK : Color.WHITE);
            return bmp;
        } catch (Exception e) { return null; }
    }

    private int statusBarHeight() {
        int result = 0;
        int rid = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (rid > 0) result = getResources().getDimensionPixelSize(rid);
        return result;
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    private TextView tv(String text, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(text); t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        return t;
    }

    private TextView tv2(LinearLayout p, String text, int sp, int color, int style) {
        TextView t = tv(text, sp, color);
        t.setTypeface(null, style);
        t.setPadding(0, dp(2), 0, dp(2));
        p.addView(t);
        return t;
    }

    private LinearLayout page() {
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setPadding(dp(12), dp(8), dp(12), dp(28));
        return ll;
    }

    private LinearLayout card(LinearLayout parent) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(14), dp(14), dp(14), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), 0xFF30363D);
        c.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(10));
        c.setLayoutParams(lp);
        if (parent != null) parent.addView(c);
        return c;
    }

    private LinearLayout row(int hPad, int vPad) {
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.HORIZONTAL);
        ll.setGravity(Gravity.CENTER_VERTICAL);
        ll.setPadding(hPad, vPad, hPad, vPad);
        ll.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        return ll;
    }

    private View gap(int dp) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(dp), dp(dp)));
        return v;
    }

    private Button btn(String label, int color, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label); b.setTextColor(Color.BLACK);
        b.setTypeface(null, Typeface.BOLD);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color); bg.setCornerRadius(dp(20));
        b.setBackground(bg);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(44), 1f);
        b.setLayoutParams(lp);
        return b;
    }

    private Button bigBtn(String label, int color) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(color == CARD || color == 0xFF0D1117 ? WHITE : Color.BLACK);
        b.setTypeface(null, Typeface.BOLD);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color); bg.setCornerRadius(dp(10));
        b.setBackground(bg);
        b.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        return b;
    }

    private LinearLayout statBox(String label, String value, int valColor) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(10), dp(8), dp(10), dp(8));
        box.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF0D1117); bg.setCornerRadius(dp(8));
        box.setBackground(bg);
        TextView lbl = tv(label, 10, GREY); lbl.setTypeface(null, Typeface.BOLD);
        box.addView(lbl);
        TextView val = tv(value, 16, valColor); val.setTypeface(null, Typeface.BOLD);
        box.addView(val);
        return box;
    }

    private TextView chainBadge(String chain) {
        TextView t = tv(chain, 10, Color.BLACK);
        t.setTypeface(null, Typeface.BOLD);
        t.setPadding(dp(6), dp(3), dp(6), dp(3));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor("BNB".equals(chain) ? AMBER : PURPLE);
        bg.setCornerRadius(dp(10));
        t.setBackground(bg);
        return t;
    }

    private View statusBadge(DexCandidate c) {
        // Show algo score if available, otherwise safety score
        String scoreStr = c.algoScore > 0
            ? c.status + " " + c.score + " | ALGO " + c.algoScore + "%"
            : c.status + " " + c.score;
        int color = "QUALIFIED".equals(c.status) ? GREEN
            : "BLOCKED".equals(c.status) ? RED : AMBER;
        TextView t = tv(scoreStr, 11, Color.BLACK);
        t.setTypeface(null, Typeface.BOLD);
        t.setPadding(dp(8), dp(4), dp(8), dp(4));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color); bg.setCornerRadius(dp(12));
        t.setBackground(bg);
        return t;
    }

    private TextView patternPill(String name, int color) {
        TextView t = tv(name.replace('_',' '), 9, color);
        t.setPadding(dp(5), dp(2), dp(5), dp(2));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x22000000 | (color & 0xFFFFFF));
        bg.setStroke(1, color); bg.setCornerRadius(dp(8));
        t.setBackground(bg);
        return t;
    }

    private GradientDrawable pill(int color, float alpha) {
        GradientDrawable d = new GradientDrawable();
        int a = (int)(alpha * 255) << 24;
        d.setColor(a | (color & 0xFFFFFF));
        d.setCornerRadius(dp(20));
        return d;
    }

    private EditText input(LinearLayout parent, String hint) {
        EditText et = new EditText(this);
        et.setHint(hint); et.setHintTextColor(GREY);
        et.setTextColor(WHITE);
        et.setBackgroundColor(0xFF0D1117);
        et.setPadding(dp(10), dp(10), dp(10), dp(10));
        et.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        if (parent != null) parent.addView(et);
        return et;
    }

    private EditText settingRow(LinearLayout parent, String label, String value) {
        tv2(parent, label, 11, GREY, Typeface.NORMAL);
        EditText et = new EditText(this);
        et.setText(value); et.setTextColor(WHITE);
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        et.setBackgroundColor(0xFF0D1117);
        et.setPadding(dp(10), dp(8), dp(10), dp(8));
        et.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
        parent.addView(et);
        parent.addView(gap(6));
        return et;
    }

    private void set(LinearLayout p) {
        ScrollView sv = tabViews[tab];
        sv.removeAllViews();
        sv.addView(p);
    }

    private void copy(String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("nanu", text));
    }

    private void alert(String title, String msg) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(msg)
            .setPositiveButton("OK", null).show();
    }

    private String fmt8(double v) {
        if (v == 0) return "0";
        if (v < 0.0001) return String.format("%.10f", v).replaceAll("0+$", "");
        return String.format("%.8f", v).replaceAll("0+$", "").replaceAll("\\.+$", "");
    }

    private String fmtK(double v) {
        if (v >= 1_000_000) return String.format("%.1fM", v / 1_000_000);
        if (v >= 1_000) return String.format("%.1fK", v / 1_000);
        return String.format("%.0f", v);
    }

    // Setting row with ⓘ info icon
    private EditText infoRow(LinearLayout parent, String label, String value, String info) {
        LinearLayout labelRow = row(0, 0);
        TextView lbl = tv(label, 11, GREY);
        lbl.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        labelRow.addView(lbl);
        TextView infoBtn = tv("  ⓘ", 14, CYAN);
        infoBtn.setOnClickListener(v -> alert(label, info));
        labelRow.addView(infoBtn);
        parent.addView(labelRow);
        EditText et = new EditText(this);
        et.setText(value); et.setTextColor(WHITE);
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        et.setBackgroundColor(0xFF0D1117);
        et.setPadding(dp(10), dp(8), dp(10), dp(8));
        et.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
        parent.addView(et);
        parent.addView(gap(6));
        return et;
    }

    interface BalanceCallback { void onBalance(String text); }

    private void fetchBalance(String chain, String address, BalanceCallback cb) {
        if (address == null || address.isEmpty()) { cb.onBalance("N/A"); return; }
        new Thread(() -> {
            try {
                String result;
                if ("bnb".equals(chain)) {
                    String url = "https://api.bscscan.com/api?module=account&action=balance&address="
                        + address + "&tag=latest";
                    java.net.HttpURLConnection conn =
                        (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                    conn.setConnectTimeout(8000); conn.setReadTimeout(8000);
                    conn.setRequestProperty("User-Agent", "NanuBot/11.1");
                    if (conn.getResponseCode() == 200) {
                        java.io.BufferedReader br = new java.io.BufferedReader(
                            new java.io.InputStreamReader(conn.getInputStream()));
                        StringBuilder sb = new StringBuilder(); String line;
                        while ((line = br.readLine()) != null) sb.append(line);
                        br.close();
                        org.json.JSONObject j = new org.json.JSONObject(sb.toString());
                        if ("1".equals(j.optString("status"))) {
                            double wei = Double.parseDouble(j.getString("result"));
                            result = String.format("%.6f BNB", wei / 1e18);
                        } else result = "N/A";
                    } else result = "N/A";
                } else {
                    // SOL RPC
                    String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"getBalance\","
                        + "\"params\":[\"" + address + "\"]}";
                    java.net.HttpURLConnection conn =
                        (java.net.HttpURLConnection) new java.net.URL(
                            "https://api.mainnet-beta.solana.com").openConnection();
                    conn.setConnectTimeout(8000); conn.setReadTimeout(8000);
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setRequestProperty("User-Agent", "NanuBot/11.1");
                    conn.setDoOutput(true);
                    conn.getOutputStream().write(body.getBytes("UTF-8"));
                    if (conn.getResponseCode() == 200) {
                        java.io.BufferedReader br = new java.io.BufferedReader(
                            new java.io.InputStreamReader(conn.getInputStream()));
                        StringBuilder sb = new StringBuilder(); String line;
                        while ((line = br.readLine()) != null) sb.append(line);
                        br.close();
                        org.json.JSONObject j = new org.json.JSONObject(sb.toString());
                        long lamports = j.optJSONObject("result") != null
                            ? j.getJSONObject("result").optLong("value", -1) : -1;
                        result = lamports >= 0 ? String.format("%.6f SOL", lamports / 1e9) : "N/A";
                    } else result = "N/A";
                }
                final String r = result;
                cb.onBalance(r);
            } catch (Exception e) { cb.onBalance("N/A"); }
        }, "nanu-balance").start();
    }
}
