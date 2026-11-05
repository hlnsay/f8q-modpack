# F8Q map integration — NeoForge 1.21.1

SimpleMap modifications and F8Q Party server discovery code. No Minecraft binaries or Minecraft source are included.

Generated places come only from already-loaded FULL chunks: registered structure starts/references, real waystone block entities, and real spawners outside registered structures. Structure coordinates use the inclusive bounding box centre. No locate calls, seed guesses, forced loads or world generation. Each tick reads at most four queued chunks within a soft 2 ms budget. Scanned chunks have a cooldown; network deltas have at most 128 records and are sent only when changed.

Discovery radius: 48 blocks from the real horizontal footprint. Per-player discoveries and generated records are saved in the world file `f8q-generated-places-v2.json`; old predicted locations are ignored. A session cache keeps received places in each dimension; server data survives restarts. Players may have to revisit old regions to populate the new records. A mod that places objects outside the structure registry and outside recognized block entities needs its own adapter.

The home marker follows the current respawn position, normally the last bed used. Names are translated for common vanilla and installed modded structures; unknown registered names remain readable names instead of an invented village label. Friendly Ribbits is verified against `ribbits:ribbit_village`. Hostile frog mapping uses the verified Croaks 2.0.0 registry IDs: croaks:outpost, croaks:shed and croaks:purple_mushroom, plus explicit hostile identifiers and pool hints.

GUI assets are supplied by the pack owner. `map-ui.png` is sliced into coordinate, zoom and context/details panels. LMB opens a details card; RMB preserves the context menu. Camera scale and position survive waypoint editing.

Tests: classification including modded camps; negative and large bounding boxes; reveal distance; packet roundtrip and bounds; dimension isolation; incremental discoveries; removal and disconnect reset. Dedicated server/client connection and visible details panel tested in an isolated instance.

Release artifacts: SimpleMap f8q10 and F8Q Party 1.0.10. Only these two mod files change. Existing non-map party features are preserved. The map payload protocol is version 2, requiring both updated client and server.
