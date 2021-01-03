package org.webrtc.audio;

import android.media.AudioTrack;
import android.util.Log;

import org.webrtc.audio.JavaAudioDeviceModule.SamplesReadyCallback;

import java.lang.reflect.Field;

import it.unive.reciak.webrtc.record.AudioTrackInterceptor;

public abstract class WebRtcAudioTrackUtils {
    static private final String TAG = "WebRtcAudioTrackUtils";

    public static void attachOutputCallback(SamplesReadyCallback callback, JavaAudioDeviceModule audioDeviceModule) throws NoSuchFieldException, IllegalAccessException, NullPointerException {
        Field audioOutputField = audioDeviceModule.getClass().getDeclaredField("audioOutput");
        audioOutputField.setAccessible(true);
        WebRtcAudioTrack audioOutput = (WebRtcAudioTrack) audioOutputField.get(audioDeviceModule);
        if (audioOutput != null) {
            Log.w(TAG, "audioOutput found");
            Field audioTrackField = audioOutput.getClass().getDeclaredField("audioTrack");
            audioTrackField.setAccessible(true);
            AudioTrack audioTrack = (AudioTrack) audioTrackField.get(audioOutput);
            if (audioTrack != null) {
                Log.w(TAG, "audioTrack found");
                AudioTrackInterceptor interceptor = new AudioTrackInterceptor(audioTrack, callback);
                audioTrackField.set(audioOutput, interceptor);
                Log.w(TAG, "callback attached");
            }
        }
    }

    public static void detachOutputCallback(JavaAudioDeviceModule audioDeviceModule) {
        try {
            Log.w(TAG, "Searching for audioTrack");
            Field audioOutputField = audioDeviceModule.getClass().getDeclaredField("audioOutput");
            audioOutputField.setAccessible(true);
            WebRtcAudioTrack audioOutput = (WebRtcAudioTrack) audioOutputField.get(audioDeviceModule);
            if (audioOutput != null) {
                Field audioTrackField = audioOutput.getClass().getDeclaredField("audioTrack");
                audioTrackField.setAccessible(true);
                AudioTrack audioTrack = (AudioTrack) audioTrackField.get(audioOutput);
                if (audioTrack instanceof AudioTrackInterceptor) {
                    AudioTrackInterceptor interceptor = (AudioTrackInterceptor) audioTrack;
                    audioTrackField.set(audioOutput, interceptor.originalTrack);
                    Log.w(TAG, "audioTrack found");
                } else {
                    Log.w(TAG, "audioTrack lost");
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to detach callback", e);
        }
    }
}