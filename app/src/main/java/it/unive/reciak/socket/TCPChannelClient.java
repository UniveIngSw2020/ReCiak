package it.unive.reciak.socket;

import android.util.Log;

import androidx.annotation.Nullable;

import org.json.JSONException;
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
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;

import it.unive.reciak.webrtc.PeerInfo;

/**
 * Socket Server e Client con callback.
 * 
 * @see <a href="https://webrtc.googlesource.com/src/+/master/examples/androidapp/src/org/appspot/apprtc/TCPChannelClient.java">Sorgente originale</a>
 * @author <a href="https://webrtc.googlesource.com/src/+/master/AUTHORS">AUTHORS</a>
 */
public class TCPChannelClient {
    private static final String TAG = "TCPChannelClient";
    // Executor gestione callback
    protected final ExecutorService executor;
    // Callback eventi
    private final TCPChannelEvents eventListener;
    // Socket client/server
    private TCPSocket socket;

    /**
     * Gestore eventi socket.
     */
    public interface TCPChannelEvents {
        /**
         * Callback dispositivo connesso al socket.
         */
        void onTCPConnected();
        /**
         * Callback messaggio ricevuto.
         *
         * @param message messaggio JSON
         */
        void onTCPMessage(JSONObject message);
        /**
         * Callback errore di connessione.
         */
        void onTCPError();
        /**
         * Callback socket chiuso.
         */
        void onTCPClose();
    }

    public TCPChannelClient(ExecutorService executor, TCPChannelEvents eventListener, PeerInfo peerInfo) {
        this.executor = executor;
        ThreadUtils.ThreadChecker executorThreadCheck = new ThreadUtils.ThreadChecker();
        executorThreadCheck.detachThread();
        this.eventListener = eventListener;
        InetAddress address;

        try {
            address = InetAddress.getByName(peerInfo.getIp());
        } catch (UnknownHostException e) {
            onError("Invalid IP address.");
            return;
        }

        // Se devo iniziare la comunicazione creo un ServerSocket
        if (peerInfo.isInitiator()) {
            socket = new TCPSocketServer(peerInfo.getPort());
        } else {
            // Altrimenti creo un Socket
            socket = new TCPSocketClient(address, peerInfo.getPort());
        }
        // Avvia il socket
        socket.start();
    }

    /**
     * Chiude il socket.
     */
    public void disconnect() {
        socket.disconnect();
    }

    /**
     * Invia un pacchetto JSON all'altro dispositivo.
     *
     * @param action tipo di messaggio
     * @param json corpo del messaggio
     */
    public void send(String action, JSONObject json) {
        // Crea il pacchetto JSON
        JSONObject packet = new JSONObject();

        try {
            packet.put("action", action);
            packet.put("value", json);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        // Invia il pacchetto
        socket.send(packet);
    }

    /**
     * Errore di connessione al socket.
     *
     * @param message messaggio d'errore
     */
    private void onError(final String message) {
        Log.e(TAG, "TCP Error: " + message);
        executor.execute(eventListener::onTCPError);
    }

    /**
     * Classe padre di TCPSocketClient e TCPSocketServer.
     */
    private abstract class TCPSocket extends Thread {
        // Lock gestione rawSocket e out
        protected final Object rawSocketLock;
        @Nullable
        private PrintWriter out;
        @Nullable
        private Socket rawSocket;

        /**
         * Creazione del socket e connessione all'altro dispositivo.
         *
         * @return socket per comunicare con l'altro dispositivo
         */
        @Nullable
        public abstract Socket connect();

        public TCPSocket() {
            rawSocketLock = new Object();
        }

        @Override
        public void run() {
            Log.d(TAG, "Listening thread started...");

            // Crea il socket
            Socket tempSocket = connect();
            BufferedReader in;
            Log.d(TAG, "TCP connection established.");
            synchronized (rawSocketLock) {
                // Salva il socket
                if (rawSocket != null) {
                    Log.e(TAG, "Socket already existed and will be replaced.");
                }
                rawSocket = tempSocket;
                // Connessione fallita
                if (rawSocket == null) {
                    return;
                }
                // Buffer scrittura e lettura
                try {
                    out = new PrintWriter(new OutputStreamWriter(rawSocket.getOutputStream(), StandardCharsets.UTF_8), true);
                    in = new BufferedReader(new InputStreamReader(rawSocket.getInputStream(), StandardCharsets.UTF_8));
                } catch (IOException e) {
                    onError("Failed to open IO on rawSocket: " + e.getMessage());
                    return;
                }
            }
            Log.v(TAG, "Execute onTCPConnected");
            executor.execute(() -> {
                Log.v(TAG, "Run onTCPConnected");
                eventListener.onTCPConnected();
            });

            // Ricezione messaggi
            while (true) {
                final String message;
                // Salva il messaggio
                try {
                    message = in.readLine();
                } catch (IOException e) {
                    synchronized (rawSocketLock) {
                        // Socket chiuso
                        if (rawSocket == null) {
                            break;
                        }
                    }
                    onError("Failed to read from rawSocket: " + e.getMessage());
                    break;
                }
                // Non ha ricevuto messaggi. Probabilmente il socket è chiuso
                if (message == null) {
                    break;
                }
                // Messaggio ricevuto
                executor.execute(() -> {
                    Log.v(TAG, "Receive: " + message);

                    try {
                        JSONObject packet = new JSONObject(message);
                        eventListener.onTCPMessage(packet);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                });
            }
            Log.d(TAG, "Receiving thread exiting...");
            // Chiude il socket
            disconnect();
        }

        /**
         * Disconnessione del socket.
         */
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
                onError("Failed to close rawSocket: " + e.getMessage());
            }
        }

        /**
         * Invia un messaggio all'altro dispositivo.
         *
         * @param json messaggio in JSON
         */
        public void send(JSONObject json) {
            final String message = json.toString();

            Log.v(TAG, "Send: " + message);
            synchronized (rawSocketLock) {
                if (out == null) {
                    onError("Sending data on closed socket.");
                    return;
                }
                out.write(message + "\n");
                out.flush();
            }
        }
    }

    /**
     * Crea una ServerSocket e attende che un dispositivo si connetta.
     */
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
            // Crea un ServerSocket
            final ServerSocket tempSocket;
            try {
                tempSocket = new ServerSocket(port, 0);
            } catch (IOException e) {
                onError("Failed to create server socket: " + e.getMessage());
                return null;
            }
            synchronized (rawSocketLock) {
                if (serverSocket != null) {
                    Log.e(TAG, "Server rawSocket was already listening and new will be opened.");
                }
                serverSocket = tempSocket;
            }

            // Attende un dispositivo
            try {
                return tempSocket.accept();
            } catch (SocketException e) {
                return null;
            } catch (IOException e) {
                onError("Failed to receive connection: " + e.getMessage());
                return null;
            }
        }

        @Override
        public void disconnect() {
            try {
                // Chiude il ServerSocket
                synchronized (rawSocketLock) {
                    if (serverSocket != null) {
                        serverSocket.close();
                        serverSocket = null;
                    }
                }
            } catch (IOException e) {
                onError("Failed to close server socket: " + e.getMessage());
            }
            super.disconnect();
        }
    }

    /**
     * Socket client.
     * Si connette a un socket di tipo ServerSocket (TCPSocketServer) creato da un altro dispositivo.
     */
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
            // Connessione al ServerSocket dell'altro dispositivo
            try {
                return new Socket(address, port);
            } catch (IOException e) {
                onError("Failed to connect: " + e.getMessage());
                return null;
            }
        }
    }
}