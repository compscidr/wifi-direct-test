package io.left.wifidirecttest;

import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class ConnectivityMonitor extends ConnectivityManager.NetworkCallback {

    private static final String TAG = ConnectivityManager.class.getName();

    @Override
    public void onAvailable(@NonNull Network network) {
        super.onAvailable(network);
        Log.d(TAG,"Network available: " + network);
    }

    @Override
    public void onBlockedStatusChanged(@NonNull Network network, boolean blocked) {
        super.onBlockedStatusChanged(network, blocked);
        Log.d(TAG,"Network " + network + " blocked? " + blocked);
    }

    @Override
    public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities networkCapabilities) {
        super.onCapabilitiesChanged(network, networkCapabilities);
        Log.d(TAG,"Network " + network + " capabilities changed: " + networkCapabilities);
    }

    @Override
    public void onLinkPropertiesChanged(@NonNull Network network, @NonNull LinkProperties linkProperties) {
        super.onLinkPropertiesChanged(network, linkProperties);
        Log.d(TAG,"Network " + network + " link properties changed: " + linkProperties);
    }

    @Override
    public void onLosing(@NonNull Network network, int maxMsToLive) {
        super.onLosing(network, maxMsToLive);
        Log.d(TAG,"Network about to be lost: " + network + " maxMsToLive: " + maxMsToLive);
    }

    @Override
    public void onLost(@NonNull Network network) {
        super.onLost(network);
        Log.d(TAG,"Network lost: " + network);
    }

    @Override
    public void onUnavailable() {
        super.onUnavailable();
        Log.d(TAG,"Network unavailable");
    }
}
