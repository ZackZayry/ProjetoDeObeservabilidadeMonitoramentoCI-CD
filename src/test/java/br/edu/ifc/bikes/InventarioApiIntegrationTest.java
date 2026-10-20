package br.edu.ifc.bikes;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Teste de integração de ponta a ponta: sobe a aplicação inteira (H2 em memória) numa porta aleatória
 * e valida o que o projeto promete: seeder, CRUD, endpoint Prometheus e erros propositais do caos.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InventarioApiIntegrationTest {

    @Value("${local.server.port}")
    private int porta;

    private final HttpClient cliente = HttpClient.newHttpClient();

    private HttpResponse<String> get(String caminho) throws Exception {
        HttpRequest requisicao = HttpRequest.newBuilder(URI.create("http://localhost:" + porta + caminho)).GET().build();
        return cliente.send(requisicao, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    @DisplayName("O Data Seeder deve popular o inventário ao subir a aplicação")
    void seederDevePopularInventario() throws Exception {
        HttpResponse<String> resposta = get("/api/itens");

        assertEquals(200, resposta.statusCode());
        assertTrue(resposta.body().contains("Espada Longa"));
    }

    @Test
    @DisplayName("/actuator/prometheus deve expor métricas da JVM e HTTP")
    void prometheusDeveExporMetricas() throws Exception {
        get("/api/itens"); // gera ao menos uma requisição HTTP para aparecer na métrica
        HttpResponse<String> resposta = get("/actuator/prometheus");

        assertEquals(200, resposta.statusCode());
        assertTrue(resposta.body().contains("jvm_memory_used_bytes"));
        assertTrue(resposta.body().contains("http_server_requests_seconds_count"));
    }

    @Test
    @DisplayName("Endpoints de caos 400 e 500 devem devolver os status propositais")
    void chaosDeveDevolverErrosPropositais() throws Exception {
        assertEquals(400, get("/api/chaos/400").statusCode());
        assertEquals(500, get("/api/chaos/500").statusCode());
    }

    @Test
    @DisplayName("Buscar item inexistente deve devolver 404")
    void itemInexistenteDeveDevolver404() throws Exception {
        assertEquals(404, get("/api/itens/999999").statusCode());
    }
}
