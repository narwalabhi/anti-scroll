package com.abhishek.antiscroll.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.abhishek.antiscroll.AntiScrollApp
import com.abhishek.antiscroll.R
import com.abhishek.antiscroll.data.SurfaceMinutesCodec
import com.abhishek.antiscroll.overlay.Ticker
import com.abhishek.antiscroll.overlay.TypingPanel
import com.abhishek.antiscroll.overlay.WaitPanel
import com.abhishek.antiscroll.overlay.fitSystemBars
import com.abhishek.antiscroll.rules.settings.AppSettings
import com.abhishek.antiscroll.rules.unlock.ChallengeAnswer
import com.abhishek.antiscroll.rules.unlock.ChallengePrompt
import com.abhishek.antiscroll.rules.unlock.ChallengeRun
import com.abhishek.antiscroll.rules.unlock.ChallengeStatus
import com.abhishek.antiscroll.rules.unlock.TypingAccuracy
import kotlin.math.roundToInt

/**
 * The cost of loosening a setting (docs/LLD.md, Milestone 6): the wait, then the typing, drawn
 * with the same panels as the override over Instagram. Passing applies the wanted settings;
 * leaving the screen discards the run, so the wait starts over next time.
 */
class LoosenActivity : AppCompatActivity() {

    private val graph get() = (application as AntiScrollApp).graph
    private val ticker = Ticker()
    private lateinit var container: FrameLayout
    private lateinit var wanted: AppSettings
    private lateinit var run: ChallengeRun
    private var lastTyping: Pair<String, String>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        container = FrameLayout(this).also(::setContentView)
        wanted = AppSettings(
            enforcementEnabled = intent.getBooleanExtra(EXTRA_ENFORCEMENT, false),
            dailyTotalMinutes = intent.getIntExtra(EXTRA_DAILY, graph.settings.dailyTotalMinutes),
            surfaceMinutes = SurfaceMinutesCodec.decode(intent.getStringExtra(EXTRA_SURFACES).orEmpty()),
        )
        run = graph.settingsChanger.challenge(graph.clock.now())
        show()
    }

    /** Leaving discards the run (docs/SPEC.md: the wait restarts if you leave the screen). */
    override fun onStop() {
        super.onStop()
        ticker.stop()
        if (!isFinishing) finish()
    }

    private fun show() {
        when (val prompt = run.prompt) {
            is ChallengePrompt.Wait -> showWait(prompt.seconds)
            is ChallengePrompt.Typing -> showTyping(prompt)
        }
    }

    private fun showWait(seconds: Int) {
        val panel = WaitPanel(inflate(R.layout.overlay_wait), ticker)
        panel.bind(
            seconds,
            WaitPanel.Copy(null, getString(R.string.loosen_wait_heading), getString(R.string.loosen_wait_sub), getString(R.string.loosen_keep), null),
            onCancel = ::finish,
            onDone = { submit(ChallengeAnswer.WaitDone) },
        )
    }

    private fun showTyping(prompt: ChallengePrompt.Typing) {
        val panel = TypingPanel(inflate(R.layout.overlay_typing))
        val needed = (prompt.minAccuracy * 100).roundToInt()
        val feedback = lastTyping?.let { (target, typed) ->
            getString(R.string.typing_feedback, (TypingAccuracy.score(typed, target) * 100).toInt(), needed)
        }
        panel.bind(
            prompt.text,
            prompt.minAccuracy,
            TypingPanel.Copy(null, getString(R.string.typing_title), feedback, getString(R.string.loosen_keep)),
            onSubmit = { typed ->
                lastTyping = prompt.text to typed
                submit(ChallengeAnswer.Typed(typed))
            },
            onCancel = ::finish,
        )
        panel.focus()
    }

    private fun submit(answer: ChallengeAnswer) {
        val step = run.submit(answer, graph.clock.now())
        val changed = step.run.prompt != run.prompt
        run = step.run
        when (step.status) {
            ChallengeStatus.Passed -> {
                graph.applySettings(wanted)
                Toast.makeText(this, R.string.loosen_done, Toast.LENGTH_SHORT).show()
                finish()
            }
            ChallengeStatus.InProgress -> if (changed) show() // an early wait report changes nothing
        }
    }

    private fun inflate(layout: Int) = LayoutInflater.from(this).inflate(layout, container, false).also {
        it.fitSystemBars()
        container.removeAllViews()
        container.addView(it)
    }

    companion object {
        private const val EXTRA_ENFORCEMENT = "enforcement"
        private const val EXTRA_DAILY = "daily"
        private const val EXTRA_SURFACES = "surfaces"

        fun start(context: Context, wanted: AppSettings) {
            context.startActivity(
                Intent(context, LoosenActivity::class.java)
                    .putExtra(EXTRA_ENFORCEMENT, wanted.enforcementEnabled)
                    .putExtra(EXTRA_DAILY, wanted.dailyTotalMinutes)
                    .putExtra(EXTRA_SURFACES, SurfaceMinutesCodec.encode(wanted.surfaceMinutes)),
            )
        }
    }
}
