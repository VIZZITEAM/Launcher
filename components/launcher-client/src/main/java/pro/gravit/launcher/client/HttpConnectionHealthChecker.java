package pro.gravit.launcher.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class HttpConnectionHealthChecker implements ConnectionResolver.HealthChecker {
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(2500))
            .build();

    @Override
    public ConnectionResolver.Health check(ConnectionRegion region, int timeoutMillis) throws Exception {
        long started = System.nanoTime();
        HttpRequest request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(region.healthUrl()))
                .timeout(Duration.ofMillis(timeoutMillis > 0 ? timeoutMillis : 2500))
                .header("Accept", "application/json")
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        long elapsedMillis = Math.max(1, (System.nanoTime() - started) / 1_000_000L);
        return new ConnectionResolver.Health(response.statusCode() >= 200 && response.statusCode() < 300, elapsedMillis);
    }
}
