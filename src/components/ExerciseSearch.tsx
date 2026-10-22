"use client";

import { useEffect, useId, useMemo, useRef, useState } from "react";
import type { AddedExercise } from "@/lib/added-exercise";
import { searchExercises, type ExerciseDefinition } from "@/lib/exercise-library";

export function ExerciseSearch({ query, onQueryChange, added, onSelect, onCreate }: {
  query: string;
  onQueryChange: (query: string) => void;
  added: AddedExercise[];
  onSelect: (exercise: ExerciseDefinition) => void;
  onCreate: () => void;
}) {
  const id = useId();
  const input = useRef<HTMLInputElement>(null);
  const [open, setOpen] = useState(false);
  const [activeIndex, setActiveIndex] = useState(-1);
  const results = useMemo(() => searchExercises(query.trim()), [query]);
  const addedIds = new Set(added.filter((exercise) => exercise.source === "free-exercise-db").map((exercise) => exercise.exerciseId));
  // Custom creation is the final option, so it also works with the keyboard.
  const availableIndices = results.flatMap((exercise, index) => addedIds.has(exercise.id) ? [] : [index]).concat(results.length);
  const active = open && availableIndices.includes(activeIndex) ? activeIndex : -1;
  const optionId = (index: number) => `${id}-option-${index}`;
  const status = query.trim().length < 2 ? "Type at least 2 characters to search."
    : results.length === 0 ? "No library exercises found. Try another search or create your own."
    : `${results.length} results${results.length === 40 ? " — refine your search for more specific matches" : ""}`;

  useEffect(() => {
    if (active >= 0) document.getElementById(`${id}-option-${active}`)?.scrollIntoView({ block: "nearest" });
  }, [active, id]);

  function choose(index: number) {
    if (!availableIndices.includes(index)) return;
    setOpen(false);
    setActiveIndex(-1);
    if (index === results.length) onCreate();
    else onSelect(results[index]);
  }

  function updateQuery(value: string) {
    onQueryChange(value);
    setActiveIndex(-1);
    setOpen(true);
  }

  return (
    <div onBlur={(event) => {
      if (!event.currentTarget.contains(event.relatedTarget)) {
        setOpen(false);
        setActiveIndex(-1);
      }
    }}>
      <label htmlFor={`${id}-query`} className="text-sm font-semibold">Search exercises</label>
      <div className={`mt-2 overflow-hidden rounded-xl border bg-[var(--bg)] focus-within:border-[var(--accent)] ${open ? "border-[var(--accent)]" : "border-[var(--line)]"}`}>
        <div className="flex items-center">
          <input ref={input} id={`${id}-query`} type="text" role="combobox"
            aria-autocomplete="list" aria-expanded={open} aria-controls={`${id}-results`}
            aria-activedescendant={active >= 0 ? optionId(active) : undefined}
            aria-describedby={`${id}-hint`} autoComplete="off" maxLength={120} value={query}
            onFocus={() => setOpen(true)} onClick={() => setOpen(true)}
            onChange={(event) => updateQuery(event.target.value)}
            onKeyDown={(event) => {
              if (event.nativeEvent.isComposing) return;
              if (event.key === "ArrowDown" || event.key === "ArrowUp") {
                event.preventDefault();
                setOpen(true);
                const current = open ? availableIndices.indexOf(active) : -1;
                const next = current < 0 ? (event.key === "ArrowDown" ? 0 : availableIndices.length - 1)
                  : (current + (event.key === "ArrowDown" ? 1 : -1) + availableIndices.length) % availableIndices.length;
                setActiveIndex(availableIndices[next]);
              } else if (open && active >= 0 && (event.key === "Home" || event.key === "End")) {
                event.preventDefault();
                setActiveIndex(event.key === "Home" ? availableIndices[0] : availableIndices[availableIndices.length - 1]);
              } else if (event.key === "Enter") {
                event.preventDefault();
                if (!open) setOpen(true);
                else if (active >= 0) choose(active);
              } else if (event.key === "Escape" && open) {
                event.preventDefault();
                event.stopPropagation();
                setOpen(false);
                setActiveIndex(-1);
              } else if (event.key === "Tab") {
                setOpen(false);
                setActiveIndex(-1);
              }
            }}
            placeholder="Search by name, equipment or muscle"
            className="h-12 min-w-0 flex-1 bg-transparent pl-3 pr-1 outline-none" />
          {query && <button type="button" aria-label="Clear exercise search" onClick={() => { updateQuery(""); input.current?.focus(); }} className="grid h-11 w-9 shrink-0 place-items-center rounded-lg text-[var(--muted)] focus-visible:outline-2 focus-visible:outline-[var(--accent)]">×</button>}
          <button type="button" tabIndex={-1} aria-label={open ? "Hide exercise options" : "Show exercise options"} aria-expanded={open} aria-controls={`${id}-results`}
            onMouseDown={(event) => event.preventDefault()}
            onClick={() => { input.current?.focus(); setOpen(!open); setActiveIndex(-1); }}
            className="grid h-11 w-11 shrink-0 place-items-center rounded-lg text-[var(--muted)] focus-visible:outline-2 focus-visible:outline-[var(--accent)]">
            <svg aria-hidden="true" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className={open ? "rotate-180" : ""}><path d="m6 9 6 6 6-6" /></svg>
          </button>
        </div>
        <div hidden={!open} className="border-t border-[var(--line)] bg-[var(--card)]">
          <p role="status" aria-live="polite" className="border-b border-[var(--line)] px-3 py-2 text-xs text-[var(--muted)]">{status}</p>
          <ul id={`${id}-results`} role="listbox" aria-label="Exercise options"
            className="max-h-[min(18rem,40dvh)] overflow-y-auto overscroll-contain p-1"
            onMouseDown={(event) => event.preventDefault()}>
            {results.map((exercise, index) => {
              const exists = addedIds.has(exercise.id);
              return <li key={exercise.id} id={optionId(index)} role="option" aria-selected={active === index} aria-disabled={exists}
                onClick={() => choose(index)} onMouseMove={() => { if (!exists) setActiveIndex(index); }}
                className={`flex min-h-14 items-center justify-between gap-3 rounded-lg px-3 py-2 text-sm ${exists ? "cursor-not-allowed opacity-45" : "cursor-pointer hover:bg-[var(--soft)]"} ${active === index ? "bg-[var(--soft)] ring-1 ring-inset ring-[var(--accent)]" : ""}`}>
                <span className="min-w-0 font-semibold">{exercise.name}<span className="mt-0.5 block text-xs font-normal text-[var(--muted)]">{[exercise.equipment, exercise.primaryMuscles.join(", ")].filter(Boolean).join(" · ")}</span></span>
                <span className="shrink-0 text-xs text-[var(--muted)]">{exists ? "Added" : "View →"}</span>
              </li>;
            })}
            <li id={optionId(results.length)} role="option" aria-selected={active === results.length}
              onClick={() => choose(results.length)} onMouseMove={() => setActiveIndex(results.length)}
              className={`flex min-h-12 cursor-pointer items-center rounded-lg px-3 py-3 text-sm font-semibold text-[var(--accent)] hover:bg-[var(--soft)] ${results.length ? "mt-1 border-t border-[var(--line)]" : ""} ${active === results.length ? "bg-[var(--soft)] ring-1 ring-inset ring-[var(--accent)]" : ""}`}>
              <span className="break-words">+ Create custom exercise{query.trim() ? `: ${query.trim()}` : ""}</span>
            </li>
          </ul>
        </div>
      </div>
      <p id={`${id}-hint`} className="mt-2 text-xs text-[var(--muted)]">Search the local library, then select an exercise to view its details. Use ↑ ↓ and Enter to choose.</p>
    </div>
  );
}
