package br.edu.ifc.bikes.config;

import br.edu.ifc.bikes.model.Categoria;
import br.edu.ifc.bikes.model.ItemInventario;
import br.edu.ifc.bikes.model.Raridade;
import br.edu.ifc.bikes.repository.ItemInventarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/** Popula o banco H2 com itens clássicos de D&D 5e assim que a aplicação sobe. */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final ItemInventarioRepository repository;

    public DataSeeder(ItemInventarioRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) {
        if (repository.count() > 0) {
            log.debug("Banco já possui itens, seed ignorado");
            return;
        }

        List<ItemInventario> itens = List.of(
                new ItemInventario("Espada Longa", "Arma marcial versátil, dano 1d8 cortante (1d10 com duas mãos).",
                        Categoria.ARMA, Raridade.COMUM, 3.0, new BigDecimal("15.00"), 1),
                new ItemInventario("Adaga", "Arma simples leve, sutil e arremessável, dano 1d4 perfurante.",
                        Categoria.ARMA, Raridade.COMUM, 1.0, new BigDecimal("2.00"), 2),
                new ItemInventario("Arco Curto", "Arma simples à distância, dano 1d6 perfurante, alcance 80/320 pés.",
                        Categoria.ARMA, Raridade.COMUM, 2.0, new BigDecimal("25.00"), 1),
                new ItemInventario("Cota de Malha", "Armadura pesada com CA 16; impõe desvantagem em Furtividade.",
                        Categoria.ARMADURA, Raridade.COMUM, 55.0, new BigDecimal("75.00"), 1),
                new ItemInventario("Escudo", "Concede +2 de CA enquanto empunhado.",
                        Categoria.ARMADURA, Raridade.COMUM, 6.0, new BigDecimal("10.00"), 1),
                new ItemInventario("Poção de Cura", "Restaura 2d4+2 pontos de vida ao ser bebida.",
                        Categoria.POCAO, Raridade.COMUM, 0.5, new BigDecimal("50.00"), 3),
                new ItemInventario("Poção de Cura Superior", "Restaura 4d4+4 pontos de vida ao ser bebida.",
                        Categoria.POCAO, Raridade.INCOMUM, 0.5, new BigDecimal("150.00"), 1),
                new ItemInventario("Corda de Cânhamo (15 m)", "50 pés de corda; suporta 500 lbs e pode ser rompida com Força CD 17.",
                        Categoria.MARAVILHOSO, Raridade.COMUM, 10.0, new BigDecimal("1.00"), 1),
                new ItemInventario("Mochila de Aventureiro", "Kit básico: mochila, saco de dormir, rações e tocha.",
                        Categoria.MARAVILHOSO, Raridade.COMUM, 5.0, new BigDecimal("2.00"), 1),
                new ItemInventario("Bolsa de Carga", "Bolsa mágica com espaço interno muito maior que o externo.",
                        Categoria.MARAVILHOSO, Raridade.INCOMUM, 15.0, new BigDecimal("4000.00"), 1)
        );

        repository.saveAll(itens);
        log.info("Data Seeder executado: {} itens clássicos de D&D inseridos", itens.size());
    }
}
