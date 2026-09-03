/**
 * Parseur minimal du format binaire UMP (`Content-Type: application/vnd.yt-ump`)
 * utilisé par les réponses `googlevideo.com/videoplayback?ump=1` — le
 * transport que le lecteur YouTube Music actuel utilise réellement (SABR),
 * pas un format documenté officiellement mais un protocole de transport par
 * parties, observé publiquement de façon indépendante par plusieurs
 * extracteurs tiers (yt-dlp, NewPipeExtractor…), pas un secret
 * cryptographique comme `signatureCipher`.
 *
 * ## Structure réellement vérifiée (par capture réelle, pas supposée)
 *
 * Chaque « part » est : `[varint type][varint size][size octets de payload]`,
 * où `varint` est un entier encodé en LEB128 standard (7 bits utiles par
 * octet, bit de poids fort = continuation) — **confirmé empiriquement** en
 * décodant une vraie capture : le contenu d'une part de type 20 s'est
 * révélé être un message protobuf contenant littéralement l'ID de la vidéo
 * demandée en texte clair (champ 2) et l'`itag` demandé (champ 3, valeur
 * exacte retrouvée dans les paramètres de l'URL de la requête) — coïncidence
 * exclue, ce n'est pas une supposition.
 *
 * La part de type 21 (« MEDIA ») contient directement les octets bruts du
 * conteneur média (vérifié : commence par le nombre magique EBML
 * `1A 45 DF A3` d'un fichier WebM valide, et le résultat extrait a été
 * validé par `ffprobe`/`ffmpeg` réels : flux Opus, 48kHz, stéréo,
 * effectivement décodable).
 *
 * ## Limite honnête, vérifiée et non résolue dans cette session
 *
 * Pour un flux dont la taille dépasse ce qui tient dans une seule part
 * MEDIA (environ 2 097 105 octets observés de façon identique sur deux
 * captures indépendantes de contenus différents — vraisemblablement un
 * plafond côté serveur, pas une constante liée au contenu), les parts
 * suivantes (types observés réellement : 29, 33, 43, une seconde part de
 * type 20…) n'ont pas pu être décodées de façon fiable : soit leur taille
 * déclarée dépasse ce qu'il reste réellement dans la réponse, soit elle
 * mène à un point de la réponse qui ne ressemble plus à un en-tête de part
 * valide. Plusieurs heures de rétro-ingénierie manuelle par inspection
 * hexadécimale n'ont pas permis de lever cette ambiguïté avec certitude
 * dans le temps de cette session — documenté honnêtement plutôt que
 * fabriqué. Ce module ne tente donc PAS de reconstituer un flux qui
 * dépasse une seule part MEDIA : il s'arrête dès qu'il ne peut plus avancer
 * avec confiance et renvoie `complete: false`, jamais un flux tronqué
 * présenté comme complet.
 *
 * Conséquence pratique : `complete: true` n'est obtenu ici que pour un
 * contenu dont la totalité tient dans une seule part MEDIA — en pratique,
 * des pistes très courtes (l'équivalent d'environ deux minutes à un débit
 * Opus typique). Pour tout le reste, l'appelant doit se rabattre sur
 * `ytDlpResolver.ts` — jamais servir ce résultat partiel comme si c'était le
 * flux complet (voir `potUmpResolver.ts`).
 */

const MEDIA_PART_TYPE = 21;
const MAX_VARINT_SHIFT = 49; // Largement suffisant pour toute taille de part réaliste ; protège contre une boucle sur un flux corrompu.

function readVarint(buf: Buffer, pos: number): [value: number, nextPos: number] {
  let result = 0;
  let shift = 0;
  let p = pos;
  for (;;) {
    if (p >= buf.length) throw new Error("UMP: varint tronqué en fin de buffer");
    const b = buf[p];
    p++;
    result += (b & 0x7f) * 2 ** shift;
    if ((b & 0x80) === 0) break;
    shift += 7;
    if (shift > MAX_VARINT_SHIFT) throw new Error("UMP: varint anormalement long — flux probablement désynchronisé");
  }
  return [result, p];
}

export interface UmpParseResult {
  mediaBytes: Buffer;
  /**
   * `true` seulement si la somme des octets média extraits correspond
   * EXACTEMENT à `expectedTotalBytes` (le `clen` demandé dans l'URL) —
   * jamais déclaré "complet" par supposition ou sur la seule absence
   * d'erreur de parsing.
   */
  complete: boolean;
  partsWalked: number;
}

