## Codex Style Rules

- When generating UI, never allow labels, values, headings, buttons, table cells, badges, navigation items, or other visible text to render as collapsed, clipped, overlapped, or unreadable.
- Prefer wrapping, responsive sizing, and layout changes over truncation. Use constraints such as `min-width`, `max-width`, `flex-wrap`, `grid-template-columns`, `minmax()`, and `overflow-wrap` so realistic labels and data remain readable on mobile and desktop widths.
- Do not use `overflow: hidden`, `text-overflow: ellipsis`, `white-space: nowrap`, or tiny font sizes for important text unless truncation is explicitly requested or the full value is available elsewhere, such as in adjacent detail text or a tooltip.

## Build Tool

- This project uses Mill, not sbt.
- Never run `sbt` commands in this repository.
- Mill commands may be run when useful for the task.
- Run Mill with an output directory outside the repository, for example `MILL_OUTPUT_DIR=/private/tmp/fdswarm-mill-out ./mill --no-server ...`.
- Prefer `--no-server` for Mill commands to avoid stale daemon state under the default `out/` directory.
- Do not run compile or test validation commands unless the user explicitly asks for them.
