package com.example.bluetooth_player;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentTransaction;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

import static android.bluetooth.BluetoothAdapter.ACTION_DISCOVERY_FINISHED;
import static android.bluetooth.BluetoothAdapter.ACTION_DISCOVERY_STARTED;
import static java.util.UUID.randomUUID;

public class MainActivity extends AppCompatActivity {
    //    TODO: ДОДЕЛАТЬ ЧТОБЫ ТЕЛЕФОН БЫЛ ВИДЕН ДЛЯ ДРУГИХ УСТРОЙСТВ ВО ВРЕМЯ ИСПОЛЬЗОВАНИЯ ПРИЛОЖЕНИЯ

    //    private static final int REQUEST_ENABLE_BT = 0;
    public static final int REQUEST_ENABLE_BLUETOOTH = 11;
    public static final int REQUEST_ACCESS_COARSE_LOCATION = 1;
    public static final String NAME = "BluetoothPlayer";
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
//    private static final UUID MY_UUID = randomUUID();

    final String TAG = "Bluetooth-check";
    //    private String status;
    BluetoothAdapter bluetooth;
    int statusDisc = 0;     //0 = start discovering, 1 = cancel discovering
    ArrayList<String> devicesInfo = new ArrayList<>();
    ArrayList<BluetoothDevice> bluetoothDevices = new ArrayList<>();
    ListView lvDevices;
    BluetoothDevice selectedDevice;
    ArrayList<UUID> uuidArrayList = new  ArrayList<UUID>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        bluetooth = BluetoothAdapter.getDefaultAdapter();
        registerReceiver(mReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
        registerReceiver(mReceiver, new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_STARTED));
        registerReceiver(mReceiver, new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED));


//        //делаем устройство видимым для других
//        Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
//        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
//        startActivity(discoverableIntent);

        checkBluetoothState();

        final Button bluetoothBtn = findViewById(R.id.bluetooth_btn);
        bluetoothBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (statusDisc == 0) {
                    if (bluetooth != null && bluetooth.isEnabled()) {
                        if (checkCoarseLocationPermission()) {
                            Boolean result = bluetooth.startDiscovery(); //start discovering and show result of function
                            Toast.makeText(getApplicationContext(), "Start discovery result: " + result, Toast.LENGTH_SHORT).show();
                            Log.d(TAG, "Start discovery: " + result);
                            bluetoothBtn.setText("Stop");
                            statusDisc = 1;
                        }
                    } else {
                        checkBluetoothState();
                    }
                } else {
                    Log.d(TAG, "Stop");
                    statusDisc = 0;
                    bluetooth.cancelDiscovery();
                    bluetoothBtn.setText("Start");
                }
            }
        });





