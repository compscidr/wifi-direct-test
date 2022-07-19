package io.left.wifidirecttest;

import static io.left.wifidirecttest.UdpEchoServer.MAX_RECEIVE_BUFFER_SIZE;

import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class ConnectivityMonitor extends ConnectivityManager.NetworkCallback {
    private volatile boolean isConnecting = false;
    private volatile boolean isConnected = false;
    private static final String TAG = ConnectivityManager.class.getName();
    private NetworkRequest request;
    private ConnectivityManager connectivityManager;
    private InetAddress serverAddress;

    public ConnectivityMonitor(ConnectivityManager connectivityManager) {
        this.connectivityManager = connectivityManager;
    }

    public void setRequest(NetworkRequest request) {
        this.request = request;
    }

    public boolean isConnecting() {
        return isConnecting;
    }

    public void setConnecting(boolean connecting) {
        isConnecting = connecting;
    }

    public boolean isConnected() {
        return isConnected;
    }

    public void setServerAddress(InetAddress serverAddress) {
        this.serverAddress = serverAddress;
    }

    public InetAddress getServerAddress() { return serverAddress; }

    private void sendUdpMessage(InetAddress i) {
        new Thread(()->{
            // make a UDP request to the server and wait for a response
            try {
                DatagramSocket socket = new DatagramSocket();
                socket.setBroadcast(true);
                // DatagramSocket socket = new DatagramSocket(new InetSocketAddress(i, 0)); // didn't work
                // network.bindSocket(socket); // didn't work
                new Thread(()->{
                    int count = 0;
                    while (count < 10) {
                        byte[] buffer = "test".getBytes();
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, serverAddress, UdpEchoServer.DEFAULT_PORT);
                        try {
                            socket.send(packet);
                            Thread.sleep(1000);
                        } catch (IOException | InterruptedException ex) {
                            Log.e(TAG, "Error sending UDP packet from IF: " + i.getHostAddress() + " " + ex);
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException exc) {
                                exc.printStackTrace();
                            }
                        }
                        Log.d(TAG, "Sent" + buffer.length + " bytes to server");
                    }
                }).start();

                byte[] recvBuffer = new byte[MAX_RECEIVE_BUFFER_SIZE];
                DatagramPacket recv = new DatagramPacket(recvBuffer, MAX_RECEIVE_BUFFER_SIZE);
                socket.receive(recv);
                Log.d(TAG, "Received " + recv.getLength() + " bytes from the server");
            } catch (IOException ex) {
                Log.e(TAG, "Error recv UDP packet from IF: " + i.getHostAddress() + " " + ex);
            }
        }).start();
    }

    @Override
    public void onAvailable(@NonNull Network network) {
        super.onAvailable(network);
        Log.d(TAG,"Network available: " + network);
        isConnecting = false;
        isConnected = true;
        //connectivityManager.bindProcessToNetwork(network); // didn't work

        new Thread(()->{
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            AndroidUtil.detectInterfaces();
        }).start();
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
        isConnecting = false;
        isConnected = false;
    }

    @Override
    public void onUnavailable() {
        super.onUnavailable();
        Log.d(TAG,"Network unavailable");
//        isConnecting = false;
//        isConnected = false;
        connectivityManager.requestNetwork(request, this);
    }
}
