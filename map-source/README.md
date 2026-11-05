# F8Q map integration РІР‚вЂќ NeoForge 1.21.1

## Current policy: manual party marks restored (v19)

Party members again see one another's manually placed pins and waypoints. The existing marker payload carries label, coordinates, dimension and custom icon; the receiving UI uses the existing TeamMarks presentation for the full map, minimap, known-places list and world HUD. Changes are checked once per second and unchanged snapshots are sent every five seconds as a heartbeat. Empty snapshots delete markers; leaving the party removes them. The server derives recipients from the authenticated sender's party; the client rejects unrelated owners.

Terrain imagery, explored-area transfer and automatic POI discovery sharing stay disabled. Exploration.java, SharedExploration.java and Unknowns.java are unchanged from v18, including the minute-stall fix. PartyMarksTest exercises recipient isolation, marker codec, presentation publication, deletion, stranger rejection and party exit. Existing private-exploration regression tests remain passing.

## Current policy: private maps (v18)

Exploration, POI discovery and private markers are no longer shared through parties. Player positions, party HUD, invites and revival remain. Raster channels remain as compatibility boundaries, with no upload, receive, rendering or export logic. The server ignores obsolete raster uploads, skips their arrays when reading old journals, and rewrites the file with personal observed coverage only. Previously learned discoveries are retained: the old schema did not distinguish personal discoveries from permanently learned party discoveries, so deleting those would risk user data loss.

A live 60-second server profile identified Exploration.save taking 2,840 ms, including 2,796 ms in ImmutableCollections.SetN. Dense x/z keys made Set.copyOf pathological. Snapshotting now uses an owned ArrayList without hashing, then compression and disk writes run on the IO executor. Reproduction used the actual 52,435/52,418-chunk datasets (not included in this repository); old copies took 2,867/2,952 ms and the replacement 8.03/0.61 ms.

Waystone-only place/break events are coalesced and scanned four ticks later, after block entities settle; renames also request a non-loading refresh. The height check excludes leaves. A separate test-only mod passed placement below a canopy, break, and replacement at the same position. Existing location journals, routes, dimensions, stale deltas and reconnect persistence are covered by MapPlacesTest. The known-places panel collapses to a right-edge arrow tab without changing other map controls.


SimpleMap modifications and F8Q Party server discovery code. No Minecraft binaries or Minecraft source are included.

Generated places come only from already-loaded FULL chunks: registered structure starts/references, real waystone block entities, and waystone block entities. Underground generation steps, spawners, mineshafts, ancient cities, strongholds and trial chambers are excluded; persisted underground entries are pruned at startup. Structure coordinates use the inclusive bounding box centre. No locate calls, seed guesses, forced loads or world generation. Each tick reads at most four queued chunks within a soft 2 ms budget. Scanned chunks have a cooldown; network deltas have at most 128 records and are sent only when changed.

Discovery radius: 48 blocks from the real horizontal footprint. Per-player discoveries and generated records are saved in the world file `f8q-generated-places-v2.json`; old predicted locations are ignored. A session cache keeps received places in each dimension; server data survives restarts. Players may have to revisit old regions to populate the new records. A mod that places objects outside the structure registry and outside recognized block entities needs its own adapter.

The home marker follows the current respawn position, normally the last bed used. Assigned place categories retain their names. Other eligible surface structures have the generic label В«Р”Р°РЅР¶В». Friendly Ribbits is verified against `ribbits:ribbit_village`. Hostile frog mapping uses the verified Croaks 2.0.0 registry IDs: croaks:outpost, croaks:shed and croaks:purple_mushroom, plus explicit hostile identifiers and pool hints.

GUI assets are supplied by the pack owner. `map-ui.png` is sliced into coordinate, zoom and context/details panels. LMB opens a details card; RMB preserves the context menu. Camera scale and position survive waypoint editing.

Tests: classification including modded camps; negative and large bounding boxes; reveal distance; packet roundtrip and bounds; dimension isolation; incremental discoveries; removal and disconnect reset. Dedicated server/client connection and visible details panel tested in an isolated instance.

Release artifacts: SimpleMap f8q11 and F8Q Party 1.0.11. Only these two mod files change. Existing non-map party features are preserved. The map payload protocol is version 2, requiring both updated client and server.


## Map polish v12

Old generated caches are not imported: only the per-player discovery history is retained. Confirmed surface records are rebuilt from loaded chunks into `f8q-surface-places-v3.json`. The known TREK monster-room namespace is explicitly excluded. Unknown structure types have no marker; category 4 is reserved for undiscovered supported places. Tavern structures and verified friendly TREK camps are settlements; verified desert/jungle outposts are pillager camps. Villager identifiers cannot accidentally match illager identifiers.

Actual ruined-portal piece placement is read through a narrow Mixin accessor: UNDERGROUND and IN_MOUNTAIN portals are rejected. Terrain height checks apply to every structure category; no centre-chunk loading is requested. Automatic POIs are hidden in cave-map modes. Surface ruins on the ocean floor remain eligible, using the ocean-floor heightmap rather than the water surface.

Ruins icons are larger. Full-map icons and captions use screen-space collision bins; undiscovered points show their caption only when hovered/selected. Selection is enlarged with a bounded 1.20–1.36 scale pulse and a thin ring. Unmapped labels use Russian category names; an unclassified object is suppressed.

