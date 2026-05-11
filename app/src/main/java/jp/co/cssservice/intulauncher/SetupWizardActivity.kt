package jp.co.cssservice.intulauncher

import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import jp.co.cssservice.intulauncher.databinding.ActivitySetupWizardBinding
import kotlin.math.abs

/**
 * IntuLauncher の初期設定を、対話型セットアップとして案内する画面です。
 */
class SetupWizardActivity : AppCompatActivity() {
    /** 画面操作に使う ViewBinding です。 */
    private lateinit var binding: ActivitySetupWizardBinding

    /** セットアップ結果を保存する設定クラスです。 */
    private lateinit var onboardingSupportPreferences: OnboardingSupportPreferences

    /** コールドスタート用の初期プロファイルを保存する設定クラスです。 */
    private lateinit var coldStartPreferences: ColdStartPreferences

    /** 現在表示中のセットアップ段階です。 */
    private var currentStage = SetupStage.WELCOME

    /** スワイプ質問の現在位置です。 */
    private var personalityIndex = 0

    /** 性格パラメータの累積値です。 */
    private var personalityScores = PersonalityScores()

    /** 既存設定から引き継ぐ利き手情報です。 */
    private var selectedHandedness = Handedness.RIGHT

    /** 外部連携の承認状態です。 */
    private val contextSelections = linkedMapOf(
        ContextChannel.LOCATION to false,
        ContextChannel.CALENDAR to false,
        ContextChannel.HEALTH to false,
        ContextChannel.NOTIFICATION to false,
    )

    /** 背景色の遷移アニメーションです。 */
    private var backgroundAnimator: ValueAnimator? = null

    /**
     * 画面生成時にウィザード全体を初期化し、Welcome ステージから開始します。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetupWizardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        onboardingSupportPreferences = OnboardingSupportPreferences(this)
        coldStartPreferences = ColdStartPreferences(this)
        selectedHandedness = onboardingSupportPreferences.getHandedness()

        // セットアップ中は戻るで即離脱せず、1 段階ずつ戻せるようにします。
        onBackPressedDispatcher.addCallback(this) {
            handleBackNavigation()
        }

        setupParticleAnimations()
        setupStaticActions()
        renderStage()
    }

    /**
     * 画面を離れる際に残っているアニメーションを停止します。
     */
    override fun onDestroy() {
        backgroundAnimator?.cancel()
        super.onDestroy()
    }

    /**
     * 固定ボタンやコンテキスト連携アイコンなどの基本操作を設定します。
     */
    private fun setupStaticActions() {
        binding.welcomeStartButton.setOnClickListener {
            moveToStage(SetupStage.PERSONALITY)
        }
        binding.contextContinueButton.setOnClickListener {
            startGenerationStage()
        }
        binding.generationContinueButton.setOnClickListener {
            moveToStage(SetupStage.FINAL)
        }
        binding.finalLaunchButton.setOnClickListener {
            completeSetup()
        }

        bindContextCard(
            card = binding.permissionLocationCard,
            channel = ContextChannel.LOCATION,
            onSelected = {
                // 位置情報の詳細設定は後続で実装するため、現状はアプリ設定画面へ誘導します。
                openAppDetailsSettings()
            },
        )
        bindContextCard(
            card = binding.permissionCalendarCard,
            channel = ContextChannel.CALENDAR,
            onSelected = {
                openCalendarApp()
            },
        )
        bindContextCard(
            card = binding.permissionHealthCard,
            channel = ContextChannel.HEALTH,
            onSelected = { },
        )
        bindContextCard(
            card = binding.permissionNotificationCard,
            channel = ContextChannel.NOTIFICATION,
            onSelected = {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            },
        )
    }

