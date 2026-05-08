package jp.co.cssservice.intulauncher

import android.content.Intent
import android.graphics.drawable.Drawable
import androidx.annotation.ColorRes

data class LaunchableApp(
    val packageName: String,
    val label: String,
    val icon: Drawable,
    val launchIntent: Intent,
)

data class ContextSnapshot(
    val hourOfDay: Int,
    val isCharging: Boolean,
    val hasHeadphones: Boolean,
    val batteryPercent: Int,
)

data class SlotSpec(
    val title: String,
    val prompt: String,
    val keywords: List<String>,
)

data class ResolvedSlot(
    val spec: SlotSpec,
    val app: LaunchableApp?,
)

data class AnchorSlotSpec(
    val key: String,
    val title: String,
    val defaultKeywords: List<String>,
)

enum class LauncherProfile(
    val displayName: String,
    val headline: String,
    val subheadline: String,
    @ColorRes val backgroundColor: Int,
    val slotSpecs: List<SlotSpec>,
) {
    MORNING_COMMUTE(
        displayName = "Morning Station",
        headline = "Commute mode, surfaced instantly.",
        subheadline = "Transit, updates, and audio are prioritized before you start hunting.",
        backgroundColor = R.color.background_commute,
        slotSpecs = listOf(
            SlotSpec("Transit", "Rail, maps, or route guidance for the move ahead.", listOf("map", "maps", "transit", "train", "route", "google map", "navitime", "station", "乗換", "地図")),
            SlotSpec("Catch up", "News and must-see updates while you are on the move.", listOf("news", "feed", "mail", "gmail", "outlook", "inoreader", "smartnews", "line")),
            SlotSpec("Audio", "Music, podcast, or voice content to start the day.", listOf("music", "spotify", "podcast", "youtube music", "audio", "radiko")),
        ),
    ),
    FOCUS_WORK(
        displayName = "Office Focus",
        headline = "Focus mode keeps your core work stack in front.",
        subheadline = "Calendar, team communication, and notes rise without a setup ritual.",
        backgroundColor = R.color.background_focus,
        slotSpecs = listOf(
            SlotSpec("Calendar", "Meetings and schedule are promoted when work hours are active.", listOf("calendar", "schedule", "outlook", "google calendar")),
            SlotSpec("Team", "Chat and collaboration apps move into the center.", listOf("slack", "teams", "chat", "line works", "discord")),
            SlotSpec("Notes", "Capture, docs, and to-do tools stay close to the flow.", listOf("keep", "note", "notion", "memo", "todo", "docs")),
        ),
    ),
    EVENING_HOME(
        displayName = "Evening Home",
        headline = "Home mode softens into recovery and routine.",
        subheadline = "Entertainment, weather, and calm utility apps replace the work stack.",
        backgroundColor = R.color.background_home,
        slotSpecs = listOf(
            SlotSpec("Relax", "Video or reading apps for the end of the day.", listOf("youtube", "netflix", "prime video", "kindle", "book", "reader")),
            SlotSpec("Weather", "Quick context for tomorrow and the trip back out.", listOf("weather", "tenki", "forecast")),
            SlotSpec("Home utility", "Alarm, smart home, or recurring daily tools.", listOf("clock", "alarm", "home", "smart home", "calendar", "camera")),
        ),
    );

    fun resolveSlots(apps: List<LaunchableApp>): List<ResolvedSlot> {
        val usedPackages = mutableSetOf<String>()
        return slotSpecs.map { spec ->
            val app = AppCatalog.findBestMatch(apps, spec.keywords, usedPackages)
            if (app != null) {
                usedPackages += app.packageName
            }
            ResolvedSlot(spec, app)
        }
    }

    companion object {
        fun from(snapshot: ContextSnapshot): LauncherProfile {
            return when {
                snapshot.hourOfDay in 5..10 -> MORNING_COMMUTE
                snapshot.hourOfDay in 11..17 -> FOCUS_WORK
                snapshot.isCharging && snapshot.hourOfDay >= 20 -> EVENING_HOME
                snapshot.hasHeadphones && snapshot.hourOfDay >= 18 -> EVENING_HOME
                snapshot.hourOfDay >= 18 || snapshot.hourOfDay <= 4 -> EVENING_HOME
                else -> FOCUS_WORK
            }
        }
    }
}

val defaultAnchorSlots = listOf(
    AnchorSlotSpec("anchor_browser", "Browser", listOf("chrome", "browser", "firefox", "edge")),
    AnchorSlotSpec("anchor_chat", "Chat", listOf("line", "slack", "teams", "discord", "messages")),
    AnchorSlotSpec("anchor_utility", "Utility", listOf("camera", "wallet", "pay", "calculator", "files")),
)

