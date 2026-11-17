package studio.king220l.yamusic;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.util.ArrayList;

import static studio.king220l.yamusic.MusicTheme.*;
import static studio.king220l.yamusic.MusicUi.*;

final class LyricsEditor extends MusicScreen {
    private static final String[] SPEEDS = {"0,25×", "0,5×", "0,75×", "1×", "1,25×", "1,5×"};
    private final LyricsAddon addon;
    private final MusicApi.Track track;
    private final ArrayList<LyricsData.Line> lines;
    private String draft, parsedDraft, notice = "";
    private boolean marking, saving;
    private int selected, offset, visible;
    private int rate = 4, timedCount;
    private long seenSeek;
    private LyricsData marked;
    private Change undo;
    private float scale;
    private Rect box, body;
    private Control markButton, transport, backStep, forwardStep;
    private MultiLineEditBox textInput;
    private final ArrayList<Control> speeds = new ArrayList<>();
    private Timeline timeline;
    private CueMarker marker;

    private record Change(int row, LyricsData.Line before) {}

    LyricsEditor(MusicMod mod, Screen parent, LyricsAddon addon, MusicApi.Track track, LyricsData lyrics) {
        super(mod, parent, "Редактор текста");
        this.addon = addon;
        this.track = track;
        lines = new ArrayList<>(lyrics.lines);
        draft = parsedDraft = lyrics.plain();
        seenSeek = mod.player.seekRevision();
        updateMarks();
        while (selected < lines.size() - 1 && lines.get(selected).timeMs() >= 0) selected++;
    }

    @Override
    protected void init() {
        scale = mod.settings.uiScale / 100f;
        int w = (int) (width / scale), h = (int) (height / scale);
        int panelW = Math.min(510, w - 18), panelH = Math.min(334, h - 14);
        box = new Rect((w - panelW) / 2, (h - panelH) / 2, panelW, panelH);
        body = new Rect(box.x() + 12, box.y() + 63, box.w() - 24, Math.max(24, box.h() - 177));
        markButton = null;
        textInput = null;
        speeds.clear();
        icon(new Rect(box.x() + 10, box.y() + 8, 22, 21), "Назад", Icon.BACK, Style.FILLED, this::onClose);
        button(new Rect(box.right() - 112, box.y() + 8, 100, 21), "Сохранить .lrc", null, Style.PRIMARY,
            true, false, this::save).active = !saving;
        if (marking) markingControls();
        else textControls();
        speedControls();
        int y = box.bottom() - 32;
        transport = icon(new Rect(box.x() + 12, y, 25, 21), "Пауза / воспроизведение", Icon.PLAY, Style.TRANSPORT,
            () -> { if (controlsPlayback()) mod.player.toggle(); });
        backStep = button(new Rect(box.x() + 43, y, 47, 21), "−0,25 с", null, Style.FILLED,
            true, false, () -> seek(mod.player.elapsedMs() - 250));
        forwardStep = button(new Rect(box.x() + 94, y, 47, 21), "+0,25 с", null, Style.FILLED,
            true, false, () -> seek(mod.player.elapsedMs() + 250));
        timeline = addRenderableWidget(new Timeline(new Rect(box.x() + 149, y + 3, box.w() - 161, 15), mod.player));
        marker = marking ? addRenderableWidget(new CueMarker(timeline)) : null;
        tick();
    }

    private void speedControls() {
        int y = box.bottom() - 59;
        int w = Math.max(24, Math.min(44, (body.w() - 62) / SPEEDS.length));
        int x = body.x() + 50;
        for (int i = 0; i < SPEEDS.length; i++) {
            int value = i + 1;
            speeds.add(button(new Rect(x + (w + 2) * i, y, w, 20), SPEEDS[i], null,
                rate == value ? Style.SELECTED : Style.FILLED, true, false, () -> changeRate(value)));
        }
    }

