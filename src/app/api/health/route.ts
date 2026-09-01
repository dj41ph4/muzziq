import { NextResponse } from "next/server";
import { DATA_DIR } from "@/lib/config";

// Every API route must opt out of static generation — Next.js will otherwise
// try to prerender this at build time, before MUZZIK_DATA_DIR even exists.
export const dynamic = "force-dynamic";

export async function GET() {
  return NextResponse.json({
    status: "ok",
    service: "muzzik",
    version: process.env.npm_package_version ?? "0.1.0",
    dataDir: DATA_DIR,
    time: new Date().toISOString(),
  });
}
