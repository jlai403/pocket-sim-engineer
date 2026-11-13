# Pocket Sim Engineer

Android app for Gran Turismo 7 that captures one lap of telemetry and pairs it with a single-lap replay for review.

## Lap capture

**Lap**:
One measured GT7 lap: its telemetry, lap time, and optional replay.
_Avoid_: run, stint, entry

**Capture**:
Recording a lap's telemetry, saved at the next start/finish crossing.
_Avoid_: record, log

**Session**:
The capture run that owns one or more laps and their telemetry.
_Avoid_: recording, track day

## Library

**Library archive**:
A zip of the selected laps, one directory per lap, carried with its clips and telemetry.
_Avoid_: backup file, export bundle

**Import (library)**:
Merging laps from an archive, skipping any id that already exists.
_Avoid_: restore, sync

## Video

**Clip**:
A video file brought in from the PS App or a share sheet, before it is synced.
_Avoid_: video, file

**Replay**:
The single-lap video attached to a lap.
_Avoid_: video, clip once attached

**Import**:
Copying a clip into app storage.
_Avoid_: upload, download

**Sync**:
Aligning a clip to its lap with an anchor and a confirmed start frame.
_Avoid_: align, calibrate

**Anchor**:
The finish-line frame that ends the lap; trimming works backwards from it.
_Avoid_: end marker, sync point

**Start frame**:
The frame one lap earlier than the anchor; the replay's first frame.
_Avoid_: clip start, sync start

**Lead-in**:
Clip time before the start frame, kept when the keyframe cut lands early.
_Avoid_: pre-roll, video start offset

**Trim**:
Cutting a synced clip to the span between the start frame and the anchor.
_Avoid_: cut, crop

**Attach**:
Saving a trimmed clip as the lap's replay.
_Avoid_: add, link

## Review

**Playhead**:
The video position within a clip or replay, measured in clip time.
_Avoid_: cursor, position

**Clip time**:
Elapsed time from the clip's first frame.
_Avoid_: video time

**Lap time**:
Elapsed time from the start frame to the anchor; the telemetry timeline.
_Avoid_: race time

**Telemetry cursor**:
The lap-time position drawn on the charts and track map.
_Avoid_: playhead, marker

**Frame step**:
Moving the playhead by exactly one video frame.
_Avoid_: scrub, nudge

**Frame ruler**:
The draggable scale that moves the playhead a frame at a time.
_Avoid_: jog wheel, dial

**Transport**:
The play/pause and ±10-second controls.
_Avoid_: player, controls

## Driver

**Driver**:
The person who drove a lap.
_Avoid_: player, user

**Driver profile**:
A driver's PSN ID, GT nickname, and country flag.
_Avoid_: account, avatar, card

**Owner**:
The driver using this install; the default for new captures.
_Avoid_: me, main user, primary profile

**Profile book**:
The local set of the owner and every cached driver profile.
_Avoid_: contacts, address book

**Assigned / unassigned lap**:
Whether a lap has a driver profile.
_Avoid_: set/unset, linked
