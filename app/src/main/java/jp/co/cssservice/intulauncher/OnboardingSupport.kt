package jp.co.cssservice.intulauncher

import android.content.Context
import java.time.Duration
import java.time.Instant

/**
 * 利き手設定を表す列挙型です。
 */
enum class Handedness(
    /** 画面表示用の名称です。 */
    val displayName: String,
) {
    RIGHT("右手中心"),
    LEFT("左手中心"),
    BOTH("両手"),
}

/**
 * アイコン密度の初期設定を表す列挙型です。
 */
enum class IconDensity(
    /** 画面表示用の名称です。 */
    val displayName: String,
) {
    COMPACT("高密度"),
    BALANCED("標準"),
    RELAXED("ゆったり"),
}

/**
 * 1分セットアップで使う質問定義です。
 */
data class SetupQuestion(
    /** 質問文です。 */
    val title: String,
    /** 選択肢です。 */
    val options: List<String>,
)

/**
 * 段階的移行の分析結果です。
 */
data class MigrationInsight(
    /** ウィジェット試行モードを推奨するかどうかです。 */
    val shouldUseTrialWidget: Boolean,
    /** 推定した UI の重心です。 */
    val gravityHint: String,
    /** アイコン密度の推奨値です。 */
    val iconDensityHint: String,
    /** 画面へ出す要約文です。 */
    val summary: String,
)

/**
 * 時短レポート用の集計結果です。
 */
data class BenefitReport(
    /** 推定時短秒数です。 */
    val savedSeconds: Int,
    /** アプリ一覧を最後に開いてからの経過日数です。 */
    val daysSinceDrawerOpen: Long?,
    /** 予測導線の採用率です。 */
    val predictionHitRate: Int,
    /** ユーザー向け要約文です。 */
    val summary: String,
)

/**
 * 数時間後のホーム変化プレビュー用データです。
 */
data class HomePreview(
    /** 何時間後の想定かです。 */
    val hoursAhead: Int,
    /** 想定プロファイル名です。 */
    val profileName: String,
    /** 想定スロットの説明です。 */
    val slotSummary: String,
)

/**
 * 導入支援とコンバージョン戦略に関する状態を管理するクラスです。
 */
class OnboardingSupportPreferences(context: Context) {
    /** 導入支援用の共有設定です。 */
    private val preferences = context.getSharedPreferences("intu_launcher_onboarding", Context.MODE_PRIVATE)

    /**
     * アプリ一覧を開いた時刻を記録します。
     */
    fun recordDrawerOpened() {
        preferences.edit()
            .putLong(KEY_LAST_DRAWER_OPEN_AT, Instant.now().toEpochMilli())
            .putInt(KEY_DRAWER_OPEN_COUNT, preferences.getInt(KEY_DRAWER_OPEN_COUNT, 0) + 1)
            .apply()
    }

    /**
     * 動的スロットの予測提示回数を記録します。
     */
    fun recordPredictionExposure() {
        preferences.edit()
            .putInt(KEY_PREDICTION_EXPOSURES, preferences.getInt(KEY_PREDICTION_EXPOSURES, 0) + 1)
            .apply()
    }

    /**
     * 予測導線からの起動成功回数を記録します。
     */
    fun recordPredictionHit() {
        preferences.edit()
            .putInt(KEY_PREDICTION_HITS, preferences.getInt(KEY_PREDICTION_HITS, 0) + 1)
            .apply()
    }

    /**
     * アンカースロットからの起動回数を記録します。
     */
    fun recordAnchorLaunch() {
        preferences.edit()
            .putInt(KEY_ANCHOR_LAUNCHES, preferences.getInt(KEY_ANCHOR_LAUNCHES, 0) + 1)
            .apply()
    }

    /**
     * 1分セットアップ結果を保存します。
     */
    fun saveSetupResult(
        profile: ColdStartProfile,
        handedness: Handedness,
        iconDensity: IconDensity,
        prefersTrialWidget: Boolean,
    ) {
        preferences.edit()
            .putString(KEY_PROFILE_NAME, profile.name)
            .putString(KEY_HANDEDNESS, handedness.name)
            .putString(KEY_ICON_DENSITY, iconDensity.name)
            .putBoolean(KEY_PREFERS_TRIAL_WIDGET, prefersTrialWidget)
            .putBoolean(KEY_SETUP_COMPLETED, true)
            .apply()
    }

    /**
     * セットアップ完了済みかどうかを返します。
     */
    fun hasCompletedSetup(): Boolean {
        return preferences.getBoolean(KEY_SETUP_COMPLETED, false)
    }

    /**
     * 推定した利き手設定を返します。
     */
    fun getHandedness(): Handedness {
        val value = preferences.getString(KEY_HANDEDNESS, Handedness.RIGHT.name) ?: Handedness.RIGHT.name
        return Handedness.entries.firstOrNull { it.name == value } ?: Handedness.RIGHT
    }

    /**
     * 推定したアイコン密度を返します。
     */
    fun getIconDensity(): IconDensity {
        val value = preferences.getString(KEY_ICON_DENSITY, IconDensity.BALANCED.name) ?: IconDensity.BALANCED.name
        return IconDensity.entries.firstOrNull { it.name == value } ?: IconDensity.BALANCED
    }

