package it.unive.reciak;

import android.content.Context;
import android.os.Handler;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.MediaStreamTrack;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpSender;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import it.unive.reciak.socket.CallSocket;
import it.unive.reciak.socket.TCPChannelClient;
import it.unive.reciak.utils.EglUtils;

public class WebRTC {
    // Lista connessioni
    @NonNull
    private final ArrayList<Peer> peers;

    @Nullable
    private SurfaceTextureHelper helper;
    @Nullable
    private VideoCapturer localCapture;

    @Nullable
    private AudioSource audioSource;
    @Nullable
    private VideoSource videoSource;

    @Nullable
    private PeerConnectionFactory peerConnectionFactory;
    @Nullable
    private PeerConnection.RTCConfiguration rtcConfig;

    // Traccia video remota
    @Nullable
    private VideoTrack remoteVideoTrack;
    // Traccia video locale
    @Nullable
    private VideoTrack videoTrack;
    // Traccia audio locale
    @Nullable
    private AudioTrack audioTrack;

    @NonNull
    private final SurfaceViewRenderer mainView;
    @NonNull
    private final SurfaceViewRenderer rightView;
    @NonNull
    private final ImageView btnRecord;
    @NonNull
    private final Context context;

    public WebRTC(@NonNull SurfaceViewRenderer mainView, @NonNull SurfaceViewRenderer rightView, @NonNull ImageView btnRecord, @NonNull Context context) {
        this.mainView = mainView;
        this.rightView = rightView;
        this.btnRecord = btnRecord;
        this.context = context;
        peers = new ArrayList<>();
    }

    public void start() {
        // Cerca la fotocamera posteriore
        @NonNull
        Camera1Enumerator camera1Enumerator = new Camera1Enumerator(false);
        @NonNull
        String[] cameraNames = camera1Enumerator.getDeviceNames();
        @Nullable
        String cameraName = null;

        for (String camera : cameraNames) {
            if (camera1Enumerator.isBackFacing(camera)) {
                cameraName = camera;
                break;
            }
        }
        // Fotocamera non trovata
        if (cameraName == null)
            onError(new Throwable("Impossibile trovare una fotocamera"));

        // Cattura il flusso video della fotocamera
        localCapture = camera1Enumerator.createCapturer(cameraName, null);

        rightView.init(EglUtils.getEglBaseContext(), null);
        mainView.init(EglUtils.getEglBaseContext(), null);

        PeerConnectionFactory.InitializationOptions initializationOptions = PeerConnectionFactory.InitializationOptions
                .builder(context)
                .createInitializationOptions();
        PeerConnectionFactory.initialize(initializationOptions);

        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        options.networkIgnoreMask = 0;
        options.disableNetworkMonitor = true;

        peerConnectionFactory = PeerConnectionFactory.builder()
                .setVideoDecoderFactory(new DefaultVideoDecoderFactory(EglUtils.getEglBaseContext()))
                .setVideoEncoderFactory(new DefaultVideoEncoderFactory(EglUtils.getEglBaseContext(), false, true))
                .setOptions(options)
                .createPeerConnectionFactory();

        if (peerConnectionFactory == null)
            onError(new Throwable("Impossibile creare una connessione con il peer"));
        if (localCapture == null)
            onError(new Throwable("Impossibile accedere alla fotocamera"));

        rtcConfig = new PeerConnection.RTCConfiguration(new ArrayList<>());
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;

        helper = SurfaceTextureHelper.create("CaptureThread", EglUtils.getEglBaseContext());

        // Crea traccia video e audio dalla fotocamera
        videoSource = peerConnectionFactory.createVideoSource(localCapture.isScreencast());
        localCapture.initialize(helper, context, videoSource.getCapturerObserver());
        localCapture.startCapture(context.getResources().getInteger(R.integer.width), context.getResources().getInteger(R.integer.height), context.getResources().getInteger(R.integer.fps));
        videoTrack = peerConnectionFactory.createVideoTrack("video", videoSource);

        audioSource = peerConnectionFactory.createAudioSource(new MediaConstraints());
        audioTrack = peerConnectionFactory.createAudioTrack("audio", audioSource);

        // TODO Scambio view
        rightView.setOnClickListener((v) -> {
            swap();
        });

        // Inizia a registrare
        // TODO Pulsante termina registrazione
        btnRecord.setOnClickListener((v) -> {
            for (Peer peer : peers) {
                // Termina condivisione video
                peer.stopVideo();
                // Avvia condivisione video
                peer.startVideo();
                // Rinegozia
                peer.createOffer();
            }
        });
    }

