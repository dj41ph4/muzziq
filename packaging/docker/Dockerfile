# syntax=docker/dockerfile:1
#
# Porté directement du Dockerfile Movviz (packaging/docker/Dockerfile, §1.1 —
# mécanismes Docker) : même pattern build standalone Next.js + utilisateur
# non-root remappé PUID/PGID. Adapté : pas d'aria2/rtorrent (webtorrent est du
# JS pur, aucun binaire torrent natif requis), pas de fontconfig/ttf (pas de
# burn-in sous-titres pour de la musique), pas d'engine/ séparé (le backend
# torrent tourne dans le process Next.js — voir les règles opérationnelles du projet).
#
# NON VÉRIFIÉ PAR UN VRAI `docker build` — Docker n'est pas disponible dans
# l'environnement où ce fichier a été écrit. Pattern éprouvé (Movviz tourne
# en prod dessus), mais à tester réellement avant tout déploiement.

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
    MUZZIK_CONFIG_DIR=/config \
    MUZZIK_MUSIC_DIR=/music

RUN addgroup -g 1001 muzzik && adduser -u 1001 -G muzzik -S muzzik
RUN apk add --no-cache su-exec

COPY --from=builder --chown=muzzik:muzzik /app/.next/standalone ./
COPY --from=builder --chown=muzzik:muzzik /app/.next/static ./.next/static
COPY --from=builder --chown=muzzik:muzzik /app/public ./public

RUN mkdir -p /config /music && chown muzzik:muzzik /config /music
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
