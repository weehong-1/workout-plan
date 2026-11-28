package com.vitrack.app.data.whoop

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * WHOOP's own v2 records, as the `whoop-callback` function passes them on
 * (supabase/functions/whoop-callback). Only the fields the app uses; every
 * score is optional because WHOOP leaves it out until a record is scored.
 */
@Serializable
data class WhoopRecords(
    val cycles: List<WhoopCycle> = emptyList(),
    val recoveries: List<WhoopRecovery> = emptyList(),
    val sleeps: List<WhoopSleep> = emptyList(),
    val workouts: List<WhoopWorkout> = emptyList(),
)

@Serializable
data class WhoopCycle(
    val id: Long,
    val start: String,
    val end: String? = null,
    @SerialName("timezone_offset") val timezoneOffset: String = "Z",
    val score: Score? = null,
    /** Null when WHOOP has no step data for the cycle. */
    @SerialName("step_count") val stepCount: Int? = null,
) {
    @Serializable
    data class Score(
        val strain: Double = 0.0,
        val kilojoule: Double = 0.0,
        @SerialName("average_heart_rate") val averageHeartRate: Int = 0,
        @SerialName("max_heart_rate") val maxHeartRate: Int = 0,
    )
}

@Serializable
data class WhoopRecovery(
    @SerialName("cycle_id") val cycleId: Long,
    val score: Score? = null,
) {
    @Serializable
    data class Score(
        @SerialName("recovery_score") val recoveryScore: Double = 0.0,
        @SerialName("resting_heart_rate") val restingHeartRate: Double = 0.0,
        @SerialName("hrv_rmssd_milli") val hrvMilli: Double = 0.0,
    )
}

@Serializable
data class WhoopSleep(
    @SerialName("cycle_id") val cycleId: Long? = null,
    val start: String,
    val end: String? = null,
    val nap: Boolean = false,
    val score: Score? = null,
) {
    @Serializable
    data class Score(
        @SerialName("stage_summary") val stages: Stages = Stages(),
        @SerialName("sleep_performance_percentage") val performance: Double? = null,
    )

    @Serializable
    data class Stages(
        @SerialName("total_in_bed_time_milli") val inBed: Long = 0,
        @SerialName("total_awake_time_milli") val awake: Long = 0,
        @SerialName("total_light_sleep_time_milli") val light: Long = 0,
        @SerialName("total_slow_wave_sleep_time_milli") val deep: Long = 0,
        @SerialName("total_rem_sleep_time_milli") val rem: Long = 0,
    )
}

@Serializable
data class WhoopWorkout(
    val start: String,
    val end: String,
    @SerialName("timezone_offset") val timezoneOffset: String = "Z",
    @SerialName("sport_name") val sportName: String = "",
    val score: Score? = null,
) {
    @Serializable
    data class Score(
        val strain: Double = 0.0,
        @SerialName("average_heart_rate") val averageHeartRate: Int = 0,
        @SerialName("max_heart_rate") val maxHeartRate: Int = 0,
        @SerialName("zone_durations") val zones: Zones = Zones(),
    )

    @Serializable
    data class Zones(
        @SerialName("zone_one_milli") val one: Long = 0,
        @SerialName("zone_two_milli") val two: Long = 0,
        @SerialName("zone_three_milli") val three: Long = 0,
        @SerialName("zone_four_milli") val four: Long = 0,
        @SerialName("zone_five_milli") val five: Long = 0,
    )
}
