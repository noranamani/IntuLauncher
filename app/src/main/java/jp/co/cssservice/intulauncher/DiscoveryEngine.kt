package jp.co.cssservice.intulauncher

import java.time.LocalDate
import java.util.Locale

/**
 * 習慣化し過ぎた推薦だけに寄らないよう、軽い偶然性を持つ候補を返す補助クラスです。
 */
object DiscoveryEngine {
    /**
     * 既存の予測候補と重ならない発見枠アプリを選びます。
     */
    fun findDiscoveryPick(
        apps: List<LaunchableApp>,
        seedKeywords: List<String>,
        usedPackages: Set<String>,
    ): LaunchableApp? {
        val keywordSet = seedKeywords.map { it.lowercase(Locale.getDefault()) }

        val ranked = apps
            .filterNot { it.packageName in usedPackages }
            .filterNot { app ->
                val loweredLabel = app.label.lowercase(Locale.getDefault())
                // 予測枠と同じキーワードに吸い寄せられるアプリは Discovery 枠から外します。
                keywordSet.any { keyword ->
                    app.packageName.lowercase(Locale.getDefault()).contains(keyword) ||
                        loweredLabel.contains(keyword)
                }
            }
            .sortedWith(compareBy<LaunchableApp> { seasonalWeight(it) }.thenBy { labelWeight(it) })

        if (ranked.isEmpty()) {
            return AppCatalog.findBestMatch(apps, seedKeywords, usedPackages)
        }

        // 日付に応じて候補位置をずらし、毎回まったく同じ順番にならないようにします。
        val dayIndex = LocalDate.now().dayOfYear % ranked.size
        return ranked[dayIndex]
    }

    /**
     * 季節性や文脈復帰のしやすさを表す簡易重みです。
     */
    private fun seasonalWeight(app: LaunchableApp): Int {
        val label = app.label.lowercase(Locale.getDefault())
        return when {
            "camera" in label || "photo" in label -> 1
            "weather" in label || "tenki" in label -> 2
            "kindle" in label || "book" in label -> 3
            else -> 10
        }
    }

    /**
     * 同点時の並びを安定させるためのラベル比較キーです。
     */
    private fun labelWeight(app: LaunchableApp): String = app.label.lowercase(Locale.getDefault()).take(32)
}
