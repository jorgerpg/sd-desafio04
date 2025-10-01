import server.QAQuizServer;     // Importa a classe do servidor do quiz
import client.QAQuizClient;     // Importa a classe do cliente do quiz

public class Main {
    public static void main(String[] args) throws Exception {
        // Uso (ajuda no terminal):
        //   Servidor: java Main server 0.0.0.0 6000 data.psv
        //   Cliente : java Main client 127.0.0.1 6000

        if (args.length < 1) {  // Se não passou nenhum argumento, mostra o uso e sai
            System.out.println("usage:\n  server <host> <port> <datafile>\n  client <host> <port>");
            return;
        }

        // Decide o modo de execução com base no primeiro argumento
        switch (args[0].toLowerCase()) {
            case "server" -> {
                // Lê parâmetros com valores padrão se não informados
                String host = (args.length > 1) ? args[1] : "0.0.0.0";           // Endereço para escutar
                int port = (args.length > 2) ? Integer.parseInt(args[2]) : 6000; // Porta do servidor
                String data = (args.length > 3) ? args[3] : "questions.psv";     // Arquivo de dados (PSV)

                // Cria e inicia o servidor (loop infinito)
                new QAQuizServer(host, port, data).serveForever();
            }
            case "client" -> {
                // Lê parâmetros com valores padrão se não informados
                String host = (args.length > 1) ? args[1] : "127.0.0.1";          // Endereço do servidor
                int port = (args.length > 2) ? Integer.parseInt(args[2]) : 6000; // Porta do servidor

                // Executa o cliente e conecta ao servidor
                QAQuizClient.run(host, port);
            }
            default -> System.out.println("unknown mode: " + args[0]); // Modo desconhecido
        }
    }
}
