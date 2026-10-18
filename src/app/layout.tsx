import type { Metadata, Viewport } from "next";
import { BottomNav } from "@/components/BottomNav";
import { RestTimerProvider } from "@/components/RestTimer";
import "./globals.css";

export const metadata: Metadata = {
  title: "Training Plan",
  description: "Push / Pull / Mixed / Arm day training plan with supersets, rest timer and log.",
  appleWebApp: { capable: true, title: "Training", statusBarStyle: "default" },
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  viewportFit: "cover",
  themeColor: [
    { media: "(prefers-color-scheme: light)", color: "#f6f6f4" },
    { media: "(prefers-color-scheme: dark)", color: "#0c0c0e" },
  ],
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body className="min-h-dvh antialiased">
        <RestTimerProvider>
          <main className="mx-auto max-w-xl px-4 pt-[calc(env(safe-area-inset-top)+1rem)] pb-[calc(env(safe-area-inset-bottom)+11rem)]">
            {children}
          </main>
          <BottomNav />
        </RestTimerProvider>
      </body>
    </html>
  );
}
