package agents.etiquette;

import com.google.adk.tools.Annotations.Schema;
import java.text.Normalizer;
import java.util.Map;

public class EtiquetteAgent {

    private static String clean(String s) {
        return Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD)
                .trim()
                .replaceAll("(\\p{IsM}+|\\p{IsP}+)", "")
                .replaceAll("\\s+", " ");
    }

    public static Map<String, String> greet(
            @Schema(name = "name", description = "Name to greet") String name) {
        String n = clean(name);
        if (n.isBlank()) n = "there";
        return Map.of(
                "status", "success",
                "report", "Hello, " + n + "! How can I assist you today?"
        );
    }

    public static Map<String, String> farewell(
            @Schema(name = "name", description = "Name to bid farewell") String name) {
        String n = clean(name);
        if (n.isBlank()) n = "friend";
        return Map.of(
                "status", "success",
                "report", "Goodbye, " + n + ". Stay safe and have a great day!"
        );
    }
}