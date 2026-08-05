package chromatikmcp.domain;

import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import heronarts.lx.LX;
import heronarts.lx.clip.AudioClipEvent;
import heronarts.lx.clip.AudioClipLane;
import heronarts.lx.clip.Cursor;
import heronarts.lx.clip.LXClipLane;
import heronarts.lx.clip.LXComposition;
import heronarts.lx.clip.TextNoteClipEvent;
import heronarts.lx.clip.TextNoteClipLane;
import heronarts.lx.command.LXCommand;

import chromatikmcp.domain.ClipEvents.EventDetail;
import chromatikmcp.domain.ClipLanes.LaneSummary;
import chromatikmcp.domain.Clips.ClipEnvelope;
import chromatikmcp.domain.Cursors.CursorInfo;

/**
 * Composition-only primitives: things that exist on the arrange timeline's
 * {@link LXComposition} but not on grid clips (locators, audio/text lanes, the timeline
 * arm). Clip-generic behavior lives in {@link Clips}.
 */
public final class Compositions {

  private Compositions() {}

  /**
   * The timeline's composition. LX initializes it with the engine, so this only fails on
   * a pre-initialization engine — but the failure is typed NOT_FOUND so a tool call in
   * that window degrades to the standard wire error instead of an NPE.
   */
  public static LXComposition get(LX lx) {
    LXComposition composition = lx.engine.timeline.getComposition();
    if (composition == null) {
      throw new Resolve.ResolveException(Resolve.Failure.NOT_FOUND,
          "No composition on the timeline engine (engine not initialized?)");
    }
    return composition;
  }

  /**
   * The get_composition payload: the shared clip envelope plus composition-only state —
   * timeline arm ({@code lx.engine.timeline.arm} is a bare public field with no
   * {@code addParameter}, unreachable via set_parameter by design), sync, locator count,
   * and per-lane summaries.
   */
  public record CompositionDetail(
      ClipEnvelope envelope,
      boolean armed,
      boolean sync,
      int locatorCount,
      List<LaneSummary> lanes) {}

  /** Snapshots {@link CompositionDetail}; {@code Payloads.composition} flattens it. */
  public static CompositionDetail describe(LX lx) {
    LXComposition composition = get(lx);
    return new CompositionDetail(
        Clips.envelope(composition),
        lx.engine.timeline.arm.isOn(),
        lx.engine.timeline.sync.isOn(),
        composition.locators.size(),
        ClipLanes.list(composition));
  }

  // ============================================================
  // F2 Locator primitives (branch comp-f2-locators) — implemented by that family agent:
  // list/add/remove/move (LXCommand.Composition.AddLocator/RemoveLocator/MoveLocator)
  // and go (launch from a locator). Locators resolve natively at
  // /lx/timeline/composition/locator/<1-based>, but the list re-sorts by cursor on every
  // add/move — indices are positional; echo the resulting index.
  // Keep additions inside this band; bands are merge-isolation, do not reorder.
  // NOTE: types not already imported are fully qualified so this band adds no import
  // lines — the import block sits outside every band and is the one line-level merge
  // hotspot between F2 and F7.
  // ============================================================

  /**
   * Locator addressing is 1-indexed everywhere — argument, payload {@code index}, the
   * {@code locator:<n>} cursor origin, and the canonical path — unlike lanes/events whose
   * payload {@code index} is 0-based; one locator vocabulary beats two off-by-one ones.
   * The list re-sorts by cursor on every add/move, so indices are positional.
   */
  public record LocatorSummary(String path, int index, String label, CursorInfo cursor) {}

  /** Snapshots one locator; {@code Payloads.locator} owns its MCP field names. */
  public static LocatorSummary locatorSummary(
      LXComposition composition, heronarts.lx.clip.Locator locator) {
    // Position in the live list, NOT locator.getIndex(): upstream removeLocator skips the
    // re-index pass that add/move run, so getIndex() (and thus getCanonicalPath()) can be
    // stale until the next sort.
    int index = composition.locators.indexOf(locator) + 1;
    return new LocatorSummary(
        Resolve.canonicalPath(composition) + "/locator/" + index,
        index,
        locator.getLabel(),
        Cursors.describe(composition, locator.position.cursor));
  }

