package it.unive.reciak;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.github.ybq.android.spinkit.SpinKitView;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import it.unive.reciak.socket.DiscoverSocket;
import it.unive.reciak.socket.TCPChannelClient;
import it.unive.reciak.utils.NetUtils;


public class DiscoverActivity extends AppCompatActivity implements TCPChannelClient.TCPChannelEvents {
    @NonNull
    private static final String TAG = "DiscoverActivity";

    // L'utente vuole creare un stanza
    private boolean isServer;
    // Lista contenente le informazioni dei peer
    @NonNull
    private final ArrayList<PeerInfo> peersInfo = new ArrayList<>();

    @Nullable
    private ExecutorService executor;
    // Socket per l'invio degli indirizzi
    @Nullable
    private DiscoverSocket discoverSocket;

    // Lista
    private ListView listView;
    // Animazione caricamento
    private SpinKitView spin;
    // Stato della connessione
    private TextView textDescription;
    // Pulsante avvio comunicazione WebRTC
    private ConstraintLayout btnStart;

    // Gestore rete Wi-Fi Direct
    @Nullable
    private WifiP2pManager manager;
    @Nullable
    private WifiP2pManager.Channel channel;

    @Nullable
    private BroadcastReceiver receiver;
    @Nullable
    private IntentFilter intentFilter;

    // Lista dei dispositivi disponibili
    @NonNull
    private final List<WifiP2pDevice> peers = new ArrayList<>();
    // Array contenente i dispositivi nella vicinanze con i relativi nomi
    private WifiP2pDevice[] deviceArray;

