package it.unive.reciak;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
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
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;

import com.github.ybq.android.spinkit.SpinKitView;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import it.unive.reciak.socket.DiscoverSocket;
import it.unive.reciak.socket.TCPChannelClient;
import it.unive.reciak.webrtc.PeerInfo;

/**
 * Activity ricerca dispositivi nelle vicinanze mediante Wi-Fi Direct
 *
 * @see <a href="https://developer.android.com/training/connect-devices-wirelessly/wifi-direct">Guida utilizzo a Wi-Fi Direct</a>
 */
public class DiscoverActivity extends AppCompatActivity implements TCPChannelClient.TCPChannelEvents {
    @NonNull
    private static final String TAG = "DiscoverActivity";

    // L'utente vuole creare un stanza
    private boolean isServer;
    // Lista contenente le informazioni dei peer
    @NonNull
    private final ArrayList<PeerInfo> peersInfo = new ArrayList<>();

    // Gestore socket
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
    private static WifiP2pManager manager;
    // Collega l'app al framework Wi-Fi Direct
    @Nullable
    private static WifiP2pManager.Channel channel;

    // Gestore stato Wi-Fi Direct
    @Nullable
    private BroadcastReceiver receiver;
    // Informazioni sullo stato della rete Wi-Fi Direct
    @Nullable
    private IntentFilter intentFilter;

    // Lista dei dispositivi disponibili
    @NonNull
    private final List<WifiP2pDevice> peers = new ArrayList<>();
    // Array contenente i dispositivi nella vicinanze con i relativi nomi
    private WifiP2pDevice[] deviceArray;

