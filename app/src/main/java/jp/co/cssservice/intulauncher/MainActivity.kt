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

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var appCatalog: AppCatalog
    private lateinit var anchorPreferences: AnchorPreferences
    private lateinit var signalReader: ContextSignalReader

    private var launchableApps: List<LaunchableApp> = emptyList()

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

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    private fun refreshUi() {
        launchableApps = appCatalog.loadLaunchableApps()
        val snapshot = signalReader.readSnapshot()
        val profile = LauncherProfile.from(snapshot)
        val slots = profile.resolveSlots(launchableApps)

        renderProfile(profile, snapshot)
        renderDynamicSlots(slots)
        renderAnchorSlots()
    }

    private fun renderProfile(profile: LauncherProfile, snapshot: ContextSnapshot) {
        binding.rootLayout.setBackgroundColor(ContextCompat.getColor(this, profile.backgroundColor))
        binding.headlineText.text = profile.headline
        binding.subheadlineText.text = profile.subheadline
        binding.profileChip.text = profile.displayName
        binding.timeText.text = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))
        binding.signalSummaryText.text = buildSignalSummary(snapshot)
    }

    private fun renderDynamicSlots(slots: List<ResolvedSlot>) {
        bindDynamicSlot(binding.slotOneCard, binding.slotOneIcon, binding.slotOneTitle, binding.slotOneSubtitle, slots.getOrNull(0))
        bindDynamicSlot(binding.slotTwoCard, binding.slotTwoIcon, binding.slotTwoTitle, binding.slotTwoSubtitle, slots.getOrNull(1))
        bindDynamicSlot(binding.slotThreeCard, binding.slotThreeIcon, binding.slotThreeTitle, binding.slotThreeSubtitle, slots.getOrNull(2))
    }

    private fun bindDynamicSlot(
        card: MaterialCardView,
        iconView: ImageView,
        titleView: TextView,
        subtitleView: TextView,
        resolvedSlot: ResolvedSlot?,
    ) {
        val accent = ContextCompat.getColor(this, R.color.panel_surface)
        card.setCardBackgroundColor(accent)

        if (resolvedSlot?.app == null) {
            iconView.setImageDrawable(ContextCompat.getDrawable(this, android.R.drawable.ic_menu_search))
            titleView.text = getString(R.string.slot_empty_title)
            subtitleView.text = getString(R.string.slot_empty_subtitle)
            card.setOnClickListener { showAppPicker(title = getString(R.string.app_picker_title), onSelected = ::launchApp) }
            return
        }

        iconView.setImageDrawable(resolvedSlot.app.icon)
        titleView.text = resolvedSlot.spec.title
        subtitleView.text = "${resolvedSlot.app.label}  •  ${resolvedSlot.spec.prompt}"
        card.setOnClickListener { launchApp(resolvedSlot.app) }
    }

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
        subtitleView.text = resolvedApp?.label ?: "Tap to pin"

        card.setOnClickListener {
            if (resolvedApp != null) {
                launchApp(resolvedApp)
            } else {
                showAnchorPicker(spec, isPinned = false)
            }
        }
        card.setOnLongClickListener {
            showAnchorPicker(spec, isPinned = pinnedPackage != null)
            true
        }
    }

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

    private fun launchApp(app: LaunchableApp) {
        startActivity(app.launchIntent)
    }

    private fun buildSignalSummary(snapshot: ContextSnapshot): String {
        val chargingLabel = if (snapshot.isCharging) "charging" else "not charging"
        val audioLabel = if (snapshot.hasHeadphones) "headphones connected" else "speaker route"
        return "Signals: ${snapshot.hourOfDay}:00 rhythm, $chargingLabel, $audioLabel, battery ${snapshot.batteryPercent}%."
    }

    private fun defaultAnchorIcon(): Drawable? {
        return ContextCompat.getDrawable(this, android.R.drawable.star_big_off)
    }
}
