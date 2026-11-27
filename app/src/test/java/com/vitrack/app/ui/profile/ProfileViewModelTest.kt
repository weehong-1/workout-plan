package com.vitrack.app.ui.profile

import com.vitrack.app.data.account.LocalData
import com.vitrack.app.data.auth.Account
import com.vitrack.app.data.auth.SignInMethod
import com.vitrack.app.data.backend.BackendException
import com.vitrack.app.data.export.DataExporter
import com.vitrack.app.data.food.FoodLabel
import com.vitrack.app.data.settings.GoalKind
import com.vitrack.app.data.settings.ReminderKind
import com.vitrack.app.data.settings.WeightGoal
import com.vitrack.app.data.settings.WeightUnit
import com.vitrack.app.testing.FIXED_CLOCK
import com.vitrack.app.testing.FakeAuthRepository
import com.vitrack.app.testing.FakeFoodRepository
import com.vitrack.app.testing.FakeProfileRepository
import com.vitrack.app.testing.FakeSettingsRepository
import com.vitrack.app.testing.FakeShareRepository
import com.vitrack.app.testing.FakeWeightRepository
import com.vitrack.app.testing.MainDispatcherRule
import com.vitrack.app.testing.TODAY
import com.vitrack.app.testing.foodPhoto
import com.vitrack.app.testing.weighIn
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** TODAY is 26 Nov 2026; the latest week's weigh-ins average 78.5 kg. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val settings = FakeSettingsRepository()
    private val auth = FakeAuthRepository().apply {
        current.value = Account("user-1", "vernon@gmail.com", "Vernon Koh", SignInMethod.Google)
    }
    private val weight = FakeWeightRepository(
        listOf(
            weighIn(TODAY, 78.4, photoIds = listOf(1)),
            weighIn(TODAY.minusDays(3), 78.6),
            weighIn(TODAY.minusDays(10), 80.0),
        ),
    )
    private val food = FakeFoodRepository(listOf(foodPhoto(5, TODAY.atTime(12, 30), FoodLabel.Lunch, note = "rice, chicken")))
    private val wiped = mutableListOf<String>()
    private val localData = LocalData { auth.events += "local"; wiped += "all" }

    private fun TestScope.viewModel(): ProfileViewModel {
        val viewModel = ProfileViewModel(
            settings, auth, FakeProfileRepository("Vernon"), weight, FakeShareRepository(),
            DataExporter(weight, food), localData, FIXED_CLOCK,
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }

    @Test
    fun `shows the account and the weekly average`() = runTest {
        val state = viewModel().uiState.value

        assertEquals("Vernon Koh", state.name)
        assertEquals(SignInMethod.Google, state.method)
        assertEquals(78.5, state.weeklyAverageKg!!, 0.0001)
        assertNull(state.progress)
    }

    @Test
    fun `a lose target below the average shows progress from today`() = runTest {
        val viewModel = viewModel()

        viewModel.setGoalWeight(75.0, WeightUnit.Kg)

        val goal = settings.weightGoal.value
        assertEquals(75.0, goal.targetKg!!, 0.0)
        assertEquals(78.5, goal.startKg!!, 0.0)
        assertEquals(TODAY, goal.startedOn)
        val progress = viewModel.uiState.value.progress!!
        assertEquals(3.4, progress.toGoKg, 0.0001)
        assertEquals(0.0286f, progress.fraction, 0.001f)
    }

    @Test
    fun `a target the wrong way is refused`() = runTest {
        val viewModel = viewModel()

        viewModel.setGoalWeight(82.0, WeightUnit.Kg)

        assertNull(settings.weightGoal.value.targetKg)
        assertEquals(ProfileMessage.GoalWrongWay, viewModel.uiState.value.message)
    }

    @Test
    fun `targets typed in lb are kept in kg`() = runTest {
        val viewModel = viewModel()

        viewModel.setGoalWeight(165.0, WeightUnit.Lb)

        assertEquals(74.8, settings.weightGoal.value.targetKg!!, 0.0)
    }

    @Test
    fun `maintain holds the weekly average and gain drops a lose target`() = runTest {
        settings.weightGoal.value = WeightGoal(GoalKind.Lose, targetKg = 75.0, startKg = 80.0)
        val viewModel = viewModel()

        viewModel.setGoalKind(GoalKind.Maintain)
        assertEquals(WeightGoal(GoalKind.Maintain, 78.5, 78.5, TODAY), settings.weightGoal.value)

        settings.weightGoal.value = WeightGoal(GoalKind.Lose, targetKg = 75.0, startKg = 80.0)
        viewModel.setGoalKind(GoalKind.Gain)
        assertEquals(WeightGoal(GoalKind.Gain), settings.weightGoal.value)
    }

    @Test
    fun `reminders need notifications`() = runTest {
        val viewModel = viewModel()

        viewModel.setReminder(ReminderKind.Food, on = true, allowed = false)
        assertFalse(settings.reminders.value.food)
        assertEquals(ProfileMessage.NotificationsBlocked, viewModel.uiState.value.message)

        viewModel.setReminder(ReminderKind.Food, on = true)
        assertTrue(settings.reminders.value.food)
    }

    @Test
    fun `a typed name wins over the account's`() = runTest {
        val viewModel = viewModel()

        viewModel.setDisplayName(" Vern ")

        assertEquals("Vern", viewModel.uiState.value.name)
    }

    @Test
    fun `delete needs the word, then removes the server account before the phone's data`() = runTest {
        val viewModel = viewModel()

        viewModel.deleteAccount("delete me")
        assertFalse(auth.deleted)

        viewModel.deleteAccount("DELETE")
        assertEquals(listOf("server", "local"), auth.events)
        assertNull(auth.current.value)
    }

    @Test
    fun `when the server refuses, the phone's data stays`() = runTest {
        auth.failNext = BackendException.Network(RuntimeException())
        val viewModel = viewModel()

        viewModel.deleteAccount("DELETE")

        assertTrue(wiped.isEmpty())
        assertEquals(ProfileMessage.Network, viewModel.uiState.value.message)
        assertNull(viewModel.uiState.value.busy)
    }

    @Test
    fun `export writes the weigh-ins and food as CSV in a zip`() = runTest {
        val viewModel = viewModel()
        val out = ByteArrayOutputStream()

        viewModel.export(out)
        // The export runs on the IO dispatcher: wait for its result.
        viewModel.uiState.first { it.message != null }

        val files = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
            generateSequence { zip.nextEntry }.forEach { files[it.name] = zip.readBytes().decodeToString() }
        }
        assertEquals(setOf("weight.csv", "food.csv"), files.keys) // the fake photo files don't exist
        assertEquals(
            "date,weight_kg,photos\n2026-11-16,80.00,\n2026-11-23,78.60,\n2026-11-26,78.40,\n",
            files["weight.csv"],
        )
        assertEquals("date,time,label,note,photo\n2026-11-26,12:30,Lunch,\"rice, chicken\",\n", files["food.csv"])
        assertEquals(ProfileMessage.Exported(3, 1), viewModel.uiState.value.message)
    }
}
