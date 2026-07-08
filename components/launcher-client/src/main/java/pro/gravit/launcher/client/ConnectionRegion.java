package pro.gravit.launcher.client;

public record ConnectionRegion(
        String id,
        String name,
        String apiUrl,
        String healthUrl,
        int priority,
        boolean enabled,
        String description,
        String countryCode,
        boolean recommended,
        boolean playerVisible,
        boolean fallbackOnly) {
    public ConnectionRegion(String id, String name, String apiUrl, String healthUrl, int priority, boolean enabled) {
        this(id, name, apiUrl, healthUrl, priority, enabled, "", "", false, true, false);
    }

    public boolean isValid() {
        return id != null && !id.isBlank()
                && apiUrl != null && !apiUrl.isBlank()
                && healthUrl != null && !healthUrl.isBlank();
    }
}
