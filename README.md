# 🌱 CacauSync API — Back-end Central para Ensaios Genéticos (CEPEC/CEPLAC)

API REST corporativa desenvolvida em **Java 21 + Spring Boot 3** para orquestração de rotas de campo, sincronização *offline-first* de coletas fenotípicas e análise estatística de produtividade por clone de cacaueiro no contexto do **Centro de Pesquisas do Cacau (CEPEC/CEPLAC)**.

> 📱 **Ecossistema Integrado:** Este back-end atua em conjunto com o aplicativo Android de Visão Computacional Offline (**CacauApp**), que utiliza **TensorFlow Lite (YOLOv8 / Object Detection)** em campo (sem conectividade de internet) para contagem de frutos na planta e exportação padronizada em CSV.

---

## 🎯 O Problema Real Resolvido

Em quadras experimentais de melhoramento genético de cacau (como o ensaio competitivo **TC-1801**, contendo ~1.000 plantas e mais de 10.000 avaliações históricas), os pesquisadores e técnicos agrícolas atuam em áreas rurais sem sinal de internet:

1. **Pré-Campo (Planejamento):** O pesquisador exporta da **CacauSync API** a planilha de rota (`GET /api/v1/ensaios/TC-1801/rota-csv`) e carrega no **Modo Rota (FieldBook)** do aplicativo Android.
2. **Em Campo (Offline + IA):** O aplicativo guia o técnico planta a planta, realiza a contagem automática de frutos via câmera com modelo `.tflite` (ou registra baixa fitossanitária como `"Não Coletável"` para plantas mortas/secas/ausentes) e grava coordenadas GPS (`Lat`/`Lng`).
3. **Pós-Campo (Sincronização no Laboratório):** De volta ao laboratório com rede local, o pesquisador envia o CSV gerado pelo celular para `POST /api/v1/coletas/importar-csv`. A API valida duplicidades (**idempotência**), atualiza automaticamente o estande de plantas vivas/mortas, audita leituras sem sinal de GPS e consolida o ranking de produtividade por clone (`CCN-51`, `SCP-16005`, `PS-13.19`, `SCP-17202`).

---

## 🛠️ Stack Tecnológica & Arquitetura

* **Java 21 + Spring Boot 3.3.5:** Arquitetura limpa em camadas (`Controller` $\rightarrow$ `Service` $\rightarrow$ `Repository` $\rightarrow$ `Entity`), uso de `Java Records` para DTOs imutáveis e tratamento global de exceções no padrão **RFC 7807 (`ProblemDetail`)**.
* **Banco de Dados Relacional (PostgreSQL 16 + Spring Data JPA + Flyway):**
  * Hierarquia relacional completa: `Ensaio` $\rightarrow$ `Parcela` $\leftrightarrow$ `Clone` $\rightarrow$ `Planta` $\rightarrow$ `LeituraColeta`.
  * Versionamento de schema automatizado via **Flyway** (`V1__create_schema.sql` e `V2__seed_ensaio_tc1801.sql` com dados reais do ensaio `TC-1801`).
* **Segurança (Spring Security 6 + Auth0 JWT):**
  * Autenticação *stateless* via Bearer Token JWT com controle de acesso baseado em papéis (`ROLE_COORDENADOR` e `ROLE_PESQUISADOR`).
* **Documentação Interativa & Painel Visual:**
  * **Swagger UI (SpringDoc OpenAPI 3)** configurado com botão *Authorize (JWT)*.
  * **Painel Web do Pesquisador** integrado em `/` (`index.html`) para upload visual de CSV e acompanhamento do ranking de clones em tempo real.
* **Qualidade de Código (JUnit 5 + Mockito + AssertJ):**
  * Testes unitários validando o parser RFC 4180 dos dois formatos CSV do Android, idempotência de sincronização, baixa de mortalidade (`"Não Coletável"`) e cálculo de média de frutos por planta viva descontando falhas de estande.
* **Containerização (Docker & Docker Compose):**
  * Arquivo `docker-compose.yml` na raiz para subir o banco PostgreSQL pronto para uso com um único comando.

---

## 🚀 Como Executar o Projeto Localmente

### 1. Subir o Banco de Dados PostgreSQL com Docker Compose
Na raiz do projeto, execute:
```bash
docker compose up -d
```
O container `cacausync-postgres` será iniciado na porta `5432` (`cacausync_db`).

### 2. Executar a API Spring Boot
```bash
mvn spring-boot:run
```
Ao iniciar, o **Flyway** executará automaticamente as migrations `V1` e `V2`, criando as tabelas e populando o ensaio `TC-1801`, clones, parcelas, plantas e usuários de demonstração.

### 3. Acessar no Navegador
* **Painel Web do Pesquisador (Dashboard):** [http://localhost:8080](http://localhost:8080)
* **Documentação Interativa (Swagger UI):** [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

**Credenciais de Demonstração (Seed Flyway V2):**
| Perfil | E-mail | Senha |
| :--- | :--- | :--- |
| **Coordenador Genético** | `coordenador@cepec.gov.br` | `cepec123` |
| **Pesquisador de Campo** | `pesquisador@cepec.gov.br` | `cepec123` |

---

## 🧪 Executando os Testes Unitários (JUnit 5 + Mockito)

```bash
mvn test
```

A suíte de testes cobre:
1. **`CsvColetaParserTest`**: Leitura dos dois formatos de CSV exportados pelo app Android (`exportarCSVCEPEC_Personalizado` de 9 colunas e `exportarCSVFinal` de 7 colunas), remoção de UTF-8 BOM e campos escapados com vírgula/aspas (RFC 4180).
2. **`ColetaSyncServiceTest`**: Garantia de idempotência no reenvio de planilhas, conversão automática de `"Não Coletável"` em status `NAO_COLETAVEL` da planta e alerta de geolocalização quando o GPS vem zerado (`0.0, 0.0`).
3. **`EnsaioServiceTest`**: Cálculo de produtividade e taxa de sobrevivência por clone (`CCN-51` vs `SCP-16005`), garantindo que plantas mortas/não coletáveis não distorçam a média de frutos por planta produtiva.
4. **`JwtServiceTest`**: Emissão, validação de assinatura HMAC256 e rejeição de tokens adulterados.
