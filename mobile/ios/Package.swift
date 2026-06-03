// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "RTCstack",
    platforms: [
        .iOS(.v15),
        .macOS(.v12),
    ],
    products: [
        .library(name: "RTCstackKit", targets: ["RTCstackKit"]),
        .library(name: "RTCstackUI", targets: ["RTCstackUI"]),
    ],
    dependencies: [
        // Verify-on-Mac: bump to the latest 2.x release (researched: 2.14+).
        .package(url: "https://github.com/livekit/client-sdk-swift.git", .upToNextMajor(from: "2.14.0")),
        .package(url: "https://github.com/livekit/components-swift.git", .upToNextMajor(from: "0.1.0")),
    ],
    targets: [
        .target(
            name: "RTCstackKit",
            dependencies: [
                .product(name: "LiveKit", package: "client-sdk-swift"),
            ]
        ),
        .target(
            name: "RTCstackUI",
            dependencies: [
                "RTCstackKit",
                .product(name: "LiveKitComponents", package: "components-swift"),
            ]
        ),
        .testTarget(
            name: "RTCstackKitTests",
            dependencies: ["RTCstackKit"]
        ),
    ]
)
