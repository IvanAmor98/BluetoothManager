package com.example.bluetooth;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.companion.BluetoothDeviceFilter;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    public static final int REQUEST_ENABLE_BT=1;
    ListView lvPairedDevices, lvAvailableDevices;
    Button btnSend, btnSearch;
    CheckBox chkEnabled, chkVisible;
    TextView deviceName;
    Set<BluetoothDevice> setPairedDevices;
    ArrayAdapter adapterPairedDevices, adapterAvailableDevices;
    ArrayList<BluetoothDevice> foundDevices = new ArrayList<>();
    ArrayList<String> foundDeviceNames = new ArrayList<>();
    ArrayList<String> deviceNames = new ArrayList<>();
    ArrayList<String> deviceAddresses = new ArrayList<>();
    BluetoothReceiver bluetoothReceiver;
    PairingReceiver pairingReceiver;
    BluetoothAdapter bluetoothAdapter;
    ConnectedThread connectedThread;

    public static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    public static final int MESSAGE_READ=0;
    public static final int MESSAGE_WRITE=1;
    public static final int CONNECTING=2;
    public static final int CONNECTED=3;
    public static final int NO_SOCKET_FOUND=4;


    String bluetooth_message="00";

    @SuppressLint("HandlerLeak")
    Handler mHandler= new Handler()
    {
        @Override
        public void handleMessage(Message msg_type) {
            super.handleMessage(msg_type);

            switch (msg_type.what){
                case MESSAGE_READ:
                    String receivedMsg = new String((byte[]) msg_type.obj);
                    if (!receivedMsg.equals("")) {
                        Toast.makeText(MainActivity.this, receivedMsg, Toast.LENGTH_LONG).show();
                    }
                    break;

                case MESSAGE_WRITE:
                    if(msg_type.obj!=null){
                        connectedThread=new ConnectedThread((BluetoothSocket)msg_type.obj);
                        connectedThread.write(bluetooth_message.getBytes());
                        connectedThread.start();
                    }
                    break;

                case CONNECTED:
                    Toast.makeText(getApplicationContext(),"Connected",Toast.LENGTH_SHORT).show();
                    break;

                case CONNECTING:
                    Toast.makeText(getApplicationContext(),"Connecting...",Toast.LENGTH_SHORT).show();
                    break;

                case NO_SOCKET_FOUND:
                    Toast.makeText(getApplicationContext(),"No socket found",Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        checkPermissions();
        registerReceivers();
        initializeBluetooth();
        initializeLayout();
        startConnectionListener();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(bluetoothReceiver);
        unregisterReceiver(pairingReceiver);
    }

    public void registerReceivers() {
        bluetoothReceiver = new BluetoothReceiver();
        bluetoothReceiver.setMainActivityHandler(this);
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        registerReceiver(bluetoothReceiver, filter);

        pairingReceiver = new PairingReceiver();
        pairingReceiver.setMainActivityHandler(this);
        filter = new IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST);
        registerReceiver(pairingReceiver, filter);
    }

    public void startConnectionListener()
    {
        AcceptThread acceptThread = new AcceptThread();
        acceptThread.start();
        Toast.makeText(getApplicationContext(),"Accepting connections",Toast.LENGTH_SHORT).show();
    }

    public void initializeLayout()
    {
        deviceName = findViewById(R.id.nombrebluetooth);
        deviceName.setText(bluetoothAdapter.getName());

        chkEnabled = findViewById(R.id.botonhabilitado);
        chkVisible = findViewById(R.id.botonvisible);

        btnSearch = findViewById(R.id.botonBusqueda);
        btnSend = findViewById(R.id.botonEnviar);

        chkEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (!bluetoothAdapter.isEnabled()) {
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableBtIntent, 1);
                }
            } else {
                bluetoothAdapter.disable();
            }
        });

        chkVisible.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                Intent changeVisibility = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
                startActivity(changeVisibility);
            }
        });

        if (bluetoothAdapter.isEnabled()) {
            chkEnabled.setChecked(true);
        } else {
            chkEnabled.setChecked(false);
        }

        btnSearch.setOnClickListener(v -> {
            if (!bluetoothAdapter.isDiscovering()) {
                foundDevices.clear();
                foundDeviceNames.clear();
                bluetoothAdapter.startDiscovery();
            } else {
                bluetoothAdapter.cancelDiscovery();
            }
        });

        btnSend.setOnClickListener(v -> {
            if (connectedThread != null) {
                connectedThread.write("Test message".getBytes());
            } else {
                Toast.makeText(MainActivity.this, "Can not send message without connected device", Toast.LENGTH_SHORT).show();
            }
        });

        lvAvailableDevices = findViewById(R.id.listadisponibles);
        adapterAvailableDevices = new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1, foundDeviceNames);
        lvAvailableDevices.setAdapter(adapterAvailableDevices);

        lvAvailableDevices.setOnItemClickListener((parent, view, position, id) -> {
            try {
                Method method = foundDevices.get(position).getClass().getMethod("createBond", (Class[]) null);
                method.invoke(foundDevices.get(position), (Object[]) null);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        lvPairedDevices = findViewById(R.id.listaemparejados);
        adapterPairedDevices = new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1, deviceNames);
        lvPairedDevices.setAdapter(adapterPairedDevices);

        lvPairedDevices.setOnItemClickListener((parent, view, position, id) -> {
            Object[] objects = setPairedDevices.toArray();
            BluetoothDevice device = (BluetoothDevice) objects[position];

            ConnectThread connectThread = new ConnectThread(device);
            connectThread.start();
        });
    }

    public void updateBondedDevices() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        setPairedDevices = bluetoothAdapter.getBondedDevices();
        deviceNames.clear();
        deviceAddresses.clear();

        if (setPairedDevices.size() > 0) {
            for (BluetoothDevice device : setPairedDevices) {
                Log.d("Device", device.getName());
                deviceNames.add(device.getName());
                deviceAddresses.add(device.getAddress());
            }
        }
        adapterPairedDevices.notifyDataSetChanged();
    }

    public void initializeBluetooth()
    {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            // Device doesn't support Bluetooth
            Toast.makeText(getApplicationContext(),"Your Device doesn't support bluetooth.",Toast.LENGTH_SHORT).show();
            finish();
        }

        else {
            setPairedDevices = bluetoothAdapter.getBondedDevices();

            if (setPairedDevices.size() > 0) {
                for (BluetoothDevice device : setPairedDevices) {
                    deviceNames.add(device.getName());
                    deviceAddresses.add(device.getAddress());
                }
            }
        }
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // If Android version M or newer:
            // Check if already has permissions
            if (checkSelfPermission(Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                // If not, asks for permissions
                requestPermissions(new String[] {
                        Manifest.permission.BLUETOOTH,
                        Manifest.permission.BLUETOOTH_ADMIN,
                        Manifest.permission.BLUETOOTH_PRIVILEGED
                }, 1);
            }
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // If not, asks for permissions
                requestPermissions(new String[] {
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                }, 1);
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_ADVERTISE
                }, 1);
            }
        }
    }


    public class AcceptThread extends Thread
    {
        private final BluetoothServerSocket serverSocket;

        public AcceptThread() {
            BluetoothServerSocket tmp = null;
            try {
                // MY_UUID is the app's UUID string, also used by the client code
                tmp = bluetoothAdapter.listenUsingRfcommWithServiceRecord("NAME",MY_UUID);
            } catch (IOException e) { }
            serverSocket = tmp;
        }

        public void run() {
            BluetoothSocket socket = null;
            // Keep listening until exception occurs or a socket is returned
            while (true) {
                try {
                    socket = serverSocket.accept();
                } catch (IOException e) {
                    break;
                }

                // If a connection was accepted
                if (socket != null)
                {
                    connectedThread = new ConnectedThread(socket);
                    connectedThread.start();
                    mHandler.obtainMessage(CONNECTED).sendToTarget();
                }
            }
        }
    }


    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            // Use a temporary object that is later assigned to mmSocket,
            // because mmSocket is final
            BluetoothSocket tmp = null;
            mmDevice = device;

            // Get a BluetoothSocket to connect with the given BluetoothDevice
            try {
                // MY_UUID is the app's UUID string, also used by the server code
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) { }
            mmSocket = tmp;
        }

        public void run() {
            // Cancel discovery because it will slow down the connection
            bluetoothAdapter.cancelDiscovery();

            try {
                // Connect the device through the socket. This will block
                // until it succeeds or throws an exception
                mHandler.obtainMessage(CONNECTING).sendToTarget();

                mmSocket.connect();
            } catch (IOException connectException) {
                // Unable to connect; close the socket and get out
                try {
                    mmSocket.close();
                } catch (IOException closeException) { }
                return;
            }

            // Do work to manage the connection (in a separate thread)
              bluetooth_message = "Connected";
              mHandler.obtainMessage(MESSAGE_WRITE,mmSocket).sendToTarget();
        }

        /** Will cancel an in-progress connection, and close the socket */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }
    }
    private class ConnectedThread extends Thread {

        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) { }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[1024];  // buffer store for the stream
            int bytes; // bytes returned from read()
            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);

                    // Send the obtained bytes to the UI activity
                    mHandler.obtainMessage(MESSAGE_READ, bytes, -1, buffer).sendToTarget();
                } catch (IOException e) {
                    break;
                }
            }
        }

        /* Call this from the main activity to send data to the remote device */
        public void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) { }
        }

        /* Call this from the main activity to shutdown the connection */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }
    }

}