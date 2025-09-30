package client;

import schema.Question;

import java.io.*;
import java.net.*;
import java.util.Scanner;

public class QAQuizClient {
    public static void run(String host, int port) {
        try (Socket s = new Socket(host, port)) {
            ObjectOutputStream oos = new ObjectOutputStream(s.getOutputStream()); oos.flush();
            ObjectInputStream  ois = new ObjectInputStream(s.getInputStream());

            System.out.println("[conectado] " + ois.readObject());

            Scanner sc = new Scanner(System.in);
            while (true) {
                Object obj = ois.readObject();

                if (obj instanceof Question q) {
                    System.out.println("\n[" + q.topic + "] " + q.text);
                    for (int i = 0; i < q.options.size(); i++) {
                        System.out.println("  " + i + ") " + q.options.get(i));
                    }
                    System.out.print("Resposta (índice) ou 'sair': ");
                    String ans = sc.nextLine().trim();
                    if (ans.equalsIgnoreCase("sair")) {
                        oos.writeObject("SAIR"); oos.flush(); break;
                    } else {
                        oos.writeObject("ANSWER:" + q.id + ":" + ans); oos.flush();
                    }
                } else if (obj instanceof String msg) {
                    if (msg.startsWith("RESULT|")) {
                        String[] p = msg.split("\\|");
                        System.out.println((p[1].equals("true") ? "✅ Correto!" : "❌ Errado.") +
                                " | Pontuação: " + p[2]);
                    } else if (msg.startsWith("END|")) {
                        System.out.println("Fim do jogo. Pontuação: " + msg.substring(4)); break;
                    } else if (msg.startsWith("BYE|")) {
                        System.out.println("Encerrado. Pontuação: " + msg.substring(4)); break;
                    }
                }
            }
        } catch (Exception e) { System.out.println("[erro] " + e.getMessage()); }
    }
}
