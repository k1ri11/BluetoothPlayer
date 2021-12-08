package com.example.bluetooth_player;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.AudioManager;
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
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.bluetooth_player.Adapters.MusicAdapter;
import com.example.bluetooth_player.Models.Music;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity implements SongChangeListener {


    private BluetoothAdapter mAdapter;
    private BluetoothService mBluetoothService;
    private boolean permissions = false;
    private boolean isServer = true;
    private int statusDisc = 0;     //0 = start discovering, 1 = cancel discovering

    private ArrayList<String> devicesInfo = new ArrayList<>();
    private ArrayList<BluetoothDevice> bluetoothDevices = new ArrayList<>();
    private List<Music> musicList = new ArrayList<>();
    private ListView lvDevices;
    private BluetoothDevice selectedDevice;

    private MediaPlayer mediaPlayer = new MediaPlayer();
    private String tmpDeviceName = "";//    колхоз
    private RecyclerView musicRV;
    private MusicAdapter musicAdapter;
    private TextView endTime, startTime;
    private boolean isPlaying = false;
    private SeekBar playerSeekBar;
    private ImageView playPauseImg;
    private Timer timer;
    private int currentSongListPosition = 0;
    private TextView connectedDevice;
    private Button disconnectBtn;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        checkCoarseLocationPermission();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            getMusicFiles();
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 11);
            } else {
                getMusicFiles();
            }
        }
        Log.d(Constans.TAG, "music files found: " + musicList.size());

        final Handler mHandler = new Handler(getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == Constans.BYTES_READ) {
                    byte[] readBuf = (byte[]) msg.obj; //буфер считанный из потока
                    int bytes = msg.arg1;
                    Toast.makeText(getApplicationContext(), "Файл получен", Toast.LENGTH_SHORT).show();
                    int seconds = parseSeconds(readBuf);
                    FileInputStream fis = mBluetoothService.WriteByteArrayToFile(readBuf);
                    onChanged2(fis, seconds);
                }
            }
        };

        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mBluetoothService = new BluetoothService(this, this.getApplicationContext(), mAdapter, permissions, mHandler);
        //делаем устройство видимым для других
        Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 100);
        startActivity(discoverableIntent);

        registerReceiver(mReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
        registerReceiver(mReceiver, new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_STARTED));
        registerReceiver(mReceiver, new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED));

        Button searchBtn = findViewById(R.id.bluetooth_btn);
        searchBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mBluetoothService.startSearch();
            }
        });

        mBluetoothService.checkBluetoothState();
        mBluetoothService.startServer();

        playerSeekBar = findViewById(R.id.playerSeekBar);
        playPauseImg = findViewById(R.id.playPauseImg);
        startTime = findViewById(R.id.startTime);
        endTime = findViewById(R.id.endTime);
        connectedDevice = findViewById(R.id.connected_device);
        disconnectBtn = findViewById(R.id.disconnect_btn);

        disconnectBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                disconnect();
            }
        });

        //recycler
        musicRV = findViewById(R.id.music_rv);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);
        musicRV.setLayoutManager(layoutManager);
        musicAdapter = new MusicAdapter(this, musicList);
        musicRV.setAdapter(musicAdapter);

        final ImageView nextBtn = findViewById(R.id.nextBtn);
        final ImageView prevBtn = findViewById(R.id.previousBtn);
        nextBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                int nextSongListPosition = currentSongListPosition + 1;

                if (nextSongListPosition >= musicList.size()) {
                    nextSongListPosition = 0;
                }

                musicList.get(currentSongListPosition).setPlaying(false);
                musicList.get(nextSongListPosition).setPlaying(true);

                musicAdapter.updateList(musicList);

                musicRV.scrollToPosition(nextSongListPosition);

                onChanged(nextSongListPosition);
            }
        });

        prevBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                int prevSongListPosition = currentSongListPosition - 1;

                if (prevSongListPosition < 0) {
                    prevSongListPosition = musicList.size() - 1; // play last song
                }

                musicList.get(currentSongListPosition).setPlaying(false);
                musicList.get(prevSongListPosition).setPlaying(true);

                musicAdapter.updateList(musicList);

                musicRV.scrollToPosition(prevSongListPosition);

                onChanged(prevSongListPosition);

            }
        });

        final CardView playPauseCard = findViewById(R.id.playPauseCard);
        playPauseCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (isPlaying) {
                    isPlaying = false;
                    mediaPlayer.pause();
                    playPauseImg.setImageResource(R.drawable.play_icon);
                } else {
                    isPlaying = true;
                    mediaPlayer.start();
                    playPauseImg.setImageResource(R.drawable.pause_btn);
                }
            }
        });

        playerSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    if (isPlaying) {
                        mediaPlayer.seekTo(progress);
                    } else {
                        mediaPlayer.seekTo(0);
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

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
                        Toast.makeText(getApplicationContext(), "подключение к " + selectedDevice.getName(), Toast.LENGTH_SHORT).show();
                    }
                });
            }

            if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
                //report user
                Log.d(Constans.TAG, "Сканирование запущено");
            }

            if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                //change button back to "Start"
                statusDisc = 0;
