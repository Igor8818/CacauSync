# Guia de Estudos e Arquitetura CacauSync
## Da Coleta no Campo (Android / Edge AI) à Auditoria Científica (Spring Boot 3 + PostgreSQL)

> **Documento Institucional de Capacitação e Engenharia de Software**  
> **Autor:** Igor Santos  
> **Projeto:** CacauSync (Backend Corporativo do CacauApp - CEPEC / CEPLAC)  
> **Tecnologias:** Java 21, Spring Boot 3.3.5, Spring Security 6 (JWT Stateless), Spring Data JPA, Hibernate, PostgreSQL 16, Flyway, Docker Compose, JUnit 5, Mockito, Swagger OpenAPI 3.

---

## Sumário
1. [Visão Geral e Contexto de Negócio (O Problema Real)](#1-visão-geral-e-contexto-de-negócio-o-problema-real)
2. [A Jornada dos Dados: Do Campo ao Laboratório (Para Leigos e Especialistas)](#2-a-jornada-dos-dados-do-campo-ao-laboratório)
3. [Como o Celular Envia Dados e Fotos para o Backend](#3-como-o-celular-envia-dados-e-fotos-para-o-backend)
4. [Fundamentos de Java 21 Utilizados](#4-fundamentos-de-java-21-utilizados)
5. [Arquitetura Spring Boot 3 em Camadas](#5-arquitetura-spring-boot-3-em-camadas)
6. [Autenticação e Segurança: Spring Security 6 + JWT](#6-autenticação-e-segurança-spring-security-6--jwt)
7. [Banco de Dados, JPA, Hibernate e Migrações Flyway](#7-banco-de-dados-jpa-hibernate-e-migrações-flyway)
8. [Armazenamento Seguro de Fotos e Prevenção de Vulnerabilidades](#8-armazenamento-seguro-de-fotos-e-prevenção-de-vulnerabilidades)
9. [Auditoria e Human-in-the-Loop (Inspeção de Detecções YOLO)](#9-auditoria-e-human-in-the-loop-inspeção-de-detecções-yolo)
10. [Pirâmide de Testes Automatizados (JUnit 5 + Mockito)](#10-pirâmide-de-testes-automatizados-junit-5--mockito)
11. [Roteiro de Respostas para Entrevistas de Emprego (Capgemini, Just Travel, etc.)](#11-roteiro-de-respostas-para-entrevistas-de-emprego)

---

## 1. Visão Geral e Contexto de Negócio (O Problema Real)

A **CEPLAC** (Comissão Executiva do Plano da Lavoura Cacaueira) e seu centro de pesquisas, o **CEPEC** (Ilhéus/Bahia), conduzem experimentos de melhoramento genético com **clones de cacau** (como *CCN-51*, *CEPEC-2002*, *PS-1319*, *IPIRANGA-01*).

### O Desafio
* Os ensaios de campo possuem dezenas de fileiras e centenas de plantas.
* No meio da mata/roça, **não há sinal de internet (nem 3G/4G, nem Wi-Fi)**.
* Historicamente, técnicos usavam pranchetas de papel, sujeitas a chuva, perda de anotações e erros de digitação ao transcrever para o Excel.
* O **CacauApp (Android)** resolveu a coleta com visão computacional (YOLOv8 via TensorFlow Lite) rodando localmente no celular (Edge Computing).
* **Faltava o principal elo corporativo:** Um servidor central seguro que recebesse esses dados, garantisse que coletas repetidas não duplicassem a contagem, armazenasse as fotos dos frutos, permitisse aos pesquisadores auditar visualmente a contagem da IA (*Human-in-the-loop*) e gerasse relatórios estatísticos confiáveis para publicação científica.

---

## 2. A Jornada dos Dados: Do Campo ao Laboratório

Imagine esta história do dia a dia:

```
[ Mata / Roça de Cacau ]                     [ Sede da CEPLAC / Wi-Fi ]
  Técnico + Celular                           Servidor Central CacauSync
  (Sem Internet)                                (Spring Boot + Postgres)
        │                                                  │
        ├─ 1. Aponta câmera pro cacaueiro                  │
        ├─ 2. IA no celular detecta frutos                 │
        ├─ 3. Tira foto (salva em Pictures/CEPEC_Analise)   │
        ├─ 4. Salva CSV com Linha, Planta, Clone, Qtd      │
        │                                                  │
        │           === Técnico volta à sede ===           │
        │                                                  │
        ├──────────────── 5. Sincronização ───────────────>│
        │         (Upload CSV + Fotos via Web ou API)      │
        │                                                  │
                                                           ├─ 6. Validação de Duplicatas (Idempotência)
                                                           ├─ 7. Amarração Foto <-> Linha da Coleta
                                                           ├─ 8. Cálculo de Produtividade dos Clones
                                                           │
                                                           ▼
                                               [ Laboratório de Genética ]
                                                 Pesquisador no Computador
                                                 Audita Fotos com Caixas YOLO
                                                 Valida / Corrige Contagem
```

### Explicação Simples (Para Leigos)
1. **O Olho na Roça:** O celular é como os olhos do técnico. Ele olha para o tronco da árvore, conta quantas frutas vê e tira uma foto para guardar como prova.
2. **A Mala Fechada:** Como não tem internet lá dentro, o celular guarda essas fotos e anotações numa maleta digital (um arquivo de texto chamado CSV e os arquivos de foto).
3. **A Chegada no Escritório:** Ao chegar na sede do CEPEC, onde tem Wi-Fi e computadores, o técnico entrega essa "mala" para o sistema central (o nosso **CacauSync**).
4. **O Guarda da Portaria (Segurança):** O CacauSync não deixa qualquer um entrar. Só quem tem o crachá digital (o token JWT de Técnico ou Pesquisador) pode entregar dados.
5. **A Conferência Inteligente:** Se o técnico enviar a mesma lista duas vezes sem querer, o CacauSync percebe ("eu já registrei a planta 5 da linha 2 hoje!") e não conta duas vezes.
6. **O Julgamento Científico:** O pesquisador senta no laboratório, abre o sistema no navegador, clica na planta e vê a foto do cacau com os quadrados coloridos desenhados pela IA. Se a IA contou 4 cacaus, mas 1 era folha seca, o pesquisador ajusta para 3. O sistema atualiza o ranking genético na hora.

---

## 3. Como o Celular Envia Dados e Fotos para o Backend

Existem **duas formas complementares** de integração com o aplicativo Android:

### Forma 1: Sem Modificar o App Atual (Pronta para Usar Hoje)
O aplicativo Android já salva o CSV na pasta de downloads e as fotos na pasta `Pictures/CEPEC_Analise` com o nome no padrão:
`${id_gerado}_${timestamp}.jpg` (Ex: `A102_1710928392100.jpg`).

* O técnico abre o navegador do celular ou do computador (conectado na mesma rede Wi-Fi da CEPLAC) acessando `http://ip-do-servidor:8080`.
* Na tela do **CacauSync**, ele seleciona o ensaio, escolhe o arquivo CSV e arrasta a pasta de fotos.
* O frontend do CacauSync compacta as fotos no cliente para economizar banda e envia em lote via `multipart/form-data`.
* O backend (classe `FotoMatcher`) lê o nome de cada foto, extrai o ID gerado e amarra perfeitamente à linha correspondente no banco de dados.

### Forma 2: Sincronização Direta pelo Celular (Para Futuras Atualizações do App)
Criamos no backend o endpoint:
`POST /api/v1/coletas/sync-json`

O aplicativo Android pode fazer uma requisição HTTP enviando um JSON com as caixas delimitadoras (*bounding boxes*) da detecção YOLO:

```json
{
  "ensaioId": 1,
  "leitorNome": "Carlos Técnico",
  "dataHora": "2026-10-28T14:30:00",
  "linha": 2,
  "planta": 5,
  "clone": "CCN-51",
  "statusPlanta": "NORMAL",
  "quantidadeFrutos": 7,
  "observacao": "Coleta matinal parcela norte",
  "nomeArquivoFoto": "A102_1710928392100.jpg",
  "deteccoes": [
    {
      "classe": "cacau_sadio",
      "confianca": 0.94,
      "ymin": 0.20,
      "xmin": 0.35,
      "ymax": 0.55,
      "xmax": 0.62
    },
    {
      "classe": "vassoura_bruxa",
      "confianca": 0.88,
      "ymin": 0.60,
      "xmin": 0.40,
      "ymax": 0.85,
      "xmax": 0.70
    }
  ]
}
```

---

## 4. Fundamentos de Java 21 Utilizados

O CacauSync utiliza os recursos mais modernos da plataforma **Java 21 LTS**:

### A. Java Records (DTOs Imutáveis)
Em vez de criar classes verbosas com dezenas de getters, setters, `equals()`, `hashCode()` e `toString()`, usamos **Records**:

```java
public record LoginRequest(
    @NotBlank(message = "Login institucional é obrigatório")
    String login,

    @NotBlank(message = "Senha é obrigatória")
    String senha
) {}
```
* **Benefício:** Imutabilidade garantida por padrão, código limpo, sem boilerplate de Lombok e thread-safe.

### B. Pattern Matching para `instanceof` e `switch`
Reduz casts manuais perigosos em validações e tratamento de exceções.

### C. `var` com Inferência de Tipos Local
Usado onde a legibilidade não é comprometida, deixando o código conciso.

---

## 5. Arquitetura Spring Boot 3 em Camadas

O sistema segue a arquitetura em camadas corporativa (*Clean Layered Architecture*):

```
                        ┌───────────────────────────────┐
                        │      Cliente (Web / App)      │
                        └───────────────┬───────────────┘
                                        │ HTTP / JSON / Multipart
                                        ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ Camada Controller (REST Endpoints)                                      │
│ - AuthController: /api/v1/auth/login                                    │
│ - ColetaController: /api/v1/coletas/importar-csv, /sync-json            │
│ - LeituraController: /api/v1/leituras/{id}/validar, /foto               │
│ - EnsaioController: /api/v1/ensaios/{id}/relatorio-produtividade        │
└───────────────────────────────────────┬─────────────────────────────────┘
                                        │ DTOs (Data Transfer Objects)
                                        ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ Camada Service (Regras de Negócio Institucionais)                       │
│ - ColetaSyncService: Idempotência, parsing CSV, persistência em lote    │
│ - LeituraService: Auditoria científica, ajuste de contagem, busca foto  │
│ - EnsaioService: Agregações estatísticas de produtividade por clone     │
│ - FotoStorageService: Armazenamento local seguro, validação de MIME     │
│ - FotoMatcher: Associação determinística Foto <-> Linha de Coleta      │
└───────────────────────────────────────┬─────────────────────────────────┘
                                        │ Entidades de Domínio
                                        ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ Camada Repository (Spring Data JPA)                                     │
│ - LeituraColetaRepository: Consultas JPQL otimizadas, índices           │
│ - UsuarioRepository, EnsaioRepository, CloneCacauRepository            │
└───────────────────────────────────────┬─────────────────────────────────┘
                                        │ SQL via Hibernate
                                        ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ Banco de Dados PostgreSQL 16 (Tabelas Versionadas por Flyway)          │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 6. Autenticação e Segurança: Spring Security 6 + JWT

### Por que JWT Stateless em vez de Sessão de Servidor?
1. **Escalabilidade:** O servidor não guarda sessão em memória (não usa JSESSIONID em disco/RAM). Se tivermos 1.000 técnicos enviando dados simultaneamente, o consumo de memória é mínimo.
2. **Mobile-Friendly:** Celulares funcionam muito melhor com cabeçalho `Authorization: Bearer <token>` do que com cookies de sessão.

### O Fluxo Completo de Segurança
1. O usuário envia `POST /api/v1/auth/login` com login e senha.
2. O `AuthenticationManager` do Spring consulta o `UsuarioRepository`.
3. A senha recebida é comparada com o hash salvo no banco via **BCrypt** (`BCryptPasswordEncoder`). A senha real nunca é armazenada em texto puro.
4. Se válida, o `JwtService` assina criptograficamente um token com chave HMAC-SHA256 (`secret-key`), contendo:
   * **Subject:** Login do usuário
   * **Claims:** Perfil (`ROLE_PESQUISADOR` ou `ROLE_TECNICO`) e Nome
   * **Expiration:** Tempo de vida limitado (ex: 24 horas).
5. Nas próximas requisições, o filtro `JwtAuthenticationFilter` intercepta a chamada antes do Controller:
   * Extrai o token do header `Authorization: Bearer ...`
   * Valida a assinatura e a expiração.
   * Coloca o usuário no `SecurityContextHolder` do Spring.

### Matriz de Permissões RBAC (Role-Based Access Control)
* `/api/v1/auth/**`, `/swagger-ui/**`, `/v3/api-docs/**`, `/index.html`: **Acesso Público**
* `POST /api/v1/coletas/**`: **ROLE_TECNICO** ou **ROLE_PESQUISADOR**
* `PATCH /api/v1/leituras/{id}/validar`: **Apenas ROLE_PESQUISADOR** (Técnico não pode validar sua própria contagem)
* `GET /api/v1/ensaios/{id}/relatorio-produtividade`: **ROLE_PESQUISADOR**

---

## 7. Banco de Dados, JPA, Hibernate e Migrações Flyway

### Por que usar Flyway em vez de `ddl-auto: update`?
* **O Risco do `ddl-auto`:** Em empresas sérias, nunca se usa `spring.jpa.hibernate.ddl-auto=update` em produção. O Hibernate pode alterar colunas acidentalmente, travar tabelas grandes com *locks* pesados ou apagar dados.
* **O Padrão Flyway:** Todas as mudanças no banco são arquivos SQL versionados (`V1__...sql`, `V2__...sql`, `V3__...sql`, `V4__...sql`). Toda vez que o sistema sobe, ele aplica apenas as migrações que ainda não rodaram, gravando o histórico na tabela `flyway_schema_history`.

### As Migrações do CacauSync:
1. `V1__init_schema.sql`: Cria as tabelas `usuarios`, `ensaios`, `clones_cacau`, `plantas_ensaio` e `leituras_coleta`.
2. `V2__seed_cepec_initial_data.sql`: Popula clones da CEPLAC (CCN-51, CEPEC-2002, etc.), o ensaio experimental e os usuários padrão.
3. `V3__update_default_users_password.sql`: Atualiza as senhas para o hash BCrypt correto de `cepec123`.
4. `V4__fotos_e_validacao_leituras.sql`: Adiciona colunas para chave da foto (`foto_chave`), status da validação (`status_validacao`), contagem corrigida (`qtd_frutos_validada`), auditor (`validado_por`) e detecções JSON (`deteccoes_json`).

### Regra de Idempotência (Chave Natural Composta)
Para impedir que o técnico envie o mesmo CSV duas vezes e duplique a colheita, criamos uma restrição de unicidade:
```sql
CONSTRAINT uk_leitura_ensaio_linha_planta_data UNIQUE (ensaio_id, linha, planta, data_hora)
```
No código Java, o `ColetaSyncService` captura a tentativa de duplicata e ignora ou atualiza conforme a regra de negócio.

---

## 8. Armazenamento Seguro de Fotos e Prevenção de Vulnerabilidades

O `FotoStorageService` foi desenhado seguindo as diretrizes da **OWASP**:

1. **Prevenção de Path Traversal:**
   Se um usuário mal-intencionado enviar um arquivo com o nome `../../../../etc/passwd` ou `C:\Windows\System32\cmd.exe`, o serviço extrai apenas o nome seguro e gera um identificador único com **UUIDv4**:
   ```java
   String chave = UUID.randomUUID() + "_" + nomeLimpo;
   ```
2. **Inspeção de Assinatura de Arquivo (Magic Bytes):**
   Não confiamos apenas na extensão `.jpg`. O backend inspeciona os primeiros bytes do arquivo para garantir que se trata de uma imagem JPEG (`FF D8 FF`) ou PNG real.
3. **Desacoplamento do Armazenamento:**
   O caminho das fotos fica isolado em uma pasta gerenciada (`storage.fotos-dir`), permitindo fácil migração futura para **AWS S3** ou **MinIO** sem alterar a regra de negócio.

---

## 9. Auditoria e Human-in-the-Loop (Inspeção de Detecções YOLO)

### O Conceito Científico de "Human-in-the-Loop"
Modelos de inteligência artificial treinados no campo sofrem com variações de luminosidade, folhas secas cobrindo os frutos e cascas manchadas por fungos (como a Vassoura-de-Bruxa ou Podridão Parda).
Nenhum pesquisador publica um artigo científico de genética vegetal baseando-se 100% em números crus da IA sem amostragem auditável.

### O Funcionamento no CacauSync:
1. O técnico coleta os dados no campo.
2. O status inicial de cada leitura é `PENDENTE`.
3. O pesquisador acessa a tabela de auditoria e clica no botão **"Inspecionar"**.
4. O modal carrega a imagem real e sobrepõe, via elemento HTML5 `<canvas>`, os retângulos coloridos:
   * **Verde:** Cacau sadio
   * **Amarelo:** Cacau com sintomas suspeitos
   * **Vermelho:** Fruto doente (ex: Vassoura-de-Bruxa)
5. O pesquisador pode:
   * **Aprovar:** Confirma que a contagem da IA está correta (Status: `VALIDADA`).
   * **Ajustar:** Corrige a quantidade se a IA errou (Status: `AJUSTADA`).
   * **Rejeitar:** Descarta a foto em caso de foto borrada ou planta errada (Status: `REJEITADA`).
6. **Efeito no Relatório:** As estatísticas de produtividade do clone utilizam sempre o valor validado pelo pesquisador.

---

## 10. Pirâmide de Testes Automatizados (JUnit 5 + Mockito)

O projeto possui **35 testes automatizados com 100% de sucesso**.

### A. Testes Unitários com Mockito
Testam a regra de negócio isolada, sem subir banco de dados ou servidor web (execução em milissegundos).
* `@ExtendWith(MockitoExtension.class)`
* `@Mock`: Simula o repositório ou serviço externo.
* `@InjectMocks`: Injeta os mocks na classe que está sendo testada.
* `when(...).thenReturn(...)`: Define o comportamento esperado do mock.
* `verify(...)`: Confirma se o método foi chamado a quantidade correta de vezes.

### B. Testes de Integração e Segurança
Testam se a configuração de segurança do Spring Security está realmente bloqueando quem não tem permissão:
* `SecurityRulesIntegrationTest.java`:
  * Garante que requisições anônimas para `/api/v1/coletas/**` tomam `401 Unauthorized` ou `403 Forbidden`.
  * Garante que um usuário com `ROLE_TECNICO` não consegue chamar o endpoint de validar leitura (`403 Forbidden`).
  * Garante que `ROLE_PESQUISADOR` consegue validar leituras com sucesso (`200 OK`).

---

## 11. Roteiro de Respostas para Entrevistas de Emprego

### Para vagas na Capgemini / Just Travel / Consultorias Globais:

#### Pergunta 1: "Como você desenhou a arquitetura do projeto CacauSync?"
> **Resposta Modelo:**  
> *"Eu arquitetei o CacauSync para resolver um problema real de pesquisa agronômica na CEPLAC/Bahia, onde tínhamos coleta offline no campo usando visão computacional (YOLO/TFLite) no Android e precisávamos de um backend corporativo centralizado. Utilizei Java 21 e Spring Boot 3.3.5 estruturado em camadas (Controller, Service, Repository). Implementei autenticação stateless via Spring Security 6 com JWT e RBAC (Pesquisador vs Técnico), persistência com Spring Data JPA sobre PostgreSQL 16 e versionamento de banco com Flyway. Como diferencial sênior, implementei um fluxo de 'Human-in-the-Loop', onde fotos com caixas delimitadoras de IA são armazenadas com segurança e auditadas por pesquisadores através de uma interface web integrada."*

#### Pergunta 2: "Como você tratou a segurança e autenticação?"
> **Resposta Modelo:**  
> *"Adotei autenticação stateless com tokens JWT assinados com HMAC-SHA256 e senhas protegidas com BCrypt. Criei um `JwtAuthenticationFilter` que intercepta requisições HTTP e popula o `SecurityContextHolder`. As permissões são granulares: técnicos de campo têm permissão de escrita de coletas, mas apenas pesquisadores autenticados com `ROLE_PESQUISADOR` podem auditar e alterar status de contagem científica. Além disso, no upload de fotos, implementei sanitização contra Path Traversal e validação de magic bytes para garantir que apenas arquivos JPEG/PNG legítimos sejam processados."*

#### Pergunta 3: "Como você evitou que a mesma coleta fosse enviada duas vezes pelo celular?"
> **Resposta Modelo:**  
> *"Trabalhei o conceito de idempotência em duas etapas: no banco de dados, através de uma restrição única composta (Unique Constraint) combinando ID do ensaio, linha, planta e timestamp da leitura; e na camada de serviço (`ColetaSyncService`), que detecta conflitos de chave antes de persistir, evitando duplicações acidentais caso o operador envie a mesma remessa de dados mais de uma vez ao retornar do campo."*

#### Pergunta 4: "Como você garantiu a qualidade do código?"
> **Resposta Modelo:**  
> *"Segui práticas de Clean Code e TDD/pirâmide de testes. Escrevi testes unitários com JUnit 5 e Mockito cobrindo regras de negócio críticas (como o cálculo estatístico de produtividade por clone e a associação determinística de fotos via `FotoMatcher`), e testes de integração de segurança com MockMvc para validar as regras do Spring Security e RBAC. Todo o build passa com zero falhas e o banco é 100% reproduzível via migrações Flyway e Docker Compose."*

---

*Documento gerado como material de referência para o projeto CacauSync - CEPEC / CEPLAC.*
