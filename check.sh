#!/bin/bash
# Off-device checks: plain Java against the SDK stubs, no emulator and no TV.
set -euo pipefail
cd "$(dirname "$0")"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
JAR="$SDK/platforms/android-34/android.jar"
[ -n "${JAVA_HOME:-}" ] && export PATH="$JAVA_HOME/bin:$PATH"
rm -rf build/check && mkdir -p build/check
javac -encoding UTF-8 -nowarn -cp "$JAR" -sourcepath src -d build/check \
    src/com/petrobot/hume/Weather.java tests/com/petrobot/hume/TipCheck.java tests/com/petrobot/hume/ThemesCheck.java
java -cp build/check com.petrobot.hume.TipCheck
java -cp build/check com.petrobot.hume.ThemesCheck
