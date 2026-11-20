# VMTRANSLATOR

Tradutor da linguagem VM do curso **nand2tetris** (projeto 7) para Assembly **Hack**. Recebe um arquivo `.vm` e gera um `.asm` equivalente, que pode ser executado no CPUEmulator.

## 1. Integrantes

- **Raylan Bruno Santana Carvalho** — Matrícula: `20260001132`
- **Kauan Guilherme Alves Pinheiro Santos** — Matrícula: `20260001160`

## 2. Pré-requisitos

- **JDK 17 ou superior**, com `JAVA_HOME` apontando para ele
- **Maven 3.9+** (opcional; sem ele, dá para compilar com `javac`, veja a seção 4)

Para conferir:

```bash
java -version
mvn -v
```

## 3. Estrutura do projeto

```
src/
  Main.java                 Ponto de entrada: lê o .vm e grava o .asm
  parser/
    Parser.java             Lê o .vm e separa cada comando em partes
    CommandType.java        Tipos de comando (C_ARITHMETIC, C_PUSH, C_POP)
  codewrite/
    CodeWriter.java         Gera o Assembly Hack de cada comando
test/
  unit/                     Testes JUnit
  validation/               Programas oficiais do projeto 7 (.vm, .tst, .cmp)
```

## 4. Como funciona

O `Main` liga as duas classes principais:

```
arquivo.vm ──► Parser ──► CodeWriter ──► arquivo.asm
```

### Parser

- Lê o arquivo linha a linha e ignora linhas vazias e comentários (`//`).
- `advance()` passa para o próximo comando.
- `commandType()` diz o tipo do comando, `arg1()` devolve o segmento (ou a operação, em comandos aritméticos) e `arg2()` devolve o índice.

### CodeWriter

A VM é uma máquina de pilha. O ponteiro da pilha (`SP`) fica em `RAM[0]` e aponta para a próxima posição livre.

**Comandos aritméticos e lógicos** (`writeArithmetic`):

- **Binários** (`add`, `sub`, `and`, `or`): desempilham `y` (topo) e operam com `x` (logo abaixo). O resultado fica no lugar de `x`. O `sub` calcula `x - y`.
- **Unários** (`neg`, `not`): alteram o topo da pilha direto.
- **Comparações** (`eq`, `gt`, `lt`): deixam `-1` na pilha se a condição for verdadeira e `0` se for falsa. Cada comparação gera um rótulo único (`COMP_TRUE_0`, `COMP_TRUE_1`, …), para que os saltos não se confundam.

**Acesso à memória** (`writePush` / `writePop`), seguindo o mapeamento da RAM Hack:

| Segmento | Endereço |
|---|---|
| `constant` | Não é memória: o próprio número é empilhado (não existe `pop constant`) |
| `local` | `RAM[LCL] + i` (`LCL` = `RAM[1]`) |
| `argument` | `RAM[ARG] + i` (`ARG` = `RAM[2]`) |
| `this` | `RAM[THIS] + i` (`THIS` = `RAM[3]`) |
| `that` | `RAM[THAT] + i` (`THAT` = `RAM[4]`) |
| `pointer` | `pointer 0` = `THIS`, `pointer 1` = `THAT` |
| `temp` | `RAM[5 + i]`, com `i` de 0 a 7 |
| `static` | Variável Assembly `Arquivo.i` (ex.: `StaticTest.3`), alocada pelo montador a partir de `RAM[16]` |

No `pop` para `local`, `argument`, `this` e `that`, o endereço de destino é calculado antes e guardado em **R13**. Isso é necessário porque o registrador `D` vai ser ocupado pelo valor desempilhado.

O tradutor **não inicializa** `SP`, `LCL`, `ARG`, `THIS` nem `THAT`. Quem define esses valores são os scripts `.tst`.

## 5. Rodando os testes

### Windows (PowerShell)

Na raiz do projeto:

```powershell
mvn test
```

Quando tudo passa, o fim da saída mostra `Tests run: ..., Failures: 0, Errors: 0` e `BUILD SUCCESS`.

Para rodar só uma classe ou um único teste:

```powershell
mvn test -Dtest=CodeWriterTest
mvn test -Dtest=CodeWriterTest#validacaoOficialBasicTest
```

### Linux

```powershell
comando linux aqui
```

## 6. Gerando o arquivo `.asm`

O tradutor recebe **o caminho de um arquivo `.vm`**. Existem vários programas de teste em `test/validation/`, e cada um precisa ser traduzido separadamente. Nos comandos abaixo o exemplo usa o **BasicTest**, mas **esse caminho muda** conforme o teste que você quer gerar:

| Teste | Caminho do `.vm` |
|---|---|
| SimpleAdd | `test/validation/StackArithmetic/SimpleAdd/SimpleAdd.vm` |
| StackTest | `test/validation/StackArithmetic/StackTest/StackTest.vm` |
| BasicTest | `test/validation/MemoryAccess/BasicTest/BasicTest.vm` |
| PointerTest | `test/validation/MemoryAccess/PointerTest/PointerTest.vm` |
| StaticTest | `test/validation/MemoryAccess/StaticTest/StaticTest.vm` |

### Windows (PowerShell)

**Com Maven:**

```powershell
mvn compile exec:java "-Dexec.args=test/validation/MemoryAccess/BasicTest/BasicTest.vm"
```

**Sem Maven:**

```powershell
javac -d out src/Main.java src/parser/*.java src/codewrite/*.java
java -cp out Main test/validation/MemoryAccess/BasicTest/BasicTest.vm
```

Nos dois casos, o `.asm` é criado **na mesma pasta do `.vm`**, com o mesmo nome:

```
Gerado: test\validation\MemoryAccess\BasicTest\BasicTest.asm
```

### Linux

```powershell
comando linux aqui
```