package studio.king220l.yamusic;

import java.util.*;
import java.util.stream.*;
import studio.king220l.yamusic.MusicApi.Track;

/** Автономные тесты (без JUnit): java ... WaveHistoryTest ; код выхода != 0 при провале. */
public class WaveHistoryTest {
    static int passed, failed;
    static void check(String name, boolean ok) {
        if (ok) { passed++; System.out.println("ok    " + name); } else { failed++; System.out.println("FAIL  " + name); }
    }
    static List<Track> t(String... ids) { return Arrays.stream(ids).map(Track::new).toList(); }
    static List<String> ids(List<Track> l) { return l.stream().map(Track::id).toList(); }
    static WaveHistory hist(String... newestFirst) { return new WaveHistory("acc", List.of(newestFirst)); }

    public static void main(String[] a) throws Exception {
        // --- повторы ---
        check("новые идут раньше повторов",
            ids(hist("1","2").choose(t("1","5","2","6"), "9")).equals(List.of("5","6","2","1")));
        check("текущий трек никогда не возвращается",
            !ids(hist().choose(t("9","5"), "9")).contains("9"));
        check("текущий с суффиксом альбома тоже исключается",
            ids(hist().choose(t("9:77","5:1"), "9")).equals(List.of("5:1")));
        check("если новых нет — отдаём повторы, давно слушанные первыми",
            ids(hist("1","2","3").choose(t("1","2","3"), "9")).equals(List.of("3","2","1")));
        check("дубликаты в ответе API схлопываются",
            ids(hist().choose(t("5","5:2","6"), "9")).equals(List.of("5","6")));
        check("единственный трек == текущий → пусто (реальный пустой выбор)",
            hist("1").choose(t("9"), "9").isEmpty());
        // --- пустые ответы API ---
        check("пустой ответ API → пустой список без исключения",
            hist("1").pick(from -> List.of(), "9", "").isEmpty());
        List<String> calls = new ArrayList<>();
        List<Track> r = hist().pick(from -> { calls.add(from); return from.equals("7") ? List.of() : t("5","6"); }, "9", "7");
        check("пустая страница по from → fallback на from=\"\"", ids(r).equals(List.of("5","6")) && calls.equals(List.of("7","")));
        // --- все предложенные уже слушали: раньше это была ошибка ---
        WaveHistory full = new WaveHistory("acc", IntStream.rangeClosed(1,32).mapToObj(String::valueOf).toList());
        List<Track> pr = full.pick(from -> t("1","2","3"), "9", "");
        check("все 32 в истории, API даёт только слушанные → повторы, не ошибка", !pr.isEmpty() && ids(pr).equals(List.of("3","2","1")));
        // --- дозапрос страниц ---
        Map<String,List<Track>> pages = Map.of("", t("1","2"), "2", t("2","3"), "3", t("4"));
        List<Track> pg = hist("1","2","3").pick(from -> pages.getOrDefault(from, List.of()), "9", "");
        check("при отсутствии новых дозапрашиваются страницы и находится новый", ids(pg).get(0).equals("4"));
        // --- история ---
        check("heard: самый свежий первым, без дублей, лимит 32",
            new WaveHistory("a", List.of()).heard("1").heard("2").heard("1").recent().equals(List.of("1","2")));
        WaveHistory h = WaveHistory.EMPTY;
        for (int i = 1; i <= 100; i++) h = h.heard(String.valueOf(i));
        check("лимит истории 32", h.recent().size() == 32 && h.latest().equals("100"));
        check("нечисловые id игнорируются", WaveHistory.EMPTY.heard("abc").recent().isEmpty() && WaveHistory.EMPTY.heard(null).recent().isEmpty());
        // --- быстрое переключение: 1000 скипов, маленькая лента (меньше лимита истории) ---
        Random rnd = new Random(42);
        List<Track> feed = t("1","2","3","4","5","6","7","8");
        WaveHistory hs = WaveHistory.EMPTY; String cur = "1"; boolean sameTrack = false, empty = false; Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            hs = hs.heard(cur);
            List<Track> shuffled = new ArrayList<>(feed); Collections.shuffle(shuffled, rnd);
            final String c = cur;
            List<Track> out = hs.pick(from -> shuffled, c, "");
            if (out.isEmpty()) { empty = true; break; }
            String nxt = out.get(0).id();
            if (nxt.equals(cur)) { sameTrack = true; break; }
            seen.add(nxt); cur = nxt;
        }
        check("1000 быстрых переключений: ни разу не пусто", !empty);
        check("1000 быстрых переключений: ни разу тот же трек подряд", !sameTrack);
        check("в ротации участвуют все треки ленты", seen.size() >= 7);
        // --- быстрое переключение при ленте из 2 треков ---
        List<Track> two = t("1","2"); WaveHistory h2 = WaveHistory.EMPTY; String c2 = "1"; boolean bad = false;
        for (int i = 0; i < 200; i++) { h2 = h2.heard(c2); final String cc = c2; List<Track> o = h2.pick(f -> two, cc, ""); if (o.isEmpty() || o.get(0).id().equals(cc)) { bad = true; break; } c2 = o.get(0).id(); }
        check("лента из 2 треков: чередуются без ошибок и без повтора подряд", !bad);
        // --- лента из одного (текущего) трека: единственная честная ошибка ---
        check("лента только из текущего трека → пусто (вызывающий код покажет понятную ошибку)",
            hist("9").pick(f -> t("9"), "9", "").isEmpty());

        System.out.println("\nПройдено: " + passed + ", провалено: " + failed);
        System.exit(failed == 0 ? 0 : 1);
    }
}
