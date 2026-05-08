package jp.co.cssservice.intulauncher

import android.content.Intent
import android.graphics.drawable.Drawable
import androidx.annotation.ColorRes

/**
 * ランチャーから起動可能なアプリの最小情報を保持します。
 */
data class LaunchableApp(
    /** パッケージ一意識別子です。 */
    val packageName: String,
    /** 画面に表示するアプリ名です。 */
    val label: String,
    /** ランチャー上で使用するアイコンです。 */
    val icon: Drawable,
    /** アプリ起動に使用するインテントです。 */
    val launchIntent: Intent,
)

/**
 * ホーム画面の切り替え判定に使用するコンテキスト情報です。
 */
data class ContextSnapshot(
    /** 現在時刻の時です。 */
    val hourOfDay: Int,
    /** 充電中または満充電かどうかです。 */
    val isCharging: Boolean,
    /** イヤホンまたはヘッドホン利用中かどうかです。 */
    val hasHeadphones: Boolean,
    /** 推定したバッテリー残量です。 */
    val batteryPercent: Int,
    /** 画面が横向きかどうかです。 */
    val isLandscape: Boolean,
)

/**
 * 1 つの動的スロットが持つ見出しと候補探索条件です。
 */
data class SlotSpec(
    /** スロット見出しです。 */
    val title: String,
    /** スロットの狙いを説明する文言です。 */
    val prompt: String,
    /** 候補アプリ探索に使用するキーワードです。 */
    val keywords: List<String>,
    /** 通常推薦か Discovery 枠かを表します。 */
    val kind: SlotKind = SlotKind.PREDICTION,
)

/**
 * スロット仕様に対して実際に解決したアプリ結果です。
 */
data class ResolvedSlot(
    /** 元となったスロット仕様です。 */
    val spec: SlotSpec,
    /** 解決されたアプリです。 */
    val app: LaunchableApp?,
    /** スロット下部に表示する案内文です。 */
    val actionHint: String,
)

/**
 * スロットが通常予測か Discovery 枠かを表す種別です。
 */
enum class SlotKind {
    PREDICTION,
    DISCOVERY,
}

/**
 * ユーザーが固定できるアンカースロットの設定です。
 */
data class AnchorSlotSpec(
    /** 永続化に使う設定キーです。 */
    val key: String,
    /** 画面に表示するアンカー名です。 */
    val title: String,
    /** 初期候補を探すためのキーワードです。 */
    val defaultKeywords: List<String>,
)

/**
 * ホーム画面全体の見た目と推薦傾向を表すプロファイルです。
 */
