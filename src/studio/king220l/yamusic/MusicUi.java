package studio.king220l.yamusic;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import studio.king220l.yamusic.CompatInput.KeyEvent;
import studio.king220l.yamusic.CompatInput.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import studio.king220l.yamusic.CompatColor;
import net.minecraft.Util;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import static studio.king220l.yamusic.MusicTheme.*;

final class MusicUi {
    private static final int ROW = 28;
    private static final int SETTING = 26;
    private static final ResourceLocation BANNER = ResourceLocation.fromNamespaceAndPath("yamusic", "textures/gui/wave.png");

    enum Style { GHOST, FILLED, SELECTED, PRIMARY, TRANSPORT }

    record Preference(String title, Consumer<Rect> layout) {}

    record Rect(int x, int y, int w, int h) {
        int right() { return x + w; }
        int bottom() { return y + h; }
        boolean contains(double px, double py) {
            return px >= x && px < right() && py >= y && py < bottom();
        }
    }

    static class Control extends AbstractWidget {
        private final Runnable action;
        Icon icon;
        Style style;
        private final boolean showLabel;
        private final boolean leftAligned;

        private long lastFrame;
        private long pulseUntil;
        private boolean held;
        float hover;
        float press;

        Control(Rect r, String label, Icon icon, Style style, boolean showLabel, boolean leftAligned, Runnable action) {
            super(r.x(), r.y(), r.w(), r.h(), MusicI18n.component(label));
            this.icon = icon;
            this.style = style;
            this.showLabel = showLabel;
            this.leftAligned = leftAligned;
            this.action = action;
        }

        void hint(String label) { setTooltip(Tooltip.create(MusicI18n.component(label))); }

        void setLabel(String label) {
            String translated = MusicI18n.text(label);
            if (!getMessage().getString().equals(translated)) setMessage(Component.literal(translated));
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            MouseButtonEvent event = CompatInput.mouse(mouseX, mouseY, 0);
            held = true;
            pulse();
            action.run();
        }

        @Override
        public void onRelease(double mouseX, double mouseY) {
            MouseButtonEvent event = CompatInput.mouse(mouseX, mouseY, 0); held = false; }

