# QA Quiz Game (Java)

Projeto de jogo de perguntas e respostas em **Java**, com suporte a múltiplos clientes e sincronização entre servidores (peers).  
Agora com **logs detalhados** no terminal para acompanhar a atividade do jogo e da rede.

---

## 🎮 Regras do jogo

- O **cliente** se conecta ao **servidor** e recebe **uma pergunta aleatória**.
- O cliente responde digitando apenas o **índice da alternativa** (`0`, `1`, `2`, …) ou `sair`.
- O servidor responde se a resposta está **certa ou errada** e mostra a **pontuação acumulada**.
- O jogo termina quando:
  - acabam as perguntas, ou
  - o cliente digita `sair`, ou
  - a conexão é encerrada.
- O servidor mantém a pontuação **só durante a sessão**.
- O banco de perguntas é salvo em um arquivo `.psv` legível.
- IDs das perguntas são **determinísticos** (baseados no conteúdo), evitando duplicatas na sincronização.

---

## 📂 Estrutura do projeto

```

src/
Main.java                # ponto de entrada
schema/Question.java     # modelo de pergunta (Serializable, ID determinístico)
server/QAQuizServer.java # servidor (jogo + sync + logs detalhados)
client/QAQuizClient.java # cliente (interface terminal)

````

---

## ⚙️ Compilação

Compile todos os arquivos para a pasta `out`:

```bash
javac -d out $(find src -name "*.java")
````

---

## 🚀 Execução

### Servidor

Inicie o servidor:

```bash
java -cp out Main server 0.0.0.0 6000 questions.psv
```

* `0.0.0.0` → endereço de escuta
* `6000` → porta do servidor
* `questions.psv` → arquivo local de perguntas

### Cliente

Conecte-se a um servidor:

```bash
java -cp out Main client 127.0.0.1 6000
```

---

## 💻 Exemplo de sessão (cliente)

```
[conectado] BANNER:QAQuizServer

[Python] Qual função imprime no console?
  0) scan()
  1) print()
  2) echo()
  3) show()
Resposta (índice) ou 'sair': 1
✅ Correto! | Pontuação: 1
```

---

## 🔄 Sincronização entre servidores

O servidor possui um **console administrativo** (no mesmo terminal onde roda).
Comandos disponíveis:

* `PEERS` → lista peers conhecidos
* `ADD_PEER <host> <port>` → adiciona um peer
* `PULL <host> <port>` → puxa todas as perguntas do peer e integra ao banco local
* `COUNT` → mostra total de perguntas

### Exemplo

Servidor A (porta 6000):

```bash
java -cp out Main server 0.0.0.0 6000 dataA.psv
```

Servidor B (porta 6001):

```bash
java -cp out Main server 0.0.0.0 6001 dataB.psv
```

No console do **Servidor A**:

```
ADD_PEER 127.0.0.1 6001
PULL 127.0.0.1 6001
COUNT
```

---

## 📜 Logs de feedback

O servidor agora exibe logs detalhados:

### Quando um cliente conecta e joga:

```
[SERVE] Escutando em /0.0.0.0:6000
[CONNECT] Conexão de /127.0.0.1:54321
[GAME] Cliente /127.0.0.1:54321 respondeu 3f1a2b (correto=true, score=1)
[GAME] Cliente /127.0.0.1:54321 terminou o jogo. Score=3
```

### Quando um peer pede EXPORT:

```
[PEER] Peer /127.0.0.1:54322 pediu EXPORT (10 perguntas)
[PEER] Enviadas 10 perguntas para /127.0.0.1:54322
```

### Quando este servidor faz PULL de outro:

```
[SYNC] Conectando ao peer 127.0.0.1:6001...
[SYNC] Recebido banner: BANNER:QAQuizServer
[SYNC] Pedido EXPORT enviado
[SYNC] Recebidas 10 perguntas, adicionadas 2 (total local 12)
[ADMIN] Importados 2 perguntas de 127.0.0.1:6001
```

---

## 📝 Banco de perguntas

Formato `.psv` (pipe-separated):

```
id|topic|text|opt0;;opt1;;opt2;;...|correctIndex
```

Exemplo:

```
3f1a2b|Java|Qual palavra-chave é usada para herdar uma classe em Java?|implements;;inherits;;extends;;super|2
```

* O **id** é calculado automaticamente (determinístico via `hashCode` do conteúdo).
* O mesmo conteúdo gera o **mesmo id**, evitando duplicação na sincronização entre servidores.

---