# Better Widget Hider

Hide specific parts of the game's built-in interfaces — minigame HUDs, overlays, any widget —
by keeping a simple list of widget IDs. Companion to
[better-object-highlight](https://github.com/jakevollkommer/better-object-highlight)'s
entity hider, but for the UI instead of the 3D scene.

## Usage

Add entries to the **Widgets to hide** list, separated by commas or newlines:

- `group.child` — e.g. `746.5`
- `group.child.index` — for a dynamic child of a container widget

Find widget IDs with RuneLite's built-in **Dev Tools** plugin → *Widget Inspector*
(requires `--developer-mode`).

Hidden widgets are re-hidden every client tick, since the game rebuilds interfaces whenever
their values update. Removing an entry (or disabling the plugin) restores the widget.

### Example: Guardians of the Rift HUD

| Entry    | Hides                                    |
|----------|------------------------------------------|
| `746.5`  | Time since last portal                    |
| `746.25` | Guardian counter (icon + count)           |
| `746.28` | Portal location text                      |

## License

BSD 2-Clause
