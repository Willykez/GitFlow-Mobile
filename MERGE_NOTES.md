# GitFlow Mobile — merge notes

This project merges three forks of the same app (WillyGit, GitCommander, AlphaClone)
into one, using **AlphaClone** as the base (it had the fullest feature set: Blame,
Conflicts, Discover/GitHub, Settings, background Sync) and applying **GitCommander's**
visual design system on top (the "cockpit" dark theme + GlassCard component).

## What changed

**Design system**
- Adopted GitCommander's dark "command center" palette (`ui/theme/Color.kt`, `Theme.kt`):
  electric blue for navigation/primary actions, gold reserved only for commit/push —
  so gold always signals "this touches the remote."
- Added `ui/components/GlassCard.kt` — the shared translucent card with hairline border
  and optional left-edge accent color, now used for repo cards, file rows, and the new
  tools sheet.
- App renamed to **GitFlow Mobile** throughout (display name, package, identifiers).

**Home screen (RepoListScreen)** — repo cards now use GlassCard with a left accent bar
(blue = clean, gold = uncommitted changes, red = error) instead of a flat surface color.

**File Explorer** — file/folder rows use GlassCard; folders get a blue accent.

**Changes screen — the big fix**: the old overflow menu crammed 24 unrelated actions
(stage, commit, fetch, pull ×3, push ×2, sync ×2, log, cherry-pick, amend, squash,
reset ×3, stash, branches, tags, remotes, gitignore, files) into one tall flat dropdown.
Replaced with:
- **Push** promoted to a direct top-bar icon (it's the #1 reason people open that menu).
- Everything else moved into a **bottom sheet** (`RepoToolsSheet`) grouped into five
  collapsible sections — Sync, Staging, History, Reset, Manage — each closed by default
  so the sheet opens short and a person only expands the group they came for.

## What wasn't touched
Business logic, ViewModels, git engine, database, and sync code are unchanged —
they were already solid across all three forks (AlphaClone's versions are the superset
and were kept as-is). Other screens (Diff, Log, Branches, Stash, Tags, Remote,
Credential, Blame, Conflicts, Discover, Settings) still use the old surface color
instead of GlassCard — same visual language is there via the shared theme, but they
weren't individually re-carded. Good next step if you want full visual consistency.

## Known limitation
This was assembled and reviewed by hand — bracket/import balance was checked, but it
has **not been compiled or run through Gradle** (no network access in this environment
to pull dependencies). Open it in Android Studio and build before relying on it; if
anything doesn't compile it's most likely a stray import, and the diffs above tell you
exactly where to look.

## Rename: Willy Commander → GitFlow Mobile
App name, package (`willykez.gitflowmobile`), applicationId, all identifiers
(`GitFlowMobileTheme`, `GitFlowMobileNavHost`, etc.), internal storage keys
(DB filename, keystore alias, worker names), and the public repo folder name
were all updated for consistency. Launcher icon was swapped to GitCommander's
adaptive icon (git graph in command-blue on the app's navy background) —
`res/drawable/ic_launcher_foreground.xml` + `res/values/colors.xml`.
