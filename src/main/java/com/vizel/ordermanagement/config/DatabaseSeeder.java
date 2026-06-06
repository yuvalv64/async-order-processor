package com.vizel.ordermanagement.config;
 
import com.vizel.ordermanagement.domain.Inventory;
import com.vizel.ordermanagement.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseSeeder implements CommandLineRunner {

    private final InventoryRepository inventoryRepository;

    @Override
    public void run(String... args) {
        seedSkuIfMissing("SKU-100", 10);
        seedSkuIfMissing("SKU-200", 5);
        seedSkuIfMissing("SKU-300", 0);
        log.info("Database seeding check completed successfully.");
    }

    private void seedSkuIfMissing(String sku, int quantity) {
        if (!inventoryRepository.existsById(sku)) {
            log.info("SKU {} is missing. Seeding initial quantity of {}...", sku, quantity);
            inventoryRepository.save(new Inventory(sku, quantity));
        } else {
            log.info("SKU {} already exists. Skipping seeding.", sku);
        }
    }
}