    // Crea i peer con le informazioni indicate precedentemente
    public void addPeers(@NonNull ArrayList<PeerInfo> peersInfo) {
        for (@NonNull PeerInfo peerInfo : peersInfo) {
            @NonNull
            Peer peer;

            // Crea un peer con le informazioni di peerInfo
            peer = new Peer(peerInfo);

            // Aggiunge il peer alla lista
            peers.add(peer);
        }
    }

    // Verifica se il peer è stato il primo ad essere stato creato
    private boolean isFirst(@NonNull Peer peer) {
        return peers.indexOf(peer) == 0;
    }

    // Verifica se il dispositivo è l'amministratore
    protected boolean isServer() {
        @NonNull
        Peer first = peers.get(0);

        return isFirst(first) && first.peerInfo.isInitiator();
    }

    // TODO Scambio view
    private void swap() {}

    private void onError(@NonNull Throwable error) {
        error.printStackTrace();
        Toast.makeText(context, error.getMessage(), Toast.LENGTH_SHORT).show();
        // Chiude la connessione con gli altri peer
        dispose();
    }

    // Chiude la connessione cancellando tutti i peer
    public void dispose() {
        for (@NonNull Peer peer : peers)
            peer.dispose();
        peers.clear();

        if (remoteVideoTrack != null) {
            remoteVideoTrack.removeSink(mainView);
            remoteVideoTrack.removeSink(rightView);
            remoteVideoTrack = null;
        }

        if (videoTrack != null) {
            videoTrack.removeSink(mainView);
            videoTrack.removeSink(rightView);
        }

        if (videoTrack != null) {
            videoTrack.dispose();
            videoTrack = null;
        }

        if (audioTrack != null) {
            audioTrack.dispose();
            audioTrack = null;
        }

        if (audioSource != null) {
            audioSource.dispose();
            audioSource = null;
        }

        if (localCapture != null) {
            localCapture.dispose();
            localCapture = null;
        }

        if (videoSource != null) {
            videoSource.dispose();
            videoSource = null;
        }

        if (helper != null) {
            helper.dispose();
            helper = null;
        }

        if (peerConnectionFactory != null) {
            peerConnectionFactory.stopAecDump();
            peerConnectionFactory = null;
        }

        EglUtils.release();

        mainView.release();
        rightView.release();

        PeerConnectionFactory.stopInternalTracingCapture();
        PeerConnectionFactory.shutdownInternalTracer();
    }

    private class Peer implements SdpObserver, PeerConnection.Observer, TCPChannelClient.TCPChannelEvents {
        // Informazioni del peer
        @NonNull
        private final PeerInfo peerInfo;

        // Gestore thread socket
        @NonNull
        private final ExecutorService executor;
        // Socket per la negoziazione della chiamata
        @NonNull
        private final CallSocket callSocket;

        // Gestore della connessione con l'altro peer
        @Nullable
        private PeerConnection peerConnection;

        // Gestori invio flusso video e audio all'altro peer
        @Nullable
        private RtpSender videoSender;
        @Nullable
        private RtpSender audioSender;

        @Nullable
        private final Handler handler;

        public Peer(@NonNull PeerInfo peerInfo) {
            this.peerInfo = peerInfo;

            handler = new Handler(context.getMainLooper());

            executor = Executors.newSingleThreadExecutor();
            callSocket = new CallSocket(executor, this, peerInfo);
        }

        // Avvia il peer
        public void start() {
            if (peerConnectionFactory == null)
                onError(new Throwable("Impossibile connettersi al peer"));

            // Si connette all'altro peer
            peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, this);

            if (peerConnection == null)
                onError(new Throwable("Impossibile connettersi al peer"));

            // Se sono l'amministratore invio il mio flusso video
            if (isServer() && peerInfo.isInitiator())
                startVideo();
        }

