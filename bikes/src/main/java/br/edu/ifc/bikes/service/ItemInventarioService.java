package br.edu.ifc.bikes.service;

import br.edu.ifc.bikes.exception.ItemNaoEncontradoException;
import br.edu.ifc.bikes.model.ItemInventario;
import br.edu.ifc.bikes.repository.ItemInventarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Regras de negócio do inventário. Aqui ficam os logs estratégicos:
 * INFO (operações concluídas), DEBUG (detalhes técnicos) e ERROR (falhas).
 */
@Service
public class ItemInventarioService {

    private static final Logger log = LoggerFactory.getLogger(ItemInventarioService.class);

    private final ItemInventarioRepository repository;

    public ItemInventarioService(ItemInventarioRepository repository) {
        this.repository = repository;
    }

    /** Adiciona um novo item. O id é sempre ignorado para garantir que seja uma inserção. */
    @Transactional
    public ItemInventario adicionar(ItemInventario item) {
        log.debug("Iniciando adição do item '{}' (categoria={}, raridade={})",
                item.getNome(), item.getCategoria(), item.getRaridade());
        try {
            item.setId(null);
            ItemInventario salvo = repository.save(item);
            log.info("Item adicionado com sucesso: id={}, nome='{}'", salvo.getId(), salvo.getNome());
            return salvo;
        } catch (DataAccessException e) {
            log.error("Falha ao persistir o item '{}': {}", item.getNome(), e.getMessage(), e);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public List<ItemInventario> listar() {
        List<ItemInventario> itens = repository.findAll();
        log.debug("Listagem do inventário retornou {} item(ns)", itens.size());
        return itens;
    }

    @Transactional(readOnly = true)
    public ItemInventario buscarPorId(Long id) {
        return repository.findById(id).orElseThrow(() -> {
            log.error("Falha ao buscar item: id={} não existe", id);
            return new ItemNaoEncontradoException(id);
        });
    }

    /** Atualiza todos os campos do item e registra em DEBUG o cálculo do valor total (preço x quantidade). */
    @Transactional
    public ItemInventario atualizar(Long id, ItemInventario dados) {
        ItemInventario existente = buscarPorId(id);

        BigDecimal valorAntes = calcularValorTotal(existente);

        existente.setNome(dados.getNome());
        existente.setDescricao(dados.getDescricao());
        existente.setCategoria(dados.getCategoria());
        existente.setRaridade(dados.getRaridade());
        existente.setPeso(dados.getPeso());
        existente.setPrecoEmOuro(dados.getPrecoEmOuro());
        existente.setQuantidade(dados.getQuantidade());

        BigDecimal valorDepois = calcularValorTotal(existente);
        log.debug("Atualização do item id={}: valor total {} po -> {} po (diferença de {} po)",
                id, valorAntes, valorDepois, valorDepois.subtract(valorAntes));

        try {
            ItemInventario salvo = repository.save(existente);
            log.info("Item atualizado com sucesso: id={}, nome='{}'", salvo.getId(), salvo.getNome());
            return salvo;
        } catch (DataAccessException e) {
            log.error("Falha ao atualizar o item id={}: {}", id, e.getMessage(), e);
            throw e;
        }
    }

    /** Remove um item. Se o ID não existir, registra ERROR e lança ItemNaoEncontradoException (404). */
    @Transactional
    public void remover(Long id) {
        if (!repository.existsById(id)) {
            log.error("Falha ao remover item: id={} não existe", id);
            throw new ItemNaoEncontradoException(id);
        }
        repository.deleteById(id);
        log.info("Item removido com sucesso: id={}", id);
    }

    /** Valor total do estoque de um item: precoEmOuro * quantidade. */
    private BigDecimal calcularValorTotal(ItemInventario item) {
        return item.getPrecoEmOuro().multiply(BigDecimal.valueOf(item.getQuantidade()));
    }
}
