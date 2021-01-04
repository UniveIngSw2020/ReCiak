package it.unive.reciak.webrtc.connection;

import android.content.Context;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.MediaStreamTrack;
import org.webrtc.PeerConnection;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpSender;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import it.unive.reciak.R;
import it.unive.reciak.socket.CallSocket;
import it.unive.reciak.socket.TCPChannelClient;
import it.unive.reciak.webrtc.PeerInfo;
import it.unive.reciak.webrtc.record.RecordChannel;

// Gestione di connessione con un peer
class RTCPeerConnection implements SdpObserver, PeerConnection.Observer, TCPChannelClient.TCPChannelEvents {
    private static final String TAG = "RTCPeerConnection";

    // Gestore connessioni WebRTC
    private final RTCRoomConnection room;

    // Informazioni del peer
    @NonNull
    private final PeerInfo peerInfo;

    // Gestione socket e registrazione
    @NonNull
    private final ExecutorService executor;
    // Socket per la negoziazione della chiamata
    @NonNull
    private CallSocket callSocket;

    // Gestore della connessione con l'altro peer
    @Nullable
    private PeerConnection peerConnection;

    // Gestori invio flusso video e audio all'altro peer
    @Nullable
    private RtpSender videoSender;
    @Nullable
    private RtpSender audioSender;

    private final Context context;

    public RTCPeerConnection(RTCRoomConnection room, @NonNull PeerInfo peerInfo, Context context) {
        this.room = room;
        this.peerInfo = peerInfo;
        this.context = context;

        executor = Executors.newSingleThreadExecutor();
        callSocket = new CallSocket(executor, this, peerInfo);
    }

    // Avvia il peer
    public void start() {
        Log.i(TAG, "start");
        if (room.peerConnectionFactory == null)
            onError(new Throwable("Impossibile connettersi al peer"));

        // Si connette all'altro peer
        peerConnection = room.peerConnectionFactory.createPeerConnection(room.rtcConfig, this);
        if (peerConnection == null)
            onError(new Throwable("Impossibile connettersi al peer"));

        // Se sono l'amministratore mostro i pulsanti e la mia fotocamera
        if (room.isServer() && peerInfo.isInitiator() && room.videoTrack != null) {
            room.videoTrack.addSink(room.mainView);
            room.runOnUiThread(() -> {
                room.mainView.setVisibility(View.VISIBLE);
                room.btnRecord.setVisibility(View.VISIBLE);
                room.btnSwitch.setVisibility(View.VISIBLE);
            });
        }
    }

    // Avvia condivisione video
    public void startVideo() {
        Log.i(TAG, "startVideo");
        // Se non sta già condividendo qualcosa
        if (videoSender == null && audioSender == null && peerConnection != null) {
            ArrayList<String> mediaStreamLabels = new ArrayList<>();
            mediaStreamLabels.add("ARDAMS");

            // Invia il proprio flusso video al peer
            videoSender = peerConnection.addTrack(room.videoTrack, mediaStreamLabels);
            audioSender = peerConnection.addTrack(room.audioTrack, mediaStreamLabels);

            if (room.videoTrack == null)
                onError(new Throwable("Impossibile collegarsi alla fotocamera"));

            // Nasconde la view più piccola
            room.runOnUiThread(() -> room.rightView.setVisibility(View.INVISIBLE));

            if (room.isFirst(this)) {
                // Visualizza la propria fotocamera nella view centrale
                room.videoTrack.addSink(room.mainView);
                executor.execute(() -> room.startRecording(RecordChannel.INPUT));
            }
            // Ha avviato la condivisione video
            room.setSharing(true);
        }
    }

    // Termina condivisione video
    public void stopVideo() {
        //Log.i(TAG, "stopVideo");
        // Termina la registrazione
        room.stopRecording();

        // Le anteprime vengono rimosse
        if (room.videoTrack != null) {
            room.videoTrack.removeSink(room.mainView);
            room.videoTrack.removeSink(room.rightView);
        }
        if (room.remoteVideoTrack != null) {
            room.remoteVideoTrack.removeSink(room.mainView);
            room.remoteVideoTrack.removeSink(room.rightView);
        }

        // Interrompe la condivisione video
        if (videoSender != null && audioSender != null) {
            room.rightView.clearImage();
            room.runOnUiThread(() -> room.rightView.setVisibility(View.VISIBLE));

            if (peerConnection != null) {
                peerConnection.removeTrack(videoSender);
                videoSender = null;

                peerConnection.removeTrack(audioSender);
                audioSender = null;
            }
        }
    }

