package com.jasonernst.wifidirecttest;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.util.Log;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class WifiDirectBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = WifiDirectBroadcastReceiver.class.getName();
    private List<WifiP2pDevice> peers = new ArrayList<WifiP2pDevice>();
    private WifiP2pManager mManager;
    WifiP2pManager.Channel channel;


    public WifiDirectBroadcastReceiver(WifiP2pManager manager, WifiP2pManager.Channel channel) {
        mManager = manager;
        this.channel = channel;
    }

    private WifiP2pManager.PeerListListener peerListListener = new WifiP2pManager.PeerListListener() {
        @Override
        public void onPeersAvailable(WifiP2pDeviceList peerList) {

            Collection<WifiP2pDevice> refreshedPeers = peerList.getDeviceList();
            if (!refreshedPeers.equals(peers)) {
                peers.clear();
                peers.addAll(refreshedPeers);
                Log.d(TAG, "PEERS: " + peers);

                // If an AdapterView is backed by this data, notify it
                // of the change. For instance, if you have a ListView of
                // available peers, trigger an update.
                // ((WiFiPeerListAdapter) getListAdapter()).notifyDataSetChanged();

                // Perform any other updates needed based on the new list of
                // peers connected to the Wi-Fi P2P network.
            }

            if (peers.size() == 0) {
                Log.d(TAG, "No devices found");
                return;
            }
        }
    };


    @SuppressLint("MissingPermission")
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
            // Determine if Wifi P2P mode is enabled or not, alert
            // the Activity.
            int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
            if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                Log.d(TAG, "Wifi p2p2 enabled true");
                //activity.setIsWifiP2pEnabled(true);
            } else {
                //activity.setIsWifiP2pEnabled(false);
                Log.d(TAG, "Wifi p2p2 enabled false");
            }
        } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {

            // The peer list has changed! We should probably do something about
            // that.
//            Log.d(TAG, "peer list changed");
//            if (mManager != null) {
//                mManager.requestPeers(channel, peerListListener);
//            }


        } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {

            // Connection state changed! We should probably do something about
            // that.
            //Log.d(TAG, "Wifi p2p2 connection state changed");
            if (mManager == null) {
                return;
            }

            NetworkInfo networkInfo = (NetworkInfo) intent
                    .getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);

            if (networkInfo.isConnected()) {
                Log.d(TAG, "CONNECTED");
                mManager.requestConnectionInfo(channel,
                        new WifiP2pManager.ConnectionInfoListener() {

                            @Override
                            public void onConnectionInfoAvailable(WifiP2pInfo info) {
                                // String from WifiP2pInfo struct
                                String groupOwnerAddress = info.groupOwnerAddress.getHostAddress();

                                // After the group negotiation, we can determine the group owner
                                // (server).
                                if (info.groupFormed && info.isGroupOwner) {
                                    // Do whatever tasks are specific to the group owner.
                                    // One common case is creating a group owner thread and accepting
                                    // incoming connections.
                                    Log.d(TAG, "GROUP OWNER: " + info);

                                    mManager.requestGroupInfo(channel, new WifiP2pManager.GroupInfoListener() {
                                        @Override
                                        public void onGroupInfoAvailable(WifiP2pGroup wifiP2pGroup) {
                                            Log.d(TAG, "GROUP INFO: " + wifiP2pGroup);
                                            if (wifiP2pGroup.getClientList().isEmpty()) {
                                                return;
                                            }

//                                            new Thread(()->{
//                                                try {
//                                                    Thread.sleep(500);
//                                                } catch (InterruptedException e) {
//                                                    e.printStackTrace();
//                                                }
//                                                AndroidUtil.detectInterfaces();
//
//                                                while (true) {
//                                                    sendUdpMulticast();
//                                                    try {
//                                                        Thread.sleep(1000);
//                                                    } catch (InterruptedException e) {
//                                                        break;
//                                                    }
//                                                }
//                                            }).start();

                                        }
                                    });

                                } else if (info.groupFormed) {
                                    // The other device acts as the peer (client). In this case,
                                    // you'll want to create a peer thread that connects
                                    // to the group owner.
                                    Log.d(TAG, "NOT GROUP OWNER: " + info);

                                    mManager.requestGroupInfo(channel, new WifiP2pManager.GroupInfoListener() {
                                        @Override
                                        public void onGroupInfoAvailable(WifiP2pGroup wifiP2pGroup) {
                                            Log.d(TAG, "GROUP INFO: " + wifiP2pGroup);
                                        }
                                    });
                                }
                            }
                        });
            } else {
                Log.d(TAG, "DISCONNECTED");
            }


            } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
            WifiP2pDevice device = (WifiP2pDevice) intent.getParcelableExtra(
                    WifiP2pManager.EXTRA_WIFI_P2P_DEVICE);
            Log.d(TAG, "This device changed: " + device.toString());

        }
    }

}
