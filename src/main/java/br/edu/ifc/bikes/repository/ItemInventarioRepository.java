package br.edu.ifc.bikes.repository;

import br.edu.ifc.bikes.model.ItemInventario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repositório Spring Data JPA: o CRUD básico já vem pronto do JpaRepository. */
@Repository
public interface ItemInventarioRepository extends JpaRepository<ItemInventario, Long> {
}
