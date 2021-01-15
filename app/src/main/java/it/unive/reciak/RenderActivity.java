package it.unive.reciak;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.arthenica.mobileffmpeg.FFmpeg;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.arthenica.mobileffmpeg.Config.RETURN_CODE_CANCEL;
import static com.arthenica.mobileffmpeg.Config.RETURN_CODE_SUCCESS;

/**
 * Activity rendering video, con riproduzione e condivisione della registrazione finale.
 *
 * @see <a href="https://github.com/tanersener/mobile-ffmpeg">Libreria usata per il rendering</a>
 */
public class RenderActivity extends AppCompatActivity {
    private static final String TAG = "RenderActivity";

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
    // Executor per il rendering
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

        // Recupera la lista dei video dall'activity precedente
        Intent peer = getIntent();
        videos = peer.getStringArrayListExtra("videos");

        executor = Executors.newSingleThreadExecutor();

        // Esegue il rendering su un executor
        executor.execute(() -> {
            // Avvia la conversione dei video
            String input = transcode();

            if (input != null) {
                // Avvia la fusione dei video
                render(input);
            }
        });
    }

    /**
     * Converte le clip in un formato compatibile alla fusione.
     *
     * @return stringa contentente i percorsi alle singole clip
     */
    private String transcode() {
        Log.i(TAG, "transcode");
        String output = "concat:";

        if (videos != null) {
            for (int i = 0; i < videos.size(); i++) {
                runOnUiThread(() -> textDescription.setText(getString(R.string.encoding)));

                // Percorso video nello storage privato dell'app
                String folder = getFilesDir().getAbsolutePath();
                // Comando ffmpeg
                String ffmpeg = String.format(Locale.getDefault(), "-i %s/%s -c copy -bsf:v h264_mp4toannexb -f mpegts %s/VID_%d.ts", folder, videos.get(i), folder, i);
                // Esegue commando ffmpeg
                int rc = FFmpeg.execute(ffmpeg);

                // Conversione terminata con successo
                if (rc == RETURN_CODE_SUCCESS) {
                    Log.i(TAG, "Command execution completed successfully");
                    // Aggiunge il video convertito alla lista dei video da unire
                    if (output.length() <= 7)
                        output = String.format(Locale.getDefault(), "%s%s/VID_%d.ts", output, folder, i);
                    else
                        output = String.format(Locale.getDefault(), "%s|%s/VID_%d.ts", output, folder, i);
                } else if (rc == RETURN_CODE_CANCEL) {
                    // Conversione annullata dall'utente
                    Log.w(TAG, "Command execution cancelled by user");
                } else {
                    // Conversione fallita, il video potrebbe essere corrotto
                    Log.e(TAG, String.format("Command execution failed with rc=%d and the output below", rc));
                }
            }
        }

        // Ritorna i percorsi delle clip convertite
        return output;
    }

    /**
     * Fonde le clip in un unico video. Il filmato verrà salvato direttamente nella cartella DCIM
     * se il dispositivo usa Android < 10, altrimenti verrà salvato nello storage privato dell'app.
     *
     * @param input stringa contenente i percorsi dei video convertiti da transcode()
     */
    private void render(String input) {
        Log.i(TAG, "render");

        // Timestamp per il nome del video fuso
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
        String time = formatter.format(new Date());
        String folder;

        try {
            // Se il dispositivo usa Android 10+, salva il video nello storage privato
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                folder = getFilesDir().getAbsolutePath();
            else {
                // Altrimenti lo salva direttamente in DCIM
                File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsoluteFile() + "/ReCiak!/");
                if (dir.mkdirs())
                    Log.i(TAG, "render: folder created");
                folder = dir.getAbsolutePath();
            }

            // Comando ffmpeg per la fusione delle clip
            String ffmpeg = String.format("-i \"%s\" -c copy -bsf:a aac_adtstoasc %s/VID_ROOM_%s.mp4", input, folder, time);
            runOnUiThread(() -> textDescription.setText(R.string.merge));
            // Esegue comando ffmpeg
            int rc = FFmpeg.execute(ffmpeg);

            // Fusione terminata con successo
            if (rc == RETURN_CODE_SUCCESS) {
                // Rendering terminato
                Log.i(TAG, "Rendering finished");
                // Se il video è salvato nello storage privato lo copia in DCIM
                Uri uri = copyVideo("VID_ROOM_" + time + ".mp4");

                runOnUiThread(() -> {
                    // Nasconde i messaggi
                    loading.setVisibility(View.INVISIBLE);
                    textResult.setVisibility(View.VISIBLE);
                    // Mostra i pulsanti di riproduzione e condivisione
                    buttons.setVisibility(View.VISIBLE);

                    // Pulsante play: avvia riproduzione video
                    btnPlay.setOnClickListener(v -> {
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setDataAndType(uri, "video/mp4");
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(intent);
                    });
                    // Pulsante condivisione: inoltra il video a un'app scelta dall'utente
                    btnShare.setOnClickListener(v -> {
                        Intent intent = new Intent();
                        intent.setAction(Intent.ACTION_SEND);
                        intent.putExtra(Intent.EXTRA_STREAM, uri);
                        intent.setType("video/mp4");
                        startActivity(intent);
                    });
                });
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

            // Elimina le clip
            deleteFiles(getApplicationContext());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Se la versione di Android usa lo scoped storage, copia il video renderizzato nella cartella condivisa DCIM.
     * Aggiorna le informazioni del video.
     *
     * @param file nome video renderizzato
     * @return Uri Uri al file renderizzato
     */
    @NonNull
    private Uri copyVideo(String file) {
        Log.i(TAG, "copyVideo");
        ContentValues values = new ContentValues();
        Uri uri;

        values.put(MediaStore.Video.Media.DISPLAY_NAME, file);
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");

        // Se il dispositivo usa Android 10+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Aggiorna le informazioni del file
            values.put(MediaStore.Video.Media.RELATIVE_PATH, "DCIM/ReCiak!");
            uri = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);

            // Copia il file dalla cartella privata dell'app alla cartella DCIM
            try {
                ParcelFileDescriptor descriptor = getContentResolver().openFileDescriptor(uri, "w");
                FileDescriptor fileDescriptor = descriptor.getFileDescriptor();

                InputStream input = openFileInput(file);
                OutputStream output = new FileOutputStream(fileDescriptor);
                byte[] buf = new byte[1024];
                int bytesRead;

                while ((bytesRead = input.read(buf)) > 0)
                    output.write(buf, 0, bytesRead);
                input.close();
                output.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            // Il video è stato salvato direttamente in DCIM
            values.put(MediaStore.Video.Media.DATA, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath() + "/ReCiak!/" + file);
            // Aggiorna le informazioni del file
            uri = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
        }

        return uri;
    }

    /**
     * Elimina le clip e i video convertiti salvati nello storage privato.
     *
     * @param context Context dell'activity chiamante
     */
    public static void deleteFiles(Context context) {
        Log.i(TAG, "deleteFiles");
        File dir = context.getFilesDir();

        if (dir != null && dir.isDirectory()) {
            String[] files = dir.list();
            if (files != null) {
                for (String file : files) {
                    if (new File(dir, file).delete())
                        Log.i(TAG, "File " + file + " deleted");
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        // Elimina tutte le clip
        deleteFiles(getApplicationContext());
        // Annulla la conversione o il rendering
        FFmpeg.cancel();
        if (executor != null)
            executor.shutdown();
        super.onDestroy();
    }
}