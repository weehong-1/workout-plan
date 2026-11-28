# Slopevo Android design

Export of the Android and Logo pages from the Claude Design canvas, taken 28 Nov 2026.
Sample data only: numbers, dates and photos are placeholders.

## screens/

Design components, one per screen. Each `*Light.dc.html` file shows the same screen in light mode.

| File | Screen |
|---|---|
| AndroidDesignSystem.dc.html | Colour roles, metric colours, type scale, shapes, rules, components |
| AndroidLogin.dc.html | Sign in (Google, passkey) |
| AndroidActivity.dc.html | Activity: steps, sleep, recovery, strain, workouts, steps date range |
| AndroidStrain.dc.html | Strain detail |
| AndroidWeight.dc.html | Weight: weekly average, chart, Weeks / Months / Years, paged history |
| AndroidWeighIn.dc.html | Log weigh-in sheet |
| AndroidFood.dc.html | Food photos by day, labels |
| AndroidReview.dc.html | Monthly / yearly review, active trainer links |
| AndroidTrainerLink.dc.html | Share with trainer (link with optional expiry) |
| AndroidProfile.dc.html | Profile: goal, units, theme, WHOOP, reminders, data |
| LogoCombo.dc.html | Slopevo app icon board |
| canvas.json | Board layout and notes for these two pages |

These files need the canvas runtime to render, so they do not open on their own in a browser. Use them as the source for layout, colours, text and type sizes; the live canvas is the place to view them.

## icon/

| File | Use |
|---|---|
| slopevo-icon.svg | Vector source (square) |
| slopevo-icon-1024.png | Square 1024 px master; store listings, WHOOP |
| slopevo-icon-1024.jpg | Same as JPG |
| slopevo-icon-512.png | Square 512 px |
| slopevo-icon-rounded-1024.png | Rounded corners, for places that show the image as uploaded |

Android adaptive icon layers (foreground, background, monochrome) are not exported yet.
