package it.unive.reciak.webrtc.connection;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.MediaConstraints;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import org.webrtc.audio.JavaAudioDeviceModule;

import java.io.File;
import java.util.ArrayList;

import it.unive.reciak.CallActivity;
import it.unive.reciak.R;
import it.unive.reciak.RenderActivity;
import it.unive.reciak.webrtc.EglUtils;
import it.unive.reciak.webrtc.PeerInfo;
import it.unive.reciak.webrtc.record.AudioSamplesInterceptor;
import it.unive.reciak.webrtc.record.MediaRecorder;
import it.unive.reciak.webrtc.record.OutputAudioSamplesInterceptor;
import it.unive.reciak.webrtc.record.RecordChannel;

/**
 * Gestore connessione a una stanza.
 *
 * @see <a href="https://webrtc.googlesource.com/src/+/master/sdk/android/">Libreria utilizzata</a>
 */
public class RTCRoomConnection {
    private final String TAG = "RTCRoomConnection";

    // Lista connessioni
    @NonNull
    private final ArrayList<RTCPeerConnection> peers;

    // Gestore rendering view
    @Nullable
    private SurfaceTextureHelper helper;
    // Gestore fotocamera del dispositivo
    @Nullable
    private VideoCapturer localCapture;

    // Sorgente audio (microfono)
    @Nullable
    private AudioSource audioSource;
    // Sorgente video (fotocamera)
    @Nullable
    private VideoSource videoSource;

    // Fabbrica connessioni a un peer
    @Nullable
    PeerConnectionFactory peerConnectionFactory;
    // Configurazione fabbrica
    @Nullable
    PeerConnection.RTCConfiguration rtcConfig;

    // Traccia video remota
    @Nullable
    VideoTrack remoteVideoTrack;
    // Traccia video locale
    @Nullable
    VideoTrack videoTrack;
    // Traccia audio locale
    @Nullable
    AudioTrack audioTrack;

    // View principale
    @NonNull
    final SurfaceViewRenderer mainView;
    // View più piccola
    @NonNull
    final SurfaceViewRenderer rightView;
    // Pulsante avvia/termina registrazione
    @NonNull
    final ImageView btnRecord;
    // Pulsante cambia fotocamera
    @NonNull
    final ImageView btnSwitch;
    @NonNull
    private final CallActivity activity;
    @NonNull
    private final Context context;
    @Nullable
    private final Handler handler;

    // Gestore audio locale
    @Nullable
    private JavaAudioDeviceModule audioDeviceModule;
    // Gestore avvio/terminazione registrazione
    @Nullable
    private MediaRecorder mediaRecorder;
    // Callback registrazione audio locale
    @Nullable
    private final AudioSamplesInterceptor inputSamplesInterceptor;
    // Callback registrazione audio remoto
    @Nullable
    private OutputAudioSamplesInterceptor outputSamplesInterceptor;

    // File video registrati
    @Nullable
    private final ArrayList<String> videos;

    // Sta condividendo la fotocamera
    private boolean sharing;
    // L'utente ha scambiato le view
    private boolean swap;
    // Stanza distrutta
    private boolean closed;

    public RTCRoomConnection(@NonNull CallActivity activity) {
        this.activity = activity;
        this.context = activity.getApplicationContext();

        mainView = activity.findViewById(R.id.mainView);
        rightView = activity.findViewById(R.id.rightView);
        btnRecord = activity.findViewById(R.id.btnRecord);
        btnSwitch = activity.findViewById(R.id.btnSwitch);
        handler = new Handler(context.getMainLooper());
        inputSamplesInterceptor = new AudioSamplesInterceptor();
        peers = new ArrayList<>();
        videos = new ArrayList<>();

        sharing = false;
        swap = false;
        closed = false;
    }

