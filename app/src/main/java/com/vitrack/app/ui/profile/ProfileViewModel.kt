package com.vitrack.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitrack.app.data.account.LocalData
import com.vitrack.app.data.auth.AuthRepository
import com.vitrack.app.data.auth.SignInMethod
import com.vitrack.app.data.backend.BackendException
import com.vitrack.app.data.export.DataExporter
import com.vitrack.app.data.profile.ProfileRepository
import com.vitrack.app.data.settings.GoalKind
import com.vitrack.app.data.settings.ReminderKind
import com.vitrack.app.data.settings.Reminders
import com.vitrack.app.data.settings.SettingsRepository
import com.vitrack.app.data.settings.ThemeMode
import com.vitrack.app.data.settings.WeightGoal
import com.vitrack.app.data.settings.WeightUnit
import com.vitrack.app.data.share.ShareRepository
import com.vitrack.app.data.weight.MAX_WEIGHT_KG
import com.vitrack.app.data.weight.MIN_WEIGHT_KG
import com.vitrack.app.data.weight.WeighIn
import com.vitrack.app.data.weight.WeightRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.OutputStream
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Progress toward a lose or gain target, measured from where it was set. */
data class GoalProgress(
    val targetKg: Double,
    val startKg: Double,
    val startedOn: LocalDate?,
    val nowKg: Double,
    /** 0..1 of the way from start to target. */
    val fraction: Float,
    /** How far is left, in kg; 0 once reached. */
    val toGoKg: Double,
) {
    val reached: Boolean get() = toGoKg <= 0.0
}

sealed interface ProfileMessage {
    data class Exported(val weighIns: Int, val foodPhotos: Int) : ProfileMessage
    data object ExportFailed : ProfileMessage
    data object GoalWrongWay : ProfileMessage
    data object NotificationsBlocked : ProfileMessage
    data object Network : ProfileMessage
    data class Server(val message: String) : ProfileMessage
}

enum class ProfileBusy { Exporting, SigningOut, Deleting }

data class ProfileUiState(
    val isLoading: Boolean = true,
    val name: String? = null,
    val email: String? = null,
    val method: SignInMethod? = null,
    /** A server is set up in this build (sign-in, sharing, account deletion). */
    val serverAvailable: Boolean = false,
    val unit: WeightUnit = WeightUnit.Kg,
    val goal: WeightGoal = WeightGoal(),
    /** The average of the latest week of weigh-ins; null with none. */
    val weeklyAverageKg: Double? = null,
    val progress: GoalProgress? = null,
    val stepGoal: Int = 0,
    val heightCm: Int? = null,
    val theme: ThemeMode = ThemeMode.System,
    val reminders: Reminders = Reminders(),
    val activeLinks: Int = 0,
    val nextLinkExpiry: LocalDate? = null,
    val busy: ProfileBusy? = null,
    val message: ProfileMessage? = null,
)

/** The average of the weigh-ins in the 7 days up to the latest one. */
internal fun weeklyAverage(weighIns: List<WeighIn>): Double? {
    val latest = weighIns.maxByOrNull { it.date } ?: return null
    val week = weighIns.filter { !it.date.isBefore(latest.date.minusDays(6)) && !it.date.isAfter(latest.date) }
    return week.map { it.weightKg }.average()
}

/** Where a lose or gain target stands; null for Maintain or with no target. */
internal fun goalProgress(goal: WeightGoal, weighIns: List<WeighIn>): GoalProgress? {
    val target = goal.targetKg ?: return null
    if (goal.kind == GoalKind.Maintain) return null
    val now = weighIns.maxByOrNull { it.date }?.weightKg ?: return null
    val start = goal.startKg ?: now
    val left = if (goal.kind == GoalKind.Lose) now - target else target - now
    val span = abs(start - target)
    val fraction = if (span < 0.05) 1f else (1.0 - left / span).toFloat().coerceIn(0f, 1f)
    return GoalProgress(target, start, goal.startedOn, now, fraction, left.coerceAtLeast(0.0))
}

private fun round1(kg: Double) = (kg * 10).roundToInt() / 10.0

