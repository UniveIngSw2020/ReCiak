package it.unive.reciak;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    // Permessi
    @NonNull
    private final String[] PERMISSIONS = {Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.ACCESS_FINE_LOCATION};

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Pulsante "CREA"
        ConstraintLayout btnServer = findViewById(R.id.btnPlay);
        // Pulsante "ENTRA"
        ConstraintLayout btnClient = findViewById(R.id.btnShare);

        btnServer.setOnClickListener(v -> callActivity(true));
        btnClient.setOnClickListener(v -> callActivity(false));

        // Chiede i permessi
        ActivityCompat.requestPermissions(this, PERMISSIONS, 1);
    }

    // Esegue l'activity ConnectActivity specificando, se l'utente vuole creare o entrare in una stanza
    private void callActivity(boolean isServer) {
        Intent intent = new Intent(this, DiscoverActivity.class);
        intent.putExtra("isServer", isServer);
        intent.putExtra("peersInfo", new ArrayList<String>());
        startActivity(intent);
    }

    // Controlla se l'utente ha concesso tutti i permessi
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == 1) {
            boolean granted = true;
            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    // Un permesso è stato rifiutato
                    granted = false;
                    break;
                }
            }

            // Se un permesso non è stato concesso
            if (!granted) {
                // Avvisa l'utente ed esce dall'app
                Toast.makeText(getApplicationContext(), R.string.permissions, Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }
}
