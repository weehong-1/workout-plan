import CryptoKit
import Foundation
import Security

/// What a trainer link includes (design `TrainerLink.dc.html`).
struct ShareIncludes: Codable, Equatable, Sendable {
    var activity = true
    var weight = true
    var weightPhotos = false
    var food = false
}

/// A link as listed under Active links. Read from `share_links`.
struct ShareLinkSummary: Identifiable, Decodable, Sendable {
    let id: UUID
    let trainerName: String
    let periodLabel: String
    let expiresAt: Date?
    let createdAt: Date
    let photoPaths: [String]

    enum CodingKeys: String, CodingKey {
        case id
        case trainerName = "trainer_name"
        case periodLabel = "period_label"
        case expiresAt = "expires_at"
        case createdAt = "created_at"
        case photoPaths = "photo_paths"
    }

    func isExpired(now: Date) -> Bool {
        expiresAt.map { $0 <= now } ?? false
    }
}

/// The review copied to the server for a link, read by the web page
/// `/share/<token>`. Version 1; numbers already rounded, weights in the
/// chosen unit, dates as yyyy-MM-dd in the phone's time zone.
struct SharedReviewPayload: Codable, Sendable {
    struct Period: Codable, Sendable {
        let kind: String
        let label: String
        let start: String
        let end: String
        let days: Int
    }

    struct Activity: Codable, Sendable {
        struct Day: Codable, Sendable {
            let date: String
            let steps: Int
            let moveKcal: Int
            let sleepMinutes: Int?
            let ringsClosed: Bool
        }
        let averageSteps: Int?
        let averageSleepMinutes: Int?
        let averageMoveKcal: Int?
        let ringsClosedDays: Int
        let daily: [Day]
    }

    struct Weight: Codable, Sendable {
        struct Point: Codable, Sendable {
            let date: String
            let value: Double
        }
        struct Photo: Codable, Sendable {
            let path: String
            let date: String
        }
        let unit: String
        let count: Int
        let first: Point
        let last: Point
        let change: Double
        let entries: [Point]
        let firstPhoto: Photo?
        let lastPhoto: Photo?
    }

    struct Food: Codable, Sendable {
        struct Photo: Codable, Sendable {
            let path: String
            let date: String
            let time: String
            let label: String?
        }
        let daysWithPhotos: Int
        let photos: [Photo]
    }

    var version = 1
    let period: Period
    /// Last day the link works (yyyy-MM-dd), or nil for no expiry.
    let expiresOn: String?
    let activity: Activity?
    let weight: Weight?
    let food: Food?
}

/// Creates, lists and revokes trainer links (spec 7, D10, D11).
///
/// Creating a link uploads the chosen summaries and photos to the existing
/// Supabase project. The link holds a random token; only its SHA-256 hash is
/// stored on the server, and the token itself stays in this phone's keychain
/// so the link can be copied again.
struct TrainerLinkService: Sendable {
    let client: SupabaseClient
    let loader: ReviewLoader
    let photoSources: (weight: any WeightStore, food: any FoodStore)

    static let tokenKeyPrefix = "share.token."
    /// Long edge of shared photos.
    static let sharedPixelSize = 1_280

    // MARK: Create

    struct Request: Sendable {
        var trainerName: String
        var period: ReviewPeriod
        var includes: ShareIncludes
        /// Last day the link works; nil for no expiry.
        var expiresOn: Date?
        var unit: WeightUnit
    }

