#!/bin/sh
set -e

# Porté directement depuis Movviz (packaging/docker/docker-entrypoint.sh) —
# remapping PUID/PGID générique, aucune logique spécifique à Movviz.
# Sans ça, le conteneur écrit toujours avec l'uid figé dans l'image (1001),
# qui ne correspond presque jamais au propriétaire réel des dossiers montés
# (NAS, volume Docker) — chaque écriture échouerait en EACCES.

PUID="${PUID:-1001}"
PGID="${PGID:-1001}"

user_on_puid="$(awk -F: -v id="$PUID" '$3==id{print $1; exit}' /etc/passwd)"
if [ -n "$user_on_puid" ] && [ "$user_on_puid" != "muzzik" ]; then
  deluser "$user_on_puid" 2>/dev/null || true
fi

group_on_pgid="$(awk -F: -v id="$PGID" '$3==id{print $1; exit}' /etc/group)"
if [ -n "$group_on_pgid" ] && [ "$group_on_pgid" != "muzzik" ]; then
  group_owner="$(awk -F: -v g="$group_on_pgid" '$4==g{print $1; exit}' /etc/passwd)"
  if [ -n "$group_owner" ] && [ "$group_owner" != "muzzik" ]; then
    deluser "$group_owner" 2>/dev/null || true
  fi
  delgroup "$group_on_pgid" 2>/dev/null || true
fi

if [ "$(id -u muzzik 2>/dev/null)" != "$PUID" ] || [ "$(id -g muzzik 2>/dev/null)" != "$PGID" ]; then
  deluser muzzik 2>/dev/null || true
  delgroup muzzik 2>/dev/null || true
fi
if ! id muzzik >/dev/null 2>&1; then
  addgroup -g "$PGID" muzzik
  adduser -D -H -G muzzik -u "$PUID" muzzik
fi

mkdir -p /config /music
chown muzzik:muzzik /config /music

exec su-exec muzzik:muzzik "$@"
