package studio.king220l.yamusic.rooms;

import com.google.gson.Gson;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

public final class RoomWire {
    public static final Gson JSON = new Gson();
    public static final long MAX_TIME = 86_400_000;
    public static final int MAX_MEMBERS = 24;
    public static final int MAX_BADGES = 256;
    public static final String RELAY_WORLD = hash("king-music-presence:v2");
    private static boolean registered;

    private RoomWire() {}

    public record Track(String source, String id, String title, String artist, long duration, long position,
                        boolean paused, long instance, long revision) {}

    public record Member(String id, String name, boolean host, Boolean online) {
        public boolean connected() { return !Boolean.FALSE.equals(online); }
    }

    public record Room(String code, boolean host, int count, double age, long seq, int epoch, Track track,
                       String own, List<Member> members, Boolean hostOnline, Integer online) {
        public List<Member> peers() { return members == null ? List.of() : members; }
        public boolean leaderConnected() { return !Boolean.FALSE.equals(hostOnline); }
        public int connectedCount() { return online == null ? count : Math.clamp(online, 0, count); }
        public long publishedAgo() { return Math.clamp(Math.round(age), 0, 4500); }
    }

    public record Reply(String error, Room room, List<String> badges) {}

    public record Request(String client, String op, String code, String target, String name, String world, String badge,
                          boolean see, boolean publish, Integer epoch, Track state, boolean signedIn, int protocol) {}

    public static synchronized void register() {
        if (registered) return;
        PayloadTypeRegistry.serverboundPlay().register(Poll.TYPE, Poll.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Snapshot.TYPE, Snapshot.CODEC);
        registered = true;
    }

    public record Poll(int serial, String json) implements CustomPacketPayload {
        public static final Type<Poll> TYPE = new Type<>(Identifier.fromNamespaceAndPath("king_music_together", "poll"));
        private static final StreamCodec<ByteBuf, Poll> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Poll::serial, ByteBufCodecs.stringUtf8(4096), Poll::json, Poll::new);
        @Override public Type<Poll> type() { return TYPE; }
    }

    public record Snapshot(int serial, String json) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE = new Type<>(Identifier.fromNamespaceAndPath("king_music_together", "snapshot"));
        private static final StreamCodec<ByteBuf, Snapshot> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Snapshot::serial, ByteBufCodecs.stringUtf8(32768), Snapshot::json, Snapshot::new);
        @Override public Type<Snapshot> type() { return TYPE; }
    }

    public static boolean validCode(String code) { return code != null && code.matches("[A-F0-9]{10}"); }

    public static boolean validTrack(Track track) {
        return track != null && "YANDEX".equals(track.source) && track.id != null && track.id.matches("[0-9]{1,16}(:[0-9]{1,16})?");
    }

    public static Track cleanTrack(Track track) {
        return !validTrack(track) ? null : new Track("YANDEX", track.id, shortText(track.title), shortText(track.artist),
            Math.clamp(track.duration, 0, MAX_TIME), Math.clamp(track.position, 0, MAX_TIME), track.paused,
            Math.max(0, track.instance), Math.max(0, track.revision));
    }

    public static String shortText(String value) {
        if (value == null) return "";
        return value.substring(0, Math.min(value.length(), 120))
            .replaceAll("[\\p{Cntrl}\\u202a-\\u202e\\u2066-\\u2069]", "");
    }

    public static String badge(UUID player) { return hash("king-music-player:" + RELAY_WORLD + ":" + player); }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}