//        if (bluetooth != null) {
////            if (!(bluetooth.isEnabled())) {
////                // Bluetooth выключен. Предложим пользователю включить его.
////                Log.d(TAG, "включаем");
////                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
////                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
////                onActivityResult(REQUEST_ENABLE_BT, RESULT_OK, enableBtIntent);
////
////            }
////            if (bluetooth.isEnabled()) {
////                // Bluetooth включен. Работаем.
////                infoText1.setText("Bluetooth Работает");
////                String myDeviceAddress = bluetooth.getAddress();
////                String myDeviceName = bluetooth.getName();
////                int state = bluetooth.getState();
//////                STATE_OFF = 10
//////                STATE_TURNING_ON = 11
//////                STATE_ON = 12
//////                STATE_TURNING_OFF = 13
////                status = myDeviceName + ": " + myDeviceAddress + '\n' + state;
////                infoText2.setText(status);
//
////                показваем список сопряженных устройств
////                Set<BluetoothDevice> pairedDevices = bluetooth.getBondedDevices();
////
////                if (pairedDevices.size() > 0) { // проходимся в цикле по этому списку
////                    for (BluetoothDevice device : pairedDevices) {
////                        devices.add(device.getName() + "\n" + device.getAddress());
////
////                        ArrayAdapter<String> devicesAdapter = new ArrayAdapter<>
////                                (this, android.R.layout.simple_list_item_1, devices);
////                        lvDevices = findViewById(R.id.lv_devices);
////                        lvDevices.setAdapter(devicesAdapter);
////                    }
////                }
//            // Создаем BroadcastReceiver для ACTION_FOUND
//
//
//            // Регистрируем BroadcastReceiver
//            IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
//            registerReceiver(mReceiver, filter);
//
//            ArrayAdapter<String> devicesAdapter = new ArrayAdapter<>
//                    (this, android.R.layout.simple_list_item_1, devices);
//            lvDevices = findViewById(R.id.lv_devices);
//            lvDevices.setAdapter(devicesAdapter);
//
//            if (bluetooth.startDiscovery()) {
//                Log.d(TAG, "запускаем поиск");
//                // Не забудьте снять регистрацию в onDestroy
//            } else {
//                Log.d(TAG, "fail");
//            }
//            Log.d(TAG, "вырубаем поиск");
//            bluetooth.cancelDiscovery();
//        }
    }
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Discovery has found a device. Get the BluetoothDevice
                // object and its info from the Intent.
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                String deviceName = device.getName();
                String deviceHardwareAddress = device.getAddress(); // MAC address
                Log.d(TAG, "Device found: " + deviceName + "|" + deviceHardwareAddress);

                bluetoothDevices.add(device);
                devicesInfo.add(device.getName() + "\n" + device.getAddress());

                ArrayAdapter devicesAdapter = new ArrayAdapter(getApplicationContext(), android.R.layout.simple_list_item_1, devicesInfo);
                lvDevices = findViewById(R.id.lv_devices);
                lvDevices.setAdapter(devicesAdapter);
                checkCoarseLocationPermission();

                lvDevices.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    public void onItemClick(AdapterView<?> parent, View view,
                                            int position, long id) {
                        Log.d(TAG, "itemClick: position = " + position + ", id = " + id);
                        selectedDevice = bluetoothDevices.get((int)id);
                        if(selectedDevice.fetchUuidsWithSdp()){
                            Log.d(TAG, "onItemClick: fetch-true");
                        }
                        BluetoothConnector bluetoothConnector = new BluetoothConnector(selectedDevice, true, bluetooth, uuidArrayList);

//                        TODO: сделать в отдельном потоке
                        try {
                            bluetoothConnector.connect();
                            Log.d(TAG, "onItemClick: ураарарара");

                        } catch (IOException e) {
                            e.printStackTrace();
                        }



//                        ConnectThread launch = new ConnectThread(selectedDevice);
//                        if (BluetoothDevice.DEVICE_TYPE_CLASSIC == selectedDevice.getType()) {
//                            launch.start();
//                        }
                    }
                });
            }

            if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
                //report user
                Log.d(TAG, "Started");
                Toast.makeText(getApplicationContext(), "STARTED", Toast.LENGTH_SHORT).show();
            }

            if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                //change button back to "Start"
                statusDisc = 0;
//                final Button test = findViewById(R.id.testbutton);
//                test.setText("Start");
                //report user
                Log.d(TAG, "Finished");
                Toast.makeText(getApplicationContext(), "FINISHED", Toast.LENGTH_SHORT).show();
            }

            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                final int extra = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
                if (extra == (BluetoothAdapter.STATE_ON)) {
                    if (bluetooth.isDiscovering()) {
                        bluetooth.cancelDiscovery();
                    }
                    Boolean b = bluetooth.startDiscovery();
                    Toast.makeText(getApplicationContext(), "Start discovery" + b, Toast.LENGTH_SHORT).show();
                }
            }
        }
    };

    private void checkBluetoothState() {
        //checks if bluetooth is available and if it´s enabled or not
        if (bluetooth == null) {
            Toast.makeText(getApplicationContext(), "Bluetooth not available", Toast.LENGTH_SHORT).show();
        } else {
            if (bluetooth.isEnabled()) {
                if (bluetooth.isDiscovering()) {
                    Toast.makeText(getApplicationContext(), "Device is discovering...", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getApplicationContext(), "Bluetooth is enabled", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(getApplicationContext(), "You need to enabled bluetooth", Toast.LENGTH_SHORT).show();
                Intent enabledIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enabledIntent, REQUEST_ENABLE_BLUETOOTH);
            }
        }
    }

    private boolean checkCoarseLocationPermission() {
        //checks all needed permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_ACCESS_COARSE_LOCATION);
            return false;
        } else {
            return true;
        }

    }

