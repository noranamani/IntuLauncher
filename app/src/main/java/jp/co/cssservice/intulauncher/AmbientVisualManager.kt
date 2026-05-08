package jp.co.cssservice.intulauncher

import android.graphics.Color
import androidx.annotation.ColorInt

/**
 * 背景モードの種別です。
 */
enum class VisualMode(
    /** 画面表示名です。 */
    val displayName: String,
) {
    AMBIENT("AI Ambient"),
    FIXED("Fixed Personal"),
}

/**
 * 背景表示に必要な状態をまとめたモデルです。
 */
data class AmbientVisualState(
    /** 現在の背景モードです。 */
    val mode: VisualMode,
    /** モード説明文です。 */
    val description: String,
    /** 背景色です。 */
    @ColorInt val backgroundColor: Int,
    /** 上部説明ラベルです。 */
    val label: String,
)

/**
 * 背景自動最適化に使う簡易エンジンです。
 */
class AmbientVisualManager {
    /**
     * 現在のコンテキストから AI Ambient 用の背景状態を生成します。
     */
    fun buildAmbientState(
        profile: LauncherProfile,
        snapshot: ContextSnapshot,
        notificationWeight: Int,
    ): AmbientVisualState {
        val baseColor = when (profile) {
            LauncherProfile.MORNING_COMMUTE -> Color.parseColor("#10213F")
            LauncherProfile.FOCUS_WORK -> Color.parseColor("#1A202A")
            LauncherProfile.EVENING_HOME -> Color.parseColor("#10281D")
        }

        // 背景ノイズは通知量や利用集中度に応じて落とし、読みやすさを優先します。
        val mutedColor = when {
            notificationWeight >= 70 -> blend(baseColor, Color.BLACK, 0.42f)
            snapshot.isCharging && snapshot.hourOfDay >= 20 -> blend(baseColor, Color.parseColor("#0A0E13"), 0.36f)
            snapshot.hourOfDay in 11..17 -> blend(baseColor, Color.parseColor("#203040"), 0.18f)
            else -> baseColor
        }

        val label = when {
            snapshot.hourOfDay in 5..10 -> "朝の光と移動視認性"
            snapshot.hourOfDay in 11..17 -> "集中優先の低彩度テーマ"
            else -> "夜の落ち着きと読みやすさ"
        }
        val description = when {
            notificationWeight >= 70 -> "通知が多いため背景ノイズを抑え、メイン導線の視認性を上げています。"
            snapshot.hasHeadphones -> "音声利用を検知し、コントラストを高めた移動向け背景へ寄せています。"
            snapshot.isLandscape -> "横向き利用を検知し、映像視聴向けの落ち着いた背景へ調整しています。"
            else -> "時刻と利用文脈に合わせて背景色とコントラストを自動調整しています。"
        }
        return AmbientVisualState(
            mode = VisualMode.AMBIENT,
            description = description,
            backgroundColor = mutedColor,
            label = label,
        )
    }

    /**
     * 固定背景モード用の説明状態を返します。
     */
    fun buildFixedState(hasCustomImage: Boolean): AmbientVisualState {
        val description = if (hasCustomImage) {
            "固定画像を維持しつつ、スロットの可読性を守るためカード濃度を優先しています。"
        } else {
            "固定モードです。背景画像を選ぶと、画像を維持しながら視認性だけを自動補正します。"
        }
        return AmbientVisualState(
            mode = VisualMode.FIXED,
            description = description,
            backgroundColor = Color.parseColor("#0D1422"),
            label = "固定背景と視認性サポート",
        )
    }

    /**
     * 2色を線形補間します。
     */
    private fun blend(@ColorInt from: Int, @ColorInt to: Int, ratio: Float): Int {
        val clamped = ratio.coerceIn(0f, 1f)
        val inverse = 1f - clamped
        val red = (Color.red(from) * inverse + Color.red(to) * clamped).toInt()
        val green = (Color.green(from) * inverse + Color.green(to) * clamped).toInt()
        val blue = (Color.blue(from) * inverse + Color.blue(to) * clamped).toInt()
        return Color.rgb(red, green, blue)
    }
}
