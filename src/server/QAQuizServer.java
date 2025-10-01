package server;

import schema.Question; // Modelo de pergunta serializável

import java.io.*;                           // E/S e Object streams
import java.net.*;                          // Sockets e ServerSocket
import java.nio.charset.StandardCharsets;   // Charset para ler/gravar arquivos
import java.nio.file.*;                     // Manipulação de arquivos/paths
import java.util.*;                         // Coleções utilitárias
import java.util.concurrent.*;              // Estruturas concorrentes (thread-safe)

// Servidor do Quiz: gerencia perguntas, aceita clientes, joga e sincroniza com peers.
public class QAQuizServer {
    private final String host;   // Interface/endereço (apenas para log)
    private final int port;      // Porta de escuta
    private final Path dataFile; // Caminho do arquivo PSV de persistência

    // Estruturas principais em memória:
    private final Map<String, Question> questions = new ConcurrentHashMap<>(); // Perguntas por id
    private final Set<String> peers = ConcurrentHashMap.newKeySet();           // Conjunto de peers "host:port"
    private final Random rng = new Random();                                    // RNG para embaralhar questões

    // Construtor: define parâmetros e carrega do disco (ou cria seeds)
    public QAQuizServer(String host, int port, String dataFile) {
        this.host = host;
        this.port = port;
        this.dataFile = Paths.get(dataFile);
        loadOrSeed(); // Carrega perguntas/peers do arquivo, ou cria seeds iniciais
    }

    // ---------- Persistência ----------
    // Salva peers e perguntas no arquivo PSV.
    private synchronized void save() {
        try (BufferedWriter w = Files.newBufferedWriter(dataFile, StandardCharsets.UTF_8)) {
            w.write("#PEERS " + String.join(",", peers)); w.newLine(); // Primeira linha: peers
            for (Question q : questions.values()) {
                w.write(q.toPSV()); w.newLine(); // Demais linhas: perguntas em PSV
            }
        } catch (IOException e) { System.err.println("[ERRO] save: " + e.getMessage()); }
    }

