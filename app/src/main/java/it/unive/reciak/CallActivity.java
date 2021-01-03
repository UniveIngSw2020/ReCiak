package it.unive.reciak;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;

import it.unive.reciak.webrtc.PeerInfo;
import it.unive.reciak.webrtc.connection.RTCRoomConnection;

public class CallActivity extends AppCompatActivity {
    @Nullable
    private RTCRoomConnection room;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call);

        // Salva i peer inseriti nell'activity precedente
        Intent intent = getIntent();
        ArrayList<PeerInfo> peersInfo = intent.getParcelableArrayListExtra("peersInfo");

        // Gestore della comunicazione tra i dispositivi via WebRTC
        room = new RTCRoomConnection(this);
        try {
            // Prepara la comunicazione
            room.start();
        } catch (Throwable e) {
            e.printStackTrace();
        }

        // Aggiunge i peer
        room.addPeers(peersInfo);
    }

    @Override
    public void onDestroy() {
        // Distrugge la stanza
        if (room != null) {
            room.dispose();
            room = null;
        }
        // Disconnessione rete Wi-Fi Direct
        DiscoverActivity.disconnect();

        finish();
        super.onDestroy();
    }
}
