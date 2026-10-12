# Readeck Share

A tiny Android app that adds **Readeck** to the system share menu. Share a link from any app and it's sent to your [Readeck](https://readeck.org) server. You stay in the app you shared from.

This is an unofficial app and isn't affiliated with the Readeck project.

## Features

- **No taps needed.** Picking "Readeck" in the share menu saves the link right away. A small sheet at the bottom of the screen shows *Saving… → Saved ✓* and then closes itself.
- **Works offline.** If you share while offline, or the server can't be reached, the link is queued and sent once you're back online. You get a short toast when it arrives.
- **See what's waiting.** Links that haven't reached Readeck yet are listed at the top of the app, along with why (waiting for a connection, retrying, or failed, e.g. because of a bad token). You can retry or remove each one, or tap it to open it in your browser.
- **Default labels.** Optionally, set labels that are added to every link you share.
- **Small and private.** Plain Android views, no tracking or analytics, and one dependency (AndroidX WorkManager). The app only talks to your own server.

Requires Android 8.0 or newer.

## Install

Download the latest `readeck-share-vX.Y.Z.apk` from [Releases](../../releases/latest) and open it on your phone. You'll need to allow installing apps from your browser or file manager. Newer releases install over older ones.

## Set up

1. In Readeck, go to **Settings → API tokens** and create a token that is allowed to add bookmarks.
2. Open **Readeck Share** and enter your server URL (for example `https://readeck.example.com`) and the token. You can also add default labels.
3. Tap **Save and test connection**. You should see "Connected as *your-username*".

Now share any link and pick **Readeck**.

## Privacy

- The app sends only the shared URL, the title provided by the sharing app (if any) and your default labels, and only to the server you configure. Readeck then fetches the article itself.
- The server URL and API token are stored in the app's private storage. They're excluded from Android backups and device-to-device transfers, so the token doesn't leave the phone.
- Plain `http://` servers are allowed, which is handy for a Readeck instance on your home network. Over `http://`, the token is sent unencrypted, so use `https://` for anything reachable from the internet.

## Building

You need JDK 17 and the Android SDK.

```sh
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest lintRelease
```

Every push runs these checks on GitHub Actions, and the debug APK is attached to each run as an artifact.

## Releasing

Releases are built and signed by GitHub Actions when you push a version tag.

**One-time setup: create a signing key** and keep a backup somewhere safe (with its password). Every future release must be signed with the same key, or people won't be able to update.

`keytool` comes with Java. If you don't have it, install a JDK (on Windows: `winget install EclipseAdoptium.Temurin.17.JDK`, then open a new terminal), or use the copy bundled with Android Studio in its `jbr/bin` folder.

On Linux or macOS:

```sh
keytool -genkeypair -v -keystore release.jks -alias readeck-share \
  -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 release.jks > release.jks.b64   # on macOS: base64 -i release.jks -o release.jks.b64
```

On Windows (PowerShell):

```powershell
keytool -genkeypair -v -keystore release.jks -alias readeck-share -keyalg RSA -keysize 4096 -validity 10000
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$PWD\release.jks")) | Set-Content release.jks.b64
```

`keytool` asks for a password and some name details (these can be anything). If it doesn't ask for a separate key password, the key password is the same as the keystore password. Once the secrets below are saved, you can delete `release.jks.b64`.

Then add these repository secrets under **Settings → Secrets and variables → Actions**:

| Secret | Value |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | contents of `release.jks.b64` |
| `RELEASE_KEYSTORE_PASSWORD` | the keystore password |
| `RELEASE_KEY_ALIAS` | `readeck-share` (or the alias you chose) |
| `RELEASE_KEY_PASSWORD` | the key password (same as the keystore password unless you set a different one) |

**For each release:**

1. Bump `versionCode` (by 1) and `versionName` in `app/build.gradle.kts`, and merge to `main`.
2. Tag the commit and push the tag:
   ```sh
   git tag v1.0.0
   git push origin v1.0.0
   ```
3. The **Release** workflow checks that the tag matches `versionName`, builds and signs the APK, and publishes it as a GitHub release with generated notes.

## License

[AGPL-3.0-only](LICENSE), the same license as Readeck.
