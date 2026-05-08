package jp.co.cssservice.intulauncher

import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.provider.Settings
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import jp.co.cssservice.intulauncher.databinding.ActivityMainBinding
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * IntuLauncher のホーム画面本体です。
 */
class MainActivity : AppCompatActivity() {
    /** 画面要素へアクセスするための ViewBinding です。 */
    private lateinit var binding: ActivityMainBinding

    /** 端末内の起動可能アプリ一覧を取得するカタログです。 */
    private lateinit var appCatalog: AppCatalog

    /** アンカースロットの固定設定を保持する設定クラスです。 */
    private lateinit var anchorPreferences: AnchorPreferences

    /** 初回導入時のプロファイルと学習期間を管理する設定クラスです。 */
    private lateinit var coldStartPreferences: ColdStartPreferences

    /** 導入支援と ROI 集計を管理する設定クラスです。 */
    private lateinit var onboardingSupportPreferences: OnboardingSupportPreferences

    /** 予測ウィジェットの表示状態を保持するクラスです。 */
    private lateinit var widgetTrialStateStore: WidgetTrialStateStore

    /** 既存の利用統計を読み込み、初期順位に反映するためのクラスです。 */
    private lateinit var usageStatsImporter: UsageStatsImporter

    /** 現在のコンテキスト信号を読み取るクラスです。 */
    private lateinit var signalReader: ContextSignalReader

    /** アンカースロットやアプリ一覧ダイアログで使う全アプリ一覧です。 */
    private var launchableApps: List<LaunchableApp> = emptyList()

    /**
     * 初期化処理を行い、イベントハンドラを設定します。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appCatalog = AppCatalog(this)
        anchorPreferences = AnchorPreferences(this)
        coldStartPreferences = ColdStartPreferences(this)
        onboardingSupportPreferences = OnboardingSupportPreferences(this)
        widgetTrialStateStore = WidgetTrialStateStore(this)
        usageStatsImporter = UsageStatsImporter(this)
        signalReader = ContextSignalReader(this)

        binding.openAllAppsButton.setOnClickListener {
            onboardingSupportPreferences.recordDrawerOpened()
            showAppPicker(title = getString(R.string.app_picker_title)) { app ->
                launchApp(app)
            }
        }
        binding.quickSetupButton.setOnClickListener {
            showQuickSetupDialog()
        }
        binding.migrationInsightButton.setOnClickListener {
            showMigrationInsightDialog()
        }
        binding.benefitDashboardButton.setOnClickListener {
            showBenefitDashboardDialog()
        }
        binding.previewButton.setOnClickListener {
            showHomePreviewDialog()
        }
        binding.widgetTrialButton.setOnClickListener {
            showWidgetTrialDialog()
        }
        binding.rollbackHomeButton.setOnClickListener {
            openHomeSettings()
        }
        binding.profileChip.setOnClickListener {
            showColdStartProfileDialog()
        }

        // 利用統計アクセスが未許可の間だけ、状態表示から設定画面へ移動できるようにします。
        binding.coldStartStatusText.setOnClickListener {
            if (!usageStatsImporter.hasAccessPermission()) {
                openUsageAccessSettings()
            }
        }

        // 初回起動時は最初のランキング方針を早めに決められるよう、導入ダイアログを出します。
        if (coldStartPreferences.getSelectedProfile() == null) {
            binding.root.post {
                showColdStartProfileDialog()
            }
        }
    }

    /**
     * 画面復帰時に最新のコンテキストと学習状態で再描画します。
     */
    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    /**
     * コンテキスト、コールドスタート状態、アンカー設定をまとめて描画します。
     */
    private fun refreshUi() {
        launchableApps = appCatalog.loadLaunchableApps()
        onboardingSupportPreferences.recordPredictionExposure()
        val usageRanking = usageStatsImporter.loadUsageRanking()
        val rankedApps = rankAppsForColdStart(launchableApps, usageRanking)
        val snapshot = signalReader.readSnapshot()
        val launcherProfile = LauncherProfile.from(snapshot)
        val coldStartStatus = buildColdStartStatus(usageRanking)
        val slots = launcherProfile.resolveSlots(rankedApps)
        updateWidgetTrialState(launcherProfile, coldStartStatus, slots)

        renderProfile(launcherProfile, snapshot, coldStartStatus)
        renderDynamicSlots(slots, coldStartStatus)
        renderAnchorSlots()
    }

