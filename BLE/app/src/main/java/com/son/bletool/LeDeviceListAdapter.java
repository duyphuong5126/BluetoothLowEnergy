package com.son.bletool;

import android.bluetooth.BluetoothDevice;
import android.widget.BaseAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by phuong.nguyenhoangd on 2/10/2017.
 */

abstract class LeDeviceListAdapter extends BaseAdapter {
    private ArrayList<BluetoothDevice> mLeDevices;
    LeDeviceListAdapter(ArrayList<BluetoothDevice> bluetoothDevices) {
        if (bluetoothDevices != null) {
            mLeDevices = bluetoothDevices;
        }
    }

    @Override
    public int getCount() {
        if (mLeDevices != null) {
            return mLeDevices.size();
        }
        return 0;
    }

    @Override
    public BluetoothDevice getItem(int i) {
        if (mLeDevices != null) {
            return mLeDevices.get(i);
        }
        return null;
    }

    @Override
    public long getItemId(int i) {
        return i;
    }
}
