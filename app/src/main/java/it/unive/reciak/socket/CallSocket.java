package it.unive.reciak.socket;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

import java.util.concurrent.ExecutorService;

import it.unive.reciak.webrtc.PeerInfo;

/**
 * Socket connessione a un peer via WebRTC.
 */
public class CallSocket extends TCPChannelClient {
    public CallSocket(ExecutorService executor, TCPChannelEvents eventListener, PeerInfo peerInfo) {
        super(executor, eventListener, new PeerInfo(peerInfo.getIp(), peerInfo.getPort(), !peerInfo.isInitiator()));
    }

    /**
     * Invia informazioni ICE (utile a stabilire una connessione con l'altro dispositivo).
     *
     * @param iceCandidate informazioni ICE
     */
    public void sendIceCandidate(@NonNull IceCandidate iceCandidate) {
        executor.execute(() -> {
            JSONObject iceCandidateJson = new JSONObject();

            try {
                iceCandidateJson.put("sdp", iceCandidate.sdp);
                iceCandidateJson.put("sdpMLineIndex", iceCandidate.sdpMLineIndex);
                iceCandidateJson.put("sdpMid", iceCandidate.sdpMid);

                send("addIceCandidate", iceCandidateJson);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * Invia informazioni sul dispositivo (es. codec, flussi video da inviare).
     *
     * @param sessionDescription SessionDescription creata in precedenza
     * @param type tipo offer/answer
     */
    public void sendSessionDescription(@NonNull SessionDescription sessionDescription, @NonNull String type) {
        executor.execute(() -> {
            JSONObject sessionDescriptionJson = new JSONObject();

            try {
                sessionDescriptionJson.put("type", type);
                sessionDescriptionJson.put("sessionDescription", sessionDescription.description);

                send("setSessionDescription", sessionDescriptionJson);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * Invia a un peer le informazioni per connettersi fra loro.
     * Inizialmente i peer sono connessi solamente all'amministratore della stanza.
     *
     * @param partnerIp ip del peer
     * @param partnerPort porta del peer
     * @param isInitiator true se deve avviare la negoziazione
     */
    public void sendAddUser(@NonNull String partnerIp, int partnerPort, boolean isInitiator) {
        executor.execute(() -> {
            JSONObject userJson = new JSONObject();

            try {
                userJson.put("partnerIp", partnerIp);
                userJson.put("partnerPort", partnerPort);
                userJson.put("isInitiator", isInitiator);

                send("addUser", userJson);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });
    }
}
