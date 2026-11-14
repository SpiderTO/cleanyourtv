# Aconatic 43HS600AN — debloat + launcher

A clean, Apple-TV-style home screen on an **Aconatic 43HS600AN** (43" Full HD, Thai market), with the
manufacturer bloat disabled. Android TV 14, KTC/MediaTek `mt5867` board, device `stanford`.

Everything is **disabled, never uninstalled**, and every step is reversible. Nothing was rooted.

## What was done

- **Launcher:** the native launcher in `launcher/` built and installed as the default Home
  (`com.gathin.tvhome`), the stock Android TV launcher (`com.google.android.tvlauncher`) disabled.
  - Dock (bottom row, left→right): **Netflix · YouTube · YouTube Kids · AIS Play**.
  - Inputs menu lists **HDMI 1, HDMI 2, AV, Live TV**. Live TV opens the digital DVB-T2 tuner app
    (`com.mediatek.wwtv.tvcenter`) — the Thai free-to-air channels, not analog.
- **Debloat:** 16 packages disabled (telemetry, stock-home ad/recommendation rows, retail demo,
  preloaded browser, and unused streaming apps). Full list: [`debloat.txt`](debloat.txt).
- **Ad-blocking DNS:** Private DNS set to AdGuard (`dns.adguard-dns.com`). Blocks ads/trackers in most
  apps, **not** YouTube ads.
- **Clock screensaver:** enabled. After **10 minutes** idle on Home, the launcher shows a large clock
  (`ClockActivity`). Change the minutes with `home_idle_clock_minutes` in `config.xml` (0 = off), then
  rebuild. Unlike the system screensaver it never starts over Live TV or an app.
- **Stats line under the clock:** enabled (`show_stats`). Shows **Memory free · Storage free**.
  **CPU is not shown on this TV:** Android 14 is Enforcing SELinux and blocks the launcher app
  (`untrusted_app`) from reading `/proc/stat`, and this board has no `cpuidle` sysfs fallback, so the
  launcher drops the CPU figure by design rather than show a blank. Memory and storage update live.
- **Screen time for kids:** the "TV Time" app from [`apps/screentime`](../../apps/screentime)
  (`com.gathin.screentime`). YouTube and YouTube Kids share **2 hours a day**, with a warning
  **5 minutes** before the end and a curfew from **21:00 to 08:00** (across midnight); a full-screen
  lock then needs the parent's 4-digit PIN. Every `com.google.android.youtube*` package counts once
  YouTube is ticked, so the remote's YouTube button, the tiles and YouTube Music all hit the same
  limit. An optional strict mode blocks every launchable app once the time is up, except Home, live
  TV and the inputs (the allow list is editable). In kids mode the TV Settings app is blocked too. Parent mode (PIN) lifts the limit
  until the TV goes to standby; waking from standby is always kids mode again. Install with
  `apps\screentime\build-and-install.ps1 -TvHost $env:TV_HOST`, which also grants the two app-ops it
  needs (`GET_USAGE_STATS`, `SYSTEM_ALERT_WINDOW`). The PIN is set on first launch; change the PIN,
  daily minutes, curfew window, counted apps and strict mode in its Settings (PIN required).
- **Kept, by request:** Netflix, YouTube, YouTube Kids, AIS Play, Google Assistant (voice), Chromecast,
  Miracast, the whole tuner/HDMI/AV input stack, Play Store, Play Services, the on-screen keyboard.

## Prerequisites (Windows)

All commands assume the TV's IP in `$env:TV_HOST` and the Python venv under `tools\venv`:

```powershell
$env:TV_HOST = "192.168.9.106"     # this TV's IP at the time of work; DHCP can change it on reboot
# one-time setup, if the venv is missing:
python -m venv tools\venv
tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt
```

Network ADB must be on. This firmware has no *Network debugging* switch, and *Wireless debugging*
cannot be turned on while the TV is on Ethernet: turn on Settings → Device Preferences → Developer
options → *USB debugging*, then **reboot**. adbd listens on port 5555 only from boot, so switching USB
debugging on without a reboot leaves the port closed. Accept the "Allow USB debugging?" prompt on the
first connection. Check the TV's IP (Settings → Network & Internet) after every reboot, since a
refused port at an old address may simply be another device. The build also needs a JDK 17+ and the Android SDK (platform 34, build-tools
34.0.0); a portable copy is under `tools\buildenv` on the machine this was built on.

## Re-enable an app

Re-enable one package:

```powershell
tools\venv\Scripts\python.exe -I tools\tv.py "pm enable <package.name>"
```

Re-enable **everything** this project disabled and put the stock launcher back as Home:

```powershell
tools\restore.ps1 -TvHost $env:TV_HOST examples\aconatic-43hs600an\debloat.txt com.google.android.tvlauncher
```

(There is no recovery record from a `disable.ps1` run here — the packages were disabled individually —
so `restore.ps1` re-enables every package in `debloat.txt`, which is correct because none of them were
disabled before this work. The two factory-disabled apps, `com.electronic.manual` and
`com.google.android.marvin.talkback`, are left as they were.)

### Undo the ad-blocking DNS

```powershell
tools\venv\Scripts\python.exe -I tools\tv.py "settings put global private_dns_mode opportunistic"
```

## Change the dock order or which apps are pinned

Edit [`launcher/app/src/main/res/values/config.xml`](../../launcher/app/src/main/res/values/config.xml),
the `dock_apps` array (first four, in order; missing apps are skipped):

```xml
<string-array name="dock_apps">
    <item>com.netflix.ninja</item>
    <item>com.google.android.youtube.tv</item>
    <item>com.google.android.youtube.tvkids</item>
    <item>com.ais.mimo.aisplay.tv</item>
</string-array>
```

Find a package's name with `tools\venv\Scripts\python.exe -I tools\tv.py "pm list packages"`. Then
rebuild and reinstall:

```powershell
$env:JAVA_HOME = "<path to JDK 17>"        # e.g. tools\buildenv\jdk-17.0.13+11
$env:ANDROID_HOME = "<path to Android SDK>" # e.g. tools\buildenv\sdk
launcher\build-and-install.ps1 -TvHost $env:TV_HOST
```

You can also reorder the dock on the TV itself from the launcher's Settings button (long-press →
reorder), no rebuild needed.