  /** The list_locators state: composition identity plus every locator in cursor order. */
  public record LocatorList(
      String path, String timeBase, int locatorCount, List<LocatorSummary> locators) {}

  /** Snapshots every locator; {@code Payloads.locatorList} owns its MCP field names. */
  public static LocatorList listLocators(LX lx) {
    LXComposition composition = get(lx);
    List<LocatorSummary> locators = new java.util.ArrayList<>();
    for (heronarts.lx.clip.Locator locator : composition.locators) {
      locators.add(locatorSummary(composition, locator));
    }
    return new LocatorList(
        Resolve.canonicalPath(composition),
        composition.getTimeBase().name(),
        composition.locators.size(),
        locators);
  }

  /**
   * Resolves a locator by exactly one of 1-indexed position or label. Label match is
   * exact and must be unique — with duplicates the index is the only safe address, so an
   * ambiguous label is {@code invalid_argument}, not a silent first-match.
   */
  public static heronarts.lx.clip.Locator resolveLocator(
      LXComposition composition, Integer index, String label) {
    if ((index == null) == (label == null)) {
      throw Resolve.invalidArgument("Provide exactly one of: index (1-indexed) | label");
    }
    if (index != null) {
      if (index < 1 || index > composition.locators.size()) {
        throw new Resolve.ResolveException(Resolve.Failure.NOT_FOUND,
            "No locator " + index + " (composition has " + composition.locators.size()
                + " locators, 1-indexed)");
      }
      return composition.locators.get(index - 1);
    }
    heronarts.lx.clip.Locator match = null;
    for (heronarts.lx.clip.Locator locator : composition.locators) {
      if (label.equals(locator.getLabel())) {
        if (match != null) {
          throw Resolve.invalidArgument("Multiple locators labeled '" + label
              + "' — address by index instead (see list_locators)");
        }
        match = locator;
      }
    }
    if (match == null) {
      throw new Resolve.ResolveException(Resolve.Failure.NOT_FOUND,
          "No locator labeled '" + label + "' (" + composition.locators.size()
              + " locators; labels are exact-match)");
    }
    return match;
  }

  /**
   * Adds a locator at {@code cursor} via {@code LXCommand.Composition.AddLocator}. The
   * command keeps its created Locator private, so the new one is recovered by identity
   * diff against the pre-perform list — position lookup would misfire when two locators
   * share a cursor. The optional label is applied directly after the command (upstream
   * has no labeled-add): undo removes the locator entirely, but a redo re-performs the
   * bare command and restores it unnamed.
   */
  public static heronarts.lx.clip.Locator addLocator(
      LX lx, heronarts.lx.clip.Cursor cursor, String label) {
    LXComposition composition = get(lx);
    java.util.List<heronarts.lx.clip.Locator> before =
        new java.util.ArrayList<>(composition.locators);
    Commands.perform(lx,
        new heronarts.lx.command.LXCommand.Composition.AddLocator(composition, cursor));
    heronarts.lx.clip.Locator added = null;
    for (heronarts.lx.clip.Locator locator : composition.locators) {
      if (!before.contains(locator)) {
        added = locator;
        break;
      }
    }
    if (added == null) {
      throw new IllegalStateException("AddLocator applied but no new locator found");
    }
    if (label != null && !label.isBlank()) {
      added.label.setValue(label);
    }
    return added;
  }

  /** Removes {@code locator} via {@code LXCommand.Composition.RemoveLocator}. */
  public static void removeLocator(LX lx, heronarts.lx.clip.Locator locator) {
    LXComposition composition = get(lx);
    Commands.perform(lx,
        new heronarts.lx.command.LXCommand.Composition.RemoveLocator(composition, locator));
  }

