# F8Q nickname access

F8Q Launcher 0.0.8 adds explicit nickname sessions. Microsoft sessions keep the original Horizon authentication engine; guest Java sessions use the F8Q executable. Game downloads use the official hash-checked Mojang manifests. No proprietary game files are redistributed.

Server-only components for NeoForge 1.21.1:
- EasyLogin 1.0.1: https://modrinth.com/mod/easylogin
- Skin Restorer 2.11.1+1.21-neoforge: https://modrinth.com/mod/skinrestorer
- F8Q Player Identity 1.0.0: source in this directory. The login profile mapping keeps existing inventory, operator and mod UUIDs intact; unknown guests use case-insensitive offline identities.

The server must have online-mode=false and enforce-secure-profile=false. Password authentication is required for all accounts, including Microsoft players. Existing names must be registered privately before the switch. Never publish the account database, passwords, usercache or the production identity mapping.

The operator deploys config/f8q-identities.json as an array of name/uuid objects from the existing authenticated player records. The bridge fails startup if that file is absent or invalid. It does not rename player data or rewrite mod saves.

New players use /register PASSWORD PASSWORD, returning players /login PASSWORD. /changepassword OLD NEW NEW changes the password. Official skins are fetched asynchronously from Mojang, cached and sent to all clients via ordinary player-info packets; no client skin mod or resource pack is required.

Activation requires a server restart. Updating a running server's files does not change its current authentication mode.

## Validation
61 account tests passed (2 upstream ignored); 16 updater tests passed. Isolated NeoForge tests checked preserved UUIDs, password rejection/acceptance, blocking pre-login commands, preventing re-registration through case changes, and signed official skin properties received by a different connected client. A private Java fixture was launched through the actual F8Q CLI with zero Microsoft accounts and verified the selected nickname/offline UUID.

Launcher overlay files follow ../launcher/README.md; account/tests.rs belongs under launcher/src/account/. Identity mixins intercept only offline profile construction in ServerLoginPacketListenerImpl.handleHello.
