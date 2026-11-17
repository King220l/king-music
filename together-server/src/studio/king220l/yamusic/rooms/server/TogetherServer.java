package studio.king220l.yamusic.rooms.server;

import com.google.gson.JsonParseException;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Util;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import static studio.king220l.yamusic.rooms.RoomWire.*;

public final class TogetherServer implements ModInitializer {
    private final Map<MinecraftServer, RoomDirectory> servers = new WeakHashMap<>();

    @Override
    public void onInitialize() {
        register();
        ServerPlayNetworking.registerGlobalReceiver(Poll.TYPE, (poll, context) -> {
            var player = context.player();
            if (!ServerPlayNetworking.canSend(player, Snapshot.TYPE)) return;
            Reply reply;
            try {
                var request = JSON.fromJson(poll.json(), Request.class);
                var rooms = servers.computeIfAbsent(context.server(), key -> new RoomDirectory());
                reply = rooms.update(player.getUUID(), player.getScoreboardName(), request, Util.getMillis());
            } catch (JsonParseException invalid) {
                reply = new Reply("bad_request", null, List.of());
            }
            context.responseSender().sendPacket(new Snapshot(poll.serial(), JSON.toJson(reply)));
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            var rooms = servers.get(server);
            if (rooms != null) rooms.disconnect(handler.getPlayer().getUUID(), Util.getMillis());
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 != 0) return;
            var rooms = servers.get(server);
            if (rooms != null) rooms.expireOffline(Util.getMillis());
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(servers::remove);
    }
}

