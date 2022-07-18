package io.left.wifidirecttest;

import static android.net.wifi.p2p.WifiP2pManager.BUSY;
import static android.net.wifi.p2p.WifiP2pManager.ERROR;
import static android.net.wifi.p2p.WifiP2pManager.P2P_UNSUPPORTED;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;

public class AndroidUtil {
    public static final int REQUEST_ACCESS_FINE_LOCATION = 1;

    public static String TAG = AndroidUtil.class.getName();
    public static void checkPermission(Activity activity) {
        if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(activity, Manifest.permission.CHANGE_NETWORK_STATE) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Need permission, asking");
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.CHANGE_NETWORK_STATE}, REQUEST_ACCESS_FINE_LOCATION);
        } else {
            hideStatusUI(activity);
        }
    }

    public static void hideStatusUI(Activity activity) {
        activity.runOnUiThread(() -> {
            TextView tvId = (TextView) activity.findViewById(R.id.txtStatus);
            tvId.setVisibility(View.INVISIBLE);
        });
    }

    public static void setStatusUI(Activity activity, String status) {
        activity.runOnUiThread(() -> {
            TextView tvId = (TextView) activity.findViewById(R.id.txtStatus);
            tvId.setText(status);
            tvId.setVisibility(View.VISIBLE);
        });
    }

    public static String getReason(int reason) {
        if (reason == P2P_UNSUPPORTED) {
            return "P2P Unsupported";
        } else if (reason == ERROR) {
            return "ERROR";
        } else if (reason == BUSY) {
            return "BUSY";
        } else {
            return "UNKNOWN";
        }
    }
}
