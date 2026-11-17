package studio.king220l.yamusic;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class MusicHttp {
    private MusicHttp() {}

    static HttpResponse.BodyHandler<String> text(long maxBytes) {
        return HttpResponse.BodyHandlers.limiting(HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8), maxBytes);
    }

    static <T> HttpResponse<T> send(HttpClient client, HttpRequest request, HttpResponse.BodyHandler<T> handler,
                                    long seconds) throws IOException, InterruptedException {
        var response = client.sendAsync(request, handler);
        try {
            return response.get(seconds, TimeUnit.SECONDS);
        } catch (TimeoutException error) {
            response.cancel(true);
            throw new HttpTimeoutException("Music request timed out");
        } catch (InterruptedException error) {
            response.cancel(true);
            throw error;
        } catch (ExecutionException error) {
            if (error.getCause() instanceof IOException failure) throw failure;
            throw new IOException("Music request failed", error.getCause());
        }
    }
}
