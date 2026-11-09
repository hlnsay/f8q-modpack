# F8Q embedded Diorama renderer

The upstream Diorama engine is embedded in the **SimpleMap JAR**. Its original NeoForge entrypoint is excluded: no second mod, key bindings, minimap, configuration screen, waypoint store or HUD layer is registered.

- Upstream 0.8.1 NeoForge 1.21.1 supplies voxel snapshots, actual block models, tile baking, GPU validation, LOD, persistent tile storage and the map viewport.
- Upstream 0.7.0 supplies the original soft-cloud fragment shader, matching the requested visual reference. A compatibility mixin bypasses 0.8's offscreen-layer parameter encoding; shader metadata lists its two actual samplers.
- F8Q keeps its existing MapScreen, panels, icons, points of interest, party markers, routes and native `.smdat` exploration.
- Middle mouse drag turns the fullscreen view by quarter turns. The minimap retains its independent north-up/follow-camera setting.
- `DioramaGeometry` recovers real block sections only for the client's already explored chunks. It reads existing server chunk files asynchronously; it does not request chunk generation. Payloads exclude entities, block entities, inventories, scheduled ticks and structure metadata. Requests contain at most 8 chunks, are rate limited and processed by a bounded single-worker queue.
- Native exploration is read, never converted in place or removed. Derived renderer data lives below `<native dimension>/diorama-engine-v1/`. Chunk snapshots and decoded NBT caches are bounded; I/O and recovery do not run on the render thread.
- Existing servers without the optional geometry channel keep the native fullscreen view until upgraded, avoiding an apparently erased map. Singleplayer uses its world save directly.

The client-only Inventory Tabs, Forgematica and MaFgLib additions do not belong on the dedicated server. The generated Russian language overrides and Insert hotkeys are in the pack's normal resource/config distribution.

Upstream is MIT licensed; see LICENSE-DIORAMA and upstream.json for exact versions, download URLs and hashes. Integration sources are in `../simplemap/src/com/velorise/simplemap/`.

No server world data, access token or test agent is part of the release.

## Build

Run `build_overlay.py --base-jar <v22.jar> --classpath-file <mapped-cp.txt> --output <simplemap.jar> --javac <JDK-21-or-newer-javac>`. The classpath file is the semicolon-separated NeoForge 1.21.1 mapped development classpath. Exact upstream binaries are fetched and hash-verified; only the integration source list is compiled.
