import SwiftUI
import UIKit

/// One food photo, large, with its label, note and delete (spec 6.3).
struct FoodPhotoDetailView: View {
    let photo: FoodPhoto
    let store: any FoodStore
    let onChange: () -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var image: UIImage?
    @State private var label: FoodLabel?
    @State private var note: String
    @State private var confirmsDelete = false
    @State private var errorMessage: String?

    init(photo: FoodPhoto, store: any FoodStore, onChange: @escaping () -> Void) {
        self.photo = photo
        self.store = store
        self.onChange = onChange
        _label = State(initialValue: photo.label)
        _note = State(initialValue: photo.note)
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.m) {
                Group {
                    if let image {
                        Image(uiImage: image)
                            .resizable()
                            .scaledToFit()
                    } else {
                        Color.appFill
                            .aspectRatio(3 / 4, contentMode: .fit)
                            .overlay { ProgressView() }
                    }
                }
                .clipShape(RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
                .accessibilityLabel("Food photo")

                VStack(alignment: .leading, spacing: Spacing.xs) {
                    Text("Label")
                        .font(AppFont.footnote.weight(.semibold))
                        .foregroundStyle(MetricKind.food.text)
                    Picker("Label", selection: $label) {
                        Text("None").tag(FoodLabel?.none)
                        ForEach(FoodLabel.allCases) { item in
                            Text(item.title).tag(FoodLabel?.some(item))
                        }
                    }
                    .pickerStyle(.segmented)
                }
                .cardPadded()

                VStack(alignment: .leading, spacing: Spacing.xs) {
                    Text("Note")
                        .font(AppFont.footnote.weight(.semibold))
                        .foregroundStyle(MetricKind.food.text)
                    TextField("Optional", text: $note, axis: .vertical)
                        .lineLimit(1...5)
                        .font(AppFont.body)
                }
                .cardPadded()

                if let errorMessage {
                    Text(errorMessage).font(AppFont.footnote).foregroundStyle(MetricKind.heart.text)
                }

                Button(role: .destructive) {
                    confirmsDelete = true
                } label: {
                    Text("Delete Photo")
                        .frame(maxWidth: .infinity, minHeight: TouchTarget.minimum)
                }
                .background(Color.appCard, in: RoundedRectangle(cornerRadius: Radius.tile, style: .continuous))
            }
            .padding(.horizontal, Spacing.m)
            .padding(.bottom, Spacing.xl)
        }
        .background(Color.appBackground)
        .navigationTitle(photo.takenAt.formatted(.dateTime.weekday(.abbreviated).day().month(.abbreviated).hour().minute()))
        .navigationBarTitleDisplayMode(.inline)
        .task {
            guard let data = await store.photoData(photo.id) else { return }
            image = await Task.detached(priority: .userInitiated) {
                PhotoProcessing.thumbnail(data, maxPixelSize: 2_048)
            }.value
        }
        .onChange(of: label) { save() }
        .onDisappear { if note != photo.note { save() } }
        .confirmationDialog("Delete this photo?", isPresented: $confirmsDelete, titleVisibility: .visible) {
            Button("Delete Photo", role: .destructive) {
                Task {
                    do {
                        try await store.delete(photo.id)
                        onChange()
                        dismiss()
                    } catch {
                        errorMessage = "Couldn't delete: \(error.localizedDescription)"
                    }
                }
            }
        } message: {
            Text("This can't be undone.")
        }
    }

    private func save() {
        let label = label
        let note = note
        Task {
            do {
                try await store.update(photo.id, label: label, note: note)
                onChange()
            } catch {
                errorMessage = "Couldn't save: \(error.localizedDescription)"
            }
        }
    }
}
