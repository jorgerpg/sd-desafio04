package server;

import schema.Question;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public class QAQuizServer {
    private final String host;
    private final int port;
    private final Path dataFile;

    private final Map<String, Question> questions = new ConcurrentHashMap<>();
    private final Set<String> peers = ConcurrentHashMap.newKeySet();
    private final Random rng = new Random();

    public QAQuizServer(String host, int port, String dataFile) {
        this.host = host;
        this.port = port;
        this.dataFile = Paths.get(dataFile);
        loadOrSeed();
    }

    // ---------- Persistência ----------
    private synchronized void save() {
        try (BufferedWriter w = Files.newBufferedWriter(dataFile, StandardCharsets.UTF_8)) {
            w.write("#PEERS " + String.join(",", peers)); w.newLine();
            for (Question q : questions.values()) {
                w.write(q.toPSV()); w.newLine();
            }
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
                        continue;
                    }
                    if (line.isBlank()) continue;
                    Question q = Question.fromPSV(line);
                    questions.putIfAbsent(q.id, q);
                }
                System.out.printf("[BOOT] Carregado %d perguntas, %d peers%n", questions.size(), peers.size());
                return;
            } catch (Exception e) { System.err.println("[ERRO] load: " + e.getMessage()); }
        }

        // Seeds
        addSeed(new Question("Python", "Qual função imprime no console?",
                List.of("scan()", "print()", "echo()", "show()"), 1));
        addSeed(new Question("Redes", "Qual camada do modelo OSI lida com roteamento?",
                List.of("Aplicação", "Transporte", "Rede", "Enlace"), 2));
        addSeed(new Question("Java", "Qual palavra-chave é usada para herdar uma classe em Java?",
                List.of("implements", "inherits", "extends", "super"), 2));
        addSeed(new Question("Banco de Dados", "Qual comando SQL é usado para remover uma tabela inteira?",
                List.of("DELETE", "DROP", "REMOVE", "TRUNCATE"), 1));
        addSeed(new Question("Sistemas Operacionais", "Qual desses é um sistema operacional de código aberto?",
                List.of("Windows", "Linux", "MacOS", "Solaris"), 1));
        addSeed(new Question("Redes", "Qual protocolo é usado para envio de e-mails?",
                List.of("SMTP", "HTTP", "FTP", "DNS"), 0));
        addSeed(new Question("Python", "Qual símbolo é usado para criar comentários de linha única em Python?",
                List.of("//", "#", "<!-- -->", "--"), 1));
        addSeed(new Question("Segurança da Informação", "Qual dessas é considerada uma técnica de criptografia simétrica?",
                List.of("AES", "RSA", "DSA", "ECC"), 0));
        addSeed(new Question("Engenharia de Software", "No ciclo de vida de software em cascata, qual é a primeira fase?",
                List.of("Implementação", "Requisitos", "Testes", "Projeto"), 1));
        addSeed(new Question("Hardware", "Qual componente é considerado a 'unidade central de processamento'?",
                List.of("Memória RAM", "CPU", "HD", "GPU"), 1));

        save();
        System.out.println("[BOOT] Seeds criados. Total perguntas: " + questions.size());
    }

    private void addSeed(Question q) {
        questions.putIfAbsent(q.id, q);
    }

    // ---------- Console admin ----------
    private void startAdminConsole() {
        Thread t = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] p = line.trim().split("\\s+");
                    if (p.length == 0) continue;
                    switch (p[0].toUpperCase()) {
                        case "PEERS" -> System.out.println("Peers: " + peers);
                        case "ADD_PEER" -> {
                            if (p.length == 3) { 
                                peers.add(p[1] + ":" + p[2]); 
                                save(); 
                                System.out.printf("[ADMIN] Peer adicionado: %s:%s%n", p[1], p[2]);
                            }
                        }
                        case "PULL" -> {
                            if (p.length == 3) {
                                int imp = pullFromPeer(p[1], Integer.parseInt(p[2]));
                                System.out.printf("[ADMIN] Importados %d perguntas de %s:%s%n", imp, p[1], p[2]);
                            }
                        }
                        case "COUNT" -> System.out.println("Perguntas: " + questions.size());
                        default -> System.out.println("Comandos: PEERS, ADD_PEER h p, PULL h p, COUNT");
                    }
                }
            } catch (Exception ignore) {}
        });
        t.setDaemon(true); t.start();
    }

    // ---------- Sincronização ----------
    private int pullFromPeer(String host, int port) {
        try (Socket s = new Socket(host, port)) {
            System.out.printf("[SYNC] Conectando ao peer %s:%d...%n", host, port);

            ObjectOutputStream oos = new ObjectOutputStream(s.getOutputStream()); oos.flush();
            ObjectInputStream  ois = new ObjectInputStream(s.getInputStream());

            Object banner = ois.readObject();
            System.out.println("[SYNC] Recebido banner: " + banner);

            oos.writeObject("EXPORT"); oos.flush();
            System.out.println("[SYNC] Pedido EXPORT enviado");

            Object resp = ois.readObject();
            if (resp instanceof List<?> list) {
                int added = 0;
                for (Object o : list) {
                    if (o instanceof Question q) {
                        if (!questions.containsKey(q.id)) {
                            questions.put(q.id, q);
                            added++;
                        }
                    }
                }
                if (added > 0) save();
                System.out.printf("[SYNC] Recebidas %d perguntas, adicionadas %d (total local %d)%n",
                        list.size(), added, questions.size());
                return added;
            }
        } catch (Exception e) {
            System.out.printf("[SYNC-ERRO] Falha ao puxar de %s:%d → %s%n", host, port, e.getMessage());
        }
        return 0;
    }

    // ---------- Servidor ----------
    public void serveForever() throws IOException {
        startAdminConsole();
        try (ServerSocket server = new ServerSocket(port)) {
            System.out.printf("[SERVE] Escutando em %s:%d%n", host, port);
            while (true) {
                Socket client = server.accept();
                System.out.printf("[CONNECT] Conexão de %s%n", client.getRemoteSocketAddress());
                new Thread(() -> handleClient(client)).start();
            }
        }
    }

    private void handleClient(Socket s) {
        try (s) {
            ObjectOutputStream oos = new ObjectOutputStream(s.getOutputStream()); oos.flush();
            ObjectInputStream  ois = new ObjectInputStream(s.getInputStream());

            oos.writeObject("BANNER:QAQuizServer"); oos.flush();

            Object first = null;
            try { s.setSoTimeout(80); first = ois.readObject(); } catch (Exception ignore) {}
            s.setSoTimeout(0);

            // Peer pedindo EXPORT
            if (first instanceof String str && "EXPORT".equalsIgnoreCase(str)) {
                System.out.printf("[PEER] Peer %s pediu EXPORT (%d perguntas)%n",
                        s.getRemoteSocketAddress(), questions.size());
                oos.writeObject(new ArrayList<>(questions.values()));
                oos.flush();
                System.out.printf("[PEER] Enviadas %d perguntas para %s%n",
                        questions.size(), s.getRemoteSocketAddress());
                return;
            }

            // Cliente do jogo
            List<Question> pool = new ArrayList<>(questions.values());
            Collections.shuffle(pool, rng);
            int score = 0, idx = 0;

            if (pool.isEmpty()) { oos.writeObject("END|" + score); return; }

            oos.writeObject(pool.get(idx)); oos.flush();

            while (true) {
                Object obj = ois.readObject();
                if (obj instanceof String cmd) {
                    if (cmd.equalsIgnoreCase("SAIR")) {
                        oos.writeObject("BYE|" + score);
                        System.out.printf("[GAME] Cliente %s saiu. Score final=%d%n",
                                s.getRemoteSocketAddress(), score);
                        break;
                    }
                    if (cmd.startsWith("ANSWER:")) {
                        String[] p = cmd.split(":", -1);
                        if (p.length == 3) {
                            String qid = p[1];
                            int choice = Integer.parseInt(p[2]);
                            Question q = pool.get(idx);
                            boolean correct = q.id.equals(qid) && choice == q.correct;
                            if (correct) score++;
                            oos.writeObject("RESULT|" + correct + "|" + score); oos.flush();
                            System.out.printf("[GAME] Cliente %s respondeu %s (correto=%b, score=%d)%n",
                                    s.getRemoteSocketAddress(), qid, correct, score);

                            idx++;
                            if (idx >= pool.size()) {
                                oos.writeObject("END|" + score);
                                System.out.printf("[GAME] Cliente %s terminou o jogo. Score=%d%n",
                                        s.getRemoteSocketAddress(), score);
                                break;
                            } else { oos.writeObject(pool.get(idx)); oos.flush(); }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.printf("[DISCONNECT] Cliente/peer %s encerrou conexão (%s)%n",
                    s.getRemoteSocketAddress(), e.getMessage());
        }
    }
}
