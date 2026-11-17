package studio.king220l.yamusic;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

import static studio.king220l.yamusic.MusicTheme.*;
import static studio.king220l.yamusic.MusicUi.*;
import static studio.king220l.yamusic.rooms.RoomWire.*;

final class TogetherScreen extends MusicScreen {
    private static final int FIRST_SETTING = 36;
    private static final int SETTING_STEP = 25;
    private final TogetherAddon addon;
    private Rect box, field;
    private EditBox input;
    private Control join, create, leave;
    private float scale;
    private String roomCode = "";
    private boolean roster, roomShown, transferShown, accountShown;
    private List<Member> shownRoster = List.of();
    private int rosterOffset, rosterRows;

    TogetherScreen(MusicMod mod, Screen parent, TogetherAddon addon) {
        super(mod, parent, "Вместе");
        this.addon = addon;
    }

    @Override
    protected void init() {
        scale = mod.settings.uiScale / 100f;
        int w = (int) (width / scale), h = (int) (height / scale);
        int panelWidth = Math.min(440, w - 16), panelHeight = Math.min(211, h - 12);
        box = new Rect((w - panelWidth) / 2, (h - panelHeight) / 2, panelWidth, panelHeight);
        field = null;
        input = null;
        join = create = leave = null;
        var room = addon.social.room();
        roomShown = room != null;
        transferShown = roomShown && room.host() && !peers(room).isEmpty();
        accountShown = mod.api.signedIn();
        if (roster && !transferShown) roster = false;

        icon(new Rect(box.x() + 9, box.y() + 8, 22, 21), "Назад", Icon.BACK, Style.FILLED, this::onClose);
        if (roster) rosterControls(room);
        else roomControls(room);
        updateControls();
    }

    private void roomControls(Room room) {
        int x = box.x() + 11, w = box.w() - 22, y = box.y() + FIRST_SETTING;
        int right = x + w;
        addRenderableWidget(new Toggle(new Rect(right - 34, y + 4, 28, 15), addon.settings.showBadges, () -> {
            addon.settings.showBadges = !addon.settings.showBadges;
            addon.settings.save();
            rebuildWidgets();
        })).hint("Значки других игроков в TAB");
        addRenderableWidget(new Toggle(new Rect(right - 34, y + SETTING_STEP + 4, 28, 15), addon.settings.betaLink, () -> {
            addon.settings.betaLink = !addon.settings.betaLink;
            addon.settings.save();
            rebuildWidgets();
        })).hint("Использовать связь · Бета, если нет Together Server");
        int qualityWidth = Math.min(126, w / 2);
        button(new Rect(right - qualityWidth - 5, y + SETTING_STEP * 2 + 2, qualityWidth, 18),
            mod.settings.yandexQuality.label, Icon.REFRESH, Style.FILLED, qualityWidth >= 75, false, () -> {
                mod.settings.cycleQuality();
                rebuildWidgets();
            }).hint(MusicSettings.QUALITY_HINT);

        y = actionY();
        if (room != null) {
            int buttonWidth = Math.min(114, (w - 8) / (transferShown ? 3 : 2));
            button(new Rect(x, y, buttonWidth, 22), "Скопировать код", Icon.LIST, Style.PRIMARY, buttonWidth >= 98, false,
                () -> {
                    var current = addon.social.room();
                    if (current != null) minecraft.keyboardHandler.setClipboard(current.code());
                }).hint("Скопировать код");
            leave = button(new Rect(x + buttonWidth + 4, y, buttonWidth, 22), "Выйти", Icon.BACK, Style.FILLED,
                buttonWidth >= 56, false, addon.social::leave);
            if (transferShown) {
                button(new Rect(x + (buttonWidth + 4) * 2, y, buttonWidth, 22), "Передать роль", Icon.USER, Style.FILLED,
                    buttonWidth >= 95, false, () -> openRoster(true)).hint("Передать роль ведущего");
            }
        } else if (!accountShown) {
            button(new Rect(x, y, Math.min(125, w), 22), "Войти в Яндекс", Icon.USER, Style.PRIMARY,
                true, false, () -> {
                    super.onClose();
                    if (parent instanceof PlayerScreen player) player.showAccount();
                });
        } else {
            int buttonWidth = Math.min(81, (w - 8) / 3);
            field = new Rect(x, y, w - buttonWidth * 2 - 8, 22);
            input = addRenderableWidget(new EditBox(font, field.x() + 7, field.y() + 6, field.w() - 14, 13,
                Component.literal("Код комнаты")));
            input.setBordered(false);
            input.setMaxLength(16);
            input.setTextColor(TEXT);
            input.setHint(styled("Код комнаты", Type.BODY));
            input.setValue(roomCode);
            input.setResponder(value -> roomCode = value);
            join = button(new Rect(field.right() + 4, y, buttonWidth, 22), "Войти", Icon.RIGHT, Style.FILLED,
                buttonWidth >= 65, false, () -> addon.social.join(roomCode));
            create = button(new Rect(right - buttonWidth, y, buttonWidth, 22), "Создать", Icon.PLUS, Style.PRIMARY,
                buttonWidth >= 65, false, addon.social::create);
        }
    }

