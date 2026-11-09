// swift-tools-version:5.9
//
// BitChordCore: everything in the iOS app that does not need Apple's UI or audio frameworks —
// the YouTube Music client and its parser, Selaras (state, presets, AutoEq, the DSP itself) and
// lyrics. Kept free of UIKit/AVFoundation so it builds and tests on Linux too:
//
//   swift test --package-path ios/BitChordCore
//
import PackageDescription

let package = Package(
    name: "BitChordCore",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [
        .library(name: "BitChordCore", targets: ["BitChordCore"]),
    ],
    dependencies: [
        // SHA-1 for YouTube's SAPISIDHASH, on Linux as well as on Apple platforms.
        .package(url: "https://github.com/apple/swift-crypto.git", "3.0.0"..<"4.0.0"),
    ],
    targets: [
        .target(
            name: "BitChordCore",
            dependencies: [.product(name: "Crypto", package: "swift-crypto")]
        ),
        .testTarget(
            name: "BitChordCoreTests",
            dependencies: ["BitChordCore"]
        ),
    ],
    swiftLanguageVersions: [.v5]
)
