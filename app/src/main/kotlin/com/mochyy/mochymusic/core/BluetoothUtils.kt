package com.mochyy.mochymusic.core

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

private fun AudioDeviceInfo.isBluetoothOutput(): Boolean =
    type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
        type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            (type == AudioDeviceInfo.TYPE_BLE_HEADSET || type == AudioDeviceInfo.TYPE_BLE_SPEAKER))

fun isBluetoothHeadphoneConnected(context: Context): Boolean {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.isBluetoothOutput() }
}

fun getConnectedBluetoothDeviceName(context: Context): String? {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
    val device = devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
        ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
        ?: devices.firstOrNull { it.isBluetoothOutput() }
    return device?.productName?.toString()
}

fun isBuds(name: String?): Boolean {
    if (name == null) return false
    val lowerName = name.lowercase()
    return lowerName.contains("buds") ||
           lowerName.contains("airpods") ||
           lowerName.contains("earpods") ||
           lowerName.contains("earphone") ||
           lowerName.contains("freebuds") ||
           lowerName.contains("pods")
}

fun isSpeaker(name: String?): Boolean {
    if (name == null) return false
    val lowerName = name.lowercase()
    return lowerName.contains("speaker") ||
           lowerName.contains("soundbar") ||
           lowerName.contains("homepod") ||
           lowerName.contains("echo") ||
           lowerName.contains("boombox") ||
           lowerName.contains("audio system") ||
           lowerName.contains("sound") ||
           lowerName.contains("audio") ||
           lowerName.contains("stereo") ||
           lowerName.contains("music") ||
           lowerName.contains("box") ||
           lowerName.contains("party") ||
           lowerName.contains("waves")
}
