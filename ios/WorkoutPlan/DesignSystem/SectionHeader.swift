import SwiftUI

/// Section title in Title 2, with optional trailing detail text.
struct SectionHeader: View {
    let title: String
    var detail: String?

    var body: some View {
        HStack(alignment: .firstTextBaseline) {
            Text(title)
                .font(AppFont.title2)
                .foregroundStyle(Color.appLabel)
                .accessibilityAddTraits(.isHeader)
            Spacer(minLength: Spacing.xs)
            if let detail {
                Text(detail)
                    .font(AppFont.subheadline)
                    .foregroundStyle(Color.appSecondaryLabel)
            }
        }
        .padding(.horizontal, Spacing.xxs)
    }
}

#Preview {
    SectionHeader(title: "Highlights")
        .padding()
}
