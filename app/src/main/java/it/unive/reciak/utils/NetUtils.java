package it.unive.reciak.utils;

import android.net.wifi.p2p.WifiP2pManager;

import androidx.annotation.NonNull;

import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;

public class NetUtils {
    // Restituisce l'indirizzo IP del dispositivo
    @NonNull
    public static String getIp() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress()) {
                        String sAddr = addr.getHostAddress();
                        boolean isIPv4 = sAddr.indexOf(':') < 0;

                        if (isIPv4 && sAddr.contains(".49."))
                            return sAddr;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    // Rimuove i gruppi Wi-Fi Direct salvati in precedenza
    public static void deletePersistentGroups(WifiP2pManager wifiP2pManager, WifiP2pManager.Channel channel) {
        try {
            Method[] methods = WifiP2pManager.class.getMethods();
            for (Method method : methods) {
                if (method.getName().equals("deletePersistentGroup")) {
                    for (int netid = 0; netid < 32; netid++) {
                        method.invoke(wifiP2pManager, channel, netid, null);
                    }
                }
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
}
