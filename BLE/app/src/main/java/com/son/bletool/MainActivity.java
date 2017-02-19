package com.son.bletool;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class MainActivity extends Activity implements View.OnClickListener {
    private static final String TAG = "Bluetooth LE";
    private static final String PASSWORD = "password";

    private boolean isFromLollipop = false;
    private BluetoothAdapter mBluetoothAdapter;
    private boolean mScanning;
    private Handler mHandler;
    private BluetoothLeScanner mBluetoothLeScanner;

    private static final int REQUEST_ENABLE_BT = 1;
    // Stops scanning after SCAN_PERIOD seconds.
    private static final long SCAN_PERIOD = 10000;
    private LeDeviceListAdapter mDeviceAdapter;
    private ArrayList<BluetoothDevice> mDeviceList;

    private LinearLayout mLayoutProgress;

    private EditText mEdtPassword;
    private View mLayoutPassword;

    private View mTextNoDevice;
    private BluetoothDevice mCurrentDevice;
    private boolean isActivatedDevice;

    private View mLayoutControl;
    private TextView mTxtDeviceInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().setStatusBarColor(Color.parseColor("#FFCC0033"));

        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager != null) {
            mBluetoothAdapter = bluetoothManager.getAdapter();
        }
        if (mBluetoothAdapter != null) {
            mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
        }
        mHandler = new Handler();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            isFromLollipop = true;
        }
        findViewById(R.id.buttonRefresh).setOnClickListener(this);
        mDeviceList = new ArrayList<>();
        final LayoutInflater inflater = getLayoutInflater();
        mDeviceAdapter = new LeDeviceListAdapter(mDeviceList) {
            @SuppressLint("InflateParams")
            @Override
            public View getView(int i, View view, ViewGroup viewGroup) {
                if (view == null) {
                    view = inflater.inflate(R.layout.scanned_device, null);
                }
                BluetoothDevice bluetoothDevice = getItem(i);
                if (bluetoothDevice != null) {
                    TextView textName = (TextView) view.findViewById(R.id.deviceName);
                    textName.setText(bluetoothDevice.getName());
                    TextView textAddress = (TextView) view.findViewById(R.id.deviceAddress);
                    textAddress.setText(bluetoothDevice.getAddress());
                    ImageView imgChecked = (ImageView) view.findViewById(R.id.imgChecked);
                    ImageView imgLocked = (ImageView) view.findViewById(R.id.imgLocked);
                }
                return view;
            }
        };
        ListView listDevices = (ListView) findViewById(R.id.listDevices);
        listDevices.setAdapter(mDeviceAdapter);
        mLayoutPassword = findViewById(R.id.layoutPassword);
        listDevices.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                mLayoutPassword.setVisibility(View.VISIBLE);
                mCurrentDevice = mDeviceList.get(i);
                isActivatedDevice = false;
                if (mCurrentDevice != null && mTxtDeviceInfo != null) {
                    mTxtDeviceInfo.setText(mCurrentDevice.toString());
                }
            }
        });
        findViewById(R.id.buttonSendPassword).setOnClickListener(this);
        mEdtPassword = (EditText) findViewById(R.id.edtPassword);
        mLayoutProgress = (LinearLayout) findViewById(R.id.layoutProgress);
        mTextNoDevice = findViewById(R.id.textNoDevice);
        mTextNoDevice.setVisibility(mDeviceList.isEmpty() ? View.VISIBLE : View.GONE);

        mLayoutControl = findViewById(R.id.layoutControl);
        findViewById(R.id.buttonOpen).setOnClickListener(this);
        findViewById(R.id.buttonClose).setOnClickListener(this);

        mTxtDeviceInfo = (TextView) findViewById(R.id.txtDeviceInfo);
        findViewById(R.id.txtClose).setOnClickListener(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        boolean mustEnable = false;
        if (mBluetoothAdapter == null) {
            mustEnable = true;
        } else {
            if (mBluetoothAdapter.isEnabled()) {
                mustEnable = true;
            }
        }
        if (mustEnable) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
    }

    private void detectNewDevice(BluetoothDevice device) {
        if (device != null) {
            if (mDeviceList != null) {
                mDeviceList.add(device);
                mDeviceAdapter.notifyDataSetChanged();
            }
        }
    }

    //-----------------------------Before lollipop version-----------------------------
    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(BluetoothDevice bluetoothDevice, int i, byte[] bytes) {
            Toast.makeText(MainActivity.this, "Scanning done", Toast.LENGTH_SHORT).show();
            if (bluetoothDevice != null) {
                Log.d(TAG, "Scan result info: " + bluetoothDevice.toString());
                detectNewDevice(bluetoothDevice);
            }
            mLayoutProgress.setVisibility(View.GONE);
            mTextNoDevice.setVisibility(mDeviceList.isEmpty() ? View.VISIBLE : View.GONE);
        }
    };

    private void scanLeDevice(boolean enable) {
        if (enable) {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "No device found", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }, SCAN_PERIOD);

            mScanning = true;
            mBluetoothAdapter.startLeScan(mLeScanCallback);
        } else {
            mScanning = false;
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }
    }

    //-----------------------------Lollipop version-----------------------------
    private ScanCallback mNewLeScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            Toast.makeText(MainActivity.this, "Scanning done", Toast.LENGTH_SHORT).show();
            BluetoothDevice bluetoothDevice = result.getDevice();
            if (bluetoothDevice != null) {
                Log.d(TAG, "Scan result info: " + bluetoothDevice.toString());
                detectNewDevice(bluetoothDevice);
            }
            mLayoutProgress.setVisibility(View.GONE);
            mTextNoDevice.setVisibility(mDeviceList.isEmpty() ? View.VISIBLE : View.GONE);
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Toast.makeText(MainActivity.this, "Scanning error", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Scan result error: " + errorCode);
            mLayoutProgress.setVisibility(View.GONE);
            mTextNoDevice.setVisibility(mDeviceList.isEmpty() ? View.VISIBLE : View.GONE);
        }
    };

    private void scanLeDeviceFromLollipop(boolean enable) {
        if (mBluetoothLeScanner != null) {
            if (enable) {
                mHandler.postDelayed(new Runnable() {
                    public void run() {
                        mLayoutProgress.setVisibility(View.GONE);
                        mTextNoDevice.setVisibility(mDeviceList.isEmpty() ? View.VISIBLE : View.GONE);
                        mScanning = false;
                        mBluetoothLeScanner.stopScan(mNewLeScanCallback);
                    }
                }, SCAN_PERIOD);

                mScanning = true;
                mBluetoothLeScanner.startScan(mNewLeScanCallback);
            } else {
                mScanning = false;
                mBluetoothLeScanner.stopScan(mNewLeScanCallback);
            }
        }
    }

    //-----------------------------Gatt-----------------------------
    public void connectToDevice(BluetoothDevice device) {
        if (mGatt == null) {
            mGatt = device.connectGatt(this, false, mGattCallback);
            scanLeDevice(false);// will stop after first device detection
        }
        mWriteCharacteristic = null;
        mLayoutControl.setVisibility(View.VISIBLE);
    }

    private BluetoothGatt mGatt;
    private BluetoothGattCharacteristic mWriteCharacteristic, mRemoteCharacteristic;
    private BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            switch (newState) {
                case BluetoothProfile.STATE_CONNECTED:
                    Log.i("gattCallback", "STATE_CONNECTED");
                    gatt.discoverServices();
                    mLayoutControl.setVisibility(View.VISIBLE);
                    break;
                case BluetoothProfile.STATE_DISCONNECTED:
                    Log.e("gattCallback", "STATE_DISCONNECTED");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mLayoutControl.setVisibility(View.GONE);
                        }
                    });
                    break;
                default:
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mLayoutControl.setVisibility(View.GONE);
                        }
                    });
                    Log.e("gattCallback", "STATE_OTHER");
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                List<BluetoothGattService> services = gatt.getServices();
                if (services != null) {
                    if (services.size() >= 0) {
                        for (int i = 0; i < services.size(); i++) {
                            BluetoothGattService service = services.get(i);
                            /*List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
                            if (characteristics != null) {
                                if (characteristics.size() > 0) {
                                    for (int j = 0; j < characteristics.size() && writeCharacteristic == null; j++) {
                                        BluetoothGattCharacteristic characteristic = characteristics.get(j);
                                        int charaProp = characteristic.getProperties();
                                        if (((charaProp & BluetoothGattCharacteristic.PROPERTY_WRITE) |
                                                (charaProp & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) > 0) {
                                            writeCharacteristic = characteristic;
                                        }
                                    }
                                }
                            }*/
                            UUID uuidWrite = UUID.fromString("f000aa64-0451-4000-b000-000000000000");
                            if (service.getUuid().compareTo(uuidWrite) == 0) {
                                getCharacteristicsFromService(service);
                            }
                        }
                    }
                }
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            gatt.disconnect();
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
        }
    };

    private void getCharacteristicsFromService(BluetoothGattService service ) {
        List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
        if (characteristics != null) {
            if (characteristics.size() > 0) {
                for (int j = 0; j < characteristics.size(); j++) {
                    BluetoothGattCharacteristic characteristic = characteristics.get(j);
                    UUID uuidWrite = UUID.fromString("f000aa65-0451-4000-b000-000000000000");
                    UUID uuidRemote = UUID.fromString("f000aa66-0451-4000-b000-000000000000");
                    if (characteristic.getUuid().compareTo(uuidWrite) == 0) {
                        mWriteCharacteristic = characteristic;
                    } else {
                        if (characteristic.getUuid().compareTo(uuidRemote) == 0) {
                            mRemoteCharacteristic = characteristic;
                        }
                    }

                }
            }
        }
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.edtPassword || id == R.id.buttonSendPassword || id == R.id.layoutPassword) {
            mLayoutPassword.setVisibility(View.VISIBLE);
        } else {
            mLayoutPassword.setVisibility(View.GONE);
        }
        switch (id) {
            case R.id.buttonRefresh:
                if (mDeviceList != null) {
                    mDeviceList.clear();
                }
                if (mDeviceAdapter != null) {
                    mDeviceAdapter.notifyDataSetChanged();
                }
                mLayoutProgress.setVisibility(View.VISIBLE);
                mTextNoDevice.setVisibility(View.GONE);
                mLayoutControl.setVisibility(View.GONE);
                if (isFromLollipop) {
                    scanLeDeviceFromLollipop(true);
                } else {
                    scanLeDevice(true);
                }
                break;
            case R.id.buttonSendPassword:
                String password = mEdtPassword.getText().toString();
                isActivatedDevice = password.equals(PASSWORD);
                Log.d(TAG, "Password " + (isActivatedDevice ? "Correct" : "Wrong"));
                if (mCurrentDevice != null) {
                    if (isActivatedDevice) {
                        connectToDevice(mCurrentDevice);
                    }
                }
                break;
            case R.id.buttonOpen:
                if (mWriteCharacteristic != null && mRemoteCharacteristic != null) {
                    //--------------------------Enter remote mode--------------------------
                    mRemoteCharacteristic.setValue(0x01, BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                    mGatt.writeCharacteristic(mRemoteCharacteristic);

                    //--------------------------Write value--------------------------
                    mWriteCharacteristic.setValue(0x5E, BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                    mGatt.writeCharacteristic(mWriteCharacteristic);
                }
                break;
            case R.id.buttonClose:
                if (mWriteCharacteristic != null && mRemoteCharacteristic != null) {
                    //--------------------------Enter remote mode--------------------------
                    mRemoteCharacteristic.setValue(0x01, BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                    mGatt.writeCharacteristic(mRemoteCharacteristic);

                    //--------------------------Write value--------------------------
                    mWriteCharacteristic.setValue(0x5D, BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                    mGatt.writeCharacteristic(mWriteCharacteristic);
                }
                break;
            case R.id.txtClose:
                mLayoutControl.setVisibility(View.GONE);
                break;
            default:
                break;
        }
    }

    public void writeCustomCharacteristic(int value) {
        if (mBluetoothAdapter == null || mGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        /*check if the service is available on the device*/
        BluetoothGattService mCustomService = mGatt.getService(UUID.fromString("00001110-0000-1000-8000-00805f9b34fb"));
        if (mCustomService == null) {
            Log.w(TAG, "Custom BLE Service not found");
            return;
        }
        /*get the read characteristic from the service*/
        BluetoothGattCharacteristic mWriteCharacteristic = mCustomService.getCharacteristic(UUID.fromString("00000001-0000-1000-8000-00805f9b34fb"));
        mWriteCharacteristic.setValue(value, android.bluetooth.BluetoothGattCharacteristic.FORMAT_UINT8, 0);
        if (!mGatt.writeCharacteristic(mWriteCharacteristic)) {
            Log.w(TAG, "Failed to write characteristic");
        }
    }
}