package com.example.bluetooth_player;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class BluetoothService {


    private int bluetoothState = 0;
    private final Context context;
    private boolean permissions;
    byte[] information = {1, 1, 0, 1, 0, 1, 0, 0, 0, 1};

    private final MainActivity activity;//    колхоз

    // Member fields
    private final BluetoothAdapter mAdapter;
    private boolean isServer = true;
    private AcceptThread mSecureAcceptThread;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private final Handler mHandler;


    public BluetoothService(MainActivity activity, Context context, BluetoothAdapter mAdapter, boolean permissions, Handler mHandler) {
        this.mAdapter = mAdapter;
        this.activity = activity;
        this.context = context;
        this.permissions = permissions;
        this.mHandler = mHandler;
    }

    public BluetoothAdapter getAdapter() {
        return mAdapter;
    }

    public ConnectedThread getmConnectedThread() {
        return mConnectedThread;
    }

    private class AcceptThread extends Thread {
        private final BluetoothServerSocket mServerSocket;

        public AcceptThread() {
            // используем вспомогательную переменную, которую в дальнейшем
            // свяжем с mmServerSocket,
            BluetoothServerSocket tmp = null;
            try {
                // MY_UUID это UUID нашего приложения, это же значение
                // используется в клиентском приложении
                tmp = mAdapter.listenUsingRfcommWithServiceRecord(Constans.NAME, Constans.MY_UUID);
            } catch (IOException e) {
                e.printStackTrace();
            }
            mServerSocket = tmp;
        }

        public void run() {
            BluetoothSocket socket = null;
            // ждем пока не произойдет ошибка или не
            // будет возвращен сокет
            while (true) {
                try {
                    socket = mServerSocket.accept();
                } catch (IOException e) {
                    break;
                }
                // если соединение было подтверждено
                if (socket != null) {
                    // управлчем соединением (в отдельном потоке)
                    manageConnectedSocket(socket);
                    try {
                        mServerSocket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                }
            }
        }

        public void cancel() {
            try {
                mServerSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    private void manageConnectedSocket(BluetoothSocket socket) {
        Log.d(Constans.TAG, "manageConnectedSocket: success");
        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();
    }

    private class ConnectThread extends Thread {
        private final BluetoothSocket mSocket;
        private final BluetoothDevice mDevice;

        public ConnectThread(BluetoothDevice device) {
            // используем вспомогательную переменную, которую в дальнейшем
            // свяжем с mmSocket,
            BluetoothSocket tmp = null;
            mDevice = device;

            // получаем BluetoothSocket чтобы соединиться с BluetoothDevice
            try {
                // MY_UUID это UUID, который используется и в сервере
                tmp = device.createRfcommSocketToServiceRecord(Constans.MY_UUID);
            } catch (IOException e) {
                e.printStackTrace();
            }
            mSocket = tmp;

        }

        public void run() {
            // Отменяем сканирование, поскольку оно тормозит соединение
            mAdapter.cancelDiscovery();

            try {
                // Соединяемся с устройством через сокет.
                // Метод блокирует выполнение программы до
                // установки соединения или возникновения ошибки
                mSocket.connect();

            } catch (IOException connectException) {
                // Невозможно соединиться. Закрываем сокет и выходим.
                Log.d(Constans.TAG, connectException.toString());
                try {
                    mSocket.close();
                } catch (IOException closeException) {
                    closeException.printStackTrace();
                }
                return;
            }
            // управлчем соединением (в отдельном потоке)
            manageConnectedSocket(mSocket);
        }

        /**
         * отмена ожидания сокета
         */
        public void cancel() {
            try {
                mSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    public class ConnectedThread extends Thread {
        private final BluetoothSocket mSocket;
        private final InputStream mInStream;
        private final OutputStream mOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            mSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Получить входящий и исходящий потоки данных
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }

            mInStream = tmpIn;
            mOutStream = tmpOut;
        }

        public void run() {

            byte[] buffer = null;
            byte[] data;
            int numberOfBytes = 0;
            int numbers;
            int index=0;
            String numberofBytes;
            boolean flag = true;
            while (true) {

                if(flag)
                {
                    try {
                        byte[] temp = new byte[mInStream.available()];
                        if(mInStream.read(temp)>0)
                        {
                            numberofBytes = new String(temp, StandardCharsets.UTF_8);
                            Log.d(Constans.TAG, "run:  " + numberofBytes);
                            numberOfBytes=Integer.parseInt(numberofBytes);
                            buffer=new byte[numberOfBytes];
                            flag=false;
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }else{
                    try {
                        data = new byte[mInStream.available()];
                        numbers = mInStream.read(data);  // почитать про read, и оптимизировать 2 след строки

                        System.arraycopy(data,0,buffer,index,numbers);
                        index=index+numbers;

                        if(index == numberOfBytes)
                        {
                            Message msg = mHandler.obtainMessage(Constans.MESSAGE_READ, numberOfBytes, -1, buffer);
                            mHandler.sendMessage(msg);
                            flag = true;
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }



//                // Прослушиваем InputStream пока не произойдет исключение
//                try {
//                    // читаем из InputStream
//                    bytesLength = mInStream.read(buffer);
//                    if (bytesLength == -1) Log.d("Bluetooth-check", "no bytes");
//                    else
//                        Log.d("Bluetooth-check", "have bytes:" + bytesLength);
////                      посылаем прочитанные байты главной деятельности
//                    Message msg = mHandler.obtainMessage(Constans.MESSAGE_READ, bytesLength, -1, buffer);
//                    mHandler.sendMessage(msg);
//                } catch (IOException e) {
//                    e.printStackTrace();
//                    break;
//                }
            }
        }

        /* Вызываем этот метод из главной деятельности, чтобы отправить данные
        удаленному устройству */
        public void write(byte[] bytes) {
            try {
                mOutStream.write(bytes);
            } catch (
                    IOException e) {
                e.printStackTrace();
            }
        }

        /* Вызываем этот метод из главной деятельности,
    чтобы разорвать соединение */
        public void cancel() {
            try {
                mSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void startServer() {
        AcceptThread acceptThread = new AcceptThread();
        Log.d(Constans.TAG, "run: start server");
        acceptThread.start();
        //TODO: позакрывать сокеты
    }

    public void startConnection(BluetoothDevice selectedDevice) {

        ConnectThread connectThread = new ConnectThread(selectedDevice);
        Log.d(Constans.TAG, "run: connectThread");
        isServer = false;
        connectThread.start();
        //TODO: позакрывать сокеты
    }


//  bluetoothState = 0 Bluetooth not available
//  bluetoothState = 1 Bluetooth is turned off
//  bluetoothState = 2 Bluetooth is turned on but not discovering
//  bluetoothState = 3 Bluetooth is discovering

    public int checkBluetoothState() {
        if (mAdapter == null) {
            Toast.makeText(context, "Bluetooth not available", Toast.LENGTH_SHORT).show();
        } else {
            if (mAdapter.isEnabled()) {
                if (mAdapter.isDiscovering()) {
                    bluetoothState = 3;
                    Toast.makeText(context, "Device is discovering...", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(context, "Bluetooth is enabled", Toast.LENGTH_SHORT).show();
                    bluetoothState = 2;
                }
            } else {
                Toast.makeText(context, "You need to enabled bluetooth", Toast.LENGTH_SHORT).show();
                bluetoothState = 1;
            }
        }
        return bluetoothState;
    }

    public void startSearch() {

        switch (bluetoothState) {
            case 1:
                activity.startBluetooth();
                break;

            case 2:
                if (permissions) {
                    boolean result = mAdapter.startDiscovery(); //start discovering and show result of function
                    Toast.makeText(context, "Start discovery result: " + result, Toast.LENGTH_SHORT).show();
                    Log.d(Constans.TAG, "Start discovery: " + result);
//                    bluetoothBtn.setText("Stop"); в ui выставляем кнопку на stop
                    bluetoothState = 3;
                }
                break;
            case 3:
                Log.d(Constans.TAG, "Stop");
                mAdapter.cancelDiscovery();
                bluetoothState = 2;
//                bluetoothBtn.setText("Start"); в ui выставляем кнопку на start
                break;
            default:
                Toast.makeText(context, "Switch failed", Toast.LENGTH_SHORT).show();
        }
    }

    public byte[] readFileToBytes(String path) throws IOException {

//      выбираем путь трек из листа который нужно проиграть, позже сделать через recycler view
        File file = new File(path);
        byte[] bytes = new byte[(int) file.length()];

        try (FileInputStream fis = new FileInputStream(file)) {
            //read file into bytes[]
            fis.read(bytes);
        }
        return bytes;
    }

    public FileInputStream WriteByteArrayToFile(byte[] mp3SoundByteArray) {
        try {
            File Mytemp = File.createTempFile("TCL", "mp3", context.getCacheDir());
            Mytemp.deleteOnExit();
            FileOutputStream fos = new FileOutputStream(Mytemp);
            fos.write(mp3SoundByteArray);
            fos.close();
            FileInputStream MyFile = new FileInputStream(Mytemp);
            return MyFile;
        } catch (IOException ex) {
            String s = ex.toString();
            ex.printStackTrace();
        }
        return null;
    }

//    public BroadcastReceiver getReceiver(){
//        return mReceiver;
//    }


//    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
//        public void onReceive(Context context, Intent intent) {
//            String action = intent.getAction();
//            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
//                // Discovery has found a device. Get the BluetoothDevice
//                // object and its info from the Intent.
//                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
//                String deviceName = device.getName();
//                String deviceHardwareAddress = device.getAddress(); // MAC address
//
//
////                Вызываем update list из адаптера recyclerView, передаемему всю инфу а он внутри сам ее add
////                bluetoothDevices.add(device);
////                devicesInfo.add(device.getName() + "\n" + device.getAddress());
////
////                это уйдет в фрагмент
////                ArrayAdapter devicesAdapter = new ArrayAdapter(getApplicationContext(), android.R.layout.simple_list_item_1, devicesInfo);
////                lvDevices = findViewById(R.id.lv_devices);
////                lvDevices.setAdapter(devicesAdapter);
////                checkCoarseLocationPermission();
//
////                полностью уйдет в адаптер/фрагмент
////                lvDevices.setOnItemClickListener(new AdapterView.OnItemClickListener() {
////                    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
////                        selectedDevice = bluetoothDevices.get((int) id);
////
////                        mBluetoothService.startConnection(selectedDevice);
////                    }
////                });
//            }
//
//            if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
//                //report user
////                Log.d(TAG, "Started");
////                Toast.makeText(getApplicationContext(), "STARTED", Toast.LENGTH_SHORT).show();
//            }
//
//            if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
//                //change button back to "Start"
////                statusDisc = 0;
////                final Button test = findViewById(R.id.testbutton);
////                test.setText("Start");
//                //report user
////                Log.d(TAG, "Finished");
////                Toast.makeText(getApplicationContext(), "FINISHED", Toast.LENGTH_SHORT).show();
//            }
//
//            if (mAdapter.ACTION_STATE_CHANGED.equals(action)) {
//                final int extra = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
//                if (extra == (BluetoothAdapter.STATE_ON)) {
//                    if (mAdapter.isDiscovering()) {
//                        mAdapter.cancelDiscovery();
//                    }
//                    Boolean b = mAdapter.startDiscovery();
//                    Toast.makeText(getApplicationContext(), "Start discovery" + b, Toast.LENGTH_SHORT).show();
//                }
//            }
//        }
//    };
}