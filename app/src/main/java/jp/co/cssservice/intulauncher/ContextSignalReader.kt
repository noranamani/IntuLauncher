package jp.co.cssservice.intulauncher

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.BatteryManager
import java.time.LocalTime

class ContextSignalReader(private val context: Context) {
    fun readSnapshot(): ContextSnapshot {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPercent = if (level >= 0 && scale > 0) {
            ((level / scale.toFloat()) * 100f).toInt()
        } else {
            0
        }

        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        val audioManager = context.getSystemService(AudioManager::class.java)
        val hasHeadphones = audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.any { device ->
            device.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                device.type == AudioDeviceInfo.TYPE_USB_HEADSET
        } == true

        return ContextSnapshot(
            hourOfDay = LocalTime.now().hour,
            isCharging = isCharging,
            hasHeadphones = hasHeadphones,
            batteryPercent = batteryPercent,
        )
    }
}

