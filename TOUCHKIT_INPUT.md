# TouchKit input and overlay

TouchKit adds touch-oriented relative mouse input and an editable in-stream control layer to
Artemis. It reuses Artemis' existing Moonlight input transport and does not change the streaming
protocol or require Android's system overlay permission.

- [English user guide](docs/touchkit/README.md)
- [简体中文使用说明](docs/touchkit/README.zh-CN.md)
- [Bilibili introduction outline](docs/touchkit/BILIBILI_VIDEO_OUTLINE.zh-CN.md)
- [Importable example layouts](docs/touchkit/examples/README.md)

## Implementation boundaries

- **Cloud gaming mode** controls touch-to-relative-mouse behavior.
- **Show floating controls** controls the in-stream button layer independently.
- The layout editor never sends keyboard or mouse input to the host.
- Layout export contains one TouchKit layout only. It excludes hosts, credentials, streaming
  settings, and per-game associations.
- Per-game matching uses the host UUID plus app UUID, with app ID as a fallback.
- Imported layouts are created as new layouts, so an existing layout is not overwritten.

## Gesture contract

| Touch gesture | Host input |
| --- | --- |
| Move one finger | Relative mouse movement |
| Tap with one finger | Left click |
| Double-tap, then move | Hold left button and drag |
| Tap with two fingers | Right click |
| Move two fingers | High-resolution vertical or horizontal scroll |
| Tap with three fingers | Show the Android soft keyboard |
| Tap with four fingers | Toggle the full on-screen keyboard |
| Tap with five fingers | Open the Artemis quick menu when enabled |

The X/Y sensitivity and movement-curve settings apply to relative pointer movement. Pressing a
floating button does not prevent another finger from moving the pointer, while duplicate presses
of an already-held key or mouse button are reference-counted to avoid stuck or jumping input.

When **Disable touch gestures** is enabled in cloud gaming mode, only direct one-finger pointer
movement remains. Tap clicks, double-tap dragging, multi-finger scrolling, momentum, and the
three-to-five-finger shortcuts are suppressed; floating controls continue to work.

## Compatibility

New control fields are optional when loading an older layout. The importer also accepts legacy
Artemis raw SharedPreferences layout JSON. Full Artemis application-settings migration is outside
the current format.