private data class Session(val busy: ProfileBusy? = null, val message: ProfileMessage? = null)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val auth: AuthRepository,
    profile: ProfileRepository,
    weight: WeightRepository,
    share: ShareRepository,
    private val exporter: DataExporter,
    private val localData: LocalData,
    private val clock: Clock,
) : ViewModel() {

    private val session = MutableStateFlow(Session())

    private data class Body(
        val unit: WeightUnit,
        val goal: WeightGoal,
        val stepGoal: Int,
        val heightCm: Int?,
        val theme: ThemeMode,
    )

    private val body = combine(settings.weightUnit, settings.weightGoal, settings.stepGoal, settings.heightCm, settings.themeMode, ::Body)

    private val weighIns = weight.observeWeighIns()

    val uiState: StateFlow<ProfileUiState> = combine(
        combine(auth.account, profile.observeDisplayName(), settings.displayName) { account, short, typed -> Triple(account, short, typed) },
        body,
        combine(weighIns, settings.reminders, share.observeLinks()) { w, r, l -> Triple(w, r, l) },
        session,
    ) { (account, short, typed), b, (entries, reminders, links), s ->
        val active = links.filter { !it.isExpired }
        ProfileUiState(
            isLoading = false,
            name = typed ?: account?.name ?: short,
            email = account?.email,
            method = account?.method,
            serverAvailable = auth.isAvailable,
            unit = b.unit,
            goal = b.goal,
            weeklyAverageKg = weeklyAverage(entries),
            progress = goalProgress(b.goal, entries),
            stepGoal = b.stepGoal,
            heightCm = b.heightCm,
            theme = b.theme,
            reminders = reminders,
            activeLinks = active.size,
            nextLinkExpiry = active.mapNotNull { it.expiresOn }.minOrNull(),
            busy = s.busy,
            message = s.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())

    /**
     * Lose and Gain keep a target only if it still lies that way from the
     * weekly average; Maintain holds the weekly average as its target.
     */
    fun setGoalKind(kind: GoalKind) {
        viewModelScope.launch {
            val goal = settings.weightGoal.first()
            if (goal.kind == kind) return@launch
            val average = weeklyAverage(weighIns.first())
            val next = when (kind) {
                GoalKind.Maintain -> WeightGoal(kind, average?.let(::round1), average?.let(::round1), LocalDate.now(clock))
                else -> {
                    val target = goal.targetKg?.takeIf { average != null && pointsTheWay(kind, it, average) }
                    WeightGoal(kind, target, target?.let { goal.startKg ?: average }, target?.let { goal.startedOn })
                }
            }
            settings.setWeightGoal(next)
        }
    }

    /** A new target in the shown unit; progress restarts from today's weekly average. */
    fun setGoalWeight(value: Double, unit: WeightUnit) {
        viewModelScope.launch {
            val kg = round1(unit.toKg(value))
            val goal = settings.weightGoal.first()
            val average = weeklyAverage(weighIns.first())
            if (kg !in MIN_WEIGHT_KG..MAX_WEIGHT_KG || (average != null && !pointsTheWay(goal.kind, kg, average))) {
                session.update { it.copy(message = ProfileMessage.GoalWrongWay) }
                return@launch
            }
            settings.setWeightGoal(goal.copy(targetKg = kg, startKg = average?.let(::round1) ?: kg, startedOn = LocalDate.now(clock)))
        }
    }

    private fun pointsTheWay(kind: GoalKind, targetKg: Double, averageKg: Double) = when (kind) {
        GoalKind.Lose -> targetKg < averageKg
        GoalKind.Gain -> targetKg > averageKg
        GoalKind.Maintain -> true
    }

    fun setStepGoal(steps: Int) = launch { settings.setStepGoal(steps) }

    fun setHeightCm(cm: Int?) = launch { settings.setHeightCm(cm) }

    fun setUnit(unit: WeightUnit) = launch { settings.setWeightUnit(unit) }

    fun setTheme(theme: ThemeMode) = launch { settings.setThemeMode(theme) }

    fun setDisplayName(name: String) = launch { settings.setDisplayName(name) }

    /** Called once notifications are allowed (or not needed); [allowed] false says they're blocked. */
    fun setReminder(kind: ReminderKind, on: Boolean, allowed: Boolean = true) {
        if (on && !allowed) {
            session.update { it.copy(message = ProfileMessage.NotificationsBlocked) }
            return
        }
        launch { settings.setReminder(kind, on) }
    }

    fun export(out: OutputStream) {
        session.update { it.copy(busy = ProfileBusy.Exporting, message = null) }
        viewModelScope.launch {
            val message = try {
                val summary = exporter.export(out)
                ProfileMessage.Exported(summary.weighIns, summary.foodPhotos)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ProfileMessage.ExportFailed
            }
            session.update { it.copy(busy = null, message = message) }
        }
    }

    fun exportFailed() = session.update { it.copy(message = ProfileMessage.ExportFailed) }

    /** Signs out; the app shows Sign in again. Data on the phone stays. */
    fun signOut() = run(ProfileBusy.SigningOut) { auth.signOut() }

    /**
     * Deletes the server account first, then this phone's data, so a failure
     * part-way never leaves the phone empty with the account still there.
     */
    fun deleteAccount(confirmation: String) {
        if (confirmation.trim() != DELETE_WORD) return
        run(ProfileBusy.Deleting) {
            if (auth.isAvailable) auth.deleteAccount()
            localData.wipe()
        }
    }

    fun dismissMessage() = session.update { it.copy(message = null) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private fun run(busy: ProfileBusy, block: suspend () -> Unit) {
        if (session.value.busy != null) return
        session.update { it.copy(busy = busy, message = null) }
        viewModelScope.launch {
            val message = try {
                block()
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: BackendException.Server) {
                ProfileMessage.Server(e.message.orEmpty())
            } catch (e: Exception) {
                ProfileMessage.Network
            }
            session.update { it.copy(busy = null, message = message) }
        }
    }

    companion object {
        /** The word typed to confirm, as the web app's `delete_own_account` requires. */
        const val DELETE_WORD = "DELETE"
    }
}
