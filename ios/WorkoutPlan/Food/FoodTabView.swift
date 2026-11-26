import PhotosUI
import SwiftUI

/// Food tab (spec 6.3, design `Food.dc.html`): date strip, label filter and
/// the day's photos as one grid.
struct FoodTabView: View {
    @State private var model: FoodViewModel
    @State private var showsCamera = false
    @State private var pickerItems: [PhotosPickerItem] = []
    @State private var showsLibrary = false

    init(store: any FoodStore) {
        _model = State(initialValue: FoodViewModel(store: store))
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.m) {
                    DateStrip(days: model.stripDays, selected: $model.selectedDay, marked: model.daysWithPhotos)
                    dayHeader
                    LabelChips(selection: $model.filter)
                    content
                    if let message = model.message {
                        Text(message)
                            .font(AppFont.footnote)
                            .foregroundStyle(Color.appSecondaryLabel)
                    }
                    Text("All photos make up the day's intake. A label is optional; tap a photo to add or change one.")
                        .font(AppFont.footnote)
                        .foregroundStyle(Color.appSecondaryLabel)
                }
                .padding(.horizontal, Spacing.m)
                .padding(.bottom, Spacing.xl)
            }
            .background(Color.appBackground)
            .navigationTitle("Food")
            .navigationBarTitleDisplayMode(.large)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Add food photo", systemImage: "plus") {
                        // + opens the camera straight away; without a camera
                        // (simulator), the library instead.
                        if CameraPicker.isAvailable {
                            showsCamera = true
                        } else {
                            showsLibrary = true
                        }
                    }
                    .disabled(model.isAdding)
                }
            }
            .accountToolbar()
            .task { await model.load() }
            .refreshable { await model.load() }
            .photosPicker(isPresented: $showsLibrary, selection: $pickerItems, maxSelectionCount: 20, matching: .images)
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
            .navigationDestination(for: FoodPhoto.self) { photo in
                FoodPhotoDetailView(photo: photo, store: model.store) {
                    Task { await model.load() }
                }
            }
        }
    }

    private var dayHeader: some View {
        HStack(alignment: .firstTextBaseline) {
            Text(model.selectedDay.formatted(.dateTime.weekday(.wide).day().month(.abbreviated)))
                .font(AppFont.title2)
                .foregroundStyle(Color.appLabel)
                .accessibilityAddTraits(.isHeader)
            Spacer(minLength: Spacing.xs)
            Text(model.dayCount == 1 ? "1 photo" : "\(model.dayCount) photos")
                .font(AppFont.subheadline)
                .foregroundStyle(Color.appSecondaryLabel)
            // The library is one tap away (spec 6.3).
            Button {
                showsLibrary = true
            } label: {
                Label("Library", systemImage: "photo.on.rectangle")
                    .labelStyle(.iconOnly)
                    .frame(width: TouchTarget.minimum, height: TouchTarget.minimum)
            }
            .disabled(model.isAdding)
            .accessibilityLabel("Add from photo library")
        }
    }

    @ViewBuilder
    private var content: some View {
        if model.isAdding {
            ProgressView("Adding photo…")
                .frame(maxWidth: .infinity)
        }
        if !model.isLoaded {
            ProgressView().frame(maxWidth: .infinity, minHeight: 200)
        } else if model.dayPhotos.isEmpty {
            ContentUnavailableView {
                Label(model.filter == nil ? "No food photos" : "No \(model.filter!.title.lowercased()) photos", systemImage: "fork.knife")
            } description: {
                Text("Take a photo of each meal or snack. Together they make up the day.")
            } actions: {
                Button("Add Photo") {
                    if CameraPicker.isAvailable { showsCamera = true } else { showsLibrary = true }
                }
                .buttonStyle(.borderedProminent)
            }
            .frame(maxWidth: .infinity, minHeight: 240)
        } else {
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: Spacing.xs), count: 3), spacing: Spacing.xs) {
                ForEach(model.dayPhotos) { photo in
                    NavigationLink(value: photo) {
                        FoodTile(photo: photo, store: model.store)
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }
}

/// Horizontal strip of days; a dot marks days with photos.
private struct DateStrip: View {
    let days: [Date]
    @Binding var selected: Date
    let marked: Set<Date>

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: Spacing.xs) {
                    ForEach(days, id: \.self) { day in
                        let isSelected = Calendar.current.isDate(day, inSameDayAs: selected)
                        Button {
                            selected = day
                        } label: {
                            VStack(spacing: 2) {
                                Text(day.formatted(.dateTime.weekday(.abbreviated)))
                                    .font(AppFont.caption)
                                Text(day.formatted(.dateTime.day()))
                                    .font(AppFont.headline)
                                    .numberDesign()
                                Circle()
                                    .fill(marked.contains(day) ? MetricKind.food.graphic : .clear)
                                    .frame(width: 5, height: 5)
                            }
                            .foregroundStyle(isSelected ? Color.white : Color.appLabel)
                            .frame(width: 44, height: 64)
                            .background(isSelected ? Color.appTint : Color.appCard, in: RoundedRectangle(cornerRadius: Radius.control + 4, style: .continuous))
                        }
                        .buttonStyle(.plain)
                        .id(day)
                        .accessibilityLabel(day.formatted(.dateTime.weekday(.wide).day().month(.wide)))
                        .accessibilityValue(marked.contains(day) ? "Has photos" : "")
                        .accessibilityAddTraits(isSelected ? .isSelected : [])
                    }
                }
            }
            .onAppear { proxy.scrollTo(days.last, anchor: .trailing) }
        }
    }
}

