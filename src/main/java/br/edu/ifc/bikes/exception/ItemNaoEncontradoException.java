package br.edu.ifc.bikes.exception;

/** Lançada quando um item com o ID informado não existe no inventário (vira HTTP 404). */
public class ItemNaoEncontradoException extends RuntimeException {

    public ItemNaoEncontradoException(Long id) {
        super("Item de inventário não encontrado com id=" + id);
    }
}
