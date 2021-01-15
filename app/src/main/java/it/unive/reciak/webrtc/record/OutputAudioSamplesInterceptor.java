package it.unive.reciak.webrtc.record;

import org.webrtc.audio.JavaAudioDeviceModule;
import org.webrtc.audio.WebRTCAudioTrackUtils;

/**
 * Sottoclasse di AudioSamplesInterceptor per la registrazione remota.
 * Vengono usate le callback di org.webrtc.audio.WebRTCAudioTrackUtils.
 */
public class OutputAudioSamplesInterceptor extends AudioSamplesInterceptor {
    private final JavaAudioDeviceModule audioDeviceModule;

    public OutputAudioSamplesInterceptor(JavaAudioDeviceModule audioDeviceModule) {
        super();
        this.audioDeviceModule = audioDeviceModule;
    }

    @Override
    public void attachCallback(Integer id, JavaAudioDeviceModule.SamplesReadyCallback callback) throws Exception {
        if (callbacks.isEmpty())
            WebRTCAudioTrackUtils.attachOutputCallback(this, audioDeviceModule);
        super.attachCallback(id, callback);
    }

    @Override
    public void detachCallback(Integer id) {
        super.detachCallback(id);
        if (callbacks.isEmpty())
            WebRTCAudioTrackUtils.detachOutputCallback(audioDeviceModule);
    }
}