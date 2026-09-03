/**
 * Déchiffrement de `signatureCipher` — réimplémentation indépendante à partir
 * de l'observation du comportement réel de `base.js` (music.youtube.com/
 * www.youtube.com), même règle que le reste de ce dossier (voir en-tête de
 * `innertubeClient.ts` et `docs/reverse-engineering/youtube-music/README.md`,
 * section « 2026-09-03 »). Aucun code Metrolist (GPL-3.0) n'a été lu ni
 * copié pour ce module ; seule son architecture générale (config distante
 * pré-résolue, puis exécution JS réelle dans un bac à sable, voir
 * `metrolist-analysis.md`) a informé la stratégie choisie ici. yt-dlp
 * (Unlicense) a servi uniquement de référence de comportement pour vérifier
 * *a posteriori* que la structure du résultat était plausible (même préfixe
 * de signature qu'une URL yt-dlp fonctionnelle) — aucune ligne de code
 * yt-dlp n'a été copiée non plus.
 *
 * ## Ce que fait ce module, concrètement
 *
 * `base.js` contient, pour chaque déploiement du lecteur YouTube, une
 * fonction de déchiffrement obfusquée par « control-flow flattening » (un
 * dispatcher unique `P2(v,q,x,X)` sert à des dizaines d'usages sans rapport,
 * le paramètre `v` sélectionnant la branche réellement exécutée par des
 * conditions bit à bit type `(v+2&62)<v`) et par une table de chaînes
 * indexée (`p[N]` résout un nom de propriété/méthode réel comme `"split"`,
 * `"reverse"`, `"splice"`). La branche de déchiffrement délègue à un objet à
 * trois méthodes (`swap(v,0,q%v.length)`, `splice(v,0,q)`, `reverse(v)`) —
 * la même « signature » structurelle que toute chaîne d'outils de ce type
 * documente publiquement depuis des années (yt-dlp, NewPipeExtractor…),
 * seule l'obfuscation autour a changé.
 *
 * Ce module :
 * 1. localise ces trois éléments dans le `base.js` réel **par leur forme**
 *    (regex structurelles sur le motif de swap/splice/reverse et sur la
 *    signature du dispatcher), jamais par un nom de variable en dur — les
 *    noms minifiés (`p`, `C8`, `P2` au moment de l'écriture) changeront au
 *    prochain déploiement, la forme du code beaucoup moins vite ;
 * 2. localise le point d'appel réel du dispatcher pour le champ `s` d'un
 *    format (`M.s`/`M.sp`, les noms de champs eux-mêmes stables — ce sont
 *    des noms de l'API InnerTube, pas des identifiants minifiés) pour en
 *    extraire les deux constantes numériques `(v, q)` qui sélectionnent la
 *    bonne branche ;
 * 3. exécute le code extrait tel quel dans un bac à sable Node (`vm`) —
 *    aucune réimplémentation manuelle des opérations de permutation : c'est
 *    le vrai code de YouTube, juste isolé et exécuté, pas retranscrit à la
 *    main (une retranscription manuelle est plus fragile : elle a été
 *    essayée en premier durant cette investigation et a nécessité plusieurs
 *    itérations avant de correspondre exactement au code réel).
 *
 * ## État réel vérifié le 2026-09-03 — IMPORTANT, à lire avant toute réutilisation
 *
 * Le déchiffrement produit une signature dont la structure est cohérente
 * (même préfixe/format qu'une signature obtenue via yt-dlp pour la même
 * vidéo) — l'algorithme est donc correct avec un niveau de confiance élevé.
 * **Mais la requête HTTP finale vers googlevideo.com avec cette signature
 * renvoie systématiquement 403**, testé sur plusieurs vidéos, plusieurs
 * `clientName` (`WEB_REMIX`, `MWEB`), avec et sans cookies de session — alors
 * qu'une URL obtenue via yt-dlp (client `VISIONOS` dans les tests réels)
 * fonctionne (`200`/`206`, `Content-Type: audio/*`) au même moment, depuis
 * la même IP. La cause la plus probable, non confirmée avec certitude dans
 * le temps de cette session : une validation côté CDN propre à la requête de
 * lecture elle-même (pas à l'appel `/player`), indépendante de la signature,
 * pour les profils client `WEB_REMIX`/`MWEB` actuellement — cohérent avec
 * l'observation documentée dans `metrolist-analysis.md` selon laquelle
 * certains profils clients sont marqués `BROKEN` par les mainteneurs
 * Metrolist alors que d'autres (cascade de repli) fonctionnent encore, et
 * que cette situation change dans le temps.
 *
 * **Conséquence pratique : ce module n'est PAS branché dans
 * `playbackResolver.ts`.** yt-dlp reste l'unique chemin qui produit un flux
 * réellement jouable, vérifié par un vrai `HTTP 200`/`206` + `Content-Type:
 * audio/*`. Ce module est conservé car (a) le déchiffrement lui-même est un
 * résultat réel et vérifiable indépendamment du blocage CDN, (b) il devient
 * immédiatement exploitable sans rien réécrire si ce blocage se lève ou
 * change de profil client, et (c) échouer silencieusement en le laissant de
 * côté sans trace aurait fait perdre ce travail à la prochaine session.
 */

