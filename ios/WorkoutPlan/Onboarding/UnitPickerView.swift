import SwiftUI

/// kg or lb, saved in `@AppStorage` (spec 5.4).
struct UnitPickerView: View {
    let onDone: () -> Void

    @AppStorage(StorageKey.weightUnit) private var unit: WeightUnit = .kg

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.xl) {
            Image(systemName: "scalemass.fill")
                .font(.system(size: 56))
                .foregroundStyle(MetricKind.weight.graphic)
                .accessibilityHidden(true)
                .padding(.top, Spacing.xxl)

            VStack(alignment: .leading, spacing: Spacing.xs) {
                Text("Weight units")
                    .font(AppFont.largeTitle)
                    .foregroundStyle(Color.appLabel)
                    .accessibilityAddTraits(.isHeader)
                Text("How you log and see your body weight. You can change it later in Account.")
                    .font(AppFont.body)
                    .foregroundStyle(Color.appSecondaryLabel)
            }

            Picker("Weight units", selection: $unit) {
                ForEach(WeightUnit.allCases) { unit in
                    Text(unit.title).tag(unit)
                }
            }
            .pickerStyle(.segmented)

            Spacer(minLength: 0)

            Button(action: onDone) {
                Text("Done")
                    .font(AppFont.headline)
                    .frame(maxWidth: .infinity, minHeight: TouchTarget.minimum)
            }
            .buttonStyle(.borderedProminent)
        }
        .padding(.horizontal, Spacing.m)
        .padding(.bottom, Spacing.m)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(Color.appBackground)
    }
}

#Preview {
    UnitPickerView(onDone: {})
}
