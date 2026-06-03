import SwiftUI

/// RTCstack design tokens, ported from `packages/ui-react/src/styles.css`.
/// Dark is the default (matches the web kit's `:root`); light mirrors `[data-theme="light"]`.
public struct RTCstackColors {
    public let bg: Color
    public let surface1: Color
    public let surface2: Color
    public let border: Color
    public let borderSubtle: Color
    public let text: Color
    public let textMuted: Color
    public let accent: Color
    public let danger: Color
    public let success: Color
    public let speakingRing: Color
    public let captionBg: Color
    public let captionText: Color

    public static let dark = RTCstackColors(
        bg: Color(hex: 0x0F0F0F),
        surface1: Color(hex: 0x1A1A1A),
        surface2: Color(hex: 0x252525),
        border: Color(hex: 0x333333),
        borderSubtle: Color(hex: 0x2A2A2A),
        text: Color(hex: 0xF5F5F5),
        textMuted: Color(hex: 0x999999),
        accent: Color(hex: 0x4F9CF9),
        danger: Color(hex: 0xE74C3C),
        success: Color(hex: 0x2ECC71),
        speakingRing: Color(hex: 0x4F9CF9),
        captionBg: Color.black.opacity(0.72),
        captionText: .white
    )

    public static let light = RTCstackColors(
        bg: Color(hex: 0xF5F5F5),
        surface1: .white,
        surface2: Color(hex: 0xEEEEEE),
        border: Color(hex: 0xDDDDDD),
        borderSubtle: Color(hex: 0xE5E5E5),
        text: Color(hex: 0x111111),
        textMuted: Color(hex: 0x666666),
        accent: Color(hex: 0x4F9CF9),
        danger: Color(hex: 0xE74C3C),
        success: Color(hex: 0x2ECC71),
        speakingRing: Color(hex: 0x4F9CF9),
        captionBg: Color.black.opacity(0.72),
        captionText: .white
    )
}

public enum RTCstackRadius {
    public static let sm: CGFloat = 4
    public static let md: CGFloat = 8
    public static let lg: CGFloat = 12
}

private struct RTCstackColorsKey: EnvironmentKey {
    static let defaultValue = RTCstackColors.dark
}

public extension EnvironmentValues {
    var rtcColors: RTCstackColors {
        get { self[RTCstackColorsKey.self] }
        set { self[RTCstackColorsKey.self] = newValue }
    }
}

public extension View {
    /// Provide RTCstack tokens to the view tree. Defaults to the current color scheme.
    func rtcstackTheme(_ colorScheme: ColorScheme? = nil) -> some View {
        modifier(RTCstackThemeModifier(forced: colorScheme))
    }
}

private struct RTCstackThemeModifier: ViewModifier {
    let forced: ColorScheme?
    @Environment(\.colorScheme) private var system

    func body(content: Content) -> some View {
        let scheme = forced ?? system
        let colors = scheme == .light ? RTCstackColors.light : RTCstackColors.dark
        content.environment(\.rtcColors, colors)
    }
}

extension Color {
    init(hex: UInt32) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: 1
        )
    }
}
