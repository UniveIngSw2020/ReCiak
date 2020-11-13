package it.unive.reciak;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;

public class ConnectActivity extends AppCompatActivity {
    private final String[] PERMISSIONS = {Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE};
    // L'utente vuole creare un stanza
    private boolean isServer;
    final ArrayList<PeerInfo> peersInfo = new ArrayList<>();
    private LinearLayout btnAdd;
    private ImageView imageAdd;
    private TextView textAdd;
    private EditText editIp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connect);

        TextView room = findViewById(R.id.room);
        LinearLayout btnStart = findViewById(R.id.btnStart);
        TextView textStart = findViewById(R.id.start_text);
        TextView textIp = findViewById(R.id.textIp);

        btnAdd = findViewById(R.id.btnAdd);
        imageAdd = findViewById(R.id.add_icon);
        textAdd = findViewById(R.id.add_text);
        editIp = findViewById(R.id.editIp);

        // Salva le variabili passate dall'activity precedente
        Intent peer = getIntent();
        isServer = peer.getBooleanExtra("isServer", false);
        // Salva le informazioni dei peer se viene richiamata questa activity
        peersInfo.addAll(peer.getParcelableArrayListExtra("peersInfo"));

        // Mostra l'indirizzo all'utente
        textIp.setText(String.format(getString(R.string.your_ip), Util.getIp(), getResources().getInteger(R.integer.port) + peersInfo.size()));
        // Modifica i testi dei widget in base al tipo di accesso alla stanza (crea o entra)
        if (!isServer) {
            editIp.setHint(R.string.ask_server_ip);
            room.setText(R.string.client_title);
            textStart.setText(R.string.client_button);
        } else {
            editIp.setHint(R.string.ask_client_ip);
            room.setText(R.string.server_title);
        }

        // Se si vuole entrare in una stanza o è stato aggiunto il secondo peer, nasconde il pulsante "AGGIUNGI"
        hideLeftButton();

        btnAdd.setOnClickListener(v -> {
            // Controlla l'input e avvisa l'utente se non è corretto
            if (checkInput())
                editIp.setError(getString(R.string.ip_error));
            else if (hasPermissions(this, PERMISSIONS)) {
                // Chiede i permessi
                ActivityCompat.requestPermissions(this, PERMISSIONS, 1);
            } else {
                // Riesegue questa activity per aggiungere il secondo peer
                callActivity(ConnectActivity.class);
            }
        });

        btnStart.setOnClickListener(v -> {
            // Controlla l'input e avvisa l'utente se non è corretto
            if (checkInput())
                editIp.setError(getString(R.string.ip_error));
            else if (hasPermissions(this, PERMISSIONS)) {
                // Chiede i permessi
                ActivityCompat.requestPermissions(this, PERMISSIONS, 1);
            } else {
                // Esegue l'activity WebRTCActivity
                callActivity(WebRTCActivity.class);
            }
        });
    }

    // Controlla i permessi
    private boolean hasPermissions(@NonNull Context context, @NonNull String[] permissions) {
        for (String permission : permissions)
            if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED)
                return true;

        return false;
    }

    // Nasconde il pulsante "AGGIUNGI"
    private void hideLeftButton() {
        if (!isServer || peersInfo.size() > 0) {
            btnAdd.setEnabled(false);
            imageAdd.setVisibility(View.INVISIBLE);
            textAdd.setVisibility(View.INVISIBLE);
        }
    }

    // Controlla l'inidrizzo inserito dall'utente
    private boolean checkInput() {
        // TODO: Controllare se è un indirizzo IPv4
        return editIp.getText() == null || editIp.getText().toString().equals("");
    }

    // Aggiunge il nuovo peer ed esegue un'activity
    private void callActivity(@NonNull Class<?> cl) {
        // Estrae l'IP e la porta dall'input
        String[] address = editIp.getText().toString().split(":");
        // Aggiunge l'IP alla lista dei peer
        peersInfo.add(new PeerInfo(address[0], Integer.parseInt(address[1]), isServer, (getResources().getInteger(R.integer.port) + peersInfo.size())));
        // Esegue l'activity passando la lista dei peer
        Intent intent = new Intent(this, cl);
        intent.putExtra("isServer", true);
        intent.putExtra("peersInfo", peersInfo);
        startActivity(intent);
        finish();
    }
}