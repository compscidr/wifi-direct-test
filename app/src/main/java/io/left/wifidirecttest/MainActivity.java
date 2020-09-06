package io.left.wifidirecttest;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.net.wifi.p2p.nsd.WifiP2pServiceRequest;
import android.os.Build;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static android.net.wifi.p2p.WifiP2pManager.BUSY;
import static android.net.wifi.p2p.WifiP2pManager.ERROR;
import static android.net.wifi.p2p.WifiP2pManager.P2P_UNSUPPORTED;
import static android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION;
import static android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION;

public class MainActivity extends AppCompatActivity implements WifiP2pManager.PeerListListener, WifiP2pManager.ConnectionInfoListener {

    public volatile boolean groupStarted = false;
    private static final String TAG = "RIGHTMESH";
    private WifiManager wifiManager;
    private WifiP2pManager wifiP2pManager;
    private WifiP2pManager.Channel channel;
    private WifiManager.WifiLock wifiLock;
    private WifiManager.MulticastLock wifiMulticastLock;
    WifiDirectBroadcastReceiver wifiDirectBroadcastReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        wifiManager.setWifiEnabled(false);
        try { Thread.sleep(1000); } catch(Exception ex) {}
        wifiManager.setWifiEnabled(true);
        try { Thread.sleep(1000); } catch(Exception ex) {}
        wifiP2pManager = (WifiP2pManager) getApplicationContext().getSystemService(WIFI_P2P_SERVICE);
        wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "RIGHTMESH");
        wifiLock.acquire();
        wifiMulticastLock = wifiManager.createMulticastLock("RIGHTMESH");
        wifiMulticastLock.acquire();

        IntentFilter wifip2p2filter = new IntentFilter();
        wifip2p2filter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        wifip2p2filter.addAction(WIFI_P2P_PEERS_CHANGED_ACTION);
        wifip2p2filter.addAction(WIFI_P2P_CONNECTION_CHANGED_ACTION);
        wifip2p2filter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        channel = wifiP2pManager.initialize(this, getMainLooper(), null);
        Log.d(TAG, getWFDMacAddress());
        wifiDirectBroadcastReceiver = new WifiDirectBroadcastReceiver(wifiP2pManager, channel, this, this);
        registerReceiver(wifiDirectBroadcastReceiver, wifip2p2filter);

        startGroup(null);
        setServiceListeners();
        startScan();
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void setServiceListeners() {
        wifiP2pManager.setDnsSdResponseListeners(channel, null, new WifiDnsSdTxtRecordListener());

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
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public void startGroup(View v) {
        wifiP2pManager.createGroup(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "SUCCESS STARTING GROUP");
                groupStarted = true;

                wifiP2pManager.requestGroupInfo(channel, new WifiP2pManager.GroupInfoListener() {
                    @Override
                    public void onGroupInfoAvailable(WifiP2pGroup group) {
                        if(group == null) {
                            Log.d(TAG, "GROUP IS NULL");
                        } else {
                            Log.d(TAG, "MY GROUP: " + group.getNetworkName() + " "
                                    + group.getPassphrase() + " "
                                    + group.getInterface() + " "
                                    + group.getOwner().deviceAddress);

                            //advertise service record
                            Map<String,String> record = new HashMap<>();
                            record.put("name", group.getNetworkName());
                            record.put("pass", group.getPassphrase());
                            record.put("mac", group.getOwner().deviceAddress);
                            WifiP2pDnsSdServiceInfo serviceInfo = WifiP2pDnsSdServiceInfo.newInstance
                                    ("RightMesh", "Mesh", record);

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

            @Override
            public void onFailure(int i) {
                Log.d(TAG, "FAIL STARTING GROUP");
                groupStarted = false;
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
        for(final WifiP2pDevice device : devices) {
            Log.d(TAG, "DEVICE: " + device.toString());
            WifiP2pConfig config = new WifiP2pConfig();
            config.deviceAddress = device.deviceAddress;
            //config.wps.setup = WpsInfo.LABEL;
            //config.wps.pin = "0000";
            //config.groupOwnerIntent = 4;
            wifiP2pManager.connect(channel, config, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    // Connection request successfully sent
                    Log.d(TAG,"Sent connect to: " + device.deviceAddress);
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
                    Log.d(TAG,"Failed to send connect to: " + device.deviceAddress + " " + reason);
                }
            });
        }
    }

    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo wifiP2pInfo) {
        Log.d(TAG, "CONNNNNNNNNEEECTED!!");
        Log.d(TAG, wifiP2pInfo.toString());
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
            Log.d(TAG, action);

            if(action == null) {
                return;
            }

            if(action.equals(WIFI_P2P_PEERS_CHANGED_ACTION)) {
                if(wifiP2pManager != null && !MainActivity.this.groupStarted) {
                    Log.d(TAG, "Requesting Peers");
                    wifiP2pManager.requestPeers(channel, peerListener);
                }
            } else if(action.equals(WIFI_P2P_CONNECTION_CHANGED_ACTION)) {
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
            }
        }
    }

    public String getWFDMacAddress(){
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface ntwInterface : interfaces) {

                if (ntwInterface.getName().equalsIgnoreCase("p2p0")) {
                    byte[] byteMac = ntwInterface.getHardwareAddress();
                    if (byteMac==null){
                        return null;
                    }
                    StringBuilder strBuilder = new StringBuilder();
                    for (int i=0; i<byteMac.length; i++) {
                        strBuilder.append(String.format("%02X:", byteMac[i]));
                    }

                    if (strBuilder.length()>0){
                        strBuilder.deleteCharAt(strBuilder.length()-1);
                    }

                    return strBuilder.toString();
                }

            }
        } catch (Exception e) {
            Log.d(TAG, e.getMessage());
        }
        return "00:00:00:00:00:00";
    }


    public void startScan() {
        wifiP2pManager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG,"Successfully started Wi-Fi Direct Peer Scan");
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
                Log.d(TAG,"Failed to start Wi-Fi Direct Peer Scan: " + reason);

                try { Thread.sleep(100); } catch(Exception ex) {}
                startScan();
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
        @Override
        public void onDnsSdTxtRecordAvailable(String fullDomain, Map<String,String> record, WifiP2pDevice device) {
            Log.d(TAG, "Record: " + record.toString());

            String name = record.get("name");
            String pass = record.get("pass");
            String mac = record.get("mac");

            if(name == null || pass == null || mac == null) {
                return;
            }

            //save wifi configuration and connect here
        }
    }
}