    // Carrega do arquivo, se existir. Caso contrário, cria perguntas seeds e salva.
    private void loadOrSeed() {
        if (Files.exists(dataFile)) {
            try (BufferedReader r = Files.newBufferedReader(dataFile, StandardCharsets.UTF_8)) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.startsWith("#PEERS ")) {
                        // Linha especial com peers → "#PEERS host1:port1,host2:port2,..."
                        String tail = line.substring(7).trim();
                        if (!tail.isEmpty()) peers.addAll(Arrays.asList(tail.split(",")));
                        continue; // Vai para próxima linha
                    }
                    if (line.isBlank()) continue;   // Ignora linhas em branco
                    Question q = Question.fromPSV(line); // Reconstrói a pergunta
                    questions.putIfAbsent(q.id, q);      // Evita duplicatas (pela chave id)
                }
                System.out.printf("[BOOT] Carregado %d perguntas, %d peers%n", questions.size(), peers.size());
                return; // Já carregou tudo, não precisa seed
            } catch (Exception e) { System.err.println("[ERRO] load: " + e.getMessage()); }
        }

        // ----- Seeds (perguntas padrão quando não há arquivo) -----
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

        save(); // Persiste as seeds para o arquivo
        System.out.println("[BOOT] Seeds criados. Total perguntas: " + questions.size());
    }

    // Adiciona uma pergunta seed evitando sobrescrever id existente
    private void addSeed(Question q) {
        questions.putIfAbsent(q.id, q);
    }

    // ---------- Console admin ----------
    // Cria uma thread para ler comandos do console e administrar o servidor em tempo real.
    private void startAdminConsole() {
        Thread t = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] p = line.trim().split("\\s+"); // Divide por espaços
                    if (p.length == 0) continue;

                    // Comandos suportados:
                    switch (p[0].toUpperCase()) {
                        case "PEERS" -> System.out.println("Peers: " + peers); // Lista peers
                        case "ADD_PEER" -> {
                            // ADD_PEER <host> <port>
                            if (p.length == 3) {
                                peers.add(p[1] + ":" + p[2]); // Adiciona ao set
                                save();                       // Persiste
                                System.out.printf("[ADMIN] Peer adicionado: %s:%s%n", p[1], p[2]);
                            }
                        }
                        case "PULL" -> {
                            // PULL <host> <port> → importa perguntas do peer informado
                            if (p.length == 3) {
                                int imp = pullFromPeer(p[1], Integer.parseInt(p[2]));
                                System.out.printf("[ADMIN] Importados %d perguntas de %s:%s%n", imp, p[1], p[2]);
                            }
                        }
                        case "COUNT" -> System.out.println("Perguntas: " + questions.size()); // Contagem
                        default -> System.out.println("Comandos: PEERS, ADD_PEER h p, PULL h p, COUNT");
                    }
                }
            } catch (Exception ignore) {} // Fecha silenciosamente se der erro/EOF
        });
        t.setDaemon(true); t.start(); // Daemon encerra junto com o processo principal
    }

    // ---------- Sincronização ----------
    // Conecta a um peer e solicita EXPORT (todas as perguntas). Retorna quantas foram importadas.
    private int pullFromPeer(String host, int port) {
        try (Socket s = new Socket(host, port)) {
            System.out.printf("[SYNC] Conectando ao peer %s:%d...%n", host, port);

            // Cria streams de objeto (oos primeiro, flush, depois ois)
            ObjectOutputStream oos = new ObjectOutputStream(s.getOutputStream()); oos.flush();
            ObjectInputStream  ois = new ObjectInputStream(s.getInputStream());

            // Lê banner do peer (apenas informativo)
            Object banner = ois.readObject();
            System.out.println("[SYNC] Recebido banner: " + banner);

            // Envia comando de exportação e informa no log
            oos.writeObject("EXPORT"); oos.flush();
            System.out.println("[SYNC] Pedido EXPORT enviado");

            // Espera uma lista de Question como resposta
            Object resp = ois.readObject();
            if (resp instanceof List<?> list) {
                int added = 0;
                for (Object o : list) {
                    if (o instanceof Question q) {
                        // Adiciona apenas perguntas novas (por id)
                        if (!questions.containsKey(q.id)) {
                            questions.put(q.id, q);
                            added++;
                        }
                    }
                }
                if (added > 0) save(); // Persiste se importou algo
                System.out.printf("[SYNC] Recebidas %d perguntas, adicionadas %d (total local %d)%n",
                        list.size(), added, questions.size());
                return added;
            }
        } catch (Exception e) {
            // Log amigável em caso de falha de rede/serialização
            System.out.printf("[SYNC-ERRO] Falha ao puxar de %s:%d → %s%n", host, port, e.getMessage());
        }
        return 0; // Nada importado
    }

    // ---------- Servidor ----------
    // Inicia o loop do servidor e aceita conexões de clientes e peers.
    public void serveForever() throws IOException {
        startAdminConsole(); // Inicia a thread de administração
        try (ServerSocket server = new ServerSocket(port)) { // Abre porta para escutar
            System.out.printf("[SERVE] Escutando em %s:%d%n", host, port);
            while (true) {
                Socket client = server.accept(); // Bloqueia até um cliente conectar
                System.out.printf("[CONNECT] Conexão de %s%n", client.getRemoteSocketAddress());
                // Cria uma thread para lidar com cada cliente/peer
                new Thread(() -> handleClient(client)).start();
            }
        }
    }

    // Trata uma conexão: pode ser um peer (EXPORT) ou um cliente do jogo.
    private void handleClient(Socket s) {
        try (s) { // Fecha o socket automaticamente ao sair
            ObjectOutputStream oos = new ObjectOutputStream(s.getOutputStream()); oos.flush();
            ObjectInputStream  ois = new ObjectInputStream(s.getInputStream());

            // Envia banner de identificação do servidor
            oos.writeObject("BANNER:QAQuizServer"); oos.flush();

            // Tenta ler o primeiro objeto rapidamente para detectar se é um peer pedindo EXPORT
            Object first = null;
            try { s.setSoTimeout(80); first = ois.readObject(); } catch (Exception ignore) {}
            s.setSoTimeout(0); // Volta ao modo bloqueante normal

            // ----- Caso 1: Peer pedindo EXPORT -----
            if (first instanceof String str && "EXPORT".equalsIgnoreCase(str)) {
                System.out.printf("[PEER] Peer %s pediu EXPORT (%d perguntas)%n",
                        s.getRemoteSocketAddress(), questions.size());
                // Envia todas as perguntas como ArrayList serializável
                oos.writeObject(new ArrayList<>(questions.values()));
                oos.flush();
                System.out.printf("[PEER] Enviadas %d perguntas para %s%n",
                        questions.size(), s.getRemoteSocketAddress());
                return; // Fim do atendimento ao peer
            }

            // ----- Caso 2: Cliente do jogo -----
            List<Question> pool = new ArrayList<>(questions.values()); // Copia perguntas atuais
            Collections.shuffle(pool, rng);                            // Embaralha ordem
            int score = 0, idx = 0;                                   // Estado do jogo

            if (pool.isEmpty()) { oos.writeObject("END|" + score); return; } // Sem perguntas

            // Envia a primeira pergunta ao cliente
            oos.writeObject(pool.get(idx)); oos.flush();

            // Loop do jogo: recebe respostas, valida e manda feedback
            while (true) {
                Object obj = ois.readObject(); // Espera comando do cliente

                if (obj instanceof String cmd) {
                    if (cmd.equalsIgnoreCase("SAIR")) { // Cliente pediu para sair
                        oos.writeObject("BYE|" + score); // Envia pontuação final
                        System.out.printf("[GAME] Cliente %s saiu. Score final=%d%n",
                                s.getRemoteSocketAddress(), score);
                        break; // Encerra o jogo para este cliente
                    }

                    if (cmd.startsWith("ANSWER:")) { // Cliente enviou uma resposta
                        String[] p = cmd.split(":", -1); // "ANSWER:<id>:<indice>"
                        if (p.length == 3) {
                            String qid = p[1];                      // ID informado pelo cliente
                            int choice = Integer.parseInt(p[2]);    // Alternativa escolhida
                            Question q = pool.get(idx);             // Pergunta corrente

                            // Valida id e alternativa correta
                            boolean correct = q.id.equals(qid) && choice == q.correct;
                            if (correct) score++; // Soma ponto se acertou

                            // Retorna feedback e score atualizado
                            oos.writeObject("RESULT|" + correct + "|" + score); oos.flush();

                            System.out.printf("[GAME] Cliente %s respondeu %s (correto=%b, score=%d)%n",
                                    s.getRemoteSocketAddress(), qid, correct, score);

                            // Avança para a próxima pergunta
                            idx++;
                            if (idx >= pool.size()) {
                                // Se acabou, envia END com score final
                                oos.writeObject("END|" + score);
                                System.out.printf("[GAME] Cliente %s terminou o jogo. Score=%d%n",
                                        s.getRemoteSocketAddress(), score);
                                break; // Encerra o jogo
                            } else {
                                // Caso contrário, envia a próxima pergunta
                                oos.writeObject(pool.get(idx)); oos.flush();
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Log quando a conexão encerra inesperadamente ou há erro de E/S
            System.out.printf("[DISCONNECT] Cliente/peer %s encerrou conexão (%s)%n",
                    s.getRemoteSocketAddress(), e.getMessage());
        }
    }
}