## Change the wallpaper

The launcher can overlay a theme (a `config.json` plus an optional wallpaper) without a rebuild:

```powershell
tools\theme.ps1 -TvHost $env:TV_HOST themes\<name>
```

See [`themes/README.md`](../../themes/README.md) for the theme format and the bundled themes. To go
back to the plain black background, push a theme without a wallpaper or clear the pushed theme files.

## Quirk on this firmware

`com.cltv.fast` (CLTV FAST) **re-enables itself on every reboot**. `pm disable-user` reports success and
it shows as disabled, but a cold boot brings it back. It is in `debloat.txt` so the first pass disables
it; just expect it back after a reboot. Nothing else re-enabled across the reboot test.

What does stick is removing it for the user: `pm uninstall -k --user 0 com.cltv.fast` survived a cold
boot (tested 2026-11-14), and `cmd package install-existing com.cltv.fast` brings it back. It starts a
foreground service at boot, so removing it helps: BOOT_COMPLETED finished in 25 s instead of 36 to 43 s.

## Closing ADB when finished

Network ADB on port 5555 is plaintext. When you are done making changes, in the TV's
Settings → Developer options: **Revoke USB debugging authorisations**, turn **USB debugging** off
(before turning Developer options off), then **reboot**. Port 5555 stays open until the reboot, even
with USB debugging off. After the reboot, confirm the TV's current IP and verify the port is closed
from this machine:

```powershell
tools\venv\Scripts\python.exe -I tools\adb-closed.py $env:TV_HOST
```
