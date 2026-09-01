import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "MUZZIK",
  description: "Plateforme musicale personnelle — catalogue unifié, lecture instantanée, bibliothèque locale.",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="fr">
      <body className="min-h-screen antialiased">{children}</body>
    </html>
  );
}