    /**
     * 現在の段階に応じて UI 全体を切り替えます。
     */
    private fun renderStage() {
        val stages = SetupStage.entries
        val progressPercent = (((stages.indexOf(currentStage) + 1).toFloat() / stages.size.toFloat()) * 100f).toInt()
        binding.progressLabelText.text = getString(R.string.setup_stage_progress, stages.indexOf(currentStage) + 1, stages.size)
        binding.progressBar.max = 100
        binding.progressBar.progress = progressPercent

        binding.welcomeSection.visibility = View.GONE
        binding.personalitySection.visibility = View.GONE
        binding.contextSection.visibility = View.GONE
        binding.generationSection.visibility = View.GONE
        binding.finalSection.visibility = View.GONE

        when (currentStage) {
            SetupStage.WELCOME -> renderWelcomeStage()
            SetupStage.PERSONALITY -> renderPersonalityStage()
            SetupStage.CONTEXT -> renderContextStage()
            SetupStage.GENERATION -> renderGenerationStage()
            SetupStage.FINAL -> renderFinalStage()
        }
    }

    /**
     * Welcome ステージを表示し、ブランド体験の導入を行います。
     */
    private fun renderWelcomeStage() {
        animateBackgroundTo(
            startColor = ContextCompat.getColor(this, R.color.background_home),
            endColor = ContextCompat.getColor(this, R.color.background_ambient),
        )
        binding.stageTitleText.text = getString(R.string.setup_welcome_title)
        binding.stageBodyText.text = getString(R.string.setup_welcome_body)
        binding.supportHintText.text = getString(R.string.setup_welcome_hint)
        binding.welcomeSection.visibility = View.VISIBLE

        ObjectAnimator.ofFloat(binding.welcomeLogoText, View.SCALE_X, 1f, 1.05f, 1f).apply {
            duration = 2200L
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
        ObjectAnimator.ofFloat(binding.welcomeLogoText, View.SCALE_Y, 1f, 1.05f, 1f).apply {
            duration = 2200L
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
        ObjectAnimator.ofFloat(binding.welcomeLogoText, View.ALPHA, 0.8f, 1f, 0.8f).apply {
            duration = 2200L
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    /**
     * Personality Swipe ステージを表示し、スワイプ操作で性格傾向を抽出します。
     */
    private fun renderPersonalityStage() {
        animateBackgroundTo(
            startColor = ContextCompat.getColor(this, R.color.background_focus),
            endColor = ContextCompat.getColor(this, R.color.background_commute),
        )
        binding.stageTitleText.text = getString(R.string.setup_personality_title)
        binding.stageBodyText.text = getString(R.string.setup_personality_body)
        binding.supportHintText.text = getString(R.string.setup_personality_hint)
        binding.personalitySection.visibility = View.VISIBLE

        val currentPrompt = PERSONALITY_PROMPTS[personalityIndex]
        val nextPrompt = PERSONALITY_PROMPTS.getOrNull(personalityIndex + 1)

        binding.personalityCounterText.text = getString(
            R.string.setup_personality_counter,
            personalityIndex + 1,
            PERSONALITY_PROMPTS.size,
        )
        binding.personalityTopQuestionText.text = currentPrompt.question
        binding.personalityTopMetaText.text = currentPrompt.categoryLabel
        binding.personalityBottomQuestionText.text = nextPrompt?.question ?: getString(R.string.setup_personality_last_card)
        binding.personalityBottomMetaText.text = nextPrompt?.categoryLabel ?: getString(R.string.setup_generation_ready_label)

        binding.personalityTopCard.translationX = 0f
        binding.personalityTopCard.translationY = 0f
        binding.personalityTopCard.rotation = 0f
        binding.personalityTopCard.alpha = 1f
        binding.personalityBottomCard.alpha = if (nextPrompt == null) 0.3f else 0.72f

        attachSwipeBehavior(binding.personalityTopCard)
    }

    /**
     * Context Connection ステージを表示し、各文脈連携の承認状態を更新します。
     */
    private fun renderContextStage() {
        animateBackgroundTo(
            startColor = ContextCompat.getColor(this, R.color.background_ambient),
            endColor = ContextCompat.getColor(this, R.color.background_focus),
        )
        binding.stageTitleText.text = getString(R.string.setup_context_title)
        binding.stageBodyText.text = getString(R.string.setup_context_body)
        binding.supportHintText.text = getString(R.string.setup_context_hint)
        binding.contextSection.visibility = View.VISIBLE
        renderContextSelectionState()
    }

    /**
     * Visual Generation ステージを表示し、生成デモを再生します。
     */
    private fun renderGenerationStage() {
        animateBackgroundTo(
            startColor = ContextCompat.getColor(this, R.color.background_commute),
            endColor = ContextCompat.getColor(this, R.color.background_night),
        )
        binding.stageTitleText.text = getString(R.string.setup_generation_title)
        binding.stageBodyText.text = getString(R.string.setup_generation_body)
        binding.supportHintText.text = getString(R.string.setup_generation_hint)
        binding.generationSection.visibility = View.VISIBLE
    }

    /**
     * Final Launch ステージを表示し、生成された結果を要約します。
     */
    private fun renderFinalStage() {
        animateBackgroundTo(
            startColor = ContextCompat.getColor(this, R.color.background_home),
            endColor = ContextCompat.getColor(this, R.color.background_focus),
        )
        binding.stageTitleText.text = getString(R.string.setup_final_title)
        binding.stageBodyText.text = getString(R.string.setup_final_body)
        binding.supportHintText.text = getString(R.string.setup_final_hint)
        binding.finalSection.visibility = View.VISIBLE

        val derivedProfile = deriveProfile()
        val derivedDensity = deriveIconDensity()
        val widgetMode = if (prefersWidgetTrial()) {
            getString(R.string.setup_wizard_option_widget_first)
        } else {
            getString(R.string.setup_wizard_option_full_launcher)
        }
        binding.finalSummaryText.text = getString(
            R.string.setup_final_summary,
            derivedProfile.displayName,
            derivedDensity.displayName,
            widgetMode,
        )
    }

    /**
     * スワイプ式カードへ操作を付与し、Yes / No に応じて次の質問へ進めます。
     */
    private fun attachSwipeBehavior(card: MaterialCardView) {
        var downX = 0f
        card.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - downX
                    view.translationX = deltaX
                    view.rotation = deltaX / 28f
                    val positiveRatio = (deltaX / view.width.toFloat()).coerceIn(0f, 1f)
                    val negativeRatio = ((-deltaX) / view.width.toFloat()).coerceIn(0f, 1f)
                    binding.personalityYesLabel.alpha = 0.35f + (positiveRatio * 0.65f)
                    binding.personalityNoLabel.alpha = 0.35f + (negativeRatio * 0.65f)
                    true
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    val deltaX = event.rawX - downX
                    val threshold = view.width * 0.22f
                    if (abs(deltaX) >= threshold) {
                        val accepted = deltaX > 0f
                        completeSwipe(accepted)
                    } else {
                        view.animate()
                            .translationX(0f)
                            .rotation(0f)
                            .setDuration(220L)
                            .setInterpolator(DecelerateInterpolator())
                            .start()
                        binding.personalityYesLabel.alpha = 0.35f
                        binding.personalityNoLabel.alpha = 0.35f
                    }
                    true
                }

                else -> false
            }
        }
    }

    /**
     * カードのスワイプ完了演出を行い、回答結果を記録して次へ進みます。
     */
    private fun completeSwipe(accepted: Boolean) {
        val direction = if (accepted) 1f else -1f
        val currentPrompt = PERSONALITY_PROMPTS[personalityIndex]
        binding.personalityTopCard.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        updateScores(currentPrompt, accepted)
        animateBackgroundChoice(accepted)
        binding.personalityTopCard.animate()
            .translationX(direction * binding.personalityTopCard.width * 1.35f)
            .rotation(direction * 18f)
            .alpha(0f)
            .setDuration(260L)
            .withEndAction {
                personalityIndex += 1
                binding.personalityYesLabel.alpha = 0.35f
                binding.personalityNoLabel.alpha = 0.35f
                if (personalityIndex >= PERSONALITY_PROMPTS.size) {
                    moveToStage(SetupStage.CONTEXT)
                } else {
                    renderPersonalityStage()
                }
            }
            .start()
    }

    /**
     * 性格質問の回答を、後続のプロファイル推定へ使う内部スコアへ反映します。
     */
    private fun updateScores(prompt: PersonalityPrompt, accepted: Boolean) {
        when (prompt.id) {
            "music_over_news" -> {
                if (accepted) {
                    personalityScores.content += 2
                    personalityScores.space += 1
                } else {
                    personalityScores.tool += 1
                    personalityScores.focus += 1
                }
            }

            "mute_after_hours" -> {
                if (accepted) {
                    personalityScores.space += 2
                    personalityScores.focus += 1
                } else {
                    personalityScores.focus += 1
                    personalityScores.tool += 1
                }
            }

            "beauty_over_efficiency" -> {
                if (accepted) {
                    personalityScores.visualDepth += 2
                    personalityScores.content += 1
                } else {
                    personalityScores.tool += 2
                    personalityScores.focus += 1
                }
            }
        }
    }

    /**
     * スワイプ方向に応じて、背景色を暖色または寒色へ一時的に揺らして反応を返します。
     */
    private fun animateBackgroundChoice(accepted: Boolean) {
        val choiceColor = if (accepted) {
            ContextCompat.getColor(this, R.color.warm_amber)
        } else {
            ContextCompat.getColor(this, R.color.accent_night)
        }
        val baseColor = ContextCompat.getColor(this, R.color.background_focus)
        ValueAnimator.ofObject(ArgbEvaluator(), baseColor, choiceColor, baseColor).apply {
            duration = 420L
            addUpdateListener { animator ->
                binding.rootLayout.setBackgroundColor(animator.animatedValue as Int)
            }
            start()
        }
    }

    /**
     * 各コンテキストアイコンのタップ挙動を設定します。
     */
    private fun bindContextCard(
        card: MaterialCardView,
        channel: ContextChannel,
        onSelected: () -> Unit,
    ) {
        card.setOnClickListener {
            contextSelections[channel] = true
            card.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            renderContextSelectionState()
            onSelected()
        }
    }

    /**
     * 連携アイコンの選択状態と、全完了時の中央統合演出を更新します。
     */
    private fun renderContextSelectionState() {
        updateContextCard(binding.permissionLocationCard, contextSelections[ContextChannel.LOCATION] == true)
        updateContextCard(binding.permissionCalendarCard, contextSelections[ContextChannel.CALENDAR] == true)
        updateContextCard(binding.permissionHealthCard, contextSelections[ContextChannel.HEALTH] == true)
        updateContextCard(binding.permissionNotificationCard, contextSelections[ContextChannel.NOTIFICATION] == true)

        val completedCount = contextSelections.values.count { it }
        binding.contextStatusText.text = getString(R.string.setup_context_status, completedCount, contextSelections.size)
        val allCompleted = completedCount == contextSelections.size
        binding.contextContinueButton.visibility = if (allCompleted) View.VISIBLE else View.INVISIBLE
        binding.contextMergeOrb.alpha = if (allCompleted) 1f else 0f
        if (allCompleted) {
            binding.contextMergeOrb.animate().scaleX(1f).scaleY(1f).setDuration(280L).start()
        }
    }

    /**
     * 連携アイコンの見た目を選択状態へ応じて更新します。
     */
    private fun updateContextCard(card: MaterialCardView, selected: Boolean) {
        card.strokeWidth = if (selected) dp(2) else dp(1)
        card.strokeColor = if (selected) {
            ContextCompat.getColor(this, R.color.lime)
        } else {
            ContextCompat.getColor(this, R.color.panel_stroke)
        }
        card.setCardBackgroundColor(
            if (selected) Color.parseColor("#223E6E5C") else Color.parseColor("#261E2D42"),
        )
    }

    /**
     * 生成デモを開始し、プレビュー要素を順番に描画していきます。
     */
    private fun startGenerationStage() {
        moveToStage(SetupStage.GENERATION)
        binding.generationProgressBar.max = 100
        binding.generationProgressBar.progress = 8
        binding.generationStatusText.text = getString(R.string.setup_generation_status_start)
        listOf(
            binding.generationSlotOne,
            binding.generationSlotTwo,
            binding.generationSlotThree,
            binding.generationAnchorSlot,
        ).forEach { preview ->
            preview.alpha = 0f
            preview.scaleX = 0.92f
            preview.scaleY = 0.92f
        }
        binding.generationContinueButton.visibility = View.INVISIBLE

        val steps = listOf(
            18 to binding.generationSlotOne,
            42 to binding.generationSlotTwo,
            66 to binding.generationSlotThree,
            92 to binding.generationAnchorSlot,
        )
        steps.forEachIndexed { index, (progress, view) ->
            binding.root.postDelayed({
                binding.generationProgressBar.progress = progress
                binding.generationStatusText.text = getString(
                    R.string.setup_generation_status_step,
                    index + 1,
                    steps.size,
                )
                view.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(260L)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }, 420L * (index + 1))
        }
        binding.root.postDelayed({
            binding.generationProgressBar.progress = 100
            binding.generationStatusText.text = getString(R.string.setup_generation_status_done)
            binding.generationContinueButton.visibility = View.VISIBLE
            binding.generationContinueButton.animate().alpha(1f).setDuration(220L).start()
            binding.generationContinueButton.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }, 2200L)
    }

    /**
     * セットアップ結果を保存し、ホーム画面へ戻します。
     */
    private fun completeSetup() {
        val derivedProfile = deriveProfile()
        coldStartPreferences.setSelectedProfile(derivedProfile)
        onboardingSupportPreferences.saveSetupResult(
            profile = derivedProfile,
            handedness = selectedHandedness,
            iconDensity = deriveIconDensity(),
            prefersTrialWidget = prefersWidgetTrial(),
        )
        setResult(Activity.RESULT_OK)
        finish()
    }

    /**
     * 現在の性格スコアから初期プロファイルを推定します。
     */
    private fun deriveProfile(): ColdStartProfile {
        return when {
            personalityScores.tool >= 4 -> ColdStartProfile.BUSINESS
            personalityScores.content >= 3 -> ColdStartProfile.ENTERTAINMENT
            else -> ColdStartProfile.STUDENT
        }
    }

    /**
     * 現在の性格スコアからアイコン密度を推定します。
     */
    private fun deriveIconDensity(): IconDensity {
        return when {
            personalityScores.visualDepth >= 2 || personalityScores.space >= 3 -> IconDensity.RELAXED
            personalityScores.focus >= 3 -> IconDensity.COMPACT
            else -> IconDensity.BALANCED
        }
    }

    /**
     * ウィジェット先行体験にするかどうかを性格傾向から推定します。
     */
    private fun prefersWidgetTrial(): Boolean {
        return personalityScores.space >= personalityScores.focus
    }

    /**
     * Welcome 背景用の粒子をゆっくり循環させます。
     */
    private fun setupParticleAnimations() {
        val particles = listOf(
            binding.particleOne,
            binding.particleTwo,
            binding.particleThree,
            binding.particleFour,
            binding.particleFive,
            binding.particleSix,
        )
        particles.forEachIndexed { index, particle ->
            ObjectAnimator.ofFloat(particle, View.TRANSLATION_Y, 0f, -140f, 0f).apply {
                duration = 3600L + (index * 260L)
                repeatCount = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }
            ObjectAnimator.ofFloat(particle, View.ALPHA, 0.1f, 0.55f, 0.1f).apply {
                duration = 3200L + (index * 200L)
                repeatCount = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }
        }
    }

    /**
     * 画面全体の背景色を現在ステージに合わせて滑らかに遷移させます。
     */
    private fun animateBackgroundTo(startColor: Int, endColor: Int) {
        backgroundAnimator?.cancel()
        backgroundAnimator = ValueAnimator.ofObject(ArgbEvaluator(), startColor, endColor).apply {
            duration = 900L
            addUpdateListener { animator ->
                binding.rootLayout.setBackgroundColor(animator.animatedValue as Int)
            }
            start()
        }
    }

    /**
     * Android のアプリ詳細設定画面を開きます。
     */
    private fun openAppDetailsSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    /**
     * カレンダー連携の導線として、カレンダーアプリ起動を試みます。
     */
    private fun openCalendarApp() {
        val intent = packageManager.getLaunchIntentForPackage("com.google.android.calendar")
        if (intent != null) {
            startActivity(intent)
        }
    }

    /**
     * 戻る操作時に現在の段階に応じた遷移を行います。
     */
    private fun handleBackNavigation() {
        currentStage = when (currentStage) {
            SetupStage.WELCOME -> {
                finish()
                return
            }

            SetupStage.PERSONALITY -> SetupStage.WELCOME
            SetupStage.CONTEXT -> SetupStage.PERSONALITY
            SetupStage.GENERATION -> SetupStage.CONTEXT
            SetupStage.FINAL -> SetupStage.GENERATION
        }
        renderStage()
    }

    /**
     * 指定された段階へ移動し、対応する UI を描画します。
     */
    private fun moveToStage(stage: SetupStage) {
        currentStage = stage
        renderStage()
    }

    /**
     * dp をピクセルへ変換します。
     */
    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    companion object {
        /** スワイプ式で表示する質問一覧です。 */
        private val PERSONALITY_PROMPTS = listOf(
            PersonalityPrompt(
                id = "music_over_news",
                categoryLabel = "Tool vs Content",
                question = "朝のニュースより、音楽を優先する？",
            ),
            PersonalityPrompt(
                id = "mute_after_hours",
                categoryLabel = "Focus vs Space",
                question = "仕事の通知は、定時を過ぎたら一切見たくない？",
            ),
            PersonalityPrompt(
                id = "beauty_over_efficiency",
                categoryLabel = "Visual Depth",
                question = "効率よりも、画面の美しさを重視する？",
            ),
        )
    }
}

/**
 * セットアップのステージを表す列挙です。
 */
private enum class SetupStage {
    WELCOME,
    PERSONALITY,
    CONTEXT,
    GENERATION,
    FINAL,
}

/**
 * スワイプ質問 1 件分の定義です。
 */
private data class PersonalityPrompt(
    /** 質問の識別子です。 */
    val id: String,
    /** 質問のカテゴリ表示です。 */
    val categoryLabel: String,
    /** ユーザーへ見せる質問本文です。 */
    val question: String,
)

/**
 * 性格推定に使う内部スコアです。
 */
private data class PersonalityScores(
    /** 集中寄りの強さです。 */
    var focus: Int = 0,
    /** 余白や安定寄りの強さです。 */
    var space: Int = 0,
    /** ツール志向の強さです。 */
    var tool: Int = 0,
    /** コンテンツ志向の強さです。 */
    var content: Int = 0,
    /** 視覚演出の深さへの好みです。 */
    var visualDepth: Int = 0,
)

/**
 * 文脈連携の種類を表します。
 */
private enum class ContextChannel {
    LOCATION,
    CALENDAR,
    HEALTH,
    NOTIFICATION,
}
