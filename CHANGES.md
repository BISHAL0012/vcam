# Vcam — CHANGES.md

Staging tree for the **Vcam** app (new product built from the original
VCAMSX 1.1.2 source, MIT licensed, package `com.wangyiheng.vcamsx` kept).
Base: pristine `VCAMSX-master` (upstream 2024-02-26). All edits are in
this staging tree only; the pristine source was not touched.

Version: **1.0 (versionCode 1)**. App name: **Vcam**.

## 1. App name, icon, version
- `app/src/main/res/values/strings.xml` — `app_name` VCAMSX → **Vcam**;
foreground-service notification strings translated to English.
- `app/src/main/AndroidManifest.xml` — `roundIcon` now `@mipmap/logo`
(was `@mipmap/ic_launcher_round`); Xposed description `虚拟摄像` →
"Virtual Camera".
- `app/build.gradle.kts` — versionCode 13 → **1**, versionName 1.1.2 →
**"1.0"**; Retrofit + converter-gson dependencies removed (network
stack deleted, see §3).
- Launcher icon: camera/clapperboard image
(`~/workspace/user/files/1000026570_1_6746.jpg`, source is 256×256,
upscaled to 512×512) circular-cropped to PNG, staged ONLY as base64
twins `logo.png.b64` in `res/drawable/` and
`res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/` (the old binary
`logo.png` files were removed — text-only push cannot carry them).
The workflow's "Decode icon PNGs" step decodes them back at build time.
Used by the manifest (`@mipmap/logo`) and the home header
(`R.drawable.logo`).

## 2. English UI
- `components/DisclaimerDialog.kt` — replaced with the VibeCast English
version (same text). NOTE: the dialog is defined but never shown in
the original app either — usage unchanged (still unshown).
- `components/SettingRow.kt` — toast `"$label 打开/关闭"` →
`"$label ON/OFF"`. NOTE: the new HomeScreen builds the decoding row
inline (explicit dark colours) and does not use SettingRow; the
component is kept, translated, for compatibility.
- `MainHook.kt` — Camera1 preview-size toast translated to English
("Video resolution must match the camera exactly / Width: / Height:").
- `utils/VideoPlayer.kt` — the two RTMP toasts are gone with the RTMP
player (see §5); no other user-visible Chinese remains.
- Log-output strings translated too (HLog default tag, VideoProvider
open-file error, VideoToFrames decode/sleep logs, MainHook CameraManager
log tag). Remaining Chinese in the tree is **comments only**.

## 3. IP removal (Vcam is IP-free)
- **Deleted:** `data/services/ApiService.kt` (uploadIp),
`data/services/NetworkModule.kt` (Retrofit, base URL
`vcamsx.gptmanage.top`), `data/services/ApiInterceptor.kt` (unused),
`data/models/Collect.kt` (UploadIpRequest/Response).
- `MyApplication.kt` — `networkModule` removed from the Koin modules.
- `modules/home/controllers/HomeController.kt` — `apiService` inject,
`getPublicIpAddress()` (api.ipify.org), `saveImage()` and the
`init()` call to it are all removed. `init()` only restores state now.
- Verified: no `ipify`, `uploadIp`, `UploadIp` anywhere except one
explanatory comment in HomeController.kt.

## 4. "This software free" text removed
- `modules/home/view/HomeScreen.kt` — the `本软件免费，点击前往软件下载页`
`ClickableText` and its GitHub releases link are deleted entirely.
(The VibeCast tree had already translated it to English; Vcam deletes
it outright.) No `免费` remains anywhere in the tree.

## 5. RTMP + Live Stream removed
- HomeScreen: RTMP TextField, Save RTMP button, "View Live Stream"
button, Live Stream switch — all deleted.
- **Deleted:** `components/LivePlayerDialog.kt`.
- `modules/home/controllers/HomeController.kt` — `playRTMPStream()`,
`ijkMediaPlayer` field and IjkMediaPlayer import deleted;
`release()` now only releases the local MediaPlayer.
- `utils/VideoPlayer.kt` — `initRTMPStreamPlayer()`,
`configureMediaPlayer()` and the `ijkMediaPlayer` field deleted;
`initializeTheStateAsWellAsThePlayer()` now only restores saved
status; the Live-Stream branch of `handleMediaPlayer()` removed
(local MediaPlayer path kept).
- `MainHook.kt` — `ijkMediaPlayer` import/use removed.
- `data/models/VideoStatues.kt` + HomeController `liveURL` default:
the dormant default `"rtmp://…"` value changed to `""`. The
`isLiveStreamingEnabled` / `liveURL` fields are KEPT in VideoStatues
(persisted-status compatibility with the hook process) but nothing
can enable them and nothing streams them.
- No `RTMP` / `Live Stream` string remains except clearly-marked
explanatory comments.

