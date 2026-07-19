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

## One more compile fix
`rulesFor(lang)`'s `when` only covered 8 of the 9 `CodeLanguage` values —
`MARKDOWN` had no branch (markdown is rendered through the separate
Preview mode, so it was easy to forget the raw-editor path still calls
into this for Edit mode). Added `MARKDOWN -> emptyList()`, same as
`PLAIN` — markdown source is edited as plain monospace text.

## Markdown preview: tables + raw HTML badges, and a real gutter-width bug

- **Gutter taking over the whole screen**: the gutter `Column`'s width was
  set with `widthIn(min = ...)` — a *minimum* only, no maximum. Every line
  number inside it uses `fillMaxWidth()` to right-align, which gives
  Compose no smaller natural width to prefer — with nothing capping it,
  the gutter would expand to fill whatever width the row handed it,
  shoving the actual text field off-screen to the right (exactly what you
  saw). Changed to a fixed `.width(...)` instead of an open-ended minimum.
- **Markdown tables**: `| a | b |` header + `|---|---|` separator rows are
  now parsed and rendered as an actual bordered table, not literal pipe
  text.
- **Raw HTML in the README**: the badge block
  (`<p><img alt="..." src="...">...</p>`) is now recognized as one unit
  (its `<img>` tags can span multiple source lines since the URLs wrap) —
  each badge's `alt` text renders as a small chip. A `<p>` with real text
  and no images strips the tags and shows the text. A stray leftover tag
  on its own line is dropped instead of showing as raw `<...>` text. This
  isn't general HTML rendering — just enough to stop badge blocks from
  showing as tag soup, which is the common case in real READMEs.

## Editor / code-viewing depth: find-in-file, better diffs, TODO list

- **Find & replace in file** (editor overflow menu → "Find…"): live match
  highlighting as you type, next/prev, case-sensitivity toggle, replace-one
  and replace-all. Matches are computed in `FileEditorViewModel` and
  rendered by extending `highlightText`/`SyntaxHighlightTransformation`
  (in `SyntaxHighlighting.kt`) with an independent match-background pass —
  it applies even in `PLAIN`/`MARKDOWN` files, since find should work
  regardless of syntax-highlighting support. New tokens
  `matchHighlight`/`currentMatchHighlight` added to `SyntaxColorSet`.
- **Diff viewer rewrite** (`DiffScreen.kt`): added old/new line-number
  gutters, syntax highlighting on the actual code content (reusing the
  editor's `highlightText`), collapsible per-file sections for multi-file
  commit diffs, and — the real bug fix in here — replaced hardcoded
  `Color(0xFF1A3A1A)`-style dark-only backgrounds with translucent tints
  over the theme surface, so it's correct in light mode too (same class of
  bug as the earlier GlassCard/TopAppBar issues). Diff parsing
  (`DiffHunk`/`DiffLine`/`parseDiff`) moved out to `git/DiffParsing.kt` so
  it's shared with hunk staging below, instead of living privately inside
  the diff screen's ViewModel.
- **Problems list** (new `ProblemsScreen`, reachable from Changes → ⋮ →
  Manage → "Problems"): scans the repo's working tree (skipping `.git` and
  binary-looking extensions, capped at 5,000 files / 1 MB each) for
  `TODO`/`FIXME`/`XXX`/`HACK` markers, lists them grouped by file, and
  tapping one opens the editor at that exact line. This needed the editor
  route to gain an optional `?line=` param
  (`Routes.editor(id, path, line)`) — existing call sites are unaffected
  since it defaults to 0 (no jump).

## Git workflow depth: hunk-level staging ("git add -p")

Added to `GitEngine.kt`: `stageHunk(git, path, hunk)` and
`discardHunk(git, path, hunk)`, wired into the diff viewer as **Stage** /
**Discard** buttons on each hunk header — but only when viewing the
*unstaged* diff (index vs. working tree), where hunk actions are
unambiguous.

**How it works**: JGit doesn't expose a hunk-granularity "apply" primitive,
so this hand-reconstructs the target file content — reads the current
index blob (or working-tree file, for discard), applies just the chosen
hunk's added/removed/context lines at the hunk's recorded old-side
position, and writes the result back as a new index entry (via
`DirCacheEditor` + a freshly inserted blob) or straight to the working-tree
file. Every other staged/unstaged change to that file is left untouched.
After either action, the diff view re-fetches from git rather than
patching local state — hunk actions shift every later hunk's line numbers
in that file, and re-fetching is the only way to guarantee the UI matches
what git actually now has.