    private boolean startCall = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_discovery);

        listView = findViewById(R.id.listView);
        spin = findViewById(R.id.spin);
        textDescription = findViewById(R.id.textDescription);
        btnStart = findViewById(R.id.btnStart);

        // Gestore reti Wi-Fi
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        // Gestore Wi-Fi Direct
        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(this, getMainLooper(), null);
        receiver = new WiFiDirectBroadcastReceiver(manager, channel, this);

        // Elimina tutti i gruppi delle reti Wi-Fi Direct salvate in precedenza
        NetUtils.deletePersistentGroups(manager, channel);

        Intent peer = getIntent();
        isServer = peer.getBooleanExtra("isServer", false);

        listView.setVisibility(View.INVISIBLE);
        btnStart.setVisibility(View.INVISIBLE);

        if (!isServer) {
            textDescription.setText(R.string.server_description);
        } else {
            textDescription.setText(R.string.client_description);
        }

        intentFilter = new IntentFilter();
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        // Attiva il Wi-Fi se disabilitato
        if (!wifiManager.isWifiEnabled())
            wifiManager.setWifiEnabled(true);

        // TODO Permessi
        // Avvia la ricerca
        manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
            // Ricerca iniziata
            @Override
            public void onSuccess() {
                Log.i(TAG, "Discovery started");
            }

            // Ricerca fallita
            @Override
            public void onFailure(int reasonCode) {
                Toast.makeText(DiscoverActivity.this, "Ricerca fallita", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Discovery failed");
            }
        });

        // Elemento della lista premuto
        listView.setOnItemClickListener((AdapterView<?> parent, View view, int position, long id) -> {
            // Informazioni sul dispositivo
            final WifiP2pDevice device = deviceArray[position];
            // Configurazione connessione
            WifiP2pConfig config = new WifiP2pConfig();
            config.deviceAddress = device.deviceAddress;
            config.wps.setup = WpsInfo.PBC;

            // Se sono il creatore della stanza, alzo la probabilità di diventare l'amministratore della rete Wi-Fi Direct
            if (isServer)
                config.groupOwnerIntent = WifiP2pConfig.GROUP_OWNER_INTENT_MAX;
            else
                config.groupOwnerIntent = WifiP2pConfig.GROUP_OWNER_INTENT_MIN;

            // Se ci sono posti liberi, prova a connettersi
            if (peersInfo.size() < getResources().getInteger(R.integer.max_users)) {
                // TODO Permessi
                manager.connect(channel, config, new WifiP2pManager.ActionListener() {
                    final String deviceName = device.deviceName;

                    // Connessione in corso
                    @Override
                    public void onSuccess() {
                        Toast.makeText(DiscoverActivity.this, "Connessione a " + deviceName + " in corso...", Toast.LENGTH_SHORT).show();
                    }

                    // Connessione fallita
                    @Override
                    public void onFailure(int reason) {
                        Toast.makeText(DiscoverActivity.this, "Connessione a " + deviceName + " fallita. Riprova", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });

        btnStart.setOnClickListener(v -> callActivity());

        executor = Executors.newSingleThreadExecutor();
        // Se sono l'amministratore creo un ServerSocket
        if (isServer)
            discoverSocket = new DiscoverSocket(executor, this, new PeerInfo("", getResources().getInteger(R.integer.direct_port), isServer));
    }

    // Se ci sono dei dispositivi liberi nelle vicinanze
    WifiP2pManager.PeerListListener peerListListener = new WifiP2pManager.PeerListListener() {
        @Override
        public void onPeersAvailable(WifiP2pDeviceList peersList) {
            // Se sono l'amministratore mostro la lista
            if (isServer) {
                spin.setVisibility(View.INVISIBLE);
                textDescription.setVisibility(View.INVISIBLE);
                listView.setVisibility(View.VISIBLE);
            }

            // Se la lista dei dispositivi è cambiata, aggiorna la lista dell'activity
            if (!peersList.getDeviceList().equals(peers)) {
                peers.clear();
                peers.addAll(peersList.getDeviceList());

                String[] deviceNameArray = new String[peersList.getDeviceList().size()];
                deviceArray = new WifiP2pDevice[peersList.getDeviceList().size()];
                int index = 0;

                for (WifiP2pDevice device : peersList.getDeviceList()) {
                    deviceNameArray[index] = device.deviceName;
                    deviceArray[index] = device;
                    index++;
                }

                ArrayAdapter<String> adapter = new ArrayAdapter<>(getApplicationContext(), R.layout.list_row, deviceNameArray);
                listView.setAdapter(adapter);
            }

            // Se sono il server e non ci sono dispositivi nelle vicinanze, nascondo la lista
            if (isServer && peers.size() == 0) {
                textDescription.setText(R.string.client_description);
                spin.setVisibility(View.VISIBLE);
                textDescription.setVisibility(View.VISIBLE);
                listView.setVisibility(View.INVISIBLE);
            }
        }
    };

    // Richiesta informazioni sulla connessione
    WifiP2pManager.ConnectionInfoListener connectionInfoListener = info -> {
        // Indirizzo dell'amministratore
        final InetAddress groupOwnerAddress = info.groupOwnerAddress;

        // Se non sono l'amministratore
        if (!isServer) {
            // Aggiungo l'amministratore nella lista dei peer e mi connetto
            peersInfo.add(new PeerInfo(groupOwnerAddress.getHostAddress(), getResources().getInteger(R.integer.port), isServer));
            discoverSocket = new DiscoverSocket(executor, this, new PeerInfo(groupOwnerAddress.getHostAddress(), getResources().getInteger(R.integer.direct_port), isServer));
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        // Esegue il receiver sul main thread
        registerReceiver(receiver, intentFilter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Rimuove il receiver
        unregisterReceiver(receiver);
    }

    // Connesso al socket dell'altro peer
    @Override
    public void onTCPConnected() {
        // Se non sono l'amministratore
        if (!isServer) {
            // Invio l'indirizzo per la chiamata via WebRTC
            if (discoverSocket != null)
                discoverSocket.sendAddress(NetUtils.getIp(), getResources().getInteger(R.integer.port));
            runOnUiThread(() -> {
                // Avvio la comunicazione WebRTC e attendo
                textDescription.setText(R.string.connected);
                callActivity();
            });
        }
    }

    // Ha ricevuto un pacchetto da un altro peer
    @Override
    public void onTCPMessage(@NonNull String message) {
        try {
            // Indirizzo dell'altro peer ricevuto
            JSONObject packet = new JSONObject(message);
            JSONObject addressJson = packet.getJSONObject("value");
            String newPartnerIp = addressJson.getString("partnerIp");
            int newPartnerPort = addressJson.getInt("partnerPort");

            // Se ci sono posti liberi
            if (peersInfo.size() < getResources().getInteger(R.integer.max_users)) {
                // Aggiunge il peer alla lista
                peersInfo.add(new PeerInfo(newPartnerIp, newPartnerPort, isServer));

                runOnUiThread(() -> {
                    // Se sono l'amministratore, mostro il pulsante di avvio
                    if (isServer)
                        btnStart.setVisibility(View.VISIBLE);
                });
            }

            // Chiude il socket
            if (discoverSocket != null)
                discoverSocket.disconnect();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onTCPError(String description) {}

    // Se un peer si è disconnesso
    @Override
    public void onTCPClose() {
        // Se sono l'amministratore, la chiamata non è iniziata e ci sono altri posti liberi
        if (isServer && !startCall && peersInfo.size() < getResources().getInteger(R.integer.max_users)) {
            // Riavvio il ServerSocket
            discoverSocket = new DiscoverSocket(executor, this, new PeerInfo("", getResources().getInteger(R.integer.direct_port), isServer));
        }
        else if (executor != null) {
            // Il thread viene chiuso
            executor.shutdown();
        }
    }

    protected void callActivity() {
        // La chiamata sta per iniziare
        startCall = true;
        // Esegue l'activity passando la lista dei peer
        Intent intent = new Intent(this, CallActivity.class);
        intent.putExtra("peersInfo", peersInfo);
        startActivity(intent);
        finish();
    }
}