## 6. New home UI — layout H (mockups vcam-ui-h6/h7.png)
- `modules/home/view/HomeScreen.kt` — full rewrite (still no package
declaration, as the original):
- Header: circular camera logo (`R.drawable.logo`), "Vcam" +
"Virtual Camera", green **"N days left"** badge from the key
expiry (amber when ≤ 3 days).
- 2×2 gradient tiles: **Select Video** (purple→pink),
**View Video** (blue→cyan), **Video ON/OFF** (amber gradient),
**Volume ON/OFF** (green gradient). Tile ON = bright gradient,
tile OFF = grey `#3A3C48` with dimmed text (mockup h7). Video /
Volume tiles toggle the original switches + `saveState()` with
"Video ON/OFF" / "Volume ON/OFF" toasts.
- **Hard Decoding** switch row below the tiles (original switch,
kept; "Hard decoding not supported" toast kept).
- **Now Playing** card: the selected video's **banner = first-frame
thumbnail** (MediaMetadataRetriever, `loadVideoBanner()` in
HomeController, 640px wide) with a play overlay, video name
(content-resolver display name, falls back to the file name) and
Playing / Paused status.
- Bottom **key card** (purple gradient): "Key valid until <date>"
+ "Renew: Telegram / imo" (amber when ≤ 3 days).
- Everything else original: video select (permission flow kept,
English toast) / view (VideoPlayerDialog kept), Video / Volume /
Decoding switches.
- Explicit dark colours throughout (background `#0B0D18`, cards
`#1A1D30`) so the UI looks the same on light- and dark-theme phones.
- NOT added: Sound Boost, My Videos list, Floating Logo, Auto
Leveling, panel redesign, mic hook, Live Stream.
- New vector drawables (`res/drawable/`, white iPhone-style thin-stroke
glyphs — no emoji, no text symbols):
`ic_tile_select.xml` (play-frame + plus), `ic_tile_view.xml` (eye),
`ic_tile_video.xml` (video camera), `ic_tile_volume.xml` (speaker),
`ic_play.xml` (banner play overlay), `ic_key.xml` (key-card icon).