    /**
     * ウィジェット試行モード志向かどうかを返します。
     */
    fun prefersTrialWidget(): Boolean {
        return preferences.getBoolean(KEY_PREFERS_TRIAL_WIDGET, true)
    }

    /**
     * 段階的移行の推奨内容を組み立てます。
     */
    fun buildMigrationInsight(): MigrationInsight {
        val handedness = getHandedness()
        val iconDensity = getIconDensity()
        val gravityHint = when (handedness) {
            Handedness.RIGHT -> "右下に主導線を寄せる"
            Handedness.LEFT -> "左下に主導線を寄せる"
            Handedness.BOTH -> "中央寄せで左右対称を維持する"
        }
        val iconDensityHint = when (iconDensity) {
            IconDensity.COMPACT -> "情報密度を維持しつつ AI スロットの幅を確保する"
            IconDensity.BALANCED -> "標準密度でアンカーと AI スロットを均衡させる"
            IconDensity.RELAXED -> "タップしやすさ重視で余白を広めに取る"
        }
        val summary = if (prefersTrialWidget()) {
            "まずは予測ウィジェットで精度を体験し、重心は $gravityHint 方針で初期化します。"
        } else {
            "フルランチャー移行を前提に、$gravityHint / $iconDensityHint の初期レイアウトで始めます。"
        }
        return MigrationInsight(
            shouldUseTrialWidget = prefersTrialWidget(),
            gravityHint = gravityHint,
            iconDensityHint = iconDensityHint,
            summary = summary,
        )
    }

    /**
     * 時短レポートを作成します。
     */
    fun buildBenefitReport(): BenefitReport {
        val exposures = preferences.getInt(KEY_PREDICTION_EXPOSURES, 0).coerceAtLeast(1)
        val hits = preferences.getInt(KEY_PREDICTION_HITS, 0)
        val anchorLaunches = preferences.getInt(KEY_ANCHOR_LAUNCHES, 0)
        val drawerOpenCount = preferences.getInt(KEY_DRAWER_OPEN_COUNT, 0)
        val savedSeconds = (hits * 8) + (anchorLaunches * 4) - drawerOpenCount
        val hitRate = ((hits.toDouble() / exposures.toDouble()) * 100.0).toInt().coerceIn(0, 100)
        val daysSinceDrawerOpen = getDaysSinceDrawerOpen()
        val summary = if (daysSinceDrawerOpen == null) {
            "まだアプリ一覧の利用履歴がありません。予測導線の採用率は $hitRate% です。"
        } else {
            "最後にアプリ一覧を開いたのは $daysSinceDrawerOpen 日前です。推定時短は ${savedSeconds.coerceAtLeast(0)} 秒です。"
        }
        return BenefitReport(
            savedSeconds = savedSeconds.coerceAtLeast(0),
            daysSinceDrawerOpen = daysSinceDrawerOpen,
            predictionHitRate = hitRate,
            summary = summary,
        )
    }

    /**
     * 数時間後のホーム変化プレビューを返します。
     */
    fun buildPreviewScenarios(): List<HomePreview> {
        val profile = preferences.getString(KEY_PROFILE_NAME, ColdStartProfile.BUSINESS.name) ?: ColdStartProfile.BUSINESS.name
        val profileName = ColdStartProfile.entries.firstOrNull { it.name == profile }?.displayName ?: "ビジネス"
        return listOf(
            HomePreview(2, profileName, "連絡 / 予定 / 発見 を中心に寄せます。"),
            HomePreview(5, profileName, "移動や休憩に合わせて音声・地図の比率を増やします。"),
            HomePreview(10, profileName, "夜帯は回復導線と翌日の準備導線へ切り替えます。"),
        )
    }

    /**
     * 最後にアプリ一覧を開いてからの経過日数を返します。
     */
    private fun getDaysSinceDrawerOpen(): Long? {
        val timestamp = preferences.getLong(KEY_LAST_DRAWER_OPEN_AT, -1L)
        if (timestamp <= 0L) {
            return null
        }
        return Duration.between(Instant.ofEpochMilli(timestamp), Instant.now()).toDays()
    }

    companion object {
        /** アプリ一覧最終オープン時刻の保存キーです。 */
        private const val KEY_LAST_DRAWER_OPEN_AT = "key_last_drawer_open_at"

        /** アプリ一覧の起動回数キーです。 */
        private const val KEY_DRAWER_OPEN_COUNT = "key_drawer_open_count"

        /** 予測スロットの提示回数キーです。 */
        private const val KEY_PREDICTION_EXPOSURES = "key_prediction_exposures"

        /** 予測スロットからの起動成功回数キーです。 */
        private const val KEY_PREDICTION_HITS = "key_prediction_hits"

        /** アンカースロットからの起動回数キーです。 */
        private const val KEY_ANCHOR_LAUNCHES = "key_anchor_launches"

        /** セットアップ完了状態キーです。 */
        private const val KEY_SETUP_COMPLETED = "key_setup_completed"

        /** 初期プロファイル名キーです。 */
        private const val KEY_PROFILE_NAME = "key_profile_name"

        /** 利き手設定キーです。 */
        private const val KEY_HANDEDNESS = "key_handedness"

        /** アイコン密度キーです。 */
        private const val KEY_ICON_DENSITY = "key_icon_density"

        /** ウィジェット試行志向キーです。 */
        private const val KEY_PREFERS_TRIAL_WIDGET = "key_prefers_trial_widget"
    }
}
