package it.unive.reciak;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import org.webrtc.SurfaceViewRenderer;

import java.util.ArrayList;

public class WebRTCActivity extends AppCompatActivity {
    @Nullable
    private WebRTC webRtc;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_webrtc);

        /*
         * View per ciascun flusso video dei tre dispositivi:
         * mainView: fotocamera del proprio dispositivo
         * rightView: fotocamera del primo peer connesso
         * leftView: fotocamera del secondo peer connesso
         *
         * Le view verranno ridimensionate in base al dispositivo che vuole registrare (ViewManager)
         */
        SurfaceViewRenderer leftView = findViewById(R.id.leftView);
        SurfaceViewRenderer rightView = findViewById(R.id.rightView);
        SurfaceViewRenderer mainView = findViewById(R.id.mainView);

        ConstraintLayout layout = findViewById(R.id.constraintLayout);

        // Mette leftView e rightView in primo piano
        leftView.setZOrderMediaOverlay(true);
        rightView.setZOrderMediaOverlay(true);

        // Salva i peer inseriti nell'activity precedente
        Intent intent = getIntent();
        ArrayList<PeerInfo> peersInfo = intent.getParcelableArrayListExtra("peersInfo");

        // Gestore della comunicazione tra i dispositivi via WebRTC
        webRtc = new WebRTC(mainView, leftView, rightView, layout, getApplicationContext());
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
