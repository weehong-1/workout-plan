# Screens

Source of the iOS screen designs, drawn on a Claude Design canvas (spec D4). The canvas is a preview only; these files are the record.

* `canvas.json`: the canvas layout (positions and titles of the artboards).
* `DesignSystem.dc.html`: colours, type, spacing, radius and components (spec section 3.1).
* One file per screen, dark by default with a `dark` switch: `Activity`, `StepDetail`, `Main` (Weight tab), `WeighIn`, `Food`, `Review`, `TrainerLink`.
* `*Light.dc.html`: the same screens with dark mode off. They only import the screen above, so a change to a screen shows in both.

All numbers, dates, names and photos are sample placeholders.

The files are Claude Design components: HTML with `{{ }}` template holes and a small logic class. They need the Claude Design canvas to render and are not code for the app.

Spec: `docs/ios/spec-2026-11-26-ui-flow.md`.
