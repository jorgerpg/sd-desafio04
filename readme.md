# QA Quiz Game (Java)

Projeto de jogo de perguntas e respostas em **Java**, com suporte a múltiplos clientes e sincronização entre servidores (peers).

## 🎮 Regras do jogo

- O **cliente** se conecta a um **servidor** e recebe **uma pergunta aleatória** do banco.
- O cliente só precisa digitar o **índice da alternativa** (ou `sair` para encerrar).
- O servidor responde **certo/errado** e mostra a **pontuação acumulada**.
- O jogo termina quando:
  - acabarem as perguntas, ou
  - o usuário digitar `sair`, ou
  - encerrar o terminal (`Ctrl+C`).
- O servidor guarda a pontuação **apenas por sessão de cliente**.
- O banco de perguntas é salvo em arquivo `.psv`.

## 📂 Estrutura do projeto

```

src/
Main.java                # ponto de entrada
schema/Question.java     # modelo de pergunta
server/QAQuizServer.java # lógica do servidor + sync com peers
client/QAQuizClient.java # lógica do cliente

````

## ⚙️ Compilação

Compile todos os arquivos para a pasta `out`:

```bash
javac -d out $(find src -name "*.java")
````

## 🚀 Execução

### Servidor

Inicie o servidor em uma porta:

```bash
java -cp out Main server 0.0.0.0 6000 questions.psv
```

* `0.0.0.0` → endereço de escuta
* `6000` → porta do servidor
* `questions.psv` → arquivo local de perguntas

### Cliente

Conecte-se a um servidor existente:

```bash
java -cp out Main client 127.0.0.1 6000
```

No cliente:

* Você já recebe uma pergunta.
* Digite o índice da alternativa (`0`, `1`, …) ou `sair`.

### Exemplo de sessão (cliente)

```
[conectado] BANNER|QAQuizServer

[Python] Qual função imprime no console?
  0) scan()
  1) print()
  2) echo()
  3) show()
Resposta (índice) ou 'sair': 1
✅ Correto!
Pontuação: 1
```

## 🔄 Sincronização entre servidores

Do lado do **servidor**, existe um **console administrativo** no mesmo terminal onde ele roda.
Comandos disponíveis:

* `PEERS` → lista peers conhecidos
* `ADD_PEER <host> <port>` → adiciona um peer
* `PULL <host> <port>` → puxa todas as perguntas do peer e integra ao banco local
* `COUNT` → mostra total de perguntas
* `HELP` → ajuda

### Exemplo

Terminal A (porta 6000):

```bash
java -cp out Main server 0.0.0.0 6000 dataA.psv
```

Terminal B (porta 6001):

```bash
java -cp out Main server 0.0.0.0 6001 dataB.psv
```

No terminal do servidor A, digite:

```
ADD_PEER 127.0.0.1 6001
PULL 127.0.0.1 6001
COUNT
```

Agora o servidor A terá também as perguntas do servidor B.

## 📝 Banco de perguntas

Formato `.psv` (pipe-separated):

```
id|topic|text|opt0;;opt1;;opt2;;...|correctIndex
```

Exemplo:

```
123e4567-e89b-12d3-a456-426614174001|Java|Qual palavra-chave é usada para herdar uma classe em Java?|implements;;inherits;;extends;;super|2
```
