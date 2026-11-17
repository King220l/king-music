package studio.king220l.yamusic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

final class TogetherSettings {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    boolean showBadges = true;
    boolean betaLink;
    String betaClient;
    private transient Path path;

    static TogetherSettings load(Path path) {
        var config = new TogetherSettings();
        if (Files.isRegularFile(path)) {
            try {
                TogetherSettings saved = GSON.fromJson(Files.readString(path), TogetherSettings.class);
                if (saved != null) config = saved;
            } catch (IOException | RuntimeException error) {
                MusicMod.LOG.warn("Could not read King Music Together settings", error);
            }
        }
        config.path = path;
        if (config.betaClient == null || !config.betaClient.matches("[a-f0-9]{32}")) {
            config.betaClient = UUID.randomUUID().toString().replace("-", "");
            if (config.betaLink) config.save();
        }
        return config;
    }

    void save() {
        try {
            Files.createDirectories(path.getParent());
            Path temp = path.resolveSibling("together.tmp");
            Files.writeString(temp, GSON.toJson(this));
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException error) {
            MusicMod.LOG.warn("Could not save King Music Together settings", error);
        }
    }
}
