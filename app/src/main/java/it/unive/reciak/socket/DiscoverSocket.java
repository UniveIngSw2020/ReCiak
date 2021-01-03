package it.unive.reciak.socket;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.ExecutorService;

import it.unive.reciak.webrtc.PeerInfo;

// Socket ricerca di un peer via Wi-Fi Direct
public class DiscoverSocket extends TCPChannelClient {
    public DiscoverSocket(ExecutorService executor, TCPChannelEvents eventListener, PeerInfo peerInfo) {
        super(executor, eventListener, peerInfo);
    }

    // Invia il proprio indirizzo all'amministratore
    public void sendAddress(@NonNull String ip, int port) {
        executor.execute(() -> {
            JSONObject packet = new JSONObject();
            JSONObject addressJson = new JSONObject();

            try {
                addressJson.put("partnerIp", ip);
                addressJson.put("partnerPort", port);

                packet.put("action", "address");
                packet.put("value", addressJson);
                send(packet);
            } catch (JSONException e) {
                e.printStackTrace();
            }

            disconnect();
        });
    }
}
