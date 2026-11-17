package studio.king220l.yamusic;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.jna.Platform;
import com.sun.jna.platform.win32.Crypt32Util;
import com.sun.jna.platform.win32.WinCrypt;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

final class MusicApi {
    private static final URI API = URI.create("https://api.music.yandex.net");
    private static final URI OAUTH = URI.create("https://oauth.yandex.ru");
    private static final String CLIENT_ID = "23cabbbdc6cd418abb4b39c32c41195d";
    private static final String CLIENT_SECRET = "53bc75238f0c4d08a118e51fe9203300";
    private static final String AUDIO_SIGNATURE_PREFIX = "XGRlBW9FXlekgbPrRHuSiA";
    private static final String LYRICS_CLIENT_KEY = "p93jhgh689SBReK6ghtw62";
    private static final String LYRICS_CLIENT = "YandexMusicAndroid/24023621";
    private static final Gson GSON = new Gson();
    private static final int SESSION_LIMIT = 65_536;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
        .followRedirects(HttpClient.Redirect.NEVER).build();
    private final Path tokenFile;
    private final String device = UUID.randomUUID().toString();
    private final ReentrantLock refreshLock = new ReentrantLock();
    private volatile Session session;

    record DeviceCode(String code, String deviceCode, long expiresAt, int interval) {
        @Override public String toString() { return "DeviceCode"; }
    }
    record Token(String access, String refresh, long expiresAt) {
        @Override public String toString() { return "Token"; }
    }
    private record Session(Token token, Object identity) {}
    record Account(String id, String name) {}
    record WaveSession(String accountId, Object identity, String seed) {
        boolean seeded() { return !seed.isEmpty(); }
        String station() { return seeded() ? "track:" + seed : "user:onyourwave"; }
    }
    record Track(String id, String title, String artist, long durationMs, java.nio.file.Path file) {
        Track(String id, String title, String artist, long durationMs) { this(id, title, artist, durationMs, null); }
    }
    record Playlist(String owner, String kind, String title, int count) {}
    record CollectionItem(String id, String title, boolean album) {}
    record SearchPage(List<Track> tracks, boolean more) {}
    record Stream(URI uri, boolean preview) {}

    private static final class HttpError extends IOException {
        final int status;

        HttpError(int status) {
            super(switch (status) {
                case 401 -> "Сеанс истёк. Войди в Яндекс снова.";
                case 403 -> "Яндекс ограничил доступ. Проверь подписку и регион.";
                case 429 -> "Слишком много запросов. Повтори чуть позже.";
                default -> "Яндекс вернул ошибку " + status + ".";
            });
            this.status = status;
        }
    }

    MusicApi(Path config) {
        tokenFile = config.resolve("session.dpapi");
        if (!Platform.isWindows() || !Files.isRegularFile(tokenFile)) return;
        try (var input = Files.newInputStream(tokenFile)) {
            byte[] encrypted = input.readNBytes(SESSION_LIMIT + 1);
            if (encrypted.length > SESSION_LIMIT) return;
            byte[] clear = Crypt32Util.cryptUnprotectData(encrypted, WinCrypt.CRYPTPROTECT_UI_FORBIDDEN);
            try {
                Token saved = GSON.fromJson(new String(clear, StandardCharsets.UTF_8), Token.class);
                if (saved != null && validToken(saved.access())) {
                    Token value = new Token(saved.access(), validToken(saved.refresh()) ? saved.refresh() : "", saved.expiresAt());
                    session = new Session(value, new Object());
                }
            } finally {
                Arrays.fill(clear, (byte) 0);
            }
        } catch (IOException | RuntimeException ignored) {}
    }

    boolean signedIn() {
        return session != null;
    }

    synchronized boolean signIn(Token value) {
        session = new Session(value, new Object());
        return storeToken(value);
    }

    Account signInWithAccessToken(String access) throws Exception {
        BuildTrust.requireOfficial();
        if (!validToken(access)) throw new IOException("Токен имеет неверный формат.");
        Token value = new Token(access.strip(), "", Long.MAX_VALUE);
        Session candidate = new Session(value, new Object());
        Account owner = account(candidate);
        synchronized (this) {
            session = candidate;
            if (!storeToken(value)) {
                session = null;
                throw new IOException("Не удалось защитить токен в Windows.");
            }
        }
        return owner;
    }

