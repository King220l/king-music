package studio.king220l.yamusic;

import net.minecraft.client.Minecraft;
import org.lwjgl.sdl.SDLDialog;
import org.lwjgl.sdl.SDL_DialogFileCallback;
import org.lwjgl.system.MemoryUtil;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

/** SDL dialogs must start on the main thread; their results may arrive on another thread. */
final class MusicFolderPicker {
    private static final AtomicLong SERIAL = new AtomicLong();
    private static final ConcurrentHashMap<Long, BiConsumer<String, Boolean>> REQUESTS = new ConcurrentHashMap<>();
    // One process-lifetime callback: native dialogs can outlive the screen that opened them.
    private static final SDL_DialogFileCallback CALLBACK = SDL_DialogFileCallback.create((request, files, filter) -> {
        var result = REQUESTS.remove(request);
        if (result == null) return;
        String selected = null;
        boolean failed = files == MemoryUtil.NULL;
        if (!failed) {
            long first = MemoryUtil.memGetAddress(files);
            if (first != MemoryUtil.NULL) selected = MemoryUtil.memUTF8(first);
        }
        // Copy the UTF-8 path before SDL releases the native file list.
        result.accept(selected, failed);
    });

    private MusicFolderPicker() {}

    static void choose(Minecraft client, String initial, BiConsumer<String, Boolean> result) {
        long request = SERIAL.incrementAndGet();
        REQUESTS.put(request, (path, failed) -> client.execute(() -> result.accept(path, failed)));
        try {
            SDLDialog.SDL_ShowOpenFolderDialog(CALLBACK, request, client.getWindow().handle(), initial, false);
        } catch (RuntimeException | LinkageError failure) {
            REQUESTS.remove(request);
            MusicMod.LOG.warn("Could not open the folder picker", failure);
            result.accept(null, true);
        }
    }
}