    /**
     * Prepara il dispositivo a connettersi alla stanza, cercando la fotocamera e preparando la
     * fabbrica delle connessioni.
     */
    public void start() {
        Log.i(TAG, "start");
        // Mette rightView in primo piano
        rightView.setZOrderMediaOverlay(true);

        // Elimina le clip della sessione precedente
        RenderActivity.deleteFiles(context);

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
            onError(new Throwable(context.getString(R.string.camera_error)));

        // Cattura il flusso video della fotocamera
        localCapture = camera1Enumerator.createCapturer(cameraName, null);

        // Prepara le view
        runOnUiThread(() -> {
            rightView.init(EglUtils.getEglBaseContext(), null);
            mainView.init(EglUtils.getEglBaseContext(), null);
        });

        // Prepara la fabbrica di connessioni ai peer
        PeerConnectionFactory.InitializationOptions initializationOptions = PeerConnectionFactory.InitializationOptions
                .builder(context)
                .createInitializationOptions();
        PeerConnectionFactory.initialize(initializationOptions);

        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        options.networkIgnoreMask = 0;
        options.disableNetworkMonitor = true;

        // Gestore audio del proprio dispositivo
        audioDeviceModule = JavaAudioDeviceModule.builder(context)
                .setUseHardwareAcousticEchoCanceler(true)
                .setUseHardwareNoiseSuppressor(true)
                .setSamplesReadyCallback(inputSamplesInterceptor)
                .createAudioDeviceModule();

        // Crea la fabbrica
        peerConnectionFactory = PeerConnectionFactory.builder()
                .setVideoDecoderFactory(new DefaultVideoDecoderFactory(EglUtils.getEglBaseContext()))
                .setVideoEncoderFactory(new DefaultVideoEncoderFactory(EglUtils.getEglBaseContext(), true, true))
                .setOptions(options)
                .setAudioDeviceModule(audioDeviceModule)
                .createPeerConnectionFactory();

        if (peerConnectionFactory == null)
            onError(new Throwable(context.getString(R.string.connection_error)));
        if (localCapture == null)
            onError(new Throwable(context.getString(R.string.camera_error)));

        rtcConfig = new PeerConnection.RTCConfiguration(new ArrayList<>());
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;

        // Avvia rendering delle view
        helper = SurfaceTextureHelper.create("CaptureThread", EglUtils.getEglBaseContext());

        // Crea traccia video
        videoSource = peerConnectionFactory.createVideoSource(localCapture.isScreencast());
        videoSource.adaptOutputFormat(context.getResources().getInteger(R.integer.width), context.getResources().getInteger(R.integer.height), context.getResources().getInteger(R.integer.fps));
        localCapture.initialize(helper, context, videoSource.getCapturerObserver());
        localCapture.startCapture(context.getResources().getInteger(R.integer.width), context.getResources().getInteger(R.integer.height), context.getResources().getInteger(R.integer.fps));
        videoTrack = peerConnectionFactory.createVideoTrack("video", videoSource);

        // Crea traccia audio
        audioSource = peerConnectionFactory.createAudioSource(new MediaConstraints());
        audioTrack = peerConnectionFactory.createAudioTrack("audio", audioSource);

        // Listener scambio delle view
        rightView.setOnClickListener((v) -> {
            if (videoTrack != null && remoteVideoTrack != null) {
                if (!swap) {
                    videoTrack.removeSink(rightView);
                    remoteVideoTrack.removeSink(mainView);
                    videoTrack.addSink(mainView);
                    remoteVideoTrack.addSink(rightView);
                } else {
                    videoTrack.removeSink(mainView);
                    remoteVideoTrack.removeSink(rightView);
                    videoTrack.addSink(rightView);
                    remoteVideoTrack.addSink(mainView);
                }

                swap = !swap;
            }
        });

        // Pulsante inizia/termina registrazione
        btnRecord.setOnClickListener(v -> {
            if (!sharing) {
                Log.i(TAG, "btnRecord: start");
                // Termina condivisione video
                stopVideo();
                for (RTCPeerConnection peer : peers) {
                    // Avvia condivisione video
                    peer.startVideo();
                    // Rinegozia
                    peer.createOffer();
                }
                setRecordingButton(true);
            } else {
                Log.i(TAG, "btnRecord: stop");
                // Avvia il rendering
                callActivity();
            }
        });

        // Cambia fotocamera
        btnSwitch.setOnClickListener(v -> switchCamera());
    }