    func create(_ request: Request, progress: @escaping @Sendable @MainActor (Int, Int) -> Void) async throws -> URL {
        guard let base = client.config.webAppURL else { throw BackendError.notConfigured }
        let session = try await client.validSession()
        let calendar = Calendar.current
        let now = Date()
        let data = try await loader.load(request.period, now: now)
        let linkID = UUID()
        let token = Self.randomToken()

        // Collect the photos first, so the payload can point at their paths.
        var uploads: [(path: String, read: @Sendable () async -> Data?)] = []
        func path(for photoID: UUID) -> String {
            "\(session.userFolder)/share-\(linkID.uuidString.lowercased())-\(photoID.uuidString.lowercased()).jpg"
        }
        let weightStore = photoSources.weight
        let foodStore = photoSources.food

        var weightPayload: SharedReviewPayload.Weight?
        if request.includes.weight, let review = data.weight {
            func point(_ entry: WeighIn) -> SharedReviewPayload.Weight.Point {
                .init(date: Self.day(entry.day), value: Self.round1(WeightMath.value(kilograms: entry.kilograms, in: request.unit)))
            }
            func photo(_ pair: (entry: WeighIn, photoID: UUID)?) -> SharedReviewPayload.Weight.Photo? {
                guard request.includes.weightPhotos, let pair else { return nil }
                let photoPath = path(for: pair.photoID)
                let id = pair.photoID
                uploads.append((photoPath, { await weightStore.photoData(id) }))
                return .init(path: photoPath, date: Self.day(pair.entry.day))
            }
            weightPayload = .init(
                unit: request.unit.rawValue,
                count: review.entries.count,
                first: point(review.first),
                last: point(review.last),
                change: Self.round1(WeightMath.value(kilograms: review.change, in: request.unit)),
                entries: review.entries.map(point),
                firstPhoto: photo(review.firstPhoto),
                lastPhoto: photo(review.lastPhoto)
            )
        }

        var foodPayload: SharedReviewPayload.Food?
        if request.includes.food {
            let photos = data.food.photos.map { item -> SharedReviewPayload.Food.Photo in
                let photoPath = path(for: item.id)
                let id = item.id
                uploads.append((photoPath, { await foodStore.photoData(id) }))
                return .init(
                    path: photoPath,
                    date: Self.day(item.takenAt),
                    time: Self.time(item.takenAt),
                    label: item.label?.title
                )
            }
            foodPayload = .init(daysWithPhotos: data.food.daysWithPhotos, photos: photos)
        }

        var activityPayload: SharedReviewPayload.Activity?
        if request.includes.activity, let review = data.activity {
            activityPayload = .init(
                averageSteps: review.averageSteps.map { Int($0.rounded()) },
                averageSleepMinutes: review.averageSleep.map { Int(($0 / 60).rounded()) },
                averageMoveKcal: review.averageMoveKcal.map { Int($0.rounded()) },
                ringsClosedDays: review.ringsClosedDays,
                daily: review.daily.map {
                    .init(
                        date: Self.day($0.day),
                        steps: Int($0.steps.rounded()),
                        moveKcal: Int($0.moveKcal.rounded()),
                        sleepMinutes: $0.sleep.map { Int(($0 / 60).rounded()) },
                        ringsClosed: $0.ringsClosed
                    )
                }
            )
        }

        let interval = request.period.interval(calendar: calendar)
        let lastDay = calendar.date(byAdding: .day, value: -1, to: interval.end) ?? interval.start
        let payload = SharedReviewPayload(
            period: .init(
                kind: request.period.kind.rawValue,
                label: request.period.label,
                start: Self.day(interval.start),
                end: Self.day(lastDay),
                days: data.days.count
            ),
            expiresOn: request.expiresOn.map(Self.day),
            activity: activityPayload,
            weight: weightPayload,
            food: foodPayload
        )

        // Upload photos, then the row. On any failure, remove what was uploaded.
        var uploaded: [String] = []
        do {
            for (index, upload) in uploads.enumerated() {
                await progress(index, uploads.count)
                guard let original = await upload.read() else { continue }
                let size = Self.sharedPixelSize
                let resized = await Task.detached(priority: .userInitiated) { () -> Data? in
                    guard let image = PhotoProcessing.downsample(original, maxPixelSize: size) else { return nil }
                    return PhotoProcessing.jpeg(image, quality: 0.8)
                }.value
                guard let resized else { continue }
                try await client.upload(jpeg: resized, path: upload.path)
                uploaded.append(upload.path)
            }
            await progress(uploads.count, uploads.count)

            let expiresAt = request.expiresOn.flatMap { day in
                calendar.date(byAdding: .day, value: 1, to: calendar.startOfDay(for: day))
            }
            let row = Insert(
                id: linkID,
                token_hash: Self.sha256Hex(token),
                trainer_name: String(request.trainerName.trimmingCharacters(in: .whitespacesAndNewlines).prefix(80)),
                period_kind: request.period.kind.rawValue,
                period_start: Self.day(interval.start),
                period_label: request.period.label,
                includes: request.includes,
                summary: payload,
                photo_paths: uploaded,
                expires_at: expiresAt.map { ISO8601DateFormatter().string(from: $0) }
            )
            try await client.insert("share_links", json: JSONEncoder().encode(row))
        } catch {
            try? await client.removeObjects(uploaded)
            throw error
        }

        Keychain.set(Data(token.utf8), for: Self.tokenKeyPrefix + linkID.uuidString)
        return Self.url(base: base, token: token)
    }

