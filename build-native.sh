#!/usr/bin/env bash
set -euo pipefail

script_dir=$(cd "$(dirname "$0")" && pwd)
cd "$script_dir"
case "$(uname -s)" in
  MINGW*|CYGWIN*|MSYS*) launcher=./mill.bat; default_output=./scalex.exe ;;
  *) launcher=./mill; default_output=./scalex ;;
esac
"$launcher" --no-daemon nativeCopy --dest "${1:-$default_output}"
