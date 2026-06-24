package com.nanu.aitradingbot;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        DexAppStore store = new DexAppStore(ctx);
        if (store.botRunning) {
            Log.i("BootReceiver", "Restarting Nanu bot after boot");
            TradeService.start(ctx);
        }
    }
}
