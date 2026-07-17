# Demo recording — beat sheet

A ~90-second screen recording: Claude Code on one side, Chromatik's preview on the
other. Cut a <60s vertical/square version for X from the same take. The prompts below
should be **dry-run against the live instance before recording** so every beat lands
on the first take.

Setup before recording:

- Chromatik open with the show model loaded, mostly-empty mixer (one default channel
  is fine). Preview window sized to fill its half of the screen.
- Claude Code connected (`claude mcp add ...` already done — don't spend screen time
  on setup; the docs cover it).
- Terminal font large enough to read in a compressed video.

## Beat 1 — establish (0:00–0:10)

Both windows visible. One caption/voiceover line:

> "This is Chromatik, a lighting engine. Claude is connected to it over MCP — it can
> read and mutate the live show."

## Beat 2 — build a look from one sentence (0:10–0:40)

Prompt:

> Build me a warm sunset look across the whole structure — slow gradient drifting
> through oranges and pinks, with a gentle breathing motion on the brightness.

Expected on screen: agent calls `list_available_patterns` / `get_component_doc`,
adds a channel with a gradient pattern, sets palette hues, adds an LFO and wires it
to the fader with a range. The preview visibly shifts to warm colors and starts
breathing. **This is the money shot — let it run a few seconds.**

## Beat 3 — the agent looks at its own output (0:40–1:05)

Prompt:

> Grab a frame and look at it. Is it reading as "sunset"? If it's too washed out,
> fix it.

Expected: agent calls `get_frame {include_image: true}`, *describes what it sees*,
then adjusts saturation/brightness. Caption:

> "It's not guessing — get_frame renders the output and the model looks at it."

## Beat 4 — structure + human control (1:05–1:25)

Prompt:

> Add a subtle sparkle layer on top, and give me a macro knob that controls how much
> sparkle there is.

Expected: `add_pattern`/`add_channel` for sparkle, `add_modulator {MacroKnobs}`,
`wire_modulator` onto the sparkle amount. Then **the human** drags the macro knob in
the Chromatik UI — the handoff moment: agent builds, human performs.

## Beat 5 — close (1:25–1:35)

Hit **Cmd-Z a few times** in Chromatik — the agent's changes unwind step by step.
Caption:

> "Every agent edit is one undo step. github.com/oveddan/lx-mcp"

## Dry-run checklist (do this via MCP before recording)

- [ ] Beat 2 prompt produces a visible warm look within ~5 tool calls (tune wording
      if the agent wanders).
- [ ] `get_frame {include_image: true}` returns a PNG that plausibly reads as the
      preview.
- [ ] The sparkle pattern chosen actually layers (BLEND) or sits on its own channel —
      no `activate_pattern` confusion on camera.
- [ ] Macro knob wiring has a non-zero `range` (a wiring without range is inert).
- [ ] `/lx/output/enabled` state doesn't matter for the preview, but check the sim
      renders at a good angle before hitting record.
