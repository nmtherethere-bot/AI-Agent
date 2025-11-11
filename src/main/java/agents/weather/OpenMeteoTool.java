package agents.weather;

import com.google.adk.tools.Annotations.Schema;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OpenMeteoTool {

    private static final HttpClient http = HttpClient.newHttpClient();

    private static String normalizeCity(String city) {
        return Normalizer.normalize(city == null ? "" : city, Normalizer.Form.NFD)
                .trim()
                .toLowerCase()
                .replaceAll("(\\p{IsM}+|\\p{IsP}+)", "")
                .replaceAll("\\s+", " ");
    }

    private static boolean isValidCity(String city) {
        if (city == null || city.isBlank()) return false;
        String cleaned = normalizeCity(city);
        return cleaned.matches("[a-z ]{2,40}");
    }

    public static Map<String, String> getCurrentWeather(
            @Schema(name = "city", description = "City to fetch live weather for") String city) {
        if (!isValidCity(city)) {
            return Map.of(
                    "status", "error",
                    "report", "Invalid city. Use alphabetic names only (2–40 chars)."
            );
        }

        try {
            // 1) Geocode the city -> lat/lon
            String geoUrl = "https://geocoding-api.open-meteo.com/v1/search?count=1&language=en&name="
                    + uriEncode(city);
            String geoJson = fetch(geoUrl);
            Double lat = extractDouble(geoJson, "\"latitude\":\\s*([0-9.-]+)");
            Double lon = extractDouble(geoJson, "\"longitude\":\\s*([0-9.-]+)");
            String resolvedName = extractString(geoJson, "\"name\":\\s*\"([^\"]+)\"");
            String country = extractString(geoJson, "\"country\":\\s*\"([^\"]+)\"");

            if (lat == null || lon == null) {
                return Map.of("status", "error", "report",
                        "Could not resolve location for '" + city + "'.");
            }

            // 2) Query current weather
            String weatherUrl = "https://api.open-meteo.com/v1/forecast?latitude=" + lat
                    + "&longitude=" + lon
                    + "&current=temperature_2m,wind_speed_10m,weather_code";
            String wxJson = fetch(weatherUrl);

            Double tempC = extractDouble(wxJson, "\"temperature_2m\":\\s*([0-9.-]+)");
            Double wind = extractDouble(wxJson, "\"wind_speed_10m\":\\s*([0-9.-]+)");
            Integer code = extractInt(wxJson, "\"weather_code\":\\s*([0-9]+)");

            if (tempC == null || wind == null || code == null) {
                return Map.of("status", "error", "report",
                        "Weather details unavailable for '" + city + "'.");
            }

            String cond = describeWeatherCode(code);
            String place = (resolvedName != null ? resolvedName : city)
                    + (country != null ? ", " + country : "");

            String report = String.format(
                    "%s: %s. Temperature %.1f°C, wind %.1f m/s.",
                    place, cond, tempC, wind);

            return Map.of("status", "success", "report", report);
        } catch (Exception e) {
            return Map.of(
                    "status", "error",
                    "report", "Weather request failed: " + e.getMessage()
            );
        }
    }

    private static String uriEncode(String s) {
        try {
            return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }

    private static String fetch(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .GET()
                .header("Accept", "application/json")
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
            return resp.body();
        }
        throw new IOException("HTTP " + resp.statusCode());
    }

    private static Double extractDouble(String json, String regex) {
        Matcher m = Pattern.compile(regex).matcher(json);
        if (m.find()) {
            try { return Double.parseDouble(m.group(1)); } catch (Exception ignored) {}
        }
        return null;
    }

    private static Integer extractInt(String json, String regex) {
        Matcher m = Pattern.compile(regex).matcher(json);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1)); } catch (Exception ignored) {}
        }
        return null;
    }

    private static String extractString(String json, String regex) {
        Matcher m = Pattern.compile(regex).matcher(json);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private static String describeWeatherCode(int code) {
        // Minimal mapping for common codes; extend as needed.
        // Reference: https://open-meteo.com/en/docs#api_form
        return switch (code) {
            case 0 -> "Clear sky";
            case 1, 2 -> "Mainly clear/partly cloudy";
            case 3 -> "Overcast";
            case 45, 48 -> "Fog";
            case 51, 53, 55 -> "Drizzle";
            case 61, 63, 65 -> "Rain";
            case 71, 73, 75 -> "Snow";
            case 95 -> "Thunderstorm";
            default -> "Unspecified conditions";
        };
    }
}