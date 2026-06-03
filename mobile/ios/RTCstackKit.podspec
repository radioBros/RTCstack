Pod::Spec.new do |s|
  s.name             = 'RTCstackKit'
  s.version          = '1.0.2'
  s.summary          = 'RTCstack iOS SDK — a thin LiveKit wrapper mirroring @rtcstack/sdk.'
  s.homepage         = 'https://github.com/radioBros/RTCstack'
  s.license          = { :type => 'MIT' }
  s.author           = 'radioBros'
  s.source           = { :git => 'https://github.com/radioBros/RTCstack.git', :tag => "ios-v#{s.version}" }

  s.ios.deployment_target = '15.0'
  s.swift_version    = '5.9'

  s.source_files     = 'project/mobile/ios/Sources/RTCstackKit/**/*.swift'

  # Verify-on-Mac: align the version with Package.swift before publishing.
  s.dependency 'LiveKitClient', '~> 2.14'

  # NOTE: RTCstackUI (SwiftUI components, depends on components-swift) is SPM-only for now —
  # CocoaPods support for the UI layer can be added as a second podspec if there is demand.
end
