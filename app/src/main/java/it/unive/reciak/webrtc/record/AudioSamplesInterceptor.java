package it.unive.reciak.webrtc.record;

import android.annotation.SuppressLint;

import org.webrtc.audio.JavaAudioDeviceModule.AudioSamples;
import org.webrtc.audio.JavaAudioDeviceModule.SamplesReadyCallback;

import java.util.HashMap;

/**
 * Gestione delle callback di JavaAudioDeviceModule per la registrazione locale.
 * JavaAudioDeviceModule consente l'aggiunta delle callback durante la costruzione dell'oggetto stesso.
 * Questa classe consente di aggiungere le callback successivamente.
 */
@SuppressWarnings("WeakerAccess")
public class AudioSamplesInterceptor implements SamplesReadyCallback {
    @SuppressLint("UseSparseArrays")
    protected final HashMap<Integer, SamplesReadyCallback> callbacks = new HashMap<>();

    @Override
    public void onWebRtcAudioRecordSamplesReady(AudioSamples audioSamples) {
        for (SamplesReadyCallback callback : callbacks.values()) {
            callback.onWebRtcAudioRecordSamplesReady(audioSamples);
        }
    }

    /**
     * Aggiunge una callback a JavaAudioDeviceModule.
     *
     * @param id id callback
     * @param callback callback
     * @throws Exception impossibile aggiungere la callback
     */
    public void attachCallback(Integer id, SamplesReadyCallback callback) throws Exception {
        callbacks.put(id, callback);
    }

    /**
     * Rimuove una callback.
     *
     * @param id id callback
     */
    public void detachCallback(Integer id) {
        callbacks.remove(id);
    }
}