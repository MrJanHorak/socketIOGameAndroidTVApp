# Science Fair Showdown — Android TV app

A small launcher app that shows the game's shared "host" screen full screen on
an Android TV (or Google TV / Fire TV-style stick, or a tablet). Phones still
join by scanning the QR code on the TV.

What it adds over opening the page in a TV browser:

- A tile on the TV home screen.
- Remembers your game server address.
- Opens `/tv`, which creates a Science Fair room and shows the lobby straight away.
- Plays music and sound without needing a click, and keeps the TV awake.
- Narration uses the TV's own text-to-speech (Android WebView has no browser speech).
- Back button opens a small menu: back to game, new room, or change server.

The host player runs the show from their phone (start, next invention,
standings, next round, ribbon, play again), so you shouldn't need the remote
once the lobby is up.

## 1. Run the game on your computer

From `socketIIPartyGame_fe` (installs once with `npm ci` in both folders):

```bash
npm run play
```

This builds the frontend and starts the game server on port 3000. It prints
lines like `On your network: http://192.168.1.20:3000` — that's the address
for the TV. Allow Node.js through the Windows firewall on private networks
when asked.

## 2. Build the APK

1. Install [Android Studio](https://developer.android.com/studio).
2. **File → Open** this `androidTvApp` folder and let Gradle sync (first sync downloads Gradle and the Android SDK pieces).
3. **Build → Build App Bundle(s) / APK(s) → Build APK(s)**. The APK lands in `app/build/outputs/apk/debug/app-debug.apk`.

## 3. Install it on the TV

Pick one:

- **adb over Wi-Fi** (most reliable): on the TV enable *Developer options* (Settings → Device Preferences → About → click *Build* 7 times), turn on *USB/Network debugging*, then from your computer:

  ```bash
  adb connect <tv-ip-address>
  adb install -r app/build/outputs/apk/debug/app-debug.apk
  ```

  `adb` is in the Android SDK `platform-tools` folder that Android Studio installs.
- **Run from Android Studio**: after `adb connect`, the TV appears in the device dropdown — press Run.
- **Send Files to TV / a USB stick + a file manager** if you prefer no developer options (allow "install unknown apps" for that file manager).

## 4. Play

Open **Science Fair Showdown** from the TV's apps row, type the address from
step 1 (just `192.168.1.20` works — port 3000 is assumed) and press Connect.

## Notes

- Everything stays on your home network; the app only talks to the address you type.
- No libraries are used (plain Activity + WebView), so the APK is tiny and builds offline after the first sync.
- If the TV voice sounds wrong, change the TV's text-to-speech engine/voice in Android settings, or turn Narration off in Host Settings.
- The optional local VoxCPM voice still works: it runs on the computer, and the TV just plays the audio it sends.
