import Foundation
import ImageIO
import UIKit
import UniformTypeIdentifiers

/// Prepares progress photos for storage: applies the orientation, resizes the
/// long edge to at most 2048 px and re-encodes as JPEG with no metadata, so
/// location (GPS) and camera data are dropped. Safe to call off the main actor.
enum PhotoProcessing {
    static let maxStoredPixelSize = 2_048
    static let jpegQuality = 0.8

    /// Resized JPEG with no metadata, or nil when the data is not an image.
    static func prepareForStorage(_ data: Data) -> Data? {
        guard let image = downsample(data, maxPixelSize: maxStoredPixelSize) else { return nil }
        return jpeg(image, quality: jpegQuality)
    }

    /// Small image for thumbnails.
    static func thumbnail(_ data: Data, maxPixelSize: Int) -> UIImage? {
        downsample(data, maxPixelSize: maxPixelSize).map { UIImage(cgImage: $0) }
    }

    static func downsample(_ data: Data, maxPixelSize: Int) -> CGImage? {
        let sourceOptions: [CFString: Any] = [kCGImageSourceShouldCache: false]
        guard let source = CGImageSourceCreateWithData(data as CFData, sourceOptions as CFDictionary) else { return nil }
        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceShouldCacheImmediately: true,
            kCGImageSourceThumbnailMaxPixelSize: maxPixelSize,
        ]
        return CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary)
    }

    /// Encodes without copying any properties from the source, so no metadata.
    static func jpeg(_ image: CGImage, quality: Double) -> Data? {
        let output = NSMutableData()
        guard let destination = CGImageDestinationCreateWithData(
            output as CFMutableData,
            UTType.jpeg.identifier as CFString,
            1,
            nil
        ) else { return nil }
        let properties: [CFString: Any] = [kCGImageDestinationLossyCompressionQuality: quality]
        CGImageDestinationAddImage(destination, image, properties as CFDictionary)
        guard CGImageDestinationFinalize(destination) else { return nil }
        return output as Data
    }
}