    private void changeRate(int value) {
        value = Math.clamp(value, 1, SPEEDS.length);
        if (!canChangeRate() || rate == value) return;
        rate = value;
        mod.player.editorRate(track, rate);
        rebuildWidgets();
    }

    private boolean canChangeRate() { return controlsPlayback() && !mod.inRoom() && !saving; }

    private void seek(long position) {
        if (controlsPlayback() && mod.player.canSeek()) mod.player.seek(track, position);
    }

    private void textControls() {
        var edit = addRenderableWidget(MultiLineEditBox.builder().setX(body.x()).setY(body.y())
            .setShowBackground(false).setShowDecorations(false)
            .setTextColor(TEXT).setCursorColor(ACCENT)
            .setPlaceholder(Component.literal("Вставь или напиши текст, по одной строке на абзац"))
            .build(font, body.w(), body.h(), Component.literal("Строки песни")));
        edit.setCharacterLimit(LyricsData.MAX_TEXT);
        edit.setLineLimit(LyricsData.MAX_LINES);
        edit.setValue(draft);
        edit.setValueListener(value -> draft = value);
        textInput = edit;
        button(new Rect(body.x(), body.bottom() + 7, 112, 21), "Расставить метки", null, Style.SELECTED,
            true, false, this::startMarking);
    }

    boolean typing() { return textInput != null && textInput.isFocused(); }

    void shortcut(MusicShortcuts.Action action) {
        if (saving) return;
        switch (action) {
            case PLAY -> { if (controlsPlayback()) mod.player.toggle(); }
            case EDITOR_BACK -> seek(mod.player.elapsedMs() - 250);
            case EDITOR_FORWARD -> seek(mod.player.elapsedMs() + 250);
            case EDITOR_STOP -> {
                if (controlsPlayback()) {
                    mod.player.setPaused(true);
                    seek(0);
                }
            }
            case EDITOR_MARK -> mark();
            case EDITOR_UNDO -> undoMark();
            case EDITOR_SLOWER -> changeRate(rate - 1);
            case EDITOR_FASTER -> changeRate(rate + 1);
            case EDITOR_PREVIOUS_LINE -> moveSelection(-1);
            case EDITOR_NEXT_LINE -> moveSelection(1);
            default -> {}
        }
    }

    private void moveSelection(int direction) {
        if (!marking || lines.isEmpty()) return;
        selected = Math.clamp(selected + direction, 0, lines.size() - 1);
        keepSelectionVisible();
        rebuildWidgets();
    }

    private void markingControls() {
        visible = Math.max(1, body.h() / 25);
        offset = Math.clamp(offset, 0, Math.max(0, lines.size() - visible));
        int until = Math.min(lines.size(), offset + visible);
        for (int i = offset; i < until; i++) {
            int line = i;
            var words = lines.get(i);
            String label = words.words().isBlank() ? "Пауза" : words.words();
            int y = body.y() + 25 * (i - offset) + 2;
            if (words.timeMs() >= 0) {
                button(new Rect(body.x() + 4, y, 66, 21), LyricsData.stamp(words.timeMs()), null, Style.GHOST,
                    true, false, () -> audition(line)).hint("Прослушать с " + LyricsData.stamp(words.timeMs()));
            }
            button(new Rect(body.x() + 73, y, body.w() - 78, 21), label, null,
                selected == i ? Style.SELECTED : Style.GHOST, true, true, () -> {
                    selected = line;
                    rebuildWidgets();
                });
        }
        int y = body.bottom() + 7;
        markButton = button(new Rect(body.x(), y, 111, 21), "Отметить строку", null, Style.SELECTED,
            true, false, this::mark);
        button(new Rect(body.x() + 116, y, 72, 21), "К тексту", null, Style.FILLED,
            true, false, () -> {
                marking = false;
                notice = "";
                rebuildWidgets();
            });
        if (body.w() >= 316) {
            button(new Rect(body.x() + 193, y, 76, 21), "Отменить", Icon.BACK, Style.FILLED,
                true, false, this::undoMark).active = undo != null && !saving;
        }
        icon(new Rect(body.right() - 45, y, 20, 21), "Строки выше", Icon.LEFT, Style.FILLED,
            () -> scroll(-visible)).active = offset > 0;
        icon(new Rect(body.right() - 22, y, 20, 21), "Строки ниже", Icon.RIGHT, Style.FILLED,
            () -> scroll(visible)).active = until < lines.size();
    }

