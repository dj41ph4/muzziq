import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  reactStrictMode: true,
  // Self-contained server output for lean Docker images and portable installs.
  output: "standalone",
  // engine/ will carry its own package.json once the torrent engine process
  // exists — pin the trace root now so Next's monorepo-root auto-detection
  // never has to guess.
  outputFileTracingRoot: __dirname,
  // LEÇON MOVVIZ (voir docs — §105.4 du plan d'architecture) : tout appel
  // fs.readdirSync/fs.readFile avec un chemin non statique (construit depuis
  // MUZZIK_DATA_DIR / MUZZIK_MUSIC_DIR) fait que le traceur de fichiers de
  // Next.js inclut TOUT le dossier contenant comme dépendance du bundle
  // standalone. Sur Movviz ça a produit un build de 446 Go (bibliothèque
  // entière aspirée dans .next/standalone) avant que ces exclusions soient
  // posées. Ces dossiers ne sont jamais importés/bundlés — lus uniquement au
  // runtime — donc explicitement exclus dès ce premier commit, pas après le
  // même incident.
  outputFileTracingExcludes: {
    "next-server": [
      "./.muzzik-data/**",
      "**/.muzzik-data/**",
      "../.muzzik-data/**",
      "../../.muzzik-data/**",
    ],
    "next-minimal-server": [
      "./.muzzik-data/**",
      "**/.muzzik-data/**",
      "../.muzzik-data/**",
      "../../.muzzik-data/**",
    ],
    "/*": [
      "./.muzzik-data/**",
      "**/.muzzik-data/**",
      "./music/**",
      "**/music/**",
      "./downloads/**",
      "**/downloads/**",
      "./engine/**",
      "**/engine/**",
      "../.muzzik-data/**",
      "../../.muzzik-data/**",
      "../../../.muzzik-data/**",
      "../../../../.muzzik-data/**",
      "../../../../../.muzzik-data/**",
      "../../../../../../.muzzik-data/**",
    ],
  },
  images: {
    remotePatterns: [
      { protocol: "https", hostname: "lh3.googleusercontent.com" },
      { protocol: "https", hostname: "yt3.googleusercontent.com" },
      { protocol: "https", hostname: "i.ytimg.com" },
      { protocol: "https", hostname: "coverartarchive.org" },
    ],
  },
};

export default nextConfig;
