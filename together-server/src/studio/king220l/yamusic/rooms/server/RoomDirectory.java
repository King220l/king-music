package studio.king220l.yamusic.rooms.server;

import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static studio.king220l.yamusic.rooms.RoomWire.*;

final class RoomDirectory {
    static final long OWNER_GRACE_MS = 120_000;
    private static final SecureRandom RANDOM = new SecureRandom();
    private final Map<UUID, Client> clients = new LinkedHashMap<>();
    private final Map<String, Session> rooms = new LinkedHashMap<>();
    private long nextExpiry = Long.MAX_VALUE;

    private static final class Client {
        final UUID player;
        final String member = UUID.randomUUID().toString();
        String name;
        Session room;
        long windowStarted = Long.MIN_VALUE;
        long offlineSince = -1;
        int requests;
        boolean signedIn;

        Client(UUID player) { this.player = player; }

        boolean online() { return offlineSince < 0; }
    }

    private static final class Session {
        final String code;
        final Set<Client> members = new LinkedHashSet<>();
        Client leader;
        Track track;
        int epoch;
        long sequence, published;

        Session(String code, Client leader, long now) {
            this.code = code;
            this.leader = leader;
            published = now;
            members.add(leader);
        }
    }

    Reply update(UUID player, String name, Request request, long now) {
        if (request == null || request.protocol() != 2 || request.op() == null) return error("bad_request");
        expireOffline(now);
        Client client = clients.computeIfAbsent(player, Client::new);
        if (client.windowStarted == Long.MIN_VALUE || now - client.windowStarted >= 10_000) {
            client.windowStarted = now;
            client.requests = 0;
        }
        if (++client.requests > 40) return error("busy");
        client.name = name;
        client.offlineSince = -1;
        client.signedIn = request.signedIn();
        if (!client.signedIn) leave(client, now);
        String failure = "";
        boolean created = false;
        switch (request.op()) {
            case "poll" -> { }
            case "leave" -> leave(client, now);
            case "create" -> {
                if (!client.signedIn) failure = "account_required";
                else if (client.room == null || client.room.leader != client) {
                    if (rooms.size() >= 256) failure = "busy";
                    else {
                        leave(client, now);
                        String code;
                        do { code = "%010X".formatted(RANDOM.nextLong() & 0xffffffffffL); }
                        while (rooms.containsKey(code));
                        client.room = new Session(code, client, now);
                        rooms.put(code, client.room);
                        created = true;
                    }
                }
            }
            case "join" -> {
                Session destination = rooms.get(request.code());
                if (!client.signedIn) failure = "account_required";
                else if (!validCode(request.code()) || destination == null) failure = "room_missing";
                else if (client.room != destination) {
                    if (destination.members.size() >= MAX_MEMBERS) failure = "room_full";
                    else {
                        leave(client, now);
                        destination.members.add(client);
                        client.room = destination;
                    }
                }
            }
            case "transfer" -> {
                var room = client.room;
                if (room == null || room.leader != client) failure = "host_only";
                else {
                    var target = room.members.stream().filter(peer -> peer != client && peer.signedIn && peer.online()
                        && peer.member.equals(request.target())).findFirst().orElse(null);
                    if (target == null) failure = "member_missing";
                    else assignLeader(room, target, now);
                }
            }
            default -> failure = "bad_request";
        }
        var room = client.room;
        if (room != null && room.leader == client && request.publish()
                && (created || request.epoch() != null && request.epoch() == room.epoch)) {
            room.track = cleanTrack(request.state());
            room.published = now;
            room.sequence++;
        }
        List<String> badges = request.see() ? clients.values().stream().filter(peer -> peer != client && peer.online())
            .limit(MAX_BADGES).map(peer -> badge(peer.player)).toList() : List.of();
        Room state = room == null ? null : new Room(room.code, room.leader == client, room.members.size(),
            Math.max(0, now - room.published), room.sequence, room.epoch, room.track, client.member,
            room.members.stream().map(peer -> new Member(peer.member, peer.name, room.leader == peer, peer.online())).toList(),
            room.leader.online(), (int) room.members.stream().filter(Client::online).count());
        return new Reply(failure, state, badges);
    }

    private static Reply error(String message) { return new Reply(message, null, List.of()); }

    private static void assignLeader(Session room, Client next, long now) {
        room.leader = next;
        room.track = null;
        room.published = now;
        room.sequence++;
        room.epoch++;
    }

    private void leave(Client client, long now) {
        var room = client.room;
        client.room = null;
        if (room == null) return;
        room.members.remove(client);
        if (room.leader == client) {
            Client next = room.members.stream().filter(peer -> peer.signedIn && peer.online()).findFirst().orElse(null);
            if (next != null) assignLeader(room, next, now);
            else {
                rooms.remove(room.code);
                for (Client peer : room.members) peer.room = null;
            }
        }
    }

    void disconnect(UUID player, long now) {
        Client client = clients.get(player);
        if (client == null) return;
        if (client.room == null) clients.remove(player);
        else {
            client.offlineSince = now;
            nextExpiry = Math.min(nextExpiry, now + OWNER_GRACE_MS);
        }
    }

    void expireOffline(long now) {
        if (now < nextExpiry) return;
        nextExpiry = Long.MAX_VALUE;
        var players = clients.values().iterator();
        while (players.hasNext()) {
            Client client = players.next();
            if (client.online()) continue;
            long expiry = client.offlineSince + OWNER_GRACE_MS;
            if (client.room == null || now >= expiry) {
                leave(client, now);
                players.remove();
            } else nextExpiry = Math.min(nextExpiry, expiry);
        }
    }
}

