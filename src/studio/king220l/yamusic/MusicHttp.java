package studio.king220l.yamusic;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class MusicHttp {
    private MusicHttp() {}

    static HttpResponse.BodyHandler<String> text(long maxBytes) {
        if (maxBytes < 0) throw new IllegalArgumentException("Negative response limit");
        return info -> new HttpResponse.BodySubscriber<>() {
            private final HttpResponse.BodySubscriber<String> delegate = HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8);
            private Flow.Subscription subscription;
            private long remaining = maxBytes;
            private boolean done;
            public CompletionStage<String> getBody() { return delegate.getBody(); }
            public void onSubscribe(Flow.Subscription value) { subscription = value; delegate.onSubscribe(value); }
            public void onNext(List<ByteBuffer> buffers) {
                if (done) return;
                for (ByteBuffer buffer : buffers) {
                    if (buffer.remaining() > remaining) {
                        done = true;
                        subscription.cancel();
                        delegate.onError(new IOException("Music response exceeds size limit"));
                        return;
                    }
                    remaining -= buffer.remaining();
                }
                delegate.onNext(buffers);
            }
            public void onError(Throwable failure) { if (!done) { done = true; delegate.onError(failure); } }
            public void onComplete() { if (!done) { done = true; delegate.onComplete(); } }
        };
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
