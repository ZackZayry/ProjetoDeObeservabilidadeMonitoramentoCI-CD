package br.edu.ifc.bikes.controller;

import br.edu.ifc.bikes.model.ItemInventario;
import br.edu.ifc.bikes.service.ItemInventarioService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/** Endpoints CRUD do inventário: /api/itens */
@RestController
@RequestMapping("/api/itens")
public class ItemInventarioController {

    private static final Logger log = LoggerFactory.getLogger(ItemInventarioController.class);

    private final ItemInventarioService service;

    public ItemInventarioController(ItemInventarioService service) {
        this.service = service;
    }

    /** GET /api/itens - lista todos os itens. */
    @GetMapping
    public List<ItemInventario> listar() {
        log.debug("GET /api/itens");
        return service.listar();
    }

    /** GET /api/itens/{id} - busca um item. */
    @GetMapping("/{id}")
    public ItemInventario buscar(@PathVariable Long id) {
        log.debug("GET /api/itens/{}", id);
        return service.buscarPorId(id);
    }

    /** POST /api/itens - adiciona um item e devolve 201 com o header Location. */
    @PostMapping
    public ResponseEntity<ItemInventario> adicionar(@Valid @RequestBody ItemInventario item) {
        log.debug("POST /api/itens nome='{}'", item.getNome());
        ItemInventario criado = service.adicionar(item);
        URI local = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(criado.getId()).toUri();
        return ResponseEntity.created(local).body(criado);
    }

    /** PUT /api/itens/{id} - atualiza um item existente. */
    @PutMapping("/{id}")
    public ItemInventario atualizar(@PathVariable Long id, @Valid @RequestBody ItemInventario item) {
        log.debug("PUT /api/itens/{}", id);
        return service.atualizar(id, item);
    }

    /** DELETE /api/itens/{id} - remove um item (204 sem corpo). */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remover(@PathVariable Long id) {
        log.debug("DELETE /api/itens/{}", id);
        service.remover(id);
        return ResponseEntity.noContent().build();
    }
}
