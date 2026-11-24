"use client";

import { supabase } from "@/lib/supabase";

// Photos live in a private Supabase Storage bucket, one folder per account
// (`<user id>/<random id>.jpg`). Storage policies only let an account read or
// change files in its own folder; rows only store these paths.
export const PHOTO_BUCKET = "photos";
export const MAX_PHOTOS = 6;
const MAX_EDGE = 1600;
const QUALITY = 0.82;
const URL_SECONDS = 3600;

const UUID = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
export function isPhotoPath(userId: string, path: unknown): path is string {
  return typeof path === "string" && new RegExp(`^${userId}/${UUID}\\.jpg$`).test(path);
}

function client() {
  const sb = supabase();
  if (!sb) throw new Error("Photo storage is not configured.");
  return sb.storage.from(PHOTO_BUCKET);
}

function storageError(message: string): Error {
  return new Error(/bucket not found/i.test(message)
    ? "Photo storage is not set up yet. The site owner must apply the latest database schema."
    : message);
}

// Phone photos are often 3~10 MB. Resize to at most 1600 px on the long edge
// and re-encode as JPEG, which also drops location metadata.
export async function compressImage(file: Blob): Promise<Blob> {
  if (!file.type.startsWith("image/")) throw new Error("Choose an image file.");
  let bitmap: ImageBitmap;
  try {
    bitmap = await createImageBitmap(file, { imageOrientation: "from-image" });
  } catch {
    throw new Error("This image format isn't supported. Try a JPEG or PNG.");
  }
  const scale = Math.min(1, MAX_EDGE / Math.max(bitmap.width, bitmap.height));
  const canvas = document.createElement("canvas");
  canvas.width = Math.max(1, Math.round(bitmap.width * scale));
  canvas.height = Math.max(1, Math.round(bitmap.height * scale));
  const ctx = canvas.getContext("2d");
  if (!ctx) throw new Error("Could not process the image.");
  ctx.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
  bitmap.close();
  return new Promise((resolve, reject) =>
    canvas.toBlob((blob) => (blob ? resolve(blob) : reject(new Error("Could not process the image."))), "image/jpeg", QUALITY),
  );
}

export async function uploadPhoto(userId: string, file: Blob): Promise<string> {
  const blob = await compressImage(file);
  const path = `${userId}/${crypto.randomUUID()}.jpg`;
  const { error } = await client().upload(path, blob, { contentType: "image/jpeg", upsert: false });
  if (error) throw storageError(error.message);
  return path;
}

const urls = new Map<string, { url: string; expires: number }>();

// Short-lived signed links, cached until shortly before they expire.
export async function photoUrls(paths: string[]): Promise<Record<string, string>> {
  const now = Date.now();
  const missing = paths.filter((p) => !((urls.get(p)?.expires ?? 0) > now));
  if (missing.length) {
    const { data, error } = await client().createSignedUrls(missing, URL_SECONDS);
    if (error) throw storageError(error.message);
    for (const item of data ?? []) {
      if (item.path && item.signedUrl) urls.set(item.path, { url: item.signedUrl, expires: now + (URL_SECONDS - 300) * 1000 });
    }
  }
  return Object.fromEntries(paths.flatMap((p) => (urls.has(p) ? [[p, urls.get(p)!.url]] : [])));
}

// Best effort: a file left behind is still private to its account.
export async function removePhotos(paths: string[]): Promise<void> {
  if (!paths.length) return;
  paths.forEach((p) => urls.delete(p));
  try {
    await client().remove(paths);
  } catch {
    // Ignore: the database no longer references these files.
  }
}

// Used before deleting an account: remove every file in its folder.
export async function removeAllPhotos(userId: string): Promise<void> {
  const bucket = client();
  for (;;) {
    const { data, error } = await bucket.list(userId, { limit: 1000 });
    if (error) {
      if (/bucket not found/i.test(error.message)) return;
      throw storageError(error.message);
    }
    const names = (data ?? []).filter((f) => f.id).map((f) => `${userId}/${f.name}`);
    if (!names.length) return;
    const { error: removeError } = await bucket.remove(names);
    if (removeError) throw storageError(removeError.message);
  }
}
