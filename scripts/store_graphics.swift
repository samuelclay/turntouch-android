import AppKit

// Native text shaping plus the app's original artwork; no synthesized app UI.
let root = URL(fileURLWithPath: FileManager.default.currentDirectoryPath)
CTFontManagerRegisterFontsForURL(root.appendingPathComponent("app/src/main/res/font/effra.ttf") as CFURL, .process, nil)
let locales = try JSONSerialization.jsonObject(with: Data(contentsOf: root.appendingPathComponent("localization/store-locales.json"))) as! [String: String]
for (language, locale) in locales.sorted(by: {$0.key < $1.key}) {
    let copy = try JSONSerialization.jsonObject(with: Data(contentsOf: root.appendingPathComponent("localization/store/\(language).json"))) as! [String: String]
    let context = CGContext(data: nil, width: 1024, height: 500, bitsPerComponent: 8, bytesPerRow: 4096, space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue)!
    NSGraphicsContext.saveGraphicsState()
    NSGraphicsContext.current = NSGraphicsContext(cgContext: context, flipped: false)
    NSColor(calibratedRed: 0.90, green: 0.95, blue: 0.99, alpha: 1).setFill()
    NSRect(x: 0, y: 0, width: 1024, height: 500).fill()
    let ink = NSColor(calibratedRed: 0.25, green: 0.29, blue: 0.38, alpha: 1)
    ("Turn Touch" as NSString).draw(at: NSPoint(x: 80, y: 290), withAttributes: [.font: NSFont(name: "Effra", size: 66)!, .foregroundColor: ink])
    let style = NSMutableParagraphStyle()
    style.alignment = ["ar", "he"].contains(language) ? .right : .left
    style.baseWritingDirection = ["ar", "he"].contains(language) ? .rightToLeft : .leftToRight
    style.lineBreakStrategy = .hangulWordPriority
    let rect = NSRect(x: 83, y: 125, width: 465, height: 145)
    let caption = copy["screenshot-caption"]! as NSString
    var size: CGFloat = 39
    var attrs: [NSAttributedString.Key: Any] = [:]
    repeat {
        attrs = [.font: NSFont.systemFont(ofSize: size, weight: .medium), .foregroundColor: ink, .paragraphStyle: style]
        if caption.boundingRect(with: NSSize(width: rect.width, height: 10000), options: [.usesLineFragmentOrigin, .usesFontLeading], attributes: attrs).height < rect.height - 10 { break }
        size -= 1
    } while size > 24
    caption.draw(with: rect, options: [.usesLineFragmentOrigin, .usesFontLeading], attributes: attrs)
    NSImage(contentsOf: root.appendingPathComponent("app/src/main/res/drawable/modal_remote_hero.png"))!.draw(in: NSRect(x: 590, y: 55, width: 370, height: 370))
    NSGraphicsContext.restoreGraphicsState()
    let bitmap = NSBitmapImageRep(cgImage: context.makeImage()!)
    let destination = root.appendingPathComponent("store/\(locale)/images")
    try FileManager.default.createDirectory(at: destination, withIntermediateDirectories: true)
    try bitmap.representation(using: .png, properties: [:])!.write(to: destination.appendingPathComponent("featureGraphic.png"))
}
