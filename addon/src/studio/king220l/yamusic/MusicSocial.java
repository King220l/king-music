package studio.king220l.yamusic;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.util.Util;

import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.net.ssl.SSLException;

import static studio.king220l.yamusic.rooms.RoomWire.*;

final class MusicSocial {
    enum Link { OFF, MINECRAFT, BETA }

    private static final URI BETA_ENDPOINT = URI.create("https://king220l.com/king-music-link/v1/poll");
    private static final int REQUEST_SECONDS = 8;
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6))
        .followRedirects(HttpClient.Redirect.NEVER).build();
    private final MusicMod mod;
    private final TogetherSettings settings;
    private final Map<UUID, String> badgeKeys = new HashMap<>();
    private Set<String> badges = Set.of();
    private ClientPacketListener connection;
    private Link link = Link.OFF;
    private CompletableFuture<Void> betaSending = CompletableFuture.completedFuture(null);
    private String status = "", connectionIssue = "";
    private Room room;
    private Action pending;
    private Flight flight;
    private boolean announced, leaving;
    private int token;
    private long nextPoll, lastReply, lastPollSent, lastSeek;
    private long publishedId, publishedPosition, publishedRevision;
    private boolean publishedPaused;
    private String followingKey = "";
    private long previousHostPosition, previousHostArrival, previousRevision;
    private boolean previousHostPaused;

    private record Action(String op, String code, String target) {
        Action(String op, String code) { this(op, code, ""); }
    }

    private record Flight(int token, Request request, long sent) {}

    MusicSocial(MusicMod mod, TogetherSettings settings) {
        this.mod = mod;
        this.settings = settings;
        register();
        ClientPlayNetworking.registerGlobalReceiver(Snapshot.TYPE, (packet, context) ->
            context.client().execute(() -> {
                if (link == Link.MINECRAFT && connection == context.client().getConnection())
                    receive(packet.serial(), parse(packet.json()), "Ответ сервера Minecraft не прочитан.");
            }));
    }

    Room room() { return room; }
    Link link() { return link; }
    boolean busy() { return pending != null || flight != null && !"poll".equals(flight.request.op()); }
    boolean following() { return room != null && !room.host(); }
    String status() { return connectionIssue.isEmpty() ? status : connectionIssue; }

    boolean marks(UUID player) {
        if (!settings.showBadges || badges.isEmpty() || Util.getMillis() - lastReply >= 7500) return false;
        if (badgeKeys.size() >= 512) badgeKeys.clear();
        return badges.contains(badgeKeys.computeIfAbsent(player, id -> badge(id)));
    }

    void create() { enter(new Action("create", "")); }

    void join(String code) {
        String normalized = code.strip().toUpperCase(Locale.ROOT).replace("-", "").replace(" ", "");
        if (!validCode(normalized)) {
            status = "Введи код комнаты из десяти символов.";
            return;
        }
        enter(new Action("join", normalized));
    }

    private void enter(Action action) {
        if (!mod.api.signedIn()) status = "Для комнаты войди в свой аккаунт Яндекса.";
        else if (link == Link.OFF) status = "Включи связь · Бета или зайди на сервер с Together Server.";
        else if (room == null && !busy()) submit(action);
    }

    void transfer(Member member) {
        if (room == null || !room.host() || member == null || member.host() || member.id() == null
                || !member.connected() || busy()) return;
        submit(new Action("transfer", "", member.id()));
    }

    void leave() {
        if (room == null && !busy()) return;
        endRoom();
        leaving = true;
        submit(new Action("leave", ""));
    }

    private void submit(Action action) {
        pending = action;
        status = "Подключение…";
        connectionIssue = "";
        nextPoll = 0;
    }

    void tick(Minecraft game) {
        long now = Util.getMillis();
        var active = game.getConnection();
        boolean nativeLink = active != null && game.player != null && ClientPlayNetworking.canSend(Poll.TYPE);
        Link next = nativeLink ? Link.MINECRAFT : settings.betaLink ? Link.BETA : Link.OFF;
        if (next != link || link == Link.MINECRAFT && active != connection) {
            if (link == Link.BETA && announced) sendBetaEnd("leave");
            resetLink();
            link = next;
        }
        connection = active;
        if (link == Link.OFF) return;
        if (!mod.api.signedIn() && (room != null || pending != null && !"leave".equals(pending.op))) leave();
        if (flight != null && now - flight.sent >= REQUEST_SECONDS * 1000L)
            receive(flight.token, null, "Связь не ответила вовремя.");
        if (flight != null || now < nextPoll && !hostPlaybackChanged(now)) return;

        boolean authenticated = mod.api.signedIn();
        boolean beta = link == Link.BETA;
        if (beta && (!betaSending.isDone() || !authenticated && room == null && pending == null && game.player == null)) return;
        Action action = pending != null ? pending : new Action("poll", "");
        pending = null;
        boolean publish = authenticated && (action.op.equals("create") || action.op.equals("poll") && room != null && room.host());
        boolean roomAction = room != null || action.op.equals("create") || action.op.equals("join");
        String name = beta && roomAction ? game.getUser().getName() : "";
        String identity = beta && game.player != null ? badge(game.player.getUUID()) : "";
        var request = new Request(beta ? settings.betaClient : "", action.op, action.code, action.target, name,
            identity.isEmpty() ? "" : RELAY_WORLD, identity, settings.showBadges, publish,
            room == null ? null : room.epoch(), publish ? hostTrack() : null, authenticated, 2);
        if (publish) {
            publishedId = mod.player.playbackId();
            publishedPosition = mod.player.elapsedMs();
            publishedPaused = mod.player.paused() || !mod.player.started();
            publishedRevision = mod.player.seekRevision();
        }
        lastPollSent = now;
        nextPoll = now + (room == null ? 2300 : beta ? 800 : 550);
        announced = true;
        flight = new Flight(++token, request, now);
        if (beta) sendBeta(request, token);
        else ClientPlayNetworking.send(new Poll(token, JSON.toJson(request)));
    }

    private boolean hostPlaybackChanged(long now) {
        if (room == null || !room.host() || !connectionIssue.isEmpty() || now - lastPollSent < 400) return false;
        var player = mod.player;
        boolean paused = player.paused() || !player.started();
        long predicted = publishedPosition + (publishedPaused ? 0 : now - lastPollSent);
        return player.playbackId() != publishedId || player.seekRevision() != publishedRevision || paused != publishedPaused
            || Math.abs(player.elapsedMs() - predicted) > 1500;
    }

    private Track hostTrack() {
        var player = mod.player;
        var track = player.track();
        if (track == null || track.file() != null || player.playbackId() == 0) return null;
        return cleanTrack(new Track("YANDEX", track.id(), track.title(), track.artist(), track.durationMs(),
            player.elapsedMs(), player.paused() || !player.started(), player.playbackId(), player.seekRevision()));
    }

    private void sendBeta(Request request, int serial) {
        var http = HttpRequest.newBuilder(BETA_ENDPOINT).timeout(Duration.ofSeconds(REQUEST_SECONDS))
            .header("Content-Type", "application/json")
            .header("User-Agent", "KingMusicTogether/0.2")
            .POST(HttpRequest.BodyPublishers.ofString(JSON.toJson(request))).build();
        betaSending = betaSending.handle((ignored, previousFailure) -> null).thenCompose(ignored -> {
            var sending = HTTP.sendAsync(http, MusicHttp.text(32_768));
            return sending.copy().orTimeout(REQUEST_SECONDS, TimeUnit.SECONDS).whenComplete((response, failure) -> {
                if (failure != null) sending.cancel(true);
            });
        }).handle((response, failure) -> {
            if (serial < 0) return null;
            int code = response == null ? 0 : response.statusCode();
            Reply reply = failure == null && code == 200 ? parse(response.body()) : null;
            String issue = reply == null ? connectionIssue(failure, code) : "";
            Minecraft.getInstance().execute(() -> receive(serial, reply, issue));
            return null;
        });
    }

    private static Reply parse(String json) {
        try {
            Reply reply = JSON.fromJson(json, Reply.class);
            return reply != null && reply.error() != null && reply.badges() != null ? reply : null;
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    private static String connectionIssue(Throwable failure, int code) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SSLException) return "Ошибка защищённой связи.";
            if (cause instanceof UnknownHostException || cause instanceof ConnectException) return "Связь · Бета недоступна.";
            if (cause instanceof HttpTimeoutException || cause instanceof TimeoutException) return "Связь · Бета не ответила вовремя.";
        }
        return switch (code) {
            case 0, 404 -> "Связь · Бета недоступна.";
            case 200 -> "Связь · Бета вернула непонятный ответ.";
            case 429 -> "Слишком много запросов. Попробуй чуть позже.";
            default -> "Ошибка связи · Бета (HTTP " + code + ").";
        };
    }

    private void receive(int serial, Reply reply, String issue) {
        if (flight == null || flight.token != serial) return;
        Flight finished = flight;
        flight = null;
        Request request = finished.request;
        long now = Util.getMillis();
        if (reply == null) {
            badges = Set.of();
            connectionIssue = issue;
            if (following()) mod.player.setPaused(true);
            if (leaving && pending == null && request.op().equals("leave")) pending = new Action("leave", "");
            nextPoll = now + 2400;
            return;
        }
        connectionIssue = "";
        lastReply = now;
        if (settings.showBadges) {
            var keys = new HashSet<String>();
            for (String key : reply.badges()) {
                if (key != null && key.matches("[a-f0-9]{64}")) keys.add(key);
                if (keys.size() >= MAX_BADGES) break;
            }
            badges = Set.copyOf(keys);
        } else badges = Set.of();
        if (leaving && !request.op().equals("leave")) return;
        if (request.op().equals("leave")) leaving = false;
        if (reply.error().equals("busy")) {
            status = "Связь занята. Попробуй чуть позже.";
            nextPoll = now + 1000;
            return;
        }
        Room next = reply.room();
        if (next != null && (!validCode(next.code()) || next.count() < 1 || next.count() > MAX_MEMBERS)) next = null;
        if (next != null && !mod.api.signedIn()) {
            endRoom();
            leaving = true;
            submit(new Action("leave", ""));
            return;
        }
        applyRoom(next);
        if (!reply.error().isEmpty()) {
            status = switch (reply.error()) {
                case "account_required" -> "Для комнаты войди в свой аккаунт Яндекса.";
                case "room_missing" -> "Комната не найдена или ведущий вышел.";
                case "room_full" -> "Комната заполнена.";
                case "host_only" -> "Роль может передать только ведущий.";
                case "member_missing" -> "Игрок уже вышел из комнаты.";
                default -> "Не удалось выполнить действие. Попробуй ещё раз.";
            };
            return;
        }
        if (room != null && room.host()) {
            status = "Ты управляешь воспроизведением.";
        } else if (following()) {
            if (room.leaderConnected()) follow(room, Math.clamp((now - finished.sent) / 2, 0, 1500), now);
            else {
                mod.player.setPaused(true);
                status = "Ждём ведущего. Через 2 минуты роль перейдёт участнику.";
            }
        } else if (request.op().equals("leave")) status = "Ты вышел из комнаты.";
    }

    private void applyRoom(Room next) {
        Room previous = room;
        boolean same = previous != null && next != null && previous.code().equals(next.code());
        if (previous != null && !same) {
            endRoom();
            if (next == null) status = "Комната закрылась.";
        }
        room = next;
        if (next == null) return;
        boolean transferred = same && (previous.epoch() != next.epoch() || previous.host() != next.host());
        if (same && !transferred) return;
        var player = mod.player;
        if (transferred || !next.host()) player.clear();
        if (next.host()) player.beginHosting();
        else player.beginFollowing();
        resetFollow();
        nextPoll = 0;
    }

    private void follow(Room state, long latency, long now) {
        Track track = state.track();
        var player = mod.player;
        if (!validTrack(track)) {
            if (!followingKey.isEmpty()) player.clear();
            resetFollow();
            status = "Ждём трек ведущего из Яндекс Музыки.";
            return;
        }
        String key = track.id() + ":" + track.instance();
        long target = Math.clamp(track.position(), 0, MAX_TIME)
            + (track.paused() ? 0 : Math.min(state.publishedAgo() + latency, 4500));
        long expected = previousHostPosition + (previousHostPaused ? 0 : Math.max(0, now - previousHostArrival));
        boolean newTrack = !key.equals(followingKey);
        boolean jumped = !newTrack && (track.revision() != previousRevision || Math.abs(target - expected) > 2900);
        followingKey = key;
        previousHostPosition = target;
        previousHostArrival = now;
        previousRevision = track.revision();
        previousHostPaused = track.paused();
        if (newTrack) {
            String title = shortText(track.title());
            player.follow(new MusicApi.Track(track.id(), title.isBlank() ? "Трек Яндекс Музыки" : title,
                shortText(track.artist()), Math.clamp(track.duration(), 0, MAX_TIME)), target, track.paused());
            lastSeek = now;
        }
        if (player.track() == null) return;
        if (!player.error().isEmpty()) {
            status = player.error();
            return;
        }
        if (player.preview() && target >= player.durationMs()) {
            player.setPaused(true);
            status = "В твоём аккаунте доступен только фрагмент этого трека.";
            return;
        }
        status = player.loading() ? "Открываем трек ведущего…" : player.preview()
            ? "В твоём аккаунте играет фрагмент." : "Воспроизведение синхронизировано.";
        long drift = Math.abs(player.elapsedMs() - target);
        if (!newTrack && player.canSeek() && (jumped || player.started() && drift > 1400 && now - lastSeek >= 4500)) {
            player.followSeek(target, track.paused());
            lastSeek = now;
        } else if (jumped && !player.loading() && !player.started() && target < track.duration() - 500) {
            player.follow(player.track(), target, track.paused());
            lastSeek = now;
        } else player.setPaused(track.paused());
    }

    private void resetFollow() {
        followingKey = "";
        previousHostPosition = previousHostArrival = previousRevision = lastSeek = 0;
        previousHostPaused = true;
    }

    private void endRoom() {
        if (following()) mod.player.clear();
        room = null;
        mod.player.endFollowing();
        mod.player.endHosting();
        resetFollow();
    }

    void stop() {
        if (announced && link == Link.BETA) sendBetaEnd(leaving ? "leave" : "suspend");
        resetLink();
    }

    private void sendBetaEnd(String operation) {
        sendBeta(new Request(settings.betaClient, operation, "", "", "", "", "",
            false, false, null, null, false, 2), -1);
    }

    private void resetLink() {
        link = Link.OFF;
        pending = null;
        flight = null;
        leaving = announced = false;
        endRoom();
        badges = Set.of();
        connectionIssue = "";
        status = "";
        nextPoll = 0;
    }

}

