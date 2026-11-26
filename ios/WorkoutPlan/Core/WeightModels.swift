import Foundation

/// One weigh-in. There is at most one per calendar day (spec 6.2).
/// Weight is always stored in kilograms and converted for display.
struct WeighIn: Identifiable, Hashable, Sendable {
    let id: UUID
    /// Start of the calendar day the weigh-in belongs to.
    let day: Date
    let kilograms: Double
    /// When it was last saved.
    let loggedAt: Date
    /// Progress photos, in display order.
    let photoIDs: [UUID]
}

/// A photo in the Log weigh-in sheet: already stored, or new and not yet saved.
/// A new photo's id becomes its stored id when the weigh-in is saved.
enum DraftPhoto: Identifiable, Hashable, Sendable {
    case stored(UUID)
    /// Resized JPEG without metadata, ready to store.
    case new(UUID, Data)

    var id: UUID {
        switch self {
        case .stored(let id), .new(let id, _): id
        }
    }
}

/// What the Log weigh-in sheet saves.
struct WeighInDraft: Sendable {
    var day: Date
    var kilograms: Double
    var photos: [DraftPhoto]
}

/// Which records and photo files a save touches. Pure, so the one-entry-per-day
/// rule can be read and tested apart from storage.
struct WeighInSavePlan: Equatable, Sendable {
    /// The record that ends up holding the weigh-in.
    let entryID: UUID
    /// Other records to delete (an edited entry moved onto a day that
    /// already had one).
    let deleteEntryIDs: [UUID]
    /// Stored photos no longer used by the result.
    let deletePhotoIDs: [UUID]

    /// - Parameters:
    ///   - editing: the entry the sheet was opened for, if any.
    ///   - existingOnDay: the entry already on the chosen day, if any.
    ///   - newID: id to use when neither exists.
    static func make(
        photos: [DraftPhoto],
        editing: WeighIn?,
        existingOnDay: WeighIn?,
        newID: UUID
    ) -> WeighInSavePlan {
        // Saving onto a day that has an entry updates that entry.
        let entryID = existingOnDay?.id ?? editing?.id ?? newID

        var deleteEntryIDs: [UUID] = []
        if let editing, let existingOnDay, editing.id != existingOnDay.id {
            deleteEntryIDs.append(editing.id)
        }

        let kept = Set(photos.compactMap { photo -> UUID? in
            if case .stored(let id) = photo { return id }
            return nil
        })
        var previous: [UUID] = editing?.photoIDs ?? []
        for id in existingOnDay?.photoIDs ?? [] where !previous.contains(id) {
            previous.append(id)
        }
        let deletePhotoIDs = previous.filter { !kept.contains($0) }

        return WeighInSavePlan(entryID: entryID, deleteEntryIDs: deleteEntryIDs, deletePhotoIDs: deletePhotoIDs)
    }
}
