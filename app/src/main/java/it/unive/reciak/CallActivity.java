package it.unive.reciak;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.webrtc.SurfaceViewRenderer;

import java.util.ArrayList;

public class CallActivity extends AppCompatActivity {
    @Nullable
    private WebRTC webRtc;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call);

        // View grande
        SurfaceViewRenderer mainView = findViewById(R.id.mainView);
        // View piccola
        SurfaceViewRenderer rightView = findViewById(R.id.rightView);

        // Pulsante registrazione
        ImageView btnRecord = findViewById(R.id.imageView);

        // Mette rightView in primo piano
        rightView.setZOrderMediaOverlay(true);

        // Salva i peer inseriti nell'activity precedente
        Intent intent = getIntent();
        ArrayList<PeerInfo> peersInfo = intent.getParcelableArrayListExtra("peersInfo");

        // Gestore della comunicazione tra i dispositivi via WebRTC
        webRtc = new WebRTC(mainView, rightView, btnRecord, getApplicationContext());
        try {
            // Prepara la comunicazione
            webRtc.start();
        } catch (Throwable e) {
            e.printStackTrace();
        }

        // Aggiunge i peer
        webRtc.addPeers(peersInfo);
    }

    @Override
    public void onDestroy() {
        // Distrugge la stanza
        if (webRtc != null) {
            webRtc.dispose();
            webRtc = null;
        }
        super.onDestroy();
    }
}
