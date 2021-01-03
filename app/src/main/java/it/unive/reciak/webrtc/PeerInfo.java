package it.unive.reciak.webrtc;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

// Informazioni di un peer
public class PeerInfo implements Parcelable {
    // IP del peer
    private final String ip;
    // Porta del peer
    private final int port;
    // Vero se devo iniziare la comunicazione, falso se l'ha iniziata il peer (int per compatibilità con il vecchio SDK)
    private final int isInitiator;

    public PeerInfo(Parcel parcel) {
        ip = parcel.readString();
        port = parcel.readInt();
        isInitiator = parcel.readInt();
    }

    public PeerInfo(@NonNull String ip, int port, boolean isInitiator) {
        this.ip = ip;
        this.port = port;
        this.isInitiator = isInitiator ? 1 : 0;
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
