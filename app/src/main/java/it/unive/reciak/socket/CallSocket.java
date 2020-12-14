package it.unive.reciak.socket;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

import java.util.concurrent.ExecutorService;

import it.unive.reciak.PeerInfo;

public class CallSocket extends TCPChannelClient {
    public CallSocket(ExecutorService executor, TCPChannelEvents eventListener, PeerInfo peerInfo) {
        super(executor, eventListener, new PeerInfo(peerInfo.getIp(), peerInfo.getPort(), !peerInfo.isInitiator()));
    }

    // Invia informazioni sull'indirizzo del peer
    public void sendIceCandidate(@NonNull IceCandidate iceCandidate) {
        executor.execute(() -> {
            JSONObject iceCandidateJson = new JSONObject();
            JSONObject packet = new JSONObject();

            try {
                iceCandidateJson.put("sdp", iceCandidate.sdp);
                iceCandidateJson.put("sdpMLineIndex", iceCandidate.sdpMLineIndex);
                iceCandidateJson.put("sdpMid", iceCandidate.sdpMid);

                packet.put("action", "addIceCandidate");
                packet.put("value", iceCandidateJson);

                send(packet);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });
    }

    // Invia informazioni sul dispositivo (es. codec, flussi video da inviare)
    public void sendSessionDescription(@NonNull SessionDescription sessionDescription, boolean isAnswer) {
        executor.execute(() -> {
            JSONObject packet = new JSONObject();
            JSONObject sessionDescriptionJson = new JSONObject();

            try {
                sessionDescriptionJson.put("isAnswer", isAnswer);
                sessionDescriptionJson.put("sessionDescription", sessionDescription.description);

                packet.put("action", "setSessionDescription");
                packet.put("value", sessionDescriptionJson);

                send(packet);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });
    }

    // Invia ai peer le informazioni per connettersi fra loro. Inizialmente i peer sono connessi soltanto al creatore della stanza
    public void sendAddUser(@NonNull String partnerIp, int partnerPort, boolean isInitiator) {
        executor.execute(() -> {
            JSONObject packet = new JSONObject();
            JSONObject userJson = new JSONObject();

            try {
                userJson.put("partnerIp", partnerIp);
                userJson.put("partnerPort", partnerPort);
                userJson.put("isInitiator", isInitiator);

                packet.put("action", "addUser");
                packet.put("value", userJson);
                send(packet);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });
    }
}