        // Avvia condivisione video
        private void startVideo() {
            // Se non sta già convidividendo qualcosa
            if (videoSender == null && audioSender == null && peerConnection != null) {
                ArrayList<String> mediaStreamLabels = new ArrayList<>();
                mediaStreamLabels.add("ARDAMS");

                // Invia il proprio flusso video al peer
                videoSender = peerConnection.addTrack(videoTrack, mediaStreamLabels);
                audioSender = peerConnection.addTrack(audioTrack, mediaStreamLabels);

                if (videoTrack == null)
                    onError(new Throwable("Impossibile collegarsi alla fotocamera"));

                // Nasconde la view più piccola
                // TODO Pulsante termina registrazione
                runOnUiThread(() -> {
                    rightView.setVisibility(View.INVISIBLE);
                    btnRecord.setVisibility(View.INVISIBLE);
                });

                // Visualizza la propria fotocamera nella view centrale
                videoTrack.addSink(mainView);
            }
        }

        // Termina condivisione video
        private void stopVideo() {
            if (videoTrack != null) {
                videoTrack.removeSink(mainView);
                videoTrack.removeSink(rightView);
            }

            if (remoteVideoTrack != null) {
                remoteVideoTrack.removeSink(mainView);
                remoteVideoTrack.removeSink(rightView);
                remoteVideoTrack.dispose();
                remoteVideoTrack = null;
            }

            if (videoSender != null && audioSender != null) {
                rightView.clearImage();
                runOnUiThread(() -> {
                    rightView.setVisibility(View.VISIBLE);
                    btnRecord.setVisibility(View.VISIBLE);
                });

                if (peerConnection != null) {
                    peerConnection.removeTrack(videoSender);
                    videoSender.dispose();
                    videoSender = null;

                    peerConnection.removeTrack(audioSender);
                    audioSender.dispose();
                    audioSender = null;
                }
            }
        }

        public void onError(@NonNull Throwable error) {
            error.printStackTrace();
            Toast.makeText(context, error.getMessage(), Toast.LENGTH_SHORT).show();
            // Chiude la connessione con l'altro peer
            dispose();
        }

        // Chiude la connessione con il peer
        public void dispose() {
            if (peerConnection != null) {
                peerConnection.removeTrack(videoSender);
                peerConnection.removeTrack(audioSender);

                peerConnection.close();
                peerConnection.dispose();
                peerConnection = null;
            }

            callSocket.disconnect();
            executor.shutdown();
        }

        // Inizia la negoziazione
        private void createOffer() {
            if (peerConnection != null)
                peerConnection.createOffer(this, new MediaConstraints());
        }

        // Gestione della SessionDescription remota
        private void setRemoteDescription(String description, boolean isAnswer) {
            if (peerConnection != null) {
                SessionDescription.Type type;
                // Sceglie il tipo in base alla SessionDescription ricevuta
                if (isAnswer)
                    type = SessionDescription.Type.ANSWER;
                else
                    type = SessionDescription.Type.OFFER;

                SessionDescription sessionDescription = new SessionDescription(type, description);
                // Salva la SessionDescription remota
                peerConnection.setRemoteDescription(this, sessionDescription);
                // Risponde al peer
                peerConnection.createAnswer(this, new MediaConstraints());
            }
        }

        // Negoziazione iniziata: viene generata una SessionDescription locale
        @Override
        public void onCreateSuccess(SessionDescription sessionDescription) {
            if (peerConnection != null) {
                // Salva la propria SessionDescription e la invia al peer
                peerConnection.setLocalDescription(this, sessionDescription);
                callSocket.sendSessionDescription(sessionDescription, sessionDescription.type == SessionDescription.Type.ANSWER);
            }
        }

        @Override
        public void onSetSuccess() {}

        @Override
        public void onCreateFailure(@NonNull String s) {}

        @Override
        public void onSetFailure(@NonNull String s) {}

        @Override
        public void onIceCandidate(@NonNull IceCandidate iceCandidate) {
            callSocket.sendIceCandidate(iceCandidate);
        }

        @Override
        public void onDataChannel(@NonNull DataChannel dataChannel) {}

