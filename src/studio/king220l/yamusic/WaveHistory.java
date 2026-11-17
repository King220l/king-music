package studio.king220l.yamusic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

record WaveHistory(String account, List<String> recent) {
    private static final int LIMIT = 32;
    static final WaveHistory EMPTY = new WaveHistory("", List.of());

    WaveHistory {
        account = account == null ? "" : account;
        recent = recent == null ? List.of() : recent.stream().filter(id -> id != null && id.matches("[0-9]{1,24}"))
            .distinct().limit(LIMIT).toList();
    }

    String latest() { return recent.isEmpty() ? "" : recent.getFirst(); }

    WaveHistory heard(String trackId) {
        String id = trackKey(trackId);
        if (!id.matches("[0-9]{1,24}") || id.equals(latest())) return this;
        var updated = new ArrayList<String>(recent.size() + 1);
        updated.add(id);
        for (String item : recent) if (!item.equals(id)) updated.add(item);
        return new WaveHistory(account, updated);
    }

    boolean hasNew(List<MusicApi.Track> tracks, String previous) {
        for (var track : tracks) {
            String id = trackKey(track.id());
            if (!id.equals(previous) && !recent.contains(id)) return true;
        }
        return false;
    }

    List<MusicApi.Track> choose(List<MusicApi.Track> tracks, String previous) {
        var distinct = new LinkedHashMap<String, MusicApi.Track>();
        for (var track : tracks) {
            String id = trackKey(track.id());
            if (!id.equals(previous)) distinct.putIfAbsent(id, track);
        }
        var fresh = new ArrayList<MusicApi.Track>();
        distinct.forEach((id, track) -> { if (!recent.contains(id)) fresh.add(track); });
        if (!fresh.isEmpty()) return List.copyOf(fresh);
        // Do not feed already-heard recommendations back to the player.
        // The API should ask for another page instead of replaying stale tracks.
        return List.of();
    }

    static String trackKey(String value) {
        int separator = value.indexOf(':');
        return separator < 0 ? value : value.substring(0, separator);
    }
}
