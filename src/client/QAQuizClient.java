package client;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class QAQuizClient {
    private static final String CRLF = "\r\n";

    public static void run(String host, int port) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), 5000);
            s.setSoTimeout(0);
            BufferedReader in  = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));

            String banner = in.readLine(); // BANNER|QAQuizServer
            System.out.println("[conectado] " + banner);

            Scanner sc = new Scanner(System.in);

            while (true) {
                String line = in.readLine();
                if (line == null) { System.out.println("[servidor encerrou]"); break; }

                if (line.startsWith("QUESTION|")) {
                    String[] p = line.split("\\|", -1);
                    // QUESTION|id|topic|text|opt0;;opt1...
                    if (p.length < 5) { System.out.println("[malformado] " + line); continue; }
                    String qid = p[1];
                    String topic = p[2];
                    String text  = p[3];
                    String[] opts = p[4].split(";;", -1);

                    System.out.println();
                    System.out.println("[" + topic + "] " + text);
                    for (int i = 0; i < opts.length; i++) {
                        System.out.println("  " + i + ") " + opts[i]);
                    }
                    System.out.print("Resposta (índice) ou 'sair': ");
                    String ans = sc.nextLine().trim();
                    if (ans.equalsIgnoreCase("sair") || ans.equalsIgnoreCase("exit")) {
                        out.write("SAIR" + CRLF); out.flush();
                        // aguarda BYE
                        String bye = in.readLine();
                        if (bye != null && bye.startsWith("BYE|")) {
                            System.out.println("Saindo. Pontuação: " + bye.substring(4));
                        }
                        break;
                    } else {
                        out.write("ANSWER|" + qid + "|" + ans + CRLF);
                        out.flush();
                    }
                } else if (line.startsWith("RESULT|")) {
                    String[] p = line.split("\\|", -1);
                    // RESULT|true/false|score
                    boolean ok = "true".equalsIgnoreCase(p[1]);
                    String score = (p.length > 2) ? p[2] : "?";
                    System.out.println(ok ? "✅ Correto!" : "❌ Errado.");
                    System.out.println("Pontuação: " + score);
                    // próxima mensagem será QUESTION ou END (loop continua)
                } else if (line.startsWith("END|")) {
                    System.out.println("\nFim do jogo. Pontuação final: " + line.substring(4));
                    break;
                } else if (line.startsWith("BYE|")) {
                    System.out.println("Encerrado pelo servidor. Pontuação: " + line.substring(4));
                    break;
                } else if (line.startsWith("ERR|")) {
                    System.out.println("[erro] " + line.substring(4));
                } else {
                    System.out.println("[srv] " + line);
                }
            }
        } catch (IOException e) {
            System.out.println("[erro de conexão] " + e.getMessage());
        }
    }
}
