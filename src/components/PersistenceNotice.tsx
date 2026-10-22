"use client";

import { useEffect } from "react";
import { flushChanges, getPersistenceStatus, usePersistenceStatus } from "@/lib/store";

export function PersistenceNotice() {
  const status = usePersistenceStatus();
  useEffect(() => {
    const beforeUnload = (event: BeforeUnloadEvent) => {
      if (!getPersistenceStatus().unsaved) return;
      event.preventDefault();
      event.returnValue = "";
    };
    const online = () => { void flushChanges(); };
    window.addEventListener("beforeunload", beforeUnload);
    window.addEventListener("online", online);
    return () => {
      window.removeEventListener("beforeunload", beforeUnload);
      window.removeEventListener("online", online);
    };
  }, []);

  if (status.locked) return (
    <div role="status" className="fixed inset-0 z-[100] grid place-items-center bg-[var(--bg)]/90 p-6 text-center font-semibold" aria-live="polite">
      Saving changes and signing out…
    </div>
  );
  if (!status.unsaved) return null;
  return (
    <aside role={status.phase === "error" ? "alert" : "status"} className="sticky top-0 z-50 border-b border-[var(--line)] bg-[var(--card)] px-4 py-3 text-sm">
      <div className="mx-auto max-w-xl">
        {status.phase === "error" ? (
          <>
            <p className="font-semibold text-red-600 dark:text-red-400">Changes have not been saved to the database.</p>
            <p className="mt-1 text-[var(--muted)]">{status.error} Keep this page open and retry; unsaved changes will be lost if you close it.</p>
            <button onClick={() => void flushChanges()} className="mt-2 rounded-lg border border-[var(--line)] px-4 py-2 font-semibold">Retry saving</button>
          </>
        ) : <p>Saving changes to the database… Please keep this page open.</p>}
      </div>
    </aside>
  );
}
