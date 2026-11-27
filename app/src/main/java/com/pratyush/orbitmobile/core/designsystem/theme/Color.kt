package com.pratyush.orbitmobile.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Centrally controlled color palette for ORBIT Mobile.
 * Visual direction: Pitch-black background, dark surfaces, purple accents.
 */
object OrbitColors {
    // Pitch Black & Deep Backgrounds
    val PitchBlack = Color(0xFF000000)
    val BackgroundDark = Color(0xFF08080A)
    val SurfaceDark = Color(0xFF101014)
    val SurfaceCard = Color(0xFF16161C)
    val SurfaceCardElevated = Color(0xFF1E1E26)
    val BorderSubtle = Color(0xFF272733)
    val BorderGlowing = Color(0xFF5B21B6)

    // Purple Accents (Primary Identity)
    val PurplePrimary = Color(0xFF8B5CF6)       // Primary vibrant purple
    val PurpleSecondary = Color(0xFFA78BFA)     // Secondary lighter purple
    val PurpleAccentGlow = Color(0xFFC084FC)    // Bright lavender glow
    val PurpleDeep = Color(0xFF581C87)          // Deep container purple
    val PurpleDarkContainer = Color(0xFF2E1065) // Dark container background

    // Neutral text tokens
    val TextPrimary = Color(0xFFF8FAFC)
    val TextSecondary = Color(0xFF94A3B8)
    val TextTertiary = Color(0xFF64748B)

    // System Status Accents
    val StatusOnline = Color(0xFF10B981)   // Emerald green
    val StatusConnecting = Color(0xFFF59E0B) // Amber
    val StatusOffline = Color(0xFFEF4444)  // Rose red
    val StatusInfo = Color(0xFF38BDF8)     // Sky blue

    /**
     * Pixel / Material You inspired tonal feature accent palette.
     * Features individual card surface tint, fluid wave color, icon container, and action button.
     */
    object Feature {
        // Remote Controls: Warm coral / terracotta
        val RemoteControls = FeatureCardTokens(
            cardSurface = Color(0xFF1B1617),
            waveColor = Color(0xFFB55737),
            iconContainer = Color(0xFFA84A2B),
            iconTint = Color.White,
            arrowContainer = Color(0xFF9E4427),
            arrowTint = Color.White
        )

        // Clipboard: Teal / mint
        val Clipboard = FeatureCardTokens(
            cardSurface = Color(0xFF131B19),
            waveColor = Color(0xFF1D5C4F),
            iconContainer = Color(0xFF174E43),
            iconTint = Color(0xFF5EEAD4),
            arrowContainer = Color(0xFF1A564A),
            arrowTint = Color.White
        )

        // AI Assistant: Purple / lavender
        val AiAssistant = FeatureCardTokens(
            cardSurface = Color(0xFF1A1522),
            waveColor = Color(0xFF59347E),
            iconContainer = Color(0xFF49276A),
            iconTint = Color(0xFFE9D5FF),
            arrowContainer = Color(0xFF542C7D),
            arrowTint = Color.White
        )

        // Trackpad: Blue / periwinkle
        val Trackpad = FeatureCardTokens(
            cardSurface = Color(0xFF141824),
            waveColor = Color(0xFF273D72),
            iconContainer = Color(0xFF1D2D56),
            iconTint = Color(0xFF93C5FD),
            arrowContainer = Color(0xFF253B73),
            arrowTint = Color.White
        )

        // File Access: Amber / soft yellow
        val FileAccess = FeatureCardTokens(
            cardSurface = Color(0xFF1C1812),
            waveColor = Color(0xFF856420),
            iconContainer = Color(0xFF5A4215),
            iconTint = Color(0xFFFDE68A),
            arrowContainer = Color(0xFF785617),
            arrowTint = Color.White
        )

        // System Monitor: Forest / mint green
        val SystemMonitor = FeatureCardTokens(
            cardSurface = Color(0xFF131B15),
            waveColor = Color(0xFF295E35),
            iconContainer = Color(0xFF1B4525),
            iconTint = Color(0xFF86EFAC),
            arrowContainer = Color(0xFF265C32),
            arrowTint = Color.White
        )
    }

    /**
     * Strict Material You Color Identity tokens for the Clipboard Screen.
     */
    object ClipboardTheme {
        // Deep background with organic accents
        val ScreenBackground = Color(0xFF090C15)
        val BlobTopRight = Color(0xFF382269)
        val BlobBottomLeft = Color(0xFF162552)
        val BlobBottomRight = Color(0xFF22163D)