    // Deve iniziare la negoziazione
    public boolean isInitiator() {
        return peerInfo.isInitiator();
    }

    // Aggiunge un altro peer
    public void addUser(String ip, int port, boolean isInitiator) {
        // Invia le informazioni del terzo peer
        callSocket.sendAddUser(ip, port, isInitiator);
    }

    public String getIp() {
        return peerInfo.getIp();
    }

    public void onError(@NonNull Throwable error) {
        error.printStackTrace();
        room.runOnUiThread(() -> Toast.makeText(context, error.getMessage(), Toast.LENGTH_SHORT).show());
        // Chiude la connessione con l'altro peer
        dispose();
    }

    // Chiude la connessione con il peer
    public void dispose() {
        Log.i(TAG, "dispose");
        // Interrompe la condivisione
        stopVideo();

        // Disconnessione
        if (peerConnection != null) {
            peerConnection.dispose();
            peerConnection = null;
        }

        // Chiude il socket
        callSocket.disconnect();
        executor.shutdown();
    }

    // Inizia la negoziazione
    public void createOffer() {
        Log.i(TAG, "createOffer");
        if (peerConnection != null) {
            MediaConstraints constraints = new MediaConstraints();
            peerConnection.createOffer(this, constraints);
        }
    }

    // Gestione della SessionDescription remota
    private void setRemoteDescription(String description, String type) {
        if (peerConnection != null) {
            SessionDescription sessionDescription = new SessionDescription(SessionDescription.Type.fromCanonicalForm(type), description);
            // Salva la SessionDescription remota
            peerConnection.setRemoteDescription(this, sessionDescription);
            // Risponde al peer
            MediaConstraints constraints = new MediaConstraints();
            peerConnection.createAnswer(this, constraints);
        }
    }

    // Negoziazione iniziata: viene generata una SessionDescription locale
    @Override
    public void onCreateSuccess(SessionDescription sessionDescription) {
        Log.i(TAG, "onCreateSuccess");
        if (peerConnection != null) {
            // Salva la propria SessionDescription e la invia al peer
            peerConnection.setLocalDescription(this, sessionDescription);
            callSocket.sendSessionDescription(sessionDescription, sessionDescription.type.toString().toLowerCase());
        }
    }

    @Override
    public void onSetSuccess() {
    }

    @Override
    public void onCreateFailure(@NonNull String s) {
    }

    @Override
    public void onSetFailure(@NonNull String s) {
    }

    // Se c'è un dispositivo pronto invia il proprio ICE
    @Override
    public void onIceCandidate(@NonNull IceCandidate iceCandidate) {
        Log.i(TAG, "onIceCandidate");
        callSocket.sendIceCandidate(iceCandidate);
    }

    @Override
    public void onDataChannel(@NonNull DataChannel dataChannel) {
    }

    @Override
    public void onIceConnectionReceivingChange(boolean receivingChange) {
    }

    @Override
    public void onIceConnectionChange(@NonNull PeerConnection.IceConnectionState state) {
    }

    @Override
    public void onIceGatheringChange(@NonNull PeerConnection.IceGatheringState state) {
    }

    @Override
    public void onAddStream(@NonNull MediaStream mediaStream) {
    }

    @Override
    public void onSignalingChange(@NonNull PeerConnection.SignalingState signalingState) {
    }

    @Override
    public void onIceCandidatesRemoved(@NonNull IceCandidate[] list) {
    }

    @Override
    public void onRemoveStream(@NonNull MediaStream mediaStream) {
    }

    @Override
    public void onRenegotiationNeeded() {
    }

