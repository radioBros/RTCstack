// Example app sources. SwiftUI app targets can't be a SwiftPM product, so on the Mac:
//   1. Xcode → New → App (SwiftUI lifecycle), iOS 15+.
//   2. Add the local package: File → Add Packages → point at ../  (project/mobile/ios).
//   3. Add RTCstackKit + RTCstackUI to the app target.
//   4. Add these two files to the app target; set Info.plist camera/mic usage strings.
//   5. (For screen share / CallKit / VoIP push, follow MAC_HANDOFF.md §3.)

import SwiftUI

@main
struct RTCstackExampleApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView().rtcstackTheme()
        }
    }
}
