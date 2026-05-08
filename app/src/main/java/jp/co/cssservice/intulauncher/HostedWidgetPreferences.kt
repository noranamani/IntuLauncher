package jp.co.cssservice.intulauncher

import android.content.Context

/**
 * ホーム上にホストしたウィジェットの ID を保存するクラスです。
 */
class HostedWidgetPreferences(context: Context) {
    /** ウィジェットホスト情報を保持する共有設定です。 */
    private val preferences = context.getSharedPreferences("intu_launcher_hosted_widget", Context.MODE_PRIVATE)

    /**
     * 保存済みのウィジェット ID を返します。
     */
    fun getHostedWidgetId(): Int? {
        val value = preferences.getInt(KEY_HOSTED_WIDGET_ID, -1)
        return if (value >= 0) value else null
    }

    /**
     * ウィジェット ID を保存します。
     */
    fun setHostedWidgetId(widgetId: Int?) {
        if (widgetId == null) {
            preferences.edit().remove(KEY_HOSTED_WIDGET_ID).apply()
        } else {
            preferences.edit().putInt(KEY_HOSTED_WIDGET_ID, widgetId).apply()
        }
    }

    companion object {
        /** ホスト済みウィジェット ID 保存キーです。 */
        private const val KEY_HOSTED_WIDGET_ID = "key_hosted_widget_id"
    }
}
