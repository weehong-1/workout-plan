import type { Metadata, Viewport } from "next";
import { Inter, Space_Grotesk } from "next/font/google";
import { AuthGate } from "@/components/AuthGate";
import { BottomNav } from "@/components/BottomNav";
import { PersistenceNotice } from "@/components/PersistenceNotice";
import "./globals.css";

const inter = Inter({ subsets: ["latin"], variable: "--font-inter", display: "swap" });
const spaceGrotesk = Space_Grotesk({ subsets: ["latin"], weight: ["500", "600", "700"], variable: "--font-space-grotesk", display: "swap" });

export const metadata: Metadata = {
  title: "Training Plan",
  description: "Your training rotation: today's weights, fast set logging and progress per lift.",
  appleWebApp: { capable: true, title: "Training", statusBarStyle: "black-translucent" },
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  viewportFit: "cover",
  themeColor: "#0f0f10",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" className={`${inter.variable} ${spaceGrotesk.variable}`}>
      <body className="min-h-dvh antialiased">
        <AuthGate>
          <PersistenceNotice />
          <main className="mx-auto max-w-xl px-4 pt-[calc(env(safe-area-inset-top)+1rem)] pb-[calc(env(safe-area-inset-bottom)+6rem)] lg:max-w-6xl lg:px-8">
            {children}
          </main>
          <BottomNav />
        </AuthGate>
      </body>
    </html>
  );
}
