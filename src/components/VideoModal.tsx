"use client";

import { useEffect, useRef } from "react";
import { createPortal } from "react-dom";
import { youtubeId } from "@/lib/plan";

// Plays a tutorial inside the app instead of opening YouTube in a new tab.
export function VideoModal({
  url,
  title,
  onClose,
}: {
  url: string;
  title: string;
  onClose: () => void;
}) {
  const id = youtubeId(url);
  const onCloseRef = useRef(onClose);
  onCloseRef.current = onClose;

  useEffect(() => {
    // Phone back button closes the video instead of leaving the workout.
    history.pushState({ ...history.state, videoModal: true }, "");
    const onPop = () => onCloseRef.current();
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && history.back();
    window.addEventListener("popstate", onPop);
    window.addEventListener("keydown", onKey);
    const overflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      window.removeEventListener("popstate", onPop);
      window.removeEventListener("keydown", onKey);
      document.body.style.overflow = overflow;
    };
  }, []);

  const close = () => history.back();

  return createPortal(
    <div
      role="dialog"
      aria-modal="true"
      aria-label={`${title} tutorial`}
      className="fixed inset-0 z-50 flex flex-col justify-center bg-black/90 px-3 pt-[env(safe-area-inset-top)] pb-[env(safe-area-inset-bottom)]"
      onClick={close}
    >
      <div className="mx-auto w-full max-w-3xl" onClick={(e) => e.stopPropagation()}>
        <div className="mb-3 flex items-center gap-3 text-white">
          <h2 className="min-w-0 flex-1 truncate text-base font-semibold">{title}</h2>
          <button
            onClick={close}
            aria-label="Close video"
            className="grid h-11 w-11 shrink-0 place-items-center rounded-full bg-white/15 active:scale-90"
          >
            <svg viewBox="0 0 24 24" className="h-6 w-6" fill="none" stroke="currentColor" strokeWidth={2.4} aria-hidden>
              <path d="M6 6l12 12M18 6 6 18" strokeLinecap="round" />
            </svg>
          </button>
        </div>
        <div className="aspect-video w-full overflow-hidden rounded-2xl bg-black">
          {id && (
            <iframe
              src={`https://www.youtube-nocookie.com/embed/${id}?autoplay=1&playsinline=1&rel=0`}
              title={`${title} tutorial`}
              allow="autoplay; encrypted-media; picture-in-picture; fullscreen"
              allowFullScreen
              className="h-full w-full"
            />
          )}
        </div>
        <p className="mt-3 text-center text-xs text-white/60">
          Video not playing?{" "}
          <a href={url} target="_blank" rel="noopener noreferrer" className="underline">
            Open in YouTube
          </a>
        </p>
      </div>
    </div>,
    document.body,
  );
}
