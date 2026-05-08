package jp.co.cssservice.intulauncher

import android.content.Context

/**
 * アンカースロットに固定したアプリのパッケージ名を保持する設定クラスです。
 */
class AnchorPreferences(context: Context) {
    /** アンカースロットの固定内容を保存する共有設定です。 */
    private val preferences = context.getSharedPreferences("intu_launcher_prefs", Context.MODE_PRIVATE)

    /**
     * 指定スロットに固定済みのパッケージ名を取得します。
     */
    fun getPinnedPackage(slotKey: String): String? = preferences.getString(slotKey, null)

    /**
     * 指定スロットの固定パッケージ名を保存または解除します。
     */
    fun setPinnedPackage(slotKey: String, packageName: String?) {
        preferences.edit().putString(slotKey, packageName).apply()
    }
}
