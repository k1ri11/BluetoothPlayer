package com.example.bluetooth_player;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {



    BluetoothAdapter mAdapter;
    BluetoothService mBluetoothService;
    boolean permissions = false;

    int statusDisc = 0;     //0 = start discovering, 1 = cancel discovering
    ArrayList<String> devicesInfo = new ArrayList<>();
    ArrayList<BluetoothDevice> bluetoothDevices = new ArrayList<>();
    ListView lvDevices;

    BluetoothDevice selectedDevice;

    TextView text;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAdapter = BluetoothAdapter.getDefaultAdapter();
        checkCoarseLocationPermission();

        final Handler mHandler = new Handler(getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == Constans.MESSAGE_READ) {
                    byte[] readBuf = (byte[]) msg.obj; //буфер считанный из потока
                    // construct a string from the valid bytes in the buffer
                    int bytes = msg.arg1;
                    text.setText("" + bytes); // возможно во фрагменте нужно будет юзать ui thread
                    Log.d(Constans.TAG, "handleMessage:bytes " + bytes);
                }
            }
        };

        mBluetoothService = new BluetoothService(this, this.getApplicationContext(), mAdapter, permissions, mHandler);

        registerReceiver(mReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
        registerReceiver(mReceiver, new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_STARTED));
        registerReceiver(mReceiver, new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED));

        //делаем устройство видимым для других
        Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 100);
        startActivity(discoverableIntent);

        mBluetoothService.checkBluetoothState();

        text = findViewById(R.id.text);

        Button serverBtn = findViewById(R.id.server_btn);
        serverBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mAdapter != null && mAdapter.isEnabled()) {
                    if (permissions) {
                        mBluetoothService.startServer();
                    }
                }
            }
        });

        Button searchBtn = findViewById(R.id.bluetooth_btn);
        searchBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mBluetoothService.startSearch();
            }
        });
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

                bluetoothDevices.add(device);
                devicesInfo.add(device.getName() + "\n" + device.getAddress());

                ArrayAdapter devicesAdapter = new ArrayAdapter(getApplicationContext(), android.R.layout.simple_list_item_1, devicesInfo);
                lvDevices = findViewById(R.id.lv_devices);
                lvDevices.setAdapter(devicesAdapter);
                checkCoarseLocationPermission();

                lvDevices.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                        selectedDevice = bluetoothDevices.get((int) id);

                        mBluetoothService.startConnection(selectedDevice);
                    }
                });
            }

            if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
                //report user
                Log.d(Constans.TAG, "Started");
                Toast.makeText(getApplicationContext(), "STARTED", Toast.LENGTH_SHORT).show();
            }

            if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                //change button back to "Start"
                statusDisc = 0;
//                final Button test = findViewById(R.id.testbutton);
//                test.setText("Start");
                //report user
                Log.d(Constans.TAG, "Finished");
                Toast.makeText(getApplicationContext(), "FINISHED", Toast.LENGTH_SHORT).show();
            }

            if (mAdapter.ACTION_STATE_CHANGED.equals(action)) {
                final int extra = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
                if (extra == (BluetoothAdapter.STATE_ON)) {
                    if (mAdapter.isDiscovering()) {
                        mAdapter.cancelDiscovery();
                    }
                    Boolean b = mAdapter.startDiscovery();
                    Toast.makeText(getApplicationContext(), "Start discovery" + b, Toast.LENGTH_SHORT).show();
                }
            }
        }
    };

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

    //    оставить в активити, в отдельном классе получится колхоз
    private void checkCoarseLocationPermission() {
        //checks all needed permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, Constans.REQUEST_ACCESS_COARSE_LOCATION);
            permissions =  false;
        } else {
            permissions =  true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case Constans.REQUEST_ACCESS_COARSE_LOCATION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(getApplicationContext(), "Permission granted", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getApplicationContext(), "Permission denied", Toast.LENGTH_SHORT).show();
                }
        }
    }

    public void startBluetooth(){
        Intent enabledIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enabledIntent, Constans.REQUEST_ENABLE_BLUETOOTH);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == Constans.REQUEST_ENABLE_BLUETOOTH) {
            mBluetoothService.checkBluetoothState();
        }
    }

//    todo: сделать преобразование трека в байты

    private static byte[] readFileToBytes() throws IOException {

        File file = new File("music.mp3");
        byte[] bytes = new byte[(int) file.length()];

        FileInputStream fis = null;
        try {

            fis = new FileInputStream(file);

            //read file into bytes[]
            fis.read(bytes);

        } finally {
            if (fis != null) {
                fis.close();
            }
            return bytes;
        }
    }

    public byte[] convert() throws IOException {


        File fileStreamPath = getFileStreamPath("music.mp3");
        FileInputStream fis = new FileInputStream("file:///android_asset/raw/music.mp3");
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] b = new byte[1024];

        for (int readNum; (readNum = fis.read(b)) != -1;) {
            bos.write(b, 0, readNum);
        }

        byte[] bytes = bos.toByteArray();

        return bytes;
    }

        @Override
        protected void onDestroy() {
            super.onDestroy();
            unregisterReceiver(mReceiver);
        }
    }