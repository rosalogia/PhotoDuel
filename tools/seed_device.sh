#!/usr/bin/env bash
# Pushes a generated camera roll (see make_bursts.py) to the connected device and rescans media.
# Usage: seed_device.sh OUT_DIR [--clear]
#   --clear removes everything in DCIM/Camera and DCIM/Screenshots first. Only use on an emulator.
set -euo pipefail
out=$1
adb="${ADB:-adb}"

if [[ "${2:-}" == "--clear" ]]; then
    if [[ "$($adb shell getprop ro.kernel.qemu)" != "1" && "$($adb shell getprop ro.boot.qemu)" != "1" ]]; then
        echo "Refusing to --clear a device that is not an emulator" >&2
        exit 1
    fi
    $adb shell 'rm -rf /sdcard/DCIM/Camera/* /sdcard/DCIM/Screenshots/*'
fi

$adb shell mkdir -p /sdcard/DCIM/Camera /sdcard/DCIM/Screenshots
$adb push "$out/Camera/." /sdcard/DCIM/Camera/ >/dev/null
$adb push "$out/Screenshots/." /sdcard/DCIM/Screenshots/ >/dev/null
$adb shell content call --uri content://media/ --method scan_volume --arg external_primary >/dev/null
echo "Seeded $(ls "$out/Camera" | wc -l) camera photos and $(ls "$out/Screenshots" | wc -l) screenshots"
