package jp.co.cssservice.intulauncher

import android.content.Context

/**
 * 予測ウィジェット用の表示状態を保持するクラスです。
 */
class WidgetTrialStateStore(context: Context) {
    /** ウィジェット状態を保存する共有設定です。 */
    private val preferences = context.getSharedPreferences("intu_launcher_widget_trial", Context.MODE_PRIVATE)

    /**
     * ウィジェットに表示する 3 スロット情報と学習状態を保存します。
     */
    fun saveState(profileLabel: String, summary: String, slotLabels: List<String>) {
        preferences.edit()
            .putString(KEY_PROFILE_LABEL, profileLabel)
            .putString(KEY_SUMMARY, summary)
            .putString(KEY_SLOT_ONE, slotLabels.getOrElse(0) { "候補準備中" })
            .putString(KEY_SLOT_TWO, slotLabels.getOrElse(1) { "候補準備中" })
            .putString(KEY_SLOT_THREE, slotLabels.getOrElse(2) { "候補準備中" })
            .apply()
    }

    /**
     * 現在のウィジェット表示状態を返します。
     */
    fun loadState(): WidgetTrialState {
        return WidgetTrialState(
            profileLabel = preferences.getString(KEY_PROFILE_LABEL, "IntuLauncher Trial") ?: "IntuLauncher Trial",
            summary = preferences.getString(KEY_SUMMARY, "学習ログを蓄積中です。") ?: "学習ログを蓄積中です。",
            slotOne = preferences.getString(KEY_SLOT_ONE, "候補準備中") ?: "候補準備中",
            slotTwo = preferences.getString(KEY_SLOT_TWO, "候補準備中") ?: "候補準備中",
            slotThree = preferences.getString(KEY_SLOT_THREE, "候補準備中") ?: "候補準備中",
        )
    }

    companion object {
        /** プロファイル表示名キーです。 */
        private const val KEY_PROFILE_LABEL = "key_profile_label"

        /** 状態要約キーです。 */
        private const val KEY_SUMMARY = "key_summary"

        /** 1枠目ラベルキーです。 */
        private const val KEY_SLOT_ONE = "key_slot_one"

        /** 2枠目ラベルキーです。 */
        private const val KEY_SLOT_TWO = "key_slot_two"

        /** 3枠目ラベルキーです。 */
        private const val KEY_SLOT_THREE = "key_slot_three"
    }
}

/**
 * 予測ウィジェットに表示する状態です。
 */
data class WidgetTrialState(
    /** プロファイル表示名です。 */
    val profileLabel: String,
    /** 学習状態や導入メッセージです。 */
    val summary: String,
    /** 1枠目表示です。 */
    val slotOne: String,
    /** 2枠目表示です。 */
    val slotTwo: String,
    /** 3枠目表示です。 */
    val slotThree: String,
)
