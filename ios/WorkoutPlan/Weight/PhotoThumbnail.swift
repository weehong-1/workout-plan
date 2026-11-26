import SwiftUI
import UIKit

/// A progress photo, square-cropped to fill its frame. Decodes off the main actor.
struct PhotoThumbnail: View {
    let photo: DraftPhoto
    let store: any WeightStore
    var maxPixelSize = 300

    @State private var image: UIImage?

    var body: some View {
        ZStack {
            Color.appFill
            if let image {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFill()
            } else {
                Image(systemName: "photo")
                    .foregroundStyle(Color.appSecondaryLabel)
            }
        }
        .clipped()
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Progress photo")
        .task(id: photo.id) {
            image = await loadImage()
        }
    }

    private func loadImage() async -> UIImage? {
        let data: Data?
        switch photo {
        case .new(_, let newData):
            data = newData
        case .stored(let id):
            data = await store.photoData(id)
        }
        guard let data else { return nil }
        let size = maxPixelSize
        return await Task.detached(priority: .userInitiated) {
            PhotoProcessing.thumbnail(data, maxPixelSize: size)
        }.value
    }
}
