// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "OpenCredentialSDK",
    defaultLocalization: "en",
    platforms: [.iOS(.v15)],
    products: [
        .library(name: "OpenCredentialSDK", targets: ["OpenCredentialSDK"]),
    ],
    dependencies: [],
    targets: [
        .target(
            name: "OpenCredentialSDK",
            dependencies: [],
            path: "Sources/OpenCredentialSDK",
            resources: [
                .process("Resources")
            ]
        ),
    ]
)
