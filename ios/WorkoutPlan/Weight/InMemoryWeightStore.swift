import Foundation

/// Weight store kept in memory: previews, and the fallback when the on-disk
/// store cannot be opened.
actor InMemoryWeightStore: WeightStore {
    private var records: [WeighIn]
    private var photos: [UUID: Data] = [:]
    private let calendar = Calendar.current

    init(entries: [WeighIn] = []) {
        self.records = entries
    }

    func entries() async throws -> [WeighIn] {
        records.sorted { $0.day > $1.day }
    }

    @discardableResult
    func save(_ draft: WeighInDraft, editingID: UUID?) async throws -> WeighIn {
        let day = calendar.startOfDay(for: draft.day)
        let existingOnDay = records.first { calendar.isDate($0.day, inSameDayAs: day) }
        let editing = editingID.flatMap { id in records.first { $0.id == id } }
        let plan = WeighInSavePlan.make(photos: draft.photos, editing: editing, existingOnDay: existingOnDay, newID: UUID())

        for case .new(let id, let data) in draft.photos {
            photos[id] = data
        }
        records.removeAll { plan.deleteEntryIDs.contains($0.id) || $0.id == plan.entryID }
        let entry = WeighIn(
            id: plan.entryID,
            day: day,
            kilograms: draft.kilograms,
            loggedAt: Date(),
            photoIDs: draft.photos.map(\.id)
        )
        records.append(entry)
        for id in plan.deletePhotoIDs {
            photos[id] = nil
        }
        return entry
    }

    func delete(_ id: UUID) async throws {
        guard let entry = records.first(where: { $0.id == id }) else { return }
        for photoID in entry.photoIDs {
            photos[photoID] = nil
        }
        records.removeAll { $0.id == id }
    }

    func photoData(_ id: UUID) async -> Data? {
        photos[id]
    }

    /// A month of sample weigh-ins, for previews.
    static func sample(now: Date = Date(), calendar: Calendar = .current) -> InMemoryWeightStore {
        let today = calendar.startOfDay(for: now)
        let weights: [Double] = [80.2, 80.0, 79.9, 79.6, 79.4, 79.5, 79.1, 79.0, 78.7, 78.4]
        let entries = weights.enumerated().compactMap { index, kilograms -> WeighIn? in
            let daysAgo = (weights.count - 1 - index) * 3
            guard let day = calendar.date(byAdding: .day, value: -daysAgo, to: today) else { return nil }
            return WeighIn(
                id: UUID(),
                day: day,
                kilograms: kilograms,
                loggedAt: day.addingTimeInterval(7 * 3_600 + 12 * 60),
                photoIDs: []
            )
        }
        return InMemoryWeightStore(entries: entries)
    }
}