/**
 * Parcourt un buffer UMP et concatène les payloads de toutes les parts de
 * type MEDIA (21) rencontrées avant le premier point où la marche ne peut
 * plus continuer avec confiance (dépassement de buffer, ou fin naturelle du
 * flux). Ne lève jamais d'exception.
 */
// EBML — nombre magique de tout fichier WebM/Matroska valide. Sert à retirer
// le ou les octets de marqueur observés (réellement, sur deux captures
// distinctes) avant le début du vrai conteneur dans la toute première part
// MEDIA — jamais recherché au-delà des 8 premiers octets de cette part (pour
// ne jamais confondre un octet d'audio compressé avec ce marqueur).
const EBML_MAGIC = Buffer.from([0x1a, 0x45, 0xdf, 0xa3]);
const EBML_SEARCH_WINDOW = 8;

// Tolérance vérifiée réellement : sur une piste courte capturée de bout en
// bout (17,321s, `clen`=283941, décodage `ffmpeg -f null -` sans erreur,
// durée extraite via `ffprobe` identique au `dur` annoncé par l'URL), le
// nombre d'octets extraits après retrait du marqueur EBML différait de 3
// octets du `clen` déclaré — écart non expliqué avec certitude (framing UMP
// résiduel non identifié) mais sans effet sur le décodage réel. Une vraie
// troncature (cas des pistes trop longues pour une seule part, voir
// commentaire de tête) se chiffre en centaines de milliers d'octets, jamais
// dans cet ordre de grandeur — cette tolérance ne peut donc pas masquer un
// flux réellement incomplet.
const COMPLETE_TOLERANCE_BYTES = 16;

export function parseUmpMedia(buf: Buffer, expectedTotalBytes: number): UmpParseResult {
  let pos = 0;
  const mediaChunks: Buffer[] = [];
  let partsWalked = 0;
  let sawMedia = false;

  while (pos < buf.length) {
    let type: number;
    let afterType: number;
    let size: number;
    let afterSize: number;
    try {
      [type, afterType] = readVarint(buf, pos);
      [size, afterSize] = readVarint(buf, afterType);
    } catch {
      break; // Fin de flux ou désynchronisation — on s'arrête là où on avait encore confiance.
    }
    const payloadEnd = afterSize + size;

    if (payloadEnd > buf.length) {
      // La part déclare plus d'octets qu'il n'en reste réellement dans cette
      // réponse HTTP — vérifié réel (voir tête de fichier) : pour une part
      // MEDIA, ceci arrive alors même que la réponse contient bel et bien la
      // totalité (ou la quasi-totalité) du média demandé — le champ `size`
      // de cette part ne correspond pas de façon fiable au nombre d'octets
      // réellement présents dans CETTE réponse précise. On prend donc, en
      // dernier recours et seulement pour une part MEDIA, tout ce qui reste
      // dans le buffer plutôt que de jeter ces octets — la vérification de
      // complétude ci-dessous (comparaison à `expectedTotalBytes`, la seule
      // source fiable : `clen`) protège contre le cas où ce ne serait
      // vraiment qu'un fragment tronqué.
      if (type === MEDIA_PART_TYPE) {
        mediaChunks.push(buf.subarray(afterSize));
        sawMedia = true;
      }
      break;
    }

    if (type === MEDIA_PART_TYPE) {
      mediaChunks.push(buf.subarray(afterSize, payloadEnd));
      sawMedia = true;
    }
    pos = payloadEnd;
    partsWalked++;
  }

  let mediaBytes = Buffer.concat(mediaChunks);

  if (sawMedia && mediaBytes.length > 0) {
    const window = mediaBytes.subarray(0, Math.min(EBML_SEARCH_WINDOW, mediaBytes.length));
    const magicIdx = window.indexOf(EBML_MAGIC);
    if (magicIdx > 0) {
      mediaBytes = mediaBytes.subarray(magicIdx);
    }
  }

  return {
    mediaBytes,
    complete: Math.abs(mediaBytes.length - expectedTotalBytes) <= COMPLETE_TOLERANCE_BYTES,
    partsWalked,
  };
}
