package agents.team;

import agents.weather.WeatherAgent;
import agents.weather.OpenMeteoTool;
import agents.etiquette.EtiquetteAgent;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.adk.tools.Annotations.Schema;
import com.google.adk.tools.FunctionTool;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;

public class WeatherTeamAgent {

    private static final String NAME = "weather_team_agent";
    private static final String USER_ID = "student";

    // Simple session memory for personalization (preferred city per user).
    private static final ConcurrentHashMap<String, String> preferredCity = new ConcurrentHashMap<>();

    // Exposed for Dev UI discovery.
    public static final BaseAgent ROOT_AGENT = initAgent();

    public static BaseAgent initAgent() {
        return LlmAgent.builder()
                .name(NAME)
                .model("gemini-2.0-flash")
                .description("A progressive weather team agent with delegation, memory, and guardrails.")
                .instruction(
                        "You are a helpful team of agents. Delegate to etiquette tools for greetings/farewells " +
                        "and to weather tools for weather queries. Prefer the user's saved city when asked to " +
                        "use their default or preferred city.")
                .tools(
                        // Delegation: etiquette helpers
                        FunctionTool.create(EtiquetteAgent.class, "greet"),
                        FunctionTool.create(EtiquetteAgent.class, "farewell"),
                        // Primary capability: weather lookup
                        FunctionTool.create(WeatherAgent.class, "getWeather"),
                        FunctionTool.create(OpenMeteoTool.class, "getCurrentWeather"),
                        // Memory/personalization helpers
                        FunctionTool.create(WeatherTeamAgent.class, "setPreferredCity"),
                        FunctionTool.create(WeatherTeamAgent.class, "getPreferredCityWeather")
                )
                .build();
    }

    private static String normalizeCity(String city) {
        return Normalizer.normalize(city == null ? "" : city, Normalizer.Form.NFD)
                .trim()
                .toLowerCase()
                .replaceAll("(\\p{IsM}+|\\p{IsP}+)", "")
                .replaceAll("\\s+", " ");
    }

    public static Map<String, String> setPreferredCity(
            @Schema(name = "city", description = "City to remember for this user") String city) {
        String cleaned = normalizeCity(city);
        if (!cleaned.matches("[a-z ]{2,40}")) {
            return Map.of("status", "error", "report",
                    "Invalid city for memory. Use letters/spaces, 2–40 chars.");
        }
        preferredCity.put(USER_ID, cleaned);
        return Map.of("status", "success",
                "report", "Saved your preferred city as: " + city);
    }

    public static Map<String, String> getPreferredCityWeather() {
        String city = preferredCity.get(USER_ID);
        if (city == null || city.isBlank()) {
            return Map.of("status", "error",
                    "report", "No preferred city saved. Use setPreferredCity first.");
        }
        return WeatherAgent.getWeather(city);
    }

    private static String sanitizeInput(String input) {
        if (input == null) return "";
        String sanitized = input.replaceAll("[\\r\\n]", " ").trim();
        // Block obviously risky or irrelevant instructions.
        String lower = sanitized.toLowerCase();
        if (lower.contains("ignore instructions") || lower.contains("system prompt")
                || lower.contains("delete memory") || lower.contains("exfiltrate")) {
            return "BLOCKED";
        }
        // Limit length to keep prompt reasonable.
        if (sanitized.length() > 400) {
            sanitized = sanitized.substring(0, 400);
        }
        return sanitized;
    }

    public static void main(String[] args) throws Exception {
        InMemoryRunner runner = new InMemoryRunner(ROOT_AGENT);

        Session session = runner.sessionService().createSession(NAME, USER_ID).blockingGet();

        try (Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8)) {
            while (true) {
                System.out.print("\nYou > ");
                String userInput = scanner.nextLine();

                if ("quit".equalsIgnoreCase(userInput)) {
                    break;
                }

                String filtered = sanitizeInput(userInput);
                if ("BLOCKED".equals(filtered)) {
                    System.out.println("\nAgent > Your input appears unsafe or off-policy. Please rephrase.");
                    continue;
                }

                Content userMsg = Content.fromParts(Part.fromText(filtered));
                Flowable<Event> events = runner.runAsync(USER_ID, session.id(), userMsg);

                System.out.print("\nAgent > ");
                events.blockingForEach(event -> System.out.println(event.stringifyContent()));
            }
        }
    }
}