import type { Metadata } from "next";
import "./globals.css";
import { PlayerProvider } from "@/components/PlayerContext";
import { MiniPlayer } from "@/components/MiniPlayer";
import { BottomNav } from "@/components/BottomNav";

export const metadata: Metadata = {
  title: "MUZZIK",
  description: "Plateforme musicale personnelle — catalogue unifié, lecture instantanée, bibliothèque locale.",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="fr">
      <body className="min-h-screen pb-28 antialiased">
        <PlayerProvider>
          {children}
          <MiniPlayer />
          <BottomNav />
        </PlayerProvider>
      </body>
    </html>
  );
}
