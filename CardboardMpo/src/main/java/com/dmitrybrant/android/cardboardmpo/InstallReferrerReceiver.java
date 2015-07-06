package com.dmitrybrant.android.cardboardmpo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public final class InstallReferrerReceiver extends BroadcastReceiver {
    static final String INSTALL_ACTION = "com.android.vending.INSTALL_REFERRER";
    static final String REFERRER_KEY = "referrer";

    public void onReceive(Context ctx, Intent intent) {
        String campaign = intent.getStringExtra(REFERRER_KEY);
        if (!INSTALL_ACTION.equals(intent.getAction()) || campaign == null) {
            return;
        }
        Log.d("InstallReferrerReceiver", ">>>>>>>>>>>>>> RECEIVED REFERRER: " + campaign);
    }
}
