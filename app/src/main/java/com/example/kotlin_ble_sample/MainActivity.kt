package com.example.kotlin_ble_sample

import android.Manifest
import android.bluetooth.*
import android.bluetooth.BluetoothDevice.*
import android.bluetooth.BluetoothGatt.GATT_SUCCESS
import android.bluetooth.le.*
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.ParcelUuid
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.kotlin_ble_sample.adapter.BluetoothDeviceAdapter
import com.example.kotlin_ble_sample.adapter.listener.OnSelectDeviceListener
import com.example.kotlin_ble_sample.model.BTDevice
import kotlinx.android.synthetic.main.activity_main.*
import java.nio.charset.StandardCharsets
import java.util.*


class MainActivity : AppCompatActivity(), OnSelectDeviceListener {

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var bluetoothDeviceAdapter: BluetoothDeviceAdapter
    private lateinit var scanner: BluetoothLeScanner


    private var bluetoothDevices = arrayListOf<BTDevice>()

    companion object {
        const val TAG = "MainActivity"
        const val REQUEST_ENABLE_BT = 9999
        const val PERMISSION_REQUEST_CODE = 9990

        const val GLUCOSE_SERVICE = 0x1808
        const val GLUCOSE_FEATURE_CHARACTERISTIC = 0x2A51
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        scanner = bluetoothAdapter.bluetoothLeScanner

        bluetooth_scan_btn.setOnClickListener {
            scanDevice()
        }

        bluetoothDeviceAdapter = BluetoothDeviceAdapter()
        bluetoothDeviceAdapter.setSelectDeviceListener(this)
        bluetooth_device_recycler.layoutManager = LinearLayoutManager(
            this,
            LinearLayoutManager.VERTICAL,
            false
        )
        bluetooth_device_recycler.adapter = bluetoothDeviceAdapter
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_BT) {
            scanDevice()
        }
    }

    private fun scanDevice() {
        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        } else {
            val filters: MutableList<ScanFilter?> = mutableListOf()
            val gcServiceUUID = UUID.fromString("00001808-0000-1000-8000-00805f9b34fb")
            val serviceUUIDs = arrayListOf<UUID>(gcServiceUUID)
            serviceUUIDs.forEach {
                val mFilter = ScanFilter.Builder()
                    .setServiceUuid(ParcelUuid(it)).build()
                filters.add(mFilter)
            }

            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                .setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT)
                .setReportDelay(0L)
                .build()

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
                    PERMISSION_REQUEST_CODE
                )
            }

            scanner.startScan(filters, scanSettings, scanCallback)
            Log.d(TAG, "onScanDevice")
        }


    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            scanDevice()
        }
    }

    private val scanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device: BluetoothDevice = result.device
            val btDevice = BTDevice(device.name, device.address)
            if (bluetoothDevices.find { it.address == device.address } == null){
                bluetoothDevices.add(btDevice)
                bluetoothDeviceAdapter.devices = bluetoothDevices
                Log.d(
                    "onScan",
                    "deviceName: ${device.name}, address: ${device.address}, type: ${device.type}"
                )
            }
        }

        override fun onBatchScanResults(results: List<ScanResult?>?) {
            results?.forEach { scanResult ->
                if (scanResult?.device != null) {
                    val device = scanResult.device
                    Log.d(
                        "onBatch",
                        "deviceName: ${device.name}, address: ${device.address}, type: ${device.type}"
                    )
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            // Ignore for now
            Log.d(TAG, "Error on Scan!")
        }
    }

    override fun onSelectDevice(btDevice: BTDevice) {
        Log.d("onSelectDevice", "Select device: ${btDevice.name}")

        bluetoothAdapter
            .getRemoteDevice(btDevice.address)
            .connectGatt(this, false, gattCallback)

    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if(status == GATT_SUCCESS && gatt != null) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    val bondState: Int = gatt.device.bondState
                    // Take action depending on the bond state
                    if (bondState == BOND_NONE || bondState == BOND_BONDED) {
                        // Connected to device, now proceed to discover it's services but delay a bit if needed
                        var delayWhenBonded = 0L
                        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N) {
                            delayWhenBonded = 1000L
                        }
                        val delay = if (bondState == BOND_BONDED) delayWhenBonded else 0
                        runOnUiThread {
                            Handler().postDelayed({
                                Log.d(TAG, String.format(Locale.ENGLISH, "discovering services of '%s' with delay of %d ms", gatt.device.name, delay))
                                val result = gatt.discoverServices()
                                if (!result) {
                                    Log.e(TAG, "discoverServices failed to start")
                                }
                            }, delay)
                        }
                    } else if (bondState == BOND_BONDING) {
                        // Bonding process in progress, let it complete
                        Log.i(TAG, "waiting for bonding to complete")
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    // We successfully disconnected on our own request
                    gatt.close()
                } else {
                    // We're CONNECTING or DISCONNECTING, ignore for now
                }
            } else {
                // An error happened...figure out what happened!
                gatt?.close()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
//            val serviceList = gatt?.services // All services in device
            val glucoseService = gatt?.getService(convertFromInteger(GLUCOSE_SERVICE))
            val glucoseServiceCharacteristicList = glucoseService?.characteristics
            glucoseServiceCharacteristicList?.forEach {
                Log.d("CharacteristicList", "${it.uuid}")
            }

            val glucoseFeatureCharacter = gatt?.getService(convertFromInteger(GLUCOSE_SERVICE))?.getCharacteristic(convertFromInteger(GLUCOSE_FEATURE_CHARACTERISTIC))
            glucoseFeatureCharacter?.writeType = BluetoothGattCharacteristic.PERMISSION_READ
            gatt?.readCharacteristic(glucoseFeatureCharacter)
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            val descriptors = characteristic?.descriptors
            val characteristicRawByteArray = characteristic?.value
            val characteristicStringValue = characteristic?.getStringValue(0)
            val service = characteristic?.service

            Log.d("service", "$service")
            Log.d("descriptors", "$descriptors")
            characteristicRawByteArray?.let { Log.d("RawByteArray", String(it, StandardCharsets.UTF_8)) }
            Log.d("StringValue", "$characteristicStringValue")
        }
    }

    private fun convertFromInteger(i: Int): UUID {
        val msb = 0x0000000000001000L
        val lsb = -0x7fffff7fa064cb05L
        val value = (i and ((-0x1).toLong()).toInt()).toLong()
        return UUID(msb or (value shl 32), lsb)
    }
}