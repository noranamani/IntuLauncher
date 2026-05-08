package jp.co.cssservice.intulauncher

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * バッテリー負荷を抑えながら定期更新を登録するクラスです。
 */
class AdaptiveUpdateScheduler(private val context: Context) {
    /**
     * 周期更新を登録します。
     */
    fun ensureScheduled() {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<AdaptiveUpdateWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest,
        )
    }

    companion object {
        /** 一意な定期更新ワーク名です。 */
        private const val UNIQUE_WORK_NAME = "intu_launcher_adaptive_update"
    }
}
