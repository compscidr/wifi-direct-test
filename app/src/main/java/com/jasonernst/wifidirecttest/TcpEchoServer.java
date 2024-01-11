package com.jasonernst.wifidirecttest;

import android.util.Log;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class TcpEchoServer {
    public static final int DEFAULT_PORT = 8888;
    public static final int MAX_RECEIVE_BUFFER_SIZE = 1024;
    private static final String TAG = TcpEchoServer.class.getName();
    private ServerSocketChannel serverSocketChannel;
    private Executor executor;
    private volatile boolean running = false;

    public TcpEchoServer() {
        try {
            serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
            serverSocketChannel.bind(new InetSocketAddress(DEFAULT_PORT));
            executor = Executors.newFixedThreadPool(5);
        } catch(IOException ex) {
            Log.d(TAG, "IO Exception opening socket channel");
        }
    }

    public void start() {
        running = true;
        executor.execute(()-> {
            while (running) {
                Log.d(TAG, "TCPServer: waiting for connection");
                try {
                    SocketChannel socketChannel = serverSocketChannel.accept();
                    if (socketChannel == null) {
                        Log.d(TAG, "TCPServer: null socket channel");
                    } else {
                        Log.d(TAG, "TCPServer: Got a new connection");
                        executor.execute(() -> handleConnection(socketChannel));
                    }
                } catch (IOException e) {
                    Log.d(TAG, "TCPServer: Error on socket accept: " + e);
                }
            }
        });
    }

    private void handleConnection(SocketChannel socketChannel) {
        ByteBuffer buffer = ByteBuffer.allocate(MAX_RECEIVE_BUFFER_SIZE);
        while (socketChannel.isConnected() && socketChannel.socket().isConnected()) {
            try {
                int bytesRead = socketChannel.read(buffer);
                if (bytesRead > 0) {
                    buffer.rewind();
                    buffer.limit(bytesRead);
                    Log.d(TAG, "Read " + bytesRead + " bytes on TCP server");
                    int bytesWrote = socketChannel.write(buffer);
                    Log.d(TAG, "Wrote " + bytesWrote + " bytes from TCP server");
                    buffer.clear();
                }
            } catch (IOException ex) {
                Log.d(TAG, "TCPServer: IO Exception on read / write on TCP server:" + ex);
                break;
            }
        }
    }

    public void stop() {
        try {
            running = false;
            serverSocketChannel.close();
        } catch (IOException e) {
            Log.d(TAG, "TCPServer: Error closing server socket channel: " + e);
        }
    }
}
