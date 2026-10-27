# Third-party components

ClipPocket links youtubedl-android 0.18.1 (library, common, and ffmpeg), licensed GPL-3.0. ClipPocket app source is provided under GPL-3.0 as well; see LICENSE.

Upstream source and license: https://github.com/yausername/youtubedl-android
Release and version history: https://github.com/yausername/youtubedl-android/releases
Maven component source artifacts: https://repo.maven.apache.org/maven2/io/github/junkfood02/youtubedl-android/
Native binary build source: https://github.com/yausername/youtubedl-android-binaries

The upstream engine bundles yt-dlp, Python, FFmpeg, and other native components. Their copyright and license notices remain with those projects and the packaged components. In particular, FFmpeg licensing depends on its configured build. Review upstream corresponding source and notices before public redistribution.

Other transitive components are resolved by Gradle from declared Maven dependencies, including Kotlin, Jackson, Apache Commons, and AndroidX. Their licenses remain applicable. No upstream code was modified for this app.
