package br.edu.ifc.bikes.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Entidade que representa um item do inventário de um personagem.
 * As anotações de validação garantem que dados inválidos resultem em HTTP 400.
 */
@Entity
@Table(name = "item_inventario")
public class ItemInventario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "O nome é obrigatório")
    @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres")
    @Column(nullable = false, length = 100)
    private String nome;

    @Size(max = 500, message = "A descrição deve ter no máximo 500 caracteres")
    @Column(length = 500)
    private String descricao;

    @NotNull(message = "A categoria é obrigatória")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Categoria categoria;

    @NotNull(message = "A raridade é obrigatória")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Raridade raridade;

    /** Peso em libras (lbs). */
    @NotNull(message = "O peso é obrigatório")
    @PositiveOrZero(message = "O peso não pode ser negativo")
    @Column(nullable = false)
    private Double peso;

    /** Preço em peças de ouro (po). BigDecimal evita erros de arredondamento. */
    @NotNull(message = "O preço em ouro é obrigatório")
    @PositiveOrZero(message = "O preço não pode ser negativo")
    @Column(name = "preco_em_ouro", nullable = false, precision = 12, scale = 2)
    private BigDecimal precoEmOuro;

    @NotNull(message = "A quantidade é obrigatória")
    @Min(value = 0, message = "A quantidade não pode ser negativa")
    @Column(nullable = false)
    private Integer quantidade;

    /** Construtor vazio exigido pelo JPA. */
    public ItemInventario() {
    }

    public ItemInventario(String nome, String descricao, Categoria categoria, Raridade raridade,
                          Double peso, BigDecimal precoEmOuro, Integer quantidade) {
        this.nome = nome;
        this.descricao = descricao;
        this.categoria = categoria;
        this.raridade = raridade;
        this.peso = peso;
        this.precoEmOuro = precoEmOuro;
        this.quantidade = quantidade;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }

    public Categoria getCategoria() { return categoria; }
    public void setCategoria(Categoria categoria) { this.categoria = categoria; }

    public Raridade getRaridade() { return raridade; }
    public void setRaridade(Raridade raridade) { this.raridade = raridade; }

    public Double getPeso() { return peso; }
    public void setPeso(Double peso) { this.peso = peso; }

    public BigDecimal getPrecoEmOuro() { return precoEmOuro; }
    public void setPrecoEmOuro(BigDecimal precoEmOuro) { this.precoEmOuro = precoEmOuro; }

    public Integer getQuantidade() { return quantidade; }
    public void setQuantidade(Integer quantidade) { this.quantidade = quantidade; }
}