    private void startMarking() {
        prepareRows();
        if (lines.isEmpty()) {
            notice = "Сначала добавь строки песни.";
            return;
        }
        marking = true;
        notice = "";
        if (mod.player.started() && marked.synced()) selectAtPlayback();
        else keepSelectionVisible();
        rebuildWidgets();
    }

    private void prepareRows() {
        if (draft.equals(parsedDraft)) return;
        LyricsData replacement = LyricsData.parse(draft);
        if (!replacement.synced() && lines.size() == replacement.lines.size()) {
            for (int i = 0; i < lines.size(); i++) {
                lines.set(i, new LyricsData.Line(lines.get(i).timeMs(), replacement.lines.get(i).words()));
            }
        } else {
            lines.clear();
            lines.addAll(replacement.lines);
        }
        parsedDraft = draft;
        selected = offset = 0;
        undo = null;
        updateMarks();
    }

    private void updateMarks() {
        marked = new LyricsData(lines);
        timedCount = (int) lines.stream().filter(line -> line.timeMs() >= 0).count();
    }

    private void selectAtPlayback() {
        int at = marked.at(mod.player.elapsedMs());
        selected = at < 0 ? 0 : at;
        keepSelectionVisible();
    }

    boolean canMark() {
        return marking && !saving && controlsPlayback() && mod.player.started() && selected < lines.size();
    }

    void mark() {
        if (!canMark()) return;
        setCue(selected, mod.player.elapsedMs());
        if (selected < lines.size() - 1) selected++;
        keepSelectionVisible();
        rebuildWidgets();
    }

    private void setCue(int row, long timestamp) {
        var before = lines.get(row);
        if (before.timeMs() != timestamp) {
            undo = new Change(row, before);
            lines.set(row, new LyricsData.Line(timestamp, before.words()));
            updateMarks();
        }
        notice = "Метка · " + LyricsData.stamp(timestamp);
    }

    private void audition(int row) {
        if (!controlsPlayback() || !mod.player.canSeek()) return;
        selected = row;
        seek(lines.get(row).timeMs());
        seenSeek = mod.player.seekRevision();
        keepSelectionVisible();
        rebuildWidgets();
    }

    private void undoMark() {
        if (undo == null || saving) return;
        selected = undo.row;
        lines.set(selected, undo.before);
        undo = null;
        updateMarks();
        keepSelectionVisible();
        notice = "Последняя метка отменена.";
        rebuildWidgets();
    }

    private void keepSelectionVisible() {
        int page = Math.max(1, body.h() / 25);
        if (selected < offset) offset = selected;
        if (selected >= offset + page) offset = selected - page + 1;
    }

    private void scroll(int delta) {
        int next = Math.clamp(offset + delta, 0, Math.max(0, lines.size() - visible));
        if (next == offset) return;
        offset = next;
        rebuildWidgets();
    }

    private boolean controlsPlayback() { return mod.player.track() == track && !mod.follows(); }

    private void save() {
        if (saving) return;
        prepareRows();
        if (lines.isEmpty()) {
            notice = "Для сохранения добавь хотя бы одну строку.";
            return;
        }
        LyricsData result = new LyricsData(lines);
        saving = true;
        notice = "Сохраняем .lrc рядом с песней…";
        rebuildWidgets();
        MusicMod.request(() -> {
            try { return result.save(track.file()); }
            catch (IOException failure) { throw new IOException("Не удалось записать .lrc рядом с песней."); }
        }, path -> {
            saving = false;
            addon.saved(track, result);
            if (minecraft.gui.screen() == this) super.onClose();
        }, error -> {
            saving = false;
            notice = error;
            if (minecraft.gui.screen() == this) rebuildWidgets();
        });
    }

