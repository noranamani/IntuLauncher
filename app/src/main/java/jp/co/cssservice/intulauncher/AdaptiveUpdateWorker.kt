package jp.co.cssservice.intulauncher

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * 軽量な再計算とログ掃除を行う定期ワーカーです。
 */
class AdaptiveUpdateWorker(
    context: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(context, workerParameters) {
    /**
     * 定期更新処理を実行します。
     */
    override suspend fun doWork(): Result {
        NotificationInsightStore(applicationContext).pruneExpiredRecords()
        IntuWidgetProvider.refreshAllWidgets(applicationContext)
        return Result.success()
    }
}
