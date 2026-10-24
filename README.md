# REM Launcher

A cinematic, Apple TV-inspired home screen for Google TV / Android TV, built for the ETOE Seal Pro 2026
projector: a REM-branded top bar with pill navigation, a featured-app hero over original artwork,
coloured category tiles, and rows of your real apps that lift and glow on focus. Remote-only, 1080p.
It never modifies firmware, never disables or uninstalls system apps, and Google TV stays one click away.

The signed, ready-to-install file is published on the
[Releases page](https://github.com/El-Mostafa01/REM-launcher/releases/latest) as `REM-launcher.apk`.
This link always serves the newest version:
https://github.com/El-Mostafa01/REM-launcher/releases/latest/download/REM-launcher.apk

(The Android package id is still `local.cinematictv`, its first name, so updates keep installing over it.)

---

## Install with Downloader (easiest)

1. On the projector, open **Downloader** by AFTVnews (free on Google Play).
2. Enter the code **3741278** and press **Go**. It always points to the latest release.
3. When the download finishes, press **Install**. The first time, Android asks you to allow Downloader
   to install apps: choose **Settings**, enable it, press **Back**, then **Install**.
4. Press **Open**. An update installs over the old version and keeps your settings.

## Install from a USB drive (no PC or ADB on the projector)

1. **On your computer**, copy `REM-launcher.apk` to a USB drive (FAT32 or exFAT, any folder).
2. **Plug the drive into the projector.**
3. **Open your file manager** on the projector and browse to the USB drive.
   No file manager installed? Install a free one from Google Play first, e.g. *File Commander* or *X-plore*.
4. **Select `REM-launcher.apk`.**
5. **First time only:** Android says installing from this app is blocked. Choose **Settings**, turn on
   **Allow from this source** for the file manager, then press **Back**.
6. **Press Install**, then **Open**.

## Make REM your Home screen

Open **Settings** in REM (the nav bar, or the Menu ☰ button) → **Default Home Experience**.

- **Use REM Launcher as Default Home** uses Android's own Home selection (the Home role, or the Settings
  Home screen) wherever the device allows it. The switch only shows *on* when the Home button really opens
  REM. On Google TV it explains why it can't, instead of pretending.
- **Home Redirect for Google TV** is the safe alternative for Google TV. It's an optional accessibility
  service you switch on once: **Settings → Guided setup → Open Accessibility settings → REM Home Redirect →
  Enable → OK**. From then on, whenever Google TV's home screen would appear (Home button, leaving an app,
  start-up), REM opens instead. It is only told when that one screen appears, reads no screen content, and
  disables nothing. Android shows its standard warning for every accessibility service when you enable it.
  - Greyed out as a *restricted setting*? Open **App info** for REM → menu (⋮) → **Allow restricted
    settings**, then try again.
  - **Go to Google TV home** (in REM Settings) pauses the redirect until you reopen REM, or for 10 minutes.
  - Turn it off with the same switch in REM, or in Accessibility settings. Stock behaviour returns at once.

### Why Google TV blocks the normal way (tested)

On the official Google TV (Android 14) emulator, Google's launcher (`com.google.android.apps.tv.launcherx`)
registers its Home screen at **priority 2**; sideloaded apps are capped at **0**. Android compares priority
*before* it looks at the Home role or a saved default. So these ADB commands **succeed but change nothing**:

```bash
adb shell cmd role add-role-holder android.app.role.HOME local.cinematictv
```
```bash
adb shell cmd package set-home-activity local.cinematictv/.MainActivity
```

After either one, REM held the Home role, yet the Home key still opened Google TV. The Settings
"Home app" / "Default apps" screens are empty stubs on Google TV. The only way to beat the priority is
disabling the stock launcher, which this project deliberately never does. Hence the Home Redirect.

### Check your own projector (optional, from a computer)

I couldn't connect to your ETOE, so these are the commands to see its real names, not guesses. Enable
**Developer options** (Settings → System → About → click *Android TV OS build* 7 times), then
**Settings → System → Developer options → USB debugging / Wireless debugging**, and from the PC:

```bash
adb connect PROJECTOR_IP:5555
```
```bash
adb shell cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.HOME
```

