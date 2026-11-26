import Foundation
import SwiftData

/// Weigh-ins and their progress photos.
///
/// Phase 2 keeps everything on the phone (spec D8). The backend is still open
/// (spec 8); a synced store can replace this one behind the same protocol.
protocol WeightStore: Sendable {
    /// All weigh-ins, newest day first.
    func entries() async throws -> [WeighIn]

    /// Saves a weigh-in, keeping one entry per day: saving onto a day that
    /// already has an entry updates it. `editingID` is the entry the sheet
    /// was opened for, if any.
    @discardableResult
    func save(_ draft: WeighInDraft, editingID: UUID?) async throws -> WeighIn

    /// Deletes a weigh-in and its photos.
    func delete(_ id: UUID) async throws

    /// Stored JPEG data of a progress photo.
    func photoData(_ id: UUID) async -> Data?
}

enum WeightStoreFactory {
    /// SwiftData store on disk. Falls back to memory only if the store cannot
    /// be opened, so the app still runs (nothing is then kept).
    static func make() -> any WeightStore {
        do {
            let container = try ModelContainer(for: WeighInRecord.self)
            return SwiftDataWeightStore(modelContainer: container)
        } catch {
            assertionFailure("Could not open the weight store: \(error)")
            return InMemoryWeightStore()
        }
    }
}

/// Progress photo files in Application Support/WeightPhotos, one JPEG each.
struct PhotoFiles: Sendable {
    let directory: URL

    init(folderName: String = "WeightPhotos") {
        directory = URL.applicationSupportDirectory.appending(path: folderName, directoryHint: .isDirectory)
    }

    func url(for id: UUID) -> URL {
        directory.appending(path: "\(id.uuidString).jpg", directoryHint: .notDirectory)
    }

    func write(_ data: Data, id: UUID) throws {
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        try data.write(to: url(for: id), options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
    }

    func read(_ id: UUID) -> Data? {
        try? Data(contentsOf: url(for: id))
    }

    func delete(_ ids: [UUID]) {
        for id in ids {
            try? FileManager.default.removeItem(at: url(for: id))
        }
    }
}
