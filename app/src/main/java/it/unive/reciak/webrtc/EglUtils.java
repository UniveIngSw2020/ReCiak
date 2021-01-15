package it.unive.reciak.webrtc;

import org.webrtc.EglBase;

/**
 * Singleton oggetto OpenGL per il rendering dei flussi video locali e remoti.
 */
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

    /**
     * Ditrugge l'oggetto EglBase.
     */
    public static void release() {
        if (eglBase != null) {
            eglBase.release();
            eglBase = null;
        }
    }
}