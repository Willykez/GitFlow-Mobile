# GitFlow Mobile

A full Git client for Android — clone, stage, commit, push, resolve conflicts,
and edit files, all from your phone. Built with Jetpack Compose, JGit, and a
dark "command center" design system.

<p>
  <img alt="Platform" src="https://img.shields.io/badge/platform-Android-3ddc84?logo=android&logoColor=white">
  <img alt="Min SDK" src="https://img.shields.io/badge/minSdk-24-blue">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7f52ff?logo=kotlin&logoColor=white">
  <img alt="License" src="https://img.shields.io/badge/license-MIT-lightgrey">
</p>

## Features

- **Repo management** — clone over HTTPS, browse your repos, see uncommitted-change counts at a glance
- **Stage & commit** — swipe to stage/discard, commit with a message, push in one tap
- **Sync** — fetch, pull (merge or rebase), push, and combined sync, including force variants when you need them
- **Conflict resolution** — pick "ours"/"theirs" per file, or jump into the built-in editor to resolve by hand
- **History** — commit log, cherry-pick, amend, squash
- **Branches, tags & remotes** — full CRUD on all three
- **File explorer & editor** — browse the working tree and edit text files directly
- **Blame view** — see who changed each line and when
- **.gitignore editor** — with quick-insert templates for common stacks
- **Discover** — search public GitHub repos and clone straight from search results
- **Credentials** — store personal access tokens per-remote, encrypted at rest via Android Keystore
- **Background sync** — optional scheduled fetch/pull so repos stay current without opening the app
- **Adaptive light & dark themes** — a dark "cockpit" look by default, with a full light theme for accessibility/system preference

## Screenshots

*(Add screenshots or a screen recording here — drop images in a `/docs` or
`/screenshots` folder and reference them, e.g. `![Home](docs/home.png)`.)*

## Tech stack

| Layer | Choice |
|---|---|
| UI | Jetpack Compose + Material 3 |
| Git engine | [JGit](https://www.eclipse.org/jgit/) |
| Persistence | Room |
| Background work | WorkManager |
| Async | Kotlin Coroutines + Flow |
| Navigation | Jetpack Navigation Compose |

## Getting started

### Requirements
- Android Studio (Koala or newer recommended)
- JDK 17
- Android SDK 34

### Build locally
```bash
git clone https://github.com/<your-username>/<your-repo>.git
cd <your-repo>
```
Open the project in Android Studio and let it sync — Android Studio
regenerates the Gradle wrapper jar automatically. Then **Run ▶** on a device
or emulator (min SDK 24 / Android 7.0+).

### Build from the command line
```bash
gradle assembleDebug
```
> This project intentionally doesn't check in the binary
> `gradle-wrapper.jar`. Android Studio regenerates it on sync; from a plain
> shell, install Gradle 8.7 yourself and call `gradle` directly (see
> `.github/workflows/ci.yml` for the exact setup used in CI).

## CI/CD

- **`ci.yml`** — runs on every push/PR: a fast debug build to catch compile
  errors early.
- **`release.yml`** — runs on a `v*` tag push (or manually via **Run
  workflow**): builds a signed, shrunk release APK and attaches it to a
  GitHub Release. See **Signing** below for which key it signs with.

## Signing

`release.yml` signs with a **persistent keystore** if you've set one up
(see below), and falls back to a **throwaway keystore** — freshly
generated, different every run — if you haven't. The throwaway path is
fine for installing on your own device(s), but it means every run signs
with a different key: you can't update an existing install in place
across runs, and it can never go to the Play Store (which requires the
same signing key on every update, forever).

### Setting up a persistent keystore

1. Generate one, on your own machine — not in CI, and don't share the
   password with anyone (including in an issue/PR):
   ```bash
   keytool -genkeypair -v -storetype PKCS12 \
     -keystore gitflowmobile-release.keystore \
     -alias gitflowmobile \
     -keyalg RSA -keysize 2048 -validity 10000 \
     -storepass YOUR_PASSWORD -keypass YOUR_PASSWORD \
     -dname "CN=GitFlow Mobile, OU=GitFlow Mobile, O=GitFlow Mobile, L=Unknown, S=Unknown, C=US"
   ```
   Use the *same* password for `-storepass` and `-keypass` — modern
   `keytool` defaults to PKCS12, which requires them to match.
2. Base64-encode it:
   - macOS: `base64 -i gitflowmobile-release.keystore -o keystore_base64.txt`
   - Linux: `base64 -w0 gitflowmobile-release.keystore > keystore_base64.txt`
   - Windows (PowerShell): `[Convert]::ToBase64String([IO.File]::ReadAllBytes("gitflowmobile-release.keystore")) | Out-File keystore_base64.txt`
3. Add three repo secrets (**Settings → Secrets and variables → Actions →
   New repository secret**):

   | Secret | Value |
   |---|---|
   | `RELEASE_KEYSTORE_BASE64` | contents of `keystore_base64.txt` |
   | `RELEASE_KEYSTORE_PASSWORD` | your password |
   | `RELEASE_KEY_ALIAS` | `gitflowmobile` (or whatever alias you used) |

4. **Back up the keystore file and password somewhere outside GitHub too**
   (a password manager, not just the repo secret). There's no recovery if
   you lose either — you'd have to ship future releases as a new app
   identity, breaking updates for anyone who already installed a version
   signed with the old key.

Once all three secrets are set, `release.yml` picks them up automatically
— no further changes needed.

## Permissions

| Permission | Why |

|---|---|
| `INTERNET` / `ACCESS_NETWORK_STATE` | Clone, fetch, push, pull over the network |
| All-files access (Android 11+) / `WRITE_EXTERNAL_STORAGE` (Android 10-) | Repos are stored in a **public** folder (`/storage/emulated/0/GitFlowMobile/repos`) instead of the app's private sandbox, so any file manager, other app, or PC-over-USB can reach your files directly |

## Project structure

```
app/src/main/java/willykez/gitflowmobile/
├── data/            # Room entities/DAOs, repositories, encrypted credential storage
├── git/             # JGit wrapper — clone/fetch/pull/push/commit/etc.
├── sync/            # WorkManager background sync
├── navigation/      # Nav graph (AppNav.kt)
└── ui/
    ├── components/  # Shared design-system pieces (GlassCard, etc.)
    ├── theme/       # Color/Theme/Type — light & dark schemes
    └── screens/     # One package per screen (repolist, changes, explorer, editor, …)
```

## Design system

The app uses a shared `GlassCard` component (a translucent card with a
hairline border and an optional status-accent bar) across repo, file, and
credential rows, and a two-color accent system: **command blue** for
navigation/primary actions, **gold** reserved specifically for commit/push —
so gold always signals "this touches the remote." Everything is driven off
`MaterialTheme.colorScheme`, so it adapts correctly between dark ("cockpit")
and light mode.

## Contributing

Issues and PRs welcome. If you're proposing a UI change, a before/after
screenshot or short screen recording speeds up review a lot.

## License

MIT — see [LICENSE](LICENSE) (add one if it isn't already in the repo).
