package jp.co.cssservice.intulauncher

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import jp.co.cssservice.intulauncher.databinding.ActivitySetupWizardBinding

/**
 * カードレイアウトで初期設定を進めるウィザード画面です。
 */
class SetupWizardActivity : AppCompatActivity() {
    /** 画面要素へアクセスする ViewBinding です。 */
    private lateinit var binding: ActivitySetupWizardBinding

    /** 導入支援設定を保持するクラスです。 */
    private lateinit var onboardingSupportPreferences: OnboardingSupportPreferences

    /** 初期プロファイルを保持するクラスです。 */
    private lateinit var coldStartPreferences: ColdStartPreferences

    /** 現在表示しているステップです。 */
    private var currentStep = 0

    /** 選択中のプロファイルです。 */
    private var selectedProfile = ColdStartProfile.BUSINESS

    /** 選択中の利き手です。 */
    private var selectedHandedness = Handedness.RIGHT

    /** 選択中のボタン密度です。 */
    private var selectedDensity = IconDensity.BALANCED

    /** ウィジェットから試すかどうかです。 */
    private var prefersWidgetTrial = true

    /**
     * 初期値を読み込み、ウィザードを表示します。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetupWizardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        onboardingSupportPreferences = OnboardingSupportPreferences(this)
        coldStartPreferences = ColdStartPreferences(this)
        loadInitialSelections()

        binding.previousButton.setOnClickListener {
            if (currentStep == 0) {
                finish()
            } else {
                currentStep -= 1
                renderStep()
            }
        }
        binding.nextButton.setOnClickListener {
            if (currentStep < LAST_STEP_INDEX) {
                currentStep += 1
                renderStep()
            } else {
                completeSetup()
            }
        }

        renderStep()
    }

    /**
     * 既存設定を初期選択へ反映します。
     */
    private fun loadInitialSelections() {
        selectedProfile = coldStartPreferences.getSelectedProfile() ?: ColdStartProfile.BUSINESS
        selectedHandedness = onboardingSupportPreferences.getHandedness()
        selectedDensity = onboardingSupportPreferences.getIconDensity()
        prefersWidgetTrial = onboardingSupportPreferences.prefersTrialWidget()
    }

    /**
     * 現在ステップの表示内容を描画します。
     */
    private fun renderStep() {
        binding.stepCounterText.text = getString(R.string.setup_wizard_step_format, currentStep + 1, LAST_STEP_INDEX + 1)
        binding.stepProgressBar.progress = currentStep + 1
        binding.previousButton.text = if (currentStep == 0) {
            getString(R.string.cancel)
        } else {
            getString(R.string.setup_wizard_previous)
        }
        binding.nextButton.text = if (currentStep == LAST_STEP_INDEX) {
            getString(R.string.setup_wizard_finish)
        } else {
            getString(R.string.setup_wizard_next)
        }

        when (currentStep) {
            0 -> renderProfileStep()
            1 -> renderHandednessStep()
            2 -> renderDensityStep()
            3 -> renderWidgetStep()
            else -> renderSummaryStep()
        }
    }

    /**
     * プロファイル選択ステップを描画します。
     */
    private fun renderProfileStep() {
        renderChoiceStep(
            title = getString(R.string.setup_wizard_profile_title),
            body = getString(R.string.setup_wizard_profile_body),
            choices = listOf(
                WizardChoice(
                    title = ColdStartProfile.BUSINESS.displayName,
                    body = getString(R.string.cold_start_profile_business_description),
                    selected = selectedProfile == ColdStartProfile.BUSINESS,
                ),
                WizardChoice(
                    title = ColdStartProfile.STUDENT.displayName,
                    body = getString(R.string.cold_start_profile_student_description),
                    selected = selectedProfile == ColdStartProfile.STUDENT,
                ),
                WizardChoice(
                    title = ColdStartProfile.ENTERTAINMENT.displayName,
                    body = getString(R.string.cold_start_profile_entertainment_description),
                    selected = selectedProfile == ColdStartProfile.ENTERTAINMENT,
                ),
            ),
        ) { index ->
            selectedProfile = when (index) {
                1 -> ColdStartProfile.STUDENT
                2 -> ColdStartProfile.ENTERTAINMENT
                else -> ColdStartProfile.BUSINESS
            }
            renderStep()
        }
    }