    private int actionY() { return box.y() + FIRST_SETTING + 3 * SETTING_STEP + 40; }

    private void updateControls() {
        boolean ready = addon.social.link() != MusicSocial.Link.OFF && !addon.social.busy();
        if (join != null) join.active = create.active = ready;
        if (leave != null) leave.active = !addon.social.busy();
    }

    private void rosterControls(Room room) {
        shownRoster = peers(room);
        rosterRows = Math.max(1, Math.min(5, (box.h() - 72) / 24));
        int last = Math.max(0, shownRoster.size() - rosterRows);
        rosterOffset = Math.clamp(rosterOffset, 0, last);
        int x = box.x() + 11, right = box.right() - 11;
        if (last > 0) {
            icon(new Rect(right - 43, box.y() + 8, 20, 21), "Предыдущие игроки", Icon.LEFT, Style.FILLED,
                () -> pageRoster(-rosterRows)).active = rosterOffset > 0;
            icon(new Rect(right - 20, box.y() + 8, 20, 21), "Следующие игроки", Icon.RIGHT, Style.FILLED,
                () -> pageRoster(rosterRows)).active = rosterOffset < last;
        }
        int end = Math.min(shownRoster.size(), rosterOffset + rosterRows);
        for (int i = rosterOffset; i < end; i++) {
            var peer = shownRoster.get(i);
            int y = box.y() + 39 + (i - rosterOffset) * 24;
            int buttonWidth = Math.min(108, (right - x) / 2);
            button(new Rect(right - buttonWidth, y, buttonWidth, 21), "Передать", Icon.RIGHT, Style.PRIMARY,
                true, false, () -> {
                    addon.social.transfer(peer);
                    openRoster(false);
                }).hint("Передать роль игроку " + peer.name());
        }
    }

    private void pageRoster(int delta) {
        rosterOffset += delta;
        rebuildWidgets();
    }

    private static List<Member> peers(Room room) {
        if (room == null) return List.of();
        return room.peers().stream().filter(peer -> peer != null && !peer.host() && peer.connected()
            && peer.id() != null && peer.name() != null).limit(MAX_MEMBERS).toList();
    }

    private void openRoster(boolean show) {
        roster = show;
        rosterOffset = 0;
        rebuildWidgets();
    }

