package br.edu.ifc.bikes.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Endpoints de "caos" para gerar carga e alimentar os dashboards do Grafana.
 * ATENÇÃO: usar apenas em ambiente de estudo/teste, nunca em produção.
 */
@RestController
@RequestMapping("/api/chaos")
public class ChaosController {

    private static final Logger log = LoggerFactory.getLogger(ChaosController.class);

    /**
     * Estressa a CPU calculando números primos por força bruta.
     * Exemplo: GET /api/chaos/cpu?segundos=10&threads=2
     */
    @GetMapping("/cpu")
    public Map<String, Object> estressarCpu(@RequestParam(defaultValue = "5") int segundos,
                                            @RequestParam(defaultValue = "1") int threads) {
        int duracao = limitar(segundos, 1, 60);
        int qtdThreads = limitar(threads, 1, Runtime.getRuntime().availableProcessors());
        log.warn("CHAOS CPU iniciado: {}s com {} thread(s)", duracao, qtdThreads);

        long fim = System.nanoTime() + duracao * 1_000_000_000L;
        ExecutorService pool = Executors.newFixedThreadPool(qtdThreads);
        try {
            List<Future<Long>> futuros = new ArrayList<>();
            for (int i = 0; i < qtdThreads; i++) {
                futuros.add(pool.submit(() -> contarPrimosAte(fim)));
            }
            long totalPrimos = 0;
            for (Future<Long> futuro : futuros) {
                totalPrimos += futuro.get();
            }
            log.info("CHAOS CPU finalizado: {} primos encontrados", totalPrimos);
            return resposta("cpu", "segundos", duracao, "threads", qtdThreads, "primosEncontrados", totalPrimos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Teste de CPU interrompido", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Falha durante o teste de CPU", e);
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * Provoca um pico de memória heap alocando blocos de 1 MB e segurando por alguns segundos.
     * Dica: rode a JVM com -Xmx512m para o pico ficar bem visível no Grafana.
     * Exemplo: GET /api/chaos/memory?megabytes=300&segundos=15
     */
    @GetMapping("/memory")
    public Map<String, Object> estressarMemoria(@RequestParam(defaultValue = "200") int megabytes,
                                                @RequestParam(defaultValue = "10") int segundos) {
        int mb = limitar(megabytes, 1, 1024);
        int duracao = limitar(segundos, 1, 60);
        log.warn("CHAOS MEMORIA iniciado: {} MB retidos por {}s", mb, duracao);

        List<byte[]> blocos = new ArrayList<>();
        try {
            for (int i = 0; i < mb; i++) {
                byte[] bloco = new byte[1024 * 1024];
                Arrays.fill(bloco, (byte) 1); // garante que a memória seja realmente "tocada"
                blocos.add(bloco);
            }
            Thread.sleep(duracao * 1000L); // mantém a memória ocupada para aparecer no gráfico
            log.info("CHAOS MEMORIA finalizado: {} MB serão liberados para o GC", mb);
            return resposta("memory", "megabytes", mb, "segundos", duracao);
        } catch (OutOfMemoryError e) {
            blocos.clear(); // libera antes de logar, para não agravar o problema
            log.error("CHAOS MEMORIA estourou o heap ao tentar alocar {} MB", mb);
            throw new IllegalStateException("Heap insuficiente para alocar " + mb + " MB", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Teste de memória interrompido", e);
        } finally {
            blocos.clear(); // a memória volta a ser elegível para o Garbage Collector
        }
    }

    /** Gera um HTTP 500 proposital (tratado pelo GlobalExceptionHandler, com log ERROR). */
    @GetMapping("/500")
    public Map<String, Object> erro500() {
        log.warn("CHAOS 500 acionado");
        throw new IllegalStateException("Erro interno proposital gerado pelo ChaosController");
    }

    /** Gera um HTTP 400 proposital (tratado pelo GlobalExceptionHandler, com log WARN). */
    @GetMapping("/400")
    public Map<String, Object> erro400() {
        log.warn("CHAOS 400 acionado");
        throw new IllegalArgumentException("Requisição inválida proposital gerada pelo ChaosController");
    }

    /** Conta primos por tentativa e divisão até o prazo (em nanoTime) acabar. */
    private long contarPrimosAte(long fimNanos) {
        long candidato = 2;
        long primos = 0;
        while (System.nanoTime() < fimNanos) {
            if (ehPrimo(candidato++)) {
                primos++;
            }
        }
        return primos;
    }

    private boolean ehPrimo(long n) {
        if (n < 2) {
            return false;
        }
        for (long i = 2; i * i <= n; i++) {
            if (n % i == 0) {
                return false;
            }
        }
        return true;
    }

    private int limitar(int valor, int minimo, int maximo) {
        return Math.min(Math.max(valor, minimo), maximo);
    }

    /** Monta o corpo da resposta a partir de pares chave/valor. */
    private Map<String, Object> resposta(String tipo, Object... paresChaveValor) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("chaos", tipo);
        for (int i = 0; i < paresChaveValor.length; i += 2) {
            corpo.put(String.valueOf(paresChaveValor[i]), paresChaveValor[i + 1]);
        }
        corpo.put("status", "concluido");
        return corpo;
    }
}
