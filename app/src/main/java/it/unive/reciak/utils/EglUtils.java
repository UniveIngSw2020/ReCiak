package it.unive.reciak.utils;

import org.webrtc.EglBase;

public class EglUtils {
    private static EglBase eglBase;

    public static synchronized EglBase getEglBase() {
        if (eglBase == null)
            eglBase = EglBase.create();

        return eglBase;
    }

    public static EglBase.Context getEglBaseContext() {
        EglBase eglBase = getEglBase();

        return eglBase == null ? null : eglBase.getEglBaseContext();
    }

    public static void release() {
        if (eglBase != null) {
            eglBase.release();
            eglBase = null;
        }
    }
}