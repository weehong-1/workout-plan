import Foundation
import Observation
import PhotosUI
import SwiftUI
import UIKit

/// State of the Log weigh-in sheet (spec 6.2, design `WeighIn.dc.html`).
@MainActor
@Observable
final class LogWeighInModel {
    /// The entry being edited; nil when logging a new weigh-in.
    let editing: WeighIn?

    var weightText: String
    var day: Date
    private(set) var photos: [DraftPhoto]
    private(set) var isSaving = false
    private(set) var isAddingPhotos = false
    var errorMessage: String?

    /// Other weigh-ins, newest first, for "Last:" and the one-per-day rule.
    private let entries: [WeighIn]
    private let store: any WeightStore
    private let calendar: Calendar

    init(editing: WeighIn?, entries: [WeighIn], store: any WeightStore, unit: WeightUnit, calendar: Calendar = .current) {
        self.editing = editing
        self.entries = entries
        self.store = store
        self.calendar = calendar
        if let editing {
            day = editing.day
            weightText = WeightMath.inputText(kilograms: editing.kilograms, unit: unit)
            photos = editing.photoIDs.map { .stored($0) }
        } else {
            day = calendar.startOfDay(for: Date())
            weightText = ""
            photos = []
        }
        if editing == nil {
            // Logging on a day that already has an entry edits it: show its photos.
            dayChanged(unit: unit)
        }
    }

    var isEditing: Bool { editing != nil }

    /// Most recent weigh-in other than the one being edited.
    var lastEntry: WeighIn? {
        entries.first { $0.id != editing?.id }
    }

    /// The weigh-in already on the chosen day, if it is not the one being edited.
    var otherEntryOnDay: WeighIn? {
        entries.first { calendar.isDate($0.day, inSameDayAs: day) && $0.id != editing?.id }
    }

    var dayNote: String {
        if otherEntryOnDay != nil {
            return isEditing
                ? "This date already has a weigh-in. Saving replaces it."
                : "This date already has a weigh-in. Saving updates it."
        }
        return "One entry per day. If this date already has one, saving updates it."
    }

    var canAddPhotos: Bool { photos.count < WeightMath.maxPhotos && !isAddingPhotos }
    var remainingPhotoSlots: Int { max(0, WeightMath.maxPhotos - photos.count) }

    func kilograms(unit: WeightUnit) -> Double? {
        WeightMath.parse(weightText, unit: unit)
    }

    func canSave(unit: WeightUnit) -> Bool {
        kilograms(unit: unit) != nil && !isSaving && !isAddingPhotos
    }

    /// Shown under the field when the text is not a valid weight.
    func validationMessage(unit: WeightUnit) -> String? {
        guard !weightText.trimmingCharacters(in: .whitespaces).isEmpty, kilograms(unit: unit) == nil else { return nil }
        let low = WeightFormat.value(WeightMath.validKilograms.lowerBound, unit: unit)
        let high = WeightFormat.value(WeightMath.validKilograms.upperBound, unit: unit)
        return "Enter a weight between \(low) and \(high) \(unit.rawValue)."
    }

    // MARK: Changes

    /// When logging, picking a day that has an entry loads that entry's photos
    /// (and its weight if nothing is typed yet), since saving edits it.
    func dayChanged(unit: WeightUnit) {
        guard editing == nil else { return }
        let newPhotos = photos.filter { if case .new = $0 { return true } else { return false } }
        if let existing = otherEntryOnDay {
            photos = existing.photoIDs.map { DraftPhoto.stored($0) } + newPhotos
            if weightText.isEmpty {
                weightText = WeightMath.inputText(kilograms: existing.kilograms, unit: unit)
            }
        } else {
            photos = newPhotos
        }
    }

    /// Converts the typed value when kg / lb is switched.
    func unitChanged(from oldUnit: WeightUnit, to newUnit: WeightUnit) {
        guard let kilograms = WeightMath.parse(weightText, unit: oldUnit) else { return }
        weightText = WeightMath.inputText(kilograms: kilograms, unit: newUnit)
    }

    func removePhoto(_ id: UUID) {
        photos.removeAll { $0.id == id }
    }

    func addLibraryItems(_ items: [PhotosPickerItem]) async {
        isAddingPhotos = true
        defer { isAddingPhotos = false }
        for item in items {
            guard photos.count < WeightMath.maxPhotos else { break }
            do {
                guard let data = try await item.loadTransferable(type: Data.self) else { continue }
                await addImageData(data)
            } catch {
                errorMessage = "Couldn't load a photo from the library."
            }
        }
    }

    func addCameraImage(_ image: UIImage) async {
        guard let data = image.jpegData(compressionQuality: 0.95) else { return }
        isAddingPhotos = true
        defer { isAddingPhotos = false }
        await addImageData(data)
    }

    private func addImageData(_ data: Data) async {
        guard photos.count < WeightMath.maxPhotos else { return }
        let prepared = await Task.detached(priority: .userInitiated) {
            PhotoProcessing.prepareForStorage(data)
        }.value
        if let prepared {
            photos.append(.new(UUID(), prepared))
        } else {
            errorMessage = "Couldn't read that photo."
        }
    }

    // MARK: Save and delete

    func save(unit: WeightUnit) async -> Bool {
        guard let kilograms = kilograms(unit: unit) else { return false }
        isSaving = true
        defer { isSaving = false }
        do {
            let draft = WeighInDraft(day: day, kilograms: kilograms, photos: photos)
            try await store.save(draft, editingID: editing?.id)
            return true
        } catch {
            errorMessage = "Couldn't save: \(error.localizedDescription)"
            return false
        }
    }

    func delete() async -> Bool {
        guard let editing else { return false }
        isSaving = true
        defer { isSaving = false }
        do {
            try await store.delete(editing.id)
            return true
        } catch {
            errorMessage = "Couldn't delete: \(error.localizedDescription)"
            return false
        }
    }
}
