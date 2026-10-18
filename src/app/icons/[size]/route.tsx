import { renderIcon } from "@/lib/icon";

const SIZES = ["192", "512"];

export const dynamicParams = false;

export function generateStaticParams() {
  return SIZES.map((size) => ({ size }));
}

export async function GET(_req: Request, { params }: { params: Promise<{ size: string }> }) {
  return renderIcon(Number((await params).size));
}
