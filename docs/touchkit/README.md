# TouchKit user guide

TouchKit is Artemis' touch-oriented mouse, keyboard, and floating-control layer for cloud gaming.
It works inside the streaming screen and does not request Android's system overlay permission.

## Quick start

1. Open **Settings > Cloud gaming controls**.
2. Enable **Cloud gaming mode**. Artemis selects the matching entry under **Mouse mode**
   automatically; selecting another mouse mode disables the Cloud gaming switch.
3. Enable **Show floating controls** if you want an on-screen button layout. This controls the
   button layer without changing the mouse mode.
4. Open **Edit button layout**. Drag a control to move it, or tap it to edit its properties. The
   editor does not send input to the host.
5. Use **Expand** to show movement curve, X/Y sensitivity, opacity, vibration, and layout transfer.
   Use **Collapse** to return to the compact settings view.

Enable **Disable touch gestures** if the game should use floating controls for all clicks and keys.
This keeps direct one-finger pointer movement but disables tap clicks, double-tap dragging,
multi-finger scrolling, momentum, and three-to-five-finger shortcuts.

## Touch gestures

| Gesture | Host input |
| --- | --- |
| Move one finger | Relative mouse movement |
| Tap with one finger | Left click |
| Double-tap, then move | Hold the left button and drag |
| Tap with two fingers | Right click |
| Move two fingers | Vertical or horizontal scrolling |
| Tap with three fingers | Show the Android soft keyboard |
| Tap with four fingers | Toggle the full on-screen keyboard |
| Tap with five fingers | Open the Artemis quick menu when enabled |

## Controls and bindings

The editor can add keyboard keys, key combinations, left/right/middle mouse buttons, Mouse 4 and
Mouse 5, scroll buttons, key wheels, joysticks, D-pads, and a soft-keyboard button.

Bindings use `KEY=description`. The description is optional:

```text
F1
F1=Adventure Handbook
Ctrl+C=Copy
LeftCtrl+LeftShift+Space
Num1
```

`1` and `Num1` are different physical keys. `LeftCtrl` and `RightCtrl` are also distinct. Enable
**Show L/R/Num labels** when the side or keypad distinction should be visible on the control.

A key wheel starts empty. Add one binding per line, up to eight segments. It stays collapsed until
pressed; slide toward a segment to select it.

## Trigger behavior

- **Hold while pressed:** sends key-down when touched and key-up when released. Pointer movement
  can continue with the same finger while cloud gaming mode is enabled.
- **Lock:** one tap holds the input; the next tap releases it.
- **Timed hold:** holds the input for the configured duration, from 0.1 to 5.0 seconds. Tap the
  control again before the timer ends to release it immediately.
- Scroll controls support an adjustable step and optional continuous scrolling while held.

## Screenshots

### Genshin Impact phone layout

![Genshin Impact phone layout](screenshots/genshin-impact-layout.png)

### Layout editor

![War Thunder layout editor](screenshots/war-thunder-layout-editor.jpg)

### Key wheel

![Expanded key wheel](screenshots/key-wheel-expanded.jpg)

### In-game streaming

![War Thunder thermal sight](screenshots/war-thunder-thermal-sight.jpg)

## Layouts and game matching

**Button layouts** lets you select, create, or delete layouts. Rename the current layout from the
editor's **More actions** menu.

Artemis remembers the last layout used for each host/game pair. The next time that game starts on
the same host, its remembered layout is selected automatically. A game without a saved mapping
uses the current default layout.

## Import and export

Use **Import and export layouts** in the expanded Cloud gaming controls section:

- **Export current layout** writes one JSON layout file.
- **Import layout** validates the file, creates a new layout, and selects it.
- Existing layouts are not overwritten.
- Host information, credentials, streaming settings, and per-game associations are not exported.
- Legacy raw Artemis button-layout JSON can be imported, but full application settings are not
  part of the layout format.

Layouts moved between devices with different aspect ratios may need small position or size
adjustments in the editor.

Importable real-world layouts are available for
[Genshin Impact](examples/genshin-impact-phone.json) and
[War Thunder ground battles](examples/war-thunder-ground-phone.json).

## Opacity

The settings-page slider adjusts gray backgrounds and outlines globally. In the editor, **More
actions** provides separate global controls for backgrounds and for text/icons. Individual controls
can still override their gray-background opacity.

## Gyroscope aiming

Add a gyroscope control to a layout and tap it to lock or disable gyroscope-assisted aiming. While
enabled, device rotation becomes relative mouse movement and the screen stays awake. X/Y
sensitivity, deadzone, and axis inversion can be adjusted independently. Calibrate the axes in a
safe scene when using a device for the first time.