The list shows every Home screen with its `priority=`. If the stock launcher's priority is above REM's 0,
the Home role can't take over on your projector either: use Home Redirect. If they're equal, REM's
**Use REM Launcher as Default Home** switch will offer the system's own chooser. If the accessibility
toggle stays greyed out, this unlocks it for REM only:

```bash
adb shell appops set local.cinematictv ACCESS_RESTRICTED_SETTINGS allow
```

## Using it with the remote

| Remote button | What it does |
|---|---|
| Arrows | Move; the focused tile lifts with a blue glow |
| Up from the content | Top bar: Home, Video, Music, Apps, Settings, search |
| Left / Right on the hero buttons | Previous / next featured app |
| OK | Open |
| Back | Close a panel, back to Home; on Home, back to Google TV (unless REM is your Home) |
| Menu (☰) | REM Settings |

Rows show real data only: **Recently Opened** (apps you opened from REM), **Your Video Apps**,
**Music & Games** and **All Apps**. Video/Music/Games come from each app's Android category, with a
list of well-known package names as a fallback. New apps appear the next time REM comes back on screen.

## Going back to Google TV (revert)

- **REM Settings → Go to Google TV home** opens the stock launcher directly.
- Turn **Home Redirect** off in REM Settings (or Android Accessibility settings).
- **REM Settings → App info → Uninstall** removes REM completely. Nothing else on the projector changes.

## Device limitations, honestly

- **Plugging in a USB drive can't install an APK by itself.** Android has no autorun, and every install
  needs an app you open (Downloader, a file manager), a one-time "allow" and your tap on **Install**.
- **No real "Continue Watching" from other apps.** Google TV doesn't let a sideloaded launcher read
  Netflix/YouTube watch history or Google's recommendations, so REM shows what it can know itself.
- **Home Redirect may briefly show Google TV** for a split second before REM appears, and after a restart
  it takes over once the system has finished starting.
- **Google TV extras aren't replicated:** profiles, the Assistant button (it still opens Assistant) and the
  projector's own picture/keystone menus.
- **Updates must use the same signing key**, or Android refuses to update the installed app.

## What was verified

On the official **Google TV (Android 14) emulator, 1920×1080 at 320 dpi**, with remote key events:
update from 1.0.0 to 1.1.0 in place, welcome panel, hero carousel, category tiles, rows, Up to the top bar,
Apps and Settings pages, guided setup, REM Home Redirect listed and enabled in Accessibility settings,
then: Home from YouTube → REM, Back on REM stays in REM, leaving an app → REM, Go to Google TV home stays
on Google TV until REM is reopened, REM in front after a reboot, and switching it off restoring stock
behaviour. The Home role and `set-home-activity` tests above ran on the same emulator.

**Not verified:** the physical ETOE projector (its firmware, remote and Accessibility menu may differ),
Downloader's own install screens, and Android's *restricted setting* screen for apps installed by Downloader.

## Build from source

Requirements: JDK 17, Android SDK with *platforms;android-35* and *build-tools;35.0.0*
(or open the folder in Android Studio).

```bash
gradlew.bat test assembleRelease
```

The signed APK lands in `app/build/outputs/apk/release/app-release.apk`. Publish it as `REM-launcher.apk`
on a new GitHub Release (bump `versionCode` first) and the `latest/download` link picks it up.
Signing reads `~/.rem-launcher-signing/keystore.properties` and its `.jks`, kept outside this repository.
**Back up that folder privately.** It is the only way to ship updates that install over this version.

## Project layout

- `MainActivity.kt`: top bar, pages (Home, Video, Music, Games, Apps, Settings), hero, panels
- `Ui.kt`: theme colours, rounded surfaces, focus lift and glow, pill buttons
- `Apps.kt`: TV app list, categories, Recently Opened history
- `Home.kt`: Home-button detection (role, priority, stubs) and Google TV hand-off
- `HomeRedirectService.kt`: the opt-in Google TV Home Redirect
- `res/drawable/hero_*.xml`: original vector artwork; `drawable-*/banner.png`, `ic_launcher.png`: REM logo
- `app/src/test/`: unit tests for the Home rule, app categories and the recents history
