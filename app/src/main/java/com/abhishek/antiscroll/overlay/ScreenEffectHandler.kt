package com.abhishek.antiscroll.overlay

import android.content.Context
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.core.view.isVisible
import com.abhishek.antiscroll.R
import com.abhishek.antiscroll.labelRes
import com.abhishek.antiscroll.present.Durations
import com.abhishek.antiscroll.present.OverlayStatus
import com.abhishek.antiscroll.rules.engine.BlockReason
import com.abhishek.antiscroll.rules.engine.Effect
import com.abhishek.antiscroll.rules.engine.EffectHandler
import com.abhishek.antiscroll.rules.engine.GuardEvent
import com.abhishek.antiscroll.rules.engine.OverrideAvailability
import com.abhishek.antiscroll.rules.time.Clock
import com.abhishek.antiscroll.rules.unlock.ChallengeAnswer
import com.abhishek.antiscroll.rules.unlock.ChallengePrompt
import com.abhishek.antiscroll.rules.unlock.ChallengeSpec
import com.abhishek.antiscroll.rules.unlock.TypingAccuracy
import com.abhishek.antiscroll.ui.widget.DotsView
import com.abhishek.antiscroll.ui.widget.RingView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.time.Duration
import java.time.Instant
import java.util.Date
import kotlin.math.roundToInt

/**
 * Draws the screens the engine asks for over Instagram (docs/UI.md, Inside Instagram) and reports
 * what the user does on them through [send]. Screens only draw; every decision is the engine's.
 * The numbers around a decision (time today, overrides left, the reset) come from [status]. Each
 * layout is inflated once and reused.
 */