enum class LauncherProfile(
    /** プロファイル名です。 */
    val displayName: String,
    /** メイン見出しです。 */
    val headline: String,
    /** サブ見出しです。 */
    val subheadline: String,
    /** 背景色リソースです。 */
    @ColorRes val backgroundColor: Int,
    /** このプロファイルで使う動的スロット定義です。 */
    val slotSpecs: List<SlotSpec>,
) {
    MORNING_COMMUTE(
        displayName = "朝の移動",
        headline = "移動に必要な導線を先回りして並べます。",
        subheadline = "乗換、更新確認、音声系の導線を先頭へ寄せ、探す手間を削ります。",
        backgroundColor = R.color.background_commute,
        slotSpecs = listOf(
            SlotSpec("移動", "移動中に必要な地図や経路案内を優先します。", listOf("map", "maps", "transit", "train", "route", "google map", "navitime", "station", "乗換", "地図")),
            SlotSpec("更新確認", "移動中に追いたいニュースや連絡を前面に出します。", listOf("news", "feed", "mail", "gmail", "outlook", "inoreader", "smartnews", "line")),
            SlotSpec("発見", "移動文脈に近いが固定化していない候補を 1 枠だけ提案します。", listOf("music", "spotify", "podcast", "youtube music", "audio", "radiko"), kind = SlotKind.DISCOVERY),
        ),
    ),
    FOCUS_WORK(
        displayName = "業務集中",
        headline = "仕事の中心導線を迷わず開ける配置に寄せます。",
        subheadline = "予定、チーム連絡、記録系を切り替え無しで触れる状態に整えます。",
        backgroundColor = R.color.background_focus,
        slotSpecs = listOf(
            SlotSpec("予定", "就業時間帯に合わせてカレンダーや予定系を優先します。", listOf("calendar", "schedule", "outlook", "google calendar")),
            SlotSpec("連絡", "チャットやコラボレーション系を中央に寄せます。", listOf("slack", "teams", "chat", "line works", "discord")),
            SlotSpec("発見", "作業が固定化し過ぎないよう補助的な候補を 1 枠だけ混ぜます。", listOf("keep", "note", "notion", "memo", "todo", "docs"), kind = SlotKind.DISCOVERY),
        ),
    ),
    EVENING_HOME(
        displayName = "夜の自宅",
        headline = "回復と日課に寄せたホーム画面へ切り替えます。",
        subheadline = "娯楽、天気、夜の定番導線を前面へ出して負荷を下げます。",
        backgroundColor = R.color.background_home,
        slotSpecs = listOf(
            SlotSpec("くつろぎ", "夜に開きやすい動画や読書アプリを優先します。", listOf("youtube", "netflix", "prime video", "kindle", "book", "reader")),
            SlotSpec("明日の準備", "天気や翌日の外出準備に近い導線を出します。", listOf("weather", "tenki", "forecast")),
            SlotSpec("発見", "忘れがちな日課や便利アプリをやわらかく差し込みます。", listOf("clock", "alarm", "home", "smart home", "calendar", "camera"), kind = SlotKind.DISCOVERY),
        ),
    );

    /**
     * プロファイル定義から 3 つのスロットを解決します。
     */
    fun resolveSlots(apps: List<LaunchableApp>): List<ResolvedSlot> {
        val usedPackages = mutableSetOf<String>()
        return slotSpecs.map { spec ->
            // Discovery 枠だけ探索ロジックを分け、同じアプリばかり出ないようにします。
            val app = when (spec.kind) {
                SlotKind.PREDICTION -> AppCatalog.findBestMatch(apps, spec.keywords, usedPackages)
                SlotKind.DISCOVERY -> DiscoveryEngine.findDiscoveryPick(apps, spec.keywords, usedPackages)
            }
            if (app != null) {
                usedPackages += app.packageName
            }
            ResolvedSlot(
                spec = spec,
                app = app,
                actionHint = app?.let { DeepActionResolver.describe(it, spec.kind) } ?: spec.prompt,
            )
        }
    }

    companion object {
        /**
         * 現在のコンテキストから最も近いホームプロファイルを選びます。
         */
        fun from(snapshot: ContextSnapshot): LauncherProfile {
            return when {
                // 夜間に横向きへ切り替わった場合は、動画視聴寄りの自宅モードへ強めに寄せます。
                snapshot.isLandscape && snapshot.hourOfDay in 18..23 -> EVENING_HOME
                snapshot.hourOfDay in 5..10 -> MORNING_COMMUTE
                snapshot.hourOfDay in 11..17 -> FOCUS_WORK
                snapshot.isCharging && snapshot.hourOfDay >= 20 -> EVENING_HOME
                snapshot.hasHeadphones && snapshot.hourOfDay >= 18 -> EVENING_HOME
                snapshot.hourOfDay >= 18 || snapshot.hourOfDay <= 4 -> EVENING_HOME
                else -> FOCUS_WORK
            }
        }
    }
}

/**
 * アンカースロットの初期候補定義です。
 */
val defaultAnchorSlots = listOf(
    AnchorSlotSpec("anchor_browser", "ブラウザ", listOf("chrome", "browser", "firefox", "edge")),
    AnchorSlotSpec("anchor_chat", "連絡", listOf("line", "slack", "teams", "discord", "messages")),
    AnchorSlotSpec("anchor_utility", "実用", listOf("camera", "wallet", "pay", "calculator", "files")),
)
