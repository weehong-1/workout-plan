package com.vitrack.app.data.whoop

import com.vitrack.app.data.activity.ActivitySnapshot
import com.vitrack.app.data.activity.DailyActivity
import com.vitrack.app.data.activity.HeartSummary
import com.vitrack.app.data.activity.SleepStages
import com.vitrack.app.data.activity.SleepSummary
import com.vitrack.app.data.activity.Workout
import com.vitrack.app.data.activity.WorkoutKind
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.math.roundToInt

/** Turns WHOOP's records into the app's days, the way WHOOP's own app labels them. */
object WhoopMapper {

    /**
     * The day a cycle belongs to. A WHOOP cycle starts when you fall asleep,
     * so one that starts in the evening is the next day's: falling asleep at
     * 23:30 on the 27th starts the 28th.
     */
    fun cycleDate(start: String, offset: String): LocalDate =
        Instant.parse(start).atOffset(zone(offset)).plusHours(12).toLocalDate()

    /** One day per date from [from] to [to], oldest first; days without a cycle are zeros. */
    fun days(records: WhoopRecords, from: LocalDate, to: LocalDate): List<DailyActivity> {
        val byDate = records.cycles.associateBy { cycleDate(it.start, it.timezoneOffset) }
        val recovery = records.recoveries.associateBy { it.cycleId }
        val sleep = mainSleeps(records)
        return generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.map { date ->
            val cycle = byDate[date] ?: return@map DailyActivity(date, 0, 0, 0.0, 0, 0, 0, 0)
            val score = cycle.score
            val night = sleep[cycle.id]?.score
            DailyActivity(
                date = date,
                sleepPerformance = night?.performance?.roundToInt() ?: 0,
                recovery = recovery[cycle.id]?.score?.recoveryScore?.roundToInt() ?: 0,
                strain = score?.strain ?: 0.0,
                steps = cycle.stepCount ?: 0,
                calories = ((score?.kilojoule ?: 0.0) / KJ_PER_KCAL).roundToInt(),
                averageHeartRate = score?.averageHeartRate ?: 0,
                maxHeartRate = score?.maxHeartRate ?: 0,
                sleepMinutes = night?.let { asleepMinutes(it.stages) } ?: 0,
            )
        }.toList()
    }

    /** Today's Activity tab: today, its heart, last night's sleep, today's workouts and the week. */
    fun snapshot(records: WhoopRecords, today: LocalDate, stepGoal: Int, syncedAt: LocalTime): ActivitySnapshot {
        val week = days(records, today.minusDays(6), today)
        val todayCycle = records.cycles.firstOrNull { cycleDate(it.start, it.timezoneOffset) == today }
        val recoveryByDate = records.recoveries.mapNotNull { r ->
            val cycle = records.cycles.firstOrNull { it.id == r.cycleId } ?: return@mapNotNull null
            cycleDate(cycle.start, cycle.timezoneOffset) to r
        }.toMap()
        val todayRecovery = todayCycle?.let { c -> records.recoveries.firstOrNull { it.cycleId == c.id } }?.score
        // A missing day repeats the day before, so the line doesn't drop to zero.
        var last = 0
        val restingWeek = week.map { day ->
            recoveryByDate[day.date]?.score?.restingHeartRate?.roundToInt()?.also { last = it } ?: last
        }
        val night = todayCycle?.let { mainSleeps(records)[it.id] }?.score?.stages
        return ActivitySnapshot(
            today = week.last(),
            stepGoal = stepGoal,
            heart = HeartSummary(
                restingBpm = todayRecovery?.restingHeartRate?.roundToInt() ?: 0,
                hrvMs = todayRecovery?.hrvMilli?.roundToInt() ?: 0,
                restingLast7Days = restingWeek,
            ),
            sleep = SleepSummary(
                durationMinutes = night?.let(::asleepMinutes) ?: 0,
                stages = night?.let(::stages) ?: SleepStages(0f, 0f, 0f, 0f),
            ),
            workouts = records.workouts
                .filter { Instant.parse(it.start).atOffset(zone(it.timezoneOffset)).toLocalDate() == today }
                .sortedBy { it.start }
                .map(::workout),
            lastSevenDays = week,
            syncedAt = syncedAt,
        )
    }

    /** The main (not nap) sleep of each cycle. */
    private fun mainSleeps(records: WhoopRecords): Map<Long, WhoopSleep> =
        records.sleeps.filter { !it.nap && it.cycleId != null }.associateBy { it.cycleId!! }

    private fun asleepMinutes(s: WhoopSleep.Stages): Int = ((s.light + s.deep + s.rem) / 60_000).toInt()

    private fun stages(s: WhoopSleep.Stages): SleepStages {
        val total = (s.awake + s.light + s.deep + s.rem).toFloat()
        if (total <= 0f) return SleepStages(0f, 0f, 0f, 0f)
        return SleepStages(s.awake / total, s.light / total, s.deep / total, s.rem / total)
    }

    private fun workout(w: WhoopWorkout): Workout {
        val start = Instant.parse(w.start)
        val zones = w.score?.zones ?: WhoopWorkout.Zones()
        val inZones = listOf(zones.one, zones.two, zones.three, zones.four, zones.five)
        val total = inZones.sum().toFloat()
        val name = w.sportName.replace('_', ' ').replace('-', ' ').trim()
            .replaceFirstChar { it.titlecase() }.ifBlank { "Workout" }
        val lower = name.lowercase()
        return Workout(
            kind = when {
                "cycl" in lower || "bik" in lower || "spin" in lower -> WorkoutKind.Cycling
                "walk" in lower || "hik" in lower -> WorkoutKind.Walking
                else -> WorkoutKind.Other
            },
            name = name,
            start = start.atOffset(zone(w.timezoneOffset)).toLocalTime(),
            durationMinutes = Duration.between(start, Instant.parse(w.end)).toMinutes().toInt(),
            averageHeartRate = w.score?.averageHeartRate ?: 0,
            maxHeartRate = w.score?.maxHeartRate ?: 0,
            strain = w.score?.strain ?: 0.0,
            zones = inZones.map { if (total > 0f) it / total else 0f },
        )
    }

    private fun zone(offset: String): ZoneOffset = runCatching { ZoneOffset.of(offset) }.getOrDefault(ZoneOffset.UTC)

    private const val KJ_PER_KCAL = 4.184
}
