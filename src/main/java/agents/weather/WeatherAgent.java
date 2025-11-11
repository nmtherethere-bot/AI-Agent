package agents.weather;

import com.google.adk.tools.Annotations.Schema;
import java.text.Normalizer;
import java.util.Map;

public class WeatherAgent {

    private static String normalizeCity(String city) {
        return Normalizer.normalize(city, Normalizer.Form.NFD)
                .trim()
                .toLowerCase()
                .replaceAll("(\\p{IsM}+|\\p{IsP}+)", "")
                .replaceAll("\\s+", " ");
    }

    private static boolean isValidCity(String city) {
        if (city == null || city.isBlank()) return false;
        String cleaned = normalizeCity(city);
        // Allow letters, spaces, and hyphens after normalization; length guard.
        return cleaned.matches("[a-z ]{2,40}");
    }

    public static Map<String, String> getWeather(
            @Schema(name = "city", description = "City to retrieve weather for") String city) {
        if (!isValidCity(city)) {
            return Map.of(
                    "status", "error",
                    "report", "Invalid city. Use alphabetic names only (2–40 chars).");
        }

        String cleaned = normalizeCity(city);

        // Simple stubbed reports to keep the example deterministic.
        if (cleaned.equals("new york")) {
            return Map.of(
                    "status", "success",
                    "report",
                    "The weather in New York is sunny with a temperature of 25°C (77°F)."
            );
        }
        if (cleaned.equals("san francisco")) {
            return Map.of(
                    "status", "success",
                    "report",
                    "San Francisco: coastal fog in the morning, high near 20°C (68°F)."
            );
        }
        if (cleaned.equals("london")) {
            return Map.of(
                    "status", "success",
                    "report",
                    "London: overcast with light rain, around 15°C (59°F)."
            );
        }

        return Map.of(
                "status", "error",
                "report", "Weather information for " + city + " is not available.");
    }
}