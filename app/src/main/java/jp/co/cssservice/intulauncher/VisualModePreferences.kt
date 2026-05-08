package jp.co.cssservice.intulauncher

import android.content.Context
import android.net.Uri

/**
 * 背景モードと固定背景画像を保持するクラスです。
 */
class VisualModePreferences(context: Context) {
    /** 背景モード用の共有設定です。 */
    private val preferences = context.getSharedPreferences("intu_launcher_visual_mode", Context.MODE_PRIVATE)

    /**
     * 現在の背景モードを返します。
     */
    fun getVisualMode(): VisualMode {
        val value = preferences.getString(KEY_VISUAL_MODE, VisualMode.AMBIENT.name) ?: VisualMode.AMBIENT.name
        return VisualMode.entries.firstOrNull { it.name == value } ?: VisualMode.AMBIENT
    }

    /**
     * 背景モードを保存します。
     */
    fun setVisualMode(mode: VisualMode) {
        preferences.edit().putString(KEY_VISUAL_MODE, mode.name).apply()
    }

    /**
     * 固定背景画像の URI を返します。
     */
    fun getFixedImageUri(): Uri? {
        val value = preferences.getString(KEY_FIXED_IMAGE_URI, null) ?: return null
        return Uri.parse(value)
    }

    /**
     * 固定背景画像の URI を保存します。
     */
    fun setFixedImageUri(uri: Uri?) {
        preferences.edit().putString(KEY_FIXED_IMAGE_URI, uri?.toString()).apply()
    }

    companion object {
        /** 背景モード保存キーです。 */
        private const val KEY_VISUAL_MODE = "key_visual_mode"

        /** 固定画像 URI 保存キーです。 */
        private const val KEY_FIXED_IMAGE_URI = "key_fixed_image_uri"
    }
}
