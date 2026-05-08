package jp.co.cssservice.intulauncher

import android.content.Context
import android.content.Intent
import java.util.Locale

/**
 * 端末内の起動可能アプリを収集し、推薦候補として扱いやすい形へ整えるクラスです。
 */
class AppCatalog(private val context: Context) {
    /**
     * ランチャーから起動可能なアプリ一覧を読み込みます。
     */
    fun loadLaunchableApps(): List<LaunchableApp> {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return context.packageManager
            .queryIntentActivities(launcherIntent, 0)
            .mapNotNull { resolveInfo ->
                val packageName = resolveInfo.activityInfo.packageName
                // 自アプリ自身を候補に含めるとホームからホームを開く循環になるため除外します。
                if (packageName == context.packageName) {
                    return@mapNotNull null
                }
                val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return@mapNotNull null
                LaunchableApp(
                    packageName = packageName,
                    label = resolveInfo.loadLabel(context.packageManager).toString(),
                    icon = resolveInfo.loadIcon(context.packageManager),
                    launchIntent = launchIntent,
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
    }

    companion object {
        /**
         * キーワード群と未使用パッケージ条件をもとに最も適したアプリを返します。
         */
        fun findBestMatch(
            apps: List<LaunchableApp>,
            keywords: List<String>,
            usedPackages: Set<String> = emptySet(),
        ): LaunchableApp? {
            val loweredKeywords = keywords.map { it.lowercase(Locale.getDefault()) }
            // まずはラベル名やパッケージ名にキーワードが一致する候補を優先します。
            val directHit = apps.firstOrNull { app ->
                app.packageName !in usedPackages && loweredKeywords.any { keyword ->
                    app.label.lowercase(Locale.getDefault()).contains(keyword) ||
                        app.packageName.lowercase(Locale.getDefault()).contains(keyword)
                }
            }
            if (directHit != null) {
                return directHit
            }

            // 一致候補が見つからない場合は、未使用アプリの先頭をフォールバックとして返します。
            return apps.firstOrNull { it.packageName !in usedPackages }
        }
    }
}
