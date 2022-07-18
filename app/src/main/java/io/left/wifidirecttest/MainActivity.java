package io.left.wifidirecttest;

import static android.net.wifi.p2p.WifiP2pManager.BUSY;
import static android.net.wifi.p2p.WifiP2pManager.ERROR;
import static android.net.wifi.p2p.WifiP2pManager.P2P_UNSUPPORTED;

import android.Manifest;

import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Map;


public class MainActivity extends AppCompatActivity {

    private static final String TAG = "WIFI-D-TEST";
    private static final int REQUEST_ACCESS_FINE_LOCATION = 1;

    // wifi adapter state related stuff
    private ConnectivityManager connectivityManager;
    private WifiManager wifiManager;
    private volatile boolean wifiEnabled = false;
    private Thread wifiStateCheckerThread;
    private volatile boolean wifiCheckerRunning = false;

    // wifi direct stuff
    private WifiP2pManager wifiP2pManager;
    private WifiP2pManager.Channel channel;


    public volatile boolean groupStarted = false;


    private WifiManager.WifiLock wifiLock;
    private WifiManager.MulticastLock wifiMulticastLock;
    //WifiDirectBroadcastReceiver wifiDirectBroadcastReceiver;

    private void checkPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Need permission, asking");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_ACCESS_FINE_LOCATION);
        } else {
            hideStatusUI();
        }
    }

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

    private void hideStatusUI() {
        runOnUiThread(() -> {
            TextView tvId = (TextView) findViewById(R.id.txtStatus);
            tvId.setVisibility(View.INVISIBLE);
        });
    }

    private void setStatusUI(String status) {
        runOnUiThread(() -> {
            TextView tvId = (TextView) findViewById(R.id.txtStatus);
            tvId.setText(status);
            tvId.setVisibility(View.VISIBLE);
        });
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
                    setStatusUI("WiFi is off");
                    wifiEnabled = false;
                }
            } else {
                if (!wifiEnabled) {
                    Log.d("TAG", "WiFi is on, good to go");
                    hideStatusUI();
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
        checkPermission();
        init();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    void init() {
        connectivityManager = (ConnectivityManager) getApplicationContext().getSystemService(CONNECTIVITY_SERVICE);
        connectivityManager.registerNetworkCallback(
                new NetworkRequest.Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                        .build(),
                new ConnectivityMonitor());
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
            channel = wifiP2pManager.initialize(getApplicationContext(), getMainLooper(), () -> {
                Log.d(TAG, "App became disconnected from wifi p2p api");
                setStatusUI("App became disconnected from wifi p2p2 api");
            });
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            clearLocalService();
        } else {
            Log.d(TAG, "WiFi disabled, can't initialize wifi p2p manager");
        }

        /*


        wifiP2pManager.initialize(getApplicationContext(), getMainLooper(), this);
        wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "P2PDEMO");
        wifiLock.acquire();
        wifiMulticastLock = wifiManager.createMulticastLock("P2PDEMO");
        wifiMulticastLock.acquire();

        IntentFilter wifip2p2filter = new IntentFilter();
        wifip2p2filter.addAction(WIFI_P2P_STATE_CHANGED_ACTION);
        wifip2p2filter.addAction(WIFI_P2P_PEERS_CHANGED_ACTION);
        wifip2p2filter.addAction(WIFI_P2P_CONNECTION_CHANGED_ACTION);
        wifip2p2filter.addAction(WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        channel = wifiP2pManager.initialize(this, getMainLooper(), null);
        Log.d(TAG, "WIFID MAC ADDRESS: " + getWFDMacAddress());
        wifiDirectBroadcastReceiver = new WifiDirectBroadcastReceiver(wifiP2pManager, channel, this, this);
        registerReceiver(wifiDirectBroadcastReceiver, wifip2p2filter);

        //startGroup(null);
        setServiceListeners();
        //startScan(null);
         */
    }

    // https://stackoverflow.com/questions/26300889/wifi-p2p-service-discovery-works-intermittently
    private void clearLocalService() {
        wifiP2pManager.clearLocalServices(channel, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                Log.d(TAG, "Success clearing local service");
                addLocalService();
            }

            @Override
            public void onFailure(int i) {
                String reason;
                if (i == P2P_UNSUPPORTED) {
                    reason = "P2P Unsupported";
                } else if (i == ERROR) {
                    reason = "ERROR";
                } else if (i == BUSY) {
                    reason = "BUSY";
                } else {
                    reason = "UNKNOWN";
                }
                Log.d(TAG, "Failed to clear local service: " + reason);
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                clearLocalService();
            }
        });
    }

    private void addLocalService() {
        Map record = new HashMap();
        record.put("listenport", String.valueOf(1000));
        record.put("buddyname", "John Doe" + (int) (Math.random() * 1000));
        record.put("available", "visible");

        // Service information.  Pass it an instance name, service type
        // _protocol._transportlayer , and the map containing
        // information other devices will want once they connect to this one.
        WifiP2pDnsSdServiceInfo serviceInfo =
                WifiP2pDnsSdServiceInfo.newInstance("_test", "_presence._tcp", record);

        checkPermission();

        wifiP2pManager.addLocalService(channel, serviceInfo, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Success adding local service");
                new Thread(() -> {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    discoverService();
                }).start();
            }

            @Override
            public void onFailure(int i) {
                String reason;
                if (i == P2P_UNSUPPORTED) {
                    reason = "P2P Unsupported";
                } else if (i == ERROR) {
                    reason = "ERROR";
                } else if (i == BUSY) {
                    reason = "BUSY";
                } else {
                    reason = "UNKNOWN";
                }
                Log.d(TAG, "Failed to add local service: " + reason);
            }
        });
    }

    private void discoverService() {

        WifiP2pManager.DnsSdTxtRecordListener txtListener = new WifiP2pManager.DnsSdTxtRecordListener() {
            @Override
            public void onDnsSdTxtRecordAvailable(String fullDomain, Map record, WifiP2pDevice device) {
                Log.d(TAG, "DnsSdTxtRecord available -" + record.toString());
            }
        };


        WifiP2pManager.DnsSdServiceResponseListener servListener = new WifiP2pManager.DnsSdServiceResponseListener() {
            @Override
            public void onDnsSdServiceAvailable(String instanceName, String registrationType, WifiP2pDevice resourceType) {
                Log.d(TAG, "onBonjourServiceAvailable " + instanceName);
            }
        };

        wifiP2pManager.setDnsSdResponseListeners(channel, servListener, txtListener);
        WifiP2pDnsSdServiceRequest serviceRequest = WifiP2pDnsSdServiceRequest.newInstance();
        wifiP2pManager.addServiceRequest(channel, serviceRequest, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Success starting service discovery");
            }

            @Override
            public void onFailure(int i) {
                String reason;
                if (i == P2P_UNSUPPORTED) {
                    reason = "P2P Unsupported";
                } else if (i == ERROR) {
                    reason = "ERROR";
                } else if (i == BUSY) {
                    reason = "BUSY";
                } else {
                    reason = "UNKNOWN";
                }
                Log.d(TAG, "Failed to start service discovery: " + reason);
            }
        });
    }
    /*

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void setServiceListeners() {
        wifiP2pManager.addServiceRequest(channel, WifiP2pDnsSdServiceRequest.newInstance(),
                new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        Log.d(TAG, "Success adding service dns service request");
                    }

                    @Override
                    public void onFailure(int i) {
                        Log.d(TAG, "Fail adding service dns service request");
                    }
                });
        wifiP2pManager.setDnsSdResponseListeners(channel, new WifiDnsSdServiceResponseListener(), new WifiDnsSdTxtRecordListener());
        wifiP2pManager.setUpnpServiceResponseListener(channel, new WiFiUpnpServiceResponseListener());
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public void startGroup(View v) {
        Log.d(TAG, "START GROUP");
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Don't have permission to start group");
            int REQUEST_ACCESS_FINE_LOCATION = 1;
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_ACCESS_FINE_LOCATION);
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        wifiP2pManager.createGroup(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "SUCCESS STARTING GROUP");
                groupStarted = true;
                advertiseService(null);
            }

            @Override
            public void onFailure(int i) {
                // Failed to send connection request.
                String reason;
                if (i == P2P_UNSUPPORTED) {
                    reason = "P2P Unsupported";
                } else if (i == ERROR) {
                    reason = "ERROR";
                } else if (i == BUSY) {
                    reason = "BUSY";
                } else {
                    reason = "UNKNOWN";
                }
                Log.d(TAG, "FAIL STARTING GROUP, REASON: " + reason);
                groupStarted = false;
            }
        });
    }

    public void advertiseService(View v) {
        if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Don't have permission to request group info");
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        wifiP2pManager.requestGroupInfo(channel, new WifiP2pManager.GroupInfoListener() {
            @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
            @Override
            public void onGroupInfoAvailable(WifiP2pGroup group) {
                if (group == null) {
                    Log.d(TAG, "GROUP IS NULL");
                    try {
                        Thread.sleep(100);
                    } catch (Exception ex) {
                    }
                    advertiseService(null);
                } else {
                    Log.d(TAG, "MY GROUP: " + group.getNetworkName() + " "
                            + group.getPassphrase() + " "
                            + group.getInterface() + " "
                            + group.getOwner().deviceAddress);

                    //advertise service record
                    Map<String, String> record = new HashMap<>();
                    record.put("name", group.getNetworkName());
                    record.put("pass", group.getPassphrase());
                    record.put("mac", group.getOwner().deviceAddress);
                    WifiP2pDnsSdServiceInfo serviceInfo = WifiP2pDnsSdServiceInfo.newInstance
                            ("P2PDEMO", "MESH", record);

                    if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        Log.e(TAG, "Don't have permission to start local service");
                        // TODO: Consider calling
                        //    ActivityCompat#requestPermissions
                        // here to request the missing permissions, and then overriding
                        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                        //                                          int[] grantResults)
                        // to handle the case where the user grants the permission. See the documentation
                        // for ActivityCompat#requestPermissions for more details.
                        return;
                    }
                    wifiP2pManager.addLocalService(channel, serviceInfo, new WifiP2pManager.ActionListener() {
                        @Override
                        public void onSuccess() {
                            Log.d(TAG, "Success Advertising local service");
                        }

                        @Override
                        public void onFailure(int i) {
                            Log.d(TAG, "Fail Advertising local service");
                        }
                    });
                }
            }
        });
    }

    public void sendMsg(View v) {
        Log.d(TAG, "Sending");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        wifiLock.release();
        wifiMulticastLock.release();
        unregisterReceiver(wifiDirectBroadcastReceiver);

        wifiP2pManager.removeGroup(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "SUCCESS STOPPING GROUP");
                groupStarted = false;
            }

            @Override
            public void onFailure(int i) {
                Log.d(TAG, "FAIL STOPPING GROUP");
            }
        });

        stopScan();
    }

    @Override
    public void onPeersAvailable(WifiP2pDeviceList peerList) {
        List<WifiP2pDevice> devices = (new ArrayList<>());
        devices.addAll(peerList.getDeviceList());

        //do something with the device list
        Log.d(TAG, "Devices found!");
        for (final WifiP2pDevice device : devices) {
            Log.d(TAG, "DEVICE: " + device.toString());
            WifiP2pConfig config = new WifiP2pConfig();
            config.deviceAddress = device.deviceAddress;
            //config.wps.setup = WpsInfo.LABEL;
            //config.wps.pin = "0000";
            //config.groupOwnerIntent = 4;
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Don't have permission for wifi p2p connect");
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            wifiP2pManager.connect(channel, config, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    // Connection request successfully sent
                    Log.d(TAG, "Sent connect to: " + device.deviceAddress);
                }

                @Override
                public void onFailure(int reasonCode) {
                    // Failed to send connection request.
                    String reason;
                    if (reasonCode == P2P_UNSUPPORTED) {
                        reason = "P2P Unsupported";
                    } else if (reasonCode == ERROR) {
                        reason = "ERROR";
                    } else if (reasonCode == BUSY) {
                        reason = "BUSY";
                    } else {
                        reason = "UNKNOWN";
                    }
                    Log.d(TAG, "Failed to send connect to: " + device.deviceAddress + " " + reason);
                }
            });
        }
    }

    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo wifiP2pInfo) {
        Log.d(TAG, "CONNNNNNNNNEEECTED!!");
        Log.d(TAG, wifiP2pInfo.toString());
    }

    @Override
    public void onChannelDisconnected() {
        Log.d(TAG, "WIFID CHANNEL DISCONNECTED");
    }

    public class WifiDirectBroadcastReceiver extends BroadcastReceiver {

        WifiP2pManager.PeerListListener peerListener;
        WifiP2pManager.ConnectionInfoListener connectionInfoListener;
        WifiP2pManager wifiP2pManager;
        WifiP2pManager.Channel channel;

        public WifiDirectBroadcastReceiver(WifiP2pManager manager, WifiP2pManager.Channel channel, WifiP2pManager.PeerListListener plistener, WifiP2pManager.ConnectionInfoListener clistener) {
            this.wifiP2pManager = manager;
            this.channel = channel;
            this.peerListener = plistener;
            this.connectionInfoListener = clistener;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action == null) {
                return;
            }

            if (action.equals(WIFI_P2P_PEERS_CHANGED_ACTION)) {
                WifiP2pDeviceList peers = intent.getParcelableExtra(EXTRA_P2P_DEVICE_LIST);
                if (peers != null) {
                    Log.d(TAG, "Wi-Fi Direct Peers have changed: ");// + peers);
                }
                if (wifiP2pManager != null && !MainActivity.this.groupStarted) {
                    if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        Log.e(TAG, "Don't have permission to request peers");
                        // TODO: Consider calling
                        //    ActivityCompat#requestPermissions
                        // here to request the missing permissions, and then overriding
                        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                        //                                          int[] grantResults)
                        // to handle the case where the user grants the permission. See the documentation
                        // for ActivityCompat#requestPermissions for more details.
                        return;
                    }
                    //Log.d(TAG, "Requesting Peers");
                    //wifiP2pManager.requestPeers(channel, peerListener);
                }
            } else if (action.equals(WIFI_P2P_CONNECTION_CHANGED_ACTION)) {
                Log.d(TAG, "Connection Changed");

                NetworkInfo networkInfo = intent
                        .getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
                WifiP2pInfo p2pInfo = intent
                        .getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO);

                if (p2pInfo != null && p2pInfo.groupOwnerAddress != null) {
                    String goAddress = p2pInfo.groupOwnerAddress.getAddress().toString();
                    boolean isGroupOwner = p2pInfo.isGroupOwner;
                }

                if (networkInfo.isConnected()) {
                    // we are connected with the other device, request connection
                    // info to find group owner IP
                    wifiP2pManager.requestConnectionInfo(channel, connectionInfoListener);
                } else {
                    // It's a disconnect
                    // activity.resetData();
                    Log.d(TAG, "Disconnected.");
                }
            } else if (action.equals(WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)) {
                Log.d(TAG, "DEVICE CHANGED");
            } else if (action.equals(WIFI_P2P_STATE_CHANGED_ACTION)) {
                int state = intent.getIntExtra(EXTRA_WIFI_STATE, WIFI_P2P_STATE_DISABLED);
                String state_string = "disabled";
                if (state == WIFI_P2P_STATE_ENABLED) {
                    state_string = "enabled";
                }
                Log.d(TAG, "STATE_CHANGED: " + state_string);
            } else {
                Log.d(TAG, "UNEXPECTED ACTION: " + action);
            }
        }
    }

    public String getWFDMacAddress() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface ntwInterface : interfaces) {

                if (ntwInterface.getName().equalsIgnoreCase("p2p0")) {
                    byte[] byteMac = ntwInterface.getHardwareAddress();
                    if (byteMac == null) {
                        return null;
                    }
                    StringBuilder strBuilder = new StringBuilder();
                    for (int i = 0; i < byteMac.length; i++) {
                        strBuilder.append(String.format("%02X:", byteMac[i]));
                    }

                    if (strBuilder.length() > 0) {
                        strBuilder.deleteCharAt(strBuilder.length() - 1);
                    }

                    return strBuilder.toString();
                }

            }
        } catch (Exception e) {
            Log.d(TAG, e.getMessage());
        }
        return "00:00:00:00:00:00";
    }


    public void startScan(View v) {
        Log.d(TAG, "STARTING SCAN");
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Don't have permission to discover peers");
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        wifiP2pManager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Successfully started Wi-Fi Direct Peer Scan");
                try {
                    Thread.sleep(100);
                } catch (Exception ex) {
                }
                //startScan(null);
            }

            @Override
            public void onFailure(int reasonCode) {
                // Failed to send connection request.
                String reason;
                if (reasonCode == P2P_UNSUPPORTED) {
                    reason = "P2P Unsupported";
                } else if (reasonCode == ERROR) {
                    reason = "ERROR";
                } else if (reasonCode == BUSY) {
                    reason = "BUSY";
                } else {
                    reason = "UNKNOWN";
                }
                Log.d(TAG, "Failed to start Wi-Fi Direct Peer Scan: " + reason);

                try {
                    Thread.sleep(100);
                } catch (Exception ex) {
                }
                startScan(null);
            }
        });
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public void stopScan() {
        wifiP2pManager.stopPeerDiscovery(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG,"Successfully stopped Wi-Fi Direct Peer Scan");
            }

            @Override
            public void onFailure(int reasonCode) {
                // Failed to send connection request.
                String reason;
                if(reasonCode == P2P_UNSUPPORTED) {
                    reason = "P2P Unsupported";
                } else if(reasonCode == ERROR) {
                    reason = "ERROR";
                } else if(reasonCode == BUSY) {
                    reason = "BUSY";
                } else {
                    reason = "UNKNOWN";
                }
                Log.d(TAG,"Failed to stop Wi-Fi Direct Peer Scan: " + reason);
            }
        });
    }

    //API level must be at minimum 16 to be able to have a DnsSdTxtRecordListener
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private class WifiDnsSdTxtRecordListener implements WifiP2pManager.DnsSdTxtRecordListener {
        @RequiresApi(api = Build.VERSION_CODES.Q)
        @Override
        public void onDnsSdTxtRecordAvailable(String fullDomain, Map<String,String> record, WifiP2pDevice device) {
            Log.d(TAG, "Wi-Fi Direct Dns Sd Record: " + record.toString());

            String name = record.get("name");
            String pass = record.get("pass");
            String mac = record.get("mac");

            if(name == null || pass == null || mac == null) {
                return;
            }

            NetworkSpecifier networkSpecifier = new WifiNetworkSpecifier.Builder()
                    .setSsid(name).setWpa2Passphrase(pass).build();

            final NetworkRequest request =
                    new NetworkRequest.Builder()
                            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                            .setNetworkSpecifier(networkSpecifier)
                            .build();
            ConnectivityManager connectivityManager =
                    (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            connectivityManager.requestNetwork(request, new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(@NonNull Network network) {
                    super.onAvailable(network);
                    Log.d(TAG, "NETWORK AVAILABLE");
                }
            });
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private class WiFiUpnpServiceResponseListener implements
            WifiP2pManager.UpnpServiceResponseListener {
        @Override
        public void onUpnpServiceAvailable(List<String> list, WifiP2pDevice wifiP2pDevice) {
            Log.d(TAG, "Wi-Fi Direct UPnP Discovery:" + list.toString() + " "
                    + wifiP2pDevice.toString());
        }
    }

    //API level must be at minimum 16 to be able to have a DnsSdServiceResponseListener
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private class WifiDnsSdServiceResponseListener implements WifiP2pManager.DnsSdServiceResponseListener {
        @Override
        public void onDnsSdServiceAvailable(String instance, String type, WifiP2pDevice device) {

            Log.d(TAG, "Wi-Fi Direct Dsn SD: " + instance);

//            String name = instance.substring(0, instance.indexOf("|"));
//            String password = instance.substring(instance.indexOf("|") + 1);
//            String uid = password.substring(instance.indexOf("|") + 1);
//            password = password.substring(0, instance.indexOf("|"));
//            Logger.log(TAG, "Network name: " + name
//                    + ", Password: " + password + " UUID: " + uid);
//            addRM("any", name, password);
        }
    }
     */
}
