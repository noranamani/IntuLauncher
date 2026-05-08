package jp.co.cssservice.intulauncher

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * 通知到着と消去を監視し、ローカル学習ログへ反映するサービスです。
 */
class IntuNotificationListenerService : NotificationListenerService() {
    /**
     * 通知到着時にログを記録し、不要通知候補なら即座にサイレント化します。
     */
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val store = NotificationInsightStore(this)
        val title = sbn.notification.extras?.getCharSequence("android.title")?.toString() ?: sbn.packageName
        val importance = sbn.notification.priority
        store.recordPosted(sbn.packageName, title, importance)
        if (store.shouldMute(sbn.packageName)) {
            cancelNotification(sbn.key)
        }
    }

    /**
     * 通知消去時に履歴へ反映します。
     */
    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        val title = sbn.notification.extras?.getCharSequence("android.title")?.toString() ?: sbn.packageName
        NotificationInsightStore(this).recordCleared(sbn.packageName, title, sbn.notification.priority)
    }
}
