import server.QAQuizServer;
import client.QAQuizClient;

public class Main {
    public static void main(String[] args) throws Exception {
        // Uso:
        //   Servidor: java Main server 0.0.0.0 6000 data.psv
        //   Cliente : java Main client 127.0.0.1 6000
        if (args.length < 1) {
            System.out.println("usage:\n  server <host> <port> <datafile>\n  client <host> <port>");
            return;
        }
        switch (args[0].toLowerCase()) {
            case "server" -> {
                String host = (args.length > 1) ? args[1] : "0.0.0.0";
                int port = (args.length > 2) ? Integer.parseInt(args[2]) : 6000;
                String data = (args.length > 3) ? args[3] : "questions.psv";
                new QAQuizServer(host, port, data).serveForever();
            }
            case "client" -> {
                String host = (args.length > 1) ? args[1] : "127.0.0.1";
                int port = (args.length > 2) ? Integer.parseInt(args[2]) : 6000;
                QAQuizClient.run(host, port);
            }
            default -> System.out.println("unknown mode: " + args[0]);
        }
    }
}
