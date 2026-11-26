import Foundation
import HealthKit

/// HealthKit implementation of `HealthStore`.
///
/// An actor, so every query is started off the main actor. Read only: the app
/// never asks to write.
actor HealthKitStore: HealthStore {
    private let store = HKHealthStore()
    private let ranges: DateRanges

    init(calendar: Calendar = .current) {
        self.ranges = DateRanges(calendar: calendar)
    }

    /// Types listed on the Why Health screen.
    static var readTypes: Set<HKObjectType> {
        [
            HKQuantityType(.stepCount),
            HKQuantityType(.activeEnergyBurned),
            HKQuantityType(.appleExerciseTime),
            HKCategoryType(.appleStandHour),
            HKObjectType.activitySummaryType(),
            HKQuantityType(.heartRate),
            HKQuantityType(.restingHeartRate),
            HKCategoryType(.sleepAnalysis),
            HKObjectType.workoutType(),
            // Needed for the distance figure on the Steps detail screen.
            HKQuantityType(.distanceWalkingRunning),
        ]
    }

    nonisolated var isAvailable: Bool {
        HKHealthStore.isHealthDataAvailable()
    }

    // MARK: Authorization

    func needsAuthorizationRequest() async -> Bool {
        guard isAvailable else { return false }
        do {
            let status = try await store.statusForAuthorizationRequest(toShare: [], read: Self.readTypes)
            return status == .shouldRequest
        } catch {
            return false
        }
    }

    func requestAuthorization() async throws {
        guard isAvailable else { return }
        try await store.requestAuthorization(toShare: [], read: Self.readTypes)
    }

    // MARK: Activity rings

    func activityRings(days: [Date]) async throws -> [DayRings] {
        guard let first = days.first, let last = days.last else { return [] }
        let calendar = ranges.calendar

        var startComponents = calendar.dateComponents([.era, .year, .month, .day], from: first)
        startComponents.calendar = calendar
        var endComponents = calendar.dateComponents([.era, .year, .month, .day], from: last)
        endComponents.calendar = calendar
        let predicate = HKQuery.predicate(forActivitySummariesBetweenStart: startComponents, end: endComponents)

        let summaries: [HKActivitySummary] = try await withCheckedThrowingContinuation { continuation in
            let query = HKActivitySummaryQuery(predicate: predicate) { _, summaries, error in
                if let error, !Self.isNoData(error) {
                    continuation.resume(throwing: error)
                } else {
                    continuation.resume(returning: summaries ?? [])
                }
            }
            store.execute(query)
        }

        var byDay: [Date: RingValues] = [:]
        for summary in summaries {
            guard let date = summary.dateComponents(for: calendar).date else { continue }
            byDay[calendar.startOfDay(for: date)] = Self.rings(from: summary)
        }
        return days.map { DayRings(day: $0, rings: byDay[calendar.startOfDay(for: $0)] ?? .empty) }
    }

    private static func rings(from summary: HKActivitySummary) -> RingValues {
        // People who set a Move goal in minutes (Move time) get no kcal goal;
        // the Move ring then shows 0. Handled properly in a later phase.
        RingValues(
            move: summary.activeEnergyBurned.doubleValue(for: .kilocalorie()),
            moveGoal: summary.activeEnergyBurnedGoal.doubleValue(for: .kilocalorie()),
            exercise: summary.appleExerciseTime.doubleValue(for: .minute()),
            exerciseGoal: summary.exerciseTimeGoal?.doubleValue(for: .minute()) ?? 30,
            stand: summary.appleStandHours.doubleValue(for: .count()),
            standGoal: summary.standHoursGoal?.doubleValue(for: .count()) ?? 12
        )
    }

    // MARK: Quantities

    func series(_ metric: QuantityMetric, in interval: DateInterval, bucket: Bucket) async throws -> [DatedValue] {
        let predicate = HKQuery.predicateForSamples(withStart: interval.start, end: interval.end, options: .strictStartDate)
        let descriptor = HKStatisticsCollectionQueryDescriptor(
            predicate: .quantitySample(type: metric.quantityType, predicate: predicate),
            options: metric.isCumulative ? .cumulativeSum : .discreteAverage,
            anchorDate: ranges.bucketStart(for: interval.start, bucket: bucket),
            intervalComponents: bucket.dateComponents
        )

        var values: [DatedValue] = []
        do {
            let collection = try await descriptor.result(for: store)
            values = collection.statistics().compactMap { statistics -> DatedValue? in
                let quantity = metric.isCumulative ? statistics.sumQuantity() : statistics.averageQuantity()
                guard let quantity else { return nil }
                return DatedValue(date: statistics.startDate, value: quantity.doubleValue(for: metric.unit))
            }
        } catch where Self.isNoData(error) {
            values = []
        }
        return metric.isCumulative ? ranges.fill(values, interval: interval, bucket: bucket) : values
    }

    func total(_ metric: QuantityMetric, in interval: DateInterval) async throws -> Double {
        let predicate = HKQuery.predicateForSamples(withStart: interval.start, end: interval.end, options: .strictStartDate)
        let descriptor = HKStatisticsQueryDescriptor(
            predicate: .quantitySample(type: metric.quantityType, predicate: predicate),
            options: metric.isCumulative ? .cumulativeSum : .discreteAverage
        )
        do {
            let statistics = try await descriptor.result(for: store)
            let quantity = metric.isCumulative ? statistics?.sumQuantity() : statistics?.averageQuantity()
            return quantity?.doubleValue(for: metric.unit) ?? 0
        } catch where Self.isNoData(error) {
            return 0
        }
    }

    func latest(_ metric: QuantityMetric, in interval: DateInterval) async throws -> DatedValue? {
        let predicate = HKQuery.predicateForSamples(withStart: interval.start, end: interval.end, options: [])
        let descriptor = HKSampleQueryDescriptor(
            predicates: [.quantitySample(type: metric.quantityType, predicate: predicate)],
            sortDescriptors: [SortDescriptor(\.endDate, order: .reverse)],
            limit: 1
        )
        do {
            guard let sample = try await descriptor.result(for: store).first else { return nil }
            return DatedValue(date: sample.endDate, value: sample.quantity.doubleValue(for: metric.unit))
        } catch where Self.isNoData(error) {
            return nil
        }
    }

    // MARK: Sleep

    func sleepSegments(in interval: DateInterval) async throws -> [SleepSegment] {
        // No options: include samples that overlap the window, so a night that
        // started before 6 PM is not cut off.
        let predicate = HKQuery.predicateForSamples(withStart: interval.start, end: interval.end, options: [])
        let descriptor = HKSampleQueryDescriptor(
            predicates: [.categorySample(type: HKCategoryType(.sleepAnalysis), predicate: predicate)],
            sortDescriptors: [SortDescriptor(\.startDate, order: .forward)],
            limit: nil
        )
        do {
            let samples = try await descriptor.result(for: store)
            return samples.compactMap { sample -> SleepSegment? in
                guard let stage = Self.stage(for: sample.value) else { return nil }
                return SleepSegment(stage: stage, start: sample.startDate, end: sample.endDate)
            }
        } catch where Self.isNoData(error) {
            return []
        }
    }

    private static func stage(for rawValue: Int) -> SleepStage? {
        guard let value = HKCategoryValueSleepAnalysis(rawValue: rawValue) else { return nil }
        switch value {
        case .inBed: return nil
        case .awake: return .awake
        case .asleepREM: return .rem
        case .asleepCore: return .core
        case .asleepDeep: return .deep
        case .asleepUnspecified: return .unspecified
        @unknown default: return nil
        }
    }

    // MARK: Workouts

    func workouts(in interval: DateInterval) async throws -> [WorkoutSummary] {
        let predicate = HKQuery.predicateForSamples(withStart: interval.start, end: interval.end, options: .strictStartDate)
        let descriptor = HKSampleQueryDescriptor(
            predicates: [.workout(predicate)],
            sortDescriptors: [SortDescriptor(\.startDate, order: .reverse)],
            limit: nil
        )
        do {
            let workouts = try await descriptor.result(for: store)
            return workouts.map { workout in
                let label = Self.label(for: workout.workoutActivityType)
                return WorkoutSummary(
                    id: workout.uuid,
                    name: label.name,
                    systemImage: label.systemImage,
                    start: workout.startDate,
                    duration: workout.duration
                )
            }
        } catch where Self.isNoData(error) {
            return []
        }
    }

    private static func label(for type: HKWorkoutActivityType) -> (name: String, systemImage: String) {
        switch type {
        case .walking: return ("Walking", "figure.walk")
        case .running: return ("Running", "figure.run")
        case .cycling: return ("Cycling", "figure.outdoor.cycle")
        case .swimming: return ("Swimming", "figure.pool.swim")
        case .hiking: return ("Hiking", "figure.hiking")
        case .traditionalStrengthTraining: return ("Strength training", "figure.strengthtraining.traditional")
        case .functionalStrengthTraining: return ("Functional strength", "figure.strengthtraining.functional")
        case .highIntensityIntervalTraining: return ("HIIT", "figure.highintensity.intervaltraining")
        case .coreTraining: return ("Core training", "figure.core.training")
        case .yoga: return ("Yoga", "figure.yoga")
        case .elliptical: return ("Elliptical", "figure.elliptical")
        case .rowing: return ("Rowing", "figure.rower")
        case .stairClimbing: return ("Stair stepper", "figure.stair.stepper")
        case .cardioDance, .socialDance: return ("Dance", "figure.dance")
        default: return ("Workout", "figure.mixed.cardio")
        }
    }

    // MARK: History

    func earliestSampleDate() async throws -> Date? {
        let descriptor = HKSampleQueryDescriptor(
            predicates: [.quantitySample(type: HKQuantityType(.stepCount), predicate: nil)],
            sortDescriptors: [SortDescriptor(\.startDate, order: .forward)],
            limit: 1
        )
        do {
            return try await descriptor.result(for: store).first?.startDate
        } catch where Self.isNoData(error) {
            return nil
        }
    }

    // MARK: Helpers

    private static func isNoData(_ error: Error) -> Bool {
        (error as? HKError)?.code == .errorNoData
    }
}

private extension QuantityMetric {
    var quantityType: HKQuantityType {
        switch self {
        case .steps: HKQuantityType(.stepCount)
        case .activeEnergy: HKQuantityType(.activeEnergyBurned)
        case .distance: HKQuantityType(.distanceWalkingRunning)
        case .heartRate: HKQuantityType(.heartRate)
        case .restingHeartRate: HKQuantityType(.restingHeartRate)
        }
    }

    var unit: HKUnit {
        switch self {
        case .steps: .count()
        case .activeEnergy: .kilocalorie()
        case .distance: .meter()
        case .heartRate, .restingHeartRate: HKUnit.count().unitDivided(by: .minute())
        }
    }
}
