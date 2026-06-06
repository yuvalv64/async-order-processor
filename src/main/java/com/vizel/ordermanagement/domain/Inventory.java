package com.vizel.ordermanagement.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@Entity
@NoArgsConstructor
@AllArgsConstructor
public class Inventory {

    @Id
    private String sku;

    private Integer quantity;

    @Version
    private Long version;

    public Inventory(String sku, int quantity) {
        this.sku = sku;
        this.quantity = quantity;
    }
}
