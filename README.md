# Alpha Clone

A minimal Android git client: clone, view changes, stage/commit, and push —
nothing else. Built on JGit (pure Kotlin/Java, no native code), and built
entirely through GitHub Actions — no PC, no Android Studio required.

## How to build it

Push this repo to GitHub, then:

- **Every push** → `.github/workflows/ci.yml` runs automatically: a fast
  debug build, just to catch compile errors quickly. Check this one first
  if a push looks like it broke something.
- **To get an installable release APK** → either push a tag like `v1.0.0`,
  or go to the repo's **Actions** tab → **Build signed release APK** →
  **Run workflow** (manual trigger, no tag needed). This produces a
  shrunk, signed release APK, uploaded as a workflow artifact — and if it
  was a tag push, also attached to a new GitHub Release.

Download the APK from either the workflow run's "Artifacts" section or the
release page, then install it on your phone (you'll need to allow installs
from that source once, same as any sideloaded APK).

## About the signing key — read this before you rely on it

The release workflow generates a **new, random signing keystore on every
run** via `keytool`. This is the simplest possible setup — no secrets to
configure, nothing to lose — but it comes with one real trade-off:

**Every release build is signed with a different key.** Android is fine
installing an APK signed with any valid key, so this works great for
"build it, install it on my phone." But it means:

- You can't publish through the Play Store this way (Play requires the same
  key on every update, forever).
- If you install a build from one workflow run, then later try to install
  an *update* built from a different run, Android will refuse to install
  over the old one (mismatched signature) — you'd need to uninstall the old
  APK first, then install the new one. You won't lose your cloned repos or
  credentials from this, since those live in the app's data directory tied
  to the app, but Android does treat it as a fresh install each time.

If that becomes annoying, the fix is to generate **one** keystore yourself
and reuse it every time, instead of a fresh one per run:

1. Run this once, on any machine with a JDK (or ask me to walk you through
   doing it via a one-off GitHub Actions job):
   ```
   keytool -genkeypair -v -keystore alphaclone-release.keystore -alias alphaclone \
     -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Base64-encode the resulting file and save it as a GitHub Actions secret
   (e.g. `RELEASE_KEYSTORE_BASE64`), along with secrets for the store
   password, key alias, and key password.
3. Swap the "Generate signing keystore" step in
   `.github/workflows/release.yml` for one that decodes the secret back
   into a file instead of calling `keytool` fresh each time.

Until you need that, the auto-generated approach is genuinely fine — this
is exactly the kind of thing to defer until it actually bothers you.

## Why there's no `gradle-wrapper.jar` in this repo

Every Gradle project normally ships a small binary `gradle-wrapper.jar` so
`./gradlew` works without Gradle being pre-installed. I didn't include one
here on purpose: generating a correct one requires either a working Gradle
install with internet access to `services.gradle.org` (not available in the
environment I built this in), and fabricating one by hand risked shipping
something subtly broken — worse than just not including it.

Instead:

- **CI never needs it.** Both workflows install Gradle 8.7 directly via
  `gradle/actions/setup-gradle`, and call `gradle` (not `./gradlew`) in
  every step. This is a completely normal, supported way to run Gradle in
  CI.
- **`gradlew` / `gradlew.bat` are still included** (the plain-text scripts),
  along with `gradle/wrapper/gradle-wrapper.properties`, for the day you
  open this in Android Studio — Studio detects the missing jar and
  regenerates it automatically on first sync. You don't need to do
  anything for that to happen.

## What works, fully

- **Clone** — HTTPS URL + optional branch + optional saved credential
- **Status** — staged vs. unstaged files, with add/modify/delete markers
- **Stage / unstage** — tap a file to move it between lists, or "Stage all"
- **Commit** — message + author name/email
- **Push** — to `origin`, with rejected/non-fast-forward updates reported
  as real errors instead of silently "succeeding"
- **Pull** — fetch + merge (fast-forward) in one step
- **Credentials** — GitHub username + Personal Access Token, encrypted at
  rest via Android Keystore

One honest limitation: a pull that hits a real merge conflict (not just a
fast-forward) surfaces as an error message — this minimal app doesn't have
conflict-resolution UI. You'd resolve that elsewhere and come back.

## Project layout

```
AlphaClone/
├── .github/workflows/
│   ├── ci.yml          ← fast debug build on every push
│   └── release.yml     ← signed + shrunk release APK, on tag or manual run
├── settings.gradle.kts, build.gradle.kts, gradle.properties
├── gradlew, gradlew.bat, gradle/wrapper/gradle-wrapper.properties
└── app/
    ├── build.gradle.kts     ← JGit dependency, release signing, shrinking
    ├── proguard-rules.pro   ← keep-rules for JGit/Room/coroutines under R8
    └── src/main/
        ├── AndroidManifest.xml
        ├── res/                ← theme, launcher icon, strings
        └── java/willykez/alphaclone/
            ├── App.kt, MainActivity.kt
            ├── git/                   ← GitEngine.kt (JGit), GitResult, GitFileEntry
            ├── data/
            │   ├── db/                ← Room: RepoEntity, CredentialEntity, DAOs
            │   └── repository/        ← RepoRepository, CredentialRepository, TokenCrypto
            ├── navigation/            ← AppNav.kt, 4 routes, slide transitions
            └── ui/
                ├── theme/             ← Color.kt, Type.kt, Theme.kt
                └── screens/           ← repolist, clone, changes, credential
```

## Errors found and fixed in this pass

A few real bugs were caught and fixed while preparing this build:

- `CloneScreen.kt` used `Spacer(Modifier.width(...))` without importing
  `Modifier.width` — a genuine compile error.
- Two ViewModels referenced `application.credentialRepository` without
  casting `application` to `App` first (Kotlin doesn't smart-cast a
  constructor parameter from a separate `val` expression) — another
  compile error.
- `GitEngine.push()` didn't check per-ref push results — JGit's `push()`
  doesn't throw on a rejected (non-fast-forward) update by default, so a
  rejected push would have silently reported "success." Now it inspects
  each `RemoteRefUpdate` and fails loudly if anything wasn't accepted.
- `CloneViewModel` left the freshly-cloned `Git` handle open after a
  successful clone instead of closing it — a minor resource leak, now
  closed immediately after the repo row is saved.
- Removed a dead, unused `App.instance` singleton and a stale doc comment
  still referencing libgit2 from the earlier native-based version.

## Where to pick this up next

1. Push this to a new GitHub repo.
2. Watch the CI workflow pass on that first push (confirms the whole
   project compiles).
3. Manually trigger the release workflow from the Actions tab, download
   the APK from its artifacts, and install it on your phone.
4. Add a GitHub PAT (github.com/settings/tokens, `repo` scope) as a
   credential, then test clone → stage → commit → push against a repo you
   control.
5. Test pull against a repo where you've made a remote-only change.
6. From there, it's yours to extend or keep exactly this minimal.
