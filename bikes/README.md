# 🎒 Inventário de D&D — Automação, Observabilidade e Padronização

> Projeto prático de **Automação e Esteira DevSecOps** — Instituto Federal Catarinense (IFC), campus Videira.

API REST em **Java 17 + Spring Boot** para gerenciar um inventário de itens de *Dungeons & Dragons*, acompanhada de uma esteira de **CI/CD** e de uma stack completa de **observabilidade** (métricas e logs), toda orquestrada por Docker Compose.

---

## 1. Visão Geral

O objetivo do projeto não é apenas ter uma API funcional, mas **demonstrar boas práticas de engenharia de software e operação**, organizadas em três pilares:

| Pilar | O que foi feito |
|-------|-----------------|
| **Automação** | Pipeline no **GitHub Actions** que compila, executa os testes automatizados (JUnit + Mockito) e, se tudo passar, constrói e publica a imagem Docker no Docker Hub. |
| **Observabilidade** | **Métricas** expostas pelo Spring Boot Actuator/Micrometer e coletadas pelo **Prometheus**, visualizadas no **Grafana**. **Logs estruturados** (formato GELF) enviados ao **Graylog**. |
| **Padronização** | Ambiente 100% em contêineres (Dockerfile *multistage* + Docker Compose): qualquer pessoa sobe a mesma infraestrutura, com o mesmo resultado, em qualquer máquina. |

A aplicação também possui um front-end didático e **endpoints de caos** (`/api/chaos/*`) que geram carga de CPU, pico de memória e erros HTTP propositais, permitindo *comprovar* que o monitoramento funciona.

---

## 2. Como Executar

### Pré-requisitos