  /**
   * Moves {@code locator} to {@code cursor} via {@code LXCommand.Composition.MoveLocator}.
   * Locator positions are unclamped (a locator may sit past the composition length), but
   * the move re-sorts the list — callers echo the summary read back, index included.
   */
  public static void moveLocator(LX lx, heronarts.lx.clip.Locator locator,
      heronarts.lx.clip.Cursor cursor) {
    LXComposition composition = get(lx);
    Commands.perform(lx,
        new heronarts.lx.command.LXCommand.Composition.MoveLocator(composition, locator, cursor));
  }

  /**
   * Transport jump to {@code locator}, mirroring upstream's own locator navigation
   * (goPreviousLocator/goNextLocator): while running, relaunch automation from the
   * locator (subject to global launch quantization); while stopped, move the insert
   * marker there — which also scrubs lane values to that point but does NOT start
   * playback. Direct engine edit both ways — LX ships no transport command — so this is
   * not undoable.
   *
   * @return whether playback was running (i.e. the relaunch branch was taken)
   */
  public static boolean goLocator(LX lx, heronarts.lx.clip.Locator locator) {
    LXComposition composition = get(lx);
    boolean running = composition.isRunning();
    if (running) {
      composition.launchAutomationFrom(locator.position.cursor);
    } else {
      // setInsertMarker bounds to [0, length] non-destructively; the locator's own
      // cursor is never mutated, and callers echo the marker read back.
      composition.setInsertMarker(locator.position.cursor);
    }
    return running;
  }

  // ============================================================
  // F7 Audio/text lane + arm primitives (branch comp-f7-audio-text-arm) — implemented by
  // that family agent: addAudioLane (LXCommand.Composition.AddAudioLane — file I/O; keep
  // out of apply_operations if batching is unwanted), addTextNoteLane (AddTextNoteLane),
  // note add/edit (not undoable — no upstream command), setArm (direct write to the
  // unregistered lx.engine.timeline.arm field; not undoable).
  // Keep additions inside this band; bands are merge-isolation, do not reorder.
  // ============================================================

  /**
   * Adds an audio lane playing {@code file} at the top of the lane list (via
   * {@code LXCommand.Composition.AddAudioLane}). Pre-validates the file with
   * {@code AudioSystem} because upstream {@code setFile} only logs read failures — without
   * the pre-check an unreadable file silently yields a lane with a zero-length event.
   * Import side effects: composition length grows to at least the audio length, and an
   * empty composition gets its timeline enabled (playEnd/loopLength set to that length).
   */
  public static AudioClipLane addAudioLane(LX lx, File file) {
    LXComposition composition = get(lx);
    try {
      AudioSystem.getAudioFileFormat(file);
    } catch (UnsupportedAudioFileException e) {
      throw Resolve.invalidArgument(
          "Not an audio format javax.sound.sampled can read (WAV/AIFF): "
              + file.getAbsolutePath());
    } catch (IOException e) {
      throw Resolve.invalidArgument(
          "Cannot read audio file: " + file.getAbsolutePath() + " (" + e.getMessage() + ")");
    }
    List<LXClipLane<?>> before = List.copyOf(composition.lanes);
    Commands.perform(lx, new LXCommand.Composition.AddAudioLane(composition, file));
    for (LXClipLane<?> lane : composition.lanes) {
      if (lane instanceof AudioClipLane audioLane && !before.contains(lane)) {
        if (audioLane.events.isEmpty()) {
          // Upstream builds the lane and its single file-backed event together, so callers
          // read events.get(0) unguarded. Assert it here rather than let a future upstream
          // split surface as an IndexOutOfBoundsException from the tool layer.
          throw new IllegalStateException(
              "AddAudioLane committed but the lane carries no audio event: "
                  + file.getAbsolutePath());
        }
        return audioLane;
      }
    }
    throw new IllegalStateException("AddAudioLane committed but no new audio lane found");
  }

