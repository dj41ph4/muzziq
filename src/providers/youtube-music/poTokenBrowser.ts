/**
 * Capture réelle d'une URL de lecture audio YouTube Music (avec `pot`
 * PoToken déjà résolu par le vrai lecteur), via un Chromium headless
 * persistant (Playwright).
 *
 * ## Pourquoi un navigateur et pas un solveur BotGuard maison
 *
 * `docs/reverse-engineering/youtube-music/README.md` (2026-09-03, section
 * "cause du 403 identifiée avec certitude") documente que le blocage `403`
 * sur les requêtes média `googlevideo.com` n'est dû ni à la signature, ni au
 * paramètre `n`, ni aux headers/cookies — uniquement à l'absence d'un `pot`
 * (PoToken, jeton BotGuard). La génération pure Node/jsdom (`bgutils-js`) a
 * été testée et s'est révélée cassée en pratique (voir même section) : le
 * challenge BotGuard actuel détecte l'absence d'un vrai moteur de rendu. Un
 * vrai Chromium headless, lui, fonctionne réellement — vérifié à nouveau
 * dans cette session (voir plus bas).
 *
 * ## Correction importante par rapport à l'hypothèse de départ de ce chantier
 *
 * L'idée initiale était de générer un `pot` une fois et de le mettre en
 * cache plusieurs heures, comme `signatureTimestamp.ts` le fait pour `sts`.
 * **Vérifié faux par test réel** : deux vidéos différentes capturées dans la
 * même page/contexte de navigateur produisent deux `pot` différents (testé
 * à la fois avec des pages séparées et avec une navigation successive sur la
 * même page — même résultat les deux fois). Le `pot` est donc lié à la
 * navigation/au contenu, pas seulement à la session — **il n'est pas
 * réutilisable d'une vidéo à l'autre**.
 *
 * Ce qui EST réutilisable, et donc mis en cache ici via `globalThis` (règle
 * #2 du projet) : le **processus Chromium lui-même** (lancement ~1-2s,
 * coûteux) et sa page. Une fois le navigateur déjà chaud, naviguer vers une
 * nouvelle vidéo et capturer sa requête média réelle prend environ 1,5 à 2
 * secondes (mesuré réellement, plusieurs fois, sur ce poste) — pas les
 * dizaines de secondes d'un lancement de navigateur à froid. C'est ce qui
 * rend ce chemin praticable comme étape de résolution de lecture (comparable
 * au coût déjà accepté du sous-processus yt-dlp), sans jamais relancer
 * Chromium par requête.
 *
 * ## Ce que fait ce module, concrètement
 *
 * Une seule page persistante navigue vers `music.youtube.com/watch?v=…` pour
 * le `videoId` demandé, gère l'écran de consentement RGPD s'il apparaît,
 * déclenche la lecture, et intercepte la vraie requête réseau
 * `googlevideo.com/videoplayback` (mime `audio/webm`) que le lecteur
 * authentique émet — avec son `pot`, sa signature et son `n` déjà résolus
 * par le vrai code YouTube, jamais réimplémentés ici. Un seul appel à la
 * fois est traité (file d'attente sur la page partagée) pour éviter que deux
 * résolutions concurrentes ne se marchent dessus sur le même onglet.
 */

import type { Browser, BrowserContext, Page, Request } from "playwright";

interface BrowserState {
  browser: Browser;
  context: BrowserContext;
  page: Page;
}

const g = globalThis as typeof globalThis & {
  __muzziqYtBrowserState?: Promise<BrowserState> | null;
  __muzziqYtBrowserQueue?: Promise<unknown>;
};

const NAV_USER_AGENT =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36";
const CONSENT_SELECTOR = 'button:has-text("Accept all"), button:has-text("Alles accepteren"), button:has-text("I agree")';

async function launchBrowserState(): Promise<BrowserState> {
  // Import dynamique : Playwright (et son binaire Chromium) est une
  // dépendance lourde et optionnelle du point de vue du chemin de lecture —
  // si elle est absente (image Docker qui ne l'installe pas), l'échec de cet
  // import ne doit jamais empêcher le reste de l'app de démarrer, seulement
  // faire échouer ce chemin précis (repli automatique vers yt-dlp, voir
  // playbackResolver.ts).
  const { chromium } = await import("playwright");
  // Le binaire Chromium téléchargé par Playwright (glibc) ne tourne pas sur
  // l'image runtime Docker actuelle (Alpine/musl, voir packaging/docker/Dockerfile)
  // — `MUZZIQ_CHROMIUM_PATH` permet de pointer vers un Chromium installé par
  // le gestionnaire de paquets de l'image le cas échéant (même pattern que
  // `MUZZIQ_YT_DLP_PATH` dans `ytDlpResolver.ts`). Non défini en dev (le
  // Chromium Playwright local, déjà installé, est utilisé tel quel).
  const executablePath = process.env.MUZZIQ_CHROMIUM_PATH || undefined;
  const browser = await chromium.launch({ headless: true, executablePath });
  const context = await browser.newContext({ userAgent: NAV_USER_AGENT, locale: "en-US" });
  const page = await context.newPage();
  return { browser, context, page };
}

async function getBrowserState(): Promise<BrowserState> {
  if (!g.__muzziqYtBrowserState) {
    g.__muzziqYtBrowserState = launchBrowserState().catch((err) => {
      g.__muzziqYtBrowserState = null;
      throw err;
    });
  }
  return g.__muzziqYtBrowserState;
}

/**
 * Capture l'URL réelle de la requête média audio émise par le vrai lecteur
 * YouTube Music pour `videoId`, `pot` inclus. Renvoie `null` (jamais
 * d'exception) si rien n'a pu être capturé dans le délai imparti — c'est à
 * l'appelant de décider du repli.
 */
export async function captureAudioPlaybackUrl(videoId: string, timeoutMs = 20000): Promise<string | null> {
  const state = await getBrowserState();
  const queue = g.__muzziqYtBrowserQueue ?? Promise.resolve();
  const run = queue.then(() => captureOnce(state.page, videoId, timeoutMs));
  g.__muzziqYtBrowserQueue = run.catch(() => undefined);
  return run;
}

async function captureOnce(page: Page, videoId: string, timeoutMs: number): Promise<string | null> {
  let captured: string | null = null;
  const onRequest = (req: Request) => {
    if (captured) return;
    const url = req.url();
    if (!url.includes("googlevideo.com/videoplayback")) return;
    if (new URL(url).searchParams.get("mime") !== "audio/webm") return;
    captured = url;
  };
  page.on("request", onRequest);
  try {
    await page.goto(`https://music.youtube.com/watch?v=${videoId}`, {
      waitUntil: "domcontentloaded",
      timeout: timeoutMs,
    });
    try {
      const consent = page.locator(CONSENT_SELECTOR);
      if (await consent.first().isVisible({ timeout: 3000 })) {
        await consent.first().click();
      }
    } catch {
      // Pas de mur de consentement (ou déjà accepté cette session) — jamais bloquant.
    }
    try {
      await page.keyboard.press("k"); // Raccourci lecture/pause de music.youtube.com.
    } catch {
      // La page a pu changer de forme — sans conséquence, on attend simplement la requête réseau.
    }
    const deadline = Date.now() + timeoutMs;
    while (!captured && Date.now() < deadline) {
      await page.waitForTimeout(250);
    }
  } catch {
    // Navigation échouée (page down, timeout réseau…) : pas d'exception qui remonte, `captured` reste `null`.
  } finally {
    page.off("request", onRequest);
  }
  return captured;
}
