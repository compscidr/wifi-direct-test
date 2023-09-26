package io.left.wifidirecttest;

import static io.left.wifidirecttest.AndroidUtil.checkPermission;
import static io.left.wifidirecttest.AndroidUtil.getReason;

import android.app.Activity;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pManager;
import android.util.Log;

import java.io.IOError;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;

public class WiFiDirectGroupManager {

    private static final String TAG = WiFiDirectGroupManager.class.getName();
    private final Activity activity;
    private final WifiP2pManager wifiP2pManager;
    private final WifiP2pManager.Channel channel;
    private final WiFiDirectServiceManager wiFiDirectServiceManager;

    public WiFiDirectGroupManager(Activity activity, WifiP2pManager wifiP2pManager, WifiP2pManager.Channel channel, WiFiDirectServiceManager wiFiDirectServiceManager) {
        this.activity = activity;
        this.wifiP2pManager = wifiP2pManager;
        this.channel = channel;
        this.wiFiDirectServiceManager = wiFiDirectServiceManager;
    }

    public void start() {
        removeGroup();
    }

    private void removeGroup() {
        // prevent remove group failing with BUSY state by checking if it exists first
        // https://stackoverflow.com/questions/30417586/wifip2pmanager-creategroup-and-removegroup-are-failing-with-busy-state
        checkPermission(activity);
        wifiP2pManager.requestGroupInfo(channel, new WifiP2pManager.GroupInfoListener() {
            @Override
            public void onGroupInfoAvailable(WifiP2pGroup wifiP2pGroup) {
                if (wifiP2pGroup != null) {
                    Log.d(TAG, "Group exists, removing");
                    wifiP2pManager.removeGroup(channel, new WifiP2pManager.ActionListener() {
                        @Override
                        public void onSuccess() {
                            Log.d(TAG, "Success removing group");
                            createGroup();
                        }

                        @Override
                        public void onFailure(int i) {
                            Log.d(TAG, "Failed at removing group: " + getReason(i));
                            try {
                                Thread.sleep(500);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            removeGroup();
                        }
                    });
                } else {
                    Log.d(TAG, "No group exists");
                    createGroup();
                }
            }
        });
    }

    private void createGroup() {
        checkPermission(activity);
        wifiP2pManager.createGroup(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Success creating group");
                try {
                    Thread.sleep(500);
                } catch(InterruptedException ex) {
                    // pass
                }
                getGroupInfo();
            }

            @Override
            public void onFailure(int i) {
                Log.d(TAG, "Failed creating group: " + getReason(i));
                try {
                    Thread.sleep(500);
                    createGroup();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void getGroupInfo() {
        checkPermission(activity);
        wifiP2pManager.requestGroupInfo(channel, new WifiP2pManager.GroupInfoListener() {
            @Override
            public void onGroupInfoAvailable(WifiP2pGroup wifiP2pGroup) {
                if (wifiP2pGroup != null) {
                    Log.d(TAG, "Got group info, can start advertising service now");
                    HashMap<String,String> advertisement = new HashMap<>();
                    advertisement.put("SSID", wifiP2pGroup.getNetworkName());
                    advertisement.put("pass", wifiP2pGroup.getPassphrase());

                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    try {
                        Enumeration e = NetworkInterface.getNetworkInterfaces();
                        while (e.hasMoreElements()) {
                            NetworkInterface n = (NetworkInterface) e.nextElement();
                            List<InterfaceAddress> interfaceAddresses = n.getInterfaceAddresses();
                            Enumeration ee = n.getInetAddresses();
                            while (ee.hasMoreElements()) {
                                InetAddress i = (InetAddress) ee.nextElement();
                                if (!i.isLoopbackAddress()) {
                                    String iface = n.getDisplayName();
                                    String hostaddr = i.getHostAddress();
                                    if (hostaddr == null) {
                                        continue;
                                    }
                                    if (iface.contains("p2p") && hostaddr.contains("%")) {
                                        if (i.getAddress().length > 4) {
                                            Log.d(TAG, "IFACE: " + iface + " IPv6: " + hostaddr);
                                            int iend = hostaddr.indexOf('%');
                                            advertisement.put("ip", hostaddr.substring(0, iend));
                                            //advertisement.put("ip", hostaddr);
                                        }
                                    }
                                }
                            }
                        }
                    } catch (IOException ex) {
                        Log.d(TAG, "Error detecting ipv6 address");
                    }

                    wiFiDirectServiceManager.start(advertisement);
                } else {
                    Log.d(TAG, "Group info not available, trying again");
                    try {
                        Thread.sleep(500);
                    } catch(InterruptedException ex) {
                        return;
                    }
                    getGroupInfo();
                }
            }
        });

    }
}
