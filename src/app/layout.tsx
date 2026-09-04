import type { Metadata } from "next";
import "./globals.css";
import { PlayerProvider } from "@/components/PlayerContext";
import { MiniPlayer } from "@/components/MiniPlayer";
import { DesktopPlayer } from "@/components/DesktopPlayer";
import { BottomNav } from "@/components/BottomNav";
import { Sidebar } from "@/components/Sidebar";
import { ContextPanel } from "@/components/ContextPanel";

export const metadata: Metadata = {
  title: "MuzziQ",
  description: "Plateforme musicale personnelle — catalogue unifié, lecture instantanée, bibliothèque locale.",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="fr">
      <body className="min-h-screen pb-32 font-sans antialiased lg:pb-28">
        <PlayerProvider>
          <Sidebar />
          <div className="lg:pl-64 xl:pr-80">{children}</div>
          <MiniPlayer />
          <DesktopPlayer />
          <ContextPanel />
          <div className="lg:hidden">
            <BottomNav />
          </div>
        </PlayerProvider>
      </body>
    </html>
  );
}
