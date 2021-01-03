package it.unive.reciak;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

public class WiFiDirectBroadcastReceiver extends BroadcastReceiver {
    private final WifiP2pManager manager;
    private final WifiP2pManager.Channel channel;
    private final DiscoverActivity activity;

    public WiFiDirectBroadcastReceiver(WifiP2pManager manager, WifiP2pManager.Channel channel, DiscoverActivity activity) {
        this.manager = manager;
        this.channel = channel;
        this.activity = activity;
    }

    // Gestore cambiamenti di stato nella connessione via Wi-Fi Direct
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
            // La lista dei dispositivi è cambiata
            if (manager != null) {
                // Controlla i permessi
                if (ActivityCompat.checkSelfPermission(activity.getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(activity.getApplicationContext(), R.string.permissions, Toast.LENGTH_SHORT).show();
                    return;
                }
                // Richiede la lista dei dispositivi disponibili
                manager.requestPeers(channel, activity.peerListListener);
            }
        } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
            // Stato della connessione Wi-Fi Direct cambiato
            if (manager == null) {
                return;
            }

            NetworkInfo networkInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
            // Se è connesso a un dispositivo
            if (networkInfo.isConnected()) {
                // Chiede informazioni sulla connessione
                manager.requestConnectionInfo(channel, activity.connectionInfoListener);
            }
        }
    }
}

