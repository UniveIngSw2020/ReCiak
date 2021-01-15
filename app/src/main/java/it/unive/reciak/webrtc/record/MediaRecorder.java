package it.unive.reciak.webrtc.record;

import android.util.Log;

import androidx.annotation.Nullable;

import org.webrtc.VideoTrack;

import java.io.File;

import it.unive.reciak.webrtc.EglUtils;

/**
 * Gestore avvio/terminazione registrazione.
 */
public class MediaRecorder {
    private static final String TAG = "MediaRecorder";

    private final VideoTrack videoTrack;
    private final AudioSamplesInterceptor audioInterceptor;
    private VideoFileRenderer videoFileRenderer;
    private boolean isRunning = false;

    public MediaRecorder( @Nullable VideoTrack videoTrack, @Nullable AudioSamplesInterceptor audioInterceptor) {
        this.videoTrack = videoTrack;
        this.audioInterceptor = audioInterceptor;
    }

    /**
     * Avvia registrazione.
     *
     * @param file File registrazione
     * @throws Exception VideoTrack null
     */
    public void startRecording(File file) throws Exception {
        if (isRunning)
            return;
        isRunning = true;
        //noinspection ResultOfMethodCallIgnored
        file.getParentFile().mkdirs();
        if (videoTrack != null) {
            videoFileRenderer = new VideoFileRenderer(file.getAbsolutePath(), EglUtils.getEglBaseContext(), audioInterceptor != null);
            videoTrack.addSink(videoFileRenderer);
            if (audioInterceptor != null)
                audioInterceptor.attachCallback(1, videoFileRenderer);
        } else {
            Log.e(TAG, "Video track is null");
            if (audioInterceptor != null) {
                throw new Exception("Audio-only recording not implemented yet");
            }
        }
    }

    /**
     * Termina registrazione.
     */
    public void stopRecording() {
        isRunning = false;
        if (audioInterceptor != null)
            audioInterceptor.detachCallback(1);
        if (videoTrack != null && videoFileRenderer != null) {
            videoTrack.removeSink(videoFileRenderer);
            videoFileRenderer.release();
            videoFileRenderer = null;
        }
    }
}