    @Override
    public void tick() {
        var room = addon.social.room();
        boolean canTransfer = room != null && room.host() && !peers(room).isEmpty();
        if (roomShown != (room != null) || transferShown != canTransfer || accountShown != mod.api.signedIn()
                || roster && !shownRoster.equals(peers(room))) rebuildWidgets();
        updateControls();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (input != null && input.isFocused() && join.active
                && (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER)) {
            addon.social.join(roomCode);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        if (roster) openRoster(false);
        else super.onClose();
    }

    private MouseButtonEvent layoutEvent(MouseButtonEvent event) {
        return new MouseButtonEvent(event.x() / scale, event.y() / scale, event.buttonInfo());
    }

    @Override public void mouseMoved(double x, double y) { super.mouseMoved(x / scale, y / scale); }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean twice) { return super.mouseClicked(layoutEvent(event), twice); }
    @Override public boolean mouseReleased(MouseButtonEvent event) { return super.mouseReleased(layoutEvent(event)); }
    @Override public boolean mouseDragged(MouseButtonEvent event, double x, double y) {
        return super.mouseDragged(layoutEvent(event), x / scale, y / scale);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        g.pose().pushMatrix().scale(scale);
        outline(g, box.x(), box.y(), box.w(), box.h(), 10, BORDER, BASE);
        int x = box.x() + 11, w = box.w() - 22;
        String title = roster ? "Кому передать роль" : "Вместе";
        text(g, font, title, box.x() + 37, box.y() + 13, TEXT, Type.STRONG, box.w() - 90);
        if (roster) {
            for (int i = rosterOffset; i < Math.min(shownRoster.size(), rosterOffset + rosterRows); i++) {
                int y = box.y() + 39 + (i - rosterOffset) * 24;
                rounded(g, x, y, w, 21, 5, RAIL);
                Icon.USER.draw(g, x + 7, y + 6, ACCENT);
                text(g, font, shownRoster.get(i).name(), x + 21, y + 6, TEXT, Type.STRONG, w - 134);
            }
        } else {
            String link = switch (addon.social.link()) {
                case MINECRAFT -> "Через Minecraft";
                case BETA -> "Связь · Бета";
                case OFF -> "Связь выключена";
            };
            int linkWidth = MusicTheme.width(font, link, Type.BODY);
            text(g, font, link, box.right() - linkWidth - 14, box.y() + 13,
                addon.social.link() == MusicSocial.Link.OFF ? MUTED : ACCENT_LIGHT, Type.BODY, linkWidth);
            var labels = List.of("Значки игроков в TAB", "Связь без серверного мода · Бета", MusicSettings.QUALITY_LABEL);
            for (int i = 0; i < labels.size(); i++) {
                int y = box.y() + FIRST_SETTING + i * SETTING_STEP;
                rounded(g, x, y, w, 22, 5, RAIL);
                text(g, font, labels.get(i), x + 8, y + 7, TEXT, Type.BODY, w - (i == 2 ? 140 : 52));
            }
            Room room = addon.social.room();
            String name = room == null ? "Комната прослушивания" : room.code() + " · " + room.connectedCount() + " на связи";
            text(g, font, name, x, actionY() - 28, TEXT, Type.STRONG, w);
            if (room != null) {
                String leader = room.peers().stream().filter(member -> member != null && member.host() && member.name() != null)
                    .map(Member::name).findFirst().orElse("Игрок");
                text(g, font, "Ведущий · " + leader, x, actionY() - 15, ACCENT, Type.BODY, w);
            }
            if (field != null) outline(g, field.x(), field.y(), field.w(), field.h(), 5,
                input.isFocused() ? ACCENT_EDGE : BORDER, RAIL);
            String message = addon.social.status();
            if (message.isEmpty()) {
                if (!accountShown) message = "Для комнат нужен свой аккаунт Яндекса.";
                else if (addon.social.link() == MusicSocial.Link.OFF) message = "Включи бета-связь или зайди на сервер с Together Server.";
                else message = "Создай комнату или введи код друга.";
            }
            text(g, font, message, x, actionY() + 29, MUTED, Type.BODY, w);
        }
        super.extractRenderState(g, (int) (mx / scale), (int) (my / scale), dt);
        g.pose().popMatrix();
    }
}

