import SwiftUI
import OpenCredentialSDK

@main
struct SampleApp: App
{
    init()
    {
        // Initialize the SDK: load stored keys or generate new ones
        let sdk = OpenCredentialSDK.shared
        if !sdk.loadStoredKeys()
        {
            sdk.generateKeys()
            print("[SampleApp] Generated new P-256 key pair")
        }
        else
        {
            print("[SampleApp] Loaded stored keys")
        }
    }

    var body: some Scene
    {
        WindowGroup
        {
            ContentView()
        }
    }
}
