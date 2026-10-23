#!/bin/sh
set -eu

case "${LOGO_DEV_TOKEN:-}" in
  ""|pk_*|live_*) ;;
  *)
    echo "LOGO_DEV_TOKEN must be a Logo.dev publishable key; ignoring unsupported key type" >&2
    LOGO_DEV_TOKEN=""
    ;;
esac

case "${LOGO_DEV_TOKEN:-}" in
  *[!A-Za-z0-9_-]*)
    echo "LOGO_DEV_TOKEN contains unsupported characters" >&2
    exit 1
    ;;
esac

printf 'window.LOGO_DEV_TOKEN = "%s";\n' "${LOGO_DEV_TOKEN:-}" > /usr/share/nginx/html/assets/logo-config.js