    @Override
    public void tick() {
        if (rate != 4 && (!controlsPlayback() || mod.inRoom())) {
            rate = 4;
            mod.player.editorRate(track, 4);
            rebuildWidgets();
            return;
        }
        long seek = mod.player.seekRevision();
        if (seenSeek != seek) {
            seenSeek = seek;
            if (marking && !lines.isEmpty() && controlsPlayback() && (marker == null || !marker.dragging)) {
                selectAtPlayback();
                notice = "";
                rebuildWidgets();
                return;
            }
        }
        if (markButton != null) markButton.active = canMark();
        transport.active = controlsPlayback() && !saving;
        backStep.active = forwardStep.active = transport.active && mod.player.canSeek();
        for (Control speed : speeds) speed.active = canChangeRate();
        transport.icon = mod.player.started() && !mod.player.paused() ? Icon.PAUSE : Icon.PLAY;
        timeline.locked = !transport.active;
        timeline.sync();
        if (marker != null) marker.active = canMark() && mod.player.durationMs() > 0;
    }

    @Override
    public void onClose() {
        if (!saving) super.onClose();
    }

    @Override
    public void removed() {
        mod.player.editorRate(track, 4);
        super.removed();
    }

    private MouseButtonEvent scaled(MouseButtonEvent event) {
        return new MouseButtonEvent(event.x() / scale, event.y() / scale, event.buttonInfo());
    }