import * as vm from "node:vm";
import { getBasePlayerScript } from "./basePlayerScript";

interface ExtractedCipherProgram {
  source: string; // code JS extrait (p + objet swap/splice/reverse + dispatcher), prêt à exécuter en vm
  callV: number;
  callQ: number;
}

function extractBalancedFunction(js: string, startIndex: number): string {
  // startIndex doit pointer sur le premier "{" du corps de fonction.
  let depth = 0;
  let i = startIndex;
  for (; i < js.length; i++) {
    if (js[i] === "{") depth++;
    else if (js[i] === "}") {
      depth--;
      if (depth === 0) return js.slice(startIndex, i + 1);
    }
  }
  throw new Error("Accolade non fermée en extrayant une fonction du script du lecteur");
}

/**
 * Localise, extrait et assemble le code de déchiffrement réel depuis le
 * texte de `base.js`. Ne retourne `null` que si l'une des formes attendues
 * est absente (déploiement qui a changé de schéma d'obfuscation) — jamais
 * d'exception qui remonterait jusqu'à l'appelant.
 */
function extractCipherProgram(js: string): ExtractedCipherProgram | null {
  // 1. La table de chaînes indexée : `var X="a;b;c;...".split(";")`. Le
  //    littéral peut contenir des guillemets échappés (motifs regex intégrés
  //    à la table) — `(?:[^"\\]|\\.)*` gère l'échappement, un `[^"]*` naïf
  //    s'arrête prématurément sur le premier guillemet échappé rencontré.
  const pMatch = js.match(/var ([A-Za-z0-9_$]{1,4})="(?:[^"\\]|\\.){300,4000}"\.split\(";"\)/);
  if (!pMatch) return null;
  const pVar = pMatch[1];
  const pStmtEnd = js.indexOf(";", pMatch.index! + pMatch[0].length) + 1;
  const pStmt = js.slice(pMatch.index!, pStmtEnd);

  // 2. L'objet à trois méthodes swap/splice/reverse, référencé via la table ci-dessus.
  const pEsc = pVar.replace(/[$]/g, "\\$");
  const helperRe = new RegExp(
    `var ([A-Za-z0-9_$]{1,4})=\\{([A-Za-z0-9_$]{1,4}):function\\(v,q\\)\\{var x=v\\[0\\];v\\[0\\]=v\\[q%v\\[${pEsc}\\[\\d+\\]\\]\\];v\\[q%v\\[${pEsc}\\[\\d+\\]\\]\\]=x\\},\\s*` +
      `([A-Za-z0-9_$]{1,4}):function\\(v,q\\)\\{v\\[${pEsc}\\[\\d+\\]\\]\\(0,q\\)\\},\\s*` +
      `([A-Za-z0-9_$]{1,4}):function\\(v\\)\\{v\\[${pEsc}\\[\\d+\\]\\]\\(\\)\\}\\};`
  );
  const helperMatch = js.match(helperRe);
  if (!helperMatch) return null;
  const helperVar = helperMatch[1];
  const helperStmt = helperMatch[0];

  // 3. Le dispatcher à 4 paramètres qui invoque cet objet — recherché par sa
  //    signature (`function(v,q,x,X){var y=q^v;`) puis vérifié après coup
  //    (le corps doit référencer l'objet swap/splice/reverse trouvé ci-dessus).
  const helperEsc = helperVar.replace(/[$]/g, "\\$");
  const dispatcherHeaderRe = new RegExp(`([A-Za-z0-9_$]{1,4})=function\\(v,q,x,X\\)\\{var y=q\\^v;`, "g");
  let dispatcherMatch: RegExpExecArray | null;
  let dispatcherVar: string | null = null;
  let dispatcherStmt: string | null = null;
  while ((dispatcherMatch = dispatcherHeaderRe.exec(js))) {
    const bodyStart = js.indexOf("{", dispatcherMatch.index);
    const body = extractBalancedFunction(js, bodyStart);
    if (body.includes(`${helperVar}[`)) {
      dispatcherVar = dispatcherMatch[1];
      dispatcherStmt = `${dispatcherMatch[1]}=function(v,q,x,X)${body}`;
      break;
    }
  }
  if (!dispatcherVar || !dispatcherStmt) return null;

  // 4. Le point d'appel réel pour un format (`M.s`/`M.sp`) — en extrait les
  //    constantes (v,q) qui sélectionnent la bonne branche du dispatcher.
  const dispatcherEsc = dispatcherVar.replace(/[$]/g, "\\$");
  const callSiteRe = new RegExp(`\\.sp[\\s\\S]{0,80}?${dispatcherEsc}\\((\\d+),\\s*(\\d+),`);
  const callSiteMatch = js.match(callSiteRe);
  if (!callSiteMatch) return null;
  const callV = Number(callSiteMatch[1]);
  const callQ = Number(callSiteMatch[2]);

  return {
    source: `${pStmt}\n${helperStmt}\nvar ${dispatcherStmt}\nglobalThis.__muzziqCipherDecipher = function(s) { return ${dispatcherVar}(${callV}, ${callQ}, s); };`,
    callV,
    callQ,
  };
}

