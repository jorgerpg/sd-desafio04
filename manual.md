# 📖 Contrato do `schema.Question`

Todo servidor deve conter uma classe (ou equivalente em outra linguagem) chamada `Question`, com os seguintes **requisitos obrigatórios**:

```java
package schema;

import java.io.Serializable;
import java.util.List;

public class Question implements Serializable {
    private static final long serialVersionUID = 1L;

    // Campos obrigatórios
    public final String id;       // gerado de forma determinística
    public final String topic;    // tema da pergunta (ex: "Java")
    public final String text;     // enunciado da pergunta
    public final List<String> options; // alternativas (ordem importa)
    public final int correct;     // índice da alternativa correta (0-based)

    // Construtor principal
    public Question(String topic, String text, List<String> options, int correct) {
        this.id = generateId(topic, text, options, correct);
        this.topic = topic;
        this.text = text;
        this.options = List.copyOf(options);
        this.correct = correct;
    }

    // ----------------------------
    // Geração de ID determinístico
    // ----------------------------
    private static String generateId(String topic, String text, List<String> options, int correct) {
        String key = topic + "|" + text + "|" + String.join(";", options) + "|" + correct;
        return Integer.toHexString(key.hashCode());
    }
}
```

---

## ✅ Por que isso é necessário?

* **`implements Serializable`** → garante que objetos `Question` possam ser enviados entre servidores e clientes via `ObjectOutputStream` / `ObjectInputStream`.
* **`id determinístico`** → impede duplicatas: se duas perguntas são iguais em conteúdo, terão o mesmo `id` em qualquer servidor.
* **campos obrigatórios** → garantem que todos os servidores e clientes saibam exatamente o que esperar ao importar/exportar.

---

## 📦 Campos obrigatórios explicados

* `id`: string calculada pelo hash do conteúdo (não é um UUID aleatório).
* `topic`: área da questão (ex.: *Python*, *Redes*).
* `text`: enunciado da questão.
* `options`: lista de alternativas (ordem importa para manter coerência).
* `correct`: índice da alternativa correta (0 = primeira, 1 = segunda, etc).

---

## 📝 Observação sobre banco local

* Como o formato do banco **é irrelevante** para o servidor, o desenvolvedor pode salvar as perguntas em `.psv`, `.json`, banco SQL, NoSQL, etc.
* O importante é que ao carregar, o servidor consiga preencher objetos `Question` **neste formato obrigatório**.


GERADO POR IA RAPEIZE!