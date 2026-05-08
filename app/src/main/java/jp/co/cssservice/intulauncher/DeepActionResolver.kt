package jp.co.cssservice.intulauncher

import java.util.Locale

/**
 * アプリ推薦結果に対して、次に起こりそうな操作を説明文として付与する補助クラスです。
 */
object DeepActionResolver {
    /**
     * アプリ種別とスロット種別から Deep Action 風の案内文を生成します。
     */
    fun describe(app: LaunchableApp, slotKind: SlotKind): String {
        val packageText = app.packageName.lowercase(Locale.getDefault())
        val labelText = app.label.lowercase(Locale.getDefault())
        val kindPrefix = if (slotKind == SlotKind.DISCOVERY) {
            "発見ヒント"
        } else {
            "次の操作"
        }

        // よく使われるアプリ群だけ個別文言を持たせ、その他は汎用ヒントにフォールバックします。
        val action = when {
            "youtube" in packageText || "youtube" in labelText -> "前回の視聴導線へそのまま戻れます"
            "spotify" in packageText || "music" in labelText -> "今の流れに合う音声体験へすぐ入れます"
            "map" in packageText || "navi" in packageText || "地図" in app.label -> "今の状況に合う経路確認へ直行できます"
            "calendar" in packageText || "schedule" in labelText -> "次の予定をメニュー探索なしで開けます"
            "slack" in packageText || "teams" in packageText || "line" in packageText -> "今やり取りすべき会話へ素早く入れます"
            "camera" in packageText -> "撮り逃したくない瞬間にすぐ反応できます"
            "kindle" in packageText || "reader" in labelText -> "読みかけの文脈に戻りやすくなります"
            else -> "次に取りそうな行動へ自然につなげます"
        }

        return "$kindPrefix: $action。"
    }
}
