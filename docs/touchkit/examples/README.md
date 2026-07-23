# TouchKit example layouts

These files use the same JSON format produced by **Import and export layouts** in Artemis.

## Genshin Impact — phone

- File: [`genshin-impact-phone.json`](genshin-impact-phone.json)
- Display name after import: `原神`
- Source canvas: 2312 × 1080 landscape
- Labels and descriptions: Simplified Chinese

![Genshin Impact phone layout preview](../screenshots/genshin-impact-layout.png)

The preview is the original device screenshot with only the numeric UID locally pixelated. No
gameplay image, control position, color, or other detail was regenerated or retouched.

This is a real phone layout contributed as a practical example. It includes keyboard keys, mouse
buttons, scrolling, a joystick, a key wheel, a soft-keyboard control, descriptions, opacity, and
trigger settings. The latest export also includes the `T` key shown near the lower-right side of
the streamed game area.

## War Thunder — ground battles, phone

- File: [`war-thunder-ground-phone.json`](war-thunder-ground-phone.json)
- Display name after import: `战雷_陆战`
- Source canvas: 2312 × 1080 landscape
- Labels and descriptions: Simplified Chinese

![War Thunder ground-battle layout](../screenshots/war-thunder-layout-editor.jpg)

This layout demonstrates mouse fire and aiming controls, a four-direction movement joystick,
timed-hold actions, descriptions, and key wheels for vehicle controls.

To use it, open **Settings > Cloud gaming controls > Expand > Import and export layouts**, choose
**Import layout**, and select the JSON file. Artemis validates it and creates a new layout rather
than overwriting an existing one.

The examples contain no host UUID, IP address, account information, streaming settings, or
per-game association. Devices with a different aspect ratio may need small position or size
adjustments in the layout editor.
