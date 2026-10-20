package br.edu.ifc.bikes.controller;

import br.edu.ifc.bikes.exception.GlobalExceptionHandler;
import br.edu.ifc.bikes.exception.ItemNaoEncontradoException;
import br.edu.ifc.bikes.model.Categoria;
import br.edu.ifc.bikes.model.ItemInventario;
import br.edu.ifc.bikes.model.Raridade;
import br.edu.ifc.bikes.service.ItemInventarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes da camada web com MockMvc "standalone": não sobem o contexto do Spring (rápidos e independentes
 * de pacotes de teste específicos de versão do Spring Boot). O Service é um mock do Mockito.
 * Nomes em ASCII de propósito, para não depender de charset na comparação do JSON.
 */
@ExtendWith(MockitoExtension.class)
class ItemInventarioControllerTest {

    private static final String JSON_VALIDO = """
            {"nome":"Pocao de Cura","descricao":"Restaura 2d4+2 PV","categoria":"POCAO",
             "raridade":"COMUM","peso":0.5,"precoEmOuro":50.00,"quantidade":3}""";

    private static final String JSON_SEM_NOME = """
            {"nome":"","descricao":"x","categoria":"POCAO",
             "raridade":"COMUM","peso":0.5,"precoEmOuro":50.00,"quantidade":3}""";

    @Mock
    private ItemInventarioService service;

    @InjectMocks
    private ItemInventarioController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void configurar() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private ItemInventario criarItem(Long id) {
        ItemInventario item = new ItemInventario("Pocao de Cura", "Restaura 2d4+2 PV", Categoria.POCAO,
                Raridade.COMUM, 0.5, new BigDecimal("50.00"), 3);
        item.setId(id);
        return item;
    }

    @Test
    @DisplayName("GET /api/itens deve retornar 200 e a lista de itens")
    void listarDeveRetornar200() throws Exception {
        when(service.listar()).thenReturn(List.of(criarItem(1L)));

        mockMvc.perform(get("/api/itens"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nome").value("Pocao de Cura"))
                .andExpect(jsonPath("$[0].categoria").value("POCAO"));
    }

    @Test
    @DisplayName("POST /api/itens com dados válidos deve retornar 201 com Location")
    void adicionarValidoDeveRetornar201() throws Exception {
        when(service.adicionar(any(ItemInventario.class))).thenReturn(criarItem(1L));

        mockMvc.perform(post("/api/itens").contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @DisplayName("POST /api/itens sem nome deve retornar 400")
    void adicionarInvalidoDeveRetornar400() throws Exception {
        mockMvc.perform(post("/api/itens").contentType(MediaType.APPLICATION_JSON).content(JSON_SEM_NOME))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("DELETE /api/itens/{id} inexistente deve retornar 404")
    void removerInexistenteDeveRetornar404() throws Exception {
        doThrow(new ItemNaoEncontradoException(99L)).when(service).remover(99L);

        mockMvc.perform(delete("/api/itens/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
