package com.example.kotlin_ble_sample.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.kotlin_ble_sample.R
import com.example.kotlin_ble_sample.adapter.listener.OnSelectDeviceListener
import com.example.kotlin_ble_sample.model.BTDevice

class BluetoothDeviceAdapter : RecyclerView.Adapter<BluetoothDeviceAdapter.BluetoothDeviceViewHolder>() {

    var listener: OnSelectDeviceListener? = null

    var devices = arrayListOf<BTDevice>()
    set(value) {
        field = value
        notifyDataSetChanged()
    }

    fun setSelectDeviceListener(context: Context){
        this.listener = context as OnSelectDeviceListener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BluetoothDeviceViewHolder {
        return BluetoothDeviceViewHolder(
            LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_bluetooth_device, parent, false))
    }

    override fun onBindViewHolder(holder: BluetoothDeviceViewHolder, position: Int) {
        holder.bind(devices[position], listener)
    }

    override fun getItemCount(): Int {
        return devices.size
    }

    inner class BluetoothDeviceViewHolder(itemView: View): RecyclerView.ViewHolder(itemView){
        var name: TextView = itemView.findViewById(R.id.device_name)
        var address: TextView = itemView.findViewById(R.id.device_address)

        fun bind(btDevice: BTDevice, listener: OnSelectDeviceListener?) {
            name.text = btDevice.name
            address.text = btDevice.address
            itemView.setOnClickListener {
                listener?.onSelectDevice(btDevice)
            }
        }
    }
}