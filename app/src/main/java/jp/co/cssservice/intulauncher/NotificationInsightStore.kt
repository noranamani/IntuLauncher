package jp.co.cssservice.intulauncher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.Instant

/**
 * 通知に対するユーザー反応の種別です。
 */
enum class NotificationEngagement {
    TAP,
    CLEAR,
    IGNORE,
}

/**
 * 通知ログ 1 件分のモデルです。
 */
data class NotificationRecord(
    /** パッケージ名です。 */
    val packageName: String,
    /** 表示タイトルです。 */
    val title: String,
    /** 記録時刻です。 */
    val timestamp: Long,
    /** 反応種別です。 */
    val engagement: NotificationEngagement,
    /** 重要度です。 */
    val importance: Int,
)

/**
 * 通知インサイトの要約結果です。
 */
data class NotificationInsight(
    /** ユーザー向け要約文です。 */
    val summary: String,
    /** 重要通知のアクション誘導文です。 */
    val prompt: String,
    /** 自動ミュート候補のパッケージ一覧です。 */
    val mutedPackages: List<String>,
)

/**
 * 通知反応履歴をローカルに保存し、要約を返すクラスです。
 */
class NotificationInsightStore(context: Context) {
    /** 通知ログ保存用の共有設定です。 */
    private val preferences = context.getSharedPreferences("intu_launcher_notifications", Context.MODE_PRIVATE)

    /**
     * 通知到着を記録します。
     */
    fun recordPosted(packageName: String, title: String, importance: Int) {
        appendRecord(NotificationRecord(packageName, title, Instant.now().toEpochMilli(), NotificationEngagement.IGNORE, importance))
    }

    /**
     * 通知タップを記録します。
     */
    fun recordTapped(packageName: String, title: String, importance: Int) {
        appendRecord(NotificationRecord(packageName, title, Instant.now().toEpochMilli(), NotificationEngagement.TAP, importance))
    }

    /**
     * 通知消去を記録します。
     */
    fun recordCleared(packageName: String, title: String, importance: Int) {
        appendRecord(NotificationRecord(packageName, title, Instant.now().toEpochMilli(), NotificationEngagement.CLEAR, importance))
    }

    /**
     * 古い通知ログを掃除します。
     */
    fun pruneExpiredRecords() {
        val threshold = Instant.now().minus(Duration.ofDays(7)).toEpochMilli()
        val filtered = loadRecords().filter { it.timestamp >= threshold }
        saveRecords(filtered)
    }

    /**
     * 現在の通知インサイトを返します。
     */
    fun buildInsight(currentProfile: LauncherProfile): NotificationInsight {
        val records = loadRecords()
        val mutedPackages = records
            .groupBy { it.packageName }
            .filterValues { packageRecords ->
                val clears = packageRecords.count { it.engagement == NotificationEngagement.CLEAR }
                val total = packageRecords.size.coerceAtLeast(1)
                clears >= 3 && (clears.toDouble() / total.toDouble()) >= 0.7
            }
            .keys
            .toList()

        val importantRecord = records
            .filter { it.importance >= 3 }
            .maxByOrNull { it.timestamp }

        val summary = when {
            mutedPackages.isNotEmpty() ->
                "消去率の高い通知を ${mutedPackages.size} 件のアプリで検出しました。サイレント候補として蓄積しています。"
            importantRecord != null ->
                "重要通知を監視中です。直近では ${importantRecord.title} を重点候補として扱います。"
            else -> "通知アクセスを有効にすると、反応履歴から重要通知と不要通知を分離できます。"
        }

        val prompt = when {
            currentProfile == LauncherProfile.FOCUS_WORK && importantRecord != null ->
                "集中モード中です。会議後に ${importantRecord.title} を振り返る候補として保持します。"
            importantRecord != null ->
                "重要通知: ${importantRecord.title} / いま開く価値が高い可能性があります。"
            else -> "重要通知が届くと、ここにアクション誘導を表示します。"
        }

        return NotificationInsight(
            summary = summary,
            prompt = prompt,
            mutedPackages = mutedPackages,
        )
    }

    /**
     * パッケージが自動ミュート候補かどうかを返します。
     */
    fun shouldMute(packageName: String): Boolean {
        return buildInsight(LauncherProfile.FOCUS_WORK).mutedPackages.contains(packageName)
    }

    /**
     * 通知アクセスが無い場合のデモデータを投入します。
     */
    fun seedDemoIfEmpty() {
        if (loadRecords().isNotEmpty()) {
            return
        }
        recordPosted("com.linecorp.line", "LINE の未返信があります", 4)
        recordCleared("com.shopping.app", "本日限定クーポン", 1)
        recordCleared("com.shopping.app", "週末セール", 1)
        recordCleared("com.shopping.app", "送料無料キャンペーン", 1)
    }

    /**
     * 末尾に通知ログを追加します。
     */
    private fun appendRecord(record: NotificationRecord) {
        val records = loadRecords().toMutableList()
        records += record
        saveRecords(records.takeLast(50))
    }

    /**
     * ローカル保存から通知ログを読み込みます。
     */
    private fun loadRecords(): List<NotificationRecord> {
        val raw = preferences.getString(KEY_RECORDS_JSON, null) ?: return emptyList()
        val array = JSONArray(raw)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    NotificationRecord(
                        packageName = item.getString("packageName"),
                        title = item.getString("title"),
                        timestamp = item.getLong("timestamp"),
                        engagement = NotificationEngagement.valueOf(item.getString("engagement")),
                        importance = item.getInt("importance"),
                    ),
                )
            }
        }
    }

    /**
     * 通知ログを JSON として保存します。
     */
    private fun saveRecords(records: List<NotificationRecord>) {
        val array = JSONArray()
        records.forEach { record ->
            array.put(
                JSONObject()
                    .put("packageName", record.packageName)
                    .put("title", record.title)
                    .put("timestamp", record.timestamp)
                    .put("engagement", record.engagement.name)
                    .put("importance", record.importance),
            )
        }
        preferences.edit().putString(KEY_RECORDS_JSON, array.toString()).apply()
    }

    companion object {
        /** 通知ログ保存キーです。 */
        private const val KEY_RECORDS_JSON = "key_records_json"
    }
}
