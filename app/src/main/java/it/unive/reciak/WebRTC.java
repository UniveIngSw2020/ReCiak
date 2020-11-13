package it.unive.reciak;


import android.content.Context;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.MediaStreamTrack;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpSender;
import org.webrtc.RtpTransceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class WebRTC {
    @NonNull
    private final ArrayList<Peer> peers;

    @Nullable
    private EglBase eglBase;
    @Nullable
    private SurfaceTextureHelper helper;
    @Nullable
    private VideoCapturer localCapture;

    @Nullable
    private ViewsManager viewsManager;

    @Nullable
    private AudioSource audioSource;
    @Nullable
    private VideoSource videoSource;

    @Nullable
    private PeerConnectionFactory peerConnectionFactory;
    @Nullable
    private PeerConnection.RTCConfiguration rtcConfig;

    @Nullable
    private VideoTrack remoteVideoTrack;
    @Nullable
    private VideoTrack videoTrack;
    @Nullable
    private AudioTrack audioTrack;

    @NonNull
    private final SurfaceViewRenderer mainView;
    @NonNull
    private final SurfaceViewRenderer leftView;
    @NonNull
    private final SurfaceViewRenderer rightView;
    @NonNull
    private final ConstraintLayout layout;
    @NonNull
    private final Context context;

    public WebRTC(@NonNull SurfaceViewRenderer mainView, @NonNull SurfaceViewRenderer leftView, @NonNull SurfaceViewRenderer rightView, @NonNull ConstraintLayout layout, @NonNull Context context) {
        this.mainView = mainView;
        this.leftView = leftView;
        this.rightView = rightView;
        this.layout = layout;
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

        // Gestore posizioni delle view
        viewsManager = new ViewsManager(mainView, leftView, rightView, layout);

        // Cattura il flusso video della fotocamra
        localCapture = camera1Enumerator.createCapturer(cameraName, null);
        eglBase = EglBase.create();

        leftView.init(eglBase.getEglBaseContext(), null);
        rightView.init(eglBase.getEglBaseContext(), null);
        mainView.init(eglBase.getEglBaseContext(), null);

        PeerConnectionFactory.InitializationOptions initializationOptions = PeerConnectionFactory.InitializationOptions
                .builder(context)
                .createInitializationOptions();
        PeerConnectionFactory.initialize(initializationOptions);

        peerConnectionFactory = PeerConnectionFactory.builder()
                .setVideoDecoderFactory(new DefaultVideoDecoderFactory(eglBase.getEglBaseContext()))
                .setVideoEncoderFactory(new DefaultVideoEncoderFactory(eglBase.getEglBaseContext(), true, true))
                .setOptions(new PeerConnectionFactory.Options())
                .createPeerConnectionFactory();

        if (peerConnectionFactory == null)
            onError(new Throwable("Impossibile creare una connessione con il peer"));
        if (localCapture == null)
            onError(new Throwable("Impossibile accedere alla fotocamera"));

        rtcConfig = new PeerConnection.RTCConfiguration(new ArrayList<>());
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;

        helper = SurfaceTextureHelper.create("CaptureThread", eglBase.getEglBaseContext());
        videoSource = peerConnectionFactory.createVideoSource(localCapture.isScreencast());
        localCapture.initialize(helper, context, videoSource.getCapturerObserver());
        localCapture.startCapture(context.getResources().getInteger(R.integer.width), context.getResources().getInteger(R.integer.height), context.getResources().getInteger(R.integer.fps));
        videoTrack = peerConnectionFactory.createVideoTrack("video", videoSource);

        audioSource = peerConnectionFactory.createAudioSource(new MediaConstraints());
        audioTrack = peerConnectionFactory.createAudioTrack("audio", audioSource);
    }

    // Crea i peer con le informazioni indicate precedentemente
    public void addPeers(ArrayList<PeerInfo> peersInfo) {
        for (PeerInfo peerInfo : peersInfo) {
            boolean isInitiator = peerInfo.isInitiator();
            SurfaceViewRenderer peerView;
            Peer peer;

            // Assegna un view al peer
            if (peers.size() == 0)
                peerView = rightView;
            else
                peerView = leftView;

            // Crea un peer con le informazioni di peerInfo
            peer = new Peer(peerInfo.getIp(), peerInfo.getPort(), peerInfo.getInitiatorPort(), peerView, isInitiator);
            // Avvia il gestore della comunicazione tra due peer
            peer.startSignaling();
            // Se devo iniziare la comunicazione
            if (isInitiator) {
                // Avvia il peer
                peer.start();
                peer.createOffer();
            }

            // Aggiunge il peer alla lista
            peers.add(peer);
        }
    }

    private void onError(@NonNull Throwable error) {
        error.printStackTrace();
        Toast.makeText(context, error.getMessage(), Toast.LENGTH_SHORT).show();
        dispose();
    }

    // Chiude la connessione cancellando tutti i peer
    public void dispose() {
        for (Peer peer : peers)
            peer.dispose();
        peers.clear();

        if (remoteVideoTrack != null) {
            remoteVideoTrack.removeSink(mainView);
            remoteVideoTrack.removeSink(leftView);
            remoteVideoTrack.removeSink(rightView);
            remoteVideoTrack = null;
        }

        if (videoTrack != null) {
            videoTrack.removeSink(mainView);
            videoTrack.removeSink(leftView);
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

        mainView.release();
        leftView.release();
        rightView.release();

        if (eglBase != null) {
            eglBase.release();
            eglBase = null;
        }

        PeerConnectionFactory.stopInternalTracingCapture();
        PeerConnectionFactory.shutdownInternalTracer();
    }

    private class Peer implements SdpObserver, PeerConnection.Observer {
        @NonNull
        private final String partnerIp;
        private final int partnerPort;
        private final int port;
        @NonNull
        private final SurfaceViewRenderer peerView;
        private final boolean isInitiator;

        @Nullable
        private CompositeDisposable disposables;
        @Nullable
        private PeerConnection peerConnection;

        @Nullable
        private RtpSender videoSender;
        @Nullable
        private RtpSender audioSender;

        public Peer(@NonNull String partnerIp, int partnerPort, int port, @NonNull SurfaceViewRenderer peerView, boolean isInitiator) {
            this.partnerIp = partnerIp;
            this.partnerPort = partnerPort;
            this.port = port;
            this.peerView = peerView;
            this.isInitiator = isInitiator;
            disposables = new CompositeDisposable();
        }

        // Avvia il peer
        public void start() {
            if (peerConnectionFactory == null)
                onError(new Throwable("Impossibile connettersi al peer"));

            peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, this);

            if (peerConnection == null)
                onError(new Throwable("Impossibile connettersi al peer"));

            ArrayList<String> mediaStreamLabels = new ArrayList<>();
            mediaStreamLabels.add("ARDAMS");
            videoSender = peerConnection.addTrack(videoTrack, mediaStreamLabels);
            audioSender = peerConnection.addTrack(audioTrack, mediaStreamLabels);

            if (audioTrack != null)
                audioTrack.setEnabled(false);

            for (RtpTransceiver transceiver : peerConnection.getTransceivers()) {
                MediaStreamTrack track = transceiver.getReceiver().track();
                if (track instanceof VideoTrack) {
                    remoteVideoTrack = (VideoTrack) track;
                    break;
                }
            }

            if (remoteVideoTrack == null) {
                onError(new Throwable("Impossibile connettersi al client"));
                return;
            }

            remoteVideoTrack.addSink(peerView);

            if (isFirst()) {
                if (videoTrack == null) {
                    onError(new Throwable("Impossibile collegarsi alla fotocamera"));
                    return;
                }
                videoTrack.addSink(mainView);

                mainView.setOnClickListener((v) -> {
                    mainView.setClickable(false);
                    if (viewsManager != null)
                        viewsManager.swapViews(mainView);

                    for (Peer peer : peers)
                        peer.sendChangeUser();

                    audioTrack.setEnabled(true);


                });

                mainView.setClickable(false);
            }

            if (!isInitiator && isFirst()) {
                if (viewsManager != null)
                    viewsManager.swapViews(peerView);
                mainView.setClickable(true);
            }
        }

        private void onError(@NonNull Throwable error) {
            error.printStackTrace();
            Toast.makeText(context, error.getMessage(), Toast.LENGTH_SHORT).show();
            dispose();
        }

        // Chiude la connessione con il peer
        public void dispose() {
            if (disposables != null) {
                disposables.dispose();
                disposables = null;
            }

            if (peerConnection != null) {
                peerConnection.removeTrack(videoSender);
                peerConnection.removeTrack(audioSender);

                peerConnection.close();
                peerConnection.dispose();
                peerConnection = null;
            }
        }

        // Verifica se il peer è stato il primo ad essere stato creato
        private boolean isFirst() {
            return port == context.getResources().getInteger(R.integer.port);
        }

        private void createOffer() {
            if (peerConnection != null)
                peerConnection.createOffer(this, new MediaConstraints());
        }

        private void setRemoteDescription(String description) {
            if (peerConnection != null) {
                SessionDescription.Type type;
                if (isInitiator)
                    type = SessionDescription.Type.ANSWER;
                else
                    type = SessionDescription.Type.OFFER;

                SessionDescription sessionDescription = new SessionDescription(type, description);
                peerConnection.setRemoteDescription(this, sessionDescription);
                peerConnection.createAnswer(this, new MediaConstraints());
            }
        }

        @Override
        public void onCreateSuccess(SessionDescription sessionDescription) {
            if (peerConnection != null) {
                peerConnection.setLocalDescription(this, sessionDescription);
                JSONObject packet = new JSONObject();

                try {
                    packet.put("action", "setSessionDescription");
                    packet.put("value", sessionDescription.description);

                    sendPacket(packet);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
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

        @Override
        public void onIceCandidate(@NonNull IceCandidate iceCandidate) {
            JSONObject iceCandidateJson = new JSONObject();
            JSONObject packet = new JSONObject();

            try {
                iceCandidateJson.put("sdp", iceCandidate.sdp);
                iceCandidateJson.put("sdpMLineIndex", iceCandidate.sdpMLineIndex);
                iceCandidateJson.put("sdpMid", iceCandidate.sdpMid);

                packet.put("action", "addIceCandidate");
                packet.put("value", iceCandidateJson);

                sendPacket(packet);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onDataChannel(@NonNull DataChannel dataChannel) {
        }

        @Override
        public void onIceConnectionReceivingChange(boolean receivingChange) {
        }

        @Override
        public void onIceConnectionChange(@NonNull PeerConnection.IceConnectionState state) {
            if (state == PeerConnection.IceConnectionState.DISCONNECTED) {
                dispose();
            }
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

        @Override
        public void onAddTrack(@NonNull RtpReceiver receiver, @NonNull MediaStream[] mediaStreams) {
        }

        // Gestore della comunicazione tra due peer. Cattura tutti i pacchetti in entrata inviati dal peer
        public void startSignaling() {
            Observable<String> observable = Observable.create(emitter -> {
                // Crea un ServerSocket in modo che il peer possa connettersi per inviare pacchetti
                try (ServerSocket serverSocket = new ServerSocket()) {
                    serverSocket.setSoTimeout(3 * 1000);
                    serverSocket.setReuseAddress(true);
                    serverSocket.bind(new InetSocketAddress(port));

                    // Finché il peer non viene eliminato
                    while (!emitter.isDisposed()) {
                        // Accetta le possibili connessioni da parte del peer e raccoglie il pacchetto in entrata
                        try (Socket socket = serverSocket.accept(); InputStream input = socket.getInputStream()) {
                            // Salva il pacchetto
                            String text = new BufferedReader(new InputStreamReader(input)).readLine();
                            // Manda il pacchetto a onJsonReceived() per tradurlo
                            emitter.onNext(text);
                        } catch (Exception ignored) {
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            Disposable disposable = observable
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(this::onJsonReceived, this::onError);

            if (disposables != null)
                disposables.add(disposable);
        }

        // Ho ricevuto un pacchetto e leggo in contenuto
        private void onJsonReceived(@NonNull String json) {
            try {
                JSONObject packet = new JSONObject(json);
                String action = packet.getString("action");
                // Risponde in base al tipo di pacchetto ricevuto
                switch (action) {
                    case "setSessionDescription":
                        if (!isInitiator)
                            start();
                        String value = packet.getString("value");
                        setRemoteDescription(value);

                        if (!isFirst() && peers.get(0).isInitiator) {
                            for (Peer peer : peers) {
                                if (peer != this) {
                                    peer.sendAddUser(partnerIp, partnerPort + peers.size() - 1, true);
                                    sendAddUser(peer.partnerIp, peer.partnerPort + peers.size() - 1, false);
                                }
                            }
                        }
                        break;
                    case "addIceCandidate":
                        JSONObject iceCandidateJson = packet.getJSONObject("value");
                        String sdp = iceCandidateJson.getString("sdp");
                        int sdpMLineIndex = iceCandidateJson.getInt("sdpMLineIndex");
                        String sdpMid = iceCandidateJson.getString("sdpMid");

                        IceCandidate iceCandidate = new IceCandidate(sdpMid, sdpMLineIndex, sdp);
                        if (peerConnection != null)
                            peerConnection.addIceCandidate(iceCandidate);
                        break;
                    // Cambio di regista
                    case "changeUser":
                        // Disabilito l'audio del mio microfono
                        if (audioTrack != null)
                            audioTrack.setEnabled(false);
                        // Metto la fotocamera del nuovo registra in primo piano
                        if (viewsManager != null)
                            viewsManager.swapViews(peerView);
                        // Abilito l'invio della richiesta per ritornare regista
                        mainView.setClickable(true);
                        break;
                    // Mi connetto all'ultimo peer che si è connesso
                    case "addUser":
                        JSONObject userJson = packet.getJSONObject("value");
                        String newPartnerIp = userJson.getString("partnerIp");
                        int newPartnerPort = userJson.getInt("partnerPort");
                        boolean newIsInitiator = userJson.getBoolean("isInitiator");

                        // Crea un nuovo peer
                        ArrayList<PeerInfo> peerInfo = new ArrayList<>();
                        peerInfo.add(new PeerInfo(newPartnerIp, newPartnerPort, newIsInitiator, port + peers.size()));
                        addPeers(peerInfo);
                        break;
                    case "closeRoom":
                        // TODO: Termina registrazione e chiude la stanza
                        break;
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        // Invio richiesta di cambio di "regista"
        private void sendChangeUser() {
            JSONObject packet = new JSONObject();

            try {
                packet.put("action", "changeUser");
                sendPacket(packet);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        // Invia ai peer le informazioni per connettersi fra loro. Inizialmente i peer sono connessi soltanto al creatore della stanza
        private void sendAddUser(@NonNull String partnerIp, int partnerPort, boolean isInitiator) {
            JSONObject packet = new JSONObject();
            JSONObject userJson = new JSONObject();

            try {
                userJson.put("partnerIp", partnerIp);
                userJson.put("partnerPort", partnerPort);
                userJson.put("isInitiator", isInitiator);

                packet.put("action", "addUser");
                packet.put("value", userJson);
                sendPacket(packet);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        // TODO: termina la registrazione e chiude la stanza
        private void sendCloseRoom() {
            JSONObject packet = new JSONObject();

            try {
                packet.put("action", "closeRoom");
                sendPacket(packet);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        // Invio di un pacchetto al peer
        private void sendPacket(@NonNull JSONObject packet) {
            Completable action = Completable.fromAction(() -> {
                try (Socket socket = new Socket()) {
                    // Timeout del socket
                    socket.setSoTimeout(10 * 1000);

                    // Si connette al peer
                    socket.connect(new InetSocketAddress(partnerIp, partnerPort), 10 * 1000);
                    // Invia il pacchetto
                    socket.getOutputStream().write(packet.toString().getBytes(StandardCharsets.UTF_8));
                }
            });

            action = action.retry(error -> error instanceof ConnectException);
            action = action.subscribeOn(Schedulers.newThread());
            action = action.observeOn(AndroidSchedulers.mainThread());

            Disposable disposable = action.subscribe(() -> {
            }, this::onError);
            if (disposables != null)
                disposables.add(disposable);
        }
    }
}
