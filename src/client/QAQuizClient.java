package client;

import schema.Question; // Importa o modelo serializável da pergunta

import java.io.*;       // Streams de E/S (Object streams para trafegar objetos)
import java.net.*;      // Socket
import java.util.Scanner; // Leitura do teclado

public class QAQuizClient {
    // Método estático para executar o cliente e conectar em (host, port)
    public static void run(String host, int port) {
        // try-with-resources garante fechar o socket ao final
        try (Socket s = new Socket(host, port)) {
            // Cria streams de objetos (ordem importa: cria OOS, flush, depois OIS)
            ObjectOutputStream oos = new ObjectOutputStream(s.getOutputStream()); oos.flush();
            ObjectInputStream  ois = new ObjectInputStream(s.getInputStream());

            // Lê o banner inicial enviado pelo servidor (ex.: "BANNER:QAQuizServer")
            System.out.println("[conectado] " + ois.readObject());

            Scanner sc = new Scanner(System.in); // Para ler respostas do usuário no console

            // Loop principal do cliente: aguarda objetos vindos do servidor
            while (true) {
                Object obj = ois.readObject(); // Pode ser Question ou String (RESULT|, END|, BYE|)

                if (obj instanceof Question q) {
                    // Recebeu uma pergunta → imprime tópico, enunciado e opções
                    System.out.println("\n[" + q.topic + "] " + q.text);
                    for (int i = 0; i < q.options.size(); i++) {
                        System.out.println("  " + i + ") " + q.options.get(i));
                    }

                    // Solicita resposta ao usuário
                    System.out.print("Resposta (índice) ou 'sair': ");
                    String ans = sc.nextLine().trim();

                    // Se digitou "sair", manda comando SAIR e termina
                    if (ans.equalsIgnoreCase("sair")) {
                        oos.writeObject("SAIR"); oos.flush(); break;
                    } else {
                        // Caso contrário, envia protocolo "ANSWER:<id>:<indice>"
                        oos.writeObject("ANSWER:" + q.id + ":" + ans); oos.flush();
                    }

                } else if (obj instanceof String msg) {
                    // Mensagens de texto do servidor: RESULT|..., END|..., BYE|...
                    if (msg.startsWith("RESULT|")) {
                        String[] p = msg.split("\\|");
                        // p[1] = "true"/"false"; p[2] = score atualizado
                        System.out.println((p[1].equals("true") ? "✅ Correto!" : "❌ Errado.") +
                                " | Pontuação: " + p[2]);
                    } else if (msg.startsWith("END|")) {
                        // Fim normal do jogo (acabaram as perguntas)
                        System.out.println("Fim do jogo. Pontuação: " + msg.substring(4)); break;
                    } else if (msg.startsWith("BYE|")) {
                        // Saída antecipada (usuário pediu sair)
                        System.out.println("Encerrado. Pontuação: " + msg.substring(4)); break;
                    }
                }
            }
        } catch (Exception e) {
            // Tratamento genérico de erro de conexão/E/S
            System.out.println("[erro] " + e.getMessage());
        }
    }
}
