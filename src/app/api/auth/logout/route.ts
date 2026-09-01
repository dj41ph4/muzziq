import { NextResponse } from "next/server";
import { destroySession } from "@/lib/auth/store";
import { SESSION_COOKIE, clearSessionCookie } from "@/lib/auth/session";

export const dynamic = "force-dynamic";

export async function POST(req: Request) {
  const cookie = req.headers.get("cookie") ?? "";
  const match = cookie.match(new RegExp(`${SESSION_COOKIE}=([^;]+)`));
  destroySession(match?.[1] ? decodeURIComponent(match[1]) : null);
  const res = NextResponse.json({ ok: true });
  clearSessionCookie(res);
  return res;
}
