/** Fallback navigateur pour le redirect OAuth Spotify. Sur Android, l'App Link
 * vérifié intercepte normalement cette URL et MainActivity reçoit directement le
 * code PKCE. Cette page n'affiche jamais le code si l'app n'est pas installée. */
export function GET() {
  return new Response(
    `<!doctype html><html lang="fr"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>MuzziQ</title></head><body style="margin:0;background:#0b0f0d;color:#f4f7f5;font-family:system-ui,sans-serif;display:grid;min-height:100vh;place-items:center;text-align:center"><main><h1>Retour vers MuzziQ</h1><p>Tu peux revenir dans l’application.</p></main></body></html>`,
    {
      headers: {
        "content-type": "text/html; charset=utf-8",
        "cache-control": "no-store",
      },
    },
  )
}