class ScreenEffectHandler(
    context: Context,
    private val overlay: OverlayController,
    private val clock: Clock,
    private val status: () -> OverlayStatus,
    private val send: (GuardEvent) -> Unit,
) : EffectHandler {

    private val themed = context.overlayContext()
    private val inflater = LayoutInflater.from(themed)
    private val ticker = Ticker()
    private val views = mutableMapOf<Int, View>()

    /** The block screen while it's up, for [Effect.ShowLeaving]. */
    private var blockView: View? = null

    /** Why Instagram is covered, for the challenge screens' "never mind" wording. */
    private var lastReason: BlockReason? = null

    /** The last typing attempt (target, typed), to say how close it was if the engine asks again. */
    private var lastTyping: Pair<String, String>? = null

    override fun handle(effect: Effect) {
        when (effect) {
            is Effect.ShowPause -> showPause(effect.seconds)
            is Effect.ShowIntention -> showIntention(effect)
            is Effect.ShowBlock -> showBlock(effect)
            is Effect.ShowChallenge -> when (val prompt = effect.prompt) {
                is ChallengePrompt.Wait -> showWait(prompt.seconds)
                is ChallengePrompt.Typing -> showTyping(prompt)
            }
            Effect.Dismiss -> dismiss()
            Effect.ShowLeaving -> blockView?.let(::showLeaving)
            Effect.GoHome, Effect.GoBack -> Unit // navigation's job
        }
    }

    /** The view for [layout], inflated the first time and kept, its insets handled. */
    private fun view(@LayoutRes layout: Int): View = views.getOrPut(layout) {
        inflater.inflate(layout, null).also { it.fitSystemBars() }
    }

    /** Counts down [seconds]; "Open Instagram" works once it reaches zero, and leads to the intention prompt. */
    private fun showPause(seconds: Int) {
        val view = view(R.layout.overlay_pause)
        val status = status()
        val ring = view.findViewById<RingView>(R.id.pauseRing)
        val count = view.findViewById<TextView>(R.id.pauseCount)
        val secondary = view.findViewById<MaterialButton>(R.id.pauseSecondary)
        view.findViewById<View>(R.id.pauseRingBox).isVisible = true
        view.findViewById<View>(R.id.pauseIcon).isVisible = false
        view.findViewById<View>(R.id.pausePill).isVisible = false
        // Opens count once the user gets in, so this one would be the next.
        view.findViewById<TextView>(R.id.pauseHeading).text = themed.getString(R.string.pause_heading, status.opens + 1)
        view.findViewById<TextView>(R.id.pauseSub).text = themed.getString(
            R.string.pause_sub_left,
            Durations.hm(status.usedMillis),
            Durations.minutesText(status.leftMillis),
        )
        primary(view, R.id.pausePrimary, themed.getString(R.string.close_instagram)) { send(GuardEvent.LeaveChosen) }
        secondary.isVisible = true // a "Going back…" on a block screen hides it, and this view is reused
        secondary.setOnClickListener { send(GuardEvent.PauseFinished) }
        blockView = null
        overlay.show(view)
        var left = seconds
        ticker.start {
            ring.progress = if (seconds == 0) 0f else left.toFloat() / seconds
            count.text = left.coerceAtLeast(0).toString()
            val done = left <= 0
            secondary.isEnabled = done
            val title = if (done) themed.getString(R.string.pause_open) else themed.getString(R.string.pause_open_in, left)
            secondary.text = twoLine(themed, title, themed.getString(R.string.pause_open_detail))
            left--
            !done
        }
    }

    /** A purpose and a length, each one choice; the second button works once both are picked. */
    private fun showIntention(effect: Effect.ShowIntention) {
        ticker.stop()
        val view = view(R.layout.overlay_intention)
        val status = status()
        val purposes = view.findViewById<ChipGroup>(R.id.purposeChips)
        val minutes = view.findViewById<ChipGroup>(R.id.minuteChips)
        val open = view.findViewById<MaterialButton>(R.id.intentionOpen)
        purposes.removeAllViews()
        minutes.removeAllViews()
        effect.purposes.forEach { purpose -> purposes.addView(choiceChip(purposes, purpose, purpose)) }
        effect.minuteChoices.forEach { choice ->
            minutes.addView(choiceChip(minutes, themed.getString(R.string.minutes_choice, choice), choice))
        }
        effect.restOfToday?.let { rest ->
            minutes.addView(choiceChip(minutes, themed.getString(R.string.rest_of_today_choice, rest), rest))
        }
        view.findViewById<TextView>(R.id.intentionLeft).text = themed.getString(
            R.string.pause_sub_left,
            Durations.hm(status.usedMillis),
            Durations.minutesText(effect.minutesLeftToday * 60_000L),
        )
        val chosenMinutes = { minutes.checkedChipId.takeIf { it != View.NO_ID }?.let { view.findViewById<Chip>(it).tag as Int } }
        val update = {
            val length = chosenMinutes()
            open.isEnabled = purposes.checkedChipId != View.NO_ID && length != null
            open.text = if (length != null) themed.getString(R.string.intention_open_for, length) else themed.getString(R.string.intention_open)
        }
        purposes.setOnCheckedStateChangeListener { _, _ -> update() }
        minutes.setOnCheckedStateChangeListener { _, _ -> update() }
        update()
        open.setOnClickListener {
            val purpose = view.findViewById<Chip>(purposes.checkedChipId).tag as String
            send(GuardEvent.IntentionChosen(purpose, chosenMinutes() ?: return@setOnClickListener, clock.now()))
        }
        primary(view, R.id.intentionClose, themed.getString(R.string.close_instagram)) { send(GuardEvent.LeaveChosen) }
        blockView = null
        overlay.show(view)
    }

    /**
     * With an override available, the pause layout with an icon: "Continue anyway" and how many are
     * left. Without one, the cooldown or cap layout counting down.
     */
    private fun showBlock(effect: Effect.ShowBlock) {
        lastReason = effect.reason
        lastTyping = null
        when (val override = effect.override) {
            is OverrideAvailability.Available -> showBlockWithOverride(effect.reason, override)
            is OverrideAvailability.CoolingDown -> showCooldown(effect.reason, override.until)
            OverrideAvailability.CapReached -> showCap(effect.reason)
        }
    }

    private fun showBlockWithOverride(reason: BlockReason, override: OverrideAvailability.Available) {
        ticker.stop()
        val view = view(R.layout.overlay_pause)
        val status = status()
        view.findViewById<View>(R.id.pauseRingBox).isVisible = false
        view.findViewById<ImageView>(R.id.pauseIcon).isVisible = true
        view.findViewById<TextView>(R.id.pauseHeading).text = heading(reason)
        view.findViewById<TextView>(R.id.pauseSub).text = sub(reason, status)
        view.findViewById<TextView>(R.id.pausePill).apply {
            isVisible = reason == BlockReason.DailyTotal || status.overMillis > 0
            text = themed.getString(R.string.over_daily_limit)
        }
        primary(view, R.id.pausePrimary, leaveLabel(reason)) { send(GuardEvent.LeaveChosen) }
        view.findViewById<MaterialButton>(R.id.pauseSecondary).apply {
            isVisible = true
            isEnabled = true
            text = twoLine(themed, themed.getString(R.string.continue_anyway), overrideDetail(override, status))
            setOnClickListener { send(GuardEvent.OverrideRequested(clock.now())) }
        }
        blockView = view
        overlay.show(view)
    }

    /** Counts down to the cooldown's end; then "Continue anyway" appears (the engine checks the time again). */
    private fun showCooldown(reason: BlockReason, until: Instant) {
        val view = cooldownView(reason)
        val status = status()
        val shownAt = clock.now()
        val total = Duration.between(shownAt, until).toMillis().coerceAtLeast(1L)
        view.findViewById<TextView>(R.id.cooldownHeading).text = heading(reason)
        view.findViewById<TextView>(R.id.cooldownSub).text = themed.getString(R.string.cooldown_sub)
        view.findViewById<TextView>(R.id.cooldownNote).text = themed.getString(R.string.cooldown_note, timeOf(until))
        dots(view, status, themed.resources.getQuantityString(R.plurals.overrides_left, status.overridesLeft, status.overridesLeft))
        val time = view.findViewById<TextView>(R.id.cooldownTime)
        val bar = view.findViewById<LinearProgressIndicator>(R.id.cooldownBar)
        val continueButton = view.findViewById<MaterialButton>(R.id.cooldownContinue)
        continueButton.isVisible = false
        continueButton.text = themed.getString(R.string.continue_anyway)
        continueButton.setOnClickListener { send(GuardEvent.OverrideRequested(clock.now())) }
        overlay.show(view)
        ticker.start {
            val left = Duration.between(clock.now(), until).toMillis()
            time.text = Durations.clock(left)
            bar.setProgressCompat((1000 - left * 1000 / total).toInt().coerceIn(0, 1000), false)
            val done = left <= 0
            continueButton.isVisible = done
            !done
        }
    }

    /** No overrides left: the time until the day resets is all there is. */
    private fun showCap(reason: BlockReason) {
        val view = cooldownView(reason)
        val status = status()
        view.findViewById<TextView>(R.id.cooldownHeading).setText(R.string.cap_heading)
        view.findViewById<TextView>(R.id.cooldownSub).text = when (reason) {
            is BlockReason.SurfaceBudget -> themed.getString(R.string.cap_sub_surface, status.cap, surfaceName(reason))
            else -> themed.getString(R.string.cap_sub, status.cap)
        }
        view.findViewById<TextView>(R.id.cooldownNote).text = themed.getString(R.string.cap_note, timeOf(status.nextResetAt))
        dots(view, status, themed.getString(R.string.overrides_used_of, status.overridesUsed, status.cap))
        view.findViewById<View>(R.id.cooldownContinue).isVisible = false
        val time = view.findViewById<TextView>(R.id.cooldownTime)
        val bar = view.findViewById<LinearProgressIndicator>(R.id.cooldownBar)
        overlay.show(view)
        ticker.start {
            val left = Duration.between(clock.now(), status.nextResetAt).toMillis()
            time.text = Durations.hm(left)
            bar.setProgressCompat((1000 - left * 1000 / DAY_MILLIS).toInt().coerceIn(0, 1000), false)
            left > 0
        }
    }

    private fun cooldownView(reason: BlockReason): View {
        val view = view(R.layout.overlay_cooldown)
        primary(view, R.id.cooldownPrimary, leaveLabel(reason)) { send(GuardEvent.LeaveChosen) }
        blockView = view
        return view
    }

    private fun dots(view: View, status: OverlayStatus, text: String) {
        view.findViewById<DotsView>(R.id.cooldownDots).apply {
            count = status.cap
            filled = status.overridesUsed
            contentDescription = themed.getString(R.string.overrides_used_of, status.overridesUsed, status.cap)
        }
        view.findViewById<TextView>(R.id.cooldownDotsText).text = text
    }

    /**
     * The block stays up while the user is taken off what's blocked underneath; say so, and give the
     * buttons back if they're still here after a few seconds (Leave again steps back again).
     */
    private fun showLeaving(view: View) {
        val message = view.findViewById<TextView>(R.id.pauseSub) ?: view.findViewById(R.id.cooldownSub)
        val buttons = listOf(R.id.pausePrimary, R.id.pauseSecondary, R.id.cooldownPrimary, R.id.cooldownContinue)
            .mapNotNull { view.findViewById<View>(it) }
        val shownBefore = buttons.map { it.isVisible }
        val messageBefore = message.text
        message.setText(R.string.leaving_back)
        buttons.forEach { it.isVisible = false }
        view.postDelayed({
            if (!view.isAttachedToWindow || blockView !== view) return@postDelayed
            message.text = messageBefore
            buttons.zip(shownBefore).forEach { (button, shown) -> button.isVisible = shown }
        }, LEAVING_TIMEOUT_MS)
    }

    /** The ring counts down; Continue reports the wait done (the run checks the time itself). */
    private fun showWait(seconds: Int) {
        val panel = WaitPanel(view(R.layout.overlay_wait), ticker)
        val status = status()
        panel.bind(
            seconds,
            WaitPanel.Copy(
                chip = overrideChip(status),
                heading = themed.getString(R.string.wait_heading),
                sub = themed.getString(R.string.wait_sub),
                cancel = neverMind(),
                detail = status.waitAfterNextSeconds?.let { themed.getString(R.string.wait_next, Durations.minutesText(it * 1_000L)) }
                    ?: themed.getString(R.string.wait_last),
            ),
            onCancel = ::cancelAndLeave,
            onDone = { send(GuardEvent.ChallengeSubmitted(ChallengeAnswer.WaitDone, clock.now())) },
        )
        blockView = null
        overlay.show(panel.view)
    }

    private fun showTyping(prompt: ChallengePrompt.Typing) {
        ticker.stop()
        val panel = TypingPanel(view(R.layout.overlay_typing))
        val needed = (prompt.minAccuracy * 100).roundToInt()
        // A typing screen right after another one means the last try missed; the engine already decided.
        val feedback = lastTyping?.let { (target, typed) ->
            themed.getString(R.string.typing_feedback, (TypingAccuracy.score(typed, target) * 100).toInt(), needed)
        }
        panel.bind(
            prompt.text,
            prompt.minAccuracy,
            TypingPanel.Copy(overrideChip(status()), themed.getString(R.string.typing_title), feedback, themed.getString(R.string.challenge_cancel)),
            onSubmit = { typed ->
                lastTyping = prompt.text to typed
                send(GuardEvent.ChallengeSubmitted(ChallengeAnswer.Typed(typed), clock.now()))
            },
            onCancel = {
                lastTyping = null
                send(GuardEvent.ChallengeCancelled(clock.now()))
            },
        )
        blockView = null
        overlay.show(panel.view, focusable = true)
        panel.focus()
    }

    /** "Never mind": back to the block, then leave as Leave would. */
    private fun cancelAndLeave() {
        send(GuardEvent.ChallengeCancelled(clock.now()))
        send(GuardEvent.LeaveChosen)
    }

    private fun primary(view: View, id: Int, label: String, onClick: () -> Unit) {
        view.findViewById<MaterialButton>(id).apply {
            isVisible = true
            text = label
            setOnClickListener { onClick() }
        }
    }

    private fun heading(reason: BlockReason): String = when (reason) {
        BlockReason.SessionTimeUp -> themed.getString(R.string.time_up_title)
        BlockReason.DailyTotal -> themed.getString(R.string.daily_total_title)
        is BlockReason.SurfaceBudget -> themed.getString(R.string.surface_limit_title, surfaceName(reason))
    }

    private fun sub(reason: BlockReason, status: OverlayStatus): String = when (reason) {
        is BlockReason.SurfaceBudget -> themed.getString(
            R.string.surface_limit_sub,
            Durations.hm(status.surfaceUsedMillis[reason.surface] ?: 0L),
            surfaceName(reason),
        )
        else -> if (status.overMillis > 0) {
            themed.getString(R.string.pause_sub_over, Durations.hm(status.usedMillis), Durations.minutesText(status.overMillis))
        } else {
            themed.getString(R.string.pause_sub_left, Durations.hm(status.usedMillis), Durations.minutesText(status.leftMillis))
        }
    }

    private fun leaveLabel(reason: BlockReason): String = when (reason) {
        is BlockReason.SurfaceBudget -> themed.getString(R.string.leave_surface, surfaceName(reason))
        else -> themed.getString(R.string.close_instagram)
    }

    private fun neverMind(): String = when (val reason = lastReason) {
        is BlockReason.SurfaceBudget -> themed.getString(R.string.never_mind_leave, surfaceName(reason))
        else -> themed.getString(R.string.wait_never_mind)
    }

    private fun surfaceName(reason: BlockReason.SurfaceBudget) = themed.getString(reason.surface.labelRes())

    private fun overrideChip(status: OverlayStatus) = themed.getString(R.string.override_n_of, status.overridesUsed + 1, status.cap)

    /** "3 overrides left today · this one waits 30s". */
    private fun overrideDetail(override: OverrideAvailability.Available, status: OverlayStatus): String {
        val left = themed.resources.getQuantityString(R.plurals.overrides_left, status.overridesLeft, status.overridesLeft)
        val wait = override.step.challenges.filterIsInstance<ChallengeSpec.Wait>().sumOf { it.seconds }
        return themed.getString(R.string.override_detail, left, Durations.hm(wait * 1_000L))
    }

    private fun timeOf(at: Instant): String = DateFormat.getTimeFormat(themed).format(Date.from(at))

    private fun choiceChip(group: ViewGroup, label: String, value: Any): Chip =
        (inflater.inflate(R.layout.item_choice_chip, group, false) as Chip).apply {
            id = View.generateViewId()
            text = label
            tag = value
        }

    private fun dismiss() {
        blockView = null
        ticker.stop()
        lastTyping = null
        overlay.remove()
    }

    private companion object {
        const val LEAVING_TIMEOUT_MS = 5_000L
        const val DAY_MILLIS = 24 * 60 * 60 * 1_000L
    }
}
