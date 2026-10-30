package com.abhishek.antiscroll.ui

import android.Manifest
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.abhishek.antiscroll.AntiScrollApp
import com.abhishek.antiscroll.R
import com.abhishek.antiscroll.data.BackupFile
import com.abhishek.antiscroll.present.HealthPresenter
import com.abhishek.antiscroll.present.HealthUiState
import com.abhishek.antiscroll.present.LimitEditing
import com.abhishek.antiscroll.present.LimitsPresenter
import com.abhishek.antiscroll.present.LimitsUiState
import com.abhishek.antiscroll.present.TodayPresenter
import com.abhishek.antiscroll.rules.engine.GuardEvent
import com.abhishek.antiscroll.rules.settings.AppSettings
import com.abhishek.antiscroll.rules.settings.SettingsChanger
import com.abhishek.antiscroll.rules.usage.DayStats
import com.abhishek.antiscroll.system.SystemSettings
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The app's one screen with three tabs (docs/UI.md, Navigation): Today, Limits, Settings. Builds
 * each tab's UI state with the presenters and hands it to the tab's binder.
 */
class MainActivity : AppCompatActivity() {

    private val graph get() = (application as AntiScrollApp).graph
    private val isDebuggable by lazy { applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 }
    private val ticker = Handler(Looper.getMainLooper())
    private var secondsSinceRefresh = 0

    private lateinit var today: TodayScreen
    private lateinit var limits: LimitsScreen
    private lateinit var settings: SettingsScreen
    private lateinit var nav: BottomNavigationView
    private var past: List<DayStats> = emptyList()
    private var baseline: List<DayStats> = emptyList()
    private var firstDay: LocalDate? = null

    /** What the health rows last showed; they're redrawn only when a grant changes. */
    private var lastChecks: com.abhishek.antiscroll.present.HealthChecks? = null

