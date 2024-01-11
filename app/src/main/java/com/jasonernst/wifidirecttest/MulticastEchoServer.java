package com.jasonernst.wifidirecttest;

import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;

public class MulticastEchoServer {
    public static final int DEFAULT_PORT = 8887;
    public static final int MAX_RECEIVE_BUFFER_SIZE = 1024;
    private static final String TAG = MulticastEchoServer.class.getName();
    private MulticastSocket socket;
    //private DatagramSocket socket;
    private volatile boolean running = false;
    private Thread listenerThread;
    private InetAddress group;

    public void start() throws IOException {
        running = true;
        socket = new MulticastSocket(DEFAULT_PORT);
        group = InetAddress.getByName("ff02::1");
        socket.joinGroup(group);
        //socket = new DatagramSocket(DEFAULT_PORT);
        socket.setReuseAddress(true);
        listenerThread = new Thread(this::listen);
        listenerThread.start();
    }

    private void listen() {
        byte[] buffer = new byte[MAX_RECEIVE_BUFFER_SIZE];
        while (running) {
            DatagramPacket request = new DatagramPacket(buffer, MAX_RECEIVE_BUFFER_SIZE);
            try {
                socket.receive(request);
                byte[] recv = new byte[request.getLength()];
                Log.d(TAG, "MULTICAST Got " + recv.length + " bytes at server from " + request.getAddress() + ":" + request.getPort());
                System.arraycopy(request.getData(), 0, recv, 0, request.getLength());

                InetAddress clientAddress = request.getAddress();
                int clientPort = request.getPort();

                DatagramPacket response = new DatagramPacket(recv, recv.length, group, DEFAULT_PORT);
                //socket.send(response);
                //Log.d(TAG, "Sent back " + recv.length + " bytes");

            } catch(IOException ex) {
                Log.d(TAG, "IO Exception on recv: " + ex);
            }
        }
    }

    public void stop() {
        running = false;
        socket.close();
        listenerThread.interrupt();
    }
}
