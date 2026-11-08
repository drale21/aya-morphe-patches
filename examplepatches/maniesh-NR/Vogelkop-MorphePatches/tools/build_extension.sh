#!/bin/bash
# Build extensions/extension.mpe — the Morphe patch extension containing the
# SignatureHookApp runtime hook (see patches/.../SpoofAppSignaturePatch.kt).
#
# The extension is compiled WITHOUT the Android SDK / AGP (this repo builds on
# minimal CI sandboxes):
#   1. kotlinc (embeddable, from the Gradle cache) compiles the extension
#      source against compile-time-only Android framework stubs
#      (tools/android-stubs). The hook is self-contained: hidden-API
#      exemptions use the VMRuntime meta-reflection trick instead of the
#      HiddenApiBypass library.
#   2. D8 (r8 jar from Google Maven) dexes ONLY the extension classes.
#      Stubs are excluded — the framework references resolve on-device.
#   3. The raw classes.dex bytes become extensions/extension.mpe — the Morphe
#      patcher reads .mpe extension resources as a raw DEX image (the .mpp
#      bundle zip, by contrast, holds the dex alongside manifest/signature
#      entries).
#
# Prerequisites: a Gradle cache with kotlin-compiler-embeddable + guava
# (populated by any ./gradlew build), curl access to dl.google.com and
# Maven Central / Aliyun for the r8 + hiddenapibypass jars.
#
# Usage: tools/build_extension.sh   (run from the repo root)
set -euo pipefail
cd "$(dirname "$0")/.."

OUT_MPE="extensions/extension.mpe"
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

KC=$(find ~/.gradle/caches -name "kotlin-compiler-embeddable-*.jar" ! -name "*sources*" | sort | tail -1)
KSTDLIB=$(find ~/.gradle/caches -path "*kotlin-stdlib*" -name "kotlin-stdlib-2*.jar" ! -name "*sources*" | sort | tail -1)
CORO=$(find ~/.gradle/caches -name "kotlinx-coroutines-core-jvm-1*.jar" ! -name "*sources*" | sort | tail -1)
ANN=$(find ~/.gradle/caches -path "*org.jetbrains/annotations*" -name "annotations-2*.jar" ! -name "*sources*" | sort | tail -1)
GUAVA=$(find ~/.gradle/caches -name "guava-3*.jre.jar" ! -name "*sources*" | sort | tail -1)
[ -n "$KC" ] && [ -n "$KSTDLIB" ] && [ -n "$ANN" ] && { echo "kotlinc deps: OK"; } || { echo "kotlinc deps missing in Gradle cache" >&2; exit 1; }

HIDDEN_API_JAR="$WORK/hiddenapibypass-4.3.jar"
R8_JAR="$WORK/r8.jar"
fetch() { # url dest
    if curl -sfL --max-time 120 -o "$2" "$1"; then return 0; fi
    echo "download failed: $1" >&2; exit 1
}
fetch "https://dl.google.com/android/maven2/com/android/tools/r8/8.5.35/r8-8.5.35.jar" "$R8_JAR"

echo "== compile extension + stubs (kotlinc) =="
# KSTDLIB is needed to RESOLVE built-in types (Any/Unit/annotations); the
# -Xno-*-assertions flags plus the no-stdlib-API code discipline keep the
# OUTPUT bytecode free of stdlib code references (verified below).
java -Xmx1500m -cp "$KC:$KSTDLIB:$CORO:$ANN" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
    -no-reflect \
    -jvm-target 1.8 \
    -Xno-param-assertions -Xno-call-assertions -Xno-receiver-assertions \
    -cp "$KSTDLIB" \
    -d "$WORK/extclasses" \
    extensions/extension/src/main/java/app/template/extension/extension/SignatureHookApp.kt \
    $(find tools/android-stubs -name "*.kt")

echo "== verify no kotlin-stdlib CODE references leaked into extension bytecode =="
# kotlin.Metadata is a harmless RUNTIME annotation; Intrinsics / function types
# would require bundling the stdlib, so they must not appear.
if find "$WORK/extclasses/app" -name "*.class" | xargs strings 2>/dev/null | grep -q "kotlin/jvm/internal/Intrinsics\|kotlin/jvm/functions\|kotlin/collections\|kotlin/sequences\|kotlin/jvm/internal/lambda"; then
    echo "ERROR: kotlin stdlib code references found; rewrite the hook without stdlib APIs" >&2
    exit 1
fi
echo "   clean (no stdlib code references)"
echo "== verify stubs are excluded from the dex input =="
if find "$WORK/extclasses/app" -name "*.class" | grep -q "android/"; then
    echo "ERROR: android stub classes leaked into extension package" >&2
    exit 1
fi

echo "== dex with D8 (extension classes only, android stubs excluded) =="
mkdir -p "$WORK/dexout"
java -Xmx1500m -cp "$R8_JAR:$GUAVA" com.android.tools.r8.D8 \
    --release --min-api 26 --lib "$KSTDLIB" \
    --output "$WORK/dexout" \
    $(find "$WORK/extclasses/app" -name "*.class")
[ -f "$WORK/dexout/classes.dex" ]

echo "== sanity: placeholders present, hook class present =="
strings "$WORK/dexout/classes.dex" | grep -q "app/template/extension/extension/SignatureHookApp"
strings "$WORK/dexout/classes.dex" | grep -q "<package-name>"
strings "$WORK/dexout/classes.dex" | grep -q "<signature>"

echo "== package .mpe (raw dex image) =="
mkdir -p "$(dirname "$OUT_MPE")"
cp "$WORK/dexout/classes.dex" "$OUT_MPE"
ls -la "$OUT_MPE"
echo "extension built: $OUT_MPE"
