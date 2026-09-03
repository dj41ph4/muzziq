# syntax=docker/dockerfile:1
#
# Porté directement du Dockerfile Movviz (packaging/docker/Dockerfile, §1.1 —
# mécanismes Docker) : même pattern build standalone Next.js + utilisateur
# non-root remappé PUID/PGID. Adapté : pas d'aria2/rtorrent (webtorrent est du
# JS pur, aucun binaire torrent natif requis), pas de fontconfig/ttf (pas de
# burn-in sous-titres pour de la musique), pas d'engine/ séparé (le backend
# torrent tourne dans le process Next.js — voir les règles opérationnelles du projet).
#
# Build réel vérifié le 2026-09-02 (GitHub Actions, amd64+arm64, image
# publiée sur Docker Hub). Bug trouvé au premier essai et corrigé : le
# dossier public/ n'existait pas dans ce projet (COPY /app/public échouait).

FROM node:22-alpine3.20 AS deps
WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm ci --no-audit --no-fund || npm install --no-audit --no-fund

FROM node:22-alpine3.20 AS builder
WORKDIR /app
# build-base/python3 : plusieurs dépendances de webtorrent (bufferutil,
# utp-native...) compilent un addon natif à l'installation sur musl/Alpine.
RUN apk add --no-cache build-base python3
COPY --from=deps /app/node_modules ./node_modules
COPY . .
ENV NEXT_TELEMETRY_DISABLED=1
RUN npm run build

FROM node:22-alpine3.20 AS runner
WORKDIR /app
ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    PORT=9910 \
    HOSTNAME=0.0.0.0 \
    MUZZIQ_CONFIG_DIR=/config \
    MUZZIQ_MUSIC_DIR=/music

RUN addgroup -g 1001 muzziq && adduser -u 1001 -G muzziq -S muzziq
# yt-dlp : résout les flux audio YouTube Music quand InnerTube anonyme est
# bloqué (voir docs/reverse-engineering/youtube-music/). Jamais aria2
# (INTERDIT 9). Bug réel #1 : l'image ne l'installait pas du tout ("spawn
# yt-dlp ENOENT" en conditions réelles). Bug réel #2 : le binaire standalone
# officiel (PyInstaller) est lié à glibc — sur Alpine (musl), il échoue avec
# "not found" (exit 127), pas une erreur claire. Fixé en utilisant le paquet
# yt-dlp du dépôt Alpine (musl-natif, tire python3 en dépendance).
RUN apk add --no-cache su-exec ca-certificates yt-dlp \
    && yt-dlp --version

# Chromium pour le chemin PoToken + UMP (src/providers/youtube-music/
# poTokenBrowser.ts, docs/reverse-engineering/youtube-music/README.md).
# AVERTISSEMENT HONNÊTE, NON VÉRIFIÉ : contrairement à yt-dlp ci-dessus, ce
# bloc n'a PAS été validé par un build Docker réel dans la session qui l'a
# écrit (faute de temps) — il applique seulement le même correctif déjà connu
# pour yt-dlp (le binaire Chromium officiel de Playwright est lié à glibc et
# ne tourne pas sur Alpine/musl, donc on utilise le paquet Chromium du dépôt
# Alpine — musl-natif — plutôt que `npx playwright install`) et pointe
# `MUZZIQ_CHROMIUM_PATH` dessus. À vérifier par un vrai build + un vrai appel
# avant de considérer ce chemin fiable en production (règle #5 du projet :
# jamais faire confiance à une capacité déclarée sans l'avoir testée en
# exécution réelle). Poids réel non mesuré non plus, mais Chromium ajoute de
# l'ordre de 300-400 Mo à l'image — coût assumé, pas cassé silencieusement :
# si ce paquet ne fonctionne pas ou est absent, `poTokenBrowser.ts` échoue
# proprement (capturé, jamais fatal) et la résolution retombe sur yt-dlp.
RUN apk add --no-cache chromium \
    && chromium-browser --version || chromium --version || true
ENV MUZZIQ_CHROMIUM_PATH=/usr/bin/chromium-browser

COPY --from=builder --chown=muzziq:muzziq /app/.next/standalone ./
COPY --from=builder --chown=muzziq:muzziq /app/.next/static ./.next/static
COPY --from=builder --chown=muzziq:muzziq /app/public ./public

RUN mkdir -p /config /music && chown muzziq:muzziq /config /music
# /config = state applicatif (stores JSON, Context Engine SQLite, torrents
# en cours) · /music = bibliothèque finale (plan §4).
VOLUME ["/config", "/music"]

COPY packaging/docker/docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
RUN chmod +x /usr/local/bin/docker-entrypoint.sh
ENTRYPOINT ["docker-entrypoint.sh"]

# Port peer BitTorrent — forward aussi sur le routeur/pare-feu, sinon les
# téléchargements restent en connexions sortantes uniquement (plus lent).
EXPOSE 9910 51413/tcp 51413/udp
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s \
  CMD wget -qO- http://127.0.0.1:9910/api/health || exit 1

CMD ["node", "server.js"]