    private boolean storeToken(Token value) {
        if (!Platform.isWindows()) return false;
        Path temp = null;
        byte[] clear = GSON.toJson(value).getBytes(StandardCharsets.UTF_8);
        try {
            Files.createDirectories(tokenFile.getParent());
            temp = Files.createTempFile(tokenFile.getParent(), "session-", ".tmp");
            byte[] encrypted = Crypt32Util.cryptProtectData(clear, WinCrypt.CRYPTPROTECT_UI_FORBIDDEN);
            Files.write(temp, encrypted);
            Files.move(temp, tokenFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (IOException | RuntimeException failure) {
            return false;
        } finally {
            Arrays.fill(clear, (byte) 0);
            if (temp != null) {
                try { Files.deleteIfExists(temp); }
                catch (IOException | RuntimeException ignored) {}
            }
        }
    }

    synchronized void signOut() throws IOException {
        session = null;
        Files.deleteIfExists(tokenFile);
        Files.deleteIfExists(tokenFile.resolveSibling("session.tmp"));
    }

    DeviceCode requestCode() throws Exception {
        BuildTrust.requireOfficial();
        JsonObject response = oauth("/device/code", "client_id=" + CLIENT_ID
            + "&device_id=" + encode(device) + "&device_name=" + encode("Minecraft Music"));
        String userCode = string(response, "user_code");
        String code = string(response, "device_code");
        if (!userCode.matches("[A-Za-z0-9-]{4,20}") || !validToken(code)) {
            throw new IOException("Яндекс не выдал код для входа.");
        }
        int expires = (int) Math.clamp(number(response, "expires_in"), 30, 900);
        int interval = (int) Math.clamp(number(response, "interval"), 5, 60);
        return new DeviceCode(userCode, code, System.currentTimeMillis() + expires * 1_000L, interval);
    }

    Token waitForToken(DeviceCode code) throws Exception {
        int interval = code.interval();
        while (System.currentTimeMillis() + interval * 1_000L < code.expiresAt()) {
            Thread.sleep(interval * 1_000L);
            JsonObject response = oauth("/token", "grant_type=device_code&code="
                + encode(code.deviceCode()) + "&client_id=" + CLIENT_ID + "&client_secret=" + CLIENT_SECRET);
            Token received = tokenFrom(response, "");
            if (received != null) return received;
            String error = string(response, "error");
            if ("slow_down".equals(error)) interval += 5;
            else if (!"authorization_pending".equals(error)) break;
        }
        throw new IOException("Вход не подтверждён. Запроси новый код.");
    }

    private Session refresh(Session expected) throws Exception {
        refreshLock.lockInterruptibly();
        try {
            Session current = currentSession(expected);
            if (current != expected) return current;
            Token old = current.token;
            Token updated = null;
            if (!old.refresh().isBlank()) {
                JsonObject response = oauth("/token", "grant_type=refresh_token&refresh_token="
                    + encode(old.refresh()) + "&client_id=" + CLIENT_ID + "&client_secret=" + CLIENT_SECRET);
                updated = tokenFrom(response, old.refresh());
            }
            synchronized (this) {
                currentSession(expected);
                if (updated == null) {
                    signOut();
                    throw new HttpError(401);
                }
                Session renewed = new Session(updated, expected.identity);
                session = renewed;
                storeToken(updated);
                return renewed;
            }
        } finally {
            refreshLock.unlock();
        }
    }

    private Session currentSession(Session expected) throws HttpError {
        Session current = session;
        if (expected == null || current == null || current.identity != expected.identity) throw new HttpError(401);
        return current;
    }

    private static Token tokenFrom(JsonObject value, String previousRefresh) {
        String access = string(value, "access_token");
        if (!validToken(access)) return null;
        String refresh = string(value, "refresh_token");
        long seconds = Math.clamp(number(value, "expires_in"), 0, 315_360_000L);
        return new Token(access, validToken(refresh) ? refresh : previousRefresh,
            seconds > 0 ? System.currentTimeMillis() + seconds * 1_000L : Long.MAX_VALUE);
    }

    private static boolean validToken(String value) {
        return value != null && !value.isEmpty() && value.length() <= 8192
            && value.chars().allMatch(c -> c > 32 && c < 127);
    }

    Account account() throws Exception {
        return account(session);
    }

    private Account account(Session session) throws Exception {
        JsonObject account = object(result("/account/status", session), "account");
        String id = string(account, "uid");
        if (!id.matches("[0-9]+")) throw new IOException("У Яндекс Музыки нет данных об аккаунте.");
        String name = string(account, "displayName");
        if (name.isBlank()) name = string(account, "login");
        return new Account(id, name.isBlank() ? "Яндекс ID" : name);
    }

    SearchPage search(String query, int page) throws Exception {
        JsonObject result = object(result("/search?type=track&text=" + encode(query) + "&page=" + page), "tracks");
        List<Track> matches = tracks(array(result, "results"));
        long perPage = number(result, "perPage");
        boolean more = perPage > 0 && (page + 1) * perPage < number(result, "total");
        return new SearchPage(matches, more);
    }

    List<Playlist> playlists(Account account) throws Exception {
        List<Playlist> playlists = new ArrayList<>();
        playlists.add(new Playlist(account.id(), "3", "Мне нравится", -1));
        for (JsonElement entry : asArray(result("/users/" + account.id() + "/playlists/list"))) {
            if (!entry.isJsonObject()) continue;
            JsonObject object = entry.getAsJsonObject();
            String kind = string(object, "kind");
            String title = string(object, "title");
            if (kind.matches("[0-9]+") && !kind.equals("3") && !title.isBlank()) {
                playlists.add(new Playlist(account.id(), kind, title, (int) number(object, "trackCount")));
            }
        }
        try {
            for (JsonElement entry : asArray(result("/users/" + account.id() + "/likes/playlists"))) {
                JsonObject playlist = object(entry, "playlist");
                String owner = string(object(playlist, "owner"), "uid");
                String kind = string(playlist, "kind");
                String title = string(playlist, "title");
                if (owner.matches("[0-9]+") && kind.matches("[0-9]+") && !title.isBlank()
                        && playlists.stream().noneMatch(existing -> existing.owner().equals(owner) && existing.kind().equals(kind))) {
                    playlists.add(new Playlist(owner, kind, title, (int) number(playlist, "trackCount")));
                }
            }
        } catch (HttpError optionalList) {
            if (optionalList.status == 401) throw optionalList;
        }
        return List.copyOf(playlists);
    }

    List<Track> playlist(Playlist playlist) throws Exception {
        String path = "/users/" + playlist.owner() + "/playlists/" + playlist.kind();
        JsonElement response = result(path);
        if (!response.isJsonObject()) return List.of();
        return tracks(array(response.getAsJsonObject(), "tracks"));
    }

    WaveSession waveSession() throws Exception {
        Session expected = currentSession(session);
        Account owner = account(expected);
        return new WaveSession(owner.id(), currentSession(expected).identity, "");
    }

    WaveSession waveByTrack(Track track) {
        Session current = session;
        String seed = WaveHistory.trackKey(track.id());
        if (current == null || track.file() != null || !seed.matches("[0-9]{1,24}")) return null;
        return new WaveSession("", current.identity, seed);
    }

    boolean waveActive(WaveSession wave) {
        Session current = session;
        return wave != null && current != null && wave.identity == current.identity;
    }

    List<Track> wave(WaveSession wave, WaveHistory history, String previousTrack) throws Exception {
        Session expected = session;
        if (expected == null || expected.identity != wave.identity) throw new HttpError(401);
        String previous = WaveHistory.trackKey(previousTrack);
        if (!previous.matches("[0-9]{1,24}")) previous = "";
        String cursor = previous.isEmpty() ? history.latest() : previous;
        if (wave.seeded() && cursor.equals(wave.seed)) cursor = "";
        List<Track> received;
        try {
            received = waveTracks(expected, wave, cursor);
        } catch (HttpError rejectedCursor) {
            if (cursor.isEmpty() || rejectedCursor.status != 400 && rejectedCursor.status != 404) throw rejectedCursor;
            received = waveTracks(expected, wave, "");
            cursor = "";
        }
        if (!cursor.isEmpty() && !history.hasNew(received, previous.isEmpty() ? cursor : previous)) {
            String tail = received.isEmpty() ? "" : WaveHistory.trackKey(received.getLast().id());
            if (tail.equals(cursor)) tail = "";
            try {
                var additional = new ArrayList<>(received);
                additional.addAll(waveTracks(expected, wave, tail));
                received = additional;
            } catch (IOException failure) {
                if (received.isEmpty() || !waveActive(wave)) throw failure;
            }
        }
        String exclude = previous.isEmpty() ? history.latest() : previous;
        List<Track> selected = history.choose(received, exclude);
        // A Wave station may return a stale page after rapid skips. Follow the
        // response tail up to twice before giving up, without looping forever.
        for (int attempt = 0; selected.isEmpty() && attempt < 2; attempt++) {
            String tail = received.isEmpty() ? "" : WaveHistory.trackKey(received.getLast().id());
            if (tail.isEmpty() || tail.equals(cursor)) break;
            cursor = tail;
            List<Track> more = waveTracks(expected, wave, cursor);
            if (more.isEmpty()) break;
            received = more;
            selected = history.choose(received, exclude);
        }
        if (selected.isEmpty()) throw new IOException("Моя волна пока не нашла новых треков. Попробуйте позже.");
        return selected;
    }

    private List<Track> waveTracks(Session expected, WaveSession wave, String cursor) throws Exception {
        String after = cursor.isEmpty() ? "" : "&queue=" + encode(cursor);
        JsonElement response = result("/rotor/station/" + wave.station() + "/tracks?settings2=True" + after, expected);
        List<Track> result = tracks(array(objectResponse(response), "sequence"));
        return wave.seeded() ? result.stream().filter(track -> !WaveHistory.trackKey(track.id()).equals(wave.seed)).toList() : result;
    }

    List<CollectionItem> collection(Account account, boolean albums) throws Exception {
        String kind = albums ? "album" : "artist";
        String suffix = albums ? "?rich=True" : "?with-timestamps=True";
        List<CollectionItem> items = new ArrayList<>();
        for (JsonElement entry : asArray(result("/users/" + account.id() + "/likes/" + kind + "s" + suffix))) {
            JsonObject data = object(entry, kind);
            if (data.isEmpty()) data = objectResponse(entry);
            String id = string(data, "id");
            String title = string(data, albums ? "title" : "name");
            if (!id.matches("[0-9]+")) id = string(objectResponse(entry), kind + "Id");
            if (id.matches("[0-9]+")) items.add(new CollectionItem(id, title.isBlank() ? kind + " " + id : title, albums));
        }
        return List.copyOf(items);
    }

    List<Track> collectionTracks(CollectionItem item) throws Exception {
        if (!item.album()) return tracks(array(objectResponse(result("/artists/" + item.id() + "/tracks?page=0")), "tracks"));
        List<Track> tracks = new ArrayList<>();
        for (JsonElement volume : array(objectResponse(result("/albums/" + item.id() + "/with-tracks")), "volumes")) {
            tracks.addAll(tracks(asArray(volume)));
        }
        return List.copyOf(tracks);
    }

    boolean toggleLike(Account account, Track track) throws Exception {
        Session expected = currentSession(session);
        String trackId = track.id().split(":")[0];
        JsonObject likes = object(result("/users/" + account.id() + "/likes/tracks", expected), "library");
        boolean liked = false;
        for (JsonElement entry : array(likes, "tracks")) {
            if (trackId.equals(string(objectResponse(entry), "id"))) {
                liked = true;
                break;
            }
        }
        Token current = currentSession(expected).token;
        String path = "/users/" + account.id() + "/likes/tracks/" + (liked ? "remove" : "add-multiple");
        HttpRequest request = request(API, path)
            .header("Authorization", "OAuth " + current.access())
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString("track-ids=" + encode(trackId))).build();
        JsonObject response = object(send(request), "result");
        if (!response.has("revision")) throw new IOException("Яндекс не подтвердил изменение отметки.");
        return !liked;
    }

    Stream stream(Track track, int targetKbps) throws Exception {
        JsonArray variants = asArray(result("/tracks/" + track.id() + "/download-info", session));
        JsonObject chosen = chooseMp3(variants, targetKbps);
        String location = string(chosen, "downloadInfoUrl");
        URI uri = cdn(URI.create(location + (location.contains("?") ? "&" : "?") + "format=json"));
        JsonElement manifest = send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15)).build(), 65_536);
        JsonObject info = manifest.getAsJsonObject();
        String path = string(info, "path");
        String host = string(info, "host");
        String timestamp = string(info, "ts");
        if (!path.startsWith("/") || !timestamp.matches("[0-9a-fA-F]{1,32}") || host.isBlank()
                || string(info, "s").isBlank()) {
            throw new IOException("Яндекс изменил формат ссылки на трек.");
        }
        String signatureInput = AUDIO_SIGNATURE_PREFIX + path.substring(1) + string(info, "s");
        String signature = HexFormat.of().formatHex(MessageDigest.getInstance("MD5")
            .digest(signatureInput.getBytes(StandardCharsets.UTF_8)));
        URI audio = cdn(URI.create("https://" + host + "/get-mp3/" + signature + "/" + timestamp + path));
        return new Stream(audio, bool(chosen, "preview"));
    }

    static JsonObject chooseMp3(JsonArray variants, int targetKbps) throws IOException {
        List<JsonObject> mp3 = new ArrayList<>();
        for (JsonElement item : variants) {
            if (item.isJsonObject() && "mp3".equals(string(item.getAsJsonObject(), "codec"))) {
                mp3.add(item.getAsJsonObject());
            }
        }
        return mp3.stream().min(Comparator
            .comparing((JsonObject item) -> bool(item, "preview"))
            .thenComparing(item -> number(item, "bitrateInKbps") <= 0)
            .thenComparingLong(item -> Math.abs(Math.clamp(number(item, "bitrateInKbps"), 0, 1000) - targetKbps))
            .thenComparingLong(item -> number(item, "bitrateInKbps")))
            .orElseThrow(() -> new IOException("Яндекс не предоставил MP3 для этого трека."));
    }

    InputStream openAudio(URI start) throws IOException {
        URI uri = start;
        for (int count = 0; count < 4; count++) {
            HttpURLConnection connection = (HttpURLConnection) cdn(uri).toURL().openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(12_000);
            try {
                int status = connection.getResponseCode();
                if (status == 200 || status == 206) {
                    return new java.io.FilterInputStream(connection.getInputStream()) {
                        @Override
                        public void close() throws IOException {
                            try {
                                super.close();
                            } finally {
                                connection.disconnect();
                            }
                        }
                    };
                }
                String target = connection.getHeaderField("Location");
                if (status < 300 || status >= 400 || target == null) throw new HttpError(status);
                uri = uri.resolve(target);
            } catch (IOException | RuntimeException error) {
                connection.disconnect();
                throw error;
            }
            connection.disconnect();
        }
        throw new IOException("Слишком много перенаправлений Яндекс Музыки.");
    }

    private JsonElement result(String path) throws Exception {
        return result(path, session);
    }

    String lyrics(String trackId) throws Exception {
        Session account = session;
        if (account == null) throw new IOException("Войди в Яндекс, чтобы открыть текст песни.");
        if (!trackId.matches("[0-9]{1,16}(:[0-9]{1,16})?")) return "";
        String id = trackId.split(":", 2)[0];
        for (String format : new String[]{"LRC", "TEXT"}) {
            try {
                String words = fetchLyrics(id, format, account);
                if (!words.isBlank()) return words;
            } catch (HttpError error) {
                if (error.status != 404 && error.status != 400) throw error;
            }
        }
        try {
            return string(object(result("/tracks/" + id + "/supplement", account), "lyrics"), "fullLyrics");
        } catch (HttpError error) {
            if (error.status != 404) throw error;
            return "";
        }
    }

    private String fetchLyrics(String id, String format, Session account) throws Exception {
        long timestamp = Instant.now().getEpochSecond();
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(LYRICS_CLIENT_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = Base64.getEncoder().encodeToString(
            mac.doFinal((id + timestamp).getBytes(StandardCharsets.UTF_8)));
        String path = "/tracks/" + id + "/lyrics?format=" + format + "&timeStamp=" + timestamp + "&sign=" + encode(signature);
        String location = string(objectResponse(result(path, account, LYRICS_CLIENT)), "downloadUrl");
        if (location.isBlank()) return "";
        var response = MusicHttp.send(http, HttpRequest.newBuilder(cdn(URI.create(location))).build(),
            MusicHttp.text(262_144), 12);
        if (response.statusCode() != 200) throw new HttpError(response.statusCode());
        return response.body();
    }

    private JsonElement result(String path, Session expected) throws Exception {
        return result(path, expected, null);
    }

    private JsonElement result(String path, Session expected, String client) throws Exception {
        Session current = expected == null ? null : currentSession(expected);
        if (current != null && current.token.expiresAt() - System.currentTimeMillis() < 120_000) {
            current = refresh(current);
        }
        try {
            return apiRequest(path, current == null ? null : current.token, client);
        } catch (HttpError error) {
            if (error.status != 401 || current == null) throw error;
            return apiRequest(path, refresh(current).token, client);
        }
    }

    private JsonElement apiRequest(String path, Token current, String client) throws Exception {
        if (current != null) BuildTrust.requireOfficial();
        HttpRequest.Builder request = request(API, path).header("Accept-Language", "ru");
        if (current != null) request.header("Authorization", "OAuth " + current.access());
        if (client != null) request.header("X-Yandex-Music-Client", client);
        JsonElement response = send(request.build());
        if (!response.isJsonObject() || !response.getAsJsonObject().has("result")) {
            throw new IOException("Ответ Яндекс Музыки изменился.");
        }
        return response.getAsJsonObject().get("result");
    }

    private JsonObject oauth(String path, String body) throws Exception {
        HttpRequest request = request(OAUTH, path)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        var response = MusicHttp.send(http, request, MusicHttp.text(65_536), 15);
        if (response.statusCode() != 200 && response.statusCode() != 400) throw new HttpError(response.statusCode());
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private JsonElement send(HttpRequest request) throws Exception {
        return send(request, 16 * 1024 * 1024);
    }

    private JsonElement send(HttpRequest request, int maxBytes) throws Exception {
        var response = MusicHttp.send(http, request, MusicHttp.text(maxBytes), 15);
        if (response.statusCode() != 200) throw new HttpError(response.statusCode());
        return JsonParser.parseString(response.body());
    }

    private static HttpRequest.Builder request(URI origin, String path) throws IOException {
        URI target = origin.resolve(path);
        if (!target.getScheme().equals(origin.getScheme()) || !origin.getRawAuthority().equals(target.getRawAuthority())
                || target.getRawFragment() != null) {
            throw new IOException("Неизвестный адрес Яндекса.");
        }
        return HttpRequest.newBuilder(target).timeout(Duration.ofSeconds(15));
    }

    private static URI cdn(URI uri) throws IOException {
        String host = uri.getHost();
        if (!"https".equals(uri.getScheme()) || uri.getUserInfo() != null || host == null
                || (uri.getPort() != -1 && uri.getPort() != 443)
                || !(host.endsWith(".yandex.net") || host.endsWith(".yandex.ru")
                    || host.endsWith(".yandex.com"))) {
            throw new IOException("Неизвестный адрес аудиосервера.");
        }
        return uri;
    }

    private static List<Track> tracks(JsonArray items) {
        List<Track> result = new ArrayList<>();
        for (JsonElement entry : items) {
            if (!entry.isJsonObject()) continue;
            JsonObject track = entry.getAsJsonObject();
            if (track.has("track")) track = object(track, "track");
            String title = string(track, "title");
            String id = string(track, "id");
            if (title.isBlank() || !id.matches("[0-9]+(?::[0-9]+)?") || (track.has("available") && !bool(track, "available"))) continue;
            List<String> names = new ArrayList<>();
            for (JsonElement artist : array(track, "artists")) {
                if (artist.isJsonObject()) names.add(string(artist.getAsJsonObject(), "name"));
            }
            String version = string(track, "version");
            if (!version.isBlank()) title += " · " + version;
            result.add(new Track(id, title, String.join(", ", names), number(track, "durationMs")));
        }
        return List.copyOf(result);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
    }

    private static long number(JsonObject object, String key) {
        String value = string(object, key);
        return value.matches("-?[0-9]{1,18}") ? Long.parseLong(value) : 0;
    }

    private static boolean bool(JsonObject object, String key) {
        return Boolean.parseBoolean(string(object, key));
    }

    private static JsonObject object(JsonElement value, String key) {
        if (value == null || !value.isJsonObject()) return new JsonObject();
        JsonElement child = value.getAsJsonObject().get(key);
        return child != null && child.isJsonObject() ? child.getAsJsonObject() : new JsonObject();
    }

    private static JsonObject objectResponse(JsonElement value) {
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
    }

    private static JsonArray array(JsonObject value, String key) {
        return asArray(value.get(key));
    }

    private static JsonArray asArray(JsonElement value) {
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }
}
