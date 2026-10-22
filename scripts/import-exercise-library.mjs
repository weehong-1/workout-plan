import { writeFile, mkdir } from "node:fs/promises";
import { createHash } from "node:crypto";

// Pinned upstream revision: updates are explicit and reviewed, never runtime fetches.
const revision = "f00c92c7dcf1216a928a52c3706c7ce8e2f71ed5";
const origin = `https://raw.githubusercontent.com/yuhonas/free-exercise-db/${revision}`;
async function download(path) {
  const response = await fetch(`${origin}/${path}`);
  if (!response.ok) throw new Error(`Download failed (${response.status}): ${path}`);
  return response.text();
}
const [raw, license] = await Promise.all([download("dist/exercises.json"), download("LICENSE.md")]);
const entries = JSON.parse(raw);
const ids = new Set();
const exercises = entries.map((entry) => {
  const value = {
    id: entry.id, name: entry.name.trim(), equipment: entry.equipment?.trim() ?? "",
    primaryMuscles: entry.primaryMuscles.map((muscle) => muscle.trim()),
    instructions: entry.instructions.map((step) => step.trim()).filter(Boolean), level: entry.level ?? "",
  };
  if (typeof value.id !== "string" || !value.id || value.id.length > 160 || ids.has(value.id)
    || !value.name || value.name.length > 120 || value.equipment.length > 100
    || value.primaryMuscles.length > 10 || value.primaryMuscles.some((m) => !m || m.length > 100)
    || value.instructions.length > 30 || value.instructions.reduce((n, step) => n + step.length, 0) > 6000) {
    throw new Error(`Unexpected exercise shape or size: ${entry.id}`);
  }
  ids.add(value.id);
  return value;
}).sort((a, b) => a.name.localeCompare(b.name, "en"));
const directory = new URL("../src/data/", import.meta.url);
await mkdir(directory, { recursive: true });
await writeFile(new URL("exercises.json", directory), JSON.stringify(exercises) + "\n");
await writeFile(new URL("FREE-EXERCISE-DB-LICENSE.txt", directory), license);
await writeFile(new URL("exercise-library-source.json", directory), JSON.stringify({
  repository: "https://github.com/yuhonas/free-exercise-db", revision,
  source: `${origin}/dist/exercises.json`, sha256: createHash("sha256").update(raw).digest("hex"),
  license: "Unlicense", count: exercises.length,
  included: "Text metadata only; no images, videos or remote media URLs are imported.",
}, null, 2) + "\n");
console.log(`Imported ${exercises.length} exercises from ${revision} (text only).`);
