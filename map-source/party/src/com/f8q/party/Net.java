package com.f8q.party;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** What travels between the game and the server. */
final class Net {
    static final int INVITE = 0;
    static final int ACCEPT = 1;
    static final int DECLINE = 2;
    static final int LEAVE = 3;

    private Net() {
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> id(String name) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("f8qparty", name));
    }

    record Action(int kind, UUID target) implements CustomPacketPayload {
        static final Type<Action> TYPE = id("action");
        static final StreamCodec<FriendlyByteBuf, Action> CODEC = StreamCodec.of(
            (buf, a) -> {
                buf.writeVarInt(a.kind());
                buf.writeUUID(a.target());
            },
            buf -> new Action(buf.readVarInt(), buf.readUUID()));

        @Override
        public Type<Action> type() {
            return TYPE;
        }
    }

    record Offer(String from, int ticks) implements CustomPacketPayload {
        static final Type<Offer> TYPE = id("offer");
        static final StreamCodec<FriendlyByteBuf, Offer> CODEC = StreamCodec.of(
            (buf, o) -> {
                buf.writeUtf(o.from());
                buf.writeVarInt(o.ticks());
            },
            buf -> new Offer(buf.readUtf(), buf.readVarInt()));

        @Override
        public Type<Offer> type() {
            return TYPE;
        }
    }

    /** One party member as the others see them; downed is ticks spent knocked out, or -1. */
    record Mate(UUID id, String name, int color, float health, float max, float extra, int effect, int downed,
                double x, double y, double z, String dimension, boolean online, int back, float yaw) {
        void write(FriendlyByteBuf buf) {
            buf.writeUUID(id);
            buf.writeUtf(name);
            buf.writeVarInt(color);
            buf.writeFloat(health);
            buf.writeFloat(max);
            buf.writeFloat(extra);
            buf.writeVarInt(effect);
            buf.writeVarInt(downed + 1);
            buf.writeDouble(x);
            buf.writeDouble(y);
            buf.writeDouble(z);
            buf.writeUtf(dimension);
            buf.writeBoolean(online);
            buf.writeVarInt(back);
            buf.writeFloat(yaw);
        }

        static Mate read(FriendlyByteBuf buf) {
            return new Mate(buf.readUUID(), buf.readUtf(), buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readVarInt(),
                buf.readVarInt() - 1, buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readUtf(), buf.readBoolean(), buf.readVarInt(), buf.readFloat());
        }
    }

    /** Who is lifting whom right now, for everyone to see them kneel. */
    record Helpers(Map<UUID, UUID> pairs) implements CustomPacketPayload {
        static final Type<Helpers> TYPE = id("helpers");
        static final StreamCodec<FriendlyByteBuf, Helpers> CODEC = StreamCodec.of(
            (buf, h) -> {
                buf.writeVarInt(h.pairs().size());
                h.pairs().forEach((helper, target) -> {
                    buf.writeUUID(helper);
                    buf.writeUUID(target);
                });
            },
            buf -> {
                int size = buf.readVarInt();
                Map<UUID, UUID> pairs = new HashMap<>();
                for (int i = 0; i < size; i++) {
                    pairs.put(buf.readUUID(), buf.readUUID());
                }
                return new Helpers(pairs);
            });

        @Override
        public Type<Helpers> type() {
            return TYPE;
        }
    }

    record State(List<Mate> mates) implements CustomPacketPayload {
        static final Type<State> TYPE = id("state");
        static final StreamCodec<FriendlyByteBuf, State> CODEC = StreamCodec.of(
            (buf, s) -> {
                buf.writeVarInt(s.mates().size());
                s.mates().forEach(mate -> mate.write(buf));
            },
            buf -> {
                int size = buf.readVarInt();
                List<Mate> mates = new ArrayList<>();
                for (int i = 0; i < size; i++) {
                    mates.add(Mate.read(buf));
                }
                return new State(mates);
            });

        @Override
        public Type<State> type() {
            return TYPE;
        }
    }

    /** One place on the map: a pin or a waypoint. */
    record Spot(boolean pin, String label, double x, double y, double z, String dimension,String iconItem,int kind) {
        Spot(boolean pin,String label,double x,double y,double z,String dimension){this(pin,label,x,y,z,dimension,"",13);}
        void write(FriendlyByteBuf buf) {
            buf.writeBoolean(pin);
            buf.writeUtf(label, 64);
            buf.writeDouble(x);
            buf.writeDouble(y);
            buf.writeDouble(z);
            buf.writeUtf(dimension, 256);
            buf.writeUtf(iconItem,128);buf.writeVarInt(kind);
        }

        static Spot read(FriendlyByteBuf buf) {
            return new Spot(buf.readBoolean(), buf.readUtf(64), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readUtf(256),buf.readUtf(128),buf.readVarInt());
        }
    }

    private static void writeSpots(FriendlyByteBuf buf, List<Spot> spots) {
        buf.writeVarInt(spots.size());
        spots.forEach(spot -> spot.write(buf));
    }

    private static List<Spot> readSpots(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        if(size<0||size>48)throw new IllegalArgumentException("Invalid shared marker count");
        List<Spot> spots = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            spots.add(Spot.read(buf));
        }
        return spots;
    }

    /** A player's own marks, sent up every few seconds. */
    record MarksUp(List<Spot> spots) implements CustomPacketPayload {
        static final Type<MarksUp> TYPE = id("marks_up");
        static final StreamCodec<FriendlyByteBuf, MarksUp> CODEC = StreamCodec.of((buf, m) -> writeSpots(buf, m.spots()), buf -> new MarksUp(readSpots(buf)));

        @Override
        public Type<MarksUp> type() {
            return TYPE;
        }
    }

    /** The same marks handed to the owner's party. */
    record MarksDown(UUID owner, List<Spot> spots) implements CustomPacketPayload {
        static final Type<MarksDown> TYPE = id("marks_down");
        static final StreamCodec<FriendlyByteBuf, MarksDown> CODEC = StreamCodec.of(
            (buf, m) -> {
                buf.writeUUID(m.owner());
                writeSpots(buf, m.spots());
            },
            buf -> new MarksDown(buf.readUUID(), readSpots(buf)));

        @Override
        public Type<MarksDown> type() {
            return TYPE;
        }
    }

    /** A place for the map: its chunk, which picture it gets and whether it has been reached. */
    record Place(String id, String structure, double x, double y, double z, int kind, boolean open) {
    }

    /** The places around a player, reached or not. */
    record Unknowns(String dimension, List<Place> places) implements CustomPacketPayload {
        static final Type<Unknowns> TYPE = id("unknowns");
        static final StreamCodec<FriendlyByteBuf, Unknowns> CODEC = StreamCodec.of(
            (buf, u) -> {
                buf.writeUtf(u.dimension(), 256);
                buf.writeVarInt(u.places().size());
                for (Place place : u.places()) {
                    buf.writeUtf(place.id(), 256);
                    buf.writeUtf(place.structure(), 256);
                    buf.writeDouble(place.x()); buf.writeDouble(place.y()); buf.writeDouble(place.z());
                    buf.writeVarInt(place.kind());
                    buf.writeBoolean(place.open());
                }
            },
            buf -> {
                String dimension = buf.readUtf(256);
                int size = buf.readVarInt();
                if (size < 0 || size > 128) throw new IllegalArgumentException("Invalid map place count");
                List<Place> places = new ArrayList<>();
                for (int i = 0; i < size; i++) {
                    places.add(new Place(buf.readUtf(256), buf.readUtf(256), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readVarInt(), buf.readBoolean()));
                }
                return new Unknowns(dimension, places);
            });

        @Override
        public Type<Unknowns> type() {
            return TYPE;
        }
    }
}
