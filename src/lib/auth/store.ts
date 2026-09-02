import fs from "node:fs";
import path from "node:path";
import { randomBytes, createHmac } from "node:crypto";
import { readJsonCached, writeJsonCached } from "@/lib/fsJsonCache";
import { DATA_DIR } from "@/lib/config";
import type { User } from "./types";
import { SESSION_SECRET_ENV } from "./constants";

/** Store utilisateurs + sessions (porté depuis Movviz src/lib/auth/store.ts, sans l'event bus qui n'existe pas encore côté MuzziQ). */

const USERS_FILE = path.join(DATA_DIR, "users.json");
const SESSIONS_FILE = path.join(DATA_DIR, "sessions.json");
const SIGNING_KEY_FILE = path.join(DATA_DIR, ".session-secret");
const SESSION_TTL_MS = 30 * 24 * 60 * 60 * 1000; // 30 jours

function hmacSign(token: string): string {
  const raw = process.env[SESSION_SECRET_ENV];
  if (raw) return createHmac("sha256", raw).update(token).digest("hex");
  try {
    const key = fs.readFileSync(SIGNING_KEY_FILE, "utf-8").trim();
    if (key) return createHmac("sha256", key).update(token).digest("hex");
  } catch {
    /* pas de clé encore générée */
  }
  const newKey = randomBytes(32).toString("hex");
  fs.mkdirSync(DATA_DIR, { recursive: true });
  fs.writeFileSync(SIGNING_KEY_FILE, newKey, "utf-8");
  return createHmac("sha256", newKey).update(token).digest("hex");
}

// ---- Utilisateurs ----

export function loadUsers(): User[] {
  return readJsonCached<User[]>(USERS_FILE, []);
}
function saveUsers(list: User[]) {
  writeJsonCached(USERS_FILE, list);
}
export function hasAnyUser(): boolean {
  return loadUsers().length > 0;
}
export function getUserById(id: string): User | null {
  return loadUsers().find((u) => u.id === id) ?? null;
}
export function getUserByUsername(username: string): User | null {
  const norm = username.trim().toLowerCase();
  return loadUsers().find((u) => u.username.toLowerCase() === norm) ?? null;
}
export function addUser(user: User): User {
  const list = loadUsers();
  list.push(user);
  saveUsers(list);
  return user;
}

// ---- Sessions ----

interface SessionRecord {
  token: string;
  userId: string;
  expiresAt: number;
}

function loadSessions(): SessionRecord[] {
  return readJsonCached<SessionRecord[]>(SESSIONS_FILE, []);
}
function saveSessions(list: SessionRecord[]) {
  writeJsonCached(SESSIONS_FILE, list);
}

export function createSession(userId: string): { token: string; expiresAt: number } {
  const raw = randomBytes(32).toString("hex");
  const token = raw + "." + hmacSign(raw);
  const expiresAt = Date.now() + SESSION_TTL_MS;
  const list = loadSessions().filter((s) => s.expiresAt > Date.now());
  list.push({ token: raw, userId, expiresAt });
  saveSessions(list);
  return { token, expiresAt };
}

const HEX64 = /^[0-9a-f]{64}$/;

export function resolveSession(cookie: string | undefined | null): User | null {
  if (!cookie) return null;
  const dot = cookie.indexOf(".");
  if (dot <= 0) return null;
  const raw = cookie.slice(0, dot);
  const sig = cookie.slice(dot + 1);
  if (!HEX64.test(raw) || !(sig.length === 64 && HEX64.test(sig))) return null;

  const session = loadSessions().find((s) => s.token === raw);
  if (!session || session.expiresAt < Date.now()) return null;
  if (sig !== hmacSign(raw)) return null;
  return getUserById(session.userId);
}

export function destroySession(token: string | undefined | null) {
  if (!token) return;
  const dot = token.indexOf(".");
  const raw = dot > 0 ? token.slice(0, dot) : token;
  saveSessions(loadSessions().filter((s) => s.token !== raw));
}
