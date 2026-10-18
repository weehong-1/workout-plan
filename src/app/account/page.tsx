import type { Metadata } from "next";
import { AccountView } from "@/components/AccountView";

export const metadata: Metadata = { title: "Account" };

export default function AccountPage() {
  return (
    <>
      <header className="mb-5">
        <h1 className="text-3xl font-bold tracking-tight">Backup &amp; sync</h1>
        <p className="mt-1 text-sm text-[var(--muted)]">Keep your training safe and use it on any device.</p>
      </header>
      <AccountView />
    </>
  );
}