    private struct Insert: Encodable {
        let id: UUID
        let token_hash: String
        let trainer_name: String
        let period_kind: String
        let period_start: String
        let period_label: String
        let includes: ShareIncludes
        let summary: SharedReviewPayload
        let photo_paths: [String]
        let expires_at: String?
    }

    // MARK: List and revoke

    func list() async throws -> [ShareLinkSummary] {
        let data = try await client.select("share_links", query: [
            URLQueryItem(name: "select", value: "id,trainer_name,period_label,expires_at,created_at,photo_paths"),
            URLQueryItem(name: "order", value: "created_at.desc"),
        ])
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .custom { decoder in
            let text = try decoder.singleValueContainer().decode(String.self)
            if let date = Self.parseTimestamp(text) { return date }
            throw DecodingError.dataCorrupted(.init(codingPath: decoder.codingPath, debugDescription: "Bad date \(text)"))
        }
        return try decoder.decode([ShareLinkSummary].self, from: data)
    }

    /// Deletes the link and its uploaded photos. The page stops working at once.
    func revoke(_ link: ShareLinkSummary) async throws {
        try await client.delete("share_links", query: [URLQueryItem(name: "id", value: "eq.\(link.id.uuidString.lowercased())")])
        try? await client.removeObjects(link.photoPaths)
        Keychain.set(nil, for: Self.tokenKeyPrefix + link.id.uuidString)
    }

    /// The link again, when this phone created it.
    func url(for link: ShareLinkSummary) -> URL? {
        guard let data = Keychain.data(for: Self.tokenKeyPrefix + link.id.uuidString),
              let token = String(data: data, encoding: .utf8),
              let base = client.config.webAppURL
        else { return nil }
        return Self.url(base: base, token: token)
    }

    // MARK: Helpers

    static func url(base: URL, token: String) -> URL {
        base.appending(path: "share").appending(path: token)
    }

    /// 32 random bytes, base64url without padding (43 characters).
    static func randomToken() -> String {
        var bytes = [UInt8](repeating: 0, count: 32)
        if SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes) != errSecSuccess {
            bytes = (0..<32).map { _ in UInt8.random(in: .min ... .max) }
        }
        return Data(bytes).base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }

    static func sha256Hex(_ text: String) -> String {
        SHA256.hash(data: Data(text.utf8)).map { String(format: "%02x", $0) }.joined()
    }

    static func day(_ date: Date) -> String {
        let parts = Calendar.current.dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02d", parts.year ?? 0, parts.month ?? 0, parts.day ?? 0)
    }

    static func time(_ date: Date) -> String {
        let parts = Calendar.current.dateComponents([.hour, .minute], from: date)
        return String(format: "%02d:%02d", parts.hour ?? 0, parts.minute ?? 0)
    }

    static func round1(_ value: Double) -> Double {
        (value * 10).rounded() / 10
    }

    static func parseTimestamp(_ text: String) -> Date? {
        let withFraction = ISO8601DateFormatter()
        withFraction.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let date = withFraction.date(from: text) { return date }
        return ISO8601DateFormatter().date(from: text)
    }
}
