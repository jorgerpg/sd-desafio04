package schema;

import java.util.*;
import java.util.stream.Collectors;

public class Question {
    public final String id;
    public final String topic;
    public final String text;
    public final List<String> options;
    public final int correct; // índice 0-based

    public Question(String id, String topic, String text, List<String> options, int correct) {
        this.id = id;
        this.topic = topic;
        this.text = text;
        this.options = List.copyOf(options);
        this.correct = correct;
    }

    // Persistência PSV: id|topic|text|opt0;;opt1...|correct
    public String toPSV() {
        String safeTopic = topic.replace("|", "¦").replace("\r", " ").replace("\n", " ");
        String safeText  = text.replace("|", "¦").replace("\r", " ").replace("\n", " ");
        List<String> safeOpts = options.stream()
            .map(o -> o.replace("|", "¦").replace(";;", ";;·").replace("\r", " ").replace("\n", " "))
            .collect(Collectors.toList());
        return String.join("|",
                id,
                safeTopic,
                safeText,
                String.join(";;", safeOpts),
                Integer.toString(correct));
    }

    public static Question fromPSV(String line) {
        String[] parts = line.split("\\|", -1);
        if (parts.length < 5) throw new IllegalArgumentException("bad PSV line");
        String id = parts[0];
        String topic = parts[1];
        String text = parts[2];
        String[] optsArr = parts[3].split(";;", -1);
        List<String> opts = new ArrayList<>();
        for (String s : optsArr) opts.add(s.replace(";;·", ";;"));
        int correct = Integer.parseInt(parts[4]);
        return new Question(id, topic, text, opts, correct);
    }
}
