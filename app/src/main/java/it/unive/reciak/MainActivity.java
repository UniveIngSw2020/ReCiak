package it.unive.reciak;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Pulsante "AGGIUNGI"
        LinearLayout btnServer = findViewById(R.id.btnAdd);
        // Pulsante "ENTRA"
        LinearLayout btnClient = findViewById(R.id.btnStart);

        btnServer.setOnClickListener(v -> callActivity(true));
        btnClient.setOnClickListener(v -> callActivity(false));
    }

    // Esegue l'activity ConnectActivity specificando, se l'utente vuole creare o entrare in una stanza
    private void callActivity(boolean isServer) {
        Intent intent = new Intent(this, ConnectActivity.class);
        intent.putExtra("isServer", isServer);
        intent.putExtra("peersInfo", new ArrayList<String>());
        startActivity(intent);
        finish();
    }
}
