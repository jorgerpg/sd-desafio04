package server;

import schema.Question;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class QAQuizServer {
    private final String host;
    private final int port;
    private final Path dataFile;

    private final Map<String, Question> questions = new ConcurrentHashMap<>();
    private final Set<String> peers = ConcurrentHashMap.newKeySet(); // "host:port"
    private final Random rng = new Random();

    private static final String CRLF = "\r\n";

    public QAQuizServer(String host, int port, String dataFile) {
        this.host = host;
        this.port = port;
        this.dataFile = Paths.get(dataFile);
        loadOrSeed();
    }

    // ---------------- Persistência ----------------
    private synchronized void save() {
        try (BufferedWriter w = Files.newBufferedWriter(dataFile, StandardCharsets.UTF_8)) {
            w.write("#PEERS " + String.join(",", peers)); w.write(CRLF);
            for (Question q : questions.values()) { w.write(q.toPSV()); w.write(CRLF); }
        } catch (IOException e) { System.err.println("[ERRO] save: " + e.getMessage()); }
    }

    private void loadOrSeed() {
        if (Files.exists(dataFile)) {
            try (BufferedReader r = Files.newBufferedReader(dataFile, StandardCharsets.UTF_8)) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.startsWith("#PEERS ")) {
                        String tail = line.substring(7).trim();
                        if (!tail.isEmpty()) peers.addAll(Arrays.asList(tail.split(",")));
                    } else if (!line.isBlank()) {
                        Question q = Question.fromPSV(line);
                        questions.put(q.id, q);
                    }
                }
                System.out.printf("[BOOT] Carregado %d perguntas, %d peers%n", questions.size(), peers.size());
                return;
            } catch (Exception e) { System.err.println("[ERRO] load: " + e.getMessage()); }
        }
        // Seed mínimo
        addSeed("Python", "Qual função imprime no console?",
                List.of("scan()", "print()", "echo()", "show()"), 1);
        addSeed("Redes", "Qual camada do modelo OSI lida com roteamento?",
                List.of("Aplicação", "Transporte", "Rede", "Enlace"), 2);
        addSeed("Java", "Qual palavra-chave é usada para herdar uma classe em Java??",
                List.of("implements", "inherits", "extends", "super"), 2);
        addSeed("Banco de Dados", "Qual comando SQL é usado para remover uma tabela inteira?",
                List.of("DELETE", "DROP", "REMOVE", "TRUNCATE"), 1);
        addSeed("Sistemas Operacionais", "Qual desses é um sistema operacional de código aberto?",
                List.of("Windows", "Linux", "MacOS", "Solaris"), 1);
        addSeed("Redes", "Qual protocolo é usado para envio de e-mails?",
                List.of("SMTP", "HTTP", "FTP", "DNS"), 0);
        addSeed("Python", "Qual símbolo é usado para criar comentários de linha única em Python?",
                List.of("//", "#", "<!-- -->", "--"), 1);
        addSeed("Segurança da Informação", "Qual dessas é considerada uma técnica de criptografia simétrica?",
                List.of("AES", "RSA", "DSA", "ECC"), 0);
        addSeed("Engenharia de Software", "No ciclo de vida de software em cascata, qual é a primeira fase?",
                List.of("Implementação", "Requisitos", "Testes", "Projeto"), 1);
        addSeed("Hardware", "Qual componente é considerado a 'unidade central de processamento'?",
                List.of("Memória RAM", "CPU", "HD", "GPU"), 1);
        save();
        System.out.println("[BOOT] Seed criado com 10 perguntas.");
    }

    private void addSeed(String topic, String text, List<String> opts, int correct) {
        String id = UUID.randomUUID().toString();
        questions.put(id, new Question(id, topic, text, opts, correct));
    }

    // ---------------- Helpers ----------------
    private synchronized int mergePSV(List<String> psvLines) {
        int added = 0;
        for (String line : psvLines) {
            if (line.isBlank() || line.startsWith("#")) continue;
            Question q = Question.fromPSV(line);
            if (!questions.containsKey(q.id)) {
                questions.put(q.id, q);
                added++;
            }
        }
        if (added > 0) save();
        return added;
    }

    private String questionFrame(Question q) {
        // QUESTION|<id>|<topic>|<text>|<opt0>;;<opt1>;;...   (1 linha)
        String topic = q.topic.replace("|", "¦");
        String text  = q.text.replace("|", "¦").replace("\r", " ").replace("\n", " ");
        String opts  = q.options.stream()
                .map(o -> o.replace("|", "¦").replace("\r", " ").replace("\n", " "))
                .collect(Collectors.joining(";;"));
        return "QUESTION|" + q.id + "|" + topic + "|" + text + "|" + opts;
    }

    // ---------------- Protocolo de peer (EXPORT) ----------------
    private static class ExportResult {
        final List<String> lines;
        ExportResult(List<String> l) { this.lines = l; }
    }

    private ExportResult exportAll() {
        List<String> lines = questions.values().stream().map(Question::toPSV).toList();
        return new ExportResult(lines);
    }

    // ---------------- Console admin (sem mexer no código do jogo) ----------------
    private void startAdminConsole() {
        Thread t = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] p = line.trim().split("\\s+");
                    if (p.length == 0 || p[0].isBlank()) continue;
                    switch (p[0].toUpperCase()) {
                        case "PEERS" -> System.out.println("Peers: " + peers);
                        case "ADD_PEER" -> {
                            if (p.length != 3) { System.out.println("uso: ADD_PEER <host> <port>"); break; }
                            String hp = p[1] + ":" + p[2]; peers.add(hp); save();
                            System.out.println("OK: " + peers);
                        }
                        case "PULL" -> {
                            if (p.length != 3) { System.out.println("uso: PULL <host> <port>"); break; }
                            int imp = pullFromPeer(p[1], Integer.parseInt(p[2]));
                            System.out.println("Importados: " + imp + " | total=" + questions.size());
                        }
                        case "COUNT" -> System.out.println("Perguntas: " + questions.size());
                        case "HELP" -> System.out.println("Comandos: PEERS, ADD_PEER h p, PULL h p, COUNT, HELP");
                        default -> System.out.println("Comando desconhecido. Tente HELP.");
                    }
                }
            } catch (Exception ignore) {}
        }, "admin-console");
        t.setDaemon(true);
        t.start();
    }

    private int pullFromPeer(String host, int port) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), 5000);
            s.setSoTimeout(5000);
            BufferedReader in  = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));
            in.readLine(); // banner
            out.write("EXPORT" + CRLF); out.flush();

            // Protocolo EXPORT: primeira linha "EXPORT_OK", depois N linhas PSV, finaliza com "."
            String first = in.readLine();
            if (!"EXPORT_OK".equals(first)) return 0;

            List<String> lines = new ArrayList<>();
            String l;
            while ((l = in.readLine()) != null) {
                if (l.equals(".")) break;
                lines.add(l);
            }
            return mergePSV(lines);
        } catch (Exception e) {
            System.out.println("[PULL ERRO] " + e.getMessage());
            return 0;
        }
    }

    // ---------------- Servidor (jogo por sessão de conexão) ----------------
    public void serveForever() throws IOException {
        startAdminConsole(); // permite ADD_PEER / PULL via terminal
        try (ServerSocket server = new ServerSocket()) {
            server.bind(new InetSocketAddress(host, port));
            System.out.printf("[SERVE] Escutando em %s:%d%n", host, port);
            while (true) {
                Socket client = server.accept();
                new Thread(() -> handleClient(client), "cli-" + client.getPort()).start();
            }
        }
    }

    private void handleClient(Socket s) {
        try (s;
             BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8))) {

            // Banner
            out.write("BANNER|QAQuizServer" + CRLF);
            out.flush();

            // Se o cliente for um peer pedindo EXPORT imediatamente:
            s.setSoTimeout(50); // leitura não bloqueante longa
            String peek = null;
            try { peek = in.readLine(); } catch (SocketTimeoutException ignored) {}
            s.setSoTimeout(0);

            if (peek != null && peek.equalsIgnoreCase("EXPORT")) {
                ExportResult ex = exportAll();
                out.write("EXPORT_OK" + CRLF);
                for (String line : ex.lines) out.write(line + CRLF);
                out.write("." + CRLF);
                out.flush();
                return; // encerra conexão de peer
            }

            // Loop do jogo por sessão
            int score = 0;
            List<Question> pool = new ArrayList<>(questions.values());
            Collections.shuffle(pool, rng);
            int idx = 0;

            if (pool.isEmpty()) {
                out.write("END|"+score+CRLF); out.flush();
                return;
            } else {
                out.write(questionFrame(pool.get(idx)) + CRLF);
                out.flush();
            }

            String line;
            while ((line = (peek != null ? peek : in.readLine())) != null) {
                peek = null; // consumido
                String cmd = line.trim();
                if (cmd.equalsIgnoreCase("SAIR") || cmd.equalsIgnoreCase("EXIT")) {
                    out.write("BYE|" + score + CRLF); out.flush();
                    return;
                }

                // Espera: ANSWER|<question_id>|<choice_index>
                if (!cmd.startsWith("ANSWER|")) {
                    // entrada inválida → instrui o cliente
                    out.write("ERR|use ANSWER|<id>|<index> ou SAIR" + CRLF);
                    out.flush();
                    continue;
                }
                String[] p = cmd.split("\\|", -1);
                if (p.length < 3) {
                    out.write("ERR|uso: ANSWER|<id>|<index>" + CRLF); out.flush(); continue;
                }
                String qid = p[1];
                int choice;
                try { choice = Integer.parseInt(p[2]); } catch (Exception e) {
                    out.write("ERR|index deve ser inteiro" + CRLF); out.flush(); continue;
                }
                Question q = null;
                for (int i = Math.max(0, idx - 1); i <= idx && i < pool.size(); i++) {
                    if (pool.get(i).id.equals(qid)) { q = pool.get(i); break; }
                }
                if (q == null) { // fallback busca global
                    for (Question x : pool) if (x.id.equals(qid)) { q = x; break; }
                }
                if (q == null) { out.write("ERR|id desconhecido" + CRLF); out.flush(); continue; }

                boolean correct = (choice == q.correct);
                if (correct) score++;

                out.write("RESULT|" + (correct ? "true" : "false") + "|" + score + CRLF);

                idx++;
                if (idx >= pool.size()) {
                    out.write("END|" + score + CRLF);
                    out.flush();
                    return;
                } else {
                    out.write(questionFrame(pool.get(idx)) + CRLF);
                    out.flush();
                }
            }
        } catch (IOException ignored) { /* conexão encerrada */ }
    }
}
