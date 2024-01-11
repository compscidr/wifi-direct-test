package com.jasonernst.wifidirecttest;

import static com.jasonernst.wifidirecttest.AndroidUtil.checkPermission;
import static com.jasonernst.wifidirecttest.AndroidUtil.getReason;

import android.app.Activity;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiNetworkSpecifier;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.os.Handler;
import android.util.Log;

import java.net.Inet6Address;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;

public class WiFiDirectServiceManager {
    private static final String TAG = WiFiDirectServiceManager.class.getName();

    private final Activity activity;
    private final ConnectivityManager connectivityManager;
    private final ConnectivityMonitor connectivityMonitor;
    private final WifiP2pManager wifiP2pManager;
    private final WifiP2pManager.Channel channel;
    private final Handler handler = new Handler();
    private Runnable serviceRunnable;
    private Runnable peerRunnable;
    int delay = 20*1000; //Delay for 15 seconds.  One second = 1000 milliseconds.
    private Map<String,String> advertisement;

    public WiFiDirectServiceManager(Activity activity, ConnectivityManager connectivityManager, WifiP2pManager wifiP2pManager, WifiP2pManager.Channel channel, ConnectivityMonitor connectivityMonitor) {
        this.activity = activity;
        this.connectivityManager = connectivityManager;
        this.connectivityMonitor = connectivityMonitor;
        this.wifiP2pManager = wifiP2pManager;
        this.channel = channel;
    }

    public void start(Map<String,String> advertisement) {
        this.advertisement = advertisement;
        clearLocalService();
    }

    // https://stackoverflow.com/questions/26300889/wifi-p2p-service-discovery-works-intermittently
    private void clearLocalService() {
        wifiP2pManager.clearLocalServices(channel, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                Log.d(TAG, "Success clearing local service");
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                addLocalService();
            }

            @Override
            public void onFailure(int i) {
                Log.d(TAG, "Failed to clear local service: " + getReason(i));
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                addLocalService();
                //clearLocalService();
            }
        });
    }

    private void addLocalService() {
        // add a local service advertisement which just gives out the wifi director hotspot info
        // in the advertisement
        WifiP2pDnsSdServiceInfo serviceInfo =
                WifiP2pDnsSdServiceInfo.newInstance("_wifid", "_connection._autoconf", advertisement);

        checkPermission(activity);

        wifiP2pManager.addLocalService(channel, serviceInfo, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Success adding local service");

                handler.postDelayed(peerRunnable = new Runnable() {
                    @Override
                    public void run() {
                        checkPermission(activity);
                        wifiP2pManager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
                            @Override
                            public void onSuccess() {
                                //Log.d(TAG, "Successfully initiated peer discover");
                            }

                            @Override
                            public void onFailure(int i) {
                                Log.d(TAG, "Failed to initiate peer discover: " + getReason(i));
                            }
                        });
                        handler.postDelayed(peerRunnable, delay);
                    }
                }, 100);

                handler.postDelayed(serviceRunnable = new Runnable() {
                    @Override
                    public void run() {
                        discoverService();
                        handler.postDelayed(serviceRunnable, delay);
                    }
                }, 100);
            }

            @Override
            public void onFailure(int i) {
                Log.d(TAG, "Failed to add local service: " + getReason(i));
            }
        });
    }

    private void discoverService() {

        WifiP2pManager.DnsSdTxtRecordListener txtListener = new WifiP2pManager.DnsSdTxtRecordListener() {
            @Override
            public void onDnsSdTxtRecordAvailable(String fullDomain, Map record, WifiP2pDevice device) {
                Log.d(TAG, "DnsSdTxtRecord available -" + record.toString());

                WifiNetworkSpecifier wifiNetworkSpecifier = new WifiNetworkSpecifier.Builder()
                        .setSsid(record.get("SSID").toString())
                        .setWpa2Passphrase(record.get("pass").toString())
                        .build();

                NetworkRequest request = new NetworkRequest.Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                        .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .setNetworkSpecifier(wifiNetworkSpecifier)
                        .build();

                if (!connectivityMonitor.isConnecting() && !connectivityMonitor.isConnected()) {
                    if (record.containsKey("ip")) {
                        Inet6Address serverAddress = (Inet6Address) Inet6Address.getLoopbackAddress();
                        try {
                            serverAddress = (Inet6Address) Inet6Address.getByName(record.get("ip").toString());
                            connectivityMonitor.setServerAddress(serverAddress);
                        } catch (UnknownHostException e) {
                            Log.e(TAG, "Error getting ipv6 address from advert: " + e);
                        }
                    }
                    connectivityMonitor.setConnecting(true);
                    connectivityMonitor.setRequest(request);
                    connectivityManager.requestNetwork(request, connectivityMonitor);
                }
            }
        };


        WifiP2pManager.DnsSdServiceResponseListener servListener = new WifiP2pManager.DnsSdServiceResponseListener() {
            @Override
            public void onDnsSdServiceAvailable(String instanceName, String registrationType, WifiP2pDevice resourceType) {
                Log.d(TAG, "onBonjourServiceAvailable " + instanceName + " " + registrationType + " " + resourceType);
            }
        };

        WifiP2pManager.UpnpServiceResponseListener upnpServiceResponseListener = new WifiP2pManager.UpnpServiceResponseListener() {
            @Override
            public void onUpnpServiceAvailable(List<String> list, WifiP2pDevice wifiP2pDevice) {
                Log.d(TAG, "upnp service available: " + list + " " + wifiP2pDevice);
            }
        };

        wifiP2pManager.setDnsSdResponseListeners(channel, servListener, txtListener);
        //wifiP2pManager.setUpnpServiceResponseListener(channel, upnpServiceResponseListener);
        WifiP2pDnsSdServiceRequest serviceRequest = WifiP2pDnsSdServiceRequest.newInstance();
        wifiP2pManager.addServiceRequest(channel, serviceRequest, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                //Log.d(TAG, "Success starting service discovery");
                checkPermission(activity);
                wifiP2pManager.discoverServices(channel, new WifiP2pManager.ActionListener() {

                    @Override
                    public void onSuccess() {
                        //Log.d(TAG, "SUCCESS DISCOVERING SERVICES");
                    }

                    @Override
                    public void onFailure(int i) {
                        Log.d(TAG, "FAILURE DISCOVERING SERVICES");
                    }
                });
            }

            @Override
            public void onFailure(int i) {
                Log.d(TAG, "Failed to start service discovery: " + getReason(i));
            }
        });
    }
}