        @Override
        public void playDownSound(SoundManager sounds) {
            if (MusicMod.uiSoundsEnabled()) {
                sounds.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.04f, .15f));
            }
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            KeyEvent event = new KeyEvent(keyCode, scanCode, modifiers);
            if (!active || !visible || !event.isSelection()) return false;
            playDownSound(Minecraft.getInstance().getSoundManager());
            pulse();
            action.run();
            return true;
        }

        private void pulse() { pulseUntil = Util.getNanos() + 90_000_000L; }

        boolean keyboardFocus() {
            return active && isFocused() && Minecraft.getInstance().getLastInputType().isKeyboard();
        }

        void beginMotion(GuiGraphics g) {
            long now = Util.getNanos();
            float ms = lastFrame == 0 ? 16 : Math.min(50, (now - lastFrame) / 1_000_000f);
            lastFrame = now;
            if (held) {
                var client = Minecraft.getInstance();
                held = client.isWindowActive()
                    && GLFW.glfwGetMouseButton(client.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
            }
            hover = approach(hover, active && (isHovered() || keyboardFocus()) ? 1 : 0, ms / 125);
            press = approach(press, active && (held && isHovered() || now < pulseUntil) ? 1 : 0, ms / 78);
            CompatPose.of(g).pushMatrix();
            if (press > 0) {
                float cx = getX() + width * .5f, cy = getY() + height * .5f;
                float scale = 1 - .043f * press;
                CompatPose.of(g).translate(cx, cy).scale(scale).translate(-cx, -cy);
            }
        }

        private static float approach(float current, float target, float step) {
            return current < target ? Math.min(current + step, target) : Math.max(current - step, target);
        }

        int symbolSize() { return icon == null ? 0 : 9; }

        void drawSymbol(GuiGraphics g, int x, int y, int color) {
            icon.draw(g, x, y, icon == Icon.CROWN ? KING_GOLD : color);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }


        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float dt) {
            int x = getX(), y = getY();
            beginMotion(g);
            boolean focused = keyboardFocus();
            int fill = switch (style) {
                case GHOST -> focused ? ELEVATED : CompatColor.multiplyAlpha(ELEVATED, hover);
                case FILLED -> CompatColor.srgbLerp(hover, ELEVATED, HOVER);
                case SELECTED -> CompatColor.srgbLerp(hover, ACCENT_SOFT, ACCENT_HOVER);
                case PRIMARY -> active ? CompatColor.srgbLerp(hover, ACCENT_SOFT, ACCENT_HOVER) : ELEVATED;
                case TRANSPORT -> CompatColor.srgbLerp(hover, ELEVATED, 0xff413743);
            };
            if (active && press > 0 && style != Style.GHOST) fill = CompatColor.scaleRGB(fill, 1 - press * .14f);
            int color = !active ? DIM : switch (style) {
                case PRIMARY -> ACCENT_LIGHT;
                case SELECTED -> ACCENT;
                case TRANSPORT -> CompatColor.srgbLerp(hover, ACCENT_LIGHT, ACCENT);
                default -> TEXT;
            };
            if (style == Style.TRANSPORT) {
                int rim = focused ? ACCENT_LIGHT : CompatColor.srgbLerp(hover, ACCENT_EDGE, ACCENT);
                outline(g, x, y, width, height, Math.min(width, height) / 2, rim, fill);
            } else if (style == Style.PRIMARY && active) {
                int rim = focused ? ACCENT_LIGHT : CompatColor.srgbLerp(hover, ACCENT_EDGE, ACCENT);
                outline(g, x, y, width, height, 5, rim, fill);
            } else if (focused || style == Style.SELECTED || style == Style.FILLED && hover > 0) {
                int rim = focused ? ACCENT : style == Style.SELECTED
                    ? CompatColor.srgbLerp(hover, ACCENT_SOFT, ACCENT_EDGE)
                    : CompatColor.srgbLerp(hover, ELEVATED, BORDER_HOVER);
                outline(g, x, y, width, height, 5, rim, fill);
            } else if (CompatColor.alpha(fill) > 0) {
                rounded(g, x, y, width, height, 5, fill);
            }

            Font font = Minecraft.getInstance().font;
            String title = getMessage().getString();
            Type type = style == Style.PRIMARY || style == Style.SELECTED ? Type.STRONG : Type.BODY;
            int iconWidth = symbolSize();
            int gap = iconWidth > 0 && showLabel ? 6 : 0;
            int maxText = Math.max(0, width - iconWidth - gap - 10);
            int textWidth = showLabel ? Math.min(maxText, MusicTheme.width(font, title, type)) : 0;
            int group = iconWidth + gap + textWidth;
            int px = leftAligned ? x + 7 : x + (width - group) / 2;
            int py = y + (height - 9) / 2;
            if (style == Style.TRANSPORT && icon != null) {
                CompatPose.of(g).translate(x + (width - 9) * .5f, y + (height - 9) * .5f);
                icon.draw(g, 0, 0, color);
            } else {
                if (iconWidth > 0) drawSymbol(g, px, y + (height - iconWidth) / 2, style == Style.GHOST && active
                    ? CompatColor.srgbLerp(hover, MUTED, color) : color);
                if (showLabel) text(g, font, title, px + iconWidth + gap, py, color, type, maxText);
            }
            CompatPose.of(g).popMatrix();
        }
    }

    static final class Timeline extends AbstractSliderButton {
        private static final int STAMP_SPACE = 91;
        private final MusicPlayer player;
        private final int stampRight;
        private MusicApi.Track draggingTrack;
        private boolean dragged;
        boolean locked;

        Timeline(Rect r, MusicPlayer player) {
            super(r.x(), r.y(), Math.max(24, r.w() - STAMP_SPACE), r.h(), Component.literal("Перемотка"), 0);
            this.player = player;
            stampRight = r.right();
        }

        void sync() {
            active = !locked && player.canSeek();
            if (draggingTrack != null && draggingTrack == player.track() && active) return;
            draggingTrack = null;
            dragged = false;
            long duration = player.durationMs();
            value = duration <= 0 ? 0 : Math.clamp((double) player.elapsedMs() / duration, 0, 1);
        }

        private long position() { return Math.round(player.durationMs() * value); }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal("Перемотка · " + time(position())));
        }

        @Override
        protected void applyValue() {
            if (draggingTrack != null) dragged = true;
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            MouseButtonEvent event = CompatInput.mouse(mouseX, mouseY, 0);
            if (!player.canSeek()) return;
            draggingTrack = player.track();
            dragged = false;
            super.onClick(mouseX, mouseY);
        }

        @Override
        public void onRelease(double mouseX, double mouseY) {
            MouseButtonEvent event = CompatInput.mouse(mouseX, mouseY, 0);
            super.onRelease(mouseX, mouseY);
            if (!locked && dragged && draggingTrack != null) player.seek(draggingTrack, position());
            draggingTrack = null;
            sync();
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            KeyEvent event = new KeyEvent(keyCode, scanCode, modifiers);
            if (!isFocused() || !active) return false;
            int key = event.key();
            if (key != GLFW.GLFW_KEY_LEFT && key != GLFW.GLFW_KEY_RIGHT) return false;
            player.seek(player.track(), player.elapsedMs() + (key == GLFW.GLFW_KEY_LEFT ? -5_000 : 5_000));
            sync();
            return true;
        }

        @Override
        public void renderWidget(GuiGraphics g, int mx, int my, float dt) {
            Font font = Minecraft.getInstance().font;
            int start = getX() + 4, end = getRight() - 4, y = getY() + height / 2;
            drawMeter(g, start, y, end, (float) value, ACCENT, active, isHoveredOrFocused() && active);
            long duration = player.durationMs();
            String stamp = duration > 0 ? time(position()) + " / " + time(duration) : time(0);
            int stampWidth = Math.min(STAMP_SPACE - 8, MusicTheme.width(font, stamp, Type.BODY));
            text(g, font, stamp, stampRight - stampWidth, y - 4, MUTED, Type.BODY, stampWidth);
            if (active && isHovered() && draggingTrack == null) {
                double fraction = Math.clamp((mx - start) / (double) Math.max(1, end - start), 0, 1);
                CompatUi.tooltip(g, font, Component.literal(time(Math.round(duration * fraction))),
                    screenX(g, mx), screenY(g, my));
            }
        }
    }

    static final class TitleIcon extends Control {
        private final MusicPlayer player;

        TitleIcon(int x, int y, MusicPlayer player, Runnable action) {
            super(new Rect(x, y, 20, 20), MusicMod.NAME, null, Style.FILLED, false, false, action);
            this.player = player;
            hint(MusicMod.NAME);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float dt) {
            beginMotion(g);
            int x = getX(), y = getY();
            if (keyboardFocus()) outline(g, x, y, width, height, 10, ACCENT, RAIL);
            else if (hover > 0) rounded(g, x, y, width, height, 10, CompatColor.multiplyAlpha(HOVER, hover));
            drawRecord(g, x + 2, y + 2, 16, player.recordPositionMs());
            CompatPose.of(g).popMatrix();
        }
    }

    static final class MiniTrack extends Control {
        private final MusicPlayer player;

        MiniTrack(Rect bounds, MusicPlayer player, Runnable action) {
            super(bounds, MusicMod.NAME, null, Style.SELECTED, true, true, action);
            this.player = player;
        }

        @Override
        int symbolSize() { return 13; }

        @Override
        void drawSymbol(GuiGraphics g, int x, int y, int color) {
            drawRecord(g, x, y, symbolSize(), player.recordPositionMs());
        }
    }

    private static final class FavoriteControl extends Control {
        private boolean marked;
        private long pulseStart = -1;

        FavoriteControl(Rect bounds, Runnable action) {
            super(bounds, "Мне нравится", Icon.HEART, Style.GHOST, false, false, action);
            hint("Добавить в любимое");
        }

        void mark(boolean value, boolean animate) {
            if (marked == value) return;
            marked = value;
            pulseStart = marked && animate ? Util.getMillis() : -1;
            hint(marked ? "Убрать из любимого" : "Добавить в любимое");
        }

        @Override
        void drawSymbol(GuiGraphics g, int x, int y, int color) {
            if (!marked) {
                super.drawSymbol(g, x, y, color);
                return;
            }
            if (pulseStart < 0) {
                Icon.HEART_FILLED.draw(g, x, y, ROSE);
                return;
            }
            float progress = Math.clamp((Util.getMillis() - pulseStart) / 320f, 0, 1);
            if (progress == 1) pulseStart = -1;
            float scale = 1 + .25f * (float) Math.sin(Math.PI * progress);
            float cx = x + 4.5f, cy = y + 4.5f;
            var pose = CompatPose.of(g);
            pose.pushMatrix().translate(cx, cy).scale(scale).translate(-cx, -cy);
            Icon.HEART.draw(g, x, y, color);
            Icon.HEART_FILLED.draw(g, x, y, CompatColor.multiplyAlpha(ROSE, Math.min(1, progress * 3)));
            pose.popMatrix();
        }
    }

    private static final class TrustCrown extends Control {
        TrustCrown(Rect bounds, Runnable action) {
            super(bounds, "Корона", Icon.CROWN, Style.GHOST, false, false, action);
        }

        @Override
        void drawSymbol(GuiGraphics g, int x, int y, int color) {
            Icon.CROWN.draw(g, x, y, BuildTrust.crownColor());
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float dt) {
            super.renderWidget(g, mx, my, dt);
            if (BuildTrust.updateAvailable()) rounded(g, getRight() - 6, getY() + 2, 4, 4, 2, ROSE);
        }
    }

    private static final class MediaRow extends Control {
        private String title = "";
        private String subtitle = "";
        private String end = "";
        private boolean playing;

        MediaRow(Rect rect, Runnable action) {
            super(rect, "", null, Style.GHOST, false, false, action);
        }

        void show(String title, String subtitle, String end, Icon icon, boolean playing) {
            if (!this.title.equals(title) || !this.subtitle.equals(subtitle)) {
                hint(subtitle.isEmpty() ? title : title + " · " + subtitle);
            }
            this.title = title;
            this.subtitle = subtitle;
            this.end = end;
            this.icon = icon;
            this.playing = playing;
            visible = true;
            setLabel(title);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float dt) {
            int x = getX(), y = getY();
            beginMotion(g);
            int fill = CompatColor.srgbLerp(hover, playing ? ACCENT_SOFT : SURFACE, playing ? ACCENT_HOVER : HOVER);
            if (keyboardFocus()) outline(g, x, y, width, height, 5, ACCENT, fill);
            else rounded(g, x, y, width, height, 5, fill);
            int iconY = y + (height - 17) / 2;
            int lineY = y + (height - 9) / 2;
            rounded(g, x + 4, iconY, 17, 17, 4, playing ? ACCENT : ELEVATED);
            (hover > .55f && icon == Icon.MUSIC ? Icon.PLAY : icon).draw(g, x + 8, iconY + 4, playing ? INK : ACCENT);
            Font font = Minecraft.getInstance().font;
            int tailWidth = MusicTheme.width(font, end, Type.BODY), left = x + 26;
            int textWidth = width - 32 - tailWidth - (tailWidth == 0 ? 0 : 6);
            int titleColor = playing ? ACCENT : TEXT;
            if (!subtitle.isEmpty()) {
                text(g, font, title, left, lineY - 5, titleColor, Type.STRONG, textWidth);
                text(g, font, subtitle, left, lineY + 5, MUTED, Type.BODY, textWidth);
            } else {
                text(g, font, title, left, lineY, titleColor, Type.STRONG, textWidth);
            }
            text(g, font, end, x + width - tailWidth - 6, lineY, DIM, Type.BODY, tailWidth);
            CompatPose.of(g).popMatrix();
        }
    }

    static final class Slider extends AbstractSliderButton {
        private static final int VALUE_GAP = 9;
        private final int min;
        private final int max;
        private final String name;
        private final String suffix;
        private final IntConsumer change;
        private final Runnable save;
        private final boolean showValue;
        private final int valueOffset;
        private Component valueText;
        private int valueWidth;
        private boolean dirty;
        private boolean fineSteps;
        private boolean stackedValue;

        Slider(Rect r, String name, String suffix, int current, int min, int max, boolean showValue,
               IntConsumer change, Runnable save) {
            super(r.x(), r.y(), Math.max(14, r.w() - (showValue ? valueSpace(suffix) : 0)), r.h(),
                Component.literal(name), (current - min) / (double) (max - min));
            this.min = min;
            this.max = max;
            this.name = name;
            this.suffix = suffix;
            this.change = change;
            this.save = save;
            this.showValue = showValue;
            valueOffset = r.w() - 4;
            updateMessage();
        }

        private static int valueSpace(String suffix) {
            Font font = Minecraft.getInstance().font;
            int digitWidth = 0;
            for (int i = 0; i < 10; i++) digitWidth = Math.max(digitWidth, MusicTheme.width(font, Integer.toString(i), Type.STRONG));
            int unitWidth = Math.max(MusicTheme.width(font, "%", Type.STRONG), MusicTheme.width(font, suffix, Type.STRONG));
            return digitWidth * 3 + unitWidth + VALUE_GAP + 5;
        }

        private int amount() { return min + (int) Math.round(value * (max - min)); }

        Slider fineSteps() {
            fineSteps = true;
            return this;
        }

        Slider stackValue() {
            stackedValue = true;
            setWidth(valueOffset + 4);
            return this;
        }

        void sync(int now) {
            if (dirty || now == amount()) return;
            value = (now - min) / (double) (max - min);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            String label = amount() + suffix;
            setMessage(Component.literal(name + ": " + label));
            if (showValue) {
                valueText = styled(label, Type.STRONG);
                valueWidth = Minecraft.getInstance().font.width(valueText);
            }
        }

        @Override
        protected void applyValue() {
            change.accept(amount());
            dirty = true;
        }

        @Override
        public void onRelease(double mouseX, double mouseY) {
            MouseButtonEvent event = CompatInput.mouse(mouseX, mouseY, 0);
            super.onRelease(mouseX, mouseY);
            flush();
        }

        @Override
        public void setFocused(boolean focused) {
            super.setFocused(focused);
            if (!focused) flush();
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            KeyEvent event = new KeyEvent(keyCode, scanCode, modifiers);
            if (fineSteps && active && isFocused()
                    && (event.key() == GLFW.GLFW_KEY_LEFT || event.key() == GLFW.GLFW_KEY_RIGHT)) {
                int step = event.key() == GLFW.GLFW_KEY_LEFT ? -1 : 1;
                value = (Math.clamp(amount() + step, min, max) - min) / (double) (max - min);
                updateMessage();
                applyValue();
                flush();
                return true;
            }
            boolean handled = super.keyPressed(keyCode, scanCode, modifiers);
            if (handled) flush();
            return handled;
        }

        private void flush() {
            if (dirty) {
                dirty = false;
                save.run();
            }
        }

        @Override
        public void renderWidget(GuiGraphics g, int mx, int my, float dt) {
            Font font = Minecraft.getInstance().font;
            int x = getX(), y = getY() + height / 2, end = x + width - 4;
            if (showValue) {
                int labelX = stackedValue ? x + (width - valueWidth) / 2 : x + valueOffset - valueWidth;
                int labelY = stackedValue ? getY() + 1 : y - 4;
                g.drawString(font, valueText, labelX, labelY, active ? TEXT : DIM, false);
            }
            if (stackedValue) y = getY() + height - 5;
            drawMeter(g, x + 4, y, end, (float) value, active ? ACCENT : DIM, true, isHoveredOrFocused() && active);
        }
    }

    abstract static class MusicScreen extends Screen {
        final MusicMod mod;
        Screen parent;
        private boolean renderingChildren;

        MusicScreen(MusicMod mod, Screen parent, String label) {
            super(Component.literal(label));
            this.mod = mod;
            this.parent = parent;
        }

        @Override
        public boolean isPauseScreen() { return parent != null && parent.isPauseScreen(); }

        @Override
        public void onClose() { minecraft.setScreen(parent); }

        @Override
        public void renderBackground(GuiGraphics g, int mx, int my, float dt) {
            if (renderingChildren) return;
            if (minecraft.level == null) renderPanorama(g, dt);
            g.fill(0, 0, width, height, 0x74121117);
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float dt) {
            // The caller paints the background before applying its interface scale.
            // 1.21.1 Screen.render invokes renderBackground again before its children.
            renderingChildren = true;
            try { super.render(g, mx, my, dt); }
            finally { renderingChildren = false; }
        }

        Control button(Rect r, String label, Icon icon, Style style, boolean showLabel, boolean left, Runnable action) {
            return addRenderableWidget(new Control(r, label, icon, style, showLabel, left, action));
        }

        Control icon(Rect r, String label, Icon icon, Style style, Runnable action) {
            Control control = button(r, label, icon, style, false, false, action);
            control.hint(label);
            return control;
        }
    }

    static final class PlayerScreen extends MusicScreen {
        private enum Tab { HOME, SEARCH, FAVORITES, PLAYLISTS, ALBUMS, ARTISTS, LOCAL, HISTORY, SETTINGS, ACCOUNT }

        private enum Preferences {
            SOUND("Плеер", Icon.SPEAKER), HUD("HUD и меню", Icon.SCREEN), KEYS("Клавиши", Icon.KEY), LYRICS("Текст", Icon.LIST);

            final String label;
            final Icon icon;

            Preferences(String label, Icon icon) {
                this.label = label;
                this.icon = icon;
            }
        }

        private Tab tab = Tab.HOME;
        private enum HomeView { MAIN, LYRICS, VISUALIZER }
        private HomeView homeView = HomeView.MAIN;
        private Tab returnTab = Tab.HOME;
        private Tab historyReturn = Tab.HOME;
        private Preferences preferences = Preferences.SOUND;
        private String query = "", searched = "", notice = "", keyNotice = "";
        private String localQuery = "";
        private boolean choosingFolder;
        private boolean localScanned;
        private List<MusicApi.Track> localTracks = List.of(), localResults = List.of();
        private final Set<String> likedTracks = new HashSet<>();
        private final HudPreview hudPreview = new HudPreview();
        private long creditShownAt = -1;
        private int page, offset, historyOffset, settingsOffset, requestSerial;
        private boolean more, busy, signedIn, loadedPlaylists, historyOpen;
        private Thread request;
        private Thread likeRequest;
        private MusicShortcuts.Action recording;
        private List<MusicApi.Track> results = List.of(), detail = List.of();
        private List<MusicApi.Playlist> playlists = List.of();
        private List<MusicApi.CollectionItem> collection = List.of();
        private MusicApi.Playlist selectedPlaylist;
        private MusicApi.CollectionItem selectedCollection;

        private Rect frame, rail, content, extraHistory, searchField, hero, stats, accountDetails;
        private int footer, listTop, preferenceTop, visiblePreferences, preferenceCount;
        private float interfaceScale = 1;
        private boolean applyInterfaceScale;
        private boolean condensed;
        private int headingReserve;
        private EditBox input, tokenInput;
        private String tokenDraft = "";
        private boolean tokenVisible;
        private Control pageBack, pageNext, wipeSearch, previous, play, next, shuffle, repeat, volumeMute;
        private Control creditCrown, previewPlay;
        private FavoriteControl favorite;
        private Control login, copy, browser;
        private Control editLyrics;
        private Slider footerVolume, soundVolume;
        private Timeline timeline;
        private MusicSettings.Repeat repeatShown;
        private List<MediaRow> rows = List.of(), historyRows = List.of();
        private List<PreferenceRow> settingRows = List.of();

        private record PreferenceRow(Rect bounds, String title, int textWidth, boolean section) {}

        PlayerScreen(MusicMod mod, Screen parent) {
            super(mod, parent, MusicMod.NAME);
            signedIn = mod.api.signedIn();
        }

        @Override
        protected void init() {
            interfaceScale = mod.settings.uiScale / 100f;
            applyInterfaceScale = false;
            int layoutWidth = (int) (width / interfaceScale);
            int layoutHeight = (int) (height / interfaceScale);
            int fw = Math.min(710, layoutWidth - 18);
            int fh = Math.min(420, layoutHeight - 18);
            frame = new Rect((layoutWidth - fw) / 2, (layoutHeight - fh) / 2, fw, fh);
            footer = frame.bottom() - 60;
            condensed = fw < 470;
            rail = new Rect(frame.x + 9, frame.y + 44, condensed ? 35 : 110, footer - frame.y - 51);
            int contentX = rail.right() + 10, contentEnd = frame.right() - 12;
            int bodyY = rail.y + 1, bodyH = footer - bodyY - 9;
            boolean split = historyOpen && canSplit();
            extraHistory = split ? new Rect(contentEnd - 146, bodyY, 146, bodyH) : null;
            int contentW = (split ? extraHistory.x - 9 : contentEnd) - contentX;
            content = new Rect(contentX, bodyY, contentW, bodyH);
            input = tokenInput = null; searchField = null; hero = null; stats = null; accountDetails = null;
            pageBack = pageNext = wipeSearch = null;
            login = copy = browser = null;
            editLyrics = null;
            creditCrown = previewPlay = null;
            soundVolume = null;
            headingReserve = 0;
            repeatShown = null;
            rows = historyRows = List.of();
            settingRows = List.of();

            if (mod.visualizer != null && !showingVisualizer()) mod.visualizer.hide();

            header();
            sidebar();
            transport();
            switch (tab) {
                case HOME -> {
                    if (showingLyrics()) lyricsHome();
                    else if (showingVisualizer()) visualizerHome();
                    else home();
                }
                case SEARCH -> searchPanel();
                case PLAYLISTS, FAVORITES, ALBUMS, ARTISTS -> libraryPanel();
                case LOCAL -> localPanel();
                case HISTORY -> history(content);
                case SETTINGS -> settingsPanel();
                case ACCOUNT -> accountPanel();
            }
            if (extraHistory != null) history(extraHistory);
            if (tab == Tab.LOCAL && !localScanned && !busy) scanLocal();
            update();
        }

        private boolean canSplit() {
            return frame.w >= 535 && tab != Tab.SETTINGS && tab != Tab.ACCOUNT && tab != Tab.HISTORY;
        }

        private void header() {
            int actionRight = frame.right() - 10;
            int y = frame.y + 9;
            icon(new Rect(actionRight - 25, y, 25, 25), "Закрыть плеер", Icon.CLOSE, Style.GHOST, this::closePlayer);
            icon(new Rect(actionRight - 54, y, 25, 25), "Настройки", Icon.GEAR,
                tab == Tab.SETTINGS ? Style.SELECTED : Style.GHOST, () -> secondary(Tab.SETTINGS));
            icon(new Rect(actionRight - 83, y, 25, 25), "Аккаунты", Icon.USER,
                tab == Tab.ACCOUNT ? Style.SELECTED : Style.GHOST, () -> secondary(Tab.ACCOUNT));
            if (frame.w >= 245) {
                creditCrown = addRenderableWidget(new TrustCrown(new Rect(actionRight - 112, y, 25, 25),
                    () -> creditShownAt = Util.getMillis()));
                creditCrown.hint(BuildTrust.crownMessage());
            }
            int left = frame.x + (condensed ? 40 : frame.w < 545 ? 144 : 176);
            int available = actionRight - (creditCrown == null ? 91 : 120) - left;
            int home = navWidth("Главная"), search = navWidth("Поиск"), library = navWidth("Библиотека");
            boolean labels = home + search + library + 8 <= available;
            if (!labels) home = search = library = Math.max(22, Math.min(54, (available - 8) / 3));
            int start = left + Math.max(0, (available - home - search - library - 8) / 2);
            headerTab(new Rect(start, y, home, 25), "Главная", Icon.HOME, tab == Tab.HOME, labels, () -> showHomeView(HomeView.MAIN));
            headerTab(new Rect(start + home + 4, y, search, 25), "Поиск", Icon.SEARCH, tab == Tab.SEARCH, labels, () -> select(Tab.SEARCH));
            headerTab(new Rect(start + home + search + 8, y, library, 25), "Библиотека", Icon.LIBRARY,
                libraryTab(), labels, () -> select(Tab.PLAYLISTS));
        }

        private int navWidth(String label) {
            return Math.max(MusicTheme.width(font, label, Type.STRONG), MusicTheme.width(font, label, Type.BODY)) + 27;
        }

        private void headerTab(Rect rect, String label, Icon icon, boolean selected, boolean showLabel, Runnable action) {
            Control control = button(rect, label, icon, selected ? Style.SELECTED : Style.GHOST, showLabel, false, action);
            if (!showLabel) control.hint(label);
        }

        private void sidebar() {
            boolean visualizerEnabled = mod.visualizer != null && mod.visualizer.enabled();
            int featureSpace = ((mod.lyrics == null ? 0 : 1) + (visualizerEnabled ? 1 : 0)) * 24;
            String[] names = {"Моя папка", "Моя волна", "Любимое", "Плейлисты", "Альбомы", "Исполнители"};
            Icon[] icons = {Icon.LIBRARY, Icon.WAVE, Icon.HEART, Icon.LIST, Icon.DISC, Icon.USER};
            Tab[] tabs = {Tab.LOCAL, Tab.HOME, Tab.FAVORITES, Tab.PLAYLISTS, Tab.ALBUMS, Tab.ARTISTS};
            int step = Math.clamp((rail.h - 38 - featureSpace) / names.length, featureSpace == 0 ? 12 : 10, 26);
            for (int i = 0; i < names.length; i++) {
                Tab target = tabs[i];
                Control control = button(new Rect(rail.x + 4, rail.y + 5 + step * i, rail.w - 8, step - 2),
                    names[i], icons[i], tab == target && (target != Tab.HOME || homeView == HomeView.MAIN)
                        ? Style.SELECTED : Style.GHOST, !condensed, !condensed,
                    () -> { if (target == Tab.HOME) showHomeView(HomeView.MAIN); else select(target); });
                if (condensed) control.hint(names[i]);
            }
            if (mod.lyrics != null) {
                button(new Rect(rail.x + 4, rail.bottom() - 53, rail.w - 8, 21), "Текст песни", Icon.LIST,
                    showingLyrics() ? Style.SELECTED : Style.GHOST, !condensed, !condensed,
                    this::toggleLyrics).hint(showingLyrics() ? "Скрыть текст песни" : "Показать текст песни");
            }
            if (visualizerEnabled) {
                button(new Rect(rail.x + 4, rail.bottom() - (mod.lyrics == null ? 53 : 77), rail.w - 8, 21),
                    "Визуализация", Icon.BARS, showingVisualizer() ? Style.SELECTED : Style.GHOST, !condensed, !condensed,
                    () -> showHomeView(showingVisualizer() ? HomeView.MAIN : HomeView.VISUALIZER))
                    .hint(showingVisualizer() ? "Скрыть визуализацию" : "Открыть визуализацию");
            }
            Control historyControl = button(new Rect(rail.x + 4, rail.bottom() - 27, rail.w - 8, 22), "Недавние", Icon.HISTORY,
                tab == Tab.HISTORY || extraHistory != null ? Style.SELECTED : Style.GHOST, !condensed, !condensed, this::toggleHistory);
            historyControl.hint("Недавние треки");
        }

        boolean showingLyrics() { return mod.lyrics != null && tab == Tab.HOME && homeView == HomeView.LYRICS; }

        private boolean showingVisualizer() {
            return mod.visualizer != null && mod.visualizer.enabled() && tab == Tab.HOME && homeView == HomeView.VISUALIZER;
        }

        void showLyrics() {
            if (mod.lyrics != null) showHomeView(HomeView.LYRICS);
        }

        private void showHomeView(HomeView view) {
            if (tab == Tab.HOME && view == homeView) return;
            homeView = view;
            if (tab != Tab.HOME) select(Tab.HOME);
            else rebuildWidgets();
        }

        void showAccount() { secondary(Tab.ACCOUNT); }

        void showLyricsSettings() {
            if (mod.lyrics == null) return;
            preferences = Preferences.LYRICS;
            if (tab != Tab.SETTINGS) select(Tab.SETTINGS);
            else rebuildWidgets();
        }

        void toggleLyrics() {
            if (showingLyrics()) showHomeView(HomeView.MAIN);
            else showLyrics();
        }

        private void lyricsHome() {
            icon(new Rect(content.right() - 23, content.y, 23, 22), "Настройки текста", Icon.GEAR, Style.FILLED,
                () -> mod.lyrics.configure(this));
            int w = Math.min(108, content.w / 3);
            editLyrics = button(new Rect(content.right() - w - 27, content.y, w, 22), "Редактор", Icon.LIST,
                Style.FILLED, w >= 72, false, () -> mod.lyrics.edit(this));
            editLyrics.hint("Редактор текста для локального трека");
            addRenderableWidget(mod.lyrics.panel(homePanelBounds()));
        }

        private void visualizerHome() {
            headingReserve = mod.visualizer.modeControls(this, content);
            addRenderableWidget(mod.visualizer.panel(homePanelBounds()));
        }

        private Rect homePanelBounds() {
            return new Rect(content.x, content.y + 29, content.w, Math.max(1, content.h - 29));
        }

        private void home() {
            boolean local = !signedIn;
            int heroSize = Math.clamp(content.h * 40 / 100, 43, 127);
            hero = new Rect(content.x, content.y, content.w, heroSize);
            String action = local ? "Фонотека" : "Слушать";
            int actionWidth = Math.min(navWidth(action), hero.w - 24);
            int actionX = hero.h < 64 ? hero.right() - 9 - actionWidth : hero.x + 12;
            int actionY = hero.h < 64 ? hero.y + (hero.h - 21) / 2 : hero.bottom() - 28;
            button(new Rect(actionX, actionY, actionWidth, 21), action, local ? Icon.LIBRARY : Icon.PLAY, Style.PRIMARY,
                true, false, local ? () -> select(Tab.LOCAL) : this::startWave);
            if (local) return;
            int cardsY = homeCardsY();
            int tiles = content.w >= 345 ? 4 : content.w >= 241 ? 3 : 2;
            int tileW = (content.w - 7 * (tiles - 1)) / tiles;
            String[] labels = {tileW >= 96 ? "Мне нравится" : "Любимое", "Minecraft",
                tileW >= 96 ? "Lo-fi для игры" : "Lo-fi", "Для фарма"};
            Icon[] icons = {Icon.HEART, Icon.HOME, Icon.MUSIC, Icon.WAVE};
            Runnable[] actions = {() -> select(Tab.FAVORITES), () -> suggested("C418 Minecraft"),
                () -> suggested("Minecraft lo-fi"), () -> suggested("Gaming electronic")};
            if (cardsY + 17 > content.bottom()) return;
            int tileH = Math.min(52, content.bottom() - cardsY);
            for (int i = 0; i < tiles; i++) {
                button(new Rect(content.x + i * (tileW + 7), cardsY, tileW, tileH), labels[i], icons[i],
                    Style.FILLED, tileW >= 55, false, actions[i]).hint(labels[i]);
            }
        }

        private int homeCardsY() {
            return hero.bottom() + 22;
        }

        private void searchPanel() {
            int y = content.y + 25;
            int tools = Math.min(67, content.w / 2);
            int h = 25;
            searchField = new Rect(content.x, y, content.w - tools - 5, h);
            input = addRenderableWidget(new EditBox(font, searchField.x + 8, y + (h - 9) / 2, searchField.w - 30, 13,
                Component.literal("Трек или исполнитель")));
            input.setBordered(false);
            input.setTextColor(TEXT);
            input.setHint(styled("Трек или исполнитель", Type.BODY));
            input.setMaxLength(160);
            input.setValue(query);
            input.setResponder(value -> query = value);
            wipeSearch = icon(new Rect(searchField.right() - 21, y + (h - 18) / 2, 18, 18),
                "Очистить поиск", Icon.CLOSE, Style.GHOST, this::clearSearch);
            button(new Rect(searchField.right() + 5, y, tools, h), "Найти", Icon.SEARCH,
                Style.PRIMARY, tools >= 56, false, () -> submit(0, query));
            pageNext = icon(new Rect(content.right() - 22, content.y, 22, 19), "Следующая страница", Icon.RIGHT,
                Style.FILLED, () -> submit(page + 1, searched));
            pageBack = icon(new Rect(content.right() - 47, content.y, 22, 19), "Предыдущая страница", Icon.LEFT,
                Style.FILLED, () -> submit(page - 1, searched));
            listTop = y + h + 8;
            if (searched.isEmpty() && !busy && notice.isEmpty() && content.h >= 108) {
                int tileY = listTop + 27, w = (content.w - 6) / 2;
                button(new Rect(content.x, tileY, w, 21), "C418", Icon.MUSIC,
                    Style.FILLED, true, false, () -> suggested("C418"));
                button(new Rect(content.x + w + 6, tileY, w, 21), "Minecraft lo-fi", Icon.MUSIC,
                    Style.FILLED, true, false, () -> suggested("Minecraft lo-fi"));
            }
            rows = mediaRows(new Rect(content.x, listTop, content.w, content.bottom() - listTop), false);
        }

        private void localPanel() {
            boolean configured = !mod.settings.localFolder.isBlank();
            int right = content.right();
            int browseWidth = Math.min(108, content.w / 2 - 4);
            button(new Rect(right - browseWidth, content.y, browseWidth, 21), "Выбрать папку", Icon.DISC, Style.PRIMARY,
                browseWidth >= 93, false, this::chooseFolder).hint("Выбрать папку с музыкой");
            icon(new Rect(right - browseWidth - 26, content.y, 22, 21), "Обновить папку",
                Icon.REFRESH, Style.FILLED, this::scanLocal).active = configured;
            headingReserve = browseWidth + 30;
            int y = content.y + 28;
            if (configured) {
                searchField = new Rect(content.x, y, content.w, 23);
                input = addRenderableWidget(new EditBox(font, content.x + 8, y + 6, content.w - 16, 13,
                    Component.literal("Поиск по папке")));
                input.setBordered(false);
                input.setTextColor(TEXT);
                input.setHint(styled("Поиск по папке", Type.BODY));
                input.setMaxLength(160);
                input.setValue(localQuery);
                input.setResponder(value -> {
                    localQuery = value;
                    filterLocal();
                    offset = 0;
                    update();
                });
                y += 30;
            }
            listTop = y;
            rows = mediaRows(new Rect(content.x, listTop, content.w, content.bottom() - listTop), false);
        }

        private void chooseFolder() {
            if (choosingFolder) return;
            choosingFolder = true;
            String initial = mod.settings.localFolder.isBlank() ? System.getProperty("user.home") : mod.settings.localFolder;
            Thread.startVirtualThread(() -> {
                String path = null;
                boolean failed = false;
                try {
                    path = TinyFileDialogs.tinyfd_selectFolderDialog(MusicMod.NAME, initial);
                } catch (RuntimeException | LinkageError error) {
                    failed = true;
                    MusicMod.LOG.warn("Could not open the folder picker", error);
                }
                String selected = path;
                boolean unavailable = failed;
                minecraft.execute(() -> {
                    choosingFolder = false;
                    if (tab != Tab.LOCAL) return;
                    if (unavailable) notice = "Диалог недоступен. Перетащи папку в окно игры.";
                    else if (selected != null) useFolder(Path.of(selected));
                });
            });
        }

        @Override
        public void onFilesDrop(List<Path> paths) {
            if (tab != Tab.LOCAL || paths.isEmpty()) return;
            Path first = paths.getFirst();
            if (java.nio.file.Files.isDirectory(first)) useFolder(first);
            else notice = "Перетащи папку с музыкой.";
        }

        private void useFolder(Path path) {
            cancelRequest();
            mod.settings.localFolder = path.toAbsolutePath().normalize().toString();
            mod.settings.save();
            localQuery = notice = "";
            localTracks = localResults = List.of();
            localScanned = false;
            rebuildWidgets();
        }

        private void scanLocal() {
            if (mod.settings.localFolder.isBlank()) return;
            String directory = mod.settings.localFolder;
            load(() -> LocalMusic.scan(Path.of(directory)), files -> {
                localTracks = files;
                filterLocal();
                offset = 0;
            });
            localScanned = true;
        }

        private void filterLocal() {
            String query = localQuery.strip().toLowerCase(Locale.ROOT);
            localResults = query.isEmpty() ? localTracks : localTracks.stream()
                .filter(track -> (track.artist() + " " + track.title()).toLowerCase(Locale.ROOT).contains(query)).toList();
        }

        private void libraryPanel() {
            listTop = content.y + 26;
            if (selectedPlaylist != null || selectedCollection != null) {
                icon(new Rect(content.right() - 23, content.y, 23, 20), "Назад к коллекции", Icon.BACK, Style.FILLED, this::clearDetail);
                headingReserve = 28;
            } else if (!mod.api.signedIn()) {
                button(new Rect(content.x, listTop + 25, 68, 21), "Войти", Icon.USER, Style.PRIMARY,
                    true, false, () -> secondary(Tab.ACCOUNT));
            } else {
                icon(new Rect(content.right() - 23, content.y, 23, 20), "Обновить коллекцию", Icon.REFRESH, Style.FILLED, this::refreshLibrary);
                headingReserve = 28;
            }
            rows = mediaRows(new Rect(content.x, listTop, content.w, content.bottom() - listTop), false);
        }

        private void history(Rect area) {
            boolean split = extraHistory != null;
            Rect box = split ? new Rect(area.x + 7, area.y + 28, area.w - 14, area.h - 33)
                : new Rect(area.x, area.y + 28, area.w, area.h - 28);
            historyRows = mediaRows(box, true);
            if (split) {
                icon(new Rect(area.right() - 23, area.y + 5, 17, 18), "Закрыть недавние", Icon.CLOSE, Style.GHOST, this::toggleHistory);
            }
        }

        private List<MediaRow> mediaRows(Rect area, boolean history) {
            List<MediaRow> widgets = new ArrayList<>();
            int count = Math.max(0, area.h / ROW);
            for (int i = 0; i < count; i++) {
                int row = i, y = area.y + i * ROW;
                widgets.add(addRenderableWidget(new MediaRow(new Rect(area.x, y, area.w - 5, ROW - 3),
                    history ? () -> playRecent(historyOffset + row) : () -> playRow(offset + row))));
            }
            return widgets;
        }

        private void transport() {
            int y = footer + 7;
            int center = frame.x + frame.w / 2;
            int gap = condensed ? 29 : 34;
            int volumeW = condensed ? 40 : 52 + Slider.valueSpace("%");
            int right = frame.right() - 10;
            favorite = addRenderableWidget(new FavoriteControl(new Rect(center - gap - 38, y + 3, 22, 22), this::like));
            previous = icon(new Rect(center - gap - 11, y + 3, 22, 22), "Предыдущий трек", Icon.PREVIOUS, Style.GHOST, mod.player::previous);
            play = icon(new Rect(center - 14, y, 28, 28), "Воспроизвести / пауза", Icon.PLAY, Style.TRANSPORT, () -> {
                if (mod.player.track() == null && !mod.player.hasNext()) {
                    if (tab == Tab.LOCAL || !signedIn) {
                        if (tab != Tab.LOCAL || localResults.isEmpty()) select(Tab.LOCAL);
                        else playRow(0);
                    } else startWave();
                } else mod.player.toggle();
            });
            next = icon(new Rect(center + gap - 11, y + 3, 22, 22), "Следующий трек", Icon.NEXT, Style.GHOST, mod.player::next);
            shuffle = icon(new Rect(center + gap + 16, y + 3, 22, 22), "Перемешать список", Icon.SHUFFLE,
                Style.GHOST, mod.player::shuffleUpcoming);
            repeat = icon(new Rect(center + gap + 43, y + 3, 22, 22), "Режим повтора", Icon.REPEAT,
                Style.GHOST, () -> {
                    var options = MusicSettings.Repeat.values();
                    mod.settings.repeat = options[(mod.settings.repeat.ordinal() + 1) % options.length];
                    mod.settings.save();
                    update();
                });
            if (condensed) {
                favorite.visible = frame.w >= 350;
            }
            icon(new Rect(right - 22, y + 3, 22, 22), "Недавние", Icon.HISTORY,
                tab == Tab.HISTORY || extraHistory != null ? Style.SELECTED : Style.GHOST, this::toggleHistory).hint("Недавние треки");
            footerVolume = volume(new Rect(right - 26 - volumeW, y + 3, volumeW, 22));
            addRenderableWidget(condensed ? footerVolume.stackValue() : footerVolume);
            volumeMute = icon(new Rect(right - 50 - volumeW, y + 3, 21, 22), "Выключить / включить звук", Icon.SPEAKER,
                Style.GHOST, mod::toggleMute);
            repeat.visible = frame.w >= 505 && repeat.getRight() + 3 <= volumeMute.getX();
            timeline = addRenderableWidget(new Timeline(
                new Rect(frame.x + 15, footer + 36, frame.w - 30, 14), mod.player));
        }

        private Slider volume(Rect bounds) {
            return new Slider(bounds, "Громкость", "%", shownVolume(), 0, 100, true,
                mod::setVolume, mod.settings::save).fineSteps();
        }

        private int shownVolume() {
            return mod.settings.muted ? 0 : mod.settings.volume;
        }

        private void settingsPanel() {
            int sections = mod.lyrics == null ? 3 : 4;
            int count = sections + (mod.extension == null ? 0 : 1);
            int w = Math.min(101, (content.w - 5 * (count - 1)) / count);
            for (int i = 0; i < sections; i++) {
                Preferences section = Preferences.values()[i];
                button(new Rect(content.x + i * (w + 5), content.y + 26, w, 22),
                    section.label, section.icon, preferences == section ? Style.SELECTED : Style.FILLED, w >= 64, false, () -> {
                        preferences = section;
                        recording = null;
                        keyNotice = "";
                        settingsOffset = 0;
                        rebuildWidgets();
                    }).hint(section.label);
            }
            if (mod.extension != null) {
                String label = mod.extension.settingsLabel();
                button(new Rect(content.x + sections * (w + 5), content.y + 26, w, 22),
                    label, Icon.USER, Style.FILLED, w >= 64, false, () -> mod.extension.openSettings(this)).hint(label);
            }
            preferenceTop = content.y + 56;
            int width = content.w;
            if (preferences == Preferences.HUD && width >= 372) {
                stats = new Rect(content.right() - 151, preferenceTop, 151, 98);
                width -= stats.w + 9;
                previewPlay = icon(new Rect(stats.right() - 25, stats.y + 4, 20, 20), "Воспроизвести / приостановить предпросмотр",
                    hudPreview.paused() ? Icon.PLAY : Icon.PAUSE, Style.GHOST, () -> {
                        hudPreview.toggle();
                        previewPlay.icon = hudPreview.paused() ? Icon.PLAY : Icon.PAUSE;
                    });
                button(new Rect(stats.x + 9, stats.bottom() - 29, stats.w - 18, 20), "Переместить HUD", Icon.SCREEN,
                    Style.FILLED, true, false, this::positionHud);
            }
            List<Preference> options = switch (preferences) {
                case SOUND -> soundSettings();
                case HUD -> hudSettings();
                case KEYS -> shortcutSettings();
                case LYRICS -> mod.lyrics.settings(this);
            };
            preferenceCount = options.size();
            int statusRoom = preferences == Preferences.KEYS ? 15 : 0;
            visiblePreferences = Math.max(0, (content.bottom() - preferenceTop - statusRoom) / SETTING);
            settingsOffset = Math.clamp(settingsOffset, 0, Math.max(0, preferenceCount - visiblePreferences));
            int end = Math.min(preferenceCount, settingsOffset + visiblePreferences);
            var list = new ArrayList<PreferenceRow>();
            for (int i = settingsOffset; i < end; i++) {
                Preference option = options.get(i);
                Rect row = new Rect(content.x, preferenceTop + (i - settingsOffset) * SETTING, width, SETTING - 3);
                int before = children().size();
                if (option.layout != null) option.layout.accept(row);
                int firstControlX = row.right();
                for (int child = before; child < children().size(); child++) {
                    if (children().get(child) instanceof AbstractWidget widget) firstControlX = Math.min(firstControlX, widget.getX());
                }
                list.add(new PreferenceRow(row, option.title, firstControlX - row.x - 15, option.layout == null));
            }
            settingRows = List.copyOf(list);
        }

        private List<Preference> soundSettings() {
            var s = mod.settings;
            List<Preference> options = new ArrayList<>(List.of(
                new Preference("Язык", row -> valueButton(row, MusicI18n.languageLabel(s.language), Icon.REFRESH, false, () -> {
                    s.language = MusicI18n.nextLanguage(s.language);
                    MusicI18n.use(s.language);
                    s.save();
                    rebuildWidgets();
                })),
                new Preference("Размер интерфейса", row -> addRenderableWidget(new Slider(sliderArea(row), "Размер интерфейса",
                    "%", s.uiScale, MusicSettings.MIN_UI_SCALE, 100, true, v -> s.uiScale = v, () -> {
                        s.save();
                        applyInterfaceScale = true;
                    }))),
                new Preference("Громкость", row -> soundVolume = addRenderableWidget(volume(sliderArea(row)))),
                new Preference("Отключить звук", row -> toggle(row, s.muted, mod::toggleMute)),
                new Preference(MusicSettings.QUALITY_LABEL, row -> valueButton(row, s.yandexQuality.label, Icon.REFRESH, false,
                    () -> { s.cycleQuality(); rebuildWidgets(); }).hint(MusicSettings.QUALITY_HINT)),
                new Preference("Автопереход", row -> toggle(row, s.autoAdvance, () -> s.autoAdvance = !s.autoAdvance)),
                new Preference("Повтор", row -> segments(row, new String[]{"Выкл.", "Трек", "Список"}, s.repeat.ordinal(),
                    i -> change(() -> s.repeat = MusicSettings.Repeat.values()[i]))),
                new Preference("Звуки кнопок", row -> toggle(row, s.uiSounds, () -> s.uiSounds = !s.uiSounds))
            ));
            if (mod.visualizer != null) options.add(new Preference("Визуализатор", row ->
                addRenderableWidget(new Toggle(new Rect(row.right() - 32, row.y() + 4, 28, 15), mod.visualizer.enabled(), () -> {
                    boolean enabled = !mod.visualizer.enabled();
                    mod.visualizer.setEnabled(enabled);
                    if (!enabled && homeView == HomeView.VISUALIZER) homeView = HomeView.MAIN;
                    rebuildWidgets();
                })).hint("Показывать вкладку визуализации")));
            return options;
        }

        private List<Preference> hudSettings() {
            var s = mod.settings;
            List<Preference> options = new ArrayList<>(List.of(
                new Preference("Показ HUD", this::hudModes),
                new Preference("Размер HUD", row -> settingSlider(row, "Размер HUD", "%", s.hudScale, 60, 150, v -> s.hudScale = v)),
                new Preference("Размер текста", row -> settingSlider(row, "Размер текста", "%", s.hudTextScale, 70, 110, v -> s.hudTextScale = v)),
                new Preference("Непрозрачность", row -> settingSlider(row, "Непрозрачность", "%", s.hudOpacity, 40, 100, v -> s.hudOpacity = v)),
                new Preference("Время показа", row -> {
                    Slider slider = settingSlider(row, "Время показа", " с", s.seconds, 2, 20, v -> s.seconds = v);
                    slider.active = s.hud == MusicSettings.HudMode.CHANGE;
                }),
                new Preference("Исполнитель", row -> toggle(row, s.showArtist, () -> s.showArtist = !s.showArtist)),
                new Preference("Мини-плеер в паузе", row -> toggle(row, s.miniPlayer, () -> s.miniPlayer = !s.miniPlayer))
            ));
            if (stats == null) {
                options.add(new Preference("Расположение HUD", row -> valueButton(row, "Переместить", Icon.SCREEN, false, this::positionHud)));
            }
            return options;
        }

        private void hudModes(Rect row) {
            var modes = MusicSettings.HudMode.values();
            String[] shortLabels = {"Скрыть", "При смене", "Всегда"};
            int left = row.x + MusicTheme.width(font, "Показ HUD", Type.BODY) + 21;
            int room = row.right() - 4 - left;
            int fullWidth = 0;
            for (var mode : modes) fullWidth += MusicTheme.width(font, mode.label, Type.STRONG) + 16;
            int x = row.right() - 4;
            for (int i = modes.length - 1; i >= 0; i--) {
                var mode = modes[i];
                String label = room >= fullWidth ? mode.label : shortLabels[i];
                int w = Math.max(26, Math.min(MusicTheme.width(font, label, Type.STRONG) + 13,
                    x - left - i * 28));
                x -= w;
                button(new Rect(x, row.y + 3, w, row.h - 6), label, null,
                    mod.settings.hud == mode ? Style.SELECTED : Style.FILLED, true, false,
                    () -> { mod.settings.setHud(mode); rebuildWidgets(); }).hint(mode.label);
                x -= 3;
            }
        }

        private List<Preference> shortcutSettings() {
            icon(new Rect(content.right() - 24, content.y, 24, 22), "Вернуть сочетания по умолчанию", Icon.REFRESH, Style.FILLED, () -> {
                recording = null;
                keyNotice = "Сочетания восстановлены.";
                change(() -> mod.settings.shortcuts = MusicShortcuts.defaults());
            });
            headingReserve = 29;
            List<Preference> options = new ArrayList<>();
            options.add(new Preference("Открыть плеер", row -> valueButton(row, mod.openKeyLabel(), Icon.RIGHT, false,
                () -> minecraft.setScreen(new KeyBindsScreen(this, minecraft.options)))));
            for (MusicShortcuts.Action action : MusicShortcuts.Action.values()) {
                if (action.requiresLyrics() && mod.lyrics == null) continue;
                if (action == MusicShortcuts.Action.EDITOR_BACK) {
                    options.add(new Preference("Горячие клавиши редактора", null));
                    options.add(new Preference("Пауза / продолжить", row -> {
                        String binding = mod.settings.shortcuts.get(MusicShortcuts.Action.PLAY).label();
                        int w = Math.min(125, row.w * 47 / 100);
                        button(new Rect(row.right() - w - 5, row.y + 3, w, 17), binding, null, Style.FILLED,
                            true, false, () -> {}).active = false;
                    }));
                }
                options.add(new Preference(action.label, row -> {
                    var binding = mod.settings.shortcuts.get(action);
                    boolean waiting = recording == action;
                    int valueW = Math.min(125, row.w * 47 / 100), x = row.right() - valueW - 25;
                    button(new Rect(x, row.y + 3, valueW, 17), waiting ? "Запись…" : binding.label(), null,
                        waiting ? Style.SELECTED : Style.FILLED, true, false, () -> {
                            recording = action;
                            keyNotice = "Нажми клавишу или кнопку мыши. Esc — отмена.";
                            rebuildWidgets();
                        }).hint(action.label + " · " + (waiting ? "Нажми клавишу или кнопку мыши" : binding.label()));
                    Control erase = icon(new Rect(row.right() - 23, row.y + 3, 19, 17),
                        "Снять назначение: " + action.label, Icon.CLOSE, Style.GHOST, () -> saveBinding(action, MusicShortcuts.unbound()));
                    erase.active = binding.assigned();
                }));
            }
            return options;
        }

        private Slider settingSlider(Rect row, String name, String suffix, int value, int low, int high, IntConsumer change) {
            return addRenderableWidget(new Slider(sliderArea(row), name, suffix, value, low, high, true, change, mod.settings::save));
        }

        static Rect sliderArea(Rect row) {
            int w = Math.min(157, row.w * 48 / 100);
            return new Rect(row.right() - w - 5, row.y + 1, w, row.h - 2);
        }

        Control valueButton(Rect row, String label, Icon icon, boolean selected, Runnable action) {
            int w = Math.min(112, row.w * 48 / 100);
            Control control = button(new Rect(row.right() - w - 4, row.y + 3, w, row.h - 6), label, icon,
                selected ? Style.SELECTED : Style.FILLED, true, false, action);
            control.hint(label);
            return control;
        }

        <T extends AbstractWidget> T preferenceWidget(T widget) { return addRenderableWidget(widget); }

        void refreshPreferences() { rebuildWidgets(); }

        private void segments(Rect row, String[] labels, int selected, IntConsumer action) {
            int gap = 3;
            int w = Math.min(53, (row.w * 61 / 100 - gap * (labels.length - 1)) / labels.length);
            int x = row.right() - 4 - w * labels.length - gap * (labels.length - 1);
            for (int i = 0; i < labels.length; i++) {
                int option = i;
                button(new Rect(x + (w + gap) * i, row.y + 3, w, row.h - 6), labels[i], null,
                    selected == i ? Style.SELECTED : Style.FILLED, true, false, () -> action.accept(option)).hint(labels[i]);
            }
        }

        private void toggle(Rect row, boolean enabled, Runnable action) {
            addRenderableWidget(new Toggle(new Rect(row.right() - 32, row.y + 4, 28, 15),
                enabled, () -> change(action))).hint(enabled ? "Выключить" : "Включить");
        }

        private void change(Runnable action) {
            action.run();
            mod.settings.save();
            rebuildWidgets();
        }

        private void positionHud() {
            minecraft.setScreen(new PositionScreen(mod, this, hudPreview));
        }

        private void accountPanel() {
            accountDetails = new Rect(content.x, content.y + 26, content.w, content.h - 26);
            if (!mod.api.signedIn()) {
                int fieldY = accountDetails.y + 43;
                int loginWidth = Math.min(82, Math.max(62, accountDetails.w / 5));
                int showWidth = 28;
                Rect field = new Rect(accountDetails.x + 8, fieldY,
                    accountDetails.w - loginWidth - showWidth - 25, 22);
                tokenInput = addRenderableWidget(new EditBox(font, field.x + 7, field.y + 6, field.w - 14, 12,
                    Component.literal("Токен Яндекс Музыки")));
                tokenInput.setBordered(false);
                tokenInput.setTextColor(TEXT);
                tokenInput.setHint(styled("Введи или вставь токен", Type.BODY));
                tokenInput.setMaxLength(8192);
                tokenInput.setValue(tokenDraft);
                tokenInput.setResponder(value -> tokenDraft = value);
                tokenInput.setFormatter((value, cursor) -> net.minecraft.util.FormattedCharSequence.forward(
                    tokenVisible ? value : "•".repeat(value.length()), net.minecraft.network.chat.Style.EMPTY));
                icon(new Rect(field.right() + 4, fieldY, showWidth, 22),
                    tokenVisible ? "Скрыть токен" : "Показать токен", tokenVisible ? Icon.CLOSE : Icon.USER,
                    Style.FILLED, () -> {
                        tokenVisible = !tokenVisible;
                        rebuildWidgets();
                    });
                button(new Rect(field.right() + showWidth + 8, fieldY, loginWidth, 22), "Войти", Icon.RIGHT,
                    Style.PRIMARY, true, false, () -> {
                        String value = tokenDraft;
                        tokenDraft = "";
                        tokenInput.setValue("");
                        mod.signInWithToken(value);
                    }).hint("Проверить токен и сохранить его через защиту Windows");
            }
            int buttonY = accountDetails.y + (mod.api.signedIn() ? 45 : 109);
            int actions = Math.min(content.w - 8, 355);
            int loginWidth = Math.min(117, actions / 3);
            int pair = (actions - loginWidth - 8) / 2;
            login = button(new Rect(content.x + 4, buttonY, loginWidth, 22), "Вход по коду", Icon.USER,
                Style.PRIMARY, true, false, () -> {
                    if (mod.api.signedIn()) mod.signOut();
                    else if (mod.authorizing()) mod.cancelAuth();
                    else mod.beginAuth();
                    updateAccount();
                });
            login.hint("Дополнительный вход. Используй его только в официальной сборке King220l");
            copy = button(new Rect(content.x + loginWidth + 8, buttonY, pair, 22), "Копировать код", null,
                Style.FILLED, true, false, () -> {
                    if (mod.deviceCode == null) return;
                    minecraft.keyboardHandler.setClipboard(mod.deviceCode.code());
                    mod.authStatus = "Код скопирован.";
                });
            browser = button(new Rect(content.x + loginWidth + pair + 12, buttonY, pair, 22), "Открыть Яндекс", Icon.RIGHT,
                Style.SELECTED, true, false, () -> Util.getPlatform().openUri(URI.create(MusicMod.AUTH_URL)));
        }

        private void updateAccount() {
            if (login == null) return;
            copy.visible = browser.visible = mod.deviceCode != null;
            login.setLabel(mod.api.signedIn() ? "Выйти" : mod.authorizing() ? "Отменить вход" : "Вход по коду");
        }

        private boolean libraryTab() {
            return switch (tab) {
                case FAVORITES, PLAYLISTS, ALBUMS, ARTISTS -> true;
                default -> false;
            };
        }

        private void secondary(Tab target) {
            if (tab != Tab.SETTINGS && tab != Tab.ACCOUNT) returnTab = tab;
            select(target);
        }

        private void toggleHistory() {
            if (tab == Tab.HISTORY) {
                select(historyReturn);
            } else if (canSplit()) {
                historyOpen = !historyOpen;
                historyOffset = 0;
                rebuildWidgets();
            } else {
                historyReturn = tab;
                select(Tab.HISTORY);
            }
        }

        private void select(Tab next) {
            if (next == tab) return;
            cancelRequest();
            tab = next;
            offset = 0;
            settingsOffset = 0;
            notice = "";
            keyNotice = "";
            recording = null;
            selectedPlaylist = null;
            selectedCollection = null;
            detail = List.of();
            if (next == Tab.ALBUMS || next == Tab.ARTISTS) collection = List.of();
            if (libraryTab()) fetchLibrary();
            rebuildWidgets();
        }

        private void refreshLibrary() {
            loadedPlaylists = false;
            offset = 0;
            fetchLibrary();
        }

        private void clearDetail() {
            cancelRequest();
            selectedPlaylist = null;
            selectedCollection = null;
            detail = List.of();
            offset = 0;
            notice = "";
            rebuildWidgets();
        }

        private void fetchLibrary() {
            if (!mod.api.signedIn()) return;
            switch (tab) {
                case FAVORITES -> load(() -> {
                    var account = mod.api.account();
                    return mod.api.playlist(new MusicApi.Playlist(account.id(), "3", "Мне нравится", -1));
                }, tracks -> {
                    detail = tracks;
                    likedTracks.clear();
                    for (var track : tracks) likedTracks.add(track.id());
                });
                case PLAYLISTS -> {
                    if (!loadedPlaylists) load(() -> mod.api.playlists(mod.api.account()), items -> {
                        playlists = items;
                        loadedPlaylists = true;
                    });
                }
                case ALBUMS, ARTISTS -> {
                    boolean albums = tab == Tab.ALBUMS;
                    load(() -> mod.api.collection(mod.api.account(), albums), items -> collection = items);
                }
                default -> { }
            }
        }

        private void startWave() {
            if (mod.follows()) {
                notice = "Сейчас треками управляет ведущий комнаты.";
                return;
            }
            if (!mod.api.signedIn()) {
                secondary(Tab.ACCOUNT);
                return;
            }
            select(Tab.HOME);
            load(mod.player::loadWave, mod.player::playWave);
        }

        private void suggested(String value) {
            query = value;
            select(Tab.SEARCH);
            submit(0, value);
        }

        private void clearSearch() {
            cancelRequest();
            query = searched = notice = "";
            page = offset = 0;
            more = false;
            results = List.of();
            rebuildWidgets();
            if (input != null) setFocused(input);
        }

        private void submit(int pageNumber, String text) {
            String value = text.strip();
            if (value.isEmpty() || pageNumber < 0) return;
            if (!value.equals(searched)) {
                results = List.of();
                page = 0;
                more = false;
            }
            load(() -> mod.api.search(value, pageNumber), found -> {
                searched = value;
                page = pageNumber;
                more = found.more();
                results = found.tracks();
                offset = 0;
            });
            rebuildWidgets();
        }

        private List<MusicApi.Track> trackList() {
            if (tab == Tab.LOCAL) return localResults;
            if (tab == Tab.SEARCH) return results;
            if (tab == Tab.FAVORITES || selectedPlaylist != null || selectedCollection != null) return detail;
            return List.of();
        }

        private int mainCount() {
            if (tab == Tab.PLAYLISTS && selectedPlaylist == null) return playlists.size();
            if ((tab == Tab.ALBUMS || tab == Tab.ARTISTS) && selectedCollection == null) return collection.size();
            return trackList().size();
        }

        private void playRow(int index) {
            var tracks = trackList();
            if (index < tracks.size()) {
                var track = tracks.get(index);
                if (playbackBlocked(track)) return;
                if (tab == Tab.LOCAL || tab == Tab.PLAYLISTS || tab == Tab.FAVORITES) mod.player.play(tracks, index);
                else mod.player.playRelated(track);
            } else if (tab == Tab.PLAYLISTS && selectedPlaylist == null && index < playlists.size()) {
                selectedPlaylist = playlists.get(index);
                var playlist = selectedPlaylist;
                detail = List.of();
                offset = 0;
                load(() -> mod.api.playlist(playlist), items -> detail = items);
                rebuildWidgets();
            } else if ((tab == Tab.ALBUMS || tab == Tab.ARTISTS) && selectedCollection == null && index < collection.size()) {
                selectedCollection = collection.get(index);
                var item = selectedCollection;
                detail = List.of();
                offset = 0;
                load(() -> mod.api.collectionTracks(item), items -> detail = items);
                rebuildWidgets();
            }
        }

        private boolean playbackBlocked(MusicApi.Track track) {
            if (mod.follows()) notice = "Сейчас треками управляет ведущий комнаты.";
            else if (mod.inRoom() && track.file() != null) notice = "В комнате доступны только треки Яндекс Музыки.";
            else return false;
            return true;
        }

        private void playRecent(int index) {
            var tracks = mod.player.recentTracks();
            if (index >= tracks.size()) return;
            var track = tracks.get(index);
            if (!playbackBlocked(track)) mod.player.playRelated(track);
        }

        private void like() {
            if (mod.player.track() == null || mod.player.track().file() != null || likeRequest != null) return;
            if (!mod.api.signedIn()) {
                secondary(Tab.ACCOUNT);
                return;
            }
            var track = mod.player.track();
            likeRequest = MusicMod.request(() -> mod.api.toggleLike(mod.api.account(), track), enabled -> {
                if (enabled) likedTracks.add(track.id());
                else likedTracks.remove(track.id());
                if (track == mod.player.track()) favorite.mark(enabled, true);
                likeRequest = null;
                if (tab == Tab.FAVORITES) fetchLibrary();
            }, error -> {
                likeRequest = null;
                notice = error;
            });
        }

        private <T> void load(Callable<T> work, Consumer<T> success) {
            cancelRequest();
            busy = true;
            notice = "";
            int serial = requestSerial;
            request = MusicMod.request(work, value -> {
                if (serial != requestSerial) return;
                busy = false;
                request = null;
                success.accept(value);
                update();
            }, error -> {
                if (serial != requestSerial) return;
                busy = false;
                request = null;
                notice = error;
                update();
            });
        }

        private void cancelRequest() {
            requestSerial++;
            if (tab == Tab.LOCAL && busy) localScanned = false;
            if (request != null) request.interrupt();
            request = null;
            busy = false;
        }

        @Override
        public void tick() {
            if (applyInterfaceScale) rebuildWidgets();
            boolean now = mod.api.signedIn();
            if (now != signedIn) {
                signedIn = now;
                likedTracks.clear();
                playlists = List.of();
                collection = List.of();
                detail = List.of();
                loadedPlaylists = false;
                selectedPlaylist = null;
                selectedCollection = null;
                if (libraryTab()) {
                    cancelRequest();
                    fetchLibrary();
                    rebuildWidgets();
                } else if (tab == Tab.HOME) rebuildWidgets();
            }
            update();
        }

        private void update() {
            var player = mod.player;
            boolean follows = mod.follows();
            previous.active = player.hasPrevious() && !follows;
            next.active = player.hasNext() && !follows;
            play.active = repeat.active = !follows;
            shuffle.visible = !player.playingWave() && (!condensed || frame.w >= 350);
            shuffle.active = player.upcomingCount() > 1 && !follows;
            if (repeatShown != mod.settings.repeat) {
                repeatShown = mod.settings.repeat;
                repeat.style = repeatShown == MusicSettings.Repeat.OFF ? Style.GHOST : Style.SELECTED;
                repeat.hint("Повтор: " + repeatShown.label);
            }
            play.icon = (player.started() || player.seeking()) && !player.paused() ? Icon.PAUSE : Icon.PLAY;
            favorite.active = player.track() != null && player.track().file() == null && likeRequest == null;
            if (editLyrics != null) editLyrics.visible = mod.lyrics.canEdit();
            favorite.mark(player.track() != null && likedTracks.contains(player.track().id()), false);
            volumeMute.icon = mod.settings.muted || mod.settings.volume == 0 ? Icon.MUTED : Icon.SPEAKER;
            timeline.locked = follows;
            timeline.sync();
            footerVolume.sync(shownVolume());
            if (soundVolume != null) soundVolume.sync(shownVolume());
            if (pageBack != null) {
                pageBack.visible = pageNext.visible = !searched.isEmpty();
                pageBack.active = !busy && page > 0;
                pageNext.active = !busy && more;
                wipeSearch.visible = !query.isEmpty();
            }
            updateAccount();

            int count = mainCount();
            offset = Math.clamp(offset, 0, Math.max(0, count - rows.size()));
            for (int i = 0; i < rows.size(); i++) {
                MediaRow row = rows.get(i);
                int index = offset + i;
                row.visible = index < count;
                if (!row.visible) continue;
                if (tab == Tab.PLAYLISTS && selectedPlaylist == null) {
                    var entry = playlists.get(index);
                    row.show(entry.title(), entry.count() < 0 ? "" : trackCount(entry.count()), "", Icon.LIST, false);
                } else if ((tab == Tab.ALBUMS || tab == Tab.ARTISTS) && selectedCollection == null) {
                    var entry = collection.get(index);
                    row.show(entry.title(), "", "", entry.album() ? Icon.DISC : Icon.USER, false);
                } else {
                    showTrack(row, trackList().get(index), player, true);
                }
            }

            var recent = player.recentTracks();
            historyOffset = Math.clamp(historyOffset, 0, Math.max(0, recent.size() - historyRows.size()));
            for (int i = 0; i < historyRows.size(); i++) {
                MediaRow row = historyRows.get(i);
                int index = historyOffset + i;
                row.visible = index < recent.size();
                if (!row.visible) continue;
                var track = recent.get(index);
                row.active = !follows && (!mod.inRoom() || track.file() == null);
                showTrack(row, track, player, tab == Tab.HISTORY);
            }
        }

        private static void showTrack(MediaRow row, MusicApi.Track track, MusicPlayer player, boolean duration) {
            String length = duration && track.durationMs() > 0 ? time(track.durationMs()) : "";
            boolean playing = player.track() != null && player.track().id().equals(track.id());
            row.show(track.title(), track.artist(), length, Icon.MUSIC, playing);
        }

        boolean recording() { return recording != null; }

        boolean typing() {
            return input != null && input.isFocused() || tokenInput != null && tokenInput.isFocused();
        }

        void record(MusicShortcuts.Binding binding) {
            if (!binding.assigned() || !binding.mouse() && MusicShortcuts.modifierKey(binding.key())) return;
            if (!binding.mouse() && binding.modifiers() == 0) {
                if (binding.key() == GLFW.GLFW_KEY_ESCAPE) {
                    recording = null;
                    keyNotice = "Запись отменена.";
                    rebuildWidgets();
                    return;
                }
                if (binding.key() == GLFW.GLFW_KEY_DELETE || binding.key() == GLFW.GLFW_KEY_BACKSPACE) {
                    saveBinding(recording, MusicShortcuts.unbound());
                    return;
                }
            }
            if (mod.matchesOpenKey(binding)) {
                keyNotice = "Эта клавиша назначена на открытие плеера.";
                return;
            }
            if (recording.editorOnly() && binding.mouse() && binding.modifiers() == 0
                    && binding.key() <= GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                keyNotice = "Для левой и правой кнопок добавь Ctrl, Shift или Alt.";
                return;
            }
            for (MusicShortcuts.Action action : MusicShortcuts.Action.values()) {
                if (action.requiresLyrics() && mod.lyrics == null) continue;
                if (action != recording && binding.equals(mod.settings.shortcuts.get(action))) {
                    keyNotice = "Уже назначено: " + action.label;
                    return;
                }
            }
            saveBinding(recording, binding);
        }

        private void saveBinding(MusicShortcuts.Action action, MusicShortcuts.Binding binding) {
            mod.settings.shortcuts.put(action, binding);
            mod.settings.save();
            recording = null;
            keyNotice = binding.assigned() ? "Сохранено: " + binding.label() : "Назначение снято.";
            rebuildWidgets();
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            KeyEvent event = new KeyEvent(keyCode, scanCode, modifiers);
            if (typing() && (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER)) {
                setFocused(null);
                if (tab == Tab.SEARCH) submit(0, query);
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public void mouseMoved(double x, double y) {
            super.mouseMoved(x / interfaceScale, y / interfaceScale);
        }

        private MouseButtonEvent layoutEvent(MouseButtonEvent event) {
            return new MouseButtonEvent(event.x() / interfaceScale, event.y() / interfaceScale, event.buttonInfo());
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            MouseButtonEvent event = CompatInput.mouse(mouseX, mouseY, button);
            return super.mouseClicked(layoutEvent(event).x(), layoutEvent(event).y(), layoutEvent(event).button());
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            MouseButtonEvent event = CompatInput.mouse(mouseX, mouseY, button);
            return super.mouseReleased(layoutEvent(event).x(), layoutEvent(event).y(), layoutEvent(event).button());
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double x, double y) {
            MouseButtonEvent event = CompatInput.mouse(mouseX, mouseY, button);
            return super.mouseDragged(layoutEvent(event).x(), layoutEvent(event).y(), layoutEvent(event).button(), x / interfaceScale, y / interfaceScale);
        }

        @Override
        public boolean mouseScrolled(double x, double y, double horizontal, double delta) {
            if (delta == 0) return false;
            x /= interfaceScale;
            y /= interfaceScale;
            int step = delta > 0 ? -1 : 1;
            Rect history = tab == Tab.HISTORY ? content : extraHistory;
            if (history != null && history.contains(x, y)) {
                historyOffset += step * 2;
                update();
                return true;
            }
            if (!content.contains(x, y)) return super.mouseScrolled(x, y, horizontal, delta);
            if (showingLyrics()) return super.mouseScrolled(x, y, horizontal, delta);
            if (tab == Tab.SETTINGS && preferenceCount > visiblePreferences) {
                int value = Math.clamp(settingsOffset + step, 0, preferenceCount - visiblePreferences);
                if (value != settingsOffset) {
                    settingsOffset = value;
                    rebuildWidgets();
                }
                return true;
            }
            if (!rows.isEmpty()) {
                offset += step * 2;
                update();
                return true;
            }
            return false;
        }

        @Override
        public void onClose() {
            if (tab == Tab.SETTINGS || tab == Tab.ACCOUNT) select(returnTab);
            else if (tab == Tab.HISTORY) select(historyReturn);
            else closePlayer();
        }

        void closePlayer() {
            cancelRequest();
            recording = null;
            super.onClose();
        }

        @Override
        public void removed() {
            if (mod.visualizer != null) mod.visualizer.hide();
            super.removed();
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float dt) {
            renderBackground(g, mx, my, dt);
            mx = (int) (mx / interfaceScale);
            my = (int) (my / interfaceScale);
            CompatPose.of(g).pushMatrix().scale(interfaceScale);
            drawFrame(g);
            switch (tab) {
                case HOME -> {
                    if (showingLyrics()) {
                        int reserve = 28 + (editLyrics != null && editLyrics.visible ? editLyrics.getWidth() + 4 : 0);
                        text(g, font, "Текст песни", content.x + 2, content.y + 8, TEXT, Type.TITLE, content.w - reserve);
                    } else if (showingVisualizer()) {
                        text(g, font, "Визуализация", content.x + 2, content.y + 8, TEXT, Type.TITLE, content.w - headingReserve);
                    } else drawHome(g);
                }
                case SEARCH, FAVORITES, PLAYLISTS, ALBUMS, ARTISTS, LOCAL -> drawListing(g);
                case HISTORY -> drawHistory(g, content);
                case SETTINGS -> drawSettings(g);
                case ACCOUNT -> drawAccount(g);
            }
            if (extraHistory != null) drawHistory(g, extraHistory);
            drawTransport(g, mx, my);
            super.render(g, mx, my, dt);
            drawCredit(g);
            CompatPose.of(g).popMatrix();
        }

        private void drawCredit(GuiGraphics g) {
            if (creditCrown == null) return;
            creditCrown.hint(BuildTrust.crownMessage());
            if (creditShownAt < 0) return;
            long age = Util.getMillis() - creditShownAt;
            if (age >= 3100) return;
            g.nextStratum();
            int x = frame.right() - 147;
            int y = frame.y + 46 + Math.round(6 * Math.max(0, 1 - age / 170f));
            int width = 137, height = 35;
            float fade = Math.min(1, (3100 - age) / 280f);
            int crown = BuildTrust.crownColor();
            outline(g, x, y, width, height, 7, CompatColor.multiplyAlpha(crown, fade), CompatColor.multiplyAlpha(0xff322820, fade));
            Icon.CROWN.draw(g, x + 9, y + 12, CompatColor.multiplyAlpha(crown, fade));
            text(g, font, BuildTrust.modified() ? "Неофициальная сборка" : "King220l", x + 25, y + 7,
                CompatColor.multiplyAlpha(crown, fade), Type.STRONG, width - 31);
            text(g, font, BuildTrust.updateAvailable() ? "Доступно обновление" : BuildTrust.modified() ? "Файл изменён" : "Оригинальный мод",
                x + 25, y + 19, CompatColor.multiplyAlpha(TEXT, fade), Type.BODY, width - 31);
            if (age >= 590) return;
            float progress = age / 590f;
            int spark = CompatColor.multiplyAlpha(crown, 1 - progress);
            int cx = creditCrown.getX() + 12, cy = creditCrown.getY() + 12;
            for (int i = 0; i < 7; i++) {
                double angle = i * Math.TAU / 7 + .27;
                int radius = 9 + Math.round(progress * (12 + i % 3 * 3));
                int sx = cx + (int) Math.round(Math.cos(angle) * radius);
                int sy = cy + (int) Math.round(Math.sin(angle) * radius + 5 * progress * progress);
                g.fill(sx - 1, sy, sx + 2, sy + 1, spark);
                g.fill(sx, sy - 1, sx + 1, sy + 2, spark);
            }
        }

        private void drawFrame(GuiGraphics g) {
            rounded(g, frame.x - 2, frame.y + 3, frame.w + 4, frame.h + 4, 12, 0x7c000000);
            outline(g, frame.x, frame.y, frame.w, frame.h, 11, BORDER, BASE);
            rounded(g, rail.x, rail.y, rail.w, rail.h, 8, RAIL);
            g.fill(rail.x + 7, rail.bottom() - 33, rail.right() - 7, rail.bottom() - 32, BORDER);
            g.fill(frame.x + 10, frame.y + 39, frame.right() - 10, frame.y + 40, BORDER);
            emblem(g, frame.x + 10, frame.y + 8, 26);
            if (!condensed) {
                Type brand = frame.w < 545 ? Type.STRONG : Type.TITLE;
                text(g, font, MusicMod.NAME, frame.x + 41, frame.y + (brand == Type.TITLE ? 19 : 18),
                    TEXT, brand, frame.w < 545 ? 98 : 130);
            }
            if (signedIn)
                rounded(g, frame.right() - 85, frame.y + 13, 4, 4, 2, ACCENT);
        }

        private void drawHome(GuiGraphics g) {
            if (hero == null) return;
            rounded(g, hero.x, hero.y, hero.w, hero.h, 8, 0xff493149);
            int x = hero.x + 1, y = hero.y + 1, w = hero.w - 2, h = hero.h - 2;
            int sourceW = Math.min(2172, Math.max(1, w * 724 / Math.max(1, h)));
            int sourceH = Math.min(724, Math.max(1, h * 2172 / Math.max(1, w)));
            int sourceX = (2172 - sourceW) * 4 / 5;
            int sourceY = (724 - sourceH) / 2;
            g.blit(RenderPipelines.GUI_TEXTURED, BANNER, x, y, (float) sourceX, (float) sourceY, w, h, sourceW, sourceH, 2172, 724);
            g.fillGradient(x, y, x + w, y + h, 0x38221b29, 0x83121019);
            maskCorners(g, x, y, w, h, 7, BASE);
            boolean local = !signedIn;
            boolean compact = hero.h < 64;
            String title = local ? "Локальная музыка" : "Моя волна";
            int titleWidth = hero.w - (compact ? navWidth(local ? "Фонотека" : "Слушать") + 27 : 24);
            text(g, font, title, hero.x + 12, hero.y + (compact ? hero.h / 2 - 2 : hero.h >= 81 ? 17 : 14),
                TEXT, compact ? Type.TITLE : Type.HERO, titleWidth);
            if (hero.h >= 81) {
                text(g, font, local ? "Треки из твоей папки" : "Музыка под твоё настроение",
                    hero.x + 13, hero.y + 40, MUTED, Type.BODY, hero.w - 25);
            }
            String message = busy ? "Загружаем музыку…" : notice;
            if (!compact && !message.isEmpty()) {
                text(g, font, message, hero.x + 121, hero.bottom() - 22, ACCENT, Type.BODY, hero.w - 132);
            }
            if (!local && homeCardsY() + 17 <= content.bottom()) {
                text(g, font, "Подборки для игры", content.x, homeCardsY() - 16,
                    TEXT, Type.STRONG, content.w);
            }
        }

        private void drawListing(GuiGraphics g) {
            String title = selectedPlaylist != null ? selectedPlaylist.title()
                : selectedCollection != null ? selectedCollection.title()
                : switch (tab) {
                    case SEARCH -> "Поиск";
                    case FAVORITES -> "Мне нравится";
                    case PLAYLISTS -> "Плейлисты";
                    case ALBUMS -> "Альбомы";
                    case ARTISTS -> "Исполнители";
                    case LOCAL -> "Моя папка";
                    default -> "";
                };
            int rightReserve = tab == Tab.SEARCH && !searched.isEmpty() ? 58 : headingReserve;
            heading(g, title, rightReserve);
            if (searchField != null) {
                outline(g, searchField.x, searchField.y, searchField.w, searchField.h, 6,
                    typing() ? ACCENT_EDGE : BORDER, RAIL);
            }
            String message = busy ? "Загрузка…" : !notice.isEmpty() ? notice : emptyList();
            if (!message.isEmpty()) {
                int y = mainCount() == 0 ? listTop + 1 : content.bottom() - 13;
                if (mainCount() > 0) rounded(g, content.x, y - 2, content.w, 16, 5, BASE);
                text(g, font, message, content.x + 2, y, busy ? MUTED : TEXT, Type.BODY, content.w - 6);
            }
            scroll(g, content.right() + 2, listTop, content.bottom() - 2, offset, rows.size(), mainCount());
        }

        private String emptyList() {
            if (mainCount() > 0) return "";
            if (tab == Tab.SEARCH) {
                return searched.isEmpty() ? "Введи название трека или исполнителя." : "По этому запросу ничего не найдено.";
            }
            if (tab == Tab.LOCAL) {
                if (mod.settings.localFolder.isBlank()) return "Выбери или перетащи папку с музыкой.";
                return localQuery.isBlank() ? "В папке пока нет MP3, WAV, AIFF или AU." : "По запросу ничего не найдено.";
            }
            if (!signedIn) return "Войди в Яндекс, чтобы открыть свою фонотеку.";
            return switch (tab) {
                case ALBUMS -> selectedCollection == null ? "Сохранённых альбомов пока нет." : "В альбоме нет доступных треков.";
                case ARTISTS -> selectedCollection == null ? "Сохранённых исполнителей пока нет." : "У исполнителя нет доступных треков.";
                case FAVORITES -> "Здесь появятся треки, отмеченные сердцем.";
                default -> selectedPlaylist == null ? "Плейлистов пока нет." : "В плейлисте нет доступных треков.";
            };
        }

        private void drawHistory(GuiGraphics g, Rect box) {
            boolean split = extraHistory != null;
            int x = box.x;
            int y = box.y;
            if (split) {
                rounded(g, x, y, box.w, box.h, 8, RAIL);
                x += 7;
                y += 7;
            }
            int count = mod.player.recentTracks().size();
            text(g, font, "Недавние треки", x, y + 2, TEXT, split ? Type.STRONG : Type.TITLE, box.w - (split ? 34 : 12));
            if (count == 0) {
                text(g, font, "Здесь появятся", x, box.y + 31, MUTED, Type.BODY, box.w - 14);
                text(g, font, "прослушанные треки.", x, box.y + 43, MUTED, Type.BODY, box.w - 14);
            }
            scroll(g, box.right() - (split ? 4 : 1), box.y + 28, box.bottom() - 4, historyOffset, historyRows.size(), count);
        }

        private void drawSettings(GuiGraphics g) {
            heading(g, "Настройки", headingReserve);
            for (PreferenceRow item : settingRows) {
                Rect row = item.bounds;
                if (item.section) {
                    Icon.KEY.draw(g, row.x + 8, row.y + 7, ACCENT);
                    text(g, font, item.title, row.x + 24, row.y + 7, ACCENT_LIGHT, Type.STRONG, row.w - 32);
                    continue;
                }
                rounded(g, row.x, row.y, row.w, row.h, 6, RAIL);
                text(g, font, item.title, row.x + 8, row.y + 7, TEXT, Type.BODY, item.textWidth);
            }
            if (stats != null) {
                rounded(g, stats.x, stats.y, stats.w, stats.h, 8, RAIL);
                text(g, font, "Предпросмотр HUD", stats.x + 9, stats.y + 8, MUTED, Type.STRONG, stats.w - 36);
                float scale = .59f * mod.settings.hudScale / 100f;
                drawHudCard(g, font, mod.settings, EXAMPLE, hudPreview.paused(), hudPreview.position(), stats.x + 8, stats.y + 29, scale);
            }
            if (preferences == Preferences.KEYS && !keyNotice.isEmpty()) {
                text(g, font, keyNotice, content.x + 1, content.bottom() - 12, ACCENT, Type.BODY, content.w - 5);
            }
            int barX = stats == null ? content.right() + 2 : stats.x - 5;
            scroll(g, barX, preferenceTop, content.bottom() - (preferences == Preferences.KEYS ? 16 : 2),
                settingsOffset, visiblePreferences, preferenceCount);
        }

        private void drawAccount(GuiGraphics g) {
            heading(g, "Аккаунты", 0);
            Rect box = accountDetails;
            int cardHeight = signedIn ? 79 : Math.min(box.h, 143);
            rounded(g, box.x, box.y, box.w, cardHeight, 8, RAIL);
            String name = signedIn && mod.account != null ? mod.account.name() : "Яндекс ID";
            emblem(g, box.x + 8, box.y + 6, 22);
            text(g, font, name, box.x + 35, box.y + 7, TEXT, Type.STRONG, box.w - 44);
            String status = mod.authStatus;
            if (status.isBlank()) status = signedIn ? "Подключён" : "Войди, чтобы открыть плейлисты и Мою волну.";
            if (mod.deviceCode != null) {
                text(g, font, "Код " + mod.deviceCode.code(), box.x + 35, box.y + 19, ACCENT, Type.STRONG, box.w - 44);
            } else text(g, font, status, box.x + 35, box.y + 19, MUTED, Type.BODY, box.w - 44);
            if (!signedIn) {
                int loginWidth = Math.min(82, Math.max(62, box.w / 5));
                rounded(g, box.x + 8, box.y + 43, box.w - loginWidth - 28 - 25, 22, 5, SURFACE);
                text(g, font, "Токен хранится через защиту Windows и отправляется только Яндексу.",
                    box.x + 8, box.y + 70, MUTED, Type.BODY, box.w - 16);
                text(g, font, "Вход по коду используй только в официальной сборке King220l.",
                    box.x + 8, box.y + 82, MUTED, Type.BODY, box.w - 16);
                text(g, font, BuildTrust.message(), box.x + 8, box.y + 94,
                    BuildTrust.message().contains("изменённая") ? ROSE : ACCENT, Type.BODY, box.w - 16);
            }
        }

        private void heading(GuiGraphics g, String title, int rightReserve) {
            text(g, font, title, content.x, content.y + 1, TEXT, Type.TITLE, content.w - rightReserve);
        }

        private void drawTransport(GuiGraphics g, int mx, int my) {
            int left = frame.x + 9;
            int right = frame.right() - 9;
            int y = footer + 7;
            rounded(g, left, footer, right - left, 52, 8, RAIL);
            g.fill(left + 10, footer, right - 10, footer + 1, BORDER);
            var player = mod.player;
            var track = player.track();
            String name = track == null ? "Трек не выбран" : track.title();
            String subtitle = !player.error().isEmpty() ? player.error() : player.seeking() ? "Перемотка…"
                : player.loading() ? (track != null && track.file() != null ? "Открываем файл…" : "Подключение…")
                : track == null ? "" : (player.preview() ? "Фрагмент · " : "") + track.artist();
            drawRecord(g, left + 6, y - 2, 29, player.recordPositionMs());
            int titleX = left + 43;
            int titleW = (favorite.visible ? favorite.getX() : previous.getX()) - titleX - 5;
            text(g, font, name, titleX, y + (subtitle.isEmpty() ? 9 : 4), TEXT, Type.STRONG, titleW);
            text(g, font, subtitle, titleX, y + 16, player.error().isEmpty() ? MUTED : ROSE, Type.BODY, titleW);
            if (!player.error().isEmpty() && new Rect(titleX, y + 12, titleW, 15).contains(mx, my)) {
                CompatUi.tooltip(g, font, Component.literal(player.error()), screenX(g, mx), screenY(g, my));
            }
        }
    }

    static final class Toggle extends Control {
        private final boolean enabled;

        Toggle(Rect r, boolean enabled, Runnable action) {
            super(r, enabled ? "Включено" : "Выключено", null, Style.FILLED, false, false, action);
            this.enabled = enabled;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float dt) {
            int x = getX(), y = getY();
            beginMotion(g);
            int fill = CompatColor.srgbLerp(hover, enabled ? ACCENT_SOFT : ELEVATED, enabled ? ACCENT_HOVER : HOVER);
            int rim = keyboardFocus() ? ACCENT_LIGHT
                : CompatColor.srgbLerp(hover, enabled ? ACCENT_EDGE : BORDER, enabled ? ACCENT : BORDER_HOVER);
            outline(g, x, y, width, height, 7, rim, fill);
            rounded(g, x + (enabled ? width - 12 : 3), y + 3, 9, 9, 4, enabled ? ACCENT : MUTED);
            CompatPose.of(g).popMatrix();
        }
    }

    private static final MusicApi.Track EXAMPLE = new MusicApi.Track("", "Название трека", "Исполнитель", 0);

    private static final class HudPreview {
        private long started = Util.getMillis();
        private long pausedAt = -1;

        boolean paused() { return pausedAt >= 0; }

        long position() { return (paused() ? pausedAt : Util.getMillis()) - started; }

        void toggle() {
            long now = Util.getMillis();
            if (paused()) {
                started += now - pausedAt;
                pausedAt = -1;
            } else {
                pausedAt = now;
            }
        }
    }

    private static final class PositionScreen extends HudPlacementScreen {
        private final HudPreview preview;

        PositionScreen(MusicMod mod, Screen parent, HudPreview preview) {
            super(mod, parent, "Положение HUD трека");
            this.preview = preview;
        }

        @Override
        void previewControls(int x, int y) {
            icon(new Rect(x, y, 21, 20), "Пауза / воспроизведение предпросмотра",
                preview.paused() ? Icon.PLAY : Icon.PAUSE, Style.GHOST, () -> {
                    preview.toggle();
                    rebuildWidgets();
                });
        }

        @Override
        Rect previewBounds() { return hudBounds(width, height, mod.settings); }

        @Override
        void drawPreview(GuiGraphics g) {
            drawHud(g, font, mod.settings, EXAMPLE, preview.paused(), preview.position());
        }

        @Override void movePreview(double x, double y) {
            mod.settings.x = x;
            mod.settings.y = y;
        }
        @Override void resetPosition() { movePreview(1, 0); }
        @Override int sizePercent() { return mod.settings.hudScale; }
        @Override void sizePercent(int value) { mod.settings.hudScale = value; }
        @Override int opacityPercent() { return mod.settings.hudOpacity; }
        @Override void opacityPercent(int value) { mod.settings.hudOpacity = value; }
        @Override void save() { mod.settings.save(); }
    }

    private static float hudScale(int width, MusicSettings settings) {
        return Math.min(settings.hudScale / 100f, Math.max(1, width - 12) / 154f);
    }

    private static Rect hudBounds(int width, int height, MusicSettings settings) {
        float scale = hudScale(width, settings);
        int w = Math.round(154 * scale);
        int h = Math.round(33 * scale);
        return HudPlacementScreen.bounds(width, height, w, h, settings.x, settings.y);
    }

    static void drawHud(GuiGraphics g, Font font, MusicSettings settings, MusicApi.Track track, boolean paused, long positionMs) {
        Rect bounds = hudBounds(g.guiWidth(), g.guiHeight(), settings);
        drawHudCard(g, font, settings, track, paused, positionMs, bounds.x, bounds.y, hudScale(g.guiWidth(), settings));
    }

    private static void drawHudCard(GuiGraphics g, Font font, MusicSettings settings, MusicApi.Track track,
                                    boolean paused, long positionMs, int x, int y, float scale) {
        var pose = CompatPose.of(g);
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        int alpha = Math.round(settings.hudOpacity * 255 / 100f) << 24;
        rounded(g, 0, 0, 154, 33, 8, alpha | (RAIL & 0xffffff));
        drawRecord(g, 5, 5, 23, positionMs);
        boolean secondary = paused || settings.showArtist && !track.artist().isBlank();
        float textScale = settings.hudTextScale / 100f;
        pose.translate(32, (33 - (secondary ? 21 : 9) * textScale) / 2);
        pose.scale(textScale);
        int available = (int) (115 / textScale);
        text(g, font, track.title(), 0, 0, TEXT, Type.STRONG, available);
        if (secondary) text(g, font, paused ? "На паузе" : track.artist(), 0, 12, MUTED, Type.BODY, available);
        pose.popMatrix();
    }

    private static void drawMeter(GuiGraphics g, int start, int y, int end, float value, int color,
                                  boolean thumb, boolean hover) {
        if (end - start < 3) return;
        int center = start + Math.round((end - start) * Math.clamp(value, 0, 1));
        rounded(g, start, y - 1, end - start, 3, 1, BORDER);
        if (center > start) rounded(g, start, y - 1, center - start, 3, 1, color);
        if (thumb) {
            int d = hover ? 8 : 6;
            rounded(g, center - d / 2, y - d / 2, d, d, d / 2, hover ? TEXT : color);
        }
    }

    private static void scroll(GuiGraphics g, int x, int y, int end, int offset, int visible, int total) {
        if (visible < 1 || total <= visible || end - y < 8) return;
        int h = end - y;
        int thumb = Math.max(6, h * visible / total);
        int slider = y + (h - thumb) * offset / (total - visible);
        rounded(g, x, y, 2, h, 1, BORDER);
        rounded(g, x, slider, 2, thumb, 1, DIM);
    }

    private static String time(long milliseconds) {
        long seconds = Math.max(0, milliseconds / 1_000);
        long part = seconds % 60;
        return (seconds / 60) + (part < 10 ? ":0" : ":") + part;
    }

    private static int screenX(GuiGraphics g, int x) { return Math.round(CompatPose.of(g).m00() * x + CompatPose.of(g).m20()); }

    private static int screenY(GuiGraphics g, int y) { return Math.round(CompatPose.of(g).m11() * y + CompatPose.of(g).m21()); }

    private static String trackCount(int count) {
        int last = count % 10, lastTwo = count % 100;
        String unit = lastTwo >= 11 && lastTwo <= 14 || last == 0 || last >= 5 ? "треков" : last == 1 ? "трек" : "трека";
        return count + " " + unit;
    }
}