    /**
     * 利き手選択ステップを描画します。
     */
    private fun renderHandednessStep() {
        renderChoiceStep(
            title = getString(R.string.setup_wizard_hand_title),
            body = getString(R.string.setup_wizard_hand_body),
            choices = Handedness.entries.map { handedness ->
                WizardChoice(
                    title = handedness.displayName,
                    body = handednessDescription(handedness),
                    selected = selectedHandedness == handedness,
                )
            },
        ) { index ->
            selectedHandedness = Handedness.entries[index]
            renderStep()
        }
    }

    /**
     * ボタン密度選択ステップを描画します。
     */
    private fun renderDensityStep() {
        renderChoiceStep(
            title = getString(R.string.setup_wizard_density_title),
            body = getString(R.string.setup_wizard_density_body),
            choices = IconDensity.entries.map { density ->
                WizardChoice(
                    title = density.displayName,
                    body = densityDescription(density),
                    selected = selectedDensity == density,
                )
            },
        ) { index ->
            selectedDensity = IconDensity.entries[index]
            renderStep()
        }
    }

    /**
     * 導入方法選択ステップを描画します。
     */
    private fun renderWidgetStep() {
        renderChoiceStep(
            title = getString(R.string.setup_wizard_widget_title),
            body = getString(R.string.setup_wizard_widget_body),
            choices = listOf(
                WizardChoice(
                    title = getString(R.string.setup_wizard_option_widget_first),
                    body = "今のホームに 3 提案を置き、精度を見ながら育てます。",
                    selected = prefersWidgetTrial,
                ),
                WizardChoice(
                    title = getString(R.string.setup_wizard_option_full_launcher),
                    body = "IntuLauncher をそのままホームとして使い始めます。",
                    selected = !prefersWidgetTrial,
                ),
            ),
        ) { index ->
            prefersWidgetTrial = index == 0
            renderStep()
        }
    }

    /**
     * 確認ステップを描画します。
     */
    private fun renderSummaryStep() {
        renderChoiceStep(
            title = getString(R.string.setup_wizard_summary_title),
            body = getString(R.string.setup_wizard_summary_body),
            choices = listOf(
                WizardChoice(
                    title = getString(R.string.setup_wizard_summary_profile, selectedProfile.displayName),
                    body = profileDescription(selectedProfile),
                    selected = true,
                ),
                WizardChoice(
                    title = getString(R.string.setup_wizard_summary_hand, selectedHandedness.displayName),
                    body = handednessDescription(selectedHandedness),
                    selected = true,
                ),
                WizardChoice(
                    title = getString(R.string.setup_wizard_summary_density, selectedDensity.displayName),
                    body = densityDescription(selectedDensity),
                    selected = true,
                ),
                WizardChoice(
                    title = getString(
                        R.string.setup_wizard_summary_widget,
                        if (prefersWidgetTrial) {
                            getString(R.string.setup_wizard_option_widget_first)
                        } else {
                            getString(R.string.setup_wizard_option_full_launcher)
                        },
                    ),
                    body = if (prefersWidgetTrial) {
                        "まずは今のホームに置き、負担を増やさず精度を育てます。"
                    } else {
                        "提案 3 枠と固定 1 枠のホームとして使い始めます。"
                    },
                    selected = true,
                ),
            ),
            showSkipNote = false,
            interactive = false,
        ) { }
    }

