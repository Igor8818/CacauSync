<div align="center">
  <img src="logo_cacausync.jpg" alt="CacauSync API Banner" width="100%" />
</div>

# 🌱 CacauSync API — Back-end Corporativo para Ensaios Genéticos (CEPEC/CEPLAC)

[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3.5-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?style=for-the-badge&logo=postgresql&logoColor=white)](https://www.postgresql.org)
[![Flyway](https://img.shields.io/badge/Flyway-Migrations-CC0200?style=for-the-badge&logo=flyway&logoColor=white)](https://flywaydb.org)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=for-the-badge&logo=docker&logoColor=white)](https://www.docker.com)
[![JUnit 5](https://img.shields.io/badge/JUnit5-51_Tests_Passed-25A162?style=for-the-badge&logo=junit5&logoColor=white)](https://junit.org)

API RESTful corporativa desenvolvida em **Java 21 + Spring Boot 3.3** para orquestração de rotas de campo, sincronização *offline-first* idempotente de coletas fenotípicas, auditoria fitossanitária com caixas YOLO e análise estatística de produtividade por clone de cacaueiro no contexto do **Centro de Pesquisas do Cacau (CEPEC/CEPLAC)**.

> 📱 **Ecossistema Integrado:** Este back-end atua em conjunto com o aplicativo Android de Visão Computacional Offline ([**CacauApp**](https://github.com/Igor8818/CacauApp)), que utiliza **TensorFlow Lite (YOLOv8 / Object Detection)** em campo (sem conectividade de internet) para contagem de frutos na planta e exportação padronizada em CSV e JSON.

---

## 🎯 O Problema Real Resolvido

Em quadras experimentais de melhoramento genético de cacau (como o ensaio competitivo **TC-1801**, contendo ~1.000 plantas e mais de 10.000 avaliações históricas), os pesquisadores e técnicos agrícolas atuam em áreas rurais sem sinal de internet:

1. **Pré-Campo (Planejamento):** O pesquisador exporta da **CacauSync API** a planilha de rota (`GET /api/v1/ensaios/TC-1801/rota-csv`) e carrega no **Modo Rota (FieldBook)** do aplicativo Android.
2. **Em Campo (Offline + IA):** O aplicativo guia o técnico planta a planta, realiza a contagem automática de frutos via câmera com modelo `.tflite` (ou registra baixa fitossanitária como `"Não Coletável"` para plantas mortas/secas/ausentes) e grava coordenadas GPS (`Lat`/`Lng`).
3. **Pós-Campo (Sincronização no Laboratório):** De volta ao laboratório com rede local, o pesquisador envia o lote de dados via JSON com caixas normalizadas ou CSV + Fotos para a API. A API valida duplicidades (**idempotência estrita**), atualiza automaticamente o estande de plantas vivas/mortas, audita leituras sem sinal de GPS e consolida o ranking de produtividade por clone (`CCN-51`, `SCP-16005`, `PS-13.19`, `SCP-17202`).

---

## 🛠️ Stack Tecnológica & Arquitetura

* **Java 21 + Spring Boot 3.3.5:** Arquitetura limpa em camadas (`Controller` $\rightarrow$ `Service` $\rightarrow$ `Repository` $\rightarrow$ `Entity`), uso de `Java Records` para DTOs imutáveis e tratamento global de exceções no padrão **RFC 7807 (`ProblemDetail`)**.
* **Banco de Dados Relacional (PostgreSQL 16 + Spring Data JPA + Flyway):**
  * Hierarquia relacional completa: `Ensaio` $\rightarrow$ `Parcela` $\leftrightarrow$ `Clone` $\rightarrow$ `Planta` $\rightarrow$ `LeituraColeta` $\rightarrow$ `DeteccaoYolo`.
  * Versionamento de schema automatizado via **Flyway** (`V1__create_schema.sql`, `V2__seed_ensaio_tc1801.sql`, `V3__update_default_users_password.sql`, `V4__fotos_e_validacao_leituras.sql` e `V5__fitossanidade_e_analise_doencas.sql`).
* **Segurança & RBAC (Spring Security 6 + Auth0 JWT):**
  * Autenticação *stateless* via Bearer Token JWT com controle de acesso baseado em papéis (`ROLE_COORDENADOR` e `ROLE_PESQUISADOR`).
* **Documentação Interativa & Painel Visual:**
  * **Swagger UI (SpringDoc OpenAPI 3)** configurado com botão *Authorize (JWT)*.
  * **Painel Web do Pesquisador** integrado em `/` (`index.html`) para upload visual de CSV, visualização das fotos com bounding boxes sobrepostas em HTML5 Canvas, auditoria fitossanitária com atalhos de teclado (1-4, Del, Esc), mapa geoespacial SVG de dispersão de doenças e acompanhamento do ranking de produtividade líquida em tempo real.
* **Qualidade de Código (JUnit 5 + Mockito + AssertJ):**
  * **51 testes automatizados** cobrindo cálculo agronômico CEPEC (`SPEC-003`), concordância contratual com o app mobile (`MobileContractTest`), regras de autorização RBAC, parser CSV RFC 4180, sincronização JSON idempotente, validação geométrica de caixas YOLO e emissão de JWT.
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
Ao iniciar, o **Flyway** executará automaticamente as migrations `V1` a `V5`, criando as tabelas e populando o ensaio `TC-1801`, clones, parcelas, plantas e usuários de demonstração.

### 3. Acessar no Navegador
* **Painel Web do Pesquisador (Dashboard):** [http://localhost:8080](http://localhost:8080)
* **Documentação Interativa (Swagger UI):** [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

**Credenciais de Demonstração (Seed Flyway V2/V3):**
| Perfil | E-mail | Senha |
| :--- | :--- | :--- |
| **Coordenador Genético** | `coordenador@cepec.gov.br` | `cepec123` |
| **Pesquisador de Campo** | `pesquisador@cepec.gov.br` | `cepec123` |

---

## 🧪 Suíte de Testes Automatizados (51 Testes JUnit 5 + Mockito)

```bash
mvn test
```

A suíte de testes cobre:
1. **`MobileContractTest`**: Valida a matriz de integração (`SPEC-000`) garantindo que alterações no back-end nunca quebrem o aplicativo Android.
2. **`EnsaioServiceTest`**: Fórmulas agronômicas do CEPEC (`SPEC-003`), calculando produtividade líquida comercial exclusivamente sobre frutos sadios e taxa de sobrevivência clonal.
3. **`LeituraServiceTest`**: Invariantes fitossanitárias (`Total = Sadios + Vassoura + Podridão + Não Avaliável`) e soberania da validação científica do pesquisador.
4. **`CsvColetaParserTest`**: Leitura dos formatos de CSV exportados pelo app Android, remoção de UTF-8 BOM e campos escapados com vírgula/aspas (RFC 4180).
5. **`ColetaSyncServiceTest`**: Garantia de idempotência no reenvio de planilhas e lotes JSON, conversão automática de `"Não Coletável"` em status `NAO_COLETAVEL` da planta e alerta de geolocalização quando o GPS vem zerado (`0.0, 0.0`).
6. **`FotoStorageServiceTest` & `FotoMatcherTest`**: Armazenamento seguro de fotos, hash SHA-256 e pareamento automático com leituras de campo.
7. **`SecurityRulesIntegrationTest` & `JwtServiceTest`**: Bloqueio de acessos não autorizados, emissão e validação de tokens JWT HMAC256.

---

## 🔒 Segurança em Produção

Para implantação em ambiente produtivo:
* Configure variáveis de ambiente para credenciais de banco (`DB_USER`, `DB_PASSWORD`, `DB_URL`);
* Defina uma chave forte para assinatura dos tokens JWT (`JWT_SECRET` com no mínimo 256 bits);
* Utilize HTTPS com terminação TLS (via Nginx ou Traefik reverse proxy);
* Consulte o arquivo `.env.example` para o template completo de variáveis de ambiente.
