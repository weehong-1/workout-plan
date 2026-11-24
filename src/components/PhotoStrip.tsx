"use client";

import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { MAX_PHOTOS, photoUrls, uploadPhoto } from "@/lib/photos";
import { usePersistenceStatus } from "@/lib/store";

// Thumbnails for a list of stored photo paths. With onAdd/onRemove, it also lets you
// add photos (camera or library) and remove them. The parent applies each
// change to its latest state, so overlapping uploads can't overwrite each other.
export function PhotoStrip({
  paths,
  label,
  onAdd,
  onRemove,
}: {
  paths: string[];
  label: string;
  onAdd?: (added: string[]) => void;
  onRemove?: (path: string) => void;
}) {
  const editable = !!onAdd && !!onRemove;
  const { userId, ready, locked } = usePersistenceStatus();
  const [urls, setUrls] = useState<Record<string, string>>({});
  const [loadError, setLoadError] = useState("");
  const [uploading, setUploading] = useState(0);
  const [error, setError] = useState("");
  const [viewing, setViewing] = useState<string | null>(null);
  const input = useRef<HTMLInputElement>(null);
  const key = paths.join("|");

  useEffect(() => {
    if (!paths.length) return;
    let live = true;
    photoUrls(paths)
      .then((u) => { if (live) { setUrls(u); setLoadError(""); } })
      .catch((e) => { if (live) setLoadError(e instanceof Error ? e.message : String(e)); });
    return () => { live = false; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key]);

  if (!editable && !paths.length) return null;
  const canAdd = editable && !!userId && ready && !locked && paths.length + uploading < MAX_PHOTOS;

  const add = async (files: FileList | null) => {
    if (!files?.length || !userId || !onAdd) return;
    const picked = [...files].slice(0, MAX_PHOTOS - paths.length - uploading);
    setError("");
    setUploading((n) => n + picked.length);
    const added: string[] = [];
    for (const file of picked) {
      try {
        added.push(await uploadPhoto(userId, file));
      } catch (e) {
        setError(e instanceof Error ? e.message : "Upload failed. Please try again.");
      } finally {
        setUploading((n) => n - 1);
      }
    }
    if (added.length) onAdd(added);
  };

  const remove = (path: string) => {
    if (!onRemove || !confirm("Remove this photo?")) return;
    onRemove(path);
  };

  return (
    <div>
      <div className="flex flex-wrap gap-2" role="list" aria-label={label}>
        {paths.map((path, i) => (
          <div key={path} role="listitem" className="relative">
            <button
              type="button"
              onClick={() => setViewing(path)}
              aria-label={`View photo ${i + 1}`}
              className="block h-16 w-16 overflow-hidden rounded-xl border border-[var(--line)] bg-[var(--soft)]"
            >
              {urls[path] && (
                // eslint-disable-next-line @next/next/no-img-element
                <img src={urls[path]} alt="" className="h-full w-full object-cover" loading="lazy" />
              )}
            </button>
            {editable && (
              <button
                type="button"
                onClick={() => remove(path)}
                aria-label={`Remove photo ${i + 1}`}
                className="absolute -top-2 -right-2 grid h-6 w-6 place-items-center rounded-full bg-[var(--fg)] text-[var(--bg)] shadow"
              >
                <svg viewBox="0 0 24 24" className="h-3.5 w-3.5" fill="none" stroke="currentColor" strokeWidth={3} aria-hidden>
                  <path d="M6 6l12 12M18 6 6 18" strokeLinecap="round" />
                </svg>
              </button>
            )}
          </div>
        ))}
        {Array.from({ length: uploading }, (_, i) => (
          <div key={`up-${i}`} role="listitem" aria-label="Uploading photo" className="grid h-16 w-16 animate-pulse place-items-center rounded-xl bg-[var(--soft)] text-[10px] text-[var(--muted)]">
            Uploading
          </div>
        ))}
        {editable && (
          <>
            <button
              type="button"
              onClick={() => input.current?.click()}
              disabled={!canAdd}
              className="flex h-16 w-16 flex-col items-center justify-center gap-0.5 rounded-xl border border-dashed border-[var(--line)] text-[11px] font-medium text-[var(--muted)] active:scale-95 disabled:opacity-40"
            >
              <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth={2} aria-hidden>
                <path d="M4 8h3l2-3h6l2 3h3v11H4z" strokeLinejoin="round" />
                <circle cx="12" cy="13" r="3.5" />
              </svg>
              Add photo
            </button>
            <input
              ref={input}
              type="file"
              accept="image/*"
              multiple
              hidden
              onChange={(e) => { void add(e.target.files); e.target.value = ""; }}
            />
          </>
        )}
      </div>
      {(error || loadError) && (
        <p role="alert" className="mt-1.5 text-xs text-red-600 dark:text-red-400">{error || `Photos could not be loaded: ${loadError}`}</p>
      )}
      {viewing && urls[viewing] && <PhotoViewer url={urls[viewing]} label={label} onClose={() => setViewing(null)} />}
    </div>
  );
}

function PhotoViewer({ url, label, onClose }: { url: string; label: string; onClose: () => void }) {
  const onCloseRef = useRef(onClose);
  onCloseRef.current = onClose;

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onCloseRef.current();
    window.addEventListener("keydown", onKey);
    const overflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      window.removeEventListener("keydown", onKey);
      document.body.style.overflow = overflow;
    };
  }, []);

  return createPortal(
    <div
      role="dialog"
      aria-modal="true"
      aria-label={label}
      onClick={onClose}
      className="fixed inset-0 z-50 flex flex-col items-center justify-center bg-black/90 p-3 pt-[env(safe-area-inset-top)] pb-[env(safe-area-inset-bottom)]"
    >
      <button
        type="button"
        onClick={onClose}
        aria-label="Close photo"
        className="absolute top-[max(0.75rem,env(safe-area-inset-top))] right-3 grid h-11 w-11 place-items-center rounded-full bg-white/15 text-white"
      >
        <svg viewBox="0 0 24 24" className="h-6 w-6" fill="none" stroke="currentColor" strokeWidth={2.4} aria-hidden>
          <path d="M6 6l12 12M18 6 6 18" strokeLinecap="round" />
        </svg>
      </button>
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img src={url} alt="" className="max-h-full max-w-full rounded-xl object-contain" onClick={(e) => e.stopPropagation()} />
    </div>,
    document.body,
  );
}