## 7. Key system (client side)
Server = two files in public repo **BISHAL0012/vcam** (repo creation is
separate; the files do not exist yet — see "Open items"):
- `keys.json`:
`{"keys":[{"android_id_sha256":"<hex sha256>","key":"VCAM-XXXX-XXXX","name":"<customer name>","contact":"<telegram/imo/phone>","expires_at":"2026-12-06","active":true}]}`
`name`/`contact` are **admin-only** — the app never reads them.
Expiry is edited admin-side (the file's `expires_at` is rewritten);
the app reads the current value on every check.
- `config.json`:
`{"min_version_code":1,"telegram_url":"https://t.me/username","imo_url":"https://imo.im/xxxx"}`

New files:
- `utils/KeyManager.kt` — fetches `keys.json` + `config.json` with
`HttpURLConnection` (10 s timeouts, `Cache-Control: no-cache`,
parsed with framework `org.json` — no new dependency):
- `androidIdHash()` — SHA-256 hex of `Settings.Secure.ANDROID_ID`.
The raw Android ID is **never stored or transmitted**.
- `check()` (on app start): fetch fails → NO_INTERNET ("Internet
required" screen + Retry). App `versionCode` (via PackageManager)
< `min_version_code` → UPDATE_REQUIRED ("Please update Vcam").
No stored key, or stored key not found / inactive / expired /
wrong device → NEED_KEY (stored key cleared). Match → VALID with
`daysLeft` (expiry day counts as valid through that day) and
`expiresAt`.
- `activate()` — re-fetches fresh, matches key (case-insensitive)
+ device hash + `active` + not expired, stores the canonical key
+ expiry in app-private SharedPreferences (`vcam_key`).
- `components/KeyDialog.kt` — blocking dialog (mockup vcam-ui-key.png):
"Enter Key" field, Android ID below it (tap = copy via
ClipboardManager + "Android ID copied" toast), "Buy key — contact
Telegram, imo" with tappable **Telegram** / **imo** buttons opening
`config.json`'s `telegram_url` / `imo_url` (so the seller can change
them any time without a new build), **Activate** button (re-fetches
and validates; failure shows "Invalid or expired key"). Cannot be
dismissed by back press or outside tap. Explicit dark surface +
`TextFieldDefaults.outlinedTextFieldColors` (the 1.1.x API — the
project pins compose-bom 2023.03.00 / material3 1.1.x, where the
newer `OutlinedTextFieldDefaults.colors()` does not exist).
- HomeScreen gates on the key state: CHECKING (spinner) →
NO_INTERNET / UPDATE_REQUIRED (blocked screens) / NEED_KEY
(KeyDialog) / VALID (home UI with days-left badge + key card).
The 1-phone-1-key rule is enforced by the Android-ID hash match.

## 8. imo fix — run #40 ONLY
- `utils/VideoToFrames.kt`:
- `targetWidth` / `targetHeight` + `setTargetSize()` (called from
`MainHook`'s Camera1 `onPreviewFrame` right after the decoder is
created, with the camera's preview size).
- **Frame-drop**: a frame already > 100 ms behind wall-clock time
(presentation time anchored to the first frame via `startPtsMs`)
is dropped BEFORE the rescale; the peer keeps the previous frame.
This is the ONLY sync mechanism — **no audio re-seek of any kind**
(no `audioPositionMs`, no initial/repeated seeks).
- **Rescale**: `getDataFromImage()` now rescales decoded frames to
the exact preview size via `scaledNv21()` (copied byte-identical
from the VibeCast tree, 6242 chars) whenever the source size
differs — fixes the imo vertical-stripe / green-purple frames.
No transform is applied (Vcam has no panel: mirror/rotate/zoom
stay at their identity defaults).
- Removed the dead per-frame `mQueue` copy (mQueue was always null)
and cleaned the `` junk artifacts from log strings.
- `MainHook.kt` — one added line:
`hw_decode_obj!!.setTargetSize(mwidth, mhight)`.
- Nothing else from CamCast/VibeCast was ported: no panel polling, no
boost, no control timers, no SurfaceTransformer, no overlay service.

## 9. Workflow
- `.github/workflows/build.yml` — copied from the VibeCast tree
(has the "Decode icon PNGs" step: decodes every `*.png.b64` under
`res/` and fails fast if any `.b64` remains — leftover twins broke
run #52). Artifact renamed `vcamsx-apk` → **`vcam-apk`**. No NDK
step (no native code in the app module itself; the ijk `.so` files
ship prebuilt in `jniLibs/`).

## Verification performed (this machine, no Android SDK — no compile)
- grep: no `ipify` / `uploadIp` / `UploadIp` (1 explanatory comment
only); no `免费`; no `RTMP` / `Live Stream` (3 explanatory comments
only); no mic-hook code (`micHook`/`MicHook`/`AudioRecord` — none);
no "Sound Boost" / "My Videos" / "Floating" / "Auto Leveling"
(1 explanatory comment only).
- CJK: 49 lines remain, ALL in `//` code comments. Zero CJK in any
user-visible string, toast, log output, or resource.
- Braces/parens/brackets balanced in all 31 `.kt` files (comment- and
string-aware check). One real bug found and fixed by this check:
the first `scaledNv21` extraction had dropped the function's closing
brace (fixed; the function is now byte-identical to VibeCast's).
- All 6 `logo.png.b64` twins decode to valid 512×512 RGBA PNGs.
- All XML resources (vectors, manifest, strings) parse.
- All 7 drawables referenced from code exist
(`logo`, `ic_tile_select`, `ic_tile_view`, `ic_tile_video`,
`ic_tile_volume`, `ic_play`, `ic_key`).

## Open items / things the parent must know
1. **Not compiled.** There is no Android SDK in this environment, so
the tree was verified by reading + greps + balance checks only.
The GitHub Actions build is the real compile check.
2. **Binaries the text-only push cannot carry** (same situation as the
VibeCast tree — the parent's push routine already handles these
somehow, but for a NEW repo they must get in): `app/src/main/
jniLibs/*/*.so` (ijkplayer, required at build time),
`app/src/main/res/mipmap-*/ic_launcher*.webp` (10 files, upstream
adaptive-icon art), `gradle/wrapper/gradle-wrapper.jar`, and
`app/release/VCAMSX1.1.2.apk` (36 MB upstream build output, not
needed for the build — can be dropped from the push).
If the new repo is seeded with text files only, the build WILL FAIL
on the missing `.so` files. Confirm how they get in before pushing.
3. **`keys.json` / `config.json` do not exist yet** in BISHAL0012/vcam
(repo creation in progress). Until they exist, every app start
reports NO_INTERNET ("Internet required") because the fetch 404s —
that message is misleading in that case, but the app cannot
distinguish "no network" from "server file missing" without the
files present.
4. **No signature pinning (v1).** The key check is server-side, so a
stock APK cannot be used without a valid key — but a modified APK
could skip the check entirely. Signature pinning / integrity check
is a v2 hardening item.
5. Logo source was 256×256 (upscaled 2× to 512×512) — slight softness
is possible; a real 512px source would be sharper.
6. `DisclaimerDialog` is translated but still never shown (as in the
original). `SettingRow` is translated but unused by the new
HomeScreen (decoding row is inline for dark-theme colours).
7. `VideoStatues.isLiveStreamingEnabled` / `liveURL` fields are kept
for persisted-status compatibility; nothing can set or use them.
8. Key expiry semantics: `expires_at` "2026-12-06" stays valid through
that whole day (UTC); `daysLeft` is 0 on the expiry day, and the
Renew line turns amber at ≤ 3 days.

## v1.1 (2026-11-06) — minute/hour keys
- KeyManager: expires_at now accepts a UTC timestamp ("2026-11-06T18:30:00Z") as well as a plain date; new timeLeftText() ("5 days left" / "3 hours left" / "25 min left") and displayExpiry(); findEntry now rejects by exact expiry instant (also fixes date keys staying valid <24h past expiry).
- HomeScreen: header badge + key card use the new time-left text; expiry shown as "2026-11-06 18:30 UTC" for timestamp keys.
- versionCode 2, versionName 1.1. min_version_code stays 1 (old builds keep working; minute keys on old builds behave as day keys).

## v1.2 (2026-11-06) — Renew button works
- HomeScreen: 'Renew: Telegram / imo' in the key card is now tappable (underlined) and opens a dialog with Telegram / imo buttons that open the contact links from config.json. (It was plain text before, so tapping did nothing.)
- versionCode 3, versionName 1.2.

## v1.3 (2026-11-06) — enforce key expiry while running
- HomeScreen: new LaunchedEffect re-checks the key every 60s while VALID. Expired/revoked key (NEED_KEY) or forced update (UPDATE_REQUIRED) locks the app back to the key screen; a still-valid key refreshes the time-left badge live. NO_INTERNET on a re-check is ignored (brief network blip must not kick the user out); the next cycle retries.
- This closes the bypass where a key kept working forever if the app stayed open/minimised past its expiry.
- versionCode 4, versionName 1.3.

## v1.4 (2026-11-06) — permanent signing key + update screen links
- Fixed: every GitHub build was signed with a fresh debug key (new runner = new keystore), so Settings.Secure.ANDROID_ID changed on every update and all keys broke. Now all builds are signed with ONE permanent release keystore (SHA-256 fingerprint 0B:C8:B9:84:51:9B:65:E0:66:90:30:EC:15:14:B3:F8:DF:FE:2E:C3:E9:EA:E8:C1:6A:C4:63:4E:40:C7:22:2A, valid until 2051). The keystore lives only in the repo secret KEYSTORE_BASE64 (passwords in KEYSTORE_PASSWORD / KEY_PASSWORD, alias in KEY_ALIAS); build.gradle.kts reads them from env and falls back to debug signing when absent. Android ID now stays stable until factory reset; ONE last ID change happens on the first install of this build — all keys must be re-issued once.
- Update screen (UPDATE_REQUIRED): added a full-width "Download Update" button opening the release page (release_url from config.json, default https://github.com/BISHAL0012/Vcam-App/releases), above the existing Telegram / imo buttons. config.json gains release_url.
- versionCode 5, versionName 1.4.
