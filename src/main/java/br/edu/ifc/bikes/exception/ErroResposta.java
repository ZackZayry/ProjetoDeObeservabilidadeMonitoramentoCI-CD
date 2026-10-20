package br.edu.ifc.bikes.exception;

import java.time.Instant;

/** Corpo padrão das respostas de erro da API. */
public record ErroResposta(Instant timestamp, int status, String erro, String mensagem, String caminho) {

    public static ErroResposta of(int status, String erro, String mensagem, String caminho) {
        return new ErroResposta(Instant.now(), status, erro, mensagem, caminho);
    }
}
