# CinematicTV

A tvOS-style launcher for Google TV / Android TV, built for the ETOE Seal Pro 2026 projector:
big rounded app cards that lift and shine on focus, a blurred "top shelf" backdrop of the focused app,
a clock, and a remote-only UI at 1080p. It never modifies firmware, never disables system apps, and
Google TV stays installed and one button away.

The signed, ready-to-install file is published on the
[Releases page](https://github.com/El-Mostafa01/REM-launcher/releases/latest) as `REM-launcher.apk`.
This link always serves the newest version:
https://github.com/El-Mostafa01/REM-launcher/releases/latest/download/REM-launcher.apk

---

## Install with Downloader (easiest)

1. On the projector, open **Downloader** by AFTVnews (free on Google Play).
2. Enter the code **3741278** and press **Go**. It always points to the latest release.
3. When the download finishes, press **Install**. The first time, Android asks you to allow Downloader
   to install apps: choose **Settings**, enable it, press **Back**, then **Install**.
4. Press **Open**.

## Install from a USB drive (no PC or ADB on the projector)

1. **On your computer**, copy `REM-launcher.apk` to a USB drive (FAT32 or exFAT, any folder).
2. **Plug the drive into the projector.**
3. **Open your file manager** on the projector and browse to the USB drive.
   No file manager installed? Install a free one from Google Play first, e.g. *File Commander* or *X-plore*.
4. **Select `REM-launcher.apk`.**
5. **First time only:** Android says installing from this app is blocked. Choose **Settings**, turn on
   **Allow from this source** for the file manager, then press **Back**.
   *If that toggle doesn't appear:* Settings → System → About → click **Android TV OS build** 7 times
   (enables Developer options), then Settings → Apps → Security & restrictions → **Unknown sources** →
   enable your file manager. Both are standard settings menus, nothing on the device is altered.
6. **Press Install**, then **Open**. CinematicTV starts at once and shows its welcome screen.

Later, open it from Google TV's **Apps** row. Tip: long-press it and choose *Add to favorites* (or move
it to the front) so it's always first.

## Using it with the remote

| Remote button | What it does |
|---|---|
| Arrows | Move between apps (the card lifts and the backdrop changes) |
| OK | Open the app |
| Back | Close a panel, or return to Google TV |
| Menu (☰), or the **Settings** tile at the end | CinematicTV settings |
| Home | Google TV home (see "Home button" below) |

New apps you install appear automatically the next time CinematicTV comes back to the screen.

## Set as Home (only where the device allows it)

CinematicTV checks how *this* device resolves the Home button and only shows **Set CinematicTV as Home**
if a sideloaded app can actually become Home there. On most Google TV devices it can't: Google's own
launcher carries a *system priority* that Android checks before any user choice, and the only way past
that is disabling the stock launcher with ADB, which CinematicTV deliberately never does. In that case the
welcome and Settings screens say so and CinematicTV runs as a normal app.

## Going back to Google TV (revert)

- **Back** on the CinematicTV home screen → Google TV.
- **Settings tile → Go to Google TV home** → opens the stock launcher directly, even if CinematicTV is Home.
- If you did set it as Home: **Settings tile → App info → Clear defaults**.
- To remove it completely: **Settings tile → App info → Uninstall** (or Google TV Settings → Apps →
  CinematicTV → Uninstall). Nothing else on the projector changes.

## Device limitations, honestly

- **Plugging in a USB drive can't launch or install an APK by itself.** Android has no autorun: mounting a
  drive only makes its files readable, and Android never executes code from storage. Every install goes
  through the system Package Installer, which needs an app *you* open to hand it the file, your one-time
  "allow from this source" permission, and your tap on **Install**. Silent installs are reserved for apps
  signed with the device firmware key or for managed (MDM / device-owner) devices. A helper app could
  watch for the USB drive and pop up the install prompt automatically, but you'd still have to confirm it,
  and you'd first have to install that helper the same manual way. The file-manager route above is the
  shortest path a stock device allows.
- **Home button.** See above: on Google TV it normally keeps opening Google TV. Some people use an
  accessibility "button mapper" app to send a remote button to CinematicTV; that's a third-party option
  at your own discretion, not part of this package.
- **Google TV extras aren't replicated:** recommendation rows, "Continue watching", profiles, the Google
  Assistant button (it still opens Assistant), and the projector's own picture/keystone/focus menus
  (still on the remote's settings button).
- **Only TV apps are listed** (apps that declare Android TV support), the same set Google TV shows.
  Apps without a TV banner get an icon card.
- **Backdrop blur needs Android 12+.** Older systems show the backdrop dimmed, without blur.
- **Updates must use the same signing key** (`cinematictv-release.jks`). An APK signed with any other key
  can't update this install; you'd have to uninstall first. Back up the key (see below).

## What was verified

On the official **Google TV (Android 14) emulator at 1920×1080, 320 dpi**, using remote key events:
fresh install and launch from the TV launcher entry, welcome screen, app grid (Live TV, YouTube, Play
Store…), arrow navigation across rows and edges, OK opens apps, Back returns from apps and closes panels,
Menu opens Settings, *Go to Google TV home*, *App info*, and the Home key still going to Google TV.
The emulator also confirmed the Home rule: Google TV's launcher is registered at priority 2 and a sideloaded
app at 0, so *Set as Home* is correctly hidden there. The signed APK passes `apksigner verify` (v2 scheme).

**Not verified:** the physical ETOE projector (its remote, file manager and Android build), and the
file-manager install screens. The emulator has no file manager, and Android's installer rejects any source
without the install permission, which is the same reason a file manager is needed on the device.

## Build from source

Requirements: JDK 17, Android SDK with *platforms;android-35* and *build-tools;35.0.0*
(or just open the folder in Android Studio).

```bash
gradlew.bat test assembleRelease
```

The signed APK lands in `app/build/outputs/apk/release/app-release.apk`. Publish it as `REM-launcher.apk`
on a new GitHub Release (bump `versionCode` first) and the `latest/download` link above picks it up.
Signing reads `~/.rem-launcher-signing/keystore.properties` and its `.jks`, kept outside this repository.
Without them the release build is unsigned.

**Back up the `~/.rem-launcher-signing/` folder somewhere private.** It is the only way to ship updates
that install over the current version.

## Project layout

- `app/src/main/java/local/cinematictv/MainActivity.kt`: home screen, app grid, welcome and settings panels
- `app/src/main/java/local/cinematictv/Home.kt`: Home-button support detection and Google TV hand-off
- `app/src/test/.../HomeTest.kt`: unit test for the Home-support rule
- `app/src/main/res/`: layout, focus/scrim drawables, banner, strings