    // Numero di dispositivi selezionati nella lista
    private int selectedPeers;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_discovery);

        // Titolo
        TextView textRoom = findViewById(R.id.textRoom);

        listView = findViewById(R.id.listView);
        spin = findViewById(R.id.spin);
        textDescription = findViewById(R.id.textDescription);
        btnStart = findViewById(R.id.btnShare);

        // Peer selezionati nella lista
        selectedPeers = 0;

        // Prepara connessione Wi-Fi Direct
        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(this, getMainLooper(), null);
        receiver = new WiFiDirectBroadcastReceiver(manager, channel, this);

        // Disconnessione da una rete Wi-Fi Direct
        disconnect();
        // Elimina tutti i gruppi delle reti Wi-Fi Direct salvate in precedenza
        deleteGroups();

        // Recupera isServer da MainActivity
        Intent peer = getIntent();
        isServer = peer.getBooleanExtra("isServer", false);

        // Cambia il testo dell'activity in base al pulsante premuto nell'activity precedente
        if (!isServer) {
            textRoom.setText(R.string.wait_invite);
            textDescription.setText(R.string.waiting);
        } else {
            textRoom.setText(R.string.send_invite);
            textDescription.setText(R.string.searching);
        }

        // Intent impliciti, l'app richiede informazioni riguardanti lo stato della connessione Wi-Fi Direct
        intentFilter = new IntentFilter();
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        executor = Executors.newSingleThreadExecutor();

        // Controlla i permessi
        if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(getApplicationContext(), R.string.permissions, Toast.LENGTH_SHORT).show();
            return;
        }
        // Avvia la ricerca
        manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.i(TAG, "discoverPeers: success");
            }

            @Override
            public void onFailure(int reason) {
                Log.e(TAG, "discoverPeers: failed");
                if (reason == WifiP2pManager.P2P_UNSUPPORTED) {
                    Log.e(TAG, "Wi-Fi Direct not supported");
                    Toast.makeText(DiscoverActivity.this, R.string.wifi_unsupported, Toast.LENGTH_SHORT).show();
                } else {
                    // Gestore reti Wi-Fi
                    WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                    // Gestore GPS
                    LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
                    // Se il Wi-Fi è disabilitato, avvisa l'utente
                    if (!wifiManager.isWifiEnabled())
                        Toast.makeText(DiscoverActivity.this, R.string.wifi_disabled, Toast.LENGTH_SHORT).show();
                    // Se il GPS è disabilitato, avvisa l'utente
                    if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
                        Toast.makeText(DiscoverActivity.this, R.string.gps_disabled, Toast.LENGTH_SHORT).show();
                }
            }
        });

        // Se sono l'amministratore
        if (isServer) {
            // Elemento della lista premuto
            listView.setOnItemClickListener((AdapterView<?> parent, View view, int position, long id) -> {
                // Informazioni sul dispositivo
                final WifiP2pDevice device = deviceArray[position];
                // Configurazione connessione
                WifiP2pConfig config = new WifiP2pConfig();
                config.deviceAddress = device.deviceAddress;
                config.wps.setup = WpsInfo.PBC;

                // Alzo la probabilità di diventare l'amministratore della rete Wi-Fi Direct
                config.groupOwnerIntent = WifiP2pConfig.GROUP_OWNER_INTENT_MAX;

                // Se ci sono posti liberi, prova a connettersi
                if (peersInfo.size() < getResources().getInteger(R.integer.max_users)) {
                    selectedPeers++;

                    // Controlla i permessi
                    if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        Toast.makeText(getApplicationContext(), R.string.permissions, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    manager.connect(channel, config, new WifiP2pManager.ActionListener() {
                        final String deviceName = device.deviceName;

                        // Connessione in corso
                        @Override
                        public void onSuccess() {
                            Log.i(TAG, "connect: success");
                            Toast.makeText(DiscoverActivity.this, String.format(getString(R.string.connect_success), deviceName), Toast.LENGTH_SHORT).show();
                        }

                        // Connessione fallita
                        @Override
                        public void onFailure(int reason) {
                            Log.e(TAG, "connect: failed");
                            Toast.makeText(DiscoverActivity.this, String.format(getString(R.string.connect_failure), deviceName), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });

            // Pulsante "AVVIA"
            btnStart.setOnClickListener(v -> {
                // Se il numero di peer connessi è diverso dal numero di richieste inviate, avvisa l'utente
                if (peersInfo.size() == selectedPeers)
                    callActivity();
                else {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AlertDialogStyle);
                    builder.setTitle(getString(R.string.alert_title));
                    builder.setMessage(getString(R.string.alert_message));

                    builder.setPositiveButton(getString(R.string.alert_positive), (dialog, which) -> callActivity());
                    builder.setNegativeButton(getString(R.string.alert_negative), null);

                    AlertDialog dialog = builder.create();
                    dialog.show();
                }
            });

            // Creo un ServerSocket
            discoverSocket = new DiscoverSocket(executor, this, new PeerInfo(null, getResources().getInteger(R.integer.discover_port), isServer));
        }
    }

    // Se ci sono dei dispositivi liberi nelle vicinanze
    public final WifiP2pManager.PeerListListener peerListListener = new WifiP2pManager.PeerListListener() {
        @Override
        public void onPeersAvailable(WifiP2pDeviceList peersList) {
            // Se sono l'amministratore
            if (isServer) {
                // Mostro la lista
                spin.setVisibility(View.INVISIBLE);
                textDescription.setVisibility(View.INVISIBLE);
                listView.setVisibility(View.VISIBLE);

                // Se la lista dei dispositivi è cambiata, aggiorno la lista dell'activity
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

                // Se non ci sono dispositivi nelle vicinanze, nascondo la lista
                if (peers.size() == 0) {
                    textDescription.setText(R.string.searching);
                    spin.setVisibility(View.VISIBLE);
                    textDescription.setVisibility(View.VISIBLE);
                    listView.setVisibility(View.INVISIBLE);
                }
            }
        }
    };

    // Richiesta informazioni sulla connessione
    public final WifiP2pManager.ConnectionInfoListener connectionInfoListener = info -> {
        // Indirizzo dell'amministratore
        final InetAddress groupOwnerAddress = info.groupOwnerAddress;

        // Se non sono l'amministratore
        if (!isServer && groupOwnerAddress != null) {
            // Aggiungo l'amministratore nella lista dei peer e mi connetto
            peersInfo.add(new PeerInfo(groupOwnerAddress.getHostAddress(), getResources().getInteger(R.integer.call_port), isServer));
            discoverSocket = new DiscoverSocket(executor, this, new PeerInfo(groupOwnerAddress.getHostAddress(), getResources().getInteger(R.integer.discover_port), isServer));
        }
    };

    /**
     * Restituisce l'indirizzo IP del dispositivo connesso a una rete Wi-Fi Direct.
     *
     * @return indirizzo IP del dispositivo
     */
    @Nullable
    public String getIp() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress()) {
                        String sAddr = addr.getHostAddress();
                        boolean isIPv4 = sAddr.indexOf(':') < 0;

                        if (isIPv4 && sAddr.contains("192.168.49."))
                            return sAddr;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Disconnessione da una rete Wi-Fi Direct.
     */
    public static void disconnect() {
        if (manager != null) {
            manager.removeGroup(channel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    Log.i(TAG, "removeGroup: success");
                }

                @Override
                public void onFailure(int reason) {
                    Log.w(TAG, "removeGroup: failed");
                }
            });
        }
    }

    /**
     * Rimuove i gruppi Wi-Fi Direct salvati in precedenza.
     * Android salva i gruppi Wi-Fi Direct di default bypassando la richiesta degli inviti.
     * Rimuovendoli i dispositivi dovranno ricevere un invito per connettersi a una stanza.
     */
    public void deleteGroups() {
        try {
            Method[] methods = WifiP2pManager.class.getMethods();
            for (Method method : methods) {
                if (method.getName().equals("deletePersistentGroup")) {
                    for (int netid = 0; netid < 32; netid++) {
                        method.invoke(manager, channel, netid, null);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Connesso al socket dell'altro peer
    @Override
    public void onTCPConnected() {
        // Se non sono l'amministratore
        if (!isServer) {
            String ip = getIp();
            // Invio l'indirizzo per la chiamata via WebRTC
            if (discoverSocket != null && ip != null)
                discoverSocket.sendAddress(ip, getResources().getInteger(R.integer.call_port));
            runOnUiThread(() -> textDescription.setText(R.string.connected));
        }
    }

    // Ha ricevuto un pacchetto da un altro peer
    @Override
    public void onTCPMessage(@NonNull JSONObject message) {
        try {
            // Indirizzo dell'altro peer ricevuto
            JSONObject addressJson = message.getJSONObject("value");
            String newPartnerIp = addressJson.getString("partnerIp");
            int newPartnerPort = addressJson.getInt("partnerPort");

            // Se ci sono posti liberi
            if (peersInfo.size() < getResources().getInteger(R.integer.max_users)) {
                // Aggiunge il peer alla lista
                peersInfo.add(new PeerInfo(newPartnerIp, newPartnerPort, isServer));

                runOnUiThread(() -> {
                    // Mostro il pulsante di avvio
                    btnStart.setVisibility(View.VISIBLE);
                });
            }

            // Chiude il socket
            if (discoverSocket != null) {
                discoverSocket.disconnect();
                discoverSocket = new DiscoverSocket(executor, this, new PeerInfo(null, getResources().getInteger(R.integer.discover_port), isServer));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onTCPError() {
        // Se non sono l'amministratore e non ho l'indirizzo dell'amministratore, riavvio il socket
        if (!isServer && !peersInfo.isEmpty() && discoverSocket != null) {
            discoverSocket.disconnect();
            discoverSocket = new DiscoverSocket(executor, this, new PeerInfo(peersInfo.get(0).getIp(), getResources().getInteger(R.integer.discover_port), isServer));
        }
    }

    // Se un peer si è disconnesso
    @Override
    public void onTCPClose() {
        // Se non sono l'amministratore, avvio la chiamata WebRTC
        if (!isServer)
            callActivity();
    }

    /**
     * Avvia l'activity CallActivity passando la lista contentente gli indirizzi dei peer connessi.
     */
    protected void callActivity() {
        // Chiude il socket
        if (discoverSocket != null)
            discoverSocket.disconnect();
        if (executor != null)
            executor.shutdown();

        // Esegue l'activity passando la lista dei peer
        Intent intent = new Intent(this, CallActivity.class);
        intent.putExtra("peersInfo", peersInfo);
        startActivity(intent);
        finish();
    }

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

    @Override
    protected void onDestroy() {
        // Chiude il socket
        if (discoverSocket != null)
            discoverSocket.disconnect();
        if (executor != null)
            executor.shutdown();

        super.onDestroy();
    }
}