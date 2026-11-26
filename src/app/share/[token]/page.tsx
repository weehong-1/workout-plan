import type { Metadata } from "next";
import { SharedLinkUnavailable, SharedReviewView } from "@/components/SharedReviewView";
import { loadSharedLink } from "@/lib/shared-review";

// Read on every request, so a revoked or expired link stops at once.
export const dynamic = "force-dynamic";

export const metadata: Metadata = {
  title: "Shared review",
  robots: { index: false, follow: false },
  referrer: "no-referrer",
};

export default async function SharePage({ params }: { params: Promise<{ token: string }> }) {
  const { token } = await params;
  const link = await loadSharedLink(token);
  return link ? <SharedReviewView link={link} /> : <SharedLinkUnavailable />;
}
