package it.unive.reciak;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.arthenica.mobileffmpeg.FFmpeg;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.arthenica.mobileffmpeg.Config.RETURN_CODE_CANCEL;
import static com.arthenica.mobileffmpeg.Config.RETURN_CODE_SUCCESS;

public class RenderActivity extends AppCompatActivity {
    private static final String TAG = "RenderActivity";

    // Cartella con registrazioni
    @Nullable
    private String folder;
    // File registrazioni
    @Nullable
    private ArrayList<String> videos;

    // Avviso rendering in corso
    private ConstraintLayout loading;
    private TextView textDescription;
    // Rendering terminato
    private TextView textResult;
    // Pulsanti
    private ConstraintLayout buttons;
    // Pulsante "PLAY"
    private ConstraintLayout btnPlay;
    // Pulsante "CONDIVIDI"
    private ConstraintLayout btnShare;
    @Nullable
    private ExecutorService executor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_render);

        loading = findViewById(R.id.loading);
        textDescription = findViewById(R.id.textDescription);
        textResult = findViewById(R.id.textResult);
        buttons = findViewById(R.id.body);
        btnPlay = findViewById(R.id.btnPlay);
        btnShare = findViewById(R.id.btnShare);

        // Nasconde i pulsanti "PLAY" e "CONDIVIDI"
        buttons.setVisibility(View.INVISIBLE);

        // Recupera la lista dei video e la cartella dall'activity precedente
        Intent peer = getIntent();
        folder = peer.getStringExtra("folder");
        videos = peer.getStringArrayListExtra("videos");

        executor = Executors.newSingleThreadExecutor();

        executor.execute(() -> {
            // Elimina tutti i file temporanei dell'app
            deleteAppFiles();
            // Avvia la conversione dei video
            String input = transcode();

            if (input != null) {
                // Avvia la fusione dei video
                render(input);
            }
        });
    }

    // Conversione delle clip in un formato compatibile alla fusione
    private String transcode() {
        Log.i(TAG, "transcode");
        String output = "concat:";

        if (videos != null) {
            for (int i = 0; i < videos.size(); i++) {
                runOnUiThread(() -> textDescription.setText(getString(R.string.encoding)));

                String ffmpeg = String.format(Locale.getDefault(), "-i %s%s -c copy -bsf:v h264_mp4toannexb -f mpegts %s/VID-%d.ts", folder, videos.get(i), getFilesDir(), i);
                int rc = FFmpeg.execute(ffmpeg);

                // Conversione terminata con successo
                if (rc == RETURN_CODE_SUCCESS) {
                    Log.i(TAG, "Command execution completed successfully");
                    // Aggiunge il video convertito alla lista dei video da unire
                    if (i == 0)
                        output = String.format(Locale.getDefault(), "%s%s/VID-%d.ts", output, getFilesDir(), i);
                    else
                        output = String.format(Locale.getDefault(), "%s|%s/VID-%d.ts", output, getFilesDir(), i);
                } else if (rc == RETURN_CODE_CANCEL) {
                    // Conversione annullata dall'utente
                    Log.w(TAG, "Command execution cancelled by user");
                } else {
                    // Conversione fallita, il video potrebbe essere corrotto
                    Log.e(TAG, String.format("Command execution failed with rc=%d and the output below", rc));
                }
            }
        }

        return output;
    }

    // Fusione dei video
    private void render(String input) {
        Log.i(TAG, "render");
        String ffmpeg = String.format("-i \"%s\" -c copy -bsf:a aac_adtstoasc %sVID-ROOM.mp4", input, folder);

        runOnUiThread(() -> textDescription.setText(R.string.merge));

        int rc = FFmpeg.execute(ffmpeg);
        if (rc == RETURN_CODE_SUCCESS) {
            // Rendering terminato
            Log.i(TAG, "Rendering finished");

            Uri uri = Uri.parse(folder + "VID-ROOM.mp4");

            runOnUiThread(() -> {
                // Nasconde i messaggi
                loading.setVisibility(View.INVISIBLE);
                textResult.setVisibility(View.VISIBLE);
                // Mostra i pulsanti di riproduzione e condivisione
                buttons.setVisibility(View.VISIBLE);
                // Pulsante play: avvia riproduzione video
                btnPlay.setOnClickListener(v -> {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setDataAndType(uri, "video/mp4");
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(intent);
                    } catch (ActivityNotFoundException e) {
                        // Nessun media player installato
                        Toast.makeText(getApplicationContext(), R.string.player_error, Toast.LENGTH_SHORT).show();
                    }
                });
                // Pulsante condivisione: inoltra il video a un'app scelta dall'utente
                btnShare.setOnClickListener(v -> {
                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.setDataAndType(uri, "video/mp4");
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(intent);
                });
            });

            // Elimina le singole clip
            deleteFiles();
        } else if (rc == RETURN_CODE_CANCEL) {
            // Rendering annullato dall'utente
            Log.w(TAG, "Rendering cancelled by user");
        } else {
            // Rendering fallito
            Log.e(TAG, "Rendering failed");
            // Avvisa l'utente e termina l'app
            runOnUiThread(() -> Toast.makeText(getApplicationContext(), R.string.merge_failed, Toast.LENGTH_SHORT).show());
            finish();
        }

        // Elimina i file generati da ffmpeg
        deleteAppFiles();
    }

    // Elimina le singole clip
    private void deleteFiles() {
        Log.i(TAG, "deleteFiles");

        if (videos != null)
            for (int i = 0; i < videos.size(); i++)
                if (new File(String.format("%sVID-%s.mp4", folder, i)).delete())
                    Log.i(TAG, "VID-" + i + ".mp4 deleted");
    }

    // Elimina tutti i file dell'app
    private void deleteAppFiles() {
        Log.i(TAG, "deleteAppFiles");
        File dir = getFilesDir();

        if (dir != null && dir.isDirectory()) {
            String[] files = dir.list();
            if (files != null) {
                for (String file : files) {
                    if (new File(dir, file).delete())
                        Log.i(TAG, "Temp file " + file + " deleted");
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        // Annulla la conversione o il rendering
        FFmpeg.cancel();
        if (executor != null)
            executor.shutdown();
        super.onDestroy();
    }
}