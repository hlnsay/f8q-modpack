# F8Q nickname access without passwords

F8Q Launcher 0.0.9 connects by nickname without Microsoft ownership or a server password. Microsoft sign-in remains optional through the original engine.

Current server-only components for NeoForge 1.21.1:
- Skin Restorer 2.11.1+1.21-neoforge: https://modrinth.com/mod/skinrestorer
- F8Q Player Identity 1.0.0: source in this directory. Existing names retain their old UUIDs, inventories, operator rights and mod data. New guest identities are case-insensitive.

EasyLogin, its config and account database have been removed at the server owner's request. No registration or /login command is needed. The server uses online-mode=false and enforce-secure-profile=false. Nicknames do not establish authenticated ownership in this mode.

Official skins are fetched asynchronously from Mojang, cached and sent through standard player-info packets. No client skin mod or resource pack is needed.

The private config/f8q-identities.json mapping is deployed by the operator, never published. The bridge refuses startup if the mapping file is absent or malformed. Activation of mod removal requires a server restart; no player data is renamed or converted.

Build the identity mod with build.py using a local NeoForge development classpath. Launcher overlay file placement is documented in ../launcher/README.md.
