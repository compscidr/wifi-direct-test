package com.jasonernst.wifidirecttest;

import static android.net.wifi.p2p.WifiP2pManager.BUSY;
import static android.net.wifi.p2p.WifiP2pManager.ERROR;
import static android.net.wifi.p2p.WifiP2pManager.P2P_UNSUPPORTED;

import static com.jasonernst.wifidirecttest.TcpEchoServer.DEFAULT_PORT;
import static com.jasonernst.wifidirecttest.TcpEchoServer.MAX_RECEIVE_BUFFER_SIZE;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.net.Network;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.List;

public class AndroidUtil {
    public static final int REQUEST_ACCESS_FINE_LOCATION = 1;
    public static final int DEFAULT_UDP_ADDRESS = 9999;

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

    public static String detectInterfaces() {
        String IPs = "IPs: ";
        try {
            Enumeration e = NetworkInterface.getNetworkInterfaces();
            while (e.hasMoreElements()) {
                NetworkInterface n = (NetworkInterface) e.nextElement();
                List<InterfaceAddress> interfaceAddresses = n.getInterfaceAddresses();

                Enumeration ee = n.getInetAddresses();
                while (ee.hasMoreElements()) {
                    InetAddress i = (InetAddress) ee.nextElement();
                    if (i.isLoopbackAddress() || n.getDisplayName().contains("rmnet") || n.getDisplayName().contains("dummy")) {
                        continue;
                    }
                    String iface = n.getDisplayName();

                    for (InterfaceAddress interfaceAddress : interfaceAddresses) {
                        if (!IPs.contains(i.getHostAddress())) {
                            Log.d(TAG, "IFACE: " + iface + " " + i.getHostAddress() + " IFACE ADDR: " + interfaceAddress);
                            IPs += i.getHostAddress() + "\n";
                        }
                    }
                }
            }
        } catch (SocketException ex) {
            ex.printStackTrace();
        }
        return IPs;
    }

    public static SocketAddress getWlanAddress() throws SocketException {
        Enumeration e = NetworkInterface.getNetworkInterfaces();
        while (e.hasMoreElements()) {
            NetworkInterface n = (NetworkInterface) e.nextElement();
            List<InterfaceAddress> interfaceAddresses = n.getInterfaceAddresses();

            Enumeration ee = n.getInetAddresses();
            while (ee.hasMoreElements()) {
                InetAddress i = (InetAddress) ee.nextElement();
                // make sure we get the IPv6 wlan interface and not the ipv4 interface
                if (n.getDisplayName().contains("wlan") && i.getAddress().length > 4) {
                    String host = i.getHostAddress();
                    host = host.substring(0, host.indexOf('%'));
                    Log.d(TAG, "WLAN: " + host);
                    return new InetSocketAddress(host, DEFAULT_UDP_ADDRESS);
                }
            }
        }
        throw new SocketException("WLAN IF NOT FOUND");
    }

