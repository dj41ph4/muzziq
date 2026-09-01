import { NextResponse } from "next/server";
import { listIndexers, addIndexer } from "@/lib/acquisition/indexers/store";

export const dynamic = "force-dynamic";

export async function GET() {
  // Jamais l'API key en clair côté client une fois enregistrée — seuls les
  // 4 derniers caractères, pour confirmer visuellement laquelle est en place.
  const redacted = listIndexers().map((ix) => ({ ...ix, apiKey: ix.apiKey ? `••••${ix.apiKey.slice(-4)}` : "", password: "" }));
  return NextResponse.json({ indexers: redacted });
}

export async function POST(req: Request) {
  const body = await req.json();
  if (!body.name || !body.baseUrl) {
    return NextResponse.json({ error: "name et baseUrl requis" }, { status: 400 });
  }
  const indexer = addIndexer({
    name: body.name,
    baseUrl: body.baseUrl,
    authType: body.authType ?? "apikey",
    apiKey: body.apiKey ?? "",
    username: body.username ?? "",
    password: body.password ?? "",
    categories: body.categories ?? [],
    enabled: true,
    caps: null,
  });
  return NextResponse.json(indexer);
}
