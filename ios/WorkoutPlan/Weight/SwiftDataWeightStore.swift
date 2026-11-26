import Foundation
import SwiftData

/// SwiftData row for a weigh-in. Photos live as files (`PhotoFiles`); the
/// row keeps their ids in order.
@Model
final class WeighInRecord {
    @Attribute(.unique) var id: UUID
    /// Start of the calendar day.
    var day: Date
    var kilograms: Double
    var loggedAt: Date
    var photoIDs: [UUID]

    init(id: UUID, day: Date, kilograms: Double, loggedAt: Date, photoIDs: [UUID]) {
        self.id = id
        self.day = day
        self.kilograms = kilograms
        self.loggedAt = loggedAt
        self.photoIDs = photoIDs
    }

    var value: WeighIn {
        WeighIn(id: id, day: day, kilograms: kilograms, loggedAt: loggedAt, photoIDs: photoIDs)
    }
}

/// On-phone weight store. A model actor, so SwiftData and file work run off
/// the main actor.
@ModelActor
actor SwiftDataWeightStore: WeightStore {
    private let photoFiles = PhotoFiles()
    private let calendar = Calendar.current

    func entries() async throws -> [WeighIn] {
        let descriptor = FetchDescriptor<WeighInRecord>(sortBy: [SortDescriptor(\.day, order: .reverse)])
        return try modelContext.fetch(descriptor).map(\.value)
    }

    @discardableResult
    func save(_ draft: WeighInDraft, editingID: UUID?) async throws -> WeighIn {
        let day = calendar.startOfDay(for: draft.day)
        // A personal log has a few thousand rows at most; fetching all keeps
        // the matching simple and avoids predicate edge cases.
        let records = try modelContext.fetch(FetchDescriptor<WeighInRecord>())
        let existingOnDay = records.first { calendar.isDate($0.day, inSameDayAs: day) }
        let editing = editingID.flatMap { id in records.first { $0.id == id } }

        let plan = WeighInSavePlan.make(
            photos: draft.photos,
            editing: editing?.value,
            existingOnDay: existingOnDay?.value,
            newID: UUID()
        )

        // Write new photos first, so a failed write leaves the entry unchanged.
        var written: [UUID] = []
        do {
            for case .new(let id, let data) in draft.photos {
                try photoFiles.write(data, id: id)
                written.append(id)
            }
        } catch {
            photoFiles.delete(written)
            throw error
        }

        for record in records where plan.deleteEntryIDs.contains(record.id) {
            modelContext.delete(record)
        }

        let record: WeighInRecord
        if let found = records.first(where: { $0.id == plan.entryID }) {
            record = found
        } else {
            record = WeighInRecord(id: plan.entryID, day: day, kilograms: draft.kilograms, loggedAt: Date(), photoIDs: [])
            modelContext.insert(record)
        }
        record.day = day
        record.kilograms = draft.kilograms
        record.loggedAt = Date()
        record.photoIDs = draft.photos.map(\.id)

        do {
            try modelContext.save()
        } catch {
            modelContext.rollback()
            photoFiles.delete(written)
            throw error
        }
        photoFiles.delete(plan.deletePhotoIDs)
        return record.value
    }

    func delete(_ id: UUID) async throws {
        let records = try modelContext.fetch(FetchDescriptor<WeighInRecord>())
        guard let record = records.first(where: { $0.id == id }) else { return }
        let photoIDs = record.photoIDs
        modelContext.delete(record)
        try modelContext.save()
        photoFiles.delete(photoIDs)
    }

    func photoData(_ id: UUID) async -> Data? {
        photoFiles.read(id)
    }
}
