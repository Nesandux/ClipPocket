# Clip Pocket 2.2 for Android

Native video/audio downloader for YouTube, Facebook, Instagram, TikTok and other sites supported by the bundled yt-dlp engine. Site support does not guarantee every video or link works.

## Install

Android 10+ on ARM64 devices. This build uses the same development certificate as versions 1.6 and 1.7, so it can be installed as an update over either. The signing certificate differs from versions 1.0–1.5; those cannot be updated in place. Uninstalling an old version clears its app data. Back up downloaded files before considering uninstalling an earlier version. No broad storage permission is required.

## Appearance

Version 2.2 retains the Apple-inspired interface and supports Match device, Light and Dark through the top-right Appearance menu. Match device is the default and follows the system theme. The app has rounded popup menus and dialog panels, rounded cards and controls, blue accents, theme-matched system bars, an updated app icon and a segmented Downloads filter. The main screen and Downloads list have increased spacing between content. The main screen retains its responsive layout, automatic link preview and thumbnail loading indicator. The About screen still credits developer Nesandu.

## Navigation

The main screen has a Downloads button in its header. Tap it to open the saved-files page. A round × close button appears at the upper left of Downloads and About; tap it to return to the same main screen without losing the link or preview. The menu opens a dedicated About page with developer Nesandu and clickable GitHub and Facebook contacts, without a profile image or avatar. The exact URLs are provided by the developer. The Downloads heading and manual refresh button are removed; the list refreshes when opening Downloads, resuming the app, or completing a download. There is no New download tab or Retry preview button. The × button in the link field clears the current link and preview. Paste link loads a fresh preview automatically, including when pasting the same link again; typing, sharing or changing quality also triggers automatic preview loading.

## Updates

The app has no in-app app-version updater. The separate Update download engine action remains in the menu.

## Preview and quality

Paste or share a video/reel link. After a short delay, the app requests the title, thumbnail, description and selected stream metadata. A centered indeterminate loading indicator is shown while metadata or the thumbnail is loading. It stops after success or failure; unavailable images use a fallback icon. Saved-file thumbnails also show a loading indicator while artwork or a video frame is being fetched. Tap the description to open it in full. Choose a resolution cap from 144p to 2160p (4K), or M4A audio. Each change loads the preview for that exact download selector, and Download becomes available when that preview is ready. A slower older response cannot overwrite a newer selection. Some sources do not offer lower resolutions, so retry another quality if preview extraction fails.

The preview shows the actual selected resolution when supplied by the site. Decimal MB/GB estimates use the selected stream sizes, approximate sizes, or duration × bitrate when available. Separate video and audio stream sizes are added. If any selected stream size cannot be estimated, the app says Size unavailable instead of showing an incomplete total. Previewing also uses network data. Final saved size and total data usage can differ because of merging, audio conversion, metadata, overhead, retries, or changed source formats. Unknown-height streams can be selected. MP4 is preferred; other containers may be used.

## Adaptive main screen

The main page has no outer scrolling container. Link/quality controls, preview and download actions share the available window height. On short or wide windows, controls and preview sit side by side; otherwise they stack with a flexible thumbnail area. Status messages are single-line or compact and open in full when tapped. Tap the description to view the full text. Download-engine updates are in the top-right menu. The Downloads page scrolls only its file list. Very small split-screen windows, large accessibility fonts or the on-screen keyboard still require device validation.

## Downloads and folder

The Downloads tab lists completed files owned by this app in the Downloads/ClipPocket folder, with All, Videos and Audio filters. Each compact horizontal row has a thumbnail, filename, size and a trash icon on its right edge. Tap the thumbnail to play; there is no separate play button. Video frames, cached source artwork or embedded audio art are used where available, with a media icon as the fallback. It queries actual MediaStore records rather than a limited recent-history list. Reopen Downloads or resume the app after an external change to refresh the list. Deleted files disappear after refresh; an installed player is needed for playback. There are no Select all, bulk Delete controls or selection checkboxes. Tap the trash icon on the right of an individual row to delete that file. A confirmation dialog shows its filename and explains that deletion is permanent. Only that app-owned record in Downloads/ClipPocket is deleted. The app reports success/failure and refreshes the list. Cached artwork is removed after successful deletion.

Downloads/ClipPocket is created automatically by Android MediaStore when the first file is saved. Files are downloaded to temporary app cache and then published into this named folder. Cancelled or failed saves remove pending MediaStore entries. Downloads run in a foreground service with a cancellation notification. If Android terminates the process, a download may need restarting. Keep enough free space for the temporary and final file copies.

## Limitations

Only download material you own or have permission to save. Private, login-only, DRM-protected, geographically restricted or anti-bot-protected content may fail. The app does not import login cookies or implement access-control bypass. Use Update download engine if an extractor becomes outdated. A successful preview does not guarantee the server will allow the subsequent download.

## Build and tests

Open in Android Studio with JDK 17, SDK 35 and Build Tools 35.0.0. Use the included Gradle 8.11.1 wrapper: `./gradlew assembleDebug`. Output is app/build/outputs/apk/debug/app-debug.apk. The development key created for 1.6 is saved separately as ClipPocket-v1.6-debug.keystore. Keep it private and reuse it for future development updates; the default debug-key alias and password follow Android debug-key conventions. Do not use a debug key for a public production release.

The standalone tests in tests/ cover URL/share handling, MIME types, selected-format rules, actual selected resolution and exact/estimated/unknown stream sizes. Run with javac/java using DownloadRules, SizeEstimate, PreviewData, both test classes and the JSON-java org.json 20240303 test JAR. JSON-java is used only for host-side tests; production uses Android's org.json. See BUILD-VERIFICATION.txt for completed checks and limits.

Download engine: youtubedl-android 0.18.1 library and ffmpeg modules from Maven Central. See THIRD-PARTY-NOTICES.md and LICENSE for upstream notices and source links.