//                final Button discovery_btn = findViewById(R.id.bluetooth_btn);
//                discovery_btn.setText("Start");
                Log.d(Constans.TAG, "Finished");
                Toast.makeText(getApplicationContext(), "Сканирование завершено", Toast.LENGTH_SHORT).show();
            }

            if (mAdapter.ACTION_STATE_CHANGED.equals(action)) {
                final int extra = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
                if (extra == (BluetoothAdapter.STATE_ON)) {
                    if (mAdapter.isDiscovering()) {
                        mAdapter.cancelDiscovery();
                    }
                    Boolean b = mAdapter.startDiscovery();
                    Toast.makeText(getApplicationContext(), "Сканирование запущено" + b, Toast.LENGTH_SHORT).show();
                }
            }
        }
    };

    public void startBluetooth() {
        Intent enabledIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enabledIntent, Constans.REQUEST_ENABLE_BLUETOOTH);
        mBluetoothService.setBluetoothState(1);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == Constans.REQUEST_ENABLE_BLUETOOTH) {
            mBluetoothService.checkBluetoothState();
        }
    }

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

    private void getMusicFiles() {

        ContentResolver contentResolver = getContentResolver();

        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

        Cursor cursor = contentResolver.query(uri, null, MediaStore.Audio.Media.DATA + " LIKE?", new String[]{"%.mp3%"}, null);


        if (cursor == null) {
            Toast.makeText(this, "Something went wrong!!!", Toast.LENGTH_SHORT).show();
        } else if (!cursor.moveToNext()) {
            Toast.makeText(this, "No Music Found", Toast.LENGTH_SHORT).show();
        } else {

            while (cursor.moveToNext()) {

                final String getMusicFileName = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.TITLE));
                final String getArtistName = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST));
                long cursorId = cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Media._ID));
