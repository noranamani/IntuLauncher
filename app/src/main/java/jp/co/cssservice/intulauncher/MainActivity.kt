package jp.co.cssservice.intulauncher

import android.animation.AnimatorSet
import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings.Secure
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
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
    /** Logcat 出力に使うタグです。 */
    private val logTag = "IntuLauncher"

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

    /** ホスト済みウィジェット ID を管理する設定クラスです。 */
    private lateinit var hostedWidgetPreferences: HostedWidgetPreferences

    /** 背景モード設定を保持するクラスです。 */
    private lateinit var visualModePreferences: VisualModePreferences

    /** 通知インサイトを管理するクラスです。 */
    private lateinit var notificationInsightStore: NotificationInsightStore

    /** 低消費電力の定期更新を登録するスケジューラです。 */
    private lateinit var adaptiveUpdateScheduler: AdaptiveUpdateScheduler

    /** 背景自動最適化ロジックを提供するクラスです。 */
    private val ambientVisualManager = AmbientVisualManager()

    /** デバイス内推論エンジンです。 */
    private val onDeviceModelEngine: OnDeviceModelEngine = HeuristicOnDeviceModelEngine()

    /** ホーム上でウィジェットを保持するためのホストです。 */
    private lateinit var appWidgetHost: AppWidgetHost

    /** ウィジェット操作用のマネージャです。 */
    private lateinit var appWidgetManager: AppWidgetManager

    /** 既存の利用統計を読み込み、初期順位に反映するためのクラスです。 */
    private lateinit var usageStatsImporter: UsageStatsImporter

    /** 現在のコンテキスト信号を読み取るクラスです。 */
    private lateinit var signalReader: ContextSignalReader

    /** アンカースロットやアプリ一覧ダイアログで使う全アプリ一覧です。 */
    private var launchableApps: List<LaunchableApp> = emptyList()

    /** スロットごとの前回表示アプリを保持し、入れ替わりアニメーション判定に使います。 */
    private val lastRenderedSlotPackages = mutableMapOf<Int, String?>()

    /** 背景遷移アニメーションの参照です。 */
    private var backgroundAnimator: ValueAnimator? = null

    /** 固定背景画像の選択ランチャーです。 */
    private val pickBackgroundLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
            visualModePreferences.setFixedImageUri(uri)
            visualModePreferences.setVisualMode(VisualMode.FIXED)
            refreshUi()
        }
    }

    /** 初期設定ウィザードから戻った後に UI を更新するランチャーです。 */
    private val setupWizardLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (this::onboardingSupportPreferences.isInitialized && onboardingSupportPreferences.hasCompletedSetup()) {
            refreshUi()
        }
    }

    /**
     * 初期化処理を行い、イベントハンドラを設定します。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ホーム画面では戻る操作で離脱しないようにし、誤操作で抜けるのを防ぎます。
        onBackPressedDispatcher.addCallback(this) {
            // no-op
        }

        appCatalog = AppCatalog(this)
        anchorPreferences = AnchorPreferences(this)
        coldStartPreferences = ColdStartPreferences(this)
        onboardingSupportPreferences = OnboardingSupportPreferences(this)
        widgetTrialStateStore = WidgetTrialStateStore(this)
        hostedWidgetPreferences = HostedWidgetPreferences(this)
        visualModePreferences = VisualModePreferences(this)
        notificationInsightStore = NotificationInsightStore(this)
        adaptiveUpdateScheduler = AdaptiveUpdateScheduler(this)
        usageStatsImporter = UsageStatsImporter(this)
        signalReader = ContextSignalReader(this)
        appWidgetManager = AppWidgetManager.getInstance(this)
        appWidgetHost = AppWidgetHost(this, APP_WIDGET_HOST_ID)
        notificationInsightStore.seedDemoIfEmpty()
        adaptiveUpdateScheduler.ensureScheduled()

        binding.openAllAppsButton.setOnClickListener {
            onboardingSupportPreferences.recordDrawerOpened()
            startActivity(Intent(this, AppDrawerActivity::class.java))
        }
        // ホーム全体ではなく下部ボタン長押しへ逃がし、通常タップとの競合を避けます。
        binding.openAllAppsButton.setOnLongClickListener {
            openSupportCenter()
            true
        }
        binding.quickSetupButton.setOnClickListener {
            openSetupWizard()
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
        binding.visualModeChip.setOnClickListener {
            showVisualModeDialog()
        }
        binding.selectBackgroundButton.setOnClickListener {
            pickFixedBackground()
        }
        binding.notificationSummaryButton.setOnClickListener {
            showNotificationInsightDialog()
        }
        binding.notificationAccessButton.setOnClickListener {
            openNotificationAccessSettings()
        }
        binding.technicalStatusButton.setOnClickListener {
            showTechnicalStatusDialog()
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

        // 初回起動時はカード型ウィザードを優先し、負担の少ない設定フローへ誘導します。
        if (!onboardingSupportPreferences.hasCompletedSetup()) {
            binding.root.post {
                openSetupWizard()
            }
        }
        binding.root.post {
            ensureHostedTrialWidget()
        }
    }

    /**
     * 画面復帰時に最新のコンテキストと学習状態で再描画します。
     */
    override fun onResume() {
        super.onResume()
        appWidgetHost.startListening()
        refreshUi()
    }

    /**
     * リスナーを停止してホスト負荷を抑えます。
     */
    override fun onPause() {
        super.onPause()
        appWidgetHost.stopListening()
    }

    /**
     * コンテキスト、コールドスタート状態、アンカー設定をまとめて描画します。
     */
    private fun refreshUi() {
        Log.i(logTag, "UI を再描画します。")
        launchableApps = appCatalog.loadLaunchableApps()
        onboardingSupportPreferences.recordPredictionExposure()
        val usageRanking = usageStatsImporter.loadUsageRanking()
        val rankedApps = rankAppsForColdStart(launchableApps, usageRanking)
        val snapshot = signalReader.readSnapshot()
        val launcherProfile = LauncherProfile.from(snapshot)
        val coldStartStatus = buildColdStartStatus(usageRanking)
        val slots = launcherProfile.resolveSlots(rankedApps)
        Log.i(logTag, "プロファイル=${launcherProfile.displayName}, 学習中=${coldStartStatus.isLearning}, 利用統計件数=${usageRanking.size}")
        notificationInsightStore.pruneExpiredRecords()
        updateWidgetTrialState(launcherProfile, coldStartStatus, slots)

        renderProfile(launcherProfile, snapshot, coldStartStatus)
        applyVisualMode(launcherProfile, snapshot)
        applyInteractionSizing(launcherProfile, snapshot)
        renderNotificationInsight(launcherProfile)
        renderHostedWidget()
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
        binding.greetingText.text = buildGreeting(snapshot)
        binding.headlineText.text = profile.headline
        binding.subheadlineText.text = profile.subheadline
        binding.profileChip.text = buildProfileChipLabel(profile, coldStartStatus)
        binding.timeText.text = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))
        binding.heroMetricsText.text = buildHeroMetrics(snapshot)
        binding.modeLabelText.text = when {
            profile == LauncherProfile.MORNING_COMMUTE -> getString(R.string.move_mode_label)
            snapshot.hourOfDay >= 20 || snapshot.hourOfDay <= 4 -> getString(R.string.night_mode_label)
            else -> profile.displayName
        }
        binding.signalSummaryText.text = buildSignalSummary(snapshot)
        binding.coldStartStatusText.text = coldStartStatus.summary
        binding.coldStartStatusText.alpha = if (coldStartStatus.isLearning) 0.95f else 0.78f
    }

    /**
     * 動的 3 スロットへコールドスタート状態を反映しながら描画します。
     */
    private fun renderDynamicSlots(slots: List<ResolvedSlot>, coldStartStatus: ColdStartStatus) {
        bindDynamicSlot(
            slotIndex = 0,
            card = binding.slotOneCard,
            iconView = binding.slotOneIcon,
            titleView = binding.slotOneTitle,
            subtitleView = binding.slotOneSubtitle,
            resolvedSlot = slots.getOrNull(0),
            coldStartStatus = coldStartStatus,
        )
        bindDynamicSlot(
            slotIndex = 1,
            card = binding.slotTwoCard,
            iconView = binding.slotTwoIcon,
            titleView = binding.slotTwoTitle,
            subtitleView = binding.slotTwoSubtitle,
            resolvedSlot = slots.getOrNull(1),
            coldStartStatus = coldStartStatus,
        )
        bindDynamicSlot(
            slotIndex = 2,
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
        slotIndex: Int,
        card: MaterialCardView,
        iconView: ImageView,
        titleView: TextView,
        subtitleView: TextView,
        resolvedSlot: ResolvedSlot?,
        coldStartStatus: ColdStartStatus,
    ) {
        card.alpha = if (coldStartStatus.isLearning) 0.96f else 1.0f

        // 候補が解決できない場合でも、全アプリ一覧へ逃がして操作を止めないようにします。
        if (resolvedSlot?.app == null) {
            iconView.setImageDrawable(ContextCompat.getDrawable(this, android.R.drawable.ic_menu_search))
            titleView.text = getString(R.string.slot_empty_title_short)
            subtitleView.text = getString(R.string.slot_empty_subtitle_short)
            card.setOnClickListener {
                showAppPicker(title = getString(R.string.app_picker_title), onSelected = ::launchApp)
            }
            animateSlotIfNeeded(card, slotIndex, null)
            return
        }

        iconView.setImageDrawable(resolvedSlot.app.icon)
        titleView.text = resolvedSlot.app.label
        // スロット内の情報量を絞り、行動ヒントだけを短く見せます。
        subtitleView.text = resolvedSlot.actionHint
        card.setOnClickListener {
            onboardingSupportPreferences.recordPredictionHit()
            launchApp(resolvedSlot.app)
        }
        animateSlotIfNeeded(card, slotIndex, resolvedSlot.app.packageName)
    }

    /**
     * 3 つのアンカースロットを描画します。
     */
    private fun renderAnchorSlots() {
        bindAnchorSlot(
            spec = defaultAnchorSlots.first(),
            card = binding.anchorOneCard,
            iconView = binding.anchorOneIcon,
            titleView = binding.anchorOneTitle,
            subtitleView = binding.anchorOneSubtitle,
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
        titleView.text = ""
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
            .setTitle(getString(R.string.app_picker_title))
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
        notificationInsightStore.recordTapped(app.packageName, app.label, 3)
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
     * 通知アクセス設定画面を開きます。
     */
    private fun openNotificationAccessSettings() {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    /**
     * 通知インサイトの表示を更新します。
     */
    private fun renderNotificationInsight(profile: LauncherProfile) {
        val insight = notificationInsightStore.buildInsight(profile)
        binding.notificationSummaryText.text = insight.summary
        binding.notificationPromptText.text = insight.prompt
        val shouldEmphasize = insight.prompt.isNotBlank()
        binding.notificationDimView.animate()
            .alpha(if (shouldEmphasize) 0.28f else 0f)
            .setDuration(420L)
            .start()
        startReplyPulse(shouldEmphasize)
    }

    /**
     * カード型の初期設定ウィザードを開きます。
     */
    private fun openSetupWizard() {
        setupWizardLauncher.launch(Intent(this, SetupWizardActivity::class.java))
    }

    /**
     * 設定・通知・レポート用の補助画面を開きます。
     */
    private fun openSupportCenter() {
        startActivity(Intent(this, LauncherSupportActivity::class.java))
    }

    /**
     * 通知インサイトの詳細ダイアログを表示します。
     */
    private fun showNotificationInsightDialog() {
        val insight = notificationInsightStore.buildInsight(LauncherProfile.FOCUS_WORK)
        val mutedSummary = if (insight.mutedPackages.isEmpty()) {
            getString(R.string.notification_muted_none)
        } else {
            insight.mutedPackages.joinToString(separator = "\n")
        }
        val message = buildString {
            appendLine(insight.summary)
            appendLine()
            appendLine(insight.prompt)
            appendLine()
            appendLine(getString(R.string.notification_muted_title))
            append(mutedSummary)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.notification_center_title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    /**
     * 技術基盤と権限状態のダイアログを表示します。
     */
    private fun showTechnicalStatusDialog() {
        val engineStatus = onDeviceModelEngine.buildStatus()
        val usageAccess = if (usageStatsImporter.hasAccessPermission()) {
            getString(R.string.technical_permission_enabled)
        } else {
            getString(R.string.technical_permission_disabled)
        }
        val notificationAccess = if (hasNotificationAccess()) {
            getString(R.string.technical_permission_enabled)
        } else {
            getString(R.string.technical_permission_disabled)
        }
        val message = buildString {
            appendLine(getString(R.string.technical_engine_name, engineStatus.engineName))
            appendLine(engineStatus.summary)
            appendLine()
            appendLine(getString(R.string.technical_update_strategy, engineStatus.updateStrategy))
            appendLine(getString(R.string.technical_usage_access, usageAccess))
            appendLine(getString(R.string.technical_notification_access, notificationAccess))
            append(getString(R.string.technical_accessibility_note))
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.technical_status_title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    /**
     * 通知アクセスが有効かどうかを返します。
     */
    private fun hasNotificationAccess(): Boolean {
        val enabledListeners = Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        return enabledListeners.contains(packageName)
    }

    /**
     * 背景モード選択ダイアログを表示します。
     */
    private fun showVisualModeDialog() {
        Log.i(logTag, "背景モード選択ダイアログを開きます。")
        val items = arrayOf(
            getString(R.string.visual_mode_ambient),
            getString(R.string.visual_mode_fixed),
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.visual_mode_title)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> {
                        visualModePreferences.setVisualMode(VisualMode.AMBIENT)
                        refreshUi()
                    }

                    else -> {
                        visualModePreferences.setVisualMode(VisualMode.FIXED)
                        pickFixedBackground()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * 固定背景画像の選択を開始します。
     */
    private fun pickFixedBackground() {
        pickBackgroundLauncher.launch(arrayOf("image/*"))
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

    /**
     * HOME として利用されている場合に、試行ウィジェットを自動配置します。
     */
    private fun ensureHostedTrialWidget() {
        val provider = ComponentName(this, IntuWidgetProvider::class.java)
        val existingWidgetId = hostedWidgetPreferences.getHostedWidgetId()
        if (existingWidgetId != null && appWidgetManager.getAppWidgetInfo(existingWidgetId) != null) {
            renderHostedWidget()
            return
        }

        // デフォルトホーム化されていない状態では、バインド許可が通らない可能性が高いため案内だけ出します。
        if (!isDefaultHome()) {
            binding.widgetHostStatusText.text = getString(R.string.widget_host_requires_home)
            return
        }

        val appWidgetId = appWidgetHost.allocateAppWidgetId()
        val isBound = appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, provider)
        if (isBound) {
            hostedWidgetPreferences.setHostedWidgetId(appWidgetId)
            binding.widgetHostStatusText.text = getString(R.string.widget_host_bound)
            renderHostedWidget()
            Log.i(logTag, "試行ウィジェットを自動配置しました。widgetId=$appWidgetId")
        } else {
            appWidgetHost.deleteAppWidgetId(appWidgetId)
            binding.widgetHostStatusText.text = getString(R.string.widget_host_fallback)
            renderHostedWidgetFallback()
            Log.w(logTag, "試行ウィジェットの自動配置に失敗したため、フォールバック表示へ切り替えました。")
        }
    }

    /**
     * ホスト済みウィジェットをホーム上へ描画します。
     */
    private fun renderHostedWidget() {
        val widgetId = hostedWidgetPreferences.getHostedWidgetId() ?: run {
            binding.widgetHostContainer.removeAllViews()
            return
        }
        val info = appWidgetManager.getAppWidgetInfo(widgetId) ?: run {
            binding.widgetHostContainer.removeAllViews()
            hostedWidgetPreferences.setHostedWidgetId(null)
            return
        }
        val hostView: AppWidgetHostView = appWidgetHost.createView(this, widgetId, info)
        hostView.setAppWidget(widgetId, info)
        binding.widgetHostContainer.removeAllViews()
        binding.widgetHostContainer.addView(
            hostView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        binding.widgetHostStatusText.text = getString(R.string.widget_host_active)
    }

    /**
     * システムバインド不可時に、同一見た目のウィジェットプレビューを表示します。
     */
    private fun renderHostedWidgetFallback() {
        val widgetState = widgetTrialStateStore.loadState()
        val fallbackView = layoutInflater.inflate(R.layout.app_widget_prediction, binding.widgetHostContainer, false)
        fallbackView.findViewById<TextView>(R.id.widgetProfileText).text = widgetState.profileLabel
        fallbackView.findViewById<TextView>(R.id.widgetSummaryText).text = widgetState.summary
        fallbackView.findViewById<TextView>(R.id.widgetSlotOneText).text = widgetState.slotOne
        fallbackView.findViewById<TextView>(R.id.widgetSlotTwoText).text = widgetState.slotTwo
        fallbackView.findViewById<TextView>(R.id.widgetSlotThreeText).text = widgetState.slotThree
        binding.widgetHostContainer.removeAllViews()
        binding.widgetHostContainer.addView(
            fallbackView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
    }

    /**
     * このアプリが現在のデフォルト HOME かどうかを返します。
     */
    private fun isDefaultHome(): Boolean {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = packageManager.resolveActivity(homeIntent, 0) ?: return false
        return resolved.activityInfo?.packageName == packageName
    }

    /**
     * 背景モードに応じたビジュアルを適用します。
     */
    private fun applyVisualMode(
        profile: LauncherProfile,
        snapshot: ContextSnapshot,
    ) {
        val notificationWeight = onboardingSupportPreferences.buildBenefitReport().predictionHitRate
        when (visualModePreferences.getVisualMode()) {
            VisualMode.AMBIENT -> {
                val state = ambientVisualManager.buildAmbientState(profile, snapshot, notificationWeight)
                animateBackgroundTo(state.backgroundColor)
                binding.fixedBackgroundImageView.setImageDrawable(null)
                binding.fixedBackgroundImageView.visibility = View.GONE
                binding.visualModeChip.text = state.mode.displayName
                binding.visualSummaryText.text = "${state.label} / ${state.description}"
                binding.ambientStatusText.visibility = View.VISIBLE
                binding.ambientStatusText.text = getString(R.string.ambient_sampling_status)
                binding.constellationOverlayView.animate()
                    .alpha(if (snapshot.hourOfDay >= 20 || snapshot.hourOfDay <= 4) 0.55f else 0f)
                    .setDuration(1200L)
                    .start()
            }

            VisualMode.FIXED -> {
                val fixedUri = visualModePreferences.getFixedImageUri()
                val state = ambientVisualManager.buildFixedState(fixedUri != null)
                if (fixedUri != null) {
                    animateBackgroundTo(ContextCompat.getColor(this, R.color.background_focus))
                    binding.fixedBackgroundImageView.setImageURI(fixedUri)
                    binding.fixedBackgroundImageView.visibility = View.VISIBLE
                } else {
                    binding.fixedBackgroundImageView.setImageDrawable(null)
                    binding.fixedBackgroundImageView.visibility = View.GONE
                    animateBackgroundTo(state.backgroundColor)
                }
                binding.visualModeChip.text = state.mode.displayName
                binding.visualSummaryText.text = "${state.label} / ${state.description}"
                binding.ambientStatusText.visibility = View.GONE
                binding.constellationOverlayView.animate().alpha(0f).setDuration(600L).start()
                if (fixedUri == null) {
                    binding.selectBackgroundButton.text = getString(R.string.select_background_button)
                } else {
                    binding.selectBackgroundButton.text = getString(R.string.change_background_button)
                }
            }
        }
    }

    /**
     * 移動中モードではボタンを大きくし、夜間は少し落ち着いた密度へ戻します。
     */
    private fun applyInteractionSizing(
        profile: LauncherProfile,
        snapshot: ContextSnapshot,
    ) {
        val isMoveMode = profile == LauncherProfile.MORNING_COMMUTE
        val isNightMode = snapshot.hourOfDay >= 20 || snapshot.hourOfDay <= 4
        val slotHeight = when {
            isMoveMode -> 136
            isNightMode -> 112
            else -> 114
        }
        val anchorHeight = if (isMoveMode) 108 else 92

        listOf(binding.slotOneCard, binding.slotTwoCard, binding.slotThreeCard).forEach { card ->
            val params = card.layoutParams
            params.height = dp(slotHeight)
            card.layoutParams = params
            card.radius = dp(if (isMoveMode) 28 else 24).toFloat()
            card.animate()
                .scaleX(if (isMoveMode) 1.02f else 1.0f)
                .scaleY(if (isMoveMode) 1.02f else 1.0f)
                .setDuration(420L)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }

        binding.anchorOneCard.layoutParams = binding.anchorOneCard.layoutParams.apply {
            height = dp(anchorHeight)
        }
        binding.anchorOneCard.radius = dp(if (isMoveMode) 34 else 30).toFloat()
        binding.anchorOneCard.animate()
            .scaleX(if (isMoveMode) 1.01f else 1.0f)
            .scaleY(if (isMoveMode) 1.01f else 1.0f)
            .setDuration(420L)
            .setInterpolator(DecelerateInterpolator())
            .start()
        binding.heroMetricsText.textSize = if (isMoveMode) 38f else 18f
        binding.modeLabelText.textSize = if (isMoveMode) 18f else 13f
    }

    /**
     * dp を整数ピクセルへ変換します。
     */
    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    /**
     * 時間帯に応じた挨拶文を返します。
     */
    private fun buildGreeting(snapshot: ContextSnapshot): String {
        val greeting = when (snapshot.hourOfDay) {
            in 5..10 -> getString(R.string.greeting_morning)
            in 11..17 -> getString(R.string.greeting_day)
            else -> getString(R.string.greeting_evening)
        }
        return "$greeting (${getString(R.string.header_location_unknown)})"
    }

    /**
     * ヘッダー中央に出す大きな状態行を返します。
     */
    private fun buildHeroMetrics(snapshot: ContextSnapshot): String {
        val time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))
        return "$time  •  ${getString(R.string.header_location_unknown)}  •  ${snapshot.batteryPercent}%"
    }

    /**
     * 背景色をゆっくり混ぜるように遷移させます。
     */
    private fun animateBackgroundTo(targetColor: Int) {
        val currentColor = (binding.root.background as? android.graphics.drawable.ColorDrawable)?.color
            ?: ContextCompat.getColor(this, R.color.background_focus)
        backgroundAnimator?.cancel()
        backgroundAnimator = ValueAnimator.ofObject(ArgbEvaluator(), currentColor, targetColor).apply {
            duration = 1800L
            addUpdateListener { animator ->
                binding.root.setBackgroundColor(animator.animatedValue as Int)
            }
            start()
        }
    }

    /**
     * スロット差し替え時に奥から手前へ出るような軽いバウンドを付けます。
     */
    private fun animateSlotIfNeeded(
        card: MaterialCardView,
        slotIndex: Int,
        packageName: String?,
    ) {
        val previousPackage = lastRenderedSlotPackages[slotIndex]
        if (previousPackage == packageName) {
            return
        }
        lastRenderedSlotPackages[slotIndex] = packageName
        if (previousPackage == null) {
            return
        }

        val alphaAnimator = ObjectAnimator.ofFloat(card, View.ALPHA, 0.55f, 1f)
        val scaleXAnimator = ObjectAnimator.ofFloat(card, View.SCALE_X, 0.94f, 1.03f, 1f)
        val scaleYAnimator = ObjectAnimator.ofFloat(card, View.SCALE_Y, 0.94f, 1.03f, 1f)
        val translationAnimator = ObjectAnimator.ofFloat(card, View.TRANSLATION_Y, 18f, -6f, 0f)

        AnimatorSet().apply {
            playTogether(alphaAnimator, scaleXAnimator, scaleYAnimator, translationAnimator)
            duration = 540L
            interpolator = OvershootInterpolator(1.15f)
            start()
        }
    }

    /**
     * 通知返信ボタンへ軽いパルス感を付けます。
     */
    private fun startReplyPulse(shouldPulse: Boolean) {
        binding.notificationSummaryButton.animate().cancel()
        if (!shouldPulse) {
            binding.notificationSummaryButton.scaleX = 1f
            binding.notificationSummaryButton.scaleY = 1f
            return
        }
        binding.notificationSummaryButton.animate()
            .scaleX(1.04f)
            .scaleY(1.04f)
            .setDuration(360L)
            .withEndAction {
                binding.notificationSummaryButton.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(360L)
                    .start()
            }
            .start()
    }

    /**
     * 背景モードをトグルします。
     */
    private fun toggleVisualMode() {
        val nextMode = if (visualModePreferences.getVisualMode() == VisualMode.AMBIENT) {
            VisualMode.FIXED
        } else {
            VisualMode.AMBIENT
        }
        Log.i(logTag, "背景モードを切り替えます: $nextMode")
        visualModePreferences.setVisualMode(nextMode)
        if (nextMode == VisualMode.FIXED && visualModePreferences.getFixedImageUri() == null) {
            pickFixedBackground()
        } else {
            refreshUi()
        }
    }

    /**
     * 固定背景画像 URI から描画可能な Drawable を生成します。
     */
    private fun loadFixedBackgroundDrawable(uri: Uri): Drawable? {
        return runCatching {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                Drawable.createFromStream(inputStream, uri.toString())
            }
        }.getOrNull()
    }

    companion object {
        /** ウィジェットホストに使う固定 ID です。 */
        private const val APP_WIDGET_HOST_ID = 2048
    }
}