//    public void checkPermission() {
//        if (Build.VERSION.SDK_INT >= 23) {
//            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
//
//            } else {
//                ActivityCompat.requestPermissions(this, new String[]{
//                        Manifest.permission.ACCESS_FINE_LOCATION,
//                        Manifest.permission.ACCESS_COARSE_LOCATION,}, 1);
//            }
//        }
//    }
//    @Override
//    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
//        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
//        if (requestCode == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
//        } else {
//            checkPermission();
//        }
//    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case REQUEST_ACCESS_COARSE_LOCATION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(getApplicationContext(), "Permission granted", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getApplicationContext(), "Permission denied", Toast.LENGTH_SHORT).show();
                }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_ENABLE_BLUETOOTH) {
            checkBluetoothState();
        }
    }

    private class AcceptThread extends Thread {
        private final BluetoothServerSocket mmServerSocket;

        public AcceptThread() {
            // используем вспомогательную переменную, которую в дальнейшем
            // свяжем с mmServerSocket,
            BluetoothServerSocket tmp = null;
            try {
                // MY_UUID это UUID нашего приложения, это же значение
                // используется в клиентском приложении
                tmp = bluetooth.listenUsingRfcommWithServiceRecord(NAME, MY_UUID);
            } catch (IOException e) {
            }
            mmServerSocket = tmp;
        }

        public void run() {
            BluetoothSocket socket = null;
            // ждем пока не произойдет ошибка или не
            // будет возвращен сокет
            while (true) {
                try {
                    socket = mmServerSocket.accept();
                } catch (IOException e) {
                    break;
                }
                // если соединение было подтверждено
                if (socket != null) {
                    // управлчем соединением (в отдельном потоке)
                    manageConnectedSocket(socket);
                    try {
                        mmServerSocket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                }
            }
        }

        public void cancel() {
            try {
                mmServerSocket.close();
            } catch (IOException e) {
            }
        }
    }
    
    

    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            // используем вспомогательную переменную, которую в дальнейшем
            // свяжем с mmSocket,
            BluetoothSocket tmp = null;
            mmDevice = device;

            // получаем BluetoothSocket чтобы соединиться с BluetoothDevice
            try {
            // MY_UUID это UUID, который используется и в сервере
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
                Log.d(TAG, "ConnectThread: constructor-succssed");
            } catch (IOException e) {
                Log.d(TAG, "ConnectThread: constructor-failed");
            }
            mmSocket = tmp;

        }

        public void run() {
            // Отменяем сканирование, поскольку оно тормозит соединение
            bluetooth.cancelDiscovery();

            try {
                // Соединяемся с устройством через сокет.
                // Метод блокирует выполнение программы до
                // установки соединения или возникновения ошибки
                mmSocket.connect();
                Log.d(TAG, "run: connect");

            } catch (IOException connectException) {
                // Невозможно соединиться. Закрываем сокет и выходим.
                Log.d(TAG, "run: fail");
                Log.d(TAG, connectException.toString());
                try {
                    mmSocket.close();
                } catch (IOException closeException) {
                }
                return;
            }
            // управлчем соединением (в отдельном потоке)
            manageConnectedSocket(mmSocket);
        }

        /**
         * отмена ожидания сокета
         */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
            }
        }
    }

    private void manageConnectedSocket(BluetoothSocket socket) {
        Log.d(TAG, "manageConnectedSocket: succeess");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
    }
}

