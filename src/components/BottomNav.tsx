"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

// Three jobs: what to lift today, whether you're getting stronger, and your
// body. Account sits in Today's header; Nutrition lives under Body.
const TABS = [
  {
    href: "/",
    label: "Today",
    match: (p: string) => p === "/" || p.startsWith("/day") || p.startsWith("/plan") || p.startsWith("/account"),
    icon: <path d="M6.5 6.5v11M17.5 6.5v11M3 9.5v5M21 9.5v5M6.5 12h11" />,
  },
  {
    href: "/progress",
    label: "Progress",
    match: (p: string) => p.startsWith("/progress"),
    icon: (
      <>
        <path d="M3 17l6-6 4 4 8-8" />
        <path d="M15 7h6v6" />
      </>
    ),
  },
  {
    href: "/weight",
    label: "Body",
    match: (p: string) => p.startsWith("/weight") || p.startsWith("/nutrition"),
    icon: (
      <>
        <path d="M4 20h16M6 20V9a6 6 0 0 1 12 0v11" />
        <path d="m12 9 2-2" />
      </>
    ),
  },
];

export function BottomNav() {
  const pathname = usePathname();
  // The workout screen is full focus: no tab bar while logging sets.
  if (/^\/day\/[^/]+$/.test(pathname)) return null;
  // Trainer links are read-only pages for someone without an account.
  if (pathname.startsWith("/share/")) return null;
  return (
    <nav aria-label="Main" className="fixed inset-x-0 bottom-0 z-40 border-t border-[var(--line)] bg-[var(--bg)]/95 pb-[env(safe-area-inset-bottom)] backdrop-blur-md">
      <ul className="mx-auto flex h-16 max-w-xl">
        {TABS.map((t) => {
          const active = t.match(pathname);
          return (
            <li key={t.href} className="flex-1">
              <Link
                href={t.href}
                aria-current={active ? "page" : undefined}
                className={`flex h-full flex-col items-center justify-center gap-1 text-xs ${
                  active ? "font-bold text-[var(--accent)]" : "font-semibold text-[var(--muted)]"
                }`}
              >
                <svg
                  viewBox="0 0 24 24"
                  className="h-6 w-6"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth={2}
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  aria-hidden
                >
                  {t.icon}
                </svg>
                {t.label}
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
