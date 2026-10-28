# Clip Pocket for Windows 1.0.0

Desktop companion to the Android app, developed by Nesandu. Windows 10/11 x64.

## Download the Windows app

In the repository's Actions tab, open the latest successful **Build Clip Pocket for Windows** run. Download the **ClipPocket-Windows-x64** artifact (GitHub sign-in required), extract the entire ZIP, and open **ClipPocket.exe**. Keep all extracted files together. No separate Python installation is required for this build. The executable is unsigned; Windows may display an unknown-publisher warning. This does not establish that the executable is safe; inspect the source and build provenance before distributing it.

## Features

- Paste or type a supported public video URL, with a clear × button.
- Automatic thumbnail, title and description preview. Click the description for full details.
- Quality caps from 144p through 2160p and M4A audio. Selected-format MB/GB estimates include audio and video, and show unavailable when incomplete.
- Downloads save in the Windows user's Downloads/ClipPocket directory.
- Progress and cancellation; incomplete downloads can leave .part files that are excluded from the saved-file list.
- Saved-file rows with cached thumbnail, filename, playback and per-file delete confirmation. Only recognized media files directly in the app's folder can be deleted. Playback uses the Windows default player.
- System, light and dark appearance. About page credits Nesandu, with GitHub and Facebook links and no avatar or WhatsApp contact.
- Download engine update using yt-dlp's official updater. No app-version updater.

Only download content you own or have permission to save. Support varies by site and may change. Private, DRM-protected and restricted content is not bypassed. YouTube can require additional site changes despite an up-to-date engine. Preview extraction consumes data and may fail. Size estimates and final data use can differ.

## Build on Windows

Install Python 3.12 x64. From this desktop folder:

```
python -m pip install -r requirements.txt
python -m unittest discover -s tests -v
python build_windows.py
```

The build downloads official yt-dlp and Deno assets, checks their published SHA-256, bundles FFmpeg supplied by imageio-ffmpeg, and records actual dependency versions in BUILD-DEPENDENCIES.json. The result is dist/ClipPocket/ClipPocket.exe plus its accompanying files. To run from source, build once to populate tools/ and then run python app.py.

The engine is copied into the user's LOCALAPPDATA/ClipPocket directory so its own update command can replace it. Thumbnails are also cached there. Files and thumbnails stay on the PC; video links are sent to the requested sites through yt-dlp. The app has no analytics or credentials collection.

## Verification limits

The helper tests and Python syntax are checked separately. Windows packaging, UI rendering, real site downloads, engine updates and default-player integration require a successful Windows build and device testing. A successful package build does not certify antivirus acceptance.

The repository's GPL-3.0 license applies to Clip Pocket code. See THIRD-PARTY.md for bundled components.
