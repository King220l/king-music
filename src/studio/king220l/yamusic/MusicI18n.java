package studio.king220l.yamusic;

import net.minecraft.network.chat.Component;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

public final class MusicI18n {
    private static volatile String language = "en_us";
    private static final Map<String, String> EN = new LinkedHashMap<>();
    private static final Map<String, Pack> PACKS = new LinkedHashMap<>();
    private record Pack(String code, String name, Map<String, String> values) {}

    static {
        add("Главная", "Home"); add("Поиск", "Search"); add("Библиотека", "Library");
        add("Моя папка", "My folder"); add("Моя волна", "My Wave"); add("Любимое", "Liked");
        add("Мне нравится", "Liked"); add("Плейлисты", "Playlists"); add("Альбомы", "Albums");
        add("Исполнители", "Artists"); add("Недавние", "Recent"); add("Недавние треки", "Recently played");
        add("Текст песни", "Lyrics"); add("Визуализация", "Visualizer"); add("Визуализатор", "Visualizer");
        add("Плеер", "Player"); add("HUD и меню", "HUD & menu"); add("Клавиши", "Shortcuts"); add("Текст", "Lyrics");
        add("Настройки", "Settings"); add("Аккаунты", "Accounts"); add("Корона", "Crown");
        add("Закрыть плеер", "Close player"); add("Назад", "Back"); add("Готово", "Done");
        add("Размер интерфейса", "Interface scale"); add("Громкость", "Volume"); add("Отключить звук", "Mute");
        add("Качество музыки", "Music quality"); add("Качество Яндекс Музыки", "Yandex Music quality");
        add("Автопереход", "Autoplay"); add("Повтор", "Repeat"); add("Звуки кнопок", "Button sounds");
        add("Показ HUD", "HUD display"); add("Размер HUD", "HUD scale"); add("Размер текста", "Text scale");
        add("Непрозрачность", "Opacity"); add("Время показа", "Display time"); add("Исполнитель", "Artist");
        add("Мини-плеер в паузе", "Pause-menu mini player"); add("Расположение HUD", "HUD position");
        add("Переместить", "Move"); add("Переместить HUD", "Move HUD"); add("Не показывать", "Never");
        add("При смене трека", "On track change"); add("Всегда", "Always"); add("Скрыть", "Hide");
        add("При смене", "On change"); add("Выкл.", "Off"); add("Трек", "Track"); add("Список", "List");
        add("Выключен", "Off"); add("Текущий трек", "Current track"); add("Весь список", "Whole list");
        add("Авто", "Auto"); add("Превосходное", "Excellent"); add("Оптимальное", "Optimal"); add("Экономичное", "Data saver");
        add("Английский", "English"); add("Русский", "Russian"); add("Язык", "Language");
        add("Открыть плеер", "Open player"); add("Воспроизвести / пауза", "Play / pause");
        add("Предыдущий трек", "Previous track"); add("Следующий трек", "Next track");
        add("Показать / скрыть HUD", "Show / hide HUD"); add("Громче", "Volume up"); add("Тише", "Volume down");
        add("Включить / выключить звук", "Mute / unmute"); add("Показать / скрыть HUD текста", "Show / hide lyrics HUD");
        add("Горячие клавиши редактора", "Editor shortcuts"); add("Пауза / продолжить", "Pause / resume");
        add("Не назначено", "Unassigned"); add("Запись…", "Listening…"); add("Вернуть сочетания по умолчанию", "Restore default shortcuts");
        add("Включить", "Enable"); add("Выключить", "Disable"); add("Перемотка", "Seek");
        add("Добавить в любимое", "Add to Liked"); add("Убрать из любимого", "Remove from Liked");
        add("Трек или исполнитель", "Track or artist"); add("Найти", "Search"); add("Трек не выбран", "No track selected");
        add("Войти", "Sign in"); add("Выйти", "Sign out"); add("Войти в Яндекс", "Sign in to Yandex");
        add("Токен Яндекс Музыки", "Yandex Music token"); add("Показать токен", "Show token"); add("Скрыть токен", "Hide token");
        add("Проверить токен и сохранить его через защиту Windows", "Verify the token and protect it with Windows encryption");
        add("Дополнительный вход. Используй его только в официальной сборке King220l", "Alternative sign-in. Use it only in an official King220l build");
        add("Вместе", "Together"); add("Комната прослушивания", "Listening room"); add("Код комнаты", "Room code");
        add("Создать", "Create"); add("Скопировать код", "Copy code"); add("Передать роль", "Transfer host");
        add("Значки игроков в TAB", "Player badges in TAB"); add("Связь без серверного мода · Бета", "Connection without server mod · Beta");
        add("Через Minecraft", "Via Minecraft"); add("Связь · Бета", "Connection · Beta"); add("Связь выключена", "Connection disabled");
        add("Редактор текста", "Lyrics editor"); add("Сохранить .lrc", "Save .lrc"); add("Расставить метки", "Set timestamps");
        add("Отметить строку", "Mark line"); add("К тексту", "Back to text"); add("Отменить", "Undo"); add("Скорость", "Speed");
        add("Синхронный текст", "Synced lyrics"); add("Текст без таймингов", "Unsynced lyrics"); add("Прокрутка", "Scrolling");
        add("Текст поверх игры", "Lyrics over the game"); add("Положение HUD текста", "Lyrics HUD position");
        add("Оформление HUD", "HUD style"); add("Уже спетая строка", "Previous line"); add("Следующие строки", "Upcoming lines");
        add("Высота строки", "Line height"); add("Строк вперёд", "Upcoming lines"); add("Выравнивание", "Alignment");
        add("Слева", "Left"); add("По центру", "Center"); add("Справа", "Right"); add("Текущая строка", "Current line");
        add("Золото", "Gold"); add("Подчеркнуть", "Underline"); add("Карточка", "Card"); add("Только текст", "Text only");
        add("Золотой контур", "Gold outline"); add("Спектр", "Spectrum"); add("Волна", "Wave"); add("Кольцо", "Ring");
        add("Пульс", "Pulse"); add("Стерео", "Stereo"); add("Включи трек", "Play a track"); add("График звука", "Audio visualization");
        add("Выбери трек, чтобы открыть текст.", "Choose a track to view its lyrics.");
        add("Для этой записи текст отсутствует.", "No lyrics are available for this track.");
        add("Загружаем текст…", "Loading lyrics…"); add("У этого трека нет текста.", "This track has no lyrics.");
        add("Вставь токен Яндекс Музыки.", "Paste your Yandex Music token."); add("Проверяем токен…", "Verifying token…");
        add("Запрашиваем код…", "Requesting code…"); add("Подключение…", "Connecting…");
        add("Сочетания восстановлены.", "Shortcuts restored.");
        add("В альбоме нет доступных треков.", "This album has no available tracks.");
        add("В плейлисте нет доступных треков.", "This playlist has no available tracks.");
        add("У исполнителя нет доступных треков.", "This artist has no available tracks.");
        add("В комнате доступны только треки Яндекс Музыки.", "Only Yandex Music tracks are available in rooms.");
        add("В комнате используется обычная скорость.", "Rooms use normal playback speed.");
        add("В папке пока нет MP3, WAV, AIFF или AU.", "There are no MP3, WAV, AIFF, or AU files in this folder.");
        add("В твоём аккаунте доступен только фрагмент этого трека.", "Only a preview of this track is available to your account.");
        add("В твоём аккаунте играет фрагмент.", "A preview is playing for your account.");
        add("Введи или вставь токен", "Type or paste a token"); add("Введи код комнаты из десяти символов.", "Enter the ten-character room code.");
        add("Введи код на странице Яндекса.", "Enter the code on the Yandex page.");
        add("Введи название трека или исполнителя.", "Enter a track or artist name."); add("Ведущий · ", "Host · ");
        add("Включено", "Enabled"); add("Выключено", "Disabled");
        add("Включи бета-связь или зайди на сервер с Together Server.", "Enable beta connection or join a server with Together Server.");
        add("Включи связь · Бета или зайди на сервер с Together Server.", "Enable Connection · Beta or join a server with Together Server.");
        add("Войди в Яндекс, чтобы открыть свою фонотеку.", "Sign in to Yandex to open your library.");
        add("Войди в Яндекс, чтобы открыть текст песни.", "Sign in to Yandex to open lyrics.");
        add("Войди, чтобы открыть плейлисты и Мою волну.", "Sign in to open playlists and My Wave.");
        add("Воспроизведение синхронизировано.", "Playback is synchronized.");
        add("Воспроизвести / приостановить предпросмотр", "Play / pause preview"); add("Вперёд на 0,25 с", "Forward 0.25 s");
        add("Назад на 0,25 с", "Back 0.25 s"); add("+0,25 с", "+0.25 s"); add("−0,25 с", "−0.25 s");
        add("Вставь или напиши текст, по одной строке на абзац", "Paste or type lyrics, one line per paragraph");
        add("Вход выполнен на этот запуск.", "Signed in for this session.");
        add("Вход не подтверждён. Запроси новый код.", "Sign-in was not confirmed. Request a new code.");
        add("Вход по коду", "Sign in with code");
        add("Вход по коду используй только в официальной сборке King220l.", "Use code sign-in only in an official King220l build.");
        add("Выбери или перетащи папку с музыкой.", "Choose or drop a music folder.");
        add("Выбери строку и передвинь стрелку над полосой времени", "Select a line and move the marker above the timeline");
        add("Выбрать папку", "Choose folder"); add("Выбрать папку с музыкой", "Choose music folder");
        add("Выбрать предыдущую строку", "Select previous line"); add("Выбрать следующую строку", "Select next line");
        add("Выключить / включить звук", "Mute / unmute"); add("Диалог недоступен. Перетащи папку в окно игры.", "The dialog is unavailable. Drop the folder onto the game window.");
        add("Для комнат нужен свой аккаунт Яндекса.", "Rooms require your own Yandex account.");
        add("Для комнаты войди в свой аккаунт Яндекса.", "Sign in to your Yandex account to use rooms.");
        add("Для левой и правой кнопок добавь Ctrl, Shift или Alt.", "Add Ctrl, Shift, or Alt to use the left or right mouse button.");
        add("Для сохранения добавь хотя бы одну строку.", "Add at least one line before saving.");
        add("Для этого трека тебе доступен только фрагмент.", "Only a preview of this track is available to you.");
        add("Добавь строки в редакторе.", "Add lines in the editor.");
        add("Добавь текст в редакторе или положи .lrc рядом с треком.", "Add lyrics in the editor or place an .lrc file beside the track.");
        add("Ждём ведущего. Через 2 минуты роль перейдёт участнику.", "Waiting for the host. The role will transfer after two minutes.");
        add("Ждём трек ведущего из Яндекс Музыки.", "Waiting for the host's Yandex Music track.");
        add("Загружаем музыку…", "Loading music…"); add("Загрузка…", "Loading…"); add("Закрыть недавние", "Close recent tracks");
        add("Запись отменена.", "Binding cancelled."); add("Запусти этот трек сам, чтобы поставить метки.", "Play this track yourself to set timestamps.");
        add("Здесь появятся", "This is where"); add("Здесь появятся треки, отмеченные сердцем.", "Tracks marked with a heart will appear here.");
        add("Значки других игроков в TAB", "Other players' badges in TAB"); add("Игрок", "Player");
        add("Игрок уже вышел из комнаты.", "The player has already left the room."); add("Код скопирован.", "Code copied.");
        add("Использовать связь · Бета, если нет Together Server", "Use Connection · Beta when Together Server is unavailable");
        add("Комната закрылась.", "The room was closed."); add("Комната заполнена.", "The room is full.");
        add("Комната не найдена или ведущий вышел.", "Room not found or the host has left."); add("Кому передать роль", "Transfer host to");
        add("Копировать код", "Copy code"); add("Локальная музыка", "Local music"); add("Локальный файл", "Local file");
        add("Метка выбранной строки", "Selected line timestamp");
        add("Метка выбранной строки · Shift при перетаскивании или стрелки для точной настройки", "Selected line timestamp · Hold Shift while dragging or use arrow keys for fine adjustment");
        add("Можно сохранить текст и без меток.", "Lyrics can be saved without timestamps."); add("Музыка под твоё настроение", "Music for your mood");
        add("На паузе", "Paused"); add("Нажми клавишу или кнопку мыши", "Press a key or mouse button");
        add("Нажми клавишу или кнопку мыши. Esc — отмена.", "Press a key or mouse button. Esc — cancel.");
        add("Назад к коллекции", "Back to collection"); add("Название трека", "Track title"); add("Назначение снято.", "Binding cleared.");
        add("Настройки текста", "Lyrics settings"); add("Не удалось выполнить действие. Попробуй ещё раз.", "The action failed. Try again.");
        add("Не удалось записать .lrc рядом с песней.", "Could not save the .lrc beside the song.");
        add("Не удалось запустить звук. Проверь устройство вывода.", "Could not start audio. Check your output device.");
        add("Не удалось защитить токен в Windows.", "Could not protect the token with Windows encryption.");
        add("Не удалось получить аудио.", "Could not load audio.");
        add("Не удалось проверить официальную сборку. Подключись к интернету и повтори.", "Could not verify the official build. Connect to the internet and try again.");
        add("Не удалось прочитать аудиофайл.", "Could not read the audio file."); add("Не удалось прочитать текст рядом с треком.", "Could not read the lyrics beside the track.");
        add("Не удалось связаться с Яндекс Музыкой. Попробуй ещё раз.", "Could not contact Yandex Music. Try again.");
        add("Не удалось удалить сохранённый сеанс.", "Could not remove the saved session.");
        add("Неизвестный адрес аудиосервера.", "Unknown audio server address."); add("Неизвестный адрес Яндекса.", "Unknown Yandex address.");
        add("Обновить коллекцию", "Refresh collection"); add("Обновить папку", "Refresh folder");
        add("Ответ сервера Minecraft не прочитан.", "The Minecraft server response could not be read."); add("Ответ Яндекс Музыки изменился.", "The Yandex Music response format has changed.");
        add("Открываем трек ведущего…", "Opening the host's track…"); add("Открываем файл…", "Opening file…"); add("Открыть", "Open");
        add("Открыть визуализацию", "Open visualizer"); add("Открыть Яндекс", "Open Yandex"); add("Отменить вход", "Cancel sign-in");
        add("Отменить последнюю метку", "Undo last timestamp"); add("Отметить время строки", "Mark line timestamp"); add("Оформление текста", "Lyrics appearance");
        add("Очистить поиск", "Clear search"); add("Ошибка защищённой связи.", "Secure connection error.");
        add("Папка с музыкой недоступна.", "The music folder is unavailable."); add("Пауза", "Pause"); add("Пауза / воспроизведение", "Pause / play");
        add("Передать", "Transfer"); add("Передать роль ведущего", "Transfer host role"); add("Перемешать список", "Shuffle list");
        add("Перетащи панель за заголовок", "Drag the panel by its title"); add("Перетащи папку с музыкой.", "Drop a music folder.");
        add("Плейлистов пока нет.", "There are no playlists yet."); add("По запросу ничего не найдено.", "No results found.");
        add("По этому запросу ничего не найдено.", "No results found for this search."); add("Подборки для игры", "Made for gaming");
        add("Подключён", "Connected"); add("Подлинность: изменённая или неофициальная сборка", "Authenticity: modified or unofficial build");
        add("Подлинность: официальная сборка King220l", "Authenticity: official King220l build");
        add("Подлинность: подтверждена сохранённой подписью", "Authenticity: verified using cached signature");
        add("Подлинность: проверяем через GitHub…", "Authenticity: checking with GitHub…"); add("Подлинность: режим разработки", "Authenticity: development mode");
        add("Подлинность: GitHub пока недоступен", "Authenticity: GitHub is currently unavailable"); add("Позже на 25 мс", "25 ms later");
        add("Поиск по папке", "Search folder"); add("Показать или скрыть текст песни", "Show or hide lyrics"); add("Показать текст песни", "Show lyrics");
        add("Показывать вкладку визуализации", "Show visualizer tab"); add("Положение HUD трека", "Track HUD position");
        add("Последняя метка отменена.", "The last timestamp was removed."); add("Предпросмотр HUD", "HUD preview");
        add("Предыдущая страница", "Previous page"); add("Предыдущие игроки", "Previous players"); add("Проверка сборки прервана.", "Build verification was interrupted.");
        add("прослушанные треки.", "recently played tracks will appear."); add("Развернуть панель", "Expand panel"); add("Раньше на 25 мс", "25 ms earlier");
        add("Редактор", "Editor"); add("Редактор текста для локального трека", "Lyrics editor for a local track"); add("Режим повтора", "Repeat mode");
        add("Роль может передать только ведущий.", "Only the host can transfer the role.");
        add("Сборка изменена и не подписана King220l. Вход в Яндекс заблокирован.", "This build was modified and is not signed by King220l. Yandex sign-in is blocked.");
        add("Сбросить положение", "Reset position"); add("Сбросить сдвиг", "Reset offset"); add("Свернуть панель", "Collapse panel");
        add("Связь · Бета вернула непонятный ответ.", "Connection · Beta returned an invalid response.");
        add("Связь · Бета не ответила вовремя.", "Connection · Beta timed out."); add("Связь · Бета недоступна.", "Connection · Beta is unavailable.");
        add("Связь занята. Попробуй чуть позже.", "Connection is busy. Try again later."); add("Связь не ответила вовремя.", "The connection timed out.");
        add("Сдвиг строк", "Lyrics offset"); add("Сделал этот мод", "Created this mod"); add("Сеанс истёк. Войди в Яндекс снова.", "Your session expired. Sign in to Yandex again.");
        add("Сейчас треками управляет ведущий комнаты.", "The room host is controlling playback."); add("Скрыть визуализацию", "Hide visualizer"); add("Скрыть текст песни", "Hide lyrics");
        add("Следующая страница", "Next page"); add("Следующие игроки", "Next players"); add("Слишком много запросов. Повтори чуть позже.", "Too many requests. Try again later.");
        add("Слишком много запросов. Попробуй чуть позже.", "Too many requests. Try again later."); add("Слишком много перенаправлений Яндекс Музыки.", "Too many Yandex Music redirects.");
        add("Слушать", "Play"); add("Сначала добавь строки песни.", "Add lyrics lines first."); add("Создай комнату или введи код друга.", "Create a room or enter a friend's code.");
        add("Сохранённых альбомов пока нет.", "There are no saved albums yet."); add("Сохранённых исполнителей пока нет.", "There are no saved artists yet.");
        add("Сохраняем .lrc рядом с песней…", "Saving .lrc beside the song…"); add("Стоп и к началу", "Stop and return to start");
        add("Строки выше", "Lines above"); add("Строки ниже", "Lines below"); add("Строки песни", "Lyrics lines");
        add("Токен имеет неверный формат.", "The token format is invalid."); add("Токен хранится через защиту Windows и отправляется только Яндексу.", "The token is protected by Windows encryption and sent only to Yandex.");
        add("Трек Яндекс Музыки", "Yandex Music track"); add("Треки из твоей папки", "Tracks from your folder"); add("Ты вышел из комнаты.", "You left the room.");
        add("Ты управляешь воспроизведением.", "You control playback."); add("У Яндекс Музыки нет данных об аккаунте.", "Yandex Music returned no account information.");
        add("Увеличить скорость", "Increase speed"); add("Уменьшить скорость", "Decrease speed"); add("Файл текста слишком большой.", "The lyrics file is too large.");
        add("Фонотека", "Music library"); add("Эта клавиша назначена на открытие плеера.", "This key is assigned to open the player.");
        add("Яндекс изменил формат ссылки на трек.", "Yandex changed the track link format."); add("Яндекс не выдал код для входа.", "Yandex did not provide a sign-in code.");
        add("Яндекс не подтвердил изменение отметки.", "Yandex did not confirm the like change."); add("Яндекс не предоставил MP3 для этого трека.", "Yandex did not provide an MP3 for this track.");
        add("Яндекс ограничил доступ. Проверь подписку и регион.", "Yandex restricted access. Check your subscription and region.");
        add("Яндекс пока не вернул следующие треки для Моей волны.", "Yandex has not returned the next My Wave tracks yet."); add("Яндекс ID", "Yandex ID");
        add("Для фарма", "For farming"); add("изменённая", "modified"); add("Код ", "Code "); add("Метка · ", "Timestamp · ");
        add("Мышь ", "Mouse "); add("Ошибка связи · Бета (HTTP ", "Connection · Beta error (HTTP ");
        add("Пауза / воспроизведение предпросмотра", "Pause / play preview"); add("Передать роль игроку ", "Transfer host role to ");
        add("Перемотка · ", "Seek · "); add("Перемотка…", "Seeking…"); add("Повтор: ", "Repeat: "); add("Прослушать с ", "Preview from ");
        add("Снять назначение: ", "Clear binding: "); add("Сохранено: ", "Saved: "); add("Уже назначено: ", "Already assigned: ");
        add("Фрагмент · ", "Preview · "); add("Яндекс вернул ошибку ", "Yandex returned error "); add("Lo-fi для игры", "Gaming lo-fi");
        add("трека", "tracks"); add("треков", "tracks"); add(" на связи", " connected"); add(" с метками", " timestamped");
        add("Неофициальная сборка", "Unofficial build"); add("Файл изменён", "File modified"); add("Оригинальный мод", "Official mod");
        add("Доступно обновление", "Update available"); add("Проверяем оригинальность King Music…", "Checking King Music authenticity…");
        add("Оригинальный King Music от King220l · цифровая подпись подтверждена", "Official King Music by King220l · digital signature verified");
        add("Не удалось проверить обновления: GitHub недоступен", "Could not check for updates: GitHub is unavailable");
    }