    /**
     * 共通のカード選択ステップを描画します。
     */
    private fun renderChoiceStep(
        title: String,
        body: String,
        choices: List<WizardChoice>,
        showSkipNote: Boolean = true,
        interactive: Boolean = true,
        onSelected: (Int) -> Unit,
    ) {
        binding.questionTitleText.text = title
        binding.questionBodyText.text = body
        binding.skipNoteText.visibility = if (showSkipNote) View.VISIBLE else View.GONE

        val cards = listOf(
            CardViews(binding.optionOneCard, binding.optionOneTitle, binding.optionOneBody),
            CardViews(binding.optionTwoCard, binding.optionTwoTitle, binding.optionTwoBody),
            CardViews(binding.optionThreeCard, binding.optionThreeTitle, binding.optionThreeBody),
            CardViews(binding.optionFourCard, binding.optionFourTitle, binding.optionFourBody),
        )

        cards.forEachIndexed { index, views ->
            val choice = choices.getOrNull(index)
            if (choice == null) {
                views.card.visibility = View.GONE
            } else {
                views.card.visibility = View.VISIBLE
                bindChoiceCard(views.card, views.titleView, views.bodyView, choice)
                if (interactive) {
                    views.card.setOnClickListener { onSelected(index) }
                } else {
                    views.card.setOnClickListener(null)
                }
            }
        }
    }

    /**
     * 1 枚のカードへ表示内容と選択状態を反映します。
     */
    private fun bindChoiceCard(
        card: MaterialCardView,
        titleView: TextView,
        bodyView: TextView,
        choice: WizardChoice,
    ) {
        titleView.text = choice.title
        bodyView.text = choice.body

        val strokeColor = if (choice.selected) getColor(R.color.lime) else getColor(R.color.panel_stroke)
        val backgroundColor = if (choice.selected) getColor(R.color.anchor_surface) else getColor(R.color.panel_surface_soft)

        // 選択中のカードだけ色と枠線を強め、現在地を視覚的に伝えます。
        card.setCardBackgroundColor(backgroundColor)
        card.strokeColor = strokeColor
        card.strokeWidth = if (choice.selected) dp(2) else dp(1)
    }

    /**
     * 現在の選択を保存してホームへ戻ります。
     */
    private fun completeSetup() {
        coldStartPreferences.setSelectedProfile(selectedProfile)
        onboardingSupportPreferences.saveSetupResult(
            profile = selectedProfile,
            handedness = selectedHandedness,
            iconDensity = selectedDensity,
            prefersTrialWidget = prefersWidgetTrial,
        )
        setResult(Activity.RESULT_OK)
        finish()
    }

    /**
     * プロファイル説明を返します。
     */
    private fun profileDescription(profile: ColdStartProfile): String {
        return when (profile) {
            ColdStartProfile.BUSINESS -> getString(R.string.cold_start_profile_business_description)
            ColdStartProfile.STUDENT -> getString(R.string.cold_start_profile_student_description)
            ColdStartProfile.ENTERTAINMENT -> getString(R.string.cold_start_profile_entertainment_description)
        }
    }

    /**
     * 利き手ごとの説明を返します。
     */
    private fun handednessDescription(handedness: Handedness): String {
        return when (handedness) {
            Handedness.RIGHT -> "右手で届きやすい下部導線を優先します。"
            Handedness.LEFT -> "左手で届きやすい下部導線を優先します。"
            Handedness.BOTH -> "左右どちらでも押しやすい中央バランスを優先します。"
        }
    }

    /**
     * ボタン密度ごとの説明を返します。
     */
    private fun densityDescription(density: IconDensity): String {
        return when (density) {
            IconDensity.COMPACT -> "情報量を残しつつ、提案を見比べやすくします。"
            IconDensity.BALANCED -> "見やすさと押しやすさのバランスを取ります。"
            IconDensity.RELAXED -> "移動中でも押しやすい大きめボタンを優先します。"
        }
    }

    /**
     * dp をピクセルへ変換します。
     */
    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    companion object {
        /** 最終ステップ番号です。 */
        private const val LAST_STEP_INDEX = 4
    }
}

/**
 * カード表示に使う View 群です。
 */
private data class CardViews(
    /** カード本体です。 */
    val card: MaterialCardView,
    /** タイトル表示です。 */
    val titleView: TextView,
    /** 説明表示です。 */
    val bodyView: TextView,
)

/**
 * ウィザードの 1 選択肢を表すモデルです。
 */
private data class WizardChoice(
    /** カード見出しです。 */
    val title: String,
    /** 補足説明です。 */
    val body: String,
    /** 現在選択中かどうかです。 */
    val selected: Boolean,
)
