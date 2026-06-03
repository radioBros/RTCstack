// TEMPLATE — copy into a Broadcast Upload Extension target you add in Xcode.
//
// This file is NOT part of the RTCstackKit SPM target. iOS screen share runs in a separate
// extension process; LiveKit ships a broadcast sample handler base you subclass.
//
// Setup on Mac (see MAC_HANDOFF.md §3):
//   1. Xcode → File → New → Target → Broadcast Upload Extension.
//   2. Add BOTH the app and this extension to the SAME App Group (e.g. group.com.yourapp.rtcstack).
//   3. Set the App Group id in both Info.plists / a shared constant.
//   4. Set BroadcastPickerView(preferredExtensionBundleId:) to this extension's bundle id.
//
// VERIFY-ON-MAC: confirm the exact base class name/module for the pinned client-sdk-swift.
// Recent LiveKit exposes `LKSampleHandler` (module: LiveKit) for exactly this purpose; older
// versions used a `BroadcastSampleHandler`. Subclass whichever the pinned version provides.

import ReplayKit
// import LiveKit   // uncomment on Mac

// Example using LiveKit's provided base (uncomment once the dependency resolves):
//
// class SampleHandler: LKSampleHandler {
//     override var enableLogging: Bool { true }
//     // The base class forwards CMSampleBuffers to the LiveKit screen-share track via the
//     // App Group. No manual socket wiring required.
// }

// Fallback skeleton if you must handle buffers manually:
class SampleHandler: RPBroadcastSampleHandler {
    override func broadcastStarted(withSetupInfo setupInfo: [String: NSObject]?) {
        // Hand off to LiveKit's broadcast uploader (see LiveKit screen-share docs).
    }

    override func broadcastFinished() {}

    override func processSampleBuffer(_ sampleBuffer: CMSampleBuffer, with sampleBufferType: RPSampleBufferType) {
        switch sampleBufferType {
        case .video:    break // forward to LiveKit uploader
        case .audioApp: break
        case .audioMic: break
        @unknown default: break
        }
    }
}
