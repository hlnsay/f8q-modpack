# F8Q 1.21.1 NeoForge expansion

All upstream downloads are pinned in selected.json by original URL and SHA-512.

Replaces Luki’s Strongholds v1.0 with YUNG’s Better Strongholds; Better Dungeons 5.1.4 and YUNG’s API 5.1.9 are already installed. Existing chunks are not regenerated.

Not installed: Blast Travel (Quilt 1.19.2 only), Nef’s Medieval Decoration (no 1.21.1 NeoForge release). AlcoCraft+ 2.1.2 is the supported alternative to the old Alcocraft Beer & Stuff link.

Sable’s optional UDP transport is disabled on both sides: exaroton provides one UDP port, reserved for Simple Voice Chat (port=-1). Sable uses its normal Minecraft/TCP fallback. Native physics stays at 2 substeps/tick. Assembly limit: 8192 blocks; network tracking: 160 blocks. Projectile loader budget: 8 forced chunks / 4 loaded per tick; smoke cannot keep chunks loaded. CBC uses shader-compatible effects, fewer liquid particles and no extra smoke/flame layers. Blood decal lifetime: 0.5. Furniture link visualization: 24 blocks. Cave sandstorm extra particles disabled. FancyMenu background preserved.

F8Q Pack Defaults is client-only. It assigns F10 to voice UI, CapsLock to push-to-talk, leaves extra voice shortcuts unbound and disables the developer Veil editor shortcut. Explicitly saved player bindings remain untouched.

Russian: preserve built-in translations; add 365 missing keys with unchanged formatting placeholders in Cozy-Russian. RuFurniture is downloaded unchanged from its author’s Modrinth CDN and covers all 654 Refurbished Furniture keys.

Known upstream limitation: Create Aeronautics has visual issues with Iris Shaders. Successful startup is not a guarantee for every moving contraption/shader combination.

Server is not restarted automatically. Update clients before rejoining after the server restart. New world generation appears in new chunks. No production map caches or saves are deleted.
