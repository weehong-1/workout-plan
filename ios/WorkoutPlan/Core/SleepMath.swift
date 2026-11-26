import Foundation

enum SleepMath {
    /// Display order of the stage bar.
    static let stageOrder: [SleepStage] = [.awake, .rem, .core, .deep, .unspecified]

    /// The samples used for a night. When any sample has a stage (REM, core,
    /// deep), only staged and awake samples are used, so an unstaged copy of
    /// the same night from another source is not counted twice. "In bed"
    /// samples never reach here.
    static func displaySegments(_ segments: [SleepSegment]) -> [SleepSegment] {
        let staged: Set<SleepStage> = [.rem, .core, .deep]
        let hasStages = segments.contains { staged.contains($0.stage) }
        return hasStages ? segments.filter { $0.stage != .unspecified } : segments
    }

    /// Summarises the sleep samples of one night.
    static func summarize(_ segments: [SleepSegment]) -> SleepNight? {
        let used = displaySegments(segments)

        let asleep = used.filter { $0.stage.isAsleep }.reduce(0) { $0 + $1.duration }
        guard asleep > 0,
              let start = used.map(\.start).min(),
              let end = used.map(\.end).max()
        else { return nil }

        let stages = stageOrder.compactMap { stage -> StageTotal? in
            let total = used.filter { $0.stage == stage }.reduce(0) { $0 + $1.duration }
            return total > 0 ? StageTotal(stage: stage, duration: total) : nil
        }
        return SleepNight(start: start, end: end, asleep: asleep, stages: stages)
    }
}
