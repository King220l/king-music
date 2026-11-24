package studio.king220l.yamusic;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

final class BuildTrust {
    private static final URI MANIFEST = URI.create(
        "https://raw.githubusercontent.com/King220l/king-music/port/fabric-26.1.2/official/26.1.2/manifest.txt");
    private static final URI SIGNATURE = URI.create(
        "https://raw.githubusercontent.com/King220l/king-music/port/fabric-26.1.2/official/26.1.2/manifest.sig");
    private static final String PUBLIC_KEY =
        "MCowBQYDK2VwAyEAP1YflYO2tF+rZJtjQHPfcBDMbM6SK2yj1jYAUMsHfJc=";
    private static final List<String> MODS = List.of(
        "yamusic", "king_music_together", "king_music_lyrics", "king_music_visualizer");
    private static final int LIMIT = 65_536;
    private static final CountDownLatch READY = new CountDownLatch(1);
    private static volatile State state = State.CHECKING;
    private static volatile boolean updateAvailable;
    private static volatile String latestVersion = "";
    private static Path cacheManifest;
    private static Path cacheSignature;

    enum State { CHECKING, VERIFIED, CACHED, MODIFIED, UNAVAILABLE, DEVELOPMENT }

    private BuildTrust() {}

    static void start(Path config) {
        cacheManifest = config.resolve("official-manifest.txt");
        cacheSignature = config.resolve("official-manifest.sig");
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
            finish(State.DEVELOPMENT);
            return;
        }
        Thread.ofVirtual().name("kingmusic-authenticity").start(BuildTrust::check);
    }

    static void requireOfficial() throws IOException {
        try {
            READY.await(8, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Проверка сборки прервана.");
        }
        if (state == State.VERIFIED || state == State.CACHED || state == State.DEVELOPMENT) return;
        throw new IOException(state == State.MODIFIED
            ? "Сборка изменена и не подписана King220l. Вход в Яндекс заблокирован."
            : "Не удалось проверить официальную сборку. Подключись к интернету и повтори.");
    }

    static String message() {
        return switch (state) {
            case CHECKING -> "Подлинность: проверяем через GitHub…";
            case VERIFIED -> "Подлинность: официальная сборка King220l";
            case CACHED -> "Подлинность: подтверждена сохранённой подписью";
            case MODIFIED -> "Подлинность: изменённая или неофициальная сборка";
            case UNAVAILABLE -> "Подлинность: GitHub пока недоступен";
            case DEVELOPMENT -> "Подлинность: режим разработки";
        };
    }

    static boolean modified() { return state == State.MODIFIED; }
    static boolean updateAvailable() { return updateAvailable; }
    static int crownColor() { return modified() ? MusicTheme.ROSE : MusicTheme.KING_GOLD; }

    static String crownMessage() {
        if (modified()) return "Предупреждение: файл изменён или не совпадает с официальными релизами King220l. Скачивай King Music только с GitHub автора, Modrinth или CurseForge.";
        if (updateAvailable) return "Официальный King Music от King220l · доступно обновление " + latestVersion;
        return switch (state) {
            case VERIFIED, CACHED -> "Оригинальный King Music от King220l · цифровая подпись подтверждена";
            case CHECKING -> "Проверяем оригинальность King Music…";
            case UNAVAILABLE -> "Не удалось проверить обновления: GitHub недоступен";
            case DEVELOPMENT -> "King Music от King220l · режим разработки";
            case MODIFIED -> "";
        };
    }

    private static void check() {
        try {
            byte[] manifest = download(MANIFEST);
            byte[] signature = download(SIGNATURE);
            if (!verifySignature(manifest, signature)) {
                finish(State.MODIFIED);
                return;
            }
            latestVersion = coreVersion(manifest);
            updateAvailable = newer(latestVersion, installedVersion());
            if (!matchesInstalled(manifest)) {
                if (cachedMatchesInstalled()) finish(State.CACHED);
                else finish(State.MODIFIED);
                return;
            }
            saveCache(manifest, signature);
            finish(State.VERIFIED);
        } catch (Exception networkFailure) {
            try {
                byte[] manifest = Files.readAllBytes(cacheManifest);
                byte[] signature = Files.readAllBytes(cacheSignature);
                finish(verifySignature(manifest, signature) && matchesInstalled(manifest)
                    ? State.CACHED : State.UNAVAILABLE);
            } catch (Exception missingCache) {
                finish(State.UNAVAILABLE);
            }
        }
    }

    private static boolean cachedMatchesInstalled() {
        try {
            byte[] manifest = Files.readAllBytes(cacheManifest);
            byte[] signature = Files.readAllBytes(cacheSignature);
            return verifySignature(manifest, signature) && matchesInstalled(manifest);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String coreVersion(byte[] manifest) throws IOException {
        String latest = "";
        for (String filename : parse(manifest).keySet()) {
            var match = java.util.regex.Pattern.compile("King-Music-([0-9]+(?:\\.[0-9]+)*)-Fabric-26\\.2\\.jar").matcher(filename);
            if (match.matches() && (latest.isEmpty() || newer(match.group(1), latest))) {
                latest = match.group(1);
            }
        }
        return latest;
    }

    private static String installedVersion() {
        return FabricLoader.getInstance().getModContainer("yamusic")
            .map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("0");
    }

    private static boolean newer(String candidate, String current) {
        if (candidate == null || candidate.isBlank()) return false;
        String[] left = candidate.split("\\."), right = current.split("\\.");
        for (int i = 0; i < Math.max(left.length, right.length); i++) {
            int a = i < left.length && left[i].matches("\\d+") ? Integer.parseInt(left[i]) : 0;
            int b = i < right.length && right[i].matches("\\d+") ? Integer.parseInt(right[i]) : 0;
            if (a != b) return a > b;
        }
        return false;
    }

    private static byte[] download(URI uri) throws Exception {
        var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(6))
            .header("Accept", "text/plain").GET().build();
        byte[] value = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6))
            .followRedirects(HttpClient.Redirect.NEVER).build()
            .send(request, HttpResponse.BodyHandlers.ofByteArray()).body();
        if (value.length == 0 || value.length > LIMIT) throw new IOException("Invalid manifest response");
        return value;
    }

    private static boolean verifySignature(byte[] manifest, byte[] encodedSignature) throws Exception {
        var publicKey = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(
            Base64.getDecoder().decode(PUBLIC_KEY)));
        var verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(publicKey);
        verifier.update(manifest);
        return verifier.verify(Base64.getDecoder().decode(new String(encodedSignature, StandardCharsets.US_ASCII).strip()));
    }

    private static boolean matchesInstalled(byte[] manifest) throws Exception {
        Map<String, String> expected = parse(manifest);
        boolean core = false;
        for (String id : MODS) {
            var container = FabricLoader.getInstance().getModContainer(id);
            if (container.isEmpty()) continue;
            Path path = container.get().getOrigin().getPaths().getFirst();
            if (!Files.isRegularFile(path)) return false;
            String wanted = expected.get(path.getFileName().toString());
            if (wanted == null || !MessageDigest.isEqual(
                wanted.getBytes(StandardCharsets.US_ASCII), sha256(path).getBytes(StandardCharsets.US_ASCII))) return false;
            if (id.equals("yamusic")) core = true;
        }
        return core;
    }

    private static Map<String, String> parse(byte[] bytes) throws IOException {
        Map<String, String> values = new HashMap<>();
        for (String line : new String(bytes, StandardCharsets.UTF_8).split("\\R")) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] parts = line.strip().split("  ", 2);
            if (parts.length != 2 || !parts[0].matches("[0-9a-f]{64}")
                || !parts[1].matches("King-Music[-A-Za-z0-9.]+\\.jar")) throw new IOException("Invalid manifest");
            values.put(parts[1], parts[0]);
        }
        return values;
    }

    private static String sha256(Path file) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            for (int read; (read = input.read(buffer)) >= 0;) digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void saveCache(byte[] manifest, byte[] signature) throws IOException {
        Files.createDirectories(cacheManifest.getParent());
        Path manifestTemp = cacheManifest.resolveSibling(cacheManifest.getFileName() + ".tmp");
        Path signatureTemp = cacheSignature.resolveSibling(cacheSignature.getFileName() + ".tmp");
        Files.write(manifestTemp, manifest);
        Files.write(signatureTemp, signature);
        Files.move(manifestTemp, cacheManifest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        Files.move(signatureTemp, cacheSignature, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static void finish(State value) {
        state = value;
        READY.countDown();
    }
}
