import AppKit

// Render a Play feature graphic from the app's existing artwork and typography.
let root = URL(fileURLWithPath: FileManager.default.currentDirectoryPath)
CTFontManagerRegisterFontsForURL(root.appendingPathComponent("app/src/main/res/font/effra.ttf") as CFURL, .process, nil)
let context = CGContext(data: nil, width: 1024, height: 500, bitsPerComponent: 8, bytesPerRow: 4096, space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue)!
NSGraphicsContext.saveGraphicsState()
NSGraphicsContext.current = NSGraphicsContext(cgContext: context, flipped: false)
NSColor(calibratedRed: 0.90, green: 0.95, blue: 0.99, alpha: 1).setFill()
NSBezierPath(rect: NSRect(x: 0, y: 0, width: 1024, height: 500)).fill()
let ink = NSColor(calibratedRed: 0.25, green: 0.29, blue: 0.38, alpha: 1)
func text(_ value: String, x: CGFloat, y: CGFloat, size: CGFloat) {
    let font = NSFont(name: "Effra", size: size) ?? NSFont.systemFont(ofSize: size)
    (value as NSString).draw(at: NSPoint(x: x, y: y), withAttributes: [.font: font, .foregroundColor: ink])
}
text("Turn Touch", x: 80, y: 290, size: 66)
text("Four buttons.", x: 83, y: 215, size: 34)
text("Your favorite actions.", x: 83, y: 168, size: 34)
NSImage(contentsOf: root.appendingPathComponent("app/src/main/res/drawable/modal_remote_hero.png"))!.draw(in: NSRect(x: 590, y: 55, width: 370, height: 370))
NSGraphicsContext.restoreGraphicsState()
let bitmap = NSBitmapImageRep(cgImage: context.makeImage()!)
try bitmap.representation(using: .png, properties: [:])!.write(to: root.appendingPathComponent("store/en-US/images/featureGraphic.png"))
