import Foundation
import Observation
import PhotosUI
import SwiftUI
import UIKit

@MainActor
@Observable
final class FoodViewModel {
    /// Days in the date strip, ending today.
    static let stripLength = 30

    var selectedDay: Date
    /// nil shows All.
    var filter: FoodLabel?
    private(set) var photos: [FoodPhoto] = []
    private(set) var isLoaded = false
    private(set) var isAdding = false
    var message: String?

    let store: any FoodStore
    private let ranges: DateRanges

    init(store: any FoodStore, ranges: DateRanges = DateRanges()) {
        self.store = store
        self.ranges = ranges
        self.selectedDay = ranges.calendar.startOfDay(for: Date())
    }

    var stripDays: [Date] { ranges.lastDays(Self.stripLength, endingAt: Date()) }

    var daysWithPhotos: Set<Date> { FoodMath.daysWithPhotos(photos, calendar: ranges.calendar) }

    /// The selected day's photos for the current filter, in the order taken.
    var dayPhotos: [FoodPhoto] {
        FoodMath.photos(photos, on: selectedDay, label: filter, calendar: ranges.calendar)
    }

    /// All of the selected day's photos, whatever the filter.
    var dayCount: Int {
        FoodMath.photos(photos, on: selectedDay, label: nil, calendar: ranges.calendar).count
    }

    func load() async {
        let interval = ranges.lastDaysInterval(Self.stripLength, endingAt: Date())
        do {
            photos = try await store.photos(in: interval)
        } catch {
            message = "Couldn't load food photos: \(error.localizedDescription)"
        }
        isLoaded = true
    }

    // MARK: Adding

    func addCameraImage(_ image: UIImage) async {
        guard let data = image.jpegData(compressionQuality: 0.95) else { return }
        isAdding = true
        defer { isAdding = false }
        // A photo taken now belongs to today.
        await add(data, takenAt: Date())
        selectedDay = ranges.calendar.startOfDay(for: Date())
        await load()
    }

    func addLibraryItems(_ items: [PhotosPickerItem]) async {
        isAdding = true
        defer { isAdding = false }
        var lastDay: Date?
        for item in items {
            do {
                guard let data = try await item.loadTransferable(type: Data.self) else { continue }
                let taken = PhotoProcessing.captureDate(data)
                    ?? FoodMath.fallbackTakenAt(selectedDay: selectedDay, now: Date(), calendar: ranges.calendar)
                if await add(data, takenAt: taken) {
                    lastDay = ranges.calendar.startOfDay(for: taken)
                }
            } catch {
                message = "Couldn't load a photo from the library."
            }
        }
        await load()
        if let lastDay {
            if stripDays.contains(lastDay) {
                selectedDay = lastDay
            } else {
                message = "Added to \(WeightFormat.day(lastDay)), which is before the dates shown here."
            }
        }
    }

    @discardableResult
    private func add(_ data: Data, takenAt: Date) async -> Bool {
        let prepared = await Task.detached(priority: .userInitiated) {
            PhotoProcessing.prepareForStorage(data)
        }.value
        guard let prepared else {
            message = "Couldn't read that photo."
            return false
        }
        do {
            try await store.add(jpeg: prepared, takenAt: takenAt)
            return true
        } catch {
            message = "Couldn't save the photo: \(error.localizedDescription)"
            return false
        }
    }
}
