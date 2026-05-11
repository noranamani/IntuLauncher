package jp.co.cssservice.intulauncher

import java.util.Locale

/**
 * アプリの種類に応じて、ホームのスロット内へ短い行動ヒントを返す解決クラスです。
 */
object DeepActionResolver {
    /**
     * アプリの種類とスロット種別から、1 行で収まる簡潔なヒント文言を返します。
     */
    fun describe(app: LaunchableApp, slotKind: SlotKind): String {
        val packageText = app.packageName.lowercase(Locale.getDefault())
        val labelText = app.label.lowercase(Locale.getDefault())

        // ホームの狭いスロット内で読み切れるよう、長文説明ではなく短い行動語に絞ります。
        return when {
            "youtube" in packageText || "youtube" in labelText -> "続きから再生"
            "spotify" in packageText || "music" in labelText -> "音楽を再開"
            "map" in packageText || "navi" in packageText || "地図" in app.label -> "経路を確認"
            "calendar" in packageText || "schedule" in labelText -> "予定を確認"
            "slack" in packageText || "teams" in packageText || "line" in packageText -> "未読を確認"
            "camera" in packageText -> "すぐ撮る"
            "kindle" in packageText || "reader" in labelText -> "続きを読む"
            slotKind == SlotKind.DISCOVERY -> "新しい使い方"
            else -> "すぐ開けます"
        }
    }
}
