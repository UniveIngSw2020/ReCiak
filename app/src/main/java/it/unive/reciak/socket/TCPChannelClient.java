/*
 *  Copyright 2016 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 *
 * Source: https://webrtc.googlesource.com/src/+/master/examples/androidapp/src/org/appspot/apprtc/TCPChannelClient.java
 */

package it.unive.reciak.socket;

import android.util.Log;

import androidx.annotation.Nullable;

import org.json.JSONObject;
import org.webrtc.ThreadUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;

import it.unive.reciak.PeerInfo;

public class TCPChannelClient {
    private static final String TAG = "TCPChannelClient";
    protected final ExecutorService executor;
    private final ThreadUtils.ThreadChecker executorThreadCheck;
    private final TCPChannelEvents eventListener;
    private TCPSocket socket;

    public interface TCPChannelEvents {
        void onTCPConnected();
        void onTCPMessage(String message);
        void onTCPError(String description);
        void onTCPClose();
    }

    public TCPChannelClient(ExecutorService executor, TCPChannelEvents eventListener, PeerInfo peerInfo) {
        this.executor = executor;
        executorThreadCheck = new ThreadUtils.ThreadChecker();
        executorThreadCheck.detachThread();
        this.eventListener = eventListener;
        InetAddress address;

        try {
            address = InetAddress.getByName(peerInfo.getIp());
        } catch (UnknownHostException e) {
            reportError("Invalid IP address.");
            return;
        }

        if (peerInfo.isInitiator()) {
            socket = new TCPSocketServer(peerInfo.getPort());
        } else {
            socket = new TCPSocketClient(address, peerInfo.getPort());
        }
        socket.start();
    }

    public void disconnect() {
        executorThreadCheck.checkIsOnValidThread();
        socket.disconnect();
    }

    public void send(JSONObject json) {
        executorThreadCheck.checkIsOnValidThread();
        socket.send(json);
    }

    private void reportError(final String message) {
        Log.e(TAG, "TCP Error: " + message);
        executor.execute(() -> eventListener.onTCPError(message));
    }

    private abstract class TCPSocket extends Thread {
        // Lock for editing out and rawSocket
        protected final Object rawSocketLock;
        @Nullable
        private PrintWriter out;
        @Nullable
        private Socket rawSocket;

        @Nullable
        public abstract Socket connect();

        public TCPSocket() {
            rawSocketLock = new Object();
        }

        @Override
        public void run() {
            Log.d(TAG, "Listening thread started...");

            Socket tempSocket = connect();
            BufferedReader in;
            Log.d(TAG, "TCP connection established.");
            synchronized (rawSocketLock) {
                if (rawSocket != null) {
                    Log.e(TAG, "Socket already existed and will be replaced.");
                }
                rawSocket = tempSocket;
                // Connecting failed, error has already been reported, just exit.
                if (rawSocket == null) {
                    return;
                }
                try {
                    out = new PrintWriter(new OutputStreamWriter(rawSocket.getOutputStream(), StandardCharsets.UTF_8), true);
                    in = new BufferedReader(new InputStreamReader(rawSocket.getInputStream(), StandardCharsets.UTF_8));
                } catch (IOException e) {
                    reportError("Failed to open IO on rawSocket: " + e.getMessage());
                    return;
                }
            }
            Log.v(TAG, "Execute onTCPConnected");
            executor.execute(() -> {
                    Log.v(TAG, "Run onTCPConnected");
                    eventListener.onTCPConnected();
            });

            while (true) {
                final String message;
                try {
                    message = in.readLine();
                } catch (IOException e) {
                    synchronized (rawSocketLock) {
                        // If socket was closed, this is expected.
                        if (rawSocket == null) {
                            break;
                        }
                    }
                    reportError("Failed to read from rawSocket: " + e.getMessage());
                    break;
                }
                // No data received, rawSocket probably closed.
                if (message == null) {
                    break;
                }
                executor.execute(() -> {
                    Log.v(TAG, "Receive: " + message);
                    eventListener.onTCPMessage(message);
                });
            }
            Log.d(TAG, "Receiving thread exiting...");
            // Close the rawSocket if it is still open.
            disconnect();
        }

        public void disconnect() {
            try {
                synchronized (rawSocketLock) {
                    if (rawSocket != null) {
                        rawSocket.close();
                        rawSocket = null;
                        out = null;
                        executor.execute(eventListener::onTCPClose);
                    }
                }
            } catch (IOException e) {
                reportError("Failed to close rawSocket: " + e.getMessage());
            }
        }

        public void send(JSONObject json) {
            final String message = json.toString();

            Log.v(TAG, "Send: " + message);
            synchronized (rawSocketLock) {
                if (out == null) {
                    reportError("Sending data on closed socket.");
                    return;
                }
                out.write(message + "\n");
                out.flush();
            }
        }
    }

    private class TCPSocketServer extends TCPSocket {
        @Nullable
        private ServerSocket serverSocket;
        final private int port;

        public TCPSocketServer(int port) {
            this.port = port;
        }

        @Nullable
        @Override
        public Socket connect() {
            Log.d(TAG, "Listening on " + port);
            final ServerSocket tempSocket;
            try {
                tempSocket = new ServerSocket(port, 0);
            } catch (IOException e) {
                reportError("Failed to create server socket: " + e.getMessage());
                return null;
            }
            synchronized (rawSocketLock) {
                if (serverSocket != null) {
                    Log.e(TAG, "Server rawSocket was already listening and new will be opened.");
                }
                serverSocket = tempSocket;
            }
            try {
                return tempSocket.accept();
            } catch (IOException e) {
                reportError("Failed to receive connection: " + e.getMessage());
                return null;
            }
        }

        @Override
        public void disconnect() {
            try {
                synchronized (rawSocketLock) {
                    if (serverSocket != null) {
                        serverSocket.close();
                        serverSocket = null;
                    }
                }
            } catch (IOException e) {
                reportError("Failed to close server socket: " + e.getMessage());
            }
            super.disconnect();
        }
    }

    private class TCPSocketClient extends TCPSocket {
        final private InetAddress address;
        final private int port;

        public TCPSocketClient(InetAddress address, int port) {
            this.address = address;
            this.port = port;
        }

        @Nullable
        @Override
        public Socket connect() {
            Log.d(TAG, "Connecting to [" + address.getHostAddress() + "]:" + port);
            try {
                return new Socket(address, port);
            } catch (IOException e) {
                reportError("Failed to connect: " + e.getMessage());
                return null;
            }
        }
    }
}