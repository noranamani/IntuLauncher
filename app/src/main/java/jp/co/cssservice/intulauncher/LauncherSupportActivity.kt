package jp.co.cssservice.intulauncher

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import jp.co.cssservice.intulauncher.databinding.ActivityLauncherSupportBinding

/**
 * ホーム画面から分離した設定・通知・レポート用の補助画面です。
 */
class LauncherSupportActivity : AppCompatActivity() {
    /** 画面要素へアクセスする ViewBinding です。 */
    private lateinit var binding: ActivityLauncherSupportBinding

    /** 導入支援とレポート情報を扱うクラスです。 */
    private lateinit var onboardingSupportPreferences: OnboardingSupportPreferences

    /** 通知インサイトを扱うクラスです。 */
    private lateinit var notificationInsightStore: NotificationInsightStore

    /** 利用状況アクセス状態を扱うクラスです。 */
    private lateinit var usageStatsImporter: UsageStatsImporter

    /** 現在コンテキストを読むクラスです。 */
    private lateinit var signalReader: ContextSignalReader

    /** 背景モード設定を扱うクラスです。 */
    private lateinit var visualModePreferences: VisualModePreferences

    /** 固定背景画像の選択ランチャーです。 */
    private val pickBackgroundLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
            visualModePreferences.setFixedImageUri(uri)
            visualModePreferences.setVisualMode(VisualMode.FIXED)
            renderVisualModeStatus()
        }
    }

    /**
     * 初期化と各操作のイベント登録を行います。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLauncherSupportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        onboardingSupportPreferences = OnboardingSupportPreferences(this)
        notificationInsightStore = NotificationInsightStore(this)
        usageStatsImporter = UsageStatsImporter(this)
        signalReader = ContextSignalReader(this)
        visualModePreferences = VisualModePreferences(this)

        binding.setupButton.setOnClickListener {
            startActivity(Intent(this, SetupWizardActivity::class.java))
        }
        binding.visualModeButton.setOnClickListener {
            showVisualModeDialog()
        }
        binding.fixedBackgroundButton.setOnClickListener {
            pickFixedBackground()
        }
        binding.notificationButton.setOnClickListener {
            showNotificationInsightDialog()
        }
        binding.reportButton.setOnClickListener {
            showBenefitDashboardDialog()
        }
        binding.previewButton.setOnClickListener {
            showPreviewDialog()
        }
        binding.homeSettingsButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        }
        binding.usageAccessButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        binding.notificationAccessButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        renderVisualModeStatus()
    }

    /**
     * 画面へ戻った際に背景モード状態を更新します。
     */
    override fun onResume() {
        super.onResume()
        renderVisualModeStatus()
    }

    /**
     * 通知インサイトの詳細を表示します。
     */
    private fun showNotificationInsightDialog() {
        val profile = LauncherProfile.from(signalReader.readSnapshot())
        val insight = notificationInsightStore.buildInsight(profile)
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
     * 時短レポートを表示します。
     */
    private fun showBenefitDashboardDialog() {
        val report = onboardingSupportPreferences.buildBenefitReport()
        val message = buildString {
            appendLine(getString(R.string.benefit_saved_seconds, report.savedSeconds))
            appendLine(getString(R.string.benefit_prediction_hit_rate, report.predictionHitRate))
            appendLine(
                report.daysSinceDrawerOpen?.let { getString(R.string.benefit_days_since_drawer, it) }
                    ?: getString(R.string.benefit_days_since_drawer_unknown),
            )
            appendLine()
            append(report.summary)
            appendLine()
            appendLine()
            append(
                if (usageStatsImporter.hasAccessPermission()) {
                    getString(R.string.technical_permission_enabled)
                } else {
                    getString(R.string.technical_permission_disabled)
                },
            )
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.benefit_dashboard_title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    /**
     * ホーム変化のプレビューを表示します。
     */
    private fun showPreviewDialog() {
        val previews = onboardingSupportPreferences.buildPreviewScenarios()
        val message = previews.joinToString(separator = "\n\n") { preview ->
            getString(R.string.preview_item_format, preview.hoursAhead, preview.profileName, preview.slotSummary)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.preview_title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    /**
     * 背景モードの現在状態を補助画面へ反映します。
     */
    private fun renderVisualModeStatus() {
        val modeLabel = when (visualModePreferences.getVisualMode()) {
            VisualMode.AMBIENT -> getString(R.string.visual_mode_ambient)
            VisualMode.FIXED -> getString(R.string.visual_mode_fixed)
        }
        val hasFixedImage = visualModePreferences.getFixedImageUri() != null
        binding.visualModeStatusText.text = getString(
            R.string.support_visual_mode_status,
            modeLabel,
            if (hasFixedImage) {
                getString(R.string.support_fixed_background_ready)
            } else {
                getString(R.string.support_fixed_background_missing)
            },
        )
        binding.fixedBackgroundButton.isEnabled = visualModePreferences.getVisualMode() == VisualMode.FIXED
        binding.fixedBackgroundButton.alpha = if (binding.fixedBackgroundButton.isEnabled) 1.0f else 0.5f
    }

    /**
     * 背景モードの選択ダイアログを開きます。
     */
    private fun showVisualModeDialog() {
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
                        renderVisualModeStatus()
                    }

                    else -> {
                        visualModePreferences.setVisualMode(VisualMode.FIXED)
                        renderVisualModeStatus()
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
}
