package com.example.bluetooth_player;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
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
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
    private final List<MusicList> musicLists = new ArrayList<>();

    byte[] bufferBytes;
    String playingMusicPath;
    MediaPlayer mediaPlayer;

    String tmpDeviceName = "";//    колхоз

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            getMusicFiles();
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 11);
            } else {
                getMusicFiles();
            }
        }
        Log.d(Constans.TAG, "music files found: " + musicLists.size());

        Button broadcastBTN = findViewById(R.id.broadcast);
        broadcastBTN.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                Runnable runnable = new Runnable() {
                    @Override
                    public void run() {

                        byte[] bytes = null;
                        try {
                            bytes = mBluetoothService.readFileToBytes(musicLists.get(0).getMusicFile());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                        int subArraySize = 990;
                        String string = String.valueOf(bytes.length); //any type: int, double, float...
                        byte[] bytes1 = string.getBytes();
                        Log.d(Constans.TAG, "run: byteLength " + string);

                        mBluetoothService.getmConnectedThread().write(bytes1);
                        try {
                            mBluetoothService.getmConnectedThread().sleep(50);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        byte[] tempArray;
                        Log.d(Constans.TAG, "run: start time");
                        for (int i = 0; i < bytes.length; i += subArraySize) {
                            tempArray = Arrays.copyOfRange(bytes, i, Math.min(bytes.length, i + subArraySize));
                            mBluetoothService.getmConnectedThread().write(tempArray);
                        }
                    }
                };
                Thread broadcast = new Thread(runnable);
                broadcast.start();
            }
        });

        mediaPlayer = new MediaPlayer();
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        checkCoarseLocationPermission();

        final Handler mHandler = new Handler(getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == Constans.MESSAGE_READ) {
                    byte[] readBuf = (byte[]) msg.obj; //буфер считанный из потока
                    // construct a string from the valid bytes in the buffer
                    int bytes = msg.arg1;

                    FileInputStream fis = mBluetoothService.WriteByteArrayToFile(readBuf);
                    startPlayer(fis);
                    Log.d(Constans.TAG, "handleMessage: finish time " + bytes);
                    text.setText("" + bytes); // возможно во фрагменте нужно будет юзать ui thread
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

    private void startPlayer(FileInputStream fis) {
        try {
            mediaPlayer.setDataSource(fis.getFD());
            mediaPlayer.prepare();
            mediaPlayer.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Discovery has found a device. Get the BluetoothDevice
                // object and its info from the Intent.
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                String deviceName = device.getName();
                if ((deviceName != null) && !(tmpDeviceName.equals(deviceName))) {
                    bluetoothDevices.add(device);
                    devicesInfo.add(deviceName);
                    tmpDeviceName = deviceName;
                }
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
            permissions = false;
        } else {
            permissions = true;
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

    public void startBluetooth() {
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


    // перенести в fragment
    private void getMusicFiles() {

        ContentResolver contentResolver = getContentResolver();

        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

        Cursor cursor = contentResolver.query(uri, null, MediaStore.Audio.Media.DATA + " LIKE?", new String[]{"%.mp3%"}, null);


        if (cursor == null) {
            Toast.makeText(this, "Something went wrong!!!", Toast.LENGTH_SHORT).show();
        } else if (!cursor.moveToNext()) {
            Toast.makeText(this, "No Music Found", Toast.LENGTH_SHORT).show();
        } else {

            while (cursor.moveToNext())
//            for (int i=0; i<1; i++)
            {

                final String getMusicFileName = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.TITLE));
                final String getArtistName = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST));
                long cursorId = cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Media._ID));

//                Uri musicFileUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cursorId);

                String musicFile = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DATA));


                String getDuration = "00:00";

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    getDuration = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.AudioColumns.DURATION));
                }


                final MusicList musicList = new MusicList(getMusicFileName, getArtistName, getDuration, false, musicFile);
                musicLists.add(musicList);

            }
//            musicAdapter = new MusicAdapter(musicLists, MainActivity.this);
//            musicRecyclerView.setAdapter(musicAdapter);
        }
        cursor.close();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
    }
}