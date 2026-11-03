import CoreGraphics
import Foundation
import ImageIO
import QuickLookThumbnailing

/// Shows the map of a WorldPainter world as the icon of its .world file in the Finder, as Blender does for .blend files.
///
/// WorldPainter saves a small JPEG picture of the map in the extra field of the gzip header of the file, in a subfield
/// with the ID "WP" (see WorldThumbnail.java), so it is read here without decompressing anything. Files saved by older
/// versions have none; the Finder then shows the usual document icon.
class ThumbnailProvider: QLThumbnailProvider {
    override func provideThumbnail(for request: QLFileThumbnailRequest, _ handler: @escaping (QLThumbnailReply?, Error?) -> Void) {
        guard let jpeg = readThumbnail(request.fileURL),
              let source = CGImageSourceCreateWithData(jpeg as CFData, nil),
              let image = CGImageSourceCreateImageAtIndex(source, 0, nil) else {
            handler(nil, CocoaError(.fileReadCorruptFile))
            return
        }
        let width = CGFloat(image.width), height = CGFloat(image.height)
        let scale = min(request.maximumSize.width / width, request.maximumSize.height / height)
        let size = CGSize(width: max((width * scale).rounded(), 1), height: max((height * scale).rounded(), 1))
        handler(QLThumbnailReply(contextSize: size) { context in
            context.interpolationQuality = .high
            context.draw(image, in: CGRect(origin: .zero, size: size))
            return true
        }, nil)
    }

    /// The JPEG picture in the "WP" subfield of the extra field of the gzip header, if any.
    private func readThumbnail(_ url: URL) -> Data? {
        guard let file = try? FileHandle(forReadingFrom: url) else {
            return nil
        }
        defer {
            try? file.close()
        }
        // The fixed part of the header, then the length of the extra field
        let header = [UInt8](file.readData(ofLength: 12))
        guard header.count == 12, header[0] == 0x1f, header[1] == 0x8b, header[3] & 0x04 != 0 else {
            return nil
        }
        let extraLength = Int(header[10]) | Int(header[11]) << 8
        let extra = [UInt8](file.readData(ofLength: extraLength))
        guard extra.count == extraLength else {
            return nil
        }
        var i = 0
        while i + 4 <= extra.count {
            let length = Int(extra[i + 2]) | Int(extra[i + 3]) << 8
            if extra[i] == UInt8(ascii: "W"), extra[i + 1] == UInt8(ascii: "P"), i + 4 + length <= extra.count {
                return Data(extra[(i + 4)..<(i + 4 + length)])
            }
            i += 4 + length
        }
        return nil
    }
}
