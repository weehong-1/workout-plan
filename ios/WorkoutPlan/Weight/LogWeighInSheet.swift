import PhotosUI
import SwiftUI

/// Log weigh-in sheet: weight, date and optional progress photos.
/// Opened empty from + or filled from a history row to edit or delete.
struct LogWeighInSheet: View {
    let store: any WeightStore

    @State private var model: LogWeighInModel
    @AppStorage(StorageKey.weightUnit) private var unit: WeightUnit = .kg
    @Environment(\.dismiss) private var dismiss
    @FocusState private var weightFocused: Bool
    @State private var pickerItems: [PhotosPickerItem] = []
    @State private var showsCamera = false
    @State private var confirmsDelete = false

    init(editing: WeighIn?, entries: [WeighIn], store: any WeightStore) {
        self.store = store
        let savedUnit = UserDefaults.standard.string(forKey: StorageKey.weightUnit).flatMap(WeightUnit.init(rawValue:)) ?? .kg
        _model = State(initialValue: LogWeighInModel(editing: editing, entries: entries, store: store, unit: savedUnit))
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.m) {
                    weightCard
                    dateCard
                    Text(model.dayNote)
                        .font(AppFont.footnote)
                        .foregroundStyle(Color.appSecondaryLabel)
                        .padding(.horizontal, Spacing.xxs)
                    photosSection
                    if let message = model.errorMessage {
                        Text(message)
                            .font(AppFont.footnote)
                            .foregroundStyle(MetricKind.heart.text)
                    }
                    if model.isEditing {
                        deleteButton
                    }
                }
                .padding(Spacing.m)
            }
            .background(Color.appBackground)
            .scrollDismissesKeyboard(.interactively)
            .navigationTitle(model.isEditing ? "Edit weigh-in" : "Log weigh-in")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel", systemImage: "xmark") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save", systemImage: "checkmark") {
                        Task {
                            if await model.save(unit: unit) { dismiss() }
                        }
                    }
                    .disabled(!model.canSave(unit: unit))
                }
                ToolbarItemGroup(placement: .keyboard) {
                    Spacer()
                    Button("Done") { weightFocused = false }
                }
            }
            .onChange(of: model.day) {
                model.dayChanged(unit: unit)
            }
            .onChange(of: unit) { oldUnit, newUnit in
                model.unitChanged(from: oldUnit, to: newUnit)
            }
            .onChange(of: pickerItems) { _, items in
                guard !items.isEmpty else { return }
                Task {
                    await model.addLibraryItems(items)
                    pickerItems = []
                }
            }
            .fullScreenCover(isPresented: $showsCamera) {
                CameraPicker { image in
                    Task { await model.addCameraImage(image) }
                }
                .ignoresSafeArea()
            }
            .confirmationDialog("Delete this weigh-in?", isPresented: $confirmsDelete, titleVisibility: .visible) {
                Button("Delete Weigh-in", role: .destructive) {
                    Task {
                        if await model.delete() { dismiss() }
                    }
                }
            } message: {
                Text("Its photos are deleted too. This can't be undone.")
            }
            .onAppear {
                if !model.isEditing { weightFocused = true }
            }
        }
        .interactiveDismissDisabled(model.isSaving)
    }

    // MARK: Sections

    private var weightCard: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            Text("Weight")
                .font(AppFont.footnote.weight(.semibold))
                .foregroundStyle(MetricKind.weight.text)

            HStack(alignment: .firstTextBaseline, spacing: Spacing.xs) {
                TextField("0.0", text: $model.weightText)
                    .keyboardType(.decimalPad)
                    .metricFont(.xl)
                    .foregroundStyle(Color.appLabel)
                    .focused($weightFocused)
                    .accessibilityLabel("Weight in \(unit.rawValue)")
                Text(unit.rawValue)
                    .font(AppFont.title2)
                    .foregroundStyle(Color.appSecondaryLabel)
            }

            if let message = model.validationMessage(unit: unit) {
                Text(message)
                    .font(AppFont.footnote)
                    .foregroundStyle(MetricKind.heart.text)
            } else if let last = model.lastEntry {
                Text("Last: \(WeightFormat.value(last.kilograms, unit: unit)) \(unit.rawValue) on \(WeightFormat.day(last.day))")
                    .font(AppFont.footnote)
                    .foregroundStyle(Color.appSecondaryLabel)
            }

            Picker("Unit", selection: $unit) {
                ForEach(WeightUnit.allCases) { unit in
                    Text(unit.title).tag(unit)
                }
            }
            .pickerStyle(.segmented)
            .padding(.top, Spacing.xxs)
        }
        .padding(Spacing.m)
        .background(Color.appCard, in: RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
    }

    private var dateCard: some View {
        DatePicker("Date", selection: $model.day, in: ...Date(), displayedComponents: .date)
            .font(AppFont.body)
            .padding(.horizontal, Spacing.m)
            .padding(.vertical, Spacing.xs)
            .frame(minHeight: TouchTarget.minimum)
            .background(Color.appCard, in: RoundedRectangle(cornerRadius: Radius.tile, style: .continuous))
    }

    private var photosSection: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            Text("PROGRESS PHOTOS · OPTIONAL")
                .font(AppFont.footnote.weight(.semibold))
                .foregroundStyle(Color.appSecondaryLabel)
                .padding(.horizontal, Spacing.xxs)
                .padding(.top, Spacing.xs)

            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: Spacing.s), count: 3), spacing: Spacing.s) {
                ForEach(model.photos) { photo in
                    photoTile(photo)
                }
                if model.canAddPhotos {
                    if CameraPicker.isAvailable {
                        Button {
                            showsCamera = true
                        } label: {
                            AddPhotoTile(title: "Camera", systemImage: "camera")
                        }
                        .buttonStyle(.plain)
                    }
                    PhotosPicker(
                        selection: $pickerItems,
                        maxSelectionCount: model.remainingPhotoSlots,
                        matching: .images
                    ) {
                        AddPhotoTile(title: "Library", systemImage: "photo.on.rectangle")
                    }
                    .buttonStyle(.plain)
                }
            }

            if model.isAddingPhotos {
                ProgressView("Preparing photo…")
                    .font(AppFont.footnote)
            }

            Text("Up to \(WeightMath.maxPhotos) photos. Photos are resized and location data is removed.")
                .font(AppFont.footnote)
                .foregroundStyle(Color.appSecondaryLabel)
                .padding(.horizontal, Spacing.xxs)
        }
    }

    private func photoTile(_ photo: DraftPhoto) -> some View {
        Color.clear
            .aspectRatio(1, contentMode: .fit)
            .overlay {
                PhotoThumbnail(photo: photo, store: store)
            }
            .clipShape(RoundedRectangle(cornerRadius: Radius.tile, style: .continuous))
            .overlay(alignment: .topTrailing) {
                Button {
                    model.removePhoto(photo.id)
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .font(.title3)
                        .symbolRenderingMode(.palette)
                        .foregroundStyle(.white, .black.opacity(0.6))
                        .frame(width: TouchTarget.minimum, height: TouchTarget.minimum)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Remove photo")
            }
    }

    private var deleteButton: some View {
        Button(role: .destructive) {
            confirmsDelete = true
        } label: {
            Text("Delete Weigh-in")
                .font(AppFont.body)
                .frame(maxWidth: .infinity, minHeight: TouchTarget.minimum)
        }
        .background(Color.appCard, in: RoundedRectangle(cornerRadius: Radius.tile, style: .continuous))
        .padding(.top, Spacing.s)
        .disabled(model.isSaving)
    }
}

/// Dashed square with an icon and a title, for adding a photo.
private struct AddPhotoTile: View {
    let title: String
    let systemImage: String

    var body: some View {
        VStack(spacing: Spacing.xxs) {
            Image(systemName: systemImage)
                .font(AppFont.title2)
            Text(title)
                .font(AppFont.footnote.weight(.semibold))
        }
        .foregroundStyle(Color.appTint)
        .frame(maxWidth: .infinity)
        .aspectRatio(1, contentMode: .fit)
        .overlay {
            RoundedRectangle(cornerRadius: Radius.tile, style: .continuous)
                .strokeBorder(Color.appSecondaryLabel.opacity(0.5), style: StrokeStyle(lineWidth: 1.5, dash: [5, 4]))
        }
        .contentShape(Rectangle())
    }
}

#Preview {
    LogWeighInSheet(editing: nil, entries: [], store: InMemoryWeightStore())
}