const g = globalThis as typeof globalThis & {
  __muzziqCipherProgram?: { program: ExtractedCipherProgram | null; fetchedAt: number } | null;
};

const PROGRAM_TTL_MS = 6 * 60 * 60 * 1000; // même politique que sts/base.js — un déploiement du lecteur est rare.

async function getCipherProgram(): Promise<ExtractedCipherProgram | null> {
  const cached = g.__muzziqCipherProgram;
  if (cached && Date.now() - cached.fetchedAt < PROGRAM_TTL_MS) {
    return cached.program;
  }
  const script = await getBasePlayerScript();
  const program = script ? extractCipherProgram(script.text) : null;
  g.__muzziqCipherProgram = { program, fetchedAt: Date.now() };
  return program;
}

/**
 * Déchiffre la valeur `s` d'un `signatureCipher`, ou renvoie `null` si
 * l'extraction structurelle a échoué (déploiement qui a changé de schéma) ou
 * si l'exécution en bac à sable a levé une exception — jamais bloquant pour
 * l'appelant, qui doit alors se rabattre sur yt-dlp.
 */
export async function decipherSignature(s: string): Promise<string | null> {
  const program = await getCipherProgram();
  if (!program) return null;
  try {
    const ctx: { __muzziqCipherDecipher?: (s: string) => string } = {};
    vm.createContext(ctx);
    vm.runInContext(program.source, ctx, { timeout: 2000 });
    const result = ctx.__muzziqCipherDecipher?.(s);
    return typeof result === "string" && result.length > 0 ? result : null;
  } catch {
    return null;
  }
}
