"use client";

import { useEffect, useRef, useState } from "react";
import useSWR from "swr";
import { CheckCircle2, XCircle, RefreshCw, Music, Link2, Trash2, Plus, ChevronDown } from "lucide-react";
import { TopBar } from "@/components/TopBar";
import type { PlexConfig, PlexSyncPolicy } from "@/lib/integrations/plex/store";
import type { PlexServerOption, PlexSection } from "@/lib/integrations/plex/types";

const fetcher = (url: string) => fetch(url).then((r) => r.json());

async function post<T>(url: string, body?: unknown): Promise<T> {
  const res = await fetch(url, {
    method: "POST",
    headers: body ? { "Content-Type": "application/json" } : undefined,
    body: body ? JSON.stringify(body) : undefined,
  });
  const json = await res.json();
  if (!res.ok) throw new Error(json.error ?? `HTTP ${res.status}`);
  return json as T;
}

export default function PlexSettingsPage() {
  const { data, mutate } = useSWR<PlexConfig & { accountUsername?: string }>("/api/integrations/plex", fetcher);

  const [connecting, setConnecting] = useState(false);
  const [pinCode, setPinCode] = useState<string | null>(null);
  const [connectError, setConnectError] = useState<string | null>(null);
  const pollTimer = useRef<ReturnType<typeof setInterval> | null>(null);

  const [testing, setTesting] = useState(false);
  const [testResult, setTestResult] = useState<{ ok: boolean; detail: string } | null>(null);

  const [servers, setServers] = useState<PlexServerOption[] | null>(null);
  const [loadingServers, setLoadingServers] = useState(false);
  const [selectingServer, setSelectingServer] = useState<string | null>(null);
  const [serverError, setServerError] = useState<string | null>(null);

  const [sections, setSections] = useState<PlexSection[] | null>(null);
  const [loadingSections, setLoadingSections] = useState(false);
  const [selectedSections, setSelectedSections] = useState<Set<string>>(new Set());

  const [syncing, setSyncing] = useState(false);
  const [syncResult, setSyncResult] = useState<{ ok: boolean; summary: string } | null>(null);
  const [syncingPlaylists, setSyncingPlaylists] = useState(false);
  const [playlistResult, setPlaylistResult] = useState<{ ok: boolean; summary: string } | null>(null);
  const [syncingHistory, setSyncingHistory] = useState(false);
  const [historyResult, setHistoryResult] = useState<{ ok: boolean; summary: string } | null>(null);

  const [mappings, setMappings] = useState<{ plexPrefix: string; localPrefix: string }[] | null>(null);
  const [showAdvanced, setShowAdvanced] = useState(false);

  useEffect(() => {
    if (data && !mappings) setMappings(data.pathMappings ?? []);
    if (data) setSelectedSections(new Set(data.musicSections?.map((s) => s.key) ?? []));
  }, [data, mappings]);

  useEffect(() => () => {
    if (pollTimer.current) clearInterval(pollTimer.current);
  }, []);

  if (!data) {
    return (
      <main className="mx-auto flex min-h-screen max-w-2xl flex-col gap-6 px-5 pt-8 pb-16 sm:px-8">
        <TopBar title="Plex" />
        <div className="flex flex-col gap-4">
          {[0, 1, 2].map((i) => (
            <div key={i} className="glass flex flex-col gap-2 rounded-2xl p-4">
              <div className="h-3 w-1/4 animate-pulse rounded bg-white/[0.06]" />
              <div className="h-9 w-2/3 animate-pulse rounded-full bg-white/[0.04]" />
            </div>
          ))}
        </div>
      </main>
    );
  }

  const connected = !!data.accountUsername;
  const serverChosen = !!data.serverUrl && !!data.machineIdentifier;

  async function save(patch: Partial<PlexConfig>) {
    const res = await fetch("/api/integrations/plex", {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(patch),
    });
    mutate(await res.json(), { revalidate: false });
  }

  async function connectPlex() {
    setConnectError(null);
    setConnecting(true);
    setPinCode(null);
    try {
      const pin = await post<{ pinId: number; code: string; authUrl: string }>("/api/integrations/plex/oauth/pin");
      setPinCode(pin.code);
      window.open(pin.authUrl, "_blank", "noopener,noreferrer");

      const startedAt = Date.now();
      pollTimer.current = setInterval(async () => {
        if (Date.now() - startedAt > 3 * 60 * 1000) {
          if (pollTimer.current) clearInterval(pollTimer.current);
          setConnecting(false);
          setConnectError("Délai dépassé — relance la connexion.");
          return;
        }
        try {
          const poll = await fetch(`/api/integrations/plex/oauth/poll?pinId=${pin.pinId}`).then((r) => r.json());
          if (poll.status === "authorized") {
            if (pollTimer.current) clearInterval(pollTimer.current);
            setConnecting(false);
            setPinCode(null);
            mutate();
          }
        } catch {
          // erreur réseau transitoire pendant le poll — on retente au prochain tick
        }
      }, 2000);
    } catch (e) {
      setConnecting(false);
      setConnectError(e instanceof Error ? e.message : "Échec de connexion Plex");
    }
  }

  async function loadServers() {
    setServerError(null);
    setLoadingServers(true);
    try {
      const res = await fetch("/api/integrations/plex/servers").then((r) => r.json());
      if (res.error) throw new Error(res.error);
      setServers(res.servers);
    } catch (e) {
      setServerError(e instanceof Error ? e.message : "Échec du chargement des serveurs");
    } finally {
      setLoadingServers(false);
    }
  }

  async function selectServer(server: PlexServerOption, uri: string) {
    setServerError(null);
    setSelectingServer(uri);
    try {
      await post("/api/integrations/plex/servers/select", { machineIdentifier: server.machineIdentifier, name: server.name, uri });
      setSections(null);
      mutate();
    } catch (e) {
      setServerError(e instanceof Error ? e.message : "Échec de la sélection du serveur");
    } finally {
      setSelectingServer(null);
    }
  }

  async function loadSections() {
    setLoadingSections(true);
    try {
      const res = await fetch("/api/integrations/plex/sections").then((r) => r.json());
      setSections(res.sections ?? []);
    } finally {
      setLoadingSections(false);
    }
  }

  async function saveSections() {
    const chosen = (sections ?? []).filter((s) => selectedSections.has(s.key)).map((s) => ({ key: s.key, title: s.title }));
    await post("/api/integrations/plex/sections/select", { sections: chosen });
    mutate();
  }

  async function test() {
    setTesting(true);
    setTestResult(null);
    const res = await fetch("/api/integrations/plex/test", { method: "POST" });
    setTestResult(await res.json());
    setTesting(false);
  }

  async function runSync() {
    setSyncing(true);
    setSyncResult(null);
    try {
      const res = await post<{ ok: boolean; tracksSeen: number; created: number; matchedExisting: number; pathMatched: number; alreadyLinked: number; skipped: number; errors: string[] }>(
        "/api/integrations/plex/sync"
      );
      const summary = `${res.tracksSeen} pistes — ${res.created} créées, ${res.matchedExisting} liées, ${res.pathMatched} via chemin, ${res.alreadyLinked} déjà à jour, ${res.skipped} échecs`;
      setSyncResult({ ok: res.errors.length === 0, summary: res.errors.length ? `${summary} · ${res.errors[0]}` : summary });
      mutate();
    } catch (e) {
      setSyncResult({ ok: false, summary: e instanceof Error ? e.message : "Échec de synchronisation" });
    } finally {
      setSyncing(false);
    }
  }

  /** Plex n'est ici qu'une source de listes : aucun appel de streaming Plex ne
   * part de ce bouton. Les playlists sont importées sous le préfixe « Plex · »
   * afin de ne jamais écraser une playlist MuzziQ du même nom. */
  async function runPlaylistSync() {
    setSyncingPlaylists(true);
    setPlaylistResult(null);
    try {
      const res = await post<{ ok: boolean; playlistsSeen: number; playlistsCreated: number; tracksSeen: number; tracksAdded: number; exportedTracks: number; remotePlaylistsCreated: number; recordingsCreated: number; skipped: number; errors: string[] }>(
        "/api/integrations/plex/sync/playlists"
      );
      const summary = `${res.playlistsSeen} playlists — ${res.tracksAdded} importés, ${res.exportedTracks} exportés, ${res.remotePlaylistsCreated} créées dans Plex`;
      setPlaylistResult({ ok: res.ok, summary: res.errors.length ? `${summary} · ${res.errors[0]}` : summary });
      mutate();
    } catch (e) {
      setPlaylistResult({ ok: false, summary: e instanceof Error ? e.message : "Échec de synchronisation des playlists" });
    } finally {
      setSyncingPlaylists(false);
    }
  }

  async function runHistorySync() {
    setSyncingHistory(true);
    setHistoryResult(null);
    try {
      const res = await post<{ ok: boolean; entriesSeen: number; imported: number; unresolved: number; errors: string[] }>(
        "/api/integrations/plex/sync/history"
      );
      setHistoryResult({ ok: res.ok, summary: `${res.entriesSeen} événements — ${res.imported} importés, ${res.unresolved} sans morceau lié` });
      mutate();
    } catch (e) {
      setHistoryResult({ ok: false, summary: e instanceof Error ? e.message : "Échec de l'import d'historique" });
    } finally {
      setSyncingHistory(false);
    }
  }

  function updateMapping(index: number, patch: Partial<{ plexPrefix: string; localPrefix: string }>) {
    setMappings((prev) => (prev ?? []).map((m, i) => (i === index ? { ...m, ...patch } : m)));
  }

  async function saveMappings(next: { plexPrefix: string; localPrefix: string }[]) {
    setMappings(next);
    await save({ pathMappings: next.filter((m) => m.plexPrefix.trim() && m.localPrefix.trim()) });
  }

  return (
    <main className="mx-auto flex min-h-screen max-w-2xl flex-col gap-6 px-5 pt-8 pb-16 sm:px-8">
      <TopBar title="Plex" />
      <p className="float-in -mt-2 text-sm text-[var(--ink-soft)]">
        Intégration optionnelle — MuzziQ fonctionne entièrement sans Plex. Jamais une dépendance.
      </p>

      {/* Compte */}
      <div className="glass float-in flex flex-col gap-3 rounded-2xl p-4">
        <div className="flex items-center justify-between">
          <span className="text-xs font-semibold uppercase tracking-wide text-[var(--ink-dim)]">Compte Plex</span>
          {connected && <CheckCircle2 size={14} className="text-[var(--brand)]" />}
        </div>

        {connected ? (
          <div className="flex items-center justify-between">
            <span className="text-sm">Connecté en tant que <strong>{data.accountUsername}</strong></span>
            <button
              onClick={connectPlex}
              disabled={connecting}
              className="rounded-full border border-white/15 px-3 py-1.5 text-xs font-semibold text-[var(--ink-soft)] hover:border-[var(--brand)]/50 hover:text-[var(--brand)] disabled:opacity-50"
            >
              Changer de compte
            </button>
          </div>
        ) : (
          <>
            <button
              onClick={connectPlex}
              disabled={connecting}
              className="brand-gradient flex items-center justify-center gap-2 rounded-full px-4 py-2.5 text-[13px] font-bold text-black transition-transform active:scale-95 disabled:opacity-50"
            >
              <Link2 size={14} />
              {connecting ? "En attente d'autorisation…" : "Se connecter avec Plex"}
            </button>
            {pinCode && (
              <p className="text-xs text-[var(--ink-dim)]">
                Un onglet plex.tv s&apos;est ouvert. Si ce n&apos;est pas le cas, va sur{" "}
                <strong>plex.tv/link</strong> et entre le code <strong className="text-[var(--brand)]">{pinCode}</strong>.
              </p>
            )}
            {connectError && (
              <div className="flex items-center gap-1.5 text-xs text-red-400">
                <XCircle size={12} />
                {connectError}
              </div>
            )}
          </>
        )}
      </div>

      {/* Serveur */}
      {connected && (
        <div className="glass float-in flex flex-col gap-3 rounded-2xl p-4">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold uppercase tracking-wide text-[var(--ink-dim)]">Serveur</span>
            {serverChosen && <CheckCircle2 size={14} className="text-[var(--brand)]" />}
          </div>

          {serverChosen && <p className="text-sm">{data.serverName}</p>}

          <button
            onClick={loadServers}
            disabled={loadingServers}
            className="flex items-center justify-center gap-2 rounded-full border border-white/15 px-4 py-2.5 text-[13px] font-bold text-[var(--ink-soft)] transition-colors hover:border-[var(--brand)]/50 hover:text-[var(--brand)] disabled:opacity-40"
          >
            <RefreshCw size={14} className={loadingServers ? "animate-spin" : ""} />
            {loadingServers ? "Recherche…" : serverChosen ? "Changer de serveur" : "Découvrir mes serveurs Plex"}
          </button>

          {serverError && (
            <div className="flex items-center gap-1.5 text-xs text-red-400">
              <XCircle size={12} />
              {serverError}
            </div>
          )}

          {servers && (
            <div className="flex flex-col gap-2">
              {servers.length === 0 && <p className="text-xs text-[var(--ink-dim)]">Aucun serveur trouvé pour ce compte.</p>}
              {servers.map((s) => (
                <div key={s.machineIdentifier} className="rounded-lg border border-white/10 p-2">
                  <div className="mb-1 text-sm font-semibold">{s.name}{s.owned ? "" : " (partagé)"}</div>
                  <div className="flex flex-wrap gap-1.5">
                    {s.connections.map((c) => (
                      <button
                        key={c.uri}
                        onClick={() => selectServer(s, c.uri)}
                        disabled={selectingServer === c.uri}
                        className="rounded-full border border-white/15 px-2.5 py-1 text-[11px] text-[var(--ink-soft)] hover:border-[var(--brand)]/50 hover:text-[var(--brand)] disabled:opacity-50"
                      >
                        {selectingServer === c.uri ? "…" : c.local ? "local" : c.relay ? "relais" : "distant"}
                        <span className="ml-1 opacity-60">{new URL(c.uri).host}</span>
                      </button>
                    ))}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Sections musicales */}
      {serverChosen && (
        <div className="glass float-in flex flex-col gap-3 rounded-2xl p-4">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold uppercase tracking-wide text-[var(--ink-dim)]">Bibliothèques musicales</span>
            {data.musicSections?.length > 0 && <CheckCircle2 size={14} className="text-[var(--brand)]" />}
          </div>

          <button
            onClick={loadSections}
            disabled={loadingSections}
            className="flex items-center justify-center gap-2 rounded-full border border-white/15 px-4 py-2.5 text-[13px] font-bold text-[var(--ink-soft)] transition-colors hover:border-[var(--brand)]/50 hover:text-[var(--brand)] disabled:opacity-40"
          >
            <Music size={14} />
            {loadingSections ? "Chargement…" : "Lister les bibliothèques"}
          </button>

          {sections && (
            <div className="flex flex-col gap-1.5">
              {sections.length === 0 && <p className="text-xs text-[var(--ink-dim)]">Aucune bibliothèque de type musique sur ce serveur.</p>}
              {sections.map((s) => (
                <label key={s.key} className="flex items-center gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={selectedSections.has(s.key)}
                    onChange={(e) => {
                      const next = new Set(selectedSections);
                      if (e.target.checked) next.add(s.key);
                      else next.delete(s.key);
                      setSelectedSections(next);
                    }}
                  />
                  {s.title}
                </label>
              ))}
              <button
                onClick={saveSections}
                className="brand-gradient mt-1 self-start rounded-full px-4 py-2 text-[12px] font-bold text-black transition-transform active:scale-95"
              >
                Enregistrer la sélection
              </button>
            </div>
          )}
        </div>
      )}

      {/* Politique de synchronisation + sync */}
      {serverChosen && (
        <div className="glass float-in flex flex-col gap-3 rounded-2xl p-4">
          <span className="text-xs font-semibold uppercase tracking-wide text-[var(--ink-dim)]">Synchronisation</span>
          <select
            value={data.syncPolicy}
            onChange={(e) => save({ syncPolicy: e.target.value as PlexSyncPolicy })}
            className="rounded-lg border border-white/10 bg-black/30 px-3 py-2 text-sm"
          >
            <option value="OFF">Désactivée</option>
            <option value="IMPORT_ONLY">Import uniquement (Plex → MuzziQ)</option>
            <option value="EXPORT_ONLY">Export uniquement (MuzziQ → Plex)</option>
            <option value="BIDIRECTIONAL">Bidirectionnelle</option>
          </select>

          <button
            onClick={test}
            disabled={testing}
            className="rounded-full border border-white/15 px-4 py-2 text-[12px] font-bold text-[var(--ink-soft)] hover:border-[var(--brand)]/50 hover:text-[var(--brand)] disabled:opacity-50"
          >
            {testing ? "Test en cours…" : "Tester la connexion"}
          </button>
          {testResult && (
            <div className={`flex items-center gap-1.5 text-xs ${testResult.ok ? "text-[var(--brand)]" : "text-red-400"}`}>
              {testResult.ok ? <CheckCircle2 size={12} /> : <XCircle size={12} />}
              {testResult.detail}
            </div>
          )}

          <div className="mt-1 flex flex-col gap-2 border-t border-white/10 pt-3">
            <button
              onClick={runSync}
              disabled={syncing || data.musicSections?.length === 0}
              className="flex items-center justify-center gap-2 rounded-full border border-white/15 px-4 py-2.5 text-[13px] font-bold text-[var(--ink-soft)] transition-colors hover:border-[var(--brand)]/50 hover:text-[var(--brand)] disabled:opacity-40"
            >
              <RefreshCw size={14} className={syncing ? "animate-spin" : ""} />
              {syncing ? "Synchronisation…" : "Synchroniser la bibliothèque maintenant"}
            </button>
            {syncResult && (
              <div className={`flex items-center gap-1.5 text-xs ${syncResult.ok ? "text-[var(--brand)]" : "text-red-400"}`}>
                {syncResult.ok ? <CheckCircle2 size={12} /> : <XCircle size={12} />}
                {syncResult.summary}
              </div>
            )}
            {data.lastLibrarySync && !syncResult && (
              <p className="text-xs text-[var(--ink-dim)]">
                Dernier sync : {new Date(data.lastLibrarySync.at).toLocaleString("fr-FR")} — {data.lastLibrarySync.summary}
              </p>
            )}

            <button
              onClick={runPlaylistSync}
              disabled={syncingPlaylists}
              className="flex items-center justify-center gap-2 rounded-full border border-white/15 px-4 py-2.5 text-[13px] font-bold text-[var(--ink-soft)] transition-colors hover:border-[var(--brand)]/50 hover:text-[var(--brand)] disabled:opacity-40"
            >
              <RefreshCw size={14} className={syncingPlaylists ? "animate-spin" : ""} />
              {syncingPlaylists ? "Import des playlists…" : "Importer les playlists Plex"}
            </button>
            <p className="text-xs text-[var(--ink-dim)]">
              Métadonnées uniquement : MuzziQ importe les titres dans des playlists « Plex · … » et ne lit jamais la musique depuis Plex.
            </p>
            {playlistResult && (
              <div className={`flex items-center gap-1.5 text-xs ${playlistResult.ok ? "text-[var(--brand)]" : "text-red-400"}`}>
                {playlistResult.ok ? <CheckCircle2 size={12} /> : <XCircle size={12} />}
                {playlistResult.summary}
              </div>
            )}
            {data.lastPlaylistSync && !playlistResult && (
              <p className="text-xs text-[var(--ink-dim)]">
                Dernier import de playlists : {new Date(data.lastPlaylistSync.at).toLocaleString("fr-FR")} — {data.lastPlaylistSync.summary}
              </p>
            )}

            <button
              onClick={runHistorySync}
              disabled={syncingHistory}
              className="flex items-center justify-center gap-2 rounded-full border border-white/15 px-4 py-2.5 text-[13px] font-bold text-[var(--ink-soft)] transition-colors hover:border-[var(--brand)]/50 hover:text-[var(--brand)] disabled:opacity-40"
            >
              <RefreshCw size={14} className={syncingHistory ? "animate-spin" : ""} />
              {syncingHistory ? "Import en cours…" : "Importer l'historique d'écoute"}
            </button>
            {historyResult && (
              <div className={`flex items-center gap-1.5 text-xs ${historyResult.ok ? "text-[var(--brand)]" : "text-red-400"}`}>
                {historyResult.ok ? <CheckCircle2 size={12} /> : <XCircle size={12} />}
                {historyResult.summary}
              </div>
            )}
            {data.lastHistorySync && !historyResult && (
              <p className="text-xs text-[var(--ink-dim)]">
                Dernier import : {new Date(data.lastHistorySync.at).toLocaleString("fr-FR")} — {data.lastHistorySync.summary}
              </p>
            )}
          </div>
        </div>
      )}

      {/* Mapping de chemins */}
      <div className="glass float-in flex flex-col gap-3 rounded-2xl p-4">
        <span className="text-xs font-semibold uppercase tracking-wide text-[var(--ink-dim)]">Mapping de chemins</span>
        <p className="text-xs text-[var(--ink-soft)]">
          Si ton NAS monte le même dossier différemment côté Plex et côté MuzziQ (ex. Plex voit{" "}
          <code>/plex/music</code> pour le dossier que MuzziQ voit comme <code>/music</code>), déclare-le ici pour
          que la correspondance de chemin fonctionne pendant la synchronisation.
        </p>

        <div className="flex flex-col gap-2">
          {(mappings ?? []).map((m, i) => (
            <div key={i} className="flex items-center gap-1.5">
              <input
                value={m.plexPrefix}
                onChange={(e) => updateMapping(i, { plexPrefix: e.target.value })}
                placeholder="/plex/music"
                className="min-w-0 flex-1 rounded-lg border border-white/10 bg-black/30 px-2.5 py-1.5 text-xs font-mono outline-none focus:border-[var(--brand)]"
              />
              <ChevronDown size={12} className="flex-shrink-0 -rotate-90 text-[var(--ink-dim)]" />
              <input
                value={m.localPrefix}
                onChange={(e) => updateMapping(i, { localPrefix: e.target.value })}
                placeholder="/music"
                className="min-w-0 flex-1 rounded-lg border border-white/10 bg-black/30 px-2.5 py-1.5 text-xs font-mono outline-none focus:border-[var(--brand)]"
              />
              <button
                onClick={() => saveMappings((mappings ?? []).filter((_, idx) => idx !== i))}
                className="flex-shrink-0 rounded-full p-1.5 text-[var(--ink-dim)] hover:text-red-400"
                aria-label="Supprimer"
              >
                <Trash2 size={13} />
              </button>
            </div>
          ))}
          <div className="flex gap-2">
            <button
              onClick={() => setMappings((prev) => [...(prev ?? []), { plexPrefix: "", localPrefix: "" }])}
              className="flex items-center gap-1.5 rounded-full border border-white/15 px-3 py-1.5 text-xs font-semibold text-[var(--ink-soft)] hover:border-[var(--brand)]/50 hover:text-[var(--brand)]"
            >
              <Plus size={12} /> Ajouter
            </button>
            <button
              onClick={() => saveMappings(mappings ?? [])}
              className="rounded-full border border-white/15 px-3 py-1.5 text-xs font-semibold text-[var(--ink-soft)] hover:border-[var(--brand)]/50 hover:text-[var(--brand)]"
            >
              Enregistrer
            </button>
          </div>
        </div>
      </div>

      {/* Configuration manuelle (avancé) */}
      <div className="glass float-in flex flex-col gap-2 rounded-2xl p-4">
        <button onClick={() => setShowAdvanced((v) => !v)} className="flex items-center justify-between text-left">
          <span className="text-xs font-semibold uppercase tracking-wide text-[var(--ink-dim)]">Configuration manuelle (avancé)</span>
          <ChevronDown size={14} className={`text-[var(--ink-dim)] transition-transform ${showAdvanced ? "rotate-180" : ""}`} />
        </button>
        {showAdvanced && (
          <div className="mt-1 flex flex-col gap-2">
            <p className="text-xs text-[var(--ink-soft)]">
              Utile si la connexion OAuth vers plex.tv n&apos;est pas possible depuis ce réseau — sinon, utilise
              &quot;Se connecter avec Plex&quot; ci-dessus, plus fiable (URL de serveur découverte automatiquement).
            </p>
            <input
              defaultValue={data.serverUrl}
              onBlur={(e) => save({ serverUrl: e.target.value })}
              placeholder="http://192.168.1.10:32400"
              className="rounded-lg border border-white/10 bg-black/30 px-3 py-2 text-sm outline-none focus:border-[var(--brand)]"
            />
            <input
              type="password"
              onBlur={(e) => {
                if (e.target.value) save({ token: e.target.value });
              }}
              placeholder="X-Plex-Token"
              className="rounded-lg border border-white/10 bg-black/30 px-3 py-2 text-sm outline-none focus:border-[var(--brand)]"
            />
          </div>
        )}
      </div>
    </main>
  );
}