/// All + one chip per label.
private struct LabelChips: View {
    @Binding var selection: FoodLabel?

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: Spacing.xs) {
                chip(title: "All", value: nil)
                ForEach(FoodLabel.allCases) { label in
                    chip(title: label.title, value: label)
                }
            }
        }
    }

    private func chip(title: String, value: FoodLabel?) -> some View {
        let isOn = selection == value
        return Button {
            selection = value
        } label: {
            Text(title)
                .font(AppFont.subheadline.weight(.semibold))
                .padding(.horizontal, 14)
                .frame(minHeight: 36)
                .foregroundStyle(isOn ? Color.white : Color.appLabel)
                .background(isOn ? Color.appTint : Color.appFill, in: Capsule())
                .frame(minHeight: TouchTarget.minimum)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isOn ? .isSelected : [])
    }
}

/// Square photo with its time and label.
private struct FoodTile: View {
    let photo: FoodPhoto
    let store: any FoodStore

    var body: some View {
        Color.clear
            .aspectRatio(1, contentMode: .fit)
            .overlay {
                PhotoThumbnail(photo: .stored(photo.id), store: store, maxPixelSize: 360)
            }
            .overlay(alignment: .bottomLeading) {
                VStack(alignment: .leading, spacing: 2) {
                    if let label = photo.label {
                        Text(label.title)
                            .font(AppFont.caption.weight(.semibold))
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(MetricKind.food.graphic, in: Capsule())
                            .foregroundStyle(Color.black)
                    }
                    Text(Format.time(photo.takenAt))
                        .font(AppFont.caption.weight(.semibold))
                        .numberDesign()
                        .foregroundStyle(Color.white)
                        .shadow(color: .black.opacity(0.6), radius: 2)
                }
                .padding(6)
            }
            .clipShape(RoundedRectangle(cornerRadius: Radius.tile, style: .continuous))
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Food photo at \(Format.time(photo.takenAt))\(photo.label.map { ", \($0.title)" } ?? "")")
            .accessibilityHint("Opens the photo")
    }
}

#Preview {
    FoodTabView(store: InMemoryFoodStore())
        .environment(AppServices.preview)
}
