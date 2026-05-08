package jp.co.cssservice.intulauncher

import android.content.Context

class AnchorPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("intu_launcher_prefs", Context.MODE_PRIVATE)

    fun getPinnedPackage(slotKey: String): String? = preferences.getString(slotKey, null)

    fun setPinnedPackage(slotKey: String, packageName: String?) {
        preferences.edit().putString(slotKey, packageName).apply()
    }
}