    private val pickBackupFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) onBackupFolderPicked(tree)
    }

    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { refreshSettings() }

    /** Once a second: catch the runner up, and redraw Today every second while a session runs, else every 30. */
    private val everySecond = object : Runnable {
        override fun run() {
            graph.runner.catchUp()
            // Android reports Display over other apps and Usage access a moment after the user comes
            // back from granting them, so a check only on resume can read the old value.
            refreshSettings()
            if (++secondsSinceRefresh >= 30 || findViewById<View>(R.id.sessionCard).isVisible) refreshToday()
            ticker.postDelayed(this, 1_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.screens)) { v, insets ->
            v.setPadding(0, insets.getInsets(WindowInsetsCompat.Type.systemBars()).top, 0, 0)
            insets
        }
        today = TodayScreen(findViewById(R.id.today), todayActions)
        limits = LimitsScreen(findViewById(R.id.limits), limitsActions)
        settings = SettingsScreen(findViewById(R.id.settings), settingsActions)
        // History is not built yet: the link stays hidden.
        findViewById<View>(R.id.historyLink).isVisible = false
        nav = findViewById(R.id.nav)
        nav.setOnItemSelectedListener { item ->
            show(item.itemId)
            true
        }
        handle(intent)
        // Before anyone is sent to Accessibility settings, say what it's for (docs/SPEC.md).
        // First launch: the optional sign-in (a restore, on a reinstall), then the disclosure.
        if (savedInstanceState == null) {
            when {
                !SignInActivity.wasShown(this) -> startActivity(Intent(this, SignInActivity::class.java))
                !graph.systemChecks.accessibilityEnabled() && !DisclosureActivity.wasShown(this) ->
                    startActivity(Intent(this, DisclosureActivity::class.java))
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** A reminder notification opens the share sheet straight away. */
    private fun handle(intent: Intent?) {
        if (intent?.action != ACTION_EXPORT) return
        intent.action = null
        nav.selectedItemId = R.id.navSettings
        export()
    }

    private fun export() {
        lifecycleScope.launch {
            val sharer = ExportSharer(this@MainActivity, graph)
            val send = withContext(Dispatchers.IO) { sharer.prepare() }
            if (send != null) sharer.open(send)
        }
    }

    override fun onResume() {
        super.onResume()
        lastChecks = null
        refreshBackup()
        graph.fallback.sync()
        graph.accountSignIn.retryPendingRestore()
        graph.cloudSync.syncIfDue()
        graph.backup.writeIfDue()
        refreshSettings()
        loadHistory()
        ticker.post(everySecond)
    }

    override fun onPause() {
        ticker.removeCallbacks(everySecond)
        super.onPause()
    }

    private fun show(itemId: Int) {
        findViewById<View>(R.id.today).isVisible = itemId == R.id.navToday
        findViewById<View>(R.id.limits).isVisible = itemId == R.id.navLimits
        findViewById<View>(R.id.settings).isVisible = itemId == R.id.navSettings
    }

    /** The calendar days before today and the baseline week: read once per resume, off the main thread. */
    private fun loadHistory() {
        val date = graph.runner.stats.date
        lifecycleScope.launch {
            val (loadedPast, loadedFirst) = withContext(Dispatchers.IO) {
                val loaded = (6L downTo 1L).map { back -> stats(date.minusDays(back)) }
                loaded to listOfNotNull(graph.statsStore.firstDay(), graph.usageStore.firstDay()).minOrNull()
            }
            val first = loadedFirst ?: date
            val loadedBaseline = withContext(Dispatchers.IO) {
                (0L until TodayPresenter.BASELINE_DAYS).map { first.plusDays(it) }
                    .filter { it.isBefore(date) }
                    .map(::stats)
            }
            past = loadedPast
            firstDay = first
            baseline = loadedBaseline
            refreshToday()
            refreshLimits()
        }
    }

    /** Dates from before calendar stats existed are shown from their limit-window counters. */
    private fun stats(date: LocalDate): DayStats =
        graph.statsStore.load(date) ?: graph.usageStore.load(date)?.let(DayStats::from) ?: DayStats(date)

    private fun refreshToday() {
        secondsSinceRefresh = 0
        val runner = graph.runner
        val state = TodayPresenter().present(
            TodayPresenter.Input(
                stats = runner.stats,
                usage = runner.usage,
                past = past,
                budgets = graph.activeBudgets,
                enforcementEnabled = graph.enforcementEnabled,
                guardState = runner.guardState,
                overrideCap = graph.ladderSettings.dailyCap,
                health = graph.health.record,
                checks = graph.systemChecks.snapshot(),
                firstDay = firstDay ?: runner.stats.date,
                now = graph.clock.now(),
            ),
        )
        today.render(state, graph.clock.now())
        nav.getOrCreateBadge(R.id.navSettings).apply {
            isVisible = state.settingsNeedsFix
            clearNumber()
            backgroundColor = ContextCompat.getColor(this@MainActivity, R.color.coral)
        }
    }

    private fun refreshLimits() {
        val stats = graph.runner.stats
        val day = ChronoUnit.DAYS.between(firstDay ?: stats.date, stats.date).toInt() + 1
        limits.render(LimitsPresenter().present(graph.settings, graph.ladderSettings, stats, baseline, day))
    }

    private fun refreshBackup() {
        settings.renderAccount(graph.cloud.googleEmail)
        val backup = graph.backup
        settings.renderBackup(backup.folderName(), backup.lastWrittenAt?.let { Text.time(this, it) })
    }

    /**
     * A folder holding another install's copy is never overwritten unasked: restore it (merged with
     * what's counted here), replace it, or leave it.
     */
    private fun onBackupFolderPicked(tree: Uri) {
        lifecycleScope.launch {
            val backup = graph.backup
            val found = withContext(Dispatchers.IO) { backup.read(tree) }
            if (found == null || found.installId == backup.installId) {
                useFolder(tree)
                return@launch
            }
            MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle(R.string.backup_found_title)
                .setMessage(
                    getString(
                        R.string.backup_found_message,
                        Text.mediumDate(Text.localDate(found.writtenAt)),
                        Text.time(this@MainActivity, found.writtenAt),
                    ),
                )
                .setPositiveButton(R.string.backup_restore) { _, _ -> restore(tree, found) }
                .setNegativeButton(R.string.backup_replace) { _, _ -> useFolder(tree) }
                .setNeutralButton(R.string.cancel, null)
                .show()
        }
    }

    private fun useFolder(tree: Uri) {
        lifecycleScope.launch {
            graph.backup.choose(tree)
            val ok = withContext(Dispatchers.IO) { graph.backup.write() }
            if (!ok) Toast.makeText(this@MainActivity, R.string.backup_failed, Toast.LENGTH_LONG).show()
            refreshBackup()
        }
    }

    private fun restore(tree: Uri, found: BackupFile.Contents) {
        lifecycleScope.launch {
            val today = graph.runner.stats.date
            val window = graph.runner.usage.day
            withContext(Dispatchers.IO) { graph.backup.mergePast(found, today, window) }
            graph.backup.finish(found)
            Toast.makeText(this@MainActivity, R.string.backup_restored, Toast.LENGTH_LONG).show()
            useFolder(tree)
            lastChecks = null
            loadHistory()
        }
    }

    private fun refreshSettings() {
        val checks = graph.systemChecks.snapshot()
        if (checks == lastChecks) return
        lastChecks = checks
        val version = packageManager.getPackageInfo(packageName, 0).versionName
        settings.render(
            HealthPresenter().present(checks),
            debug = isDebuggable,
            shortTimers = graph.shortTimers,
            version = getString(R.string.version_line, version, if (isDebuggable) "debug" else "release"),
        )
        refreshToday() // the alert, the status chip and the Settings dot follow the grants
    }

    /** Tightening applies now; loosening costs the wait and typing (docs/SPEC.md, Self-protection). */
    private fun request(wanted: AppSettings) {
        when (val answer = graph.settingsChanger.request(graph.settings, wanted, graph.runner.usage, graph.clock.now())) {
            is SettingsChanger.Answer.Apply -> {
                graph.applySettings(answer.settings)
                refreshLimits()
                refreshToday()
            }
            is SettingsChanger.Answer.Refused -> MaterialAlertDialogBuilder(this)
                .setMessage(R.string.loosen_refused)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            is SettingsChanger.Answer.Challenge -> LoosenActivity.start(this, answer.wanted)
        }
    }

    private val todayActions = object : TodayScreen.Actions {
        override fun openLimits() { nav.selectedItemId = R.id.navLimits }
        override fun openSettings() { nav.selectedItemId = R.id.navSettings }
        override fun openHistory() = Unit
        override fun turnOnAccessibility() = startActivity(Intent(this@MainActivity, DisclosureActivity::class.java))
        override fun openBattery() = startActivity(SystemSettings.battery(this@MainActivity))
        override fun endSession() {
            graph.runner.handle(GuardEvent.EndSessionRequested(graph.clock.now()))
            refreshToday()
        }
    }

    private val limitsActions = object : LimitsScreen.Actions {
        override fun setGuarding(on: Boolean) = request(graph.settings.copy(enforcementEnabled = on))

        override fun editLimit(row: LimitsUiState.Row) {
            EditLimitSheet(this@MainActivity, row, graph.enforcementEnabled) { minutes ->
                request(LimitEditing.apply(graph.settings, row.target, minutes))
            }.show()
        }
    }

    private val settingsActions = object : SettingsScreen.Actions {
        override fun fix(item: HealthUiState.Item) {
            when (item) {
                HealthUiState.Item.ACCESSIBILITY -> startActivity(Intent(this@MainActivity, DisclosureActivity::class.java))
                HealthUiState.Item.OVERLAY -> startActivity(SystemSettings.overlay(this@MainActivity))
                HealthUiState.Item.BATTERY -> startActivity(SystemSettings.battery(this@MainActivity))
                HealthUiState.Item.USAGE_ACCESS -> startActivity(SystemSettings.usageAccess())
                HealthUiState.Item.NOTIFICATIONS ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        startActivity(SystemSettings.notifications(this@MainActivity))
                    }
            }
        }

        override fun export() = this@MainActivity.export()

        override fun openSignIn() = startActivity(Intent(this@MainActivity, SignInActivity::class.java))

        override fun chooseBackupFolder() = pickBackupFolder.launch(graph.backup.folder ?: graph.backup.suggestedStart)

        override fun setShortTimers(on: Boolean) {
            if (graph.shortTimers != on) graph.shortTimers = on
            lastChecks = null
            refreshToday()
        }
    }

    companion object {
        const val ACTION_EXPORT = "com.abhishek.antiscroll.EXPORT"
    }
}