Tests include real installed TREK IDs, portal placement exclusions, tavern categories, villager/illager separation, unsupported-object suppression, label/icon collision rules, and pulse bounds. Dedicated server starts, generates a ruined portal and shuts down cleanly with the portal accessor active. Only the two map integration JARs are deployed.


## Journal and interface v13

Known surface places are saved client-side in the existing map world directory as `f8q-places-<account UUID>.json`. The journal keeps all dimensions, opened/question states, personal names/colors and the private route. It flushes on logout/world change and at bounded intervals when dirty. Atomic file replacement protects prior data. Explicit JSON record adapters support both older and newer Gson versions. The server also remembers each player's seen places and resends previously seen/opened points even outside the immediate 4096-block window. The cache schema from v12 is preserved.

Generated places, personal waypoints and party marks use one PlaceEntry presentation for the list, card and menu. The list is scrollable with a draggable scrollbar; clicking focuses the map. It includes actual waypoint item icons and party owners/colors. Personal POI edits stay only in the local journal. The active route is excluded from shared party pins and is rendered locally with name/distance and a larger mini-map edge icon. Party spot payloads are version 3 to carry the actual item icon; generated-place payload remains version 2.

The settings gear opens map interface settings: POI visibility (default on), marker size and active-route size (0.75–3), and a link to all original settings. Preferences live outside the pack's managed config files in `simplemap/f8q-map-ui.properties`. Mini-map rendering exits for every AbstractContainerScreen, including creative/survival inventories and chests. Cave-map policy defaults to 2 and the pack-managed simplemap-server.json is set to 2; underground POIs remain excluded.

The places-list and personal-editor panels are original imagegen PNGs matching the supplied map-ui style. Source PNGs are unmodified, with their transparent margins handled by nine-slice UV coordinates. GUI review in an isolated instance verified a 44-row scrollable journal, camera focus, actual card/list icons, personal color changes and inventory minimap hiding. Serialization tests cover restart persistence, world/account separation and party icon payload roundtrips.


## Regression repair v14

The original MapConfigScreen and waypoint-manager source is identical to the pre-v13 version. The gear opens the original tabbed settings directly. The original toolbar implementation is restored and GUI controls are drawn in a dedicated foreground layer. Automatic surface POIs respect the user visibility preference without depending on CaveType; underground locations are still rejected server-side and on journal load. Selection scales the single world-space icon and no longer draws a second icon or a ring. Journals flush confirmed deltas, restore after session rebind, retain opened status on stale deltas and persist removals. The server resets per-connection delta suppression on login. Regression tests exercise actual TeamMarks logout/reload without new packets, stale deltas and persisted deletions; isolated dedicated-server startup/shutdown and portal mixin validation passed. Live visual validation of v14 is still pending.


## Party exploration and HUD v15

Latest user policy: party members permanently learn previously shared areas and opened POIs. Leaving stops future sharing but does not erase learned knowledge. Raster tiles use a separate world/account-scoped compressed client journal, never fake generated terrain. The server maintains per-player watched coverage and uploaded raster tiles. Transfers are bounded at eight 16x16 tiles per owner per half-second; membership is checked at every send and receive. Client export has a 2ms admission budget, though the final single tile job can exceed that budget. Raster data is displayed as a separate alpha-masked layer over unknown personal terrain. No forced chunk generation or global reveal to strangers is used. Files are bounded, and color tile storage is currently capped at 16,384 tiles per player.

Generated POIs use real chunk coverage or learned knowledge, with names of Waystones carried in payload protocol 4. Obelisks have block lifecycle rescans, bounded loaded-block validation, persisted deletion tombstones and actual names. Shared-place protocol 2 carries the same named-place codec. The ConchWaystonesMixin startup workaround from the preceding Claude patch is preserved. Other mod files are unchanged.

HUD changes use original, unmodified supplied arrow/heart PNGs with explicit UV selection. Health strips sample the active Minecraft resource-pack heart textures (XP-like HUD). The existing low-health vignette remains; the second procedural downed overlay is removed. The lower HUD is inset six GUI units and hidden during bleeding. Only the hovered-player tooltip lost its box; original map settings are preserved.

Validation: codec and bounds tests; clock offsets; per-player stranger isolation; learned POI persistence across leaving and reconnecting; a two-client dedicated-server run confirmed 478/518 received tiles, including a remote peer area not visited by the recipient. Latest obelisk/HUD integration checks are recorded locally. This is not a measurement of every shader or seasonal snow overlay.


## v17: JourneyMap rollback

Restored the native-only map pipeline from v15. JourneyMap import and history streaming are removed. On first use, the auxiliary raster file is backed up before reset, without changing native region files, private waypoints or POI journals. The server similarly archives its old raster store and clears shared images once, retaining per-player discovery coverage. This prevents the imported images from returning through party synchronization. Normal native party sharing remains available.

The supplied PNG keycaps select ё or ~ by game language. The revive prompt uses the existing left-button texture without a negative-scale quad. AppleSkin layers follow the same six-pixel lower-HUD inset and knockdown suppression as vanilla layers.