- [Docker](https://docs.docker.com/get-docker/) com o plugin **Docker Compose** (v2)
- ~4 GB de memória livre para os contêineres (o OpenSearch é o mais pesado)
- *Somente Linux:* o OpenSearch exige `sudo sysctl -w vm.max_map_count=262144`

### Subindo toda a infraestrutura com um único comando

```bash
docker compose up --build
```

Aguarde alguns minutos na primeira execução (download das imagens + inicialização do Graylog/OpenSearch). Em seguida, acesse:

| Serviço | URL | Credenciais |
|---------|-----|-------------|
| Aplicação (front-end) | http://localhost:7080 | — |
| API REST | http://localhost:7080/api/itens | — |
| Métricas (Prometheus format) | http://localhost:7080/actuator/prometheus | — |
| Prometheus | http://localhost:9090 | — |
| Grafana | http://localhost:3000 | `admin` / `admin` |
| Graylog | http://localhost:9500 | `admin` / `admin` |

Para encerrar: `docker compose down` (adicione `-v` para apagar também os volumes).

### Configurando o Grafana (primeira vez)

1. Em **Connections → Data sources → Add data source**, escolha **Prometheus**.
2. Em *URL*, informe `http://prometheus:9090` e clique em **Save & test**.
3. Crie um dashboard com os painéis abaixo (ou importe o dashboard comunitário **JVM (Micrometer)**, ID `4701`):

| Painel | Consulta PromQL |
|--------|-----------------|
| Memória Heap da JVM | `sum(jvm_memory_used_bytes{area="heap"})` |
| Uso de CPU | `process_cpu_usage` |
| Taxa de requisições (RPS) | `sum(rate(http_server_requests_seconds_count{uri!~"/actuator.*"}[1m]))` |
| Status 2xx / 4xx / 5xx | `sum(rate(http_server_requests_seconds_count{status=~"2.."}[1m]))` (repita com `4..` e `5..`) |

### Gerando dados para os dashboards

Os endpoints de caos (apenas para ambiente de estudo) permitem provocar variações nos gráficos:

```bash
curl "http://localhost:7080/api/chaos/cpu?segundos=10&threads=2"       # pico de CPU
curl "http://localhost:7080/api/chaos/memory?megabytes=300&segundos=15" # pico de memória heap
curl "http://localhost:7080/api/chaos/400"                              # erro 4xx
curl "http://localhost:7080/api/chaos/500"                              # erro 5xx
```

Também é possível acionar tudo pela aba **Observabilidade & Caos** do front-end (http://localhost:7080).

### Executando os testes localmente

```bash
mvn clean test
```

---

## 3. Arquitetura de Contêineres

O `docker-compose.yml` orquestra os seguintes serviços:

- **App** (`bikes-api`): aplicação Spring Boot (Java 17). Expõe a API REST, o front-end e as métricas em `/actuator/prometheus`; envia seus logs no formato GELF ao Graylog.
- **Prometheus**: coletor de métricas. Faz *scraping* periódico do endpoint `/actuator/prometheus` da aplicação e armazena as séries temporais.
- **Grafana**: camada de visualização. Consulta o Prometheus e exibe os dashboards (heap da JVM, CPU, RPS e status codes).
- **Graylog**: plataforma de gestão de logs. Recebe os logs estruturados da aplicação (input GELF UDP, criado automaticamente) e permite buscá-los e filtrá-los por nível (`DEBUG`, `INFO`, `WARN`, `ERROR`).
- **MongoDB**: banco de metadados do Graylog (configurações, usuários, *streams* e *inputs*).
- **OpenSearch**: motor de busca e armazenamento onde o Graylog guarda e indexa as mensagens de log.

Serviços de apoio:

- **MySQL** (`db`): banco de dados relacional usado pela aplicação dentro do Compose (fora dele, a aplicação usa H2 em memória).
- **graylog-input**: contêiner temporário que cria o *input* GELF UDP no Graylog na primeira subida e termina em seguida.

```text
                  ┌───────────┐  métricas   ┌────────────┐  consultas  ┌──────────┐
   Usuário ─────► │    App    │ ◄────────── │ Prometheus │ ◄────────── │ Grafana  │
 (porta 7080)     │ Spring    │  (scrape)   └────────────┘             └──────────┘
                  │ Boot      │
                  │           │──── logs GELF/UDP ───►┌─────────┐──►┌────────────┐
                  └────┬──────┘                       │ Graylog │   │ OpenSearch │
                       │ JDBC                         └────┬────┘   └────────────┘
                  ┌────▼────┐                              │ metadados
                  │  MySQL  │                         ┌────▼────┐
                  └─────────┘                         │ MongoDB │
                                                      └─────────┘
```

---

## 4. Pipeline CI/CD

O arquivo [`.github/workflows/pipeline.yml`](.github/workflows/pipeline.yml) é executado a cada `push` ou `pull request` nas branches `main` e `master`:

1. **Compilação e Testes** — configura o JDK 17 (Eclipse Temurin) e executa `mvn clean test`.
2. **Build e Push** — executado após o sucesso do job anterior (somente em `push`): faz login no Docker Hub, constrói a imagem a partir do `Dockerfile` multistage e a publica com as tags `latest` e o SHA do commit.

**Secrets necessários** (*Settings → Secrets and variables → Actions*): `DOCKER_USERNAME` e `DOCKER_PASSWORD` (use um *Access Token* do Docker Hub como senha).

---

## 5. Evidências do Projeto

> Substitua os arquivos em [`docs/img/`](docs/img/) pelos seus prints (mantendo os nomes) ou ajuste os links abaixo.

### 5.1 Pipeline CI/CD executada com sucesso no GitHub

![Pipeline CI/CD executada com sucesso no GitHub Actions](docs/img/pipeline-sucesso.png)

### 5.2 Dashboards do Grafana

**Consumo de Memória Heap da JVM**

![Dashboard do Grafana: memória heap da JVM](docs/img/grafana-heap.png)

**Uso de CPU**

![Dashboard do Grafana: uso de CPU](docs/img/grafana-cpu.png)

**Taxa de requisições (RPS)**

![Dashboard do Grafana: taxa de requisições por segundo](docs/img/grafana-rps.png)

**Status Code (2xx, 4xx e 5xx)**

![Dashboard do Grafana: respostas HTTP por status code](docs/img/grafana-status-code.png)

### 5.3 Logs estruturados no Graylog

![Painel do Graylog exibindo logs estruturados da aplicação Spring](docs/img/graylog-logs.png)

---

## 6. Tecnologias

Java 17 · Spring Boot · Spring Data JPA · H2 / MySQL · Micrometer · Prometheus · Grafana · Graylog (GELF) · OpenSearch · MongoDB · JUnit 5 · Mockito · Docker · Docker Compose · GitHub Actions
