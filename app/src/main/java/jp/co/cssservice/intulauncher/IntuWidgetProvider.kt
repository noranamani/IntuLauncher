package jp.co.cssservice.intulauncher

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * 既存ホーム上で予測精度を体験してもらうためのウィジェットです。
 */
class IntuWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { appWidgetId ->
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        /**
         * 配置済みウィジェットをすべて更新します。
         */
        fun refreshAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, IntuWidgetProvider::class.java)
            val widgetIds = appWidgetManager.getAppWidgetIds(componentName)
            widgetIds.forEach { widgetId ->
                updateWidget(context, appWidgetManager, widgetId)
            }
        }

        /**
         * 1つのウィジェットを更新します。
         */
        private fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
        ) {
            val state = WidgetTrialStateStore(context).loadState()
            val views = RemoteViews(context.packageName, R.layout.app_widget_prediction)
            views.setTextViewText(R.id.widgetProfileText, state.profileLabel)
            views.setTextViewText(R.id.widgetSummaryText, state.summary)
            views.setTextViewText(R.id.widgetSlotOneText, state.slotOne)
            views.setTextViewText(R.id.widgetSlotTwoText, state.slotTwo)
            views.setTextViewText(R.id.widgetSlotThreeText, state.slotThree)

            // ウィジェットをタップしたら本体を開き、移行導線と詳細を見られるようにします。
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widgetRootLayout, pendingIntent)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
