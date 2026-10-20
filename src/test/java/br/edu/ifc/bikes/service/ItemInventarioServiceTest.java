package br.edu.ifc.bikes.service;

import br.edu.ifc.bikes.exception.ItemNaoEncontradoException;
import br.edu.ifc.bikes.model.Categoria;
import br.edu.ifc.bikes.model.ItemInventario;
import br.edu.ifc.bikes.model.Raridade;
import br.edu.ifc.bikes.repository.ItemInventarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Testes unitários do Service (sem subir o Spring): rápidos, ideais para a etapa de CI. */
@ExtendWith(MockitoExtension.class)
class ItemInventarioServiceTest {

    @Mock
    private ItemInventarioRepository repository;

    @InjectMocks
    private ItemInventarioService service;

    private ItemInventario criarItem(Long id) {
        ItemInventario item = new ItemInventario("Espada Longa", "Arma versatil", Categoria.ARMA,
                Raridade.COMUM, 3.0, new BigDecimal("15.00"), 1);
        item.setId(id);
        return item;
    }

    @Test
    @DisplayName("Adicionar deve ignorar o id recebido e salvar como novo item")
    void adicionarDeveSalvarItem() {
        ItemInventario entrada = criarItem(99L);
        when(repository.save(any(ItemInventario.class))).thenAnswer(inv -> {
            ItemInventario i = inv.getArgument(0);
            i.setId(1L);
            return i;
        });

        ItemInventario salvo = service.adicionar(entrada);

        assertEquals(1L, salvo.getId());
        assertEquals("Espada Longa", salvo.getNome());
        verify(repository).save(entrada);
    }

    @Test
    @DisplayName("Listar deve devolver todos os itens do repositório")
    void listarDeveRetornarTodosOsItens() {
        when(repository.findAll()).thenReturn(List.of(criarItem(1L), criarItem(2L)));

        assertEquals(2, service.listar().size());
    }

    @Test
    @DisplayName("Buscar id inexistente deve lançar ItemNaoEncontradoException")
    void buscarInexistenteDeveLancarExcecao() {
        when(repository.findById(42L)).thenReturn(Optional.empty());

        assertThrows(ItemNaoEncontradoException.class, () -> service.buscarPorId(42L));
    }

    @Test
    @DisplayName("Atualizar deve alterar os campos do item existente")
    void atualizarDeveAlterarCampos() {
        ItemInventario existente = criarItem(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(existente));
        when(repository.save(any(ItemInventario.class))).thenAnswer(inv -> inv.getArgument(0));

        ItemInventario novosDados = new ItemInventario("Espada Longa +1", "Encantada", Categoria.ARMA,
                Raridade.INCOMUM, 3.0, new BigDecimal("500.00"), 2);

        ItemInventario atualizado = service.atualizar(1L, novosDados);

        assertEquals("Espada Longa +1", atualizado.getNome());
        assertEquals(Raridade.INCOMUM, atualizado.getRaridade());
        assertEquals(new BigDecimal("500.00"), atualizado.getPrecoEmOuro());
        assertEquals(2, atualizado.getQuantidade());
    }

    @Test
    @DisplayName("Atualizar id inexistente deve lançar exceção e não salvar nada")
    void atualizarInexistenteDeveLancarExcecao() {
        when(repository.findById(7L)).thenReturn(Optional.empty());

        assertThrows(ItemNaoEncontradoException.class, () -> service.atualizar(7L, criarItem(null)));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Remover id existente deve chamar deleteById")
    void removerExistenteDeveDeletar() {
        when(repository.existsById(1L)).thenReturn(true);

        service.remover(1L);

        verify(repository).deleteById(1L);
    }

    @Test
    @DisplayName("Remover id inexistente deve lançar exceção e não deletar")
    void removerInexistenteDeveLancarExcecao() {
        when(repository.existsById(99L)).thenReturn(false);

        assertThrows(ItemNaoEncontradoException.class, () -> service.remover(99L));
        verify(repository, never()).deleteById(any());
    }
}
