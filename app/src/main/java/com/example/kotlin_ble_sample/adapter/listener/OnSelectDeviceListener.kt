package com.example.kotlin_ble_sample.adapter.listener

import com.example.kotlin_ble_sample.model.BTDevice

interface OnSelectDeviceListener {
    fun onSelectDevice(btDevice: BTDevice)
}