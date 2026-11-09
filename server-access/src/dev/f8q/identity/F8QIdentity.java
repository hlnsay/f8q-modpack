package dev.f8q.identity;
import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
@Mod("f8qidentity")
public final class F8QIdentity {
    private static Map<String, GameProfile> reserved = Map.of();
    public F8QIdentity() {
        Path file = FMLPaths.CONFIGDIR.get().resolve("f8q-identities.json");
        try { reserved = read(Files.readString(file)); }
        catch (Exception e) { throw new IllegalStateException("F8Q: missing or invalid identity mapping; refusing unsafe offline login", e); }
    }
    public static Map<String, GameProfile> read(String text) {
        JsonArray rows = JsonParser.parseString(text).getAsJsonArray();
        Map<String, GameProfile> out = new HashMap<>();
        Set<UUID> ids = new HashSet<>();
        for (JsonElement row : rows) {
            JsonObject v = row.getAsJsonObject(); String name = v.get("name").getAsString();
            UUID id = UUID.fromString(v.get("uuid").getAsString());
            if (!name.matches("[A-Za-z0-9_]{3,16}") || out.put(name.toLowerCase(Locale.ROOT), new GameProfile(id, name)) != null || !ids.add(id))
                throw new IllegalArgumentException("Duplicate or invalid identity");
        }
        return Map.copyOf(out);
    }
    public static GameProfile profile(String name) { return resolve(name, reserved); }
    public static GameProfile resolve(String name, Map<String, GameProfile> known) {
        GameProfile old = known.get(name.toLowerCase(Locale.ROOT));
        if (old != null) return new GameProfile(old.getId(), old.getName());
        // Case changes must never create a second account or bypass the password of a new guest.
        UUID id = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name.toLowerCase(Locale.ROOT)).getBytes(StandardCharsets.UTF_8));
        return new GameProfile(id, name);
    }
}
