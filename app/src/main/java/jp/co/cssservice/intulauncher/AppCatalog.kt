package jp.co.cssservice.intulauncher

import android.content.Context
import android.content.Intent
import java.util.Locale

class AppCatalog(private val context: Context) {
    fun loadLaunchableApps(): List<LaunchableApp> {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return context.packageManager
            .queryIntentActivities(launcherIntent, 0)
            .mapNotNull { resolveInfo ->
                val packageName = resolveInfo.activityInfo.packageName
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
        fun findBestMatch(
            apps: List<LaunchableApp>,
            keywords: List<String>,
            usedPackages: Set<String> = emptySet(),
        ): LaunchableApp? {
            val loweredKeywords = keywords.map { it.lowercase(Locale.getDefault()) }
            val directHit = apps.firstOrNull { app ->
                app.packageName !in usedPackages && loweredKeywords.any { keyword ->
                    app.label.lowercase(Locale.getDefault()).contains(keyword) ||
                        app.packageName.lowercase(Locale.getDefault()).contains(keyword)
                }
            }
            if (directHit != null) {
                return directHit
            }

            return apps.firstOrNull { it.packageName !in usedPackages }
        }
    }
}

