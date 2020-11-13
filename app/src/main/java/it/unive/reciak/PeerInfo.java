package it.unive.reciak;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

public class PeerInfo implements Parcelable {
    // IP del peer
    private final String ip;
    // Port del peer
    private final int port;
    // Vero se devo iniziare la comunicazione, falso se l'ha iniziata il peer (int per compatibilità con il vecchio SDK)
    private final int isInitiator;
    // Porta da usare per comunicare con il peer
    private final int initiatorPort;

    public PeerInfo(Parcel parcel) {
        ip = parcel.readString();
        port = parcel.readInt();
        isInitiator = parcel.readInt();
        initiatorPort = parcel.readInt();
    }

    public PeerInfo(@NonNull String ip, int port, boolean isInitiator, int initiatorPort) {
        this.ip = ip;
        this.port = port;
        this.isInitiator = isInitiator ? 1 : 0;
        this.initiatorPort = initiatorPort;
    }

    @NonNull
    public String getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }

    public boolean isInitiator() {
        return isInitiator == 1;
    }

    public int getInitiatorPort() {
        return initiatorPort;
    }

    // Metodi di Parcelable, interfaccia necessaria per passare un oggetto PeerInfo da un'activity a un'altra
    @Override
    public int describeContents() {
        return this.hashCode();
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeString(ip);
        parcel.writeInt(port);
        parcel.writeInt(isInitiator);
        parcel.writeInt(initiatorPort);
    }

    public static Creator<PeerInfo> CREATOR = new Creator<PeerInfo>() {
        @Override
        public PeerInfo createFromParcel(Parcel parcel) {
            return new PeerInfo(parcel);
        }

        @Override
        public PeerInfo[] newArray(int size) {
            return new PeerInfo[size];
        }
    };
}
