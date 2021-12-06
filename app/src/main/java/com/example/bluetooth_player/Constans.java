package com.example.bluetooth_player;

import java.util.UUID;

public interface Constans {
    int BYTES_READ = 1;
    int SECONDS_READ = 2;
    int REQUEST_ENABLE_BLUETOOTH = 11;
    int REQUEST_ACCESS_COARSE_LOCATION = 1;
    String TAG = "Bluetooth-check";
    String NAME = "BluetoothPlayer";

    //    TODO: поменять uuid на кастомный
    UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    //    private static final UUID MY_UUID = randomUUID();
}
