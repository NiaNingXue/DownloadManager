package com.nianing.downloadmanager;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

class SystemFacade{
    private Context mContext;

    public SystemFacade(Context context) {
        mContext = context;
    }

    public long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    public NetworkInfo getActiveNetworkInfo() {
        ConnectivityManager connectivity =
                (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivity == null) {
            return null;
        }

        final NetworkInfo activeInfo = connectivity.getActiveNetworkInfo();
        return activeInfo;
    }
}
