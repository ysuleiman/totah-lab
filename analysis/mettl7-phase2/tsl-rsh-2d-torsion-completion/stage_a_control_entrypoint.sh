#!/bin/sh
set -eu
if [ -z "${PUBLIC_KEY:-}" ]; then
  echo 'PUBLIC_KEY is required' >&2
  exit 64
fi
printf '%s\n' "$PUBLIC_KEY" > /root/.ssh/authorized_keys
chmod 0600 /root/.ssh/authorized_keys
/usr/sbin/sshd
exec "$@"