**This is the least-tested part of everything built today** — it's
hand-rolled patch application, not a single well-worn JGit call, and it
writes directly to the index/working tree. Test it against a throwaway
repo (or a repo you don't mind restoring from a backup/remote) before
trusting it on real uncommitted work. If a hunk action ever produces
unexpected content, `git status`/`git diff` from a regular terminal will
show exactly what happened, and `git checkout -- <file>` /
`git reset HEAD -- <file>` recover cleanly either way since nothing here
touches commits or refs.

## Not in this pass: interactive rebase

Scoped but deliberately not rushed into this same batch — reordering/
squashing/dropping commits via JGit's `RebaseCommand.InteractiveHandler`
plus a reorderable commit-list UI is a meaningfully larger, higher-stakes
piece (it rewrites history, not just working-tree/index state), and it
benefits from its own focused pass rather than being tacked onto an
already-large set of changes. Next up.

## This session: local repos, GitHub repo management, search, notifications, visual pass, bulk actions

### Local repo detection (new capability — not from cloning)
- `data/LocalRepoScanner.kt` — walks the app's repos folder (2 levels deep,
  tolerating some manual subfolder organizing) for directories containing
  `.git` that aren't yet tracked in the database, and reads each one's
  `origin` URL straight out of `.git/config` (best-effort — empty string if
  there isn't one, which is fine, it can be added later via the Remote
  screen).
- Wired into `RepoListViewModel.init {}` — runs automatically whenever the
  repo list loads (i.e. on app open), auto-registers what it finds, and
  shows a snackbar ("Found and added N local repos") rather than staying
  silent. No manual "scan" button was added — the top bar already had 5
  icons (Sort, Discover, Credentials, Settings, Clone) and auto-scan-on-load
  covers the described use case (drop a repo in, reopen the app) without
  adding a 6th.

### GitHub repo create/delete (extends the existing Discover→GitHub integration)
- `GitHubApi.kt`: generalized the GET-only HTTP layer into a shared
  `httpRequest(url, method, token, jsonBody)` used by the existing search/
  list-mine calls too. Added `createRepo` (POST), `updateRepo` (PATCH,
  rename/description/visibility), `deleteRepo` (DELETE, handles GitHub's
  204 No Content response).
- Discover screen: "New repo" button next to "My Repos" opens a sheet
  (name, description, private toggle, "clone to this device" toggle —
  on by default, since the point of a git *client* creating a repo is
  usually to start working in it locally right away). Each row in "My
  Repos" now also gets a delete button, gated behind a confirmation
  dialog that's explicit about scope: deleting on GitHub is separate
  from any local clone you have.
- **Known caveat, not a bug**: `deleteRepo` requires a token with the
  `delete_repo` scope specifically, which most personal access tokens
  don't have by default even if push/pull work fine — GitHub treats
  deletion as a separately opt-in permission. A token missing it gets a
  403 from GitHub, and that message is passed straight through rather
  than papered over, so it's clear what's actually wrong rather than
  looking like a generic failure.

### Full-text search across a repo (new — distinct from find-in-file, which only searches the currently open file)
- New `ui/screens/search/RepoSearchScreen.kt`, reachable from Changes → ⋮
  → Manage → "Search repo". Walks the repo (same skip-.git/skip-binary-
  extensions/cap-file-count approach as the Problems scan — kept as its
  own copy for now rather than sharing a walker utility, to avoid touching
  the already-working Problems scan mid-session; a reasonable follow-up
  cleanup, not a functional gap). Triggered explicitly (search button/IME
  action), not per-keystroke — walking every file in a repo is too heavy
  to re-run on every character the way single-file find can. Tapping a
  result opens the editor at that exact line (reuses the `?line=` param
  added for the Problems list).

### Background sync notifications
- `sync/SyncNotifications.kt` — one summary notification per sync run
  (not one per repo) when a fetch finds new commits available to pull.
  Deliberately doesn't claim to notify on "conflicts hit," since this
  worker only ever fetches, never merges — there's nothing to conflict
  with. `POST_NOTIFICATIONS` (API 33+ runtime permission) is requested
  from Settings when background sync is switched on, not at app launch;
  declining it doesn't block sync itself, only the notification.

### GlassCard visual pass — Log, Branches, Stash, Tags, Remote, Credential, Conflicts, Discover
All eight screens `MERGE_NOTES.md` had flagged as still using the old flat
`Card`/`surfaceVariant` styling now use the shared `GlassCard`. Two got a
semantic accent color while I was in there: the current branch in
Branches (blue), and unresolved files in Conflicts (red). Blame and
Settings were deliberately left alone — Blame renders as continuous
attributed code lines, not a list of items, and Settings is a plain form;
neither would actually look better as a stack of cards.

**Bonus bug fix**: Branches' current-branch highlight was using `PlumSoft`
— a hardcoded dark-only color alias — unconditionally, the same class of
light/dark bug fixed earlier in TopAppBars and GlassCard itself. It's an
adaptive `CommandBlue` accent now.

### Multi-select bulk actions in File Explorer
Long-press any file/folder to enter selection mode; tap toggles further
selections. Top bar swaps to a selection-mode bar (count, Stage, Delete,
Cancel) while active. Both bulk actions open the repo once and loop
per-path rather than once per file — meaningfully faster for a large
selection, and each reports a proper "N succeeded, M failed" outcome
rather than failing silently partway through.

## Still ahead
**PR creation/viewing** — natural next extension of the `GitHubApi` work
above (list/view/create PRs, using the same auth/token plumbing).
**Interactive rebase** and **submodule support** remain the two biggest,
highest-stakes items — both touch commit history / repo structure more
deeply than anything shipped today, and both deliberately still deserve
their own focused pass rather than being appended to an already-large
batch.
