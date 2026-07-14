package willykez.gitflowmobile.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * GitFlow Mobile's palette — a dark "command center" cockpit rather than a
 * light productivity-app look. Electric blue is the command/navigation
 * color; gold is deliberately rationed to the two highest-stakes actions
 * (commit, push) so it always reads as "this changes the remote."
 */

// Core surfaces
val Void = Color(0xFF0A0E14)          // app background — near-black, slight blue cast
val Hull = Color(0xFF10161F)          // base surface (scaffold, bars)
val Deck = Color(0xFF161D28)          // card surface
val DeckRaised = Color(0xFF1E2733)    // pressed / raised card, sheets, menus
val HullBorder = Color(0x1FFFFFFF)    // ~12% white — hairline borders for the "glass" edge

// Command accent — navigation, links, primary actions
val CommandBlue = Color(0xFF4FB6FF)
val CommandBlueDeep = Color(0xFF1C7FD1)
val CommandBlueDim = Color(0x224FB6FF) // translucent chip/badge fill

// Gold — reserved for commit / push / anything that touches the remote
val SignalGold = Color(0xFFF2B33D)
val SignalGoldDeep = Color(0xFFC98A1E)

// Status
val StatusAdded = Color(0xFF3ED97D)     // staged / new file / success
val StatusModified = Color(0xFFF2B33D)  // modified file (shares gold hue family)
val StatusDeleted = Color(0xFFFF6363)   // deleted / destructive / errors
val StatusMuted = Color(0xFF8792A3)     // neutral / clean / secondary text

// Light theme (secondary — dark is the default identity for a dev tool)
val Paper = Color(0xFFF6F7F9)
val PaperDim = Color(0xFFE9EBEF)
val Graphite = Color(0xFF11151C)

// ---------------------------------------------------------------------------
// Legacy token aliases — every screen inherited from the three source apps
// was written against one of these names. Keeping them as aliases (instead
// of touching 50+ screen files individually) repaints the whole app with
// the unified palette immediately and consistently. New/rewritten screens
// should prefer the tokens above.
// ---------------------------------------------------------------------------
val PlumDeep = Hull
val PlumSoft = Deck
val Amber = SignalGold
val AmberDeep = SignalGoldDeep
val Cream = Color(0xFFF3F5F8)
val CreamDim = DeckRaised
val Ink = Void
val InkSurface = Deck
val StatusClean = StatusMuted
