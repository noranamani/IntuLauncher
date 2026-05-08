package jp.co.cssservice.intulauncher

import android.graphics.drawable.Drawable
import android.os.Bundle
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

    /** アンカースロットの固定状態を保存する設定です。 */
    private lateinit var anchorPreferences: AnchorPreferences

    /** 現在のコンテキスト情報を取得する読み取りクラスです。 */
    private lateinit var signalReader: ContextSignalReader

    /** 現在メモリ上に保持している起動可能アプリ一覧です。 */
    private var launchableApps: List<LaunchableApp> = emptyList()

    /**
     * 画面初期化と固定 UI イベント設定を行います。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appCatalog = AppCatalog(this)
        anchorPreferences = AnchorPreferences(this)
        signalReader = ContextSignalReader(this)

        binding.openAllAppsButton.setOnClickListener {
            showAppPicker(title = getString(R.string.app_picker_title)) { app ->
                launchApp(app)
            }
        }
    }

    /**
     * ホーム画面へ戻るたびに最新のコンテキストで UI を再構成します。
     */
    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    /**
     * コンテキスト取得から UI 反映までを一括で更新します。
     */
    private fun refreshUi() {
        launchableApps = appCatalog.loadLaunchableApps()
        val snapshot = signalReader.readSnapshot()
        val profile = LauncherProfile.from(snapshot)
        val slots = profile.resolveSlots(launchableApps)

        renderProfile(profile, snapshot)
        renderDynamicSlots(slots)
        renderAnchorSlots()
    }

    /**
     * ホーム全体のプロファイル情報を画面へ反映します。
     */
    private fun renderProfile(profile: LauncherProfile, snapshot: ContextSnapshot) {
        binding.rootLayout.setBackgroundColor(ContextCompat.getColor(this, profile.backgroundColor))
        binding.headlineText.text = profile.headline
        binding.subheadlineText.text = profile.subheadline
        binding.profileChip.text = profile.displayName
        binding.timeText.text = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))
        binding.signalSummaryText.text = buildSignalSummary(snapshot)
    }

    /**
     * 3 つの動的スロットを順に描画します。
     */
    private fun renderDynamicSlots(slots: List<ResolvedSlot>) {
        bindDynamicSlot(binding.slotOneCard, binding.slotOneIcon, binding.slotOneTitle, binding.slotOneSubtitle, slots.getOrNull(0))
        bindDynamicSlot(binding.slotTwoCard, binding.slotTwoIcon, binding.slotTwoTitle, binding.slotTwoSubtitle, slots.getOrNull(1))
        bindDynamicSlot(binding.slotThreeCard, binding.slotThreeIcon, binding.slotThreeTitle, binding.slotThreeSubtitle, slots.getOrNull(2))
    }

    /**
     * 単一の動的スロットへ候補アプリまたはフォールバック導線を描画します。
     */
    private fun bindDynamicSlot(
        card: MaterialCardView,
        iconView: ImageView,
        titleView: TextView,
        subtitleView: TextView,
        resolvedSlot: ResolvedSlot?,
    ) {
        val accent = ContextCompat.getColor(this, R.color.panel_surface)
        card.setCardBackgroundColor(accent)

        // 候補が見つからない場合でも、アプリライブラリへの導線は維持します。
        if (resolvedSlot?.app == null) {
            iconView.setImageDrawable(ContextCompat.getDrawable(this, android.R.drawable.ic_menu_search))
            titleView.text = getString(R.string.slot_empty_title)
            subtitleView.text = getString(R.string.slot_empty_subtitle)
            card.setOnClickListener { showAppPicker(title = getString(R.string.app_picker_title), onSelected = ::launchApp) }
            return
        }

        iconView.setImageDrawable(resolvedSlot.app.icon)
        val slotKindLabel = if (resolvedSlot.spec.kind == SlotKind.DISCOVERY) {
            getString(R.string.slot_kind_discovery)
        } else {
            getString(R.string.slot_kind_prediction)
        }
        titleView.text = "${resolvedSlot.spec.title} / ${resolvedSlot.app.label}"
        subtitleView.text = "$slotKindLabel / ${resolvedSlot.actionHint}"
        card.setOnClickListener { launchApp(resolvedSlot.app) }
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
     * 単一のアンカースロットに固定済みアプリまたは初期候補を割り当てます。
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
        subtitleView.text = resolvedApp?.label ?: "長押しで固定"

        card.setOnClickListener {
            if (resolvedApp != null) {
                launchApp(resolvedApp)
            } else {
                showAnchorPicker(spec, isPinned = false)
            }
        }
        // 長押し時だけ固定変更 UI を開くことで、通常タップの起動動線を崩さないようにします。
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
     * 一般アプリ一覧ダイアログを表示します。
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
     * 現在のコンテキストを人が読める簡易サマリーへ変換します。
     */
    private fun buildSignalSummary(snapshot: ContextSnapshot): String {
        val chargingLabel = if (snapshot.isCharging) "充電中" else "非充電"
        val audioLabel = if (snapshot.hasHeadphones) "イヤホン接続" else "本体スピーカー"
        val postureLabel = if (snapshot.isLandscape) "横向き" else "縦向き"
        return "時刻 ${snapshot.hourOfDay}:00 / $chargingLabel / $audioLabel / $postureLabel / 電池 ${snapshot.batteryPercent}%"
    }

    /**
     * アンカー未設定時に使う代替アイコンを返します。
     */
    private fun defaultAnchorIcon(): Drawable? {
        return ContextCompat.getDrawable(this, android.R.drawable.star_big_off)
    }
}