    @Override
    public void mouseMoved(double x, double y) { super.mouseMoved(x / scale, y / scale); }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean twice) { return super.mouseClicked(scaled(event), twice); }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) { return super.mouseReleased(scaled(event)); }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double x, double y) {
        return super.mouseDragged(scaled(event), x / scale, y / scale);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double delta) {
        if (marking && body.contains(x / scale, y / scale) && delta != 0) {
            scroll(delta > 0 ? -2 : 2);
            return true;
        }
        return super.mouseScrolled(x / scale, y / scale, horizontal, delta);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        g.pose().pushMatrix().scale(scale);
        outline(g, box.x(), box.y(), box.w(), box.h(), 10, BORDER, BASE);
        text(g, font, "Редактор текста", box.x() + 39, box.y() + 14, TEXT, Type.STRONG, box.w() - 154);
        int progressWidth = marking ? 99 : 0;
        text(g, font, track.title(), body.x(), box.y() + 39, ACCENT_LIGHT, Type.TITLE, body.w() - progressWidth);
        if (marking) {
            String progress = timedCount + "/" + lines.size() + " с метками";
            int w = MusicTheme.width(font, progress, Type.BODY);
            text(g, font, progress, body.right() - w, box.y() + 43, MUTED, Type.BODY, progressWidth);
        }
        outline(g, body.x(), body.y(), body.w(), body.h(), 6, BORDER, RAIL);
        if (marking) {
            int current = marked.at(mod.player.elapsedMs());
            for (int i = offset; i < Math.min(lines.size(), offset + visible); i++) {
                var line = lines.get(i);
                int y = body.y() + (i - offset) * 25;
                if (line.timeMs() < 0)
                    text(g, font, "—", body.x() + 10, y + 9, i == selected ? ACCENT : MUTED, Type.BODY, 64);
                if (i == current) rounded(g, body.x() + 4, y + 8, 2, 9, 1, ACCENT);
            }
        }
        String help = notice;
        if (help.isEmpty()) {
            if (!marking) help = "Можно сохранить текст и без меток.";
            else if (!controlsPlayback()) help = "Запусти этот трек сам, чтобы поставить метки.";
            else if (mod.inRoom()) help = "В комнате используется обычная скорость.";
            else {
                String key = addon.markKey();
                help = "Выбери строку и передвинь стрелку над полосой времени" + (key.isEmpty() ? "." : " · " + key);
            }
        }
        text(g, font, help, body.x(), body.bottom() + 37, MUTED, Type.BODY, body.w());
        text(g, font, "Скорость", body.x(), box.bottom() - 53, MUTED, Type.BODY, 47);
        super.extractRenderState(g, (int) (mx / scale), (int) (my / scale), dt);
        g.pose().popMatrix();
    }

    private final class CueMarker extends AbstractWidget {
        private boolean dragging, wasFine;
        private double lastX;
        private long pendingTime;

        CueMarker(Timeline rail) {
            super(rail.getX(), rail.getY() - 10, rail.getWidth(), 10, Component.literal("Метка выбранной строки"));
            setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
                "Метка выбранной строки · Shift при перетаскивании или стрелки для точной настройки")));
        }

        private long timestamp() {
            if (dragging) return pendingTime;
            long cue = lines.get(selected).timeMs();
            return Math.clamp(cue < 0 ? mod.player.elapsedMs() : cue, 0, mod.player.durationMs());
        }

        private long position(double x) {
            double fraction = Math.clamp((x - getX() - 4) / Math.max(1, getWidth() - 8), 0, 1);
            return Math.round(mod.player.durationMs() * fraction);
        }

        @Override
        public void onClick(MouseButtonEvent event, boolean twice) {
            if (!active) return;
            long start = timestamp();
            wasFine = event.hasShiftDown();
            lastX = event.x();
            double pointer = getX() + 4 + (getWidth() - 8) * (double) start / mod.player.durationMs();
            pendingTime = Math.abs(event.x() - pointer) <= 5 || wasFine ? start : position(event.x());
            dragging = true;
        }

        @Override
        protected void onDrag(MouseButtonEvent event, double dx, double dy) {
            if (!dragging || !active) return;
            boolean fine = event.hasShiftDown();
            if (fine) pendingTime = Math.clamp(pendingTime + Math.round((event.x() - lastX) * 10), 0, mod.player.durationMs());
            else if (!wasFine || Math.abs(event.x() - lastX) > 2) pendingTime = position(event.x());
            lastX = event.x();
            wasFine = fine;
        }

        @Override
        public void onRelease(MouseButtonEvent event) {
            if (!dragging) return;
            dragging = false;
            if (!active || !canMark()) return;
            setCue(selected, pendingTime);
            rebuildWidgets();
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            if (!active || !isFocused() || event.key() != GLFW.GLFW_KEY_LEFT && event.key() != GLFW.GLFW_KEY_RIGHT) return false;
            int step = (event.hasShiftDown() ? 100 : 10) * (event.key() == GLFW.GLFW_KEY_LEFT ? -1 : 1);
            setCue(selected, Math.clamp(timestamp() + step, 0, mod.player.durationMs()));
            rebuildWidgets();
            if (marker != null) LyricsEditor.this.setFocused(marker);
            return true;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
            if (!active) return;
            long stamp = timestamp();
            int x = getX() + 4 + (int) Math.round((getWidth() - 8) * (double) stamp / mod.player.durationMs());
            int y = getY();
            int color = dragging || isHoveredOrFocused() ? ACCENT_LIGHT : ACCENT;
            g.fill(getX() + 4, y + 4, getRight() - 4, y + 5, BORDER);
            g.fill(x - 4, y + 1, x + 5, y + 4, color);
            g.fill(x - 3, y + 4, x + 4, y + 5, color);
            g.fill(x - 2, y + 5, x + 3, y + 6, color);
            g.fill(x - 1, y + 6, x + 2, y + 7, color);
            g.fill(x, y + 7, x + 1, y + 11, color);
            if (dragging || isHoveredOrFocused()) {
                int right = box.right() - 13;
                String label = LyricsData.stamp(stamp);
                int pixels = MusicTheme.width(font, label, Type.BODY);
                text(g, font, label, right - pixels, y, ACCENT_LIGHT, Type.BODY, pixels);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
    }
}
