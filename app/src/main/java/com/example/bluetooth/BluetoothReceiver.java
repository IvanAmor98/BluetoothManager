package com.example.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public class BluetoothReceiver extends BroadcastReceiver {

    MainActivity main = null;
    public void setMainActivityHandler(MainActivity main){
        this.main = main;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        switch (action) {
            case BluetoothAdapter.ACTION_DISCOVERY_STARTED:
                Toast.makeText(main, "Searching", Toast.LENGTH_SHORT).show();
                break;
            case BluetoothAdapter.ACTION_DISCOVERY_FINISHED:
                Toast.makeText(main, "Stopped  searching", Toast.LENGTH_SHORT).show();
                break;
            case BluetoothDevice.ACTION_FOUND:
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (!main.foundDevices.contains(device)) {
                    main.foundDevices.add(device);
                    main.foundDeviceNames.add(device.getName() + ": " + device.getAddress());
                    main.adapterAvailableDevices.notifyDataSetChanged();
                }
                break;
            case BluetoothDevice.ACTION_BOND_STATE_CHANGED:
                main.updateBondedDevices();
                break;
        }
    }


}