    /**
     * Aggiunge e crea una connessione per ciascun peer.
     *
     * @param peersInfo indirizzi dei peer
     */
    public synchronized void addPeers(@NonNull ArrayList<PeerInfo> peersInfo) {
        for (@NonNull PeerInfo peerInfo : peersInfo) {
            @NonNull
            RTCPeerConnection peerConnection;

            // Crea un peer con le informazioni di peerInfo
            peerConnection = new RTCPeerConnection(this, peerInfo, context);

            // Aggiunge il peer alla lista
            peers.add(peerConnection);
        }
    }

    /**
     * Dato un peer ritorna l'indice della lista.
     *
     * @param peer peer da cercare nella lista
     * @return indice del peer della lista
     */
    public synchronized int peerIndex(@NonNull RTCPeerConnection peer) {
        return peers.indexOf(peer);
    }

    /**
     * Verifica se il peer è stato il primo ad essere stato creato.
     *
     * @param peer peer da cercare nella lista
     * @return true se è il primo peer della lista
     */
    public synchronized boolean isFirst(@NonNull RTCPeerConnection peer) {
        return peerIndex(peer) == 0;
    }

    /**
     * Verifica se il dispositivo è l'amministratore.
     *
     * @return true se il dispositivo amministra la stanza
     */
    protected synchronized boolean isServer() {
        @NonNull
        RTCPeerConnection first = peers.get(0);

        return isFirst(first) && first.isInitiator();
    }

    /**
     * Termina la condivisione video verso tutti i peer.
     */
    public synchronized void stopVideo() {
        Log.i(TAG, "stopVideo");
        for (RTCPeerConnection peer : peers)
            peer.stopVideo();
    }

    /**
     * Cambia la descrizione e l'icona del pulsante avvia/termina registrazione.
     *
     * @param isSharing true se il dispositivo sta condividendo la propria fotocamera
     */
    public synchronized void setRecordingButton(boolean isSharing) {
        String description;
        int draw;

        if (isSharing) {
            description = context.getString(R.string.recording_stop);
            draw = R.drawable.ic_circle_recording;
        }
        else {
            description = context.getString(R.string.recording_start);
            draw = R.drawable.ic_circle;
        }

        // Cambia l'icona e la descrizione del pulsante
        runOnUiThread(() -> {
            btnRecord.setContentDescription(description);
            btnRecord.setImageDrawable(ContextCompat.getDrawable(context, draw));
        });
        this.sharing = isSharing;
    }

    /**
     * Gestione aggiunta utenti.
     * Necessaria per connettere i peer fra loro.
     *
     * @param currentPeer peer corrente
     */
    public synchronized void addUser(RTCPeerConnection currentPeer) {
        // Se sono l'amministratore
        if (isServer()) {
            boolean isInitiator = false;
            // Se sono connesso a più di un peer
            for (RTCPeerConnection peer : peers) {
                Log.i(TAG, "addUser");

                // Avvisa gli altri peer della connessione a un nuovo dispositivo
                if (peer != currentPeer) {
                    int port;
                    if (isInitiator)
                        port = peers.indexOf(currentPeer);
                    else
                        port = peers.indexOf(peer);

                    // Invia l'indirizzo al peer
                    currentPeer.addUser(peer.getIp(), context.getResources().getInteger(R.integer.call_port) + port + 1, isInitiator);
                } else
                    isInitiator = true;
            }
        }
    }

