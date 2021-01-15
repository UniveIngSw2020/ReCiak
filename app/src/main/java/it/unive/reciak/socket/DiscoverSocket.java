package it.unive.reciak.socket;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.ExecutorService;

import it.unive.reciak.webrtc.PeerInfo;

/**
 * Socket ricerca di un peer via Wi-Fi Direct.
 */
public class DiscoverSocket extends TCPChannelClient {
    public DiscoverSocket(ExecutorService executor, TCPChannelEvents eventListener, PeerInfo peerInfo) {
        super(executor, eventListener, peerInfo);
    }

    /**
     * Invia il proprio indirizzo all'amministratore.
     *
     * I dispositivi connessi a una rete Wi-Fi Direct non hanno a disposizione gli indirizzi dei
     * dispositivi connessi (hanno solamente l'indirizzo dell'amministratore della rete).
     *
     * @param ip IP del dispositivo
     * @param port porta del dispositivo
     */
    public void sendAddress(@NonNull String ip, int port) {
        executor.execute(() -> {
            JSONObject addressJson = new JSONObject();

            try {
                addressJson.put("partnerIp", ip);
                addressJson.put("partnerPort", port);

                send("address", addressJson);
            } catch (JSONException e) {
                e.printStackTrace();
            }

            // Chiude il socket
            disconnect();
        });
    }
}
