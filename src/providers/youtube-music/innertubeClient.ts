/**
 * Client InnerTube brut pour YouTube Music (mode anonyme).
 *
 * Réimplémentation indépendante à partir de l'observation du comportement
 * réel de music.youtube.com (règle §1.2 du plan d'architecture — jamais de
 * code copié depuis MetroList, seulement le comportement observé et
 * documenté). Constantes vérifiées par appel réel le 2026-09-01 — voir
 * `docs/reverse-engineering/youtube-music/README.md` pour le détail des
 * sondes et leur résultat.
 *
 * La clé ci-dessous est la clé API publique embarquée dans la page
 * music.youtube.com (envoyée à chaque navigateur, pas un secret serveur).
 */

const API_KEY = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30";
const CLIENT_VERSION = "1.20241201.01.00";
const BASE_URL = "https://music.youtube.com/youtubei/v1";

interface InnertubeContext {
  client: {
    clientName: string;
    clientVersion: string;
    hl: string;
    gl: string;
  };
}

function baseContext(): InnertubeContext {
  return {
    client: {
      clientName: "WEB_REMIX",
      clientVersion: CLIENT_VERSION,
      hl: "en",
      gl: "US",
    },
  };
}

export class InnertubeError extends Error {
  constructor(
    message: string,
    public readonly httpStatus: number
  ) {
    super(message);
    this.name = "InnertubeError";
  }
}

async function post<T>(endpoint: string, body: Record<string, unknown>): Promise<T> {
  const url = `${BASE_URL}/${endpoint}?key=${API_KEY}&prettyPrint=false`;
  const res = await fetch(url, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Origin: "https://music.youtube.com",
      Referer: "https://music.youtube.com/",
    },
    body: JSON.stringify({ context: baseContext(), ...body }),
    // Un provider externe ne doit jamais bloquer une requête utilisateur
    // indéfiniment (plan §76) — délai raisonnable, l'appelant décide du repli.
    signal: AbortSignal.timeout(8000),
  });

  if (!res.ok) {
    throw new InnertubeError(`InnerTube ${endpoint} a répondu ${res.status}`, res.status);
  }
  return (await res.json()) as T;
}

export function innertubeSearch(query: string, params?: string): Promise<unknown> {
  return post("search", { query, ...(params ? { params } : {}) });
}

export function innertubePlayer(videoId: string): Promise<unknown> {
  return post("player", { videoId, contentCheckOk: true, racyCheckOk: true });
}
