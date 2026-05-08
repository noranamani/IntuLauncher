package jp.co.cssservice.intulauncher

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * 既存の端末利用統計を読み取り、コールドスタート時の初期順位に活用するクラスです。
 */
class UsageStatsImporter(private val context: Context) {
    /**
     * 利用統計アクセス権が有効かどうかを返します。
     */
    fun hasAccessPermission(): Boolean {
        val appOpsManager = context.getSystemService(AppOpsManager::class.java)
        val mode = appOpsManager?.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        ) ?: AppOpsManager.MODE_IGNORED
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * 直近 14 日の利用統計からパッケージ名ごとの利用時間順マップを返します。
     */
    fun loadUsageRanking(): Map<String, Long> {
        if (!hasAccessPermission()) {
            return emptyMap()
        }

        val usageStatsManager = context.getSystemService(UsageStatsManager::class.java) ?: return emptyMap()
        val end = Instant.now().toEpochMilli()
        val start = Instant.now().minus(14, ChronoUnit.DAYS).toEpochMilli()
        val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)

        // 同一パッケージの利用時間を集約し、初期順位付けで扱いやすい形に揃えます。
        return stats
            .groupBy { it.packageName }
            .mapValues { entry -> entry.value.sumOf { it.totalTimeInForeground } }
            .filterValues { it > 0L }
    }
}
