package schema;

import java.io.Serializable;
import java.util.List;

public class Question implements Serializable {
    private static final long serialVersionUID = 1L;

    public final String id;       // gerado automaticamente
    public final String topic;
    public final String text;
    public final List<String> options;
    public final int correct; // índice 0-based

    // Gera ID determinístico simples
    private static String generateId(String topic, String text, List<String> options, int correct) {
        String key = topic + "|" + text + "|" + String.join(";", options) + "|" + correct;
        return Integer.toHexString(key.hashCode());
    }

    public Question(String topic, String text, List<String> options, int correct) {
        this.id = generateId(topic, text, options, correct);
        this.topic = topic.trim();
        this.text = text.trim();
        this.options = List.copyOf(options);
        this.correct = correct;
    }

    // Serialização simples em PSV
    public String toPSV() {
        return String.join("|",
                id,
                topic.replace("|", "¦"),
                text.replace("|", "¦"),
                String.join(";;", options).replace("|", "¦"),
                Integer.toString(correct));
    }

    // Reconstrói a partir de linha PSV
    public static Question fromPSV(String line) {
        String[] p = line.split("\\|", -1);
        if (p.length < 5) throw new IllegalArgumentException("bad PSV line");
        String topic = p[1].replace("¦", "|");
        String text = p[2].replace("¦", "|");
        String[] opts = p[3].replace("¦", "|").split(";;", -1);
        int correct = Integer.parseInt(p[4]);
        return new Question(topic, text, List.of(opts), correct);
    }
}
