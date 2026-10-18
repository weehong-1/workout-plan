import { ImageResponse } from "next/og";

// A dumbbell on an orange tile, drawn at any size for favicons and the home screen.
export function renderIcon(size: number) {
  const u = size / 32;
  const plate = (w: number, h: number) => (
    <div style={{ width: w * u, height: h * u, background: "#fff", borderRadius: u }} />
  );
  return new ImageResponse(
    (
      <div
        style={{
          width: "100%",
          height: "100%",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          background: "#ea580c",
          gap: u * 0.6,
        }}
      >
        {plate(2.5, 9)}
        {plate(3, 14)}
        <div style={{ width: 8 * u, height: 2.5 * u, background: "#fff" }} />
        {plate(3, 14)}
        {plate(2.5, 9)}
      </div>
    ),
    { width: size, height: size },
  );
}
