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
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class MainActivity extends Activity implements View.OnClickListener {
    private static final String TAG = "LEX";
    private static final String PASSWORD = "p";

    private boolean isFromLollipop = false;
    private BluetoothAdapter mBluetoothAdapter;
    private boolean mScanning;
    private Handler mHandler;
    private BluetoothLeScanner mBluetoothLeScanner;

    private static final int REQUEST_ENABLE_BT = 1;
    // Stops scanning after SCAN_PERIOD seconds.
    private static final long SCAN_PERIOD = 4000;
    private LeDeviceListAdapter mDeviceAdapter;
    private ArrayList<BluetoothDevice> mDeviceList;

    private View mTextNoDevice;
    private BluetoothDevice mCurrentDevice;
    private boolean isActivatedDevice;

    private EditText mEdtPassword;

    private BluetoothGatt mGatt;
    private BluetoothGattCharacteristic mWriteCharacteristic, mRemoteCharacteristic;
    private BluetoothGattService mBatteryService;

    boolean isSetMode;

    private View mLayoutListDevice;
    private View mLayoutProgress;

    private View mButtonNoDevice, mButtonChooseDevice;

    private View mButtonDisconnectHidden, mButtonDisconnect;

    private View mButtonOpen, mButtonOpenHidden, mButtonClose, mButtonCloseHidden;

    private TextView mTextStatus;

    private boolean isShowKeyboard;

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
                }
                return view;
            }
        };
        ListView listDevices = (ListView) findViewById(R.id.listDevices);
        listDevices.setAdapter(mDeviceAdapter);

        listDevices.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                mCurrentDevice = mDeviceList.get(i);
                isActivatedDevice = false;
                mLayoutListDevice.setVisibility(View.GONE);
                toggleDisconnectButton(true);
                String text = "Successful connection.\n";
                if (mBatteryService != null) {
                    List<BluetoothGattCharacteristic> characteristics = mBatteryService.getCharacteristics();
                    if (characteristics != null) {

                    }
                }
                mTextStatus.setText(text);

            }
        });
        mButtonNoDevice = findViewById(R.id.buttonNoDevice);
        mButtonNoDevice.setOnClickListener(this);
        mButtonChooseDevice = findViewById(R.id.buttonChooseDevice);
        mButtonChooseDevice.setOnClickListener(this);

        mButtonDisconnect = findViewById(R.id.buttonDisconnect);
        mButtonDisconnect.setOnClickListener(this);
        mButtonDisconnectHidden = findViewById(R.id.buttonDisconnectHidden);
        mButtonDisconnectHidden.setOnClickListener(this);

        mButtonOpen = findViewById(R.id.buttonOpen);
        mButtonOpen.setOnClickListener(this);
        mButtonOpenHidden = findViewById(R.id.buttonOpenHidden);
        mButtonOpenHidden.setOnClickListener(this);
        mButtonClose = findViewById(R.id.buttonClose);
        mButtonClose.setOnClickListener(this);
        mButtonCloseHidden = findViewById(R.id.buttonCloseHidden);
        mButtonCloseHidden.setOnClickListener(this);

        mTextStatus = (TextView) findViewById(R.id.textStatus);

        mLayoutListDevice = findViewById(R.id.layoutListDevices);
        mLayoutProgress = findViewById(R.id.layoutProgress);

        findViewById(R.id.buttonRefresh).setOnClickListener(this);

        mEdtPassword = (EditText) findViewById(R.id.edtPassword);
        mEdtPassword.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                isShowKeyboard = true;
            }
        });
        findViewById(R.id.buttonSendPassword).setOnClickListener(this);

        mTextNoDevice = findViewById(R.id.textNoDevice);
        mTextNoDevice.setVisibility(mDeviceList.isEmpty() ? View.VISIBLE : View.GONE);

        findViewById(R.id.buttonOpen).setOnClickListener(this);
        findViewById(R.id.buttonClose).setOnClickListener(this);

        findViewById(R.id.buttonDisconnect).setOnClickListener(this);

        findViewById(R.id.buttonCloseApp).setOnClickListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        isSetMode = true;
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
                boolean foundDevice = false;
                for (int i = 0; i < mDeviceList.size() && !foundDevice; i++) {
                    BluetoothDevice device1 = mDeviceList.get(i);
                    foundDevice = device.getAddress().equals(device1.getAddress());
                }
                if (!foundDevice) {
                    mDeviceList.add(device);
                }
                mDeviceAdapter.notifyDataSetChanged();
            }
        }
    }

    //-----------------------------Before lollipop version-----------------------------
    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(BluetoothDevice bluetoothDevice, int i, byte[] bytes) {
            if (bluetoothDevice != null) {
                Log.d(TAG, "Scan result info: " + bluetoothDevice.toString());
                detectNewDevice(bluetoothDevice);
            }
            if (mDeviceList.isEmpty()) {
                mTextStatus.setText("No device found.");
                toggleChooseDeviceButton(false);
            } else {
                mTextStatus.setText("Connect to device...");
                toggleChooseDeviceButton(true);
            }
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
                    mLayoutProgress.setVisibility(View.GONE);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "No device found", Toast.LENGTH_SHORT).show();
                            mTextStatus.setText("Scanning's out of time. We remain your last scanning result.");
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
            if (mDeviceList.isEmpty()) {
                mTextStatus.setText("No device found.");
                toggleChooseDeviceButton(false);
            } else {
                mTextStatus.setText("Connect to device...");
                toggleChooseDeviceButton(true);
            }
            mTextNoDevice.setVisibility(mDeviceList.isEmpty() ? View.VISIBLE : View.GONE);
            mLayoutProgress.setVisibility(View.GONE);
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Toast.makeText(MainActivity.this, "Scanning error", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Scan result error: " + errorCode);
            mTextStatus.setText("No device found.");
            toggleChooseDeviceButton(false);
            mTextNoDevice.setVisibility(mDeviceList.isEmpty() ? View.VISIBLE : View.GONE);
            mLayoutProgress.setVisibility(View.GONE);
        }
    };

    private void scanLeDeviceFromLollipop(boolean enable) {
        if (mBluetoothLeScanner != null) {
            if (enable) {
                mHandler.postDelayed(new Runnable() {
                    public void run() {
                        mTextNoDevice.setVisibility(mDeviceList.isEmpty() ? View.VISIBLE : View.GONE);
                        mLayoutProgress.setVisibility(View.GONE);
                        mTextStatus.setText("Scanning's out of time. We remain your last scanning result.");
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
        try {
            mGatt = device.connectGatt(this, false, mGattCallback);
            scanLeDevice(false);// will stop after first device detection

            mWriteCharacteristic = null;
        } catch (Exception e) {

        }
//        if (mGatt == null) {
//            mGatt = device.connectGatt(this, false, mGattCallback);
//            scanLeDevice(false);// will stop after first device detection
//        }
//        mWriteCharacteristic = null;
//        mLayoutControl.setVisibility(View.VISIBLE);

    }

    public void disConnectToDevice() {
        try {
            mGatt.disconnect();
//            mGatt.discoverServices();
            mCurrentDevice = null;
            mTextStatus.setText("Device is disconnected.");
            toggleControlButtons(false);
        } catch (Exception ignored) {

        }
    }

    private void toggleChooseDeviceButton(boolean enable) {
        mButtonNoDevice.setVisibility(enable ? View.GONE : View.VISIBLE);
        mButtonChooseDevice.setVisibility(enable ? View.VISIBLE : View.GONE);
    }

    private void toggleDisconnectButton(boolean enable) {
        mButtonDisconnectHidden.setVisibility(enable ? View.GONE : View.VISIBLE);
        mButtonDisconnect.setVisibility(enable ? View.VISIBLE : View.GONE);
    }

    private void toggleControlButtons(boolean enable) {
        mButtonCloseHidden.setVisibility(enable ? View.GONE : View.VISIBLE);
        mButtonOpenHidden.setVisibility(enable ? View.GONE : View.VISIBLE);

        mButtonClose.setVisibility(enable ? View.VISIBLE : View.GONE);
        mButtonOpen.setVisibility(enable ? View.VISIBLE : View.GONE);
    }

    private BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            switch (newState) {
                case BluetoothProfile.STATE_CONNECTED:
                    Log.i("gattCallback", "STATE_CONNECTED");
                    gatt.discoverServices();

                    break;
                case BluetoothProfile.STATE_DISCONNECTED:
                    Log.e("gattCallback", "STATE_DISCONNECTED");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {

                        }
                    });
                    break;
                default:
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {

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
                            UUID uuidBattery = UUID.fromString("f000ffe0-0451-4000-b000-000000000000");
                            if (service.getUuid().compareTo(uuidWrite) == 0) {
                                getCharacteristicsFromService(service);
                            } else if (service.getUuid().compareTo(uuidBattery) == 0) {
                                mBatteryService = service;
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

    private void getCharacteristicsFromService(BluetoothGattService service) {
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
            isSetMode = true;
        }
        switch (id) {
            case R.id.buttonRefresh:
                mTextStatus.setText("Scanning...");
                if (mDeviceList != null) {
                    mDeviceList.clear();
                }
                if (mDeviceAdapter != null) {
                    mDeviceAdapter.notifyDataSetChanged();
                }
                mTextNoDevice.setVisibility(View.GONE);
                mLayoutProgress.setVisibility(View.VISIBLE);
                if (isFromLollipop) {
                    scanLeDeviceFromLollipop(true);
                } else {
                    scanLeDevice(true);
                }
                break;
            case R.id.buttonSendPassword:
                hideSoftKeyboard();
                if (mCurrentDevice != null) {
                    String password = mEdtPassword.getText().toString();
                    isActivatedDevice = PASSWORD.equals(password);
                    Log.d(TAG, "Password " + (isActivatedDevice ? "Correct" : "Wrong"));
                    if (mCurrentDevice != null) {
                        if (isActivatedDevice) {
                            connectToDevice(mCurrentDevice);
                            mTextStatus.setText("Correct password. Open the door.");
                            toggleControlButtons(true);
                        } else {
                            mTextStatus.setText("Wrong password. Try again!");
                            toggleControlButtons(false);
                        }
                    }
                } else {
                    mTextStatus.setText("Please connect to a device.");
                }
                break;
            case R.id.buttonOpen:
                if (mWriteCharacteristic != null && mRemoteCharacteristic != null) {
                    if (isSetMode) {
                        isSetMode = false;
                        mRemoteCharacteristic.setValue(1, BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                        mGatt.writeCharacteristic(mRemoteCharacteristic);

                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                mWriteCharacteristic.setValue("2");
                                mGatt.writeCharacteristic(mWriteCharacteristic);
                            }
                        }, 3000);

                    } else {
                        mWriteCharacteristic.setValue("2");
                        mGatt.writeCharacteristic(mWriteCharacteristic);
                    }

                }
                break;
            case R.id.buttonClose:
                if (mWriteCharacteristic != null && mRemoteCharacteristic != null) {
                    if (isSetMode) {
                        isSetMode = false;
                        mRemoteCharacteristic.setValue(1, BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                        mGatt.writeCharacteristic(mRemoteCharacteristic);

                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                mWriteCharacteristic.setValue("1");
                                mGatt.writeCharacteristic(mWriteCharacteristic);
                            }
                        }, 3000);

                    } else {
                        mWriteCharacteristic.setValue("1");
                        mGatt.writeCharacteristic(mWriteCharacteristic);
                    }
                }
                break;
            case R.id.buttonDisconnect:
//                mWriteCharacteristic.setValue("1");
//                mGatt.writeCharacteristic(mWriteCharacteristic);
                disConnectToDevice();
                isSetMode = true;
                toggleDisconnectButton(false);
                break;
            case R.id.buttonNoDevice:
                break;
            case R.id.buttonChooseDevice:
                mLayoutListDevice.setVisibility(View.VISIBLE);
                break;
            case R.id.buttonCloseApp:
                if (mGatt != null) {
                    mGatt.disconnect();
                }
                finish();
                break;
            default:
                break;
        }
    }

    private void hideSoftKeyboard() {
        /*View view = getWindow().getCurrentFocus();
        if (view != null) {
            InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            inputMethodManager.hideSoftInputFromInputMethod(view.getWindowToken(), InputMethodManager.HIDE_IMPLICIT_ONLY);
        }*/

        if (isShowKeyboard) {
            isShowKeyboard = false;
            InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            inputMethodManager.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0);
        }
    }
}