  /**
   * Adds a text-notes lane at the end of the lane list (via
   * {@code LXCommand.Composition.AddTextNoteLane}), optionally renaming it — the command
   * has no label argument, so the rename rides after it and is undone with the lane
   * itself, not as a separate step.
   */
  public static TextNoteClipLane addTextNoteLane(LX lx, String label) {
    LXComposition composition = get(lx);
    List<LXClipLane<?>> before = List.copyOf(composition.lanes);
    Commands.perform(lx, new LXCommand.Composition.AddTextNoteLane(composition));
    for (LXClipLane<?> lane : composition.lanes) {
      if (lane instanceof TextNoteClipLane notesLane && !before.contains(lane)) {
        if (label != null) {
          notesLane.label.setValue(label);
        }
        return notesLane;
      }
    }
    throw new IllegalStateException("AddTextNoteLane committed but no new notes lane found");
  }

  /**
   * Inserts a text-note event — a direct engine edit, LX ships no command for note events,
   * so this is not undoable. Upstream {@code TextNoteClipLane.addEvent(note, cursor,
   * length)} drops its note argument on the floor, so the text is set on the returned
   * event here.
   */
  public static TextNoteClipEvent addNote(TextNoteClipLane lane, String note,
      Cursor cursor, Cursor length) {
    TextNoteClipEvent event = lane.addEvent(note, cursor, length);
    event.note.setValue(note);
    return event;
  }

  /**
   * Edits the text-note event at {@code index} (resolved through the shared
   * {@code atCursor} guard): text, position, and/or duration — any combination the caller
   * passed non-null. Direct edits, not undoable. Both cursor writes clamp upstream —
   * {@code moveEvent} constrains between the neighboring events and the clip length,
   * {@code setEventEnd} floors the duration at {@code Cursor.MIN_LOOP} — so callers must
   * echo the state read back from the event, never the request.
   */
  public static TextNoteClipEvent setNote(TextNoteClipLane lane, int index, Cursor atCursor,
      String note, Cursor moveTo, Cursor length) {
    TextNoteClipEvent event = ClipEvents.eventAt(lane, index, atCursor);
    if (note != null) {
      event.note.setValue(note);
    }
    if (moveTo != null) {
      lane.moveEvent(event, moveTo);
      // moveEvent writes the cursor field directly, bypassing LXCompositionEvent's
      // refreshEnd() — re-derive end through the public setter so the read-back span
      // stays end == cursor + length even on a move-only edit.
      event.setEventEnd(event.getCursor().add((length != null) ? length : event.length));
    } else if (length != null) {
      event.setEventEnd(event.getCursor().add(length));
    }
    return event;
  }

  /**
   * Text-note event payload — exactly the shared {@link ClipEvents#detail} shape
   * ({@code {index, cursor, note, length, end}}), so add/set_clip_note and get_clip_lane
   * can never drift on field names.
   */
  public static EventDetail describeNote(TextNoteClipLane lane, TextNoteClipEvent event) {
    return ClipEvents.detail(lane, event, lane.events.indexOf(event));
  }

  /**
   * Audio event state: the shared {@link ClipEvents#detail} shape plus {@code filePath} —
   * an additive extension (tool-conventions "Drill-down" pattern), never a parallel
   * serializer, so add_audio_lane and get_clip_lane share every common field.
   */
  public record AudioEventDetail(EventDetail event, String filePath) {}

  /** Snapshots one audio event; {@code Payloads.audioEvent} flattens it. */
  public static AudioEventDetail describeAudioEvent(AudioClipLane lane, AudioClipEvent event) {
    return new AudioEventDetail(
        ClipEvents.detail(lane, event, lane.events.indexOf(event)),
        event.filePath.getString());
  }

  /**
   * Sets the timeline record-arm — a bare engine field with no {@code addParameter}, so
   * {@code /lx/timeline/arm} is unresolvable by design and set_parameter cannot reach it.
   * Direct write, not undoable. Upstream side effect (LXTimelineEngine.armChanged): arming
   * while the composition is stopped launches it into recording — from the start when
   * empty, from the playhead when it has content.
   */
  public static boolean setArm(LX lx, boolean armed) {
    lx.engine.timeline.arm.setValue(armed);
    return lx.engine.timeline.arm.isOn();
  }
}
