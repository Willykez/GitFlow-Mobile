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

Rename was later completed in `.github/workflows/ci.yml`, `release.yml`
(artifact names, keystore alias, `-P` Gradle property names), and
`app/proguard-rules.pro` (which still had `-keep` rules pointed at the old
`willykez.alphaclone` package — this would have silently broken R8 in
release builds, since the shrinker wouldn't have known to keep the real
entity/DAO/crypto classes).

## Bug fixes

- **Clone sheet failing to appear on first tap (once a repo already
  exists)**: `CloneViewModel` is scoped to the screen behind the sheet, not
  to the sheet's own visibility — so after a successful clone, `done =
  true` stayed set on the retained ViewModel. The next time the sheet
  opened, its `LaunchedEffect(state.done)` fired immediately on first
  composition, closing the sheet before it could animate in. Fixed by
  adding `CloneViewModel.resetForm()`, called right after `onCloned()` is
  consumed.
- **Keyboard covering editable fields**: the app calls `enableEdgeToEdge()`,
  which means `windowSoftInputMode="adjustResize"` alone doesn't push
  content above the keyboard — Compose needs an explicit `imePadding()`.
  Added to: the Clone sheet, the Credential (token) sheet, the commit
  message bar in Changes, the code editor in FileEditor, the `.gitignore`
  editor, and the merge-commit-message field in Conflicts.


## Editor upgrade: syntax highlighting, gutter, undo/redo, Markdown preview

New files under `ui/screens/editor/`:
- `SyntaxHighlighting.kt` — extension-based language detection + a regex
  tokenizer (keywords/types/strings/numbers/comments/annotations) wrapped
  as a `VisualTransformation`, so highlighting is purely visual and never
  touches the actual edited text or cursor offsets. Covers Kotlin/Java/JS/
  TS/C-family, XML/HTML, JSON, YAML, Properties, Shell, Python, and
  Markdown (via the preview instead). Colors live in
  `ui/theme/SyntaxColors.kt` with separate light/dark sets.
- `CodeEditorField.kt` — the editor itself: a fixed line-number gutter
  beside a non-wrapping, horizontally-scrolling text field, both riding one
  shared vertical `ScrollState` so they can't drift out of sync. Also has
  `lineColForOffset` / `offsetForLineCol` for the Ln/Col indicator and
  "Go to line" jump.
- `MarkdownPreview.kt` — a hand-rolled renderer covering the Markdown
  subset that covers most real READMEs (headers, bold/italic, inline +
  fenced code, blockquotes, lists, links, rules). Not full CommonMark.

`FileEditorScreen.kt` changes:
- Undo/redo, coalesced by a 700ms typing-pause window so it undoes "what
  you just typed" rather than one character at a time. History resets on
  file load and isn't persisted — closing the file clears it, same as most
  editors.
- Top bar now shows `<language> · Ln X, Col Y`, live off the cursor
  position — the whole point being that when something reports an error
  at a specific line/column, "Go to line" (in the overflow menu) jumps
  straight there.
- Preview/Edit toggle appears only for `.md` files.
- Kept the top bar to 5 always-visible icons (Undo, Redo, [Preview],
  Save, Push) + one overflow menu (Go to line, Select all) rather than
  letting it regrow into the kind of flat icon pile the Changes screen
  had before.

**Known trade-off**: no wrap + one shared un-virtualized scroll container
means very large files (tens of thousands of lines) will feel heavier to
scroll than a real IDE. Files are already capped at 2 MB before this
editor opens them at all, and syntax highlighting itself is capped at
300k characters (falls back to plain monospace beyond that) — reasonable
for source files and docs, not built for giant generated files.

## Two more editor fixes

- **Real compile break**: a KDoc comment in `MarkdownPreview.kt` described
  inline emphasis using literal markdown syntax — `*italic*/_italic_` —
  and that `*/` in the middle of the sentence closed the comment early.
  Everything after it (including a chunk of real code on a later line) got
  parsed as garbage top-level declarations, which is what CI's wall of
  "Expecting a top level declaration" errors was. Reworded the comment to
  not contain a literal `*/`, and re-audited every comment in the touched
  files for the same trap (all others were clean).
- **Blank editor body**: `CodeEditorField`'s gutter and text field were both
  using `fillMaxHeight()` inside a `Modifier.verticalScroll()` container.
  A scrollable container gives its child an *unbounded* height to measure
  against (that's how it knows there's more content than the viewport) —
  asking a child to "fill" an unbounded height doesn't error, it just
  breaks the layout silently. The gutter's numbers still happened to
  paint because `Text` doesn't depend on that measurement, but the actual
  code area collapsed. Fixed by letting both size to their natural
  content height instead (no `fillMaxHeight()` anywhere in that subtree)
  inside a properly bounded outer `Column` — this is also the standard,
  reliable pattern for a scrollable code editor in Compose.
- Added a quick-symbol toolbar above the keyboard (`→ { } ( ) [ ] ; = " '
  < > / \ + - * _ # @ ! & | :`) — the punctuation mobile keyboards bury
  behind a symbols layer, one tap away instead. Matches the reference
  MT Manager screenshot's bottom toolbar.

## Two more compile fixes

- `MarkdownPreview.kt`: missing import for `androidx.compose.foundation.layout.width` (used by the blockquote's `Spacer`).
- `SyntaxHighlighting.kt`: the `Rule` data class had `colorOf: (SyntaxColorSet) -> Color` as its *second* parameter and `italic: Boolean = false` last. Trailing-lambda call syntax (`Rule(pattern) { it.type }`) always binds to the *last* parameter — so every one of those calls was actually trying to pass the lambda as `italic`, not `colorOf`, which is why the error log showed a `colorOf` parameter never being filled everywhere. Reordered so `colorOf` is genuinely last, and updated the `italic = true` call sites to `Rule(pattern, italic = true) { ... }`.
