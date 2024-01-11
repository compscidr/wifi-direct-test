package com.jasonernst.wifidirecttest;

import static com.jasonernst.wifidirecttest.AndroidUtil.REQUEST_ACCESS_FINE_LOCATION;
import static com.jasonernst.wifidirecttest.AndroidUtil.detectInterfaces;

import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pManager;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "WIFI-D-TEST";

    // wifi adapter state related stuff
    private ConnectivityManager connectivityManager;
    private WifiManager wifiManager;
    private volatile boolean wifiEnabled = false;
    private Thread wifiStateCheckerThread;
    private volatile boolean wifiCheckerRunning = false;

    // wifi direct stuff
    private WifiP2pManager wifiP2pManager;
    private WifiP2pManager.Channel channel;
    private WifiDirectBroadcastReceiver receiver;
    private final IntentFilter intentFilter = new IntentFilter();


    private ConnectivityMonitor connectivityMonitor;

    private final UdpEchoServer udpEchoServer = new UdpEchoServer(this);
    private final TcpEchoServer tcpEchoServer = new TcpEchoServer();
    private final MulticastEchoServer multicastEchoServer = new MulticastEchoServer();

    private WifiManager.WifiLock wifiLock;
    private WifiManager.MulticastLock wifiMulticastLock;

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_ACCESS_FINE_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Got permission");
                runOnUiThread(() -> {
                    TextView tvId = (TextView) findViewById(R.id.txtStatus);
                    tvId.setVisibility(View.INVISIBLE);
                });
                init();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    /**
     * Intended to run in a background thread - continues to check if wifi has been shut off which
     * will help explain lots of the possible failures.
     */
    private void wifiStateChecker() {
        wifiCheckerRunning = true;
        while (wifiCheckerRunning) {
            if (!wifiManager.isWifiEnabled()) {
                if (wifiEnabled) {
                    Log.d(TAG, "WiFi is off, can't continue");
                    AndroidUtil.setStatusUI(this,"WiFi is off");
                    wifiEnabled = false;
                }
            } else {
                if (!wifiEnabled) {
                    Log.d("TAG", "WiFi is on, good to go");
                    AndroidUtil.hideStatusUI(this);
                    wifiEnabled = true;
                }
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                break;
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        AndroidUtil.checkPermission(this);

        // Indicates a change in the Wi-Fi P2P status.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);

        // Indicates a change in the list of available peers.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);

        // Indicates the state of Wi-Fi P2P connectivity has changed.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);

        // Indicates this device's details have changed.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);


        init();
    }

    @Override
    public void onResume() {
        super.onResume();
        receiver = new WifiDirectBroadcastReceiver(wifiP2pManager, channel);
        registerReceiver(receiver, intentFilter);
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        wifiLock.release();
        wifiMulticastLock.release();
        udpEchoServer.stop();
        tcpEchoServer.stop();
        multicastEchoServer.stop();
    }

    void init() {
        String interfaces = detectInterfaces();
        Log.d(TAG, "INTERFACES: \n" + interfaces);
        connectivityManager = (ConnectivityManager) getApplicationContext().getSystemService(CONNECTIVITY_SERVICE);
        //ConnectivityMonitor connectivityMonitor = new ConnectivityMonitor(connectivityManager);
        //connectivityManager.registerDefaultNetworkCallback(connectivityMonitor);
        wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);

        if (wifiStateCheckerThread != null) {
            wifiCheckerRunning = false;
            wifiStateCheckerThread.interrupt();
            try {
                wifiStateCheckerThread.join();
            } catch (InterruptedException e) {
                Log.e(TAG, "Error waiting on wifi state checker thread: " + e);
                return;
            }
        }
        wifiStateCheckerThread = new Thread(this::wifiStateChecker);
        wifiStateCheckerThread.start();

        wifiP2pManager = (WifiP2pManager) getApplicationContext().getSystemService(WIFI_P2P_SERVICE);

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (wifiEnabled) {
            wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "P2PDEMO");
            wifiLock.acquire();
            wifiMulticastLock = wifiManager.createMulticastLock("P2PDEMO");
            wifiMulticastLock.setReferenceCounted(true);
            wifiMulticastLock.acquire();

            try {
                udpEchoServer.start();
            } catch(Exception ex) {
                Log.e(TAG, "Failed to start udp server");
            }
            tcpEchoServer.start();

            try {
                multicastEchoServer.start();
            } catch (IOException e) {
                Log.e(TAG, "Failed to start the multicast udp server");
            }

            channel = wifiP2pManager.initialize(getApplicationContext(), getMainLooper(), () -> {
                Log.d(TAG, "App became disconnected from wifi p2p api");
                AndroidUtil.setStatusUI(this, "App became disconnected from wifi p2p2 api");
            });
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            connectivityMonitor = new ConnectivityMonitor(connectivityManager);
            WiFiDirectServiceManager wiFiDirectServiceManager = new WiFiDirectServiceManager(this, connectivityManager, wifiP2pManager, channel, connectivityMonitor);
            WiFiDirectGroupManager wiFiDirectGroupManager = new WiFiDirectGroupManager(this, wifiP2pManager, channel, wiFiDirectServiceManager);
            wiFiDirectGroupManager.start();
        } else {
            Log.d(TAG, "WiFi disabled, can't initialize wifi p2p manager");
        }
    }

    public void sendMsg(View v) {
        EditText tvIp = (EditText)findViewById(R.id.txtIp);
        AndroidUtil.sendUdpMessage(tvIp.getText().toString(), this);
        //AndroidUtil.sendUdpMessage(connectivityMonitor.getServerAddress().getHostAddress());
        //AndroidUtil.sendTcpMessage(tvIp.getText().toString());
        //AndroidUtil.sendUdpMulticast();
    }

    public void updateIps(View v) {
        TextView tvIps = (TextView) findViewById(R.id.txtIps);
        String IPs = detectInterfaces();
        tvIps.setText(IPs);
    }
}
