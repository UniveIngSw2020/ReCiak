package it.unive.reciak;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    // TODO Richiesta permessi
    @NonNull
    private final String[] PERMISSIONS = {Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.ACCESS_FINE_LOCATION};

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Pulsante "CREA"
        ConstraintLayout btnServer = findViewById(R.id.btnCreate);
        // Pulsante "ENTRA"
        ConstraintLayout btnClient = findViewById(R.id.btnStart);

        btnServer.setOnClickListener(v -> callActivity(true));
        btnClient.setOnClickListener(v -> callActivity(false));
    }

    // Esegue l'activity ConnectActivity specificando, se l'utente vuole creare o entrare in una stanza
    private void callActivity(boolean isServer) {
        Intent intent = new Intent(this, DiscoverActivity.class);
        intent.putExtra("isServer", isServer);
        intent.putExtra("peersInfo", new ArrayList<String>());
        startActivity(intent);
        finish();
    }

    // Controlla i permessi
    private boolean hasPermissions(@NonNull Context context, @NonNull String[] permissions) {
        for (String permission : permissions)
            if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED)
                return true;

        return false;
    }
}
