import SwiftUI

/// One activity ring. `progress` runs from 0 upwards; the ring closes at 1.0.
/// Past 1.0 a second lap is drawn over the closed ring with a soft shadow at
/// its end, as in the Fitness app.
struct ActivityRing: View {
    let progress: Double
    let color: Color
    let lineWidth: CGFloat

    private var clamped: Double { max(0, progress) }

    var body: some View {
        ZStack {
            Circle()
                .stroke(color.opacity(0.22), lineWidth: lineWidth)

            Circle()
                .trim(from: 0, to: min(clamped, 1))
                .stroke(color, style: StrokeStyle(lineWidth: lineWidth, lineCap: .round))
                .rotationEffect(.degrees(-90))

            if clamped > 1 {
                Circle()
                    .trim(from: 0, to: min(clamped - 1, 1))
                    .stroke(color, style: StrokeStyle(lineWidth: lineWidth, lineCap: .round))
                    .rotationEffect(.degrees(-90))
                    .shadow(color: .black.opacity(0.35), radius: lineWidth / 4)
            }
        }
        // Keep the stroke inside the frame.
        .padding(lineWidth / 2)
    }
}

/// Three concentric rings: Move (outer), Exercise, Stand (inner).
struct ActivityRings: View {
    let move: Double
    let exercise: Double
    let stand: Double
    var lineWidth: CGFloat = 16
    var gap: CGFloat = 2

    var body: some View {
        ZStack {
            ActivityRing(progress: move, color: MetricKind.move.graphic, lineWidth: lineWidth)
            ActivityRing(progress: exercise, color: MetricKind.exercise.graphic, lineWidth: lineWidth)
                .padding(lineWidth + gap)
            ActivityRing(progress: stand, color: MetricKind.stand.graphic, lineWidth: lineWidth)
                .padding(2 * (lineWidth + gap))
        }
        .aspectRatio(1, contentMode: .fit)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Activity rings")
        .accessibilityValue(
            "Move \(Self.percent(move)), Exercise \(Self.percent(exercise)), Stand \(Self.percent(stand))"
        )
    }

    private static func percent(_ value: Double) -> String {
        max(0, value).formatted(.percent.precision(.fractionLength(0)))
    }
}

#Preview("Rings") {
    VStack(spacing: 24) {
        ActivityRings(move: 0.61, exercise: 1.0, stand: 0.75)
            .frame(width: 150, height: 150)
        ActivityRings(move: 1.4, exercise: 0.2, stand: 0, lineWidth: 3.5, gap: 1)
            .frame(width: 32, height: 32)
    }
    .padding()
}
