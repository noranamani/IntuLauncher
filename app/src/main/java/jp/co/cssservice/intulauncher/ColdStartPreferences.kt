package jp.co.cssservice.intulauncher

import android.content.Context
import java.time.Duration
import java.time.Instant

/**
 * コールドスタートに関する初期導入設定と学習開始時刻を管理するクラスです。
 */
class ColdStartPreferences(context: Context) {
    /** コールドスタート情報を保持する共有設定です。 */
    private val preferences = context.getSharedPreferences("intu_launcher_cold_start", Context.MODE_PRIVATE)

    /**
     * 選択済みの初期プロファイルを返します。
     */
    fun getSelectedProfile(): ColdStartProfile? {
        val name = preferences.getString(KEY_PROFILE, null) ?: return null
        return ColdStartProfile.entries.firstOrNull { it.name == name }
    }

    /**
     * 初期プロファイルを保存します。
     */
    fun setSelectedProfile(profile: ColdStartProfile) {
        if (!preferences.contains(KEY_FIRST_OPEN_AT)) {
            preferences.edit().putLong(KEY_FIRST_OPEN_AT, Instant.now().toEpochMilli()).apply()
        }
        preferences.edit().putString(KEY_PROFILE, profile.name).apply()
    }

    /**
     * 学習開始からの経過日数を返します。
     */
    fun getElapsedDays(): Long {
        val firstOpenAt = preferences.getLong(KEY_FIRST_OPEN_AT, -1L)
        if (firstOpenAt <= 0L) {
            return 0L
        }
        return Duration.between(Instant.ofEpochMilli(firstOpenAt), Instant.now()).toDays()
    }

    /**
     * まだ学習初期期間かどうかを返します。
     */
    fun isLearningPhase(): Boolean {
        return getSelectedProfile() == null || getElapsedDays() < LEARNING_DAYS
    }

    companion object {
        /** 学習初期期間として扱う日数です。 */
        private const val LEARNING_DAYS = 3L

        /** 初期プロファイル保存キーです。 */
        private const val KEY_PROFILE = "key_profile"

        /** 初回起動時刻保存キーです。 */
        private const val KEY_FIRST_OPEN_AT = "key_first_open_at"
    }
}