    public static void sendUdpMessage(String ipAddress, Activity activity) {
        new Thread(()->{
            Thread sendthread;
            Thread recvThread;
            DatagramSocket socket;
            InetAddress serverAddress;
            // make a UDP request to the server and wait for a response
            try {
                serverAddress = InetAddress.getByName(ipAddress);
                socket = new DatagramSocket();
                socket.setBroadcast(true);
                socket.setSoTimeout(1000);
            } catch (IOException ex) {
                Log.e(TAG, "Error making a datagram socket: " + ex);
                return;
            }
            // DatagramSocket socket = new DatagramSocket(new InetSocketAddress(i, 0)); // didn't work
            // network.bindSocket(socket); // didn't work
            sendthread = new Thread(()-> {
                byte[] buffer = "test".getBytes();
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, serverAddress, UdpEchoServer.DEFAULT_PORT);
                try {
                    socket.send(packet);
                    Thread.sleep(1000);
                } catch (IOException | InterruptedException ex) {
                    Log.e(TAG, "Error sending UDP packet to: " + serverAddress.getHostAddress() + " " + ex);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException exc) {
                        exc.printStackTrace();
                    }
                }
                Log.d(TAG, "Sent" + buffer.length + " bytes to server: " + serverAddress + ":" + UdpEchoServer.DEFAULT_PORT);
            });
            sendthread.start();

            recvThread = new Thread(()-> {
                byte[] recvBuffer = new byte[MAX_RECEIVE_BUFFER_SIZE];
                DatagramPacket recv = new DatagramPacket(recvBuffer, MAX_RECEIVE_BUFFER_SIZE);
                try {
                    socket.receive(recv);
                } catch (IOException ex) {
                    Log.d(TAG, "Error recv: " + ex);
                }
                Log.d(TAG, "Received " + recv.getLength() + " bytes from the server: " + recv.getAddress() + ":" + recv.getPort());
                activity.runOnUiThread(() -> {
                    int duration = Toast.LENGTH_SHORT;
                    Toast.makeText(activity.getApplicationContext(), "GOT MSG", duration).show();
                });
            });
            recvThread.start();

            try {
                sendthread.join();
                recvThread.join();
            } catch (InterruptedException ex) {
                // pass
            }

        }).start();
    }

    public static void sendTcpMessage(String ipAddress) {
        new Thread(()->{
            InetAddress serverAddress;
            try {
                serverAddress = InetAddress.getByName(ipAddress);
            } catch (UnknownHostException e) {
                Log.d(TAG, "Error looking up server address: " + ipAddress + ": " + e);
                return;
            }
            InetSocketAddress inetSocketAddress = new InetSocketAddress(serverAddress, DEFAULT_PORT);
            Socket s = new Socket();
            OutputStream outputStream;
            InputStream inputStream;
            try {
                Log.d(TAG, "trying to connect to server address: " + ipAddress);
                s.connect(inetSocketAddress, 1000);
                Log.d(TAG, "connected to server address: " + ipAddress);
                outputStream = s.getOutputStream();
                inputStream = s.getInputStream();
            } catch (IOException e) {
                Log.d(TAG, "Error connecting to server address: " + ipAddress + ": " + e);
                return;
            }

            try {
                outputStream.write("test".getBytes());
                outputStream.flush();
            } catch (IOException e) {
                Log.d(TAG, "Failed to write to server: " + e);
                return;
            }

            try {
                byte[] recvBuffer = new byte[MAX_RECEIVE_BUFFER_SIZE];
                int bytesRead = inputStream.read(recvBuffer);
                Log.d(TAG, "READ " + bytesRead + " bytes from server");
                s.close();
            } catch (IOException e) {
                Log.d(TAG, "Failed to read from server: " + e);
            }

        }).start();
    }

    public static void sendUdpMulticast(Network network) {
        new Thread(()->{
            // make a UDP request to the server and wait for a response
            InetAddress group;
            DatagramSocket socket;

            while (true) {
                try {
                    group = InetAddress.getByName("ff02::1");
                    socket = new DatagramSocket(getWlanAddress());
                    //network.bindSocket(socket); // didn't work
                    break;

                } catch(SocketException e) {
                    Log.d(TAG, "Error opening socket: " + e);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ex) {
                        return;
                    }
                } catch (UnknownHostException e) {
                    Log.d(TAG, "Error creating multicast group:" + e);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ex) {
                        return;
                    }
                } catch (Exception e) {
                    Log.d(TAG, "Error binding socket: " + e);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ex) {
                        return;
                    }
                }
            }

            while (true) {
                byte[] buffer = "test".getBytes();
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, MulticastEchoServer.DEFAULT_PORT);
                try {
                    socket.send(packet);
                } catch (IOException ex) {
                    Log.d(TAG, "Failed sending multicast packet: " + ex);
                    socket.close();
                }

//                new Thread(() -> {
//                    int count = 0;
//                    while (count < 2) {
//                        byte[] recvBuffer = new byte[MAX_RECEIVE_BUFFER_SIZE];
//                        DatagramPacket recv = new DatagramPacket(recvBuffer, MAX_RECEIVE_BUFFER_SIZE);
//                        try {
//                            socket.receive(recv);
//                            Log.d(TAG, "Received " + recv.getLength() + " bytes from: " + recv.getAddress() + ":" + recv.getPort());
//                        } catch(IOException ex) {
//                            Log.e(TAG, "Error recv: " + ex);
//                            break;
//                        }
//                    }
//                }).start();
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }).start();
    }
}
