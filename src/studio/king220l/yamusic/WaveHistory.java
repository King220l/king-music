package studio.king220l.yamusic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;


/**
 * История «Моей волны» по аккаунту.
 * Wave hotfix: фильтрация стала мягкой. Порядок выбора: новые треки -> допустимые повторы
 * (давно слушанные первыми) -> пусто только если API не вернул ничего, кроме текущего трека.
 * Текущий трек никогда не попадает в результат.
 * recent хранит самые свежие треки первыми (как и в 0.5.16, формат в настройках не менялся).
 */
record WaveHistory(String account, List<String> recent) {
    private static final int LIMIT = 32;
    /** Сколько дополнительных страниц запросить в поисках новых треков. */
    static final int MAX_EXTRA_PAGES = 3;
    static final WaveHistory EMPTY = new WaveHistory("", List.of());

    WaveHistory {
        account = account == null ? "" : account;
        recent = recent == null ? List.of()
                : recent.stream().filter(s -> s != null && s.matches("[0-9]{1,24}")).distinct().limit(LIMIT).toList();
    }

    String latest() {
        return recent.isEmpty() ? "" : recent.getFirst();
    }

    WaveHistory heard(String trackId) {
        String key = trackKey(trackId);
        if (!key.matches("[0-9]{1,24}") || key.equals(latest())) return this;
        List<String> next = new ArrayList<>(recent.size() + 1);
        next.add(key);
        for (String s : recent) if (!s.equals(key)) next.add(s);
        return new WaveHistory(account, next);
    }

    /** true, если среди треков есть хотя бы один не текущий и не слушанный недавно. */
    boolean hasNew(List<MusicApi.Track> tracks, String current) {
        String cur = trackKey(current);
        for (MusicApi.Track t : tracks) {
            String k = trackKey(t.id());
            if (!k.equals(cur) && !recent.contains(k)) return true;
        }
        return false;
    }

    /** Новые первыми, затем повторы (давно слушанные раньше). Текущий исключён. Пусто только если других нет. */
    List<MusicApi.Track> choose(List<MusicApi.Track> tracks, String current) {
        String cur = trackKey(current);
        LinkedHashMap<String, MusicApi.Track> unique = new LinkedHashMap<>();
        for (MusicApi.Track t : tracks) {
            String k = trackKey(t.id());
            if (!k.equals(cur)) unique.putIfAbsent(k, t);
        }
        List<MusicApi.Track> fresh = new ArrayList<>();
        List<MusicApi.Track> repeats = new ArrayList<>();
        unique.forEach((k, t) -> { if (recent.contains(k)) repeats.add(t); else fresh.add(t); });
        // recent: индекс 0 = самый недавний; повторы с БОЛЬШИМ индексом (слушали давно) идут первыми
        repeats.sort((a, b) -> Integer.compare(recent.indexOf(trackKey(b.id())), recent.indexOf(trackKey(a.id()))));
        List<MusicApi.Track> out = new ArrayList<>(fresh.size() + repeats.size());
        out.addAll(fresh);
        out.addAll(repeats);
        return List.copyOf(out);
    }

    /**
     * Весь цикл подбора: страница от {@code from}, при отсутствии новых — до MAX_EXTRA_PAGES страниц
     * «от последнего трека», затем один перезапуск станции (from = ""). Никогда не бросает исключение
     * из-за нехватки новых; пустой результат означает, что API реально ничего не дал.
     */
    @FunctionalInterface
    interface Fetch { List<MusicApi.Track> get(String cursor) throws Exception; }

    List<MusicApi.Track> pick(Fetch fetch, String current, String from) throws Exception {
        String cur = trackKey(current);
        List<MusicApi.Track> pool = new ArrayList<>();
        String cursor = from == null ? "" : from;
        java.util.Set<String> usedCursors = new java.util.HashSet<>();
        java.util.Set<String> pageSignatures = new java.util.HashSet<>();
        java.util.Set<String> unique = new java.util.HashSet<>();
        // Fetch additional pages even if the first page has fresh songs: otherwise
        // a small station page (typically 5-6 tracks) repeats on every continuation.
        for (int i = 0; i <= MAX_EXTRA_PAGES; i++) {
            if (!usedCursors.add(cursor)) break;
            List<MusicApi.Track> page = fetch.get(cursor);
            if (page.isEmpty()) break;
            String signature = page.stream().map(t -> trackKey(t.id())).distinct()
                .collect(java.util.stream.Collectors.joining(","));
            if (!pageSignatures.add(signature)) break;
            int added = 0;
            for (MusicApi.Track track : page) {
                if (unique.add(trackKey(track.id()))) { pool.add(track); added++; }
            }
            if (added == 0) break;
            String last = trackKey(page.getLast().id());
            if (last.isEmpty() || last.equals(cur) || usedCursors.contains(last)) break;
            cursor = last;
        }
        // A restart is only useful if there were no usable candidates at all.
        if (choose(pool, cur).isEmpty() && usedCursors.add("")) {
            pool.addAll(fetch.get(""));
        }
        return choose(pool, cur);
    }

    static String trackKey(String id) {
        if (id == null) return "";
        int i = id.indexOf(':');
        return i < 0 ? id : id.substring(0, i);
    }
}