    /**
     * 現在のホームプロファイルと学習状態をヘッダへ反映します。
     */
    private fun renderProfile(
        profile: LauncherProfile,
        snapshot: ContextSnapshot,
        coldStartStatus: ColdStartStatus,
    ) {
        binding.rootLayout.setBackgroundColor(ContextCompat.getColor(this, profile.backgroundColor))
        binding.headlineText.text = profile.headline
        binding.subheadlineText.text = profile.subheadline
        binding.profileChip.text = buildProfileChipLabel(profile, coldStartStatus)
        binding.timeText.text = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))
        binding.signalSummaryText.text = buildSignalSummary(snapshot)
        binding.coldStartStatusText.text = coldStartStatus.summary
        binding.coldStartStatusText.alpha = if (coldStartStatus.isLearning) 0.95f else 0.78f
    }

    /**
     * 動的 3 スロットへコールドスタート状態を反映しながら描画します。
     */
    private fun renderDynamicSlots(slots: List<ResolvedSlot>, coldStartStatus: ColdStartStatus) {
        bindDynamicSlot(
            card = binding.slotOneCard,
            iconView = binding.slotOneIcon,
            titleView = binding.slotOneTitle,
            subtitleView = binding.slotOneSubtitle,
            resolvedSlot = slots.getOrNull(0),
            coldStartStatus = coldStartStatus,
        )
        bindDynamicSlot(
            card = binding.slotTwoCard,
            iconView = binding.slotTwoIcon,
            titleView = binding.slotTwoTitle,
            subtitleView = binding.slotTwoSubtitle,
            resolvedSlot = slots.getOrNull(1),
            coldStartStatus = coldStartStatus,
        )
        bindDynamicSlot(
            card = binding.slotThreeCard,
            iconView = binding.slotThreeIcon,
            titleView = binding.slotThreeTitle,
            subtitleView = binding.slotThreeSubtitle,
            resolvedSlot = slots.getOrNull(2),
            coldStartStatus = coldStartStatus,
        )
    }

    /**
     * 1 つの動的スロットを描画し、学習中の案内も重ねて表示します。
     */
    private fun bindDynamicSlot(
        card: MaterialCardView,
        iconView: ImageView,
        titleView: TextView,
        subtitleView: TextView,
        resolvedSlot: ResolvedSlot?,
        coldStartStatus: ColdStartStatus,
    ) {
        card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.panel_surface))
        card.alpha = if (coldStartStatus.isLearning) 0.96f else 1.0f

        // 候補が解決できない場合でも、全アプリ一覧へ逃がして操作を止めないようにします。
        if (resolvedSlot?.app == null) {
            iconView.setImageDrawable(ContextCompat.getDrawable(this, android.R.drawable.ic_menu_search))
            titleView.text = getString(R.string.slot_empty_title)
            subtitleView.text = getString(R.string.slot_empty_subtitle)
            card.setOnClickListener {
                showAppPicker(title = getString(R.string.app_picker_title), onSelected = ::launchApp)
            }
            return
        }

        iconView.setImageDrawable(resolvedSlot.app.icon)
        titleView.text = "${resolvedSlot.spec.title} / ${resolvedSlot.app.label}"

        val slotKindLabel = if (resolvedSlot.spec.kind == SlotKind.DISCOVERY) {
            getString(R.string.slot_kind_discovery)
        } else {
            getString(R.string.slot_kind_prediction)
        }
        val learningLabel = if (coldStartStatus.isLearning) {
            getString(R.string.learning_badge_prefix)
        } else {
            ""
        }
        subtitleView.text = "$learningLabel$slotKindLabel / ${resolvedSlot.actionHint}"
        card.setOnClickListener { launchApp(resolvedSlot.app) }
        card.setOnClickListener {
            onboardingSupportPreferences.recordPredictionHit()
            launchApp(resolvedSlot.app)
        }
    }

    /**
     * 3 つのアンカースロットを描画します。
     */
    private fun renderAnchorSlots() {
        bindAnchorSlot(
            spec = defaultAnchorSlots[0],
            card = binding.anchorOneCard,
            iconView = binding.anchorOneIcon,
            titleView = binding.anchorOneTitle,
            subtitleView = binding.anchorOneSubtitle,
        )
        bindAnchorSlot(
            spec = defaultAnchorSlots[1],
            card = binding.anchorTwoCard,
            iconView = binding.anchorTwoIcon,
            titleView = binding.anchorTwoTitle,
            subtitleView = binding.anchorTwoSubtitle,
        )
        bindAnchorSlot(
            spec = defaultAnchorSlots[2],
            card = binding.anchorThreeCard,
            iconView = binding.anchorThreeIcon,
            titleView = binding.anchorThreeTitle,
            subtitleView = binding.anchorThreeSubtitle,
        )
    }

    /**
     * 1 つのアンカースロットへ固定アプリまたは初期候補を表示します。
     */
    private fun bindAnchorSlot(
        spec: AnchorSlotSpec,
        card: MaterialCardView,
        iconView: ImageView,
        titleView: TextView,
        subtitleView: TextView,
    ) {
        val pinnedPackage = anchorPreferences.getPinnedPackage(spec.key)
        val resolvedApp = launchableApps.firstOrNull { it.packageName == pinnedPackage }
            ?: AppCatalog.findBestMatch(launchableApps, spec.defaultKeywords)

        iconView.setImageDrawable(resolvedApp?.icon ?: defaultAnchorIcon())
        titleView.text = spec.title
        subtitleView.text = resolvedApp?.label ?: getString(R.string.anchor_unset)

        card.setOnClickListener {
            if (resolvedApp != null) {
                onboardingSupportPreferences.recordAnchorLaunch()
                launchApp(resolvedApp)
            } else {
                showAnchorPicker(spec, isPinned = false)
            }
        }
        // 長押し時だけ固定先の変更ダイアログを開き、通常タップの起動導線を壊さないようにします。
        card.setOnLongClickListener {
            showAnchorPicker(spec, isPinned = pinnedPackage != null)
            true
        }
    }

    /**
     * アンカースロット用のアプリ選択ダイアログを表示します。
     */
    private fun showAnchorPicker(spec: AnchorSlotSpec, isPinned: Boolean) {
        val adapter = AppPickerAdapter(this, launchableApps)
        val dialog = AlertDialog.Builder(this)
            .setTitle("${spec.title}: ${getString(R.string.app_picker_title)}")
            .setAdapter(adapter) { _, which ->
                val app = launchableApps[which]
                anchorPreferences.setPinnedPackage(spec.key, app.packageName)
                refreshUi()
            }
            .setNegativeButton(R.string.cancel, null)

        if (isPinned) {
            dialog.setNeutralButton(R.string.clear_anchor) { _, _ ->
                anchorPreferences.setPinnedPackage(spec.key, null)
                refreshUi()
            }
        }

        dialog.show()
    }

    /**
     * 初期プロファイル選択ダイアログを表示します。
     */
    private fun showColdStartProfileDialog() {
        val profiles = ColdStartProfile.entries.toTypedArray()
        val items = profiles.map { profile ->
            "${profile.displayName} / ${buildProfileDescription(profile)}"
        }.toTypedArray()

        val builder = AlertDialog.Builder(this)
            .setTitle(R.string.cold_start_profile_title)
            .setItems(items) { _, which ->
                coldStartPreferences.setSelectedProfile(profiles[which])
                refreshUi()
            }
            .setNegativeButton(R.string.cancel, null)

        // 利用統計アクセスがあると、選択した属性プリセットに既存の利用傾向も合成できます。
        if (!usageStatsImporter.hasAccessPermission()) {
            builder.setNeutralButton(R.string.open_usage_access_settings) { _, _ ->
                openUsageAccessSettings()
            }
        }

        builder.show()
    }

    /**
     * 5 問の簡易セットアップを順番に表示します。
     */
    private fun showQuickSetupDialog() {
        val answers = mutableListOf<Int>()
        val questions = listOf(
            SetupQuestion(
                title = getString(R.string.setup_question_primary_goal),
                options = listOf(
                    getString(R.string.setup_answer_business),
                    getString(R.string.setup_answer_student),
                    getString(R.string.setup_answer_entertainment),
                ),
            ),
            SetupQuestion(
                title = getString(R.string.setup_question_handedness),
                options = Handedness.entries.map { it.displayName },
            ),
            SetupQuestion(
                title = getString(R.string.setup_question_density),
                options = IconDensity.entries.map { it.displayName },
            ),
            SetupQuestion(
                title = getString(R.string.setup_question_trial),
                options = listOf(
                    getString(R.string.setup_answer_trial_first),
                    getString(R.string.setup_answer_full_launcher),
                ),
            ),
            SetupQuestion(
                title = getString(R.string.setup_question_confirmation),
                options = listOf(
                    getString(R.string.setup_answer_keep_fast),
                    getString(R.string.setup_answer_keep_safe),
                ),
            ),
        )
        showSetupQuestionStep(questions, answers, 0)
    }

    /**
     * セットアップの各質問を再帰的に進めます。
     */
    private fun showSetupQuestionStep(
        questions: List<SetupQuestion>,
        answers: MutableList<Int>,
        index: Int,
    ) {
        if (index >= questions.size) {
            completeQuickSetup(answers)
            return
        }
        val question = questions[index]
        AlertDialog.Builder(this)
            .setTitle(question.title)
            .setItems(question.options.toTypedArray()) { _, which ->
                answers += which
                showSetupQuestionStep(questions, answers, index + 1)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * セットアップ回答から初期設定を決定します。
     */
    private fun completeQuickSetup(answers: List<Int>) {
        val profile = when (answers.getOrNull(0) ?: 0) {
            1 -> ColdStartProfile.STUDENT
            2 -> ColdStartProfile.ENTERTAINMENT
            else -> ColdStartProfile.BUSINESS
        }
        val handedness = Handedness.entries.getOrElse(answers.getOrNull(1) ?: 0) { Handedness.RIGHT }
        val density = IconDensity.entries.getOrElse(answers.getOrNull(2) ?: 1) { IconDensity.BALANCED }
        val prefersTrialWidget = (answers.getOrNull(3) ?: 0) == 0

        coldStartPreferences.setSelectedProfile(profile)
        onboardingSupportPreferences.saveSetupResult(profile, handedness, density, prefersTrialWidget)
        refreshUi()
        AlertDialog.Builder(this)
            .setTitle(R.string.setup_completed_title)
            .setMessage(
                getString(
                    R.string.setup_completed_message,
                    profile.displayName,
                    handedness.displayName,
                    density.displayName,
                ),
            )
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    /**
     * 段階的移行の推奨内容を表示します。
     */
    private fun showMigrationInsightDialog() {
        val insight = onboardingSupportPreferences.buildMigrationInsight()
        val message = buildString {
            appendLine(insight.summary)
            appendLine()
            appendLine(getString(R.string.migration_gravity_label, insight.gravityHint))
            append(getString(R.string.migration_density_label, insight.iconDensityHint))
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.migration_insight_title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    /**
     * 時短レポートを表示します。
     */
    private fun showBenefitDashboardDialog() {
        val report = onboardingSupportPreferences.buildBenefitReport()
        val drawerSummary = report.daysSinceDrawerOpen?.let {
            getString(R.string.benefit_days_since_drawer, it)
        } ?: getString(R.string.benefit_days_since_drawer_unknown)
        val message = buildString {
            appendLine(report.summary)
            appendLine()
            appendLine(getString(R.string.benefit_saved_seconds, report.savedSeconds))
            appendLine(getString(R.string.benefit_prediction_hit_rate, report.predictionHitRate))
            append(drawerSummary)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.benefit_dashboard_title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    /**
     * 数時間後のホーム変化プレビューを表示します。
     */
    private fun showHomePreviewDialog() {
        val message = onboardingSupportPreferences.buildPreviewScenarios().joinToString("\n\n") { preview ->
            getString(
                R.string.preview_item_format,
                preview.hoursAhead,
                preview.profileName,
                preview.slotSummary,
            )
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.preview_title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    /**
     * 予測ウィジェットの学習状況と移行提案を表示します。
     */
    private fun showWidgetTrialDialog() {
        val report = onboardingSupportPreferences.buildBenefitReport()
        val shouldSuggestMigration = report.predictionHitRate >= 45 || report.savedSeconds >= 60
        val message = buildString {
            appendLine(getString(R.string.widget_trial_learning_message))
            appendLine()
            appendLine(getString(R.string.widget_trial_hit_rate, report.predictionHitRate))
            appendLine(getString(R.string.widget_trial_saved_seconds, report.savedSeconds))
            appendLine()
            append(
                if (shouldSuggestMigration) {
                    getString(R.string.widget_trial_conversion_ready)
                } else {
                    getString(R.string.widget_trial_continue_learning)
                },
            )
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.widget_trial_title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    /**
     * 全アプリ一覧ダイアログを表示します。
     */
    private fun showAppPicker(title: String, onSelected: (LaunchableApp) -> Unit) {
        val adapter = AppPickerAdapter(this, launchableApps)
        AlertDialog.Builder(this)
            .setTitle(title)
            .setAdapter(adapter) { _, which ->
                onSelected(launchableApps[which])
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * 指定アプリを起動します。
     */
    private fun launchApp(app: LaunchableApp) {
        startActivity(app.launchIntent)
    }

    /**
     * 利用統計アクセス設定画面を開きます。
     */
    private fun openUsageAccessSettings() {
        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    /**
     * ホームアプリ設定画面を開き、元のホームへ戻しやすくします。
     */
    private fun openHomeSettings() {
        startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
    }

    /**
     * コールドスタート向けにアプリ一覧を並べ替えます。
     */
    private fun rankAppsForColdStart(
        apps: List<LaunchableApp>,
        usageRanking: Map<String, Long>,
    ): List<LaunchableApp> {
        val selectedProfile = coldStartPreferences.getSelectedProfile()
        if (usageRanking.isEmpty() && selectedProfile == null) {
            return apps
        }

        return apps.sortedWith(
            compareByDescending<LaunchableApp> { app ->
                scoreForColdStart(app, usageRanking, selectedProfile)
            }.thenBy {
                it.label.lowercase(Locale.getDefault())
            },
        )
    }

    /**
     * 利用統計と属性プリセットを合成した初期スコアを返します。
     */
    private fun scoreForColdStart(
        app: LaunchableApp,
        usageRanking: Map<String, Long>,
        selectedProfile: ColdStartProfile?,
    ): Long {
        val usageScore = usageRanking[app.packageName] ?: 0L
        val profileBoost = selectedProfile?.let { profile ->
            val matchCount = countBoostKeywordMatches(app, profile.boostKeywords)
            // 利用履歴が薄い端末でも、最初の 3 スロットに属性の違いが出るように大きめに加点します。
            matchCount * 7_200_000L
        } ?: 0L
        return usageScore + profileBoost
    }

    /**
     * 属性プリセットのキーワード一致数を数えます。
     */
    private fun countBoostKeywordMatches(app: LaunchableApp, keywords: List<String>): Long {
        val label = app.label.lowercase(Locale.getDefault())
        val packageName = app.packageName.lowercase(Locale.getDefault())
        return keywords.count { keyword ->
            val loweredKeyword = keyword.lowercase(Locale.getDefault())
            label.contains(loweredKeyword) || packageName.contains(loweredKeyword)
        }.toLong()
    }

    /**
     * コールドスタート状態を説明用の表示モデルへまとめます。
     */
    private fun buildColdStartStatus(usageRanking: Map<String, Long>): ColdStartStatus {
        val selectedProfile = coldStartPreferences.getSelectedProfile()
        val isLearning = coldStartPreferences.isLearningPhase()
        val usingUsageStats = usageRanking.isNotEmpty()
        val remainingDays = (3L - coldStartPreferences.getElapsedDays()).coerceAtLeast(0L)

        val summary = when {
            selectedProfile == null && usingUsageStats ->
                getString(R.string.cold_start_summary_without_profile_with_usage)
            selectedProfile == null ->
                getString(R.string.cold_start_summary_without_profile)
            isLearning && usingUsageStats ->
                getString(R.string.cold_start_summary_learning_with_usage, selectedProfile.displayName, remainingDays)
            isLearning ->
                getString(R.string.cold_start_summary_learning_without_usage, selectedProfile.displayName, remainingDays)
            usingUsageStats ->
                getString(R.string.cold_start_summary_ready_with_usage, selectedProfile.displayName)
            else ->
                getString(R.string.cold_start_summary_ready_without_usage, selectedProfile.displayName)
        }

        return ColdStartStatus(
            selectedProfile = selectedProfile,
            isLearning = isLearning,
            usingUsageStats = usingUsageStats,
            summary = summary,
        )
    }

    /**
     * ヘッダのチップ表示文言を組み立てます。
     */
    private fun buildProfileChipLabel(
        profile: LauncherProfile,
        coldStartStatus: ColdStartStatus,
    ): String {
        val selectedProfileLabel = coldStartStatus.selectedProfile?.displayName ?: getString(R.string.cold_start_profile_unselected)
        return "${profile.displayName} / $selectedProfileLabel"
    }

    /**
     * プロファイル選択ダイアログ用の補足説明を返します。
     */
    private fun buildProfileDescription(profile: ColdStartProfile): String {
        return when (profile) {
            ColdStartProfile.BUSINESS -> getString(R.string.cold_start_profile_business_description)
            ColdStartProfile.STUDENT -> getString(R.string.cold_start_profile_student_description)
            ColdStartProfile.ENTERTAINMENT -> getString(R.string.cold_start_profile_entertainment_description)
        }
    }

    /**
     * 現在のコンテキストを人が読める要約へ変換します。
     */
    private fun buildSignalSummary(snapshot: ContextSnapshot): String {
        val chargingLabel = if (snapshot.isCharging) {
            getString(R.string.signal_charging_on)
        } else {
            getString(R.string.signal_charging_off)
        }
        val audioLabel = if (snapshot.hasHeadphones) {
            getString(R.string.signal_audio_headphones)
        } else {
            getString(R.string.signal_audio_speaker)
        }
        val postureLabel = if (snapshot.isLandscape) {
            getString(R.string.signal_posture_landscape)
        } else {
            getString(R.string.signal_posture_portrait)
        }
        return getString(
            R.string.signal_summary_format,
            snapshot.hourOfDay,
            chargingLabel,
            audioLabel,
            postureLabel,
            snapshot.batteryPercent,
        )
    }

    /**
     * アンカー未設定時に使う代替アイコンを返します。
     */
    private fun defaultAnchorIcon(): Drawable? {
        return ContextCompat.getDrawable(this, android.R.drawable.star_big_off)
    }

    /**
     * 現在のスロット状態を予測ウィジェットへ同期します。
     */
    private fun updateWidgetTrialState(
        profile: LauncherProfile,
        coldStartStatus: ColdStartStatus,
        slots: List<ResolvedSlot>,
    ) {
        val slotLabels = slots.map { resolvedSlot ->
            resolvedSlot.app?.let { "${resolvedSlot.spec.title}: ${it.label}" } ?: "${resolvedSlot.spec.title}: 準備中"
        }
        widgetTrialStateStore.saveState(
            profileLabel = "${profile.displayName} / Trial",
            summary = coldStartStatus.summary,
            slotLabels = slotLabels,
        )
        IntuWidgetProvider.refreshAllWidgets(this)
    }
}