        // Sync Active Card (Violet / Lavender)
        val SyncActiveCardBg = Color(0xFF13172B)
        val SyncActiveCardBorder = Color(0xFF262D4A)
        val SyncActiveIconBg = Color(0xFF1D2C59)
        val SyncActiveIconTint = Color(0xFF93C5FD)
        val SyncActiveSwitchTrackOn = Color(0xFF4F46E5)
        val SyncActiveSwitchThumbOn = Color(0xFFFFFFFF)

        // Current Phone Clipboard Card (Blue / Cyan)
        val CurrentClipCardBg = Color(0xFF101524)
        val CurrentClipCardBorder = Color(0xFF1D2845)
        val CurrentClipIconBg = Color(0xFF162544)
        val CurrentClipIconTint = Color(0xFF38BDF8)
        val SyncToPcPillBg = Color(0xFF162A4A)
        val SyncToPcPillBorder = Color(0xFF23447A)
        val SyncToPcTextTint = Color(0xFF38BDF8)
        val InnerClipContainerBg = Color(0xFF0C101C)
        val InnerClipContainerBorder = Color(0xFF1A233A)
        val InnerClipIconBg = Color(0xFF182A4D)
        val InnerClipIconTint = Color(0xFF60A5FA)

        // Information Notice (Muted Amber / Gold)
        val InfoNoticeBg = Color(0xFF17161F)
        val InfoNoticeBorder = Color(0xFF2F261E)
        val InfoNoticeIconTint = Color(0xFFFBBF24)
        val InfoNoticeText = Color(0xFFD6D3D1)

        // Synced History (Indigo / Blue unified treatment)
        val HistoryCardBg = Color(0xFF121626)
        val HistoryCardBorder = Color(0xFF1E2642)
        val HistoryIconBg = Color(0xFF1A2342)
        val HistoryIconTint = Color(0xFF818CF8)
        val HistoryBadgeBg = Color(0xFF1F2B52)
        val HistoryBadgeText = Color(0xFFA5B4FC)
        val HistoryCopyBtnBg = Color(0xFF17203A)
        val HistoryCopyBtnTint = Color(0xFF94A3B8)

        // Source badges
        val SourcePhoneText = Color(0xFF38BDF8)   // Cyan
        val SourcePhoneBg = Color(0xFF11283B)
        val SourceWindowsText = Color(0xFFC084FC) // Violet
        val SourceWindowsBg = Color(0xFF2C1C45)

        // Actions / Destructive
        val DeleteButtonBg = Color(0xFF26151B)
        val DeleteButtonTint = Color(0xFFF87171)
        val DeleteBorder = Color(0xFF4C2129)

        // Floating Pill Navigation
        val NavPillBg = Color(0xF0121626)
        val NavPillBorder = Color(0xFF252D47)
        val NavSelectedPill = Color(0xFF2B3A70)
        val NavSelectedTint = Color(0xFF8B9BFF)
        val NavUnselectedTint = Color(0xFF737F9E)
    }
}

/**
 * Visual design tokens for the Home Screen feature cards.
 */
data class FeatureCardTokens(
    val cardSurface: Color,
    val waveColor: Color,
    val iconContainer: Color,
    val iconTint: Color,
    val arrowContainer: Color,
    val arrowTint: Color
)

val DarkColorScheme = darkColorScheme(
    primary = OrbitColors.PurplePrimary,
    onPrimary = Color.White,
    primaryContainer = OrbitColors.PurpleDarkContainer,
    onPrimaryContainer = OrbitColors.PurpleSecondary,
    secondary = OrbitColors.PurpleSecondary,
    onSecondary = Color.Black,
    secondaryContainer = OrbitColors.SurfaceCardElevated,
    onSecondaryContainer = OrbitColors.TextPrimary,
    tertiary = OrbitColors.PurpleAccentGlow,
    onTertiary = Color.Black,
    background = OrbitColors.PitchBlack,
    onBackground = OrbitColors.TextPrimary,
    surface = OrbitColors.SurfaceDark,
    onSurface = OrbitColors.TextPrimary,
    surfaceVariant = OrbitColors.SurfaceCard,
    onSurfaceVariant = OrbitColors.TextSecondary,
    outline = OrbitColors.BorderSubtle,
    outlineVariant = OrbitColors.BorderGlowing
)
