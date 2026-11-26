import Foundation
import SwiftData

/// Food photos, kept on the phone like weigh-ins (spec D8, D11).
protocol FoodStore: PhotoSource {
    /// Photos taken inside `interval`, oldest first.
    func photos(in interval: DateInterval) async throws -> [FoodPhoto]

    /// Stores an already prepared JPEG (resized, no metadata).
    @discardableResult
    func add(jpeg: Data, takenAt: Date) async throws -> FoodPhoto

    func update(_ id: UUID, label: FoodLabel?, note: String) async throws

    func delete(_ id: UUID) async throws

    func photoData(_ id: UUID) async -> Data?

    /// Removes every food photo (account deletion).
    func deleteAll() async throws
}

@Model
final class FoodPhotoRecord {
    @Attribute(.unique) var id: UUID
    var takenAt: Date
    /// `FoodLabel.rawValue`, or nil for no label.
    var labelRaw: String?
    var note: String

    init(id: UUID, takenAt: Date, labelRaw: String?, note: String) {
        self.id = id
        self.takenAt = takenAt
        self.labelRaw = labelRaw
        self.note = note
    }

    var value: FoodPhoto {
        FoodPhoto(id: id, takenAt: takenAt, label: labelRaw.flatMap(FoodLabel.init(rawValue:)), note: note)
    }
}

@ModelActor
actor SwiftDataFoodStore: FoodStore {
    private let files = PhotoFiles(folderName: "FoodPhotos")

    func photos(in interval: DateInterval) async throws -> [FoodPhoto] {
        let start = interval.start
        let end = interval.end
        let descriptor = FetchDescriptor<FoodPhotoRecord>(
            predicate: #Predicate<FoodPhotoRecord> { $0.takenAt >= start && $0.takenAt < end },
            sortBy: [SortDescriptor(\.takenAt)]
        )
        return try modelContext.fetch(descriptor).map(\.value)
    }

    @discardableResult
    func add(jpeg: Data, takenAt: Date) async throws -> FoodPhoto {
        let id = UUID()
        try files.write(jpeg, id: id)
        let record = FoodPhotoRecord(id: id, takenAt: takenAt, labelRaw: nil, note: "")
        modelContext.insert(record)
        do {
            try modelContext.save()
        } catch {
            modelContext.rollback()
            files.delete([id])
            throw error
        }
        return record.value
    }

    func update(_ id: UUID, label: FoodLabel?, note: String) async throws {
        guard let record = try self.record(id) else { return }
        record.labelRaw = label?.rawValue
        record.note = note
        try modelContext.save()
    }

    func delete(_ id: UUID) async throws {
        guard let record = try self.record(id) else { return }
        modelContext.delete(record)
        try modelContext.save()
        files.delete([id])
    }

    func photoData(_ id: UUID) async -> Data? {
        files.read(id)
    }

    func deleteAll() async throws {
        let records = try modelContext.fetch(FetchDescriptor<FoodPhotoRecord>())
        let ids = records.map(\.id)
        for record in records {
            modelContext.delete(record)
        }
        try modelContext.save()
        files.delete(ids)
    }

    private func record(_ id: UUID) throws -> FoodPhotoRecord? {
        let descriptor = FetchDescriptor<FoodPhotoRecord>(predicate: #Predicate<FoodPhotoRecord> { $0.id == id })
        return try modelContext.fetch(descriptor).first
    }
}

/// Food store kept in memory, for previews and as a fallback.
actor InMemoryFoodStore: FoodStore {
    private var items: [FoodPhoto] = []
    private var data: [UUID: Data] = [:]

    func photos(in interval: DateInterval) async throws -> [FoodPhoto] {
        items.filter { $0.takenAt >= interval.start && $0.takenAt < interval.end }.sorted { $0.takenAt < $1.takenAt }
    }

    @discardableResult
    func add(jpeg: Data, takenAt: Date) async throws -> FoodPhoto {
        let photo = FoodPhoto(id: UUID(), takenAt: takenAt, label: nil, note: "")
        items.append(photo)
        data[photo.id] = jpeg
        return photo
    }

    func update(_ id: UUID, label: FoodLabel?, note: String) async throws {
        guard let index = items.firstIndex(where: { $0.id == id }) else { return }
        let old = items[index]
        items[index] = FoodPhoto(id: old.id, takenAt: old.takenAt, label: label, note: note)
    }

    func delete(_ id: UUID) async throws {
        items.removeAll { $0.id == id }
        data[id] = nil
    }

    func photoData(_ id: UUID) async -> Data? { data[id] }

    func deleteAll() async throws {
        items = []
        data = [:]
    }
}
