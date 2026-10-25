#!/usr/bin/env bash
# Builds libheif.dll for Windows in Docker and puts it where the desktop build picks it up.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
out="$here/../../desktop-app/natives/windows-x64"
docker build -t photoduel-libheif-win "$here"
mkdir -p "$out"
id=$(docker create photoduel-libheif-win)
trap 'docker rm "$id" >/dev/null' EXIT
docker cp "$id:/out/bin/libheif.dll" "$out/libheif.dll"
ls -l "$out"
