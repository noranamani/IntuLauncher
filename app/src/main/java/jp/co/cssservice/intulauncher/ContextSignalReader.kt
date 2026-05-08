package jp.co.cssservice.intulauncher

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.BatteryManager
import java.time.LocalTime

/**
 * 端末状態からホーム画面更新に必要な軽量コンテキストを抽出するクラスです。
 */
class ContextSignalReader(private val context: Context) {
    /**
     * 現在の時間帯、充電状態、オーディオ出力状態、画面向きをまとめて取得します。
     */
    fun readSnapshot(): ContextSnapshot {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        // 取得失敗時は 0 扱いにして、UI 側で処理不能にならないようにします。
        val batteryPercent = if (level >= 0 && scale > 0) {
            ((level / scale.toFloat()) * 100f).toInt()
        } else {
            0
        }

        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        val audioManager = context.getSystemService(AudioManager::class.java)
        // 複数の出力方式をまとめて判定し、イヤホン利用中かどうかを単純な真偽値へ落とします。
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
            isLandscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE,
        )
    }
}