    /**
     * Avvia la registrazione.
     *
     * @param audioChannel registrazione locale o remota
     */
    public synchronized void startRecording(@Nullable RecordChannel audioChannel) {
        Log.i(TAG, "startRecording");
        VideoTrack track = null;
        if (videos != null) {
            try {
                // Aggiunge la nuova registrazione nella lista dei video
                final String videoName = "VID_" + videos.size() + ".mp4";
                videos.add(videoName);
                AudioSamplesInterceptor interceptor = null;
                if (audioChannel == RecordChannel.INPUT) {
                    // Registrazione locale
                    track = videoTrack;
                    interceptor = inputSamplesInterceptor;
                } else if (audioChannel == RecordChannel.OUTPUT) {
                    // Registrazione remota
                    if (outputSamplesInterceptor == null)
                        outputSamplesInterceptor = new OutputAudioSamplesInterceptor(audioDeviceModule);
                    track = remoteVideoTrack;
                    interceptor = outputSamplesInterceptor;
                }
                // Avvia la registrazione
                mediaRecorder = new MediaRecorder(track, interceptor);
                mediaRecorder.startRecording(new File(context.getFilesDir().getAbsolutePath(), videoName));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Termina la registrazione.
     */
    public synchronized void stopRecording() {
        Log.i(TAG, "stopRecording");
        if (mediaRecorder != null)
            mediaRecorder.stopRecording();
    }

    /**
     * Switch fotocamera anteriore/posteriore.
     */
    private void switchCamera() {
        if (localCapture != null && localCapture instanceof CameraVideoCapturer) {
            Log.i(TAG, "switchCamera");
            CameraVideoCapturer cameraVideoCapturer = (CameraVideoCapturer) localCapture;
            cameraVideoCapturer.switchCamera(null);
        } else {
            Log.e(TAG, "switchCamera: failed to switch camera");
        }
    }

    /**
     * Gestione errori.
     *
     * @param error eccezione
     */
    private void onError(@NonNull Throwable error) {
        error.printStackTrace();
        Toast.makeText(context, error.getMessage(), Toast.LENGTH_SHORT).show();
        // Chiude la connessione con gli altri peer
        dispose();
    }

    /**
     * Esegue una runnable nel thread principale.
     *
     * @param r runnable da eseguire
     */
    public void runOnUiThread(Runnable r) {
        if (handler != null)
            handler.post(r);
    }

    /**
     * Avvia l'activity RenderActivity e avvia il rendering.
     */
    public synchronized void callActivity() {
        Log.i(TAG, "callActivity");
        if (!closed) {
            dispose();
            closed = true;
            // Esegue l'activity passando la lista dei video
            Intent intent = new Intent(context, RenderActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra("videos", videos);
            context.startActivity(intent);
            activity.finish();
        }
    }

    /**
     * Chiude la stanza disconnettendosi da tutti i peer.
     */
    public synchronized void dispose() {
        if (!closed) {
            Log.i(TAG, "dispose");
            for (@NonNull RTCPeerConnection peer : peers)
                peer.dispose();

            if (audioSource != null) {
                Log.i(TAG, "AudioSource: dispose");
                audioSource.dispose();
                audioSource = null;
            }

            if (localCapture != null) {
                Log.i(TAG, "LocalCapture: dispose");
                localCapture.dispose();
                localCapture = null;
            }

            if (videoSource != null) {
                Log.i(TAG, "VideoSource: dispose");
                videoSource.dispose();
                videoSource = null;
            }

            if (helper != null) {
                Log.i(TAG, "Helper: dispose");
                helper.dispose();
                helper = null;
            }

            Log.i(TAG, "EglBase: dispose");
            mainView.release();
            rightView.release();
            EglUtils.release();

            if (peerConnectionFactory != null) {
                Log.i(TAG, "PeerConnectionFactory: dispose");
                peerConnectionFactory.dispose();
                peerConnectionFactory = null;
            }
            peers.clear();

            PeerConnectionFactory.stopInternalTracingCapture();
            PeerConnectionFactory.shutdownInternalTracer();
        }
    }
}