        @Override
        public void onIceConnectionReceivingChange(boolean receivingChange) {}

        @Override
        public void onIceConnectionChange(@NonNull PeerConnection.IceConnectionState state) {}

        @Override
        public void onIceGatheringChange(@NonNull PeerConnection.IceGatheringState state) {}

        @Override
        public void onAddStream(@NonNull MediaStream mediaStream) {}

        @Override
        public void onSignalingChange(@NonNull PeerConnection.SignalingState signalingState) {}

        @Override
        public void onIceCandidatesRemoved(@NonNull IceCandidate[] list) {}

        @Override
        public void onRemoveStream(@NonNull MediaStream mediaStream) {}

        @Override
        public void onRenegotiationNeeded() {}

        // Quando viene riceve una traccia remota
        @Override
        public void onAddTrack(@NonNull RtpReceiver receiver, @NonNull MediaStream[] mediaStreams) {
            @Nullable
            MediaStreamTrack track = receiver.track();

            // Se è una traccia video
            if (track instanceof VideoTrack) {
                // Termina condivisione video
                for (Peer peer : peers)
                    peer.stopVideo();

                // Visualizza il flusso video nella view principale
                remoteVideoTrack = (VideoTrack) track;
                remoteVideoTrack.addSink(mainView);

                // Mostra la propria fotocamera nella view più piccola
                if (videoTrack != null)
                    videoTrack.addSink(rightView);
            }
        }

        // Connesso al socket dell'altro peer
        @Override
        public void onTCPConnected() {
            boolean isInitiator = false;

            // Se sono l'amministratore
            if (peerInfo.isInitiator()) {
                // Avvio il peer
                start();
                // Inizio la negoziazione
                createOffer();
            }

            // TODO Fix ECONNREFUSED (Socket prova a connettersi prima del ServerSocket)
            // Se sono l'amministratore
            if (isServer()) {
                for (Peer peer : peers) {
                    // Avvisa gli altri peer della connessione a un nuovo dispositivo
                    if (peer != this) {
                        int port;
                        if (isInitiator)
                            port = peers.indexOf(this);
                        else
                            port = peers.indexOf(peer);

                        callSocket.sendAddUser(peer.peerInfo.getIp(), context.getResources().getInteger(R.integer.port) + port + 1, isInitiator);
                    } else
                        isInitiator = true;
                }
            }
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
                        boolean isAnswer = sessionDescriptionJson.getBoolean("isAnswer");
                        String sessionDescription = sessionDescriptionJson.getString("sessionDescription");

                        if (!peerInfo.isInitiator() && peerConnection == null)
                            start();

                        setRemoteDescription(sessionDescription, isAnswer);
                        break;
                    // Informazioni sull'indirizzo del peer
                    case "addIceCandidate":
                        JSONObject iceCandidateJson = packet.getJSONObject("value");
                        String sdp = iceCandidateJson.getString("sdp");
                        int sdpMLineIndex = iceCandidateJson.getInt("sdpMLineIndex");
                        String sdpMid = iceCandidateJson.getString("sdpMid");

                        IceCandidate iceCandidate = new IceCandidate(sdpMid, sdpMLineIndex, sdp);
                        if (peerConnection != null)
                            peerConnection.addIceCandidate(iceCandidate);
                        break;
                    // Si connette al peer appena connesso all'amministratore
                    case "addUser":
                        JSONObject userJson = packet.getJSONObject("value");
                        String newPartnerIp = userJson.getString("partnerIp");
                        int newPartnerPort = userJson.getInt("partnerPort");
                        boolean newIsInitiator = userJson.getBoolean("isInitiator");

                        // Crea un nuovo peer
                        ArrayList<PeerInfo> newPeerInfo = new ArrayList<>();
                        newPeerInfo.add(new PeerInfo(newPartnerIp, newPartnerPort, newIsInitiator));
                        addPeers(newPeerInfo);
                        break;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onTCPError(String description) {}

        @Override
        public void onTCPClose() {}

        // Esegue la runnable sul thread principale
        private void runOnUiThread(Runnable r) {
            if (handler != null)
                handler.post(r);
        }
    }
}
