# Third-party components

Clip Pocket is distributed under the repository's GNU GPL v3 license.

- yt-dlp: https://github.com/yt-dlp/yt-dlp — Unlicense for the project, with bundled dependencies under their own licenses. Official Windows executable and corresponding source release: https://github.com/yt-dlp/yt-dlp/releases . Build records contain the exact tag and asset checksum.
- Deno: https://github.com/denoland/deno — MIT, with dependency notices in the matching source release. https://github.com/denoland/deno/blob/main/LICENSE.md . Exact tag and checksum recorded in BUILD-DEPENDENCIES.json.
- FFmpeg: https://ffmpeg.org/ — LGPL/GPL depending on build configuration. The packaged executable is supplied by imageio-ffmpeg; its build details, binary provenance, licenses and source/build scripts are maintained at https://github.com/imageio/imageio-ffmpeg and https://github.com/imageio/imageio-binaries . Run tools/ffmpeg.exe -version for configuration. Redistribution must comply with that binary's applicable license; retain notices and provide matching source before public binary distribution.
- CustomTkinter: https://github.com/TomSchimansky/CustomTkinter — MIT.
- Pillow: https://github.com/python-pillow/Pillow — HPND and included dependency notices.
- Python/Tk: https://www.python.org/psf/license/ and https://www.tcl.tk/software/tcltk/license.html .
- PyInstaller: https://pyinstaller.org/ — GPL with bootloader exception permitting packaged application distribution.

Review bundled-component license and source obligations before public release. CI artifacts are development builds, not a reviewed production installer.
