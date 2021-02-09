/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.bluetoothlegatt;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ExpandableListView;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 */
public class DeviceControlActivity extends Activity {
    private final static String TAG = DeviceControlActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    private TextView mConnectionState;
    private TextView mDataField;
    private Button req_bt03;
    private Button req_bt04;
    private Button req_bt05;
    private String mDeviceName;
    private String mDeviceAddress;
    private BluetoothLeService mBluetoothLeService;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
            new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
    private boolean mConnected = false;
    private BluetoothGattCharacteristic mNotifyCharacteristic;

    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                updateConnectionState(R.string.connected);
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
                clearUI();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                displayData(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
            }
        }
    };

    // If a given GATT characteristic is selected, check for supported features.  This sample
    // demonstrates 'Read' and 'Notify' features.  See
    // http://d.android.com/reference/android/bluetooth/BluetoothGatt.html for the complete
    // list of supported characteristic features.
    private final ExpandableListView.OnChildClickListener servicesListClickListner =
            new ExpandableListView.OnChildClickListener() {
                @Override
                public boolean onChildClick(ExpandableListView parent, View v, int groupPosition,
                                            int childPosition, long id) {
                    if (mGattCharacteristics != null) {
                        final BluetoothGattCharacteristic characteristic =
                                mGattCharacteristics.get(groupPosition).get(childPosition);
                        final int charaProp = characteristic.getProperties();
                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                            // If there is an active notification on a characteristic, clear
                            // it first so it doesn't update the data field on the user interface.
                            if (mNotifyCharacteristic != null) {
                                mBluetoothLeService.setCharacteristicNotification(
                                        mNotifyCharacteristic, false);
                                mNotifyCharacteristic = null;
                            }

                        }
                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                            mNotifyCharacteristic = characteristic;
                            mBluetoothLeService.setCharacteristicNotification(
                                    characteristic, true);
                        }
                        return true;
                    }
                    return false;
                }
            };

    private void clearUI() {
        mDataField.setText(R.string.no_data);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.gatt_services_characteristics);

        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        // Sets up UI references.
        ((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);
        mConnectionState = (TextView) findViewById(R.id.connection_state);
        mDataField = (TextView) findViewById(R.id.data_value);
        req_bt03 = (Button) findViewById(R.id.bt_req03);
        req_bt04 = (Button) findViewById(R.id.bt_req04);
        req_bt05 = (Button) findViewById(R.id.bt_req05);
        req_bt03.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onClickBtReq03();
            }
        });
        req_bt04.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onClickBtReq04();
            }
        });
        req_bt05.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onClickBtReq05();
            }
        });

        getActionBar().setTitle(mDeviceName);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_services, menu);
        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_connect:
                mBluetoothLeService.connect(mDeviceAddress);
                return true;
            case R.id.menu_disconnect:
                mBluetoothLeService.disconnect();
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mConnectionState.setText(resourceId);
            }
        });
    }

    private void displayData(String data) {
        if (data != null) {
            mDataField.setText(data);
        }
    }

    // Demonstrates how to iterate through the supported GATT Services/Characteristics.
    // In this sample, we populate the data structure that is bound to the ExpandableListView
    // on the UI.


    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    private void onClickBtReq03() {

        BluetoothGattCharacteristic bluetoothGattCharacteristic = null;

        if(mBluetoothLeService.beginReliableWrite()){
            Toast.makeText(this, "Request", Toast.LENGTH_SHORT).show();
        }else {
            Toast.makeText(this, "failed beginReliableWrite", Toast.LENGTH_SHORT).show();
        }

        BluetoothGattService bluetoothGattServiceList = mBluetoothLeService.getSupportedGattService(UUID.fromString("0000ff00-0000-1000-8000-00805f9b34fb"));


        byte[] test = new byte[]{(byte) 0xDD, (byte) 0xA5 , 0x03, 0x00, (byte) 0xFF, (byte) 0xFD , 0x77};
        //byte[] test = new byte[]{0x03, 0x00, (byte) 0xFF, (byte) 0xFD };

            try {
                bluetoothGattCharacteristic = bluetoothGattServiceList.getCharacteristic(UUID.fromString("0000ff02-0000-1000-8000-00805f9b34fb"));
                mBluetoothLeService.writeCharacteristic(bluetoothGattCharacteristic, test);
                mBluetoothLeService.readCharacteristic(bluetoothGattServiceList.getCharacteristic(UUID.fromString("0000ff02-0000-1000-8000-00805f9b34fb")));

                //Log.d("value" , value.toString());
            }catch (Exception e){
                Log.e("test ", e.getMessage());
            }

        //
    }

    private void onClickBtReq04() {

        BluetoothGattCharacteristic bluetoothGattCharacteristic = null;

        if(mBluetoothLeService.beginReliableWrite()){
            Toast.makeText(this, "Request", Toast.LENGTH_SHORT).show();
        }else {
            Toast.makeText(this, "failed beginReliableWrite", Toast.LENGTH_SHORT).show();
        }


        BluetoothGattService bluetoothGattServiceList = mBluetoothLeService.getSupportedGattService(UUID.fromString("0000ff00-0000-1000-8000-00805f9b34fb"));


        byte[] test1 = new byte[]{(byte) 0xDD, (byte) 0xA5 , 0x04, 0x00, (byte) 0xFF, (byte) 0xFC , 0x77};
        //byte[] test1 = new byte[]{ 0x04, 0x00, (byte) 0xFF, (byte) 0xFC };

        try {
            bluetoothGattCharacteristic = bluetoothGattServiceList.getCharacteristic(UUID.fromString("0000ff02-0000-1000-8000-00805f9b34fb"));
            mBluetoothLeService.writeCharacteristic(bluetoothGattCharacteristic, test1);
            mBluetoothLeService.readCharacteristic(bluetoothGattServiceList.getCharacteristic(UUID.fromString("0000ff02-0000-1000-8000-00805f9b34fb")));
            //Log.d("value" , value.toString());
        }catch (Exception e){
            Log.e("test ", e.getMessage());
        }

        //
    }

    private void onClickBtReq05() {

        BluetoothGattCharacteristic bluetoothGattCharacteristic = null;

        if(mBluetoothLeService.beginReliableWrite()){
            Toast.makeText(this, "Request", Toast.LENGTH_SHORT).show();
        }else {
            Toast.makeText(this, "failed beginReliableWrite", Toast.LENGTH_SHORT).show();
        }

        BluetoothGattService bluetoothGattServiceList = mBluetoothLeService.getSupportedGattService(UUID.fromString("0000ff00-0000-1000-8000-00805f9b34fb"));

        byte[] test2 = new byte[]{(byte) 0xDD, (byte) 0xA5 , 0x12, 0x00, (byte) 0xFF, (byte) 0xee , 0x77};
        //byte[] test2 = new byte[]{ 0x05, 0x00, (byte) 0xFF, (byte) 0xFB };

        try {
            bluetoothGattCharacteristic = bluetoothGattServiceList.getCharacteristic(UUID.fromString("0000ff02-0000-1000-8000-00805f9b34fb"));
            mBluetoothLeService.writeCharacteristic(bluetoothGattCharacteristic, test2);
            mBluetoothLeService.readCharacteristic(bluetoothGattServiceList.getCharacteristic(UUID.fromString("0000ff02-0000-1000-8000-00805f9b34fb")));
            //Log.d("value" , value.toString());
        }catch (Exception e){
            Log.e("test ", e.getMessage());
        }

    }
}