    private MusicI18n() {}
    private static void add(String ru, String en) { EN.put(ru, en); }

    static synchronized void initialize(Path config) {
        PACKS.clear();
        PACKS.put("en_us", new Pack("en_us", "English", EN));
        PACKS.put("ru_ru", new Pack("ru_ru", "Русский", Map.of()));
        Path directory = config.resolve("lang");
        try {
            Files.createDirectories(directory);
            try (Stream<Path> files = Files.list(directory)) {
                files.filter(Files::isRegularFile).filter(MusicI18n::languageFile).sorted().forEach(MusicI18n::loadPack);
            }
        } catch (IOException ignored) {}
    }

    private static boolean languageFile(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".lang") || name.endsWith(".json");
    }

    private static void loadPack(Path file) {
        String filename = file.getFileName().toString();
        String code = filename.substring(0, filename.lastIndexOf('.')).toLowerCase(Locale.ROOT).replace('-', '_');
        if (!code.matches("[a-z0-9_]{2,24}")) return;
        try {
            Map<String, String> values = filename.toLowerCase(Locale.ROOT).endsWith(".json")
                ? new Gson().fromJson(Files.readString(file, StandardCharsets.UTF_8), new TypeToken<LinkedHashMap<String, String>>() {}.getType())
                : readLang(file);
            if (values == null) return;
            String name = values.remove("language.name");
            if (name == null || name.isBlank()) name = code;
            PACKS.put(code, new Pack(code, name.strip(), Map.copyOf(values)));
        } catch (IOException | RuntimeException ignored) {}
    }

    private static Map<String, String> readLang(Path file) throws IOException {
        Map<String, String> values = new LinkedHashMap<>();
        for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int split = line.indexOf('=');
            if (split > 0) values.put(line.substring(0, split).strip(), line.substring(split + 1).strip());
        }
        return values;
    }

    static boolean hasLanguage(String code) { return code != null && PACKS.containsKey(code.toLowerCase(Locale.ROOT)); }
    static String languageLabel(String code) {
        Pack pack = PACKS.get(code);
        return pack == null ? "English" : pack.name();
    }
    static String nextLanguage(String current) {
        List<String> codes = new ArrayList<>(PACKS.keySet());
        int index = codes.indexOf(current);
        return codes.get((Math.max(index, 0) + 1) % codes.size());
    }
    static void use(String value) { language = hasLanguage(value) ? value : "en_us"; }
    public static boolean russian() { return "ru_ru".equals(language); }
    public static Component component(String value) { return Component.literal(text(value)); }

    public static String text(String value) {
        if (value == null || value.isEmpty() || russian()) return value;
        Pack pack = PACKS.getOrDefault(language, PACKS.get("en_us"));
        String exact = pack.values().get(value);
        if (exact != null) return exact;
        String result = value;
        for (var entry : pack.values().entrySet()) {
            if (result.contains(entry.getKey())) result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }
}