    // Quando riceve una traccia remota
    @Override
    public void onAddTrack(@NonNull RtpReceiver receiver, @NonNull MediaStream[] mediaStreams) {
        Log.i(TAG, "onAddTrack");
        @Nullable
        MediaStreamTrack track = receiver.track();

        // Se è una traccia video
        if (track instanceof VideoTrack) {
            // Termina condivisione video
            room.stopVideo();
            room.setSharing(false);

            // Visualizza il flusso video nella view principale
            room.remoteVideoTrack = (VideoTrack) track;
            room.remoteVideoTrack.addSink(room.mainView);

            // Mostra la propria fotocamera nella view più piccola
            if (room.videoTrack != null)
                room.videoTrack.addSink(room.rightView);
            // Avvia la registrazione
            executor.execute(() -> room.startRecording(RecordChannel.OUTPUT));
            room.runOnUiThread(() -> {
                // Mostra le view
                room.mainView.setVisibility(View.VISIBLE);
                room.rightView.setVisibility(View.VISIBLE);
                room.btnRecord.setVisibility(View.VISIBLE);
                room.btnSwitch.setVisibility(View.VISIBLE);

                // Avvisa l'utente della nuova registrazione
                Toast toast = Toast.makeText(context, R.string.recording, Toast.LENGTH_SHORT);
                toast.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 50);
                toast.show();
            });
        }
    }

    // Connesso al socket dell'altro peer
    @Override
    public void onTCPConnected() {
        Log.i(TAG, "onTCPConnected");
        // Se sono l'amministratore
        if (peerInfo.isInitiator()) {
            // Avvio il peer
            start();
        }

        // Notifica la presenza di altri peer al dispositivo appena connesso
        room.addUser(this);
    }

    // Il dispositivo ha ricevuto un messaggio dal peer
    public void onTCPMessage(@NonNull String message) {
        try {
            JSONObject packet = new JSONObject(message);
            String action = packet.getString("action");
            // Risponde in base al messaggio ricevuto
            switch (action) {
                // Riceve informazioni di trasmissione del peer
                case "setSessionDescription":
                    JSONObject sessionDescriptionJson = packet.getJSONObject("value");
                    String type = sessionDescriptionJson.getString("type");
                    String sessionDescription = sessionDescriptionJson.getString("sessionDescription");

                    // Se non sono l'amministratore e ricevo un'offerta, avvio il peer
                    if (!peerInfo.isInitiator() && peerConnection == null)
                        start();

                    // Salva la SessionDescription remota
                    setRemoteDescription(sessionDescription, type);
                    break;
                // Informazioni sull'indirizzo del peer
                case "addIceCandidate":
                    JSONObject iceCandidateJson = packet.getJSONObject("value");
                    String sdp = iceCandidateJson.getString("sdp");
                    int sdpMLineIndex = iceCandidateJson.getInt("sdpMLineIndex");
                    String sdpMid = iceCandidateJson.getString("sdpMid");

                    // Salva l'indirizzo del peer
                    IceCandidate iceCandidate = new IceCandidate(sdpMid, sdpMLineIndex, sdp);
                    if (peerConnection != null)
                        peerConnection.addIceCandidate(iceCandidate);
                    break;
                // Informazioni di un altro peer connesso all'amministratore
                case "addUser":
                    JSONObject userJson = packet.getJSONObject("value");
                    String newPartnerIp = userJson.getString("partnerIp");
                    int newPartnerPort = userJson.getInt("partnerPort");
                    boolean newIsInitiator = userJson.getBoolean("isInitiator");

                    // Crea un nuovo peer
                    ArrayList<PeerInfo> newPeerInfo = new ArrayList<>();
                    newPeerInfo.add(new PeerInfo(newPartnerIp, newPartnerPort, newIsInitiator));
                    room.addPeers(newPeerInfo);
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Creazione del socket fallita
    @Override
    public void onTCPError() {
        Log.i(TAG, "onTCPError");
        // Riavvia il socket
        callSocket.disconnect();
        callSocket = new CallSocket(executor, this, peerInfo);
    }

    // Se il socket viene chiuso, avvia il rendering
    @Override
    public void onTCPClose() {
        room.callActivity();
    }
}
