# Readeck Share

A tiny Android app that adds **Readeck** to the system share menu. Share a link from any app and it's sent to your [Readeck](https://readeck.org) server. You stay in the app you shared from.

- **No taps needed.** Picking "Readeck" in the share menu saves the link right away. A small sheet at the bottom of the screen shows *Saving… → Saved ✓* and then closes itself.
- **Works offline.** If you share while offline, or the server can't be reached, the link is queued and sent once you're back online. You get a short toast when it arrives.
- **See what's waiting.** Links that haven't reached Readeck yet are listed at the top of the app, along with why (waiting for a connection, retrying, or failed, e.g. because of a bad token). You can retry or remove each one, or tap it to open it in your browser.
- **Default labels.** Optionally, set labels that are added to every link you share.
- **Small.** Plain Android views, no tracking, and one dependency (AndroidX WorkManager).

## Install

1. Open the [Actions tab](../../actions/workflows/build.yml), pick the latest successful run, and download the `readeck-share-debug` artifact.
2. Unzip it and install `app-debug.apk` on your phone. You'll need to allow installing apps from unknown sources.

Or build it yourself with `./gradlew assembleDebug` (requires the Android SDK). The APK ends up in `app/build/outputs/apk/debug/`.

> CI builds are signed with a throwaway debug key that changes between runs. To update to a newer CI build, you may need to uninstall the old one first.

## Set up

1. In Readeck, go to **Settings → API tokens** and create a token that is allowed to add bookmarks.
2. Open **Readeck Share** and enter your server URL (for example `https://readeck.example.com`) and the token. You can also add default labels.
3. Tap **Save and test connection**. You should see "Connected as *your-username*".

Now share any link and pick **Readeck**.

## Notes

- The token is stored in the app's private storage. App backup is turned off so the token doesn't leave the device.
- Plain `http://` servers are allowed, which is handy for a Readeck instance on your home network.
- Readeck fetches the article itself. The app only sends the URL, the title from the sharing app (if any), and your labels.

## License

[AGPL-3.0-only](LICENSE), the same license as Readeck.