//                Uri musicFileUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cursorId);
                String musicFile = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DATA));
                String getDuration = "00:00";
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    getDuration = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.AudioColumns.DURATION));
                }
                final Music music = new Music(getMusicFileName, getArtistName, getDuration, false, musicFile);
                musicList.add(music);
            }
        }
        cursor.close();
    }

    public void updateState(boolean isServer){
        this.isServer = isServer;
        if (!isServer){
            connectedDevice.setText(selectedDevice.getName());
        }
        else{
            connectedDevice.setText("");
        }
    }

    public void disconnect(){
        mBluetoothService.getmConnectedThread().cancel();
        selectedDevice = null;
    }

    public void startBroadcast() {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                byte[] bytes = null;
                try {
                    bytes = mBluetoothService.readFileToBytes(musicList.get(currentSongListPosition).getMusicFile());
                } catch (IOException e) {
                    e.printStackTrace();
                }
                int subArraySize = 990;
                int length = bytes.length + 15;
                String string = String.valueOf(length); //any type: int, double, float...
                byte[] bytes1 = string.getBytes();
                Log.d(Constans.TAG, "run: byteLength " + string);

                mBluetoothService.getmConnectedThread().write(bytes1);
                try {
                    mBluetoothService.getmConnectedThread().sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                byte[] tempArray;
                Log.d(Constans.TAG, "run: start time");
                for (int i = 0; i < bytes.length; i += subArraySize) {
                    tempArray = Arrays.copyOfRange(bytes, i, Math.min(bytes.length, i + subArraySize));
                    mBluetoothService.getmConnectedThread().write(tempArray);
                }
                Integer seconds = mediaPlayer.getCurrentPosition();
                String string2 = String.valueOf(seconds);
                byte[] arraySeconds = new byte[15];
                arraySeconds = string2.getBytes();
                Log.d(Constans.TAG, "run: отправили" + seconds + "\n" + Arrays.toString(arraySeconds));

                mBluetoothService.getmConnectedThread().write(arraySeconds);
            }
        };
        Thread broadcast = new Thread(runnable);
        broadcast.start();
    }

    private int parseSeconds(byte[] readBuf) {
        byte[] arraySeconds = new byte[15];
        System.arraycopy(readBuf, readBuf.length - 15, arraySeconds, 0, 15);

        int counter = 0;
        for (int i = 0; i < 15; i++){
            if(arraySeconds[i] != 0){
                counter++;
            }
        }
        byte[] tmpBytes = new byte[counter];
        int j =0;
        for (int i = 0; i < 15; i++){
            if(arraySeconds[i] != 0){
                counter++;
                tmpBytes[j] = arraySeconds[i];
                j++;
            }
        }

        String secondsString = new String(tmpBytes, StandardCharsets.UTF_8);
        return Integer.parseInt(secondsString);
    }

    @Override
    public void onChanged(int position) {

        currentSongListPosition = position;

        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            mediaPlayer.reset();
        }

        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    mediaPlayer.setDataSource(MainActivity.this, Uri.parse(musicList.get(position).getMusicFile()));
                    mediaPlayer.prepare();
                } catch (IOException e) {
                    e.printStackTrace();
                    Toast.makeText(MainActivity.this, "Unable to play track", Toast.LENGTH_SHORT).show();
                }
            }
        }).start();

        mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                final int getTotalDuration = mp.getDuration();

                String generateDuration = String.format(Locale.getDefault(), "%02d:%02d",
                        TimeUnit.MILLISECONDS.toMinutes(getTotalDuration),
                        TimeUnit.MILLISECONDS.toSeconds(getTotalDuration) -
                                TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(getTotalDuration)));

                endTime.setText(generateDuration);
                isPlaying = true;

                mp.start();

                playerSeekBar.setMax(getTotalDuration);

                playPauseImg.setImageResource(R.drawable.pause_btn);
            }
        });

        timer = new Timer();

        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {

                        final int getCurrentDuration = mediaPlayer.getCurrentPosition();

                        String generateDuration = String.format(Locale.getDefault(), "%02d:%02d",
                                TimeUnit.MILLISECONDS.toMinutes(getCurrentDuration),
                                TimeUnit.MILLISECONDS.toSeconds(getCurrentDuration) -
                                        TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(getCurrentDuration)));

                        playerSeekBar.setProgress(getCurrentDuration);

                        startTime.setText(generateDuration);
                    }
                });

            }
        }, 1000, 1000);


        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                mediaPlayer.reset();

                timer.purge();
                timer.cancel();

                isPlaying = false;

                playPauseImg.setImageResource(R.drawable.play_icon);

                playerSeekBar.setProgress(0);
            }
        });
    }

    public void onChanged2(FileInputStream fis, int seconds) {

        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
        mediaPlayer.reset();

        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    mediaPlayer.setDataSource(fis.getFD());
                    mediaPlayer.prepare();
                } catch (IOException e) {
                    e.printStackTrace();
                    Toast.makeText(MainActivity.this, "Unable to play track", Toast.LENGTH_SHORT).show();
                }
            }
        }).start();
        mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                final int getTotalDuration = mp.getDuration();

                String generateDuration = String.format(Locale.getDefault(), "%02d:%02d",
                        TimeUnit.MILLISECONDS.toMinutes(getTotalDuration),
                        TimeUnit.MILLISECONDS.toSeconds(getTotalDuration) -
                                TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(getTotalDuration)));

                endTime.setText(generateDuration);
                isPlaying = true;
                mp.seekTo(seconds);
                mp.start();

                playerSeekBar.setMax(getTotalDuration);

                playPauseImg.setImageResource(R.drawable.pause_btn);
            }
        });

        timer = new Timer();

        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {

                        final int getCurrentDuration = mediaPlayer.getCurrentPosition();

                        String generateDuration = String.format(Locale.getDefault(), "%02d:%02d",
                                TimeUnit.MILLISECONDS.toMinutes(getCurrentDuration),
                                TimeUnit.MILLISECONDS.toSeconds(getCurrentDuration) -
                                        TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(getCurrentDuration)));

                        playerSeekBar.setProgress(getCurrentDuration);

                        startTime.setText(generateDuration);
                    }
                });

            }
        }, 1000, 1000);


        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                mediaPlayer.reset();

                timer.purge();
                timer.cancel();

                isPlaying = false;

                playPauseImg.setImageResource(R.drawable.play_icon);

                playerSeekBar.setProgress(0);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
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