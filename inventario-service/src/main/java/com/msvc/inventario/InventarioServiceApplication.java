package com.msvc.inventario;

import com.msvc.inventario.entity.Inventario;
import com.msvc.inventario.repository.InventarioRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class InventarioServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventarioServiceApplication.class, args);
    }

    @Bean
    public CommandLineRunner loadData(InventarioRepository inventarioRepository) {
        return args -> {
            Inventario inventario1 = new Inventario();
            inventario1.setCodigoSku("iphone_15");
            inventario1.setCantidad(100);

            Inventario inventario2 = new Inventario();
            inventario2.setCodigoSku("iphone_15_blue");
            inventario2.setCantidad(0);

            inventarioRepository.save(inventario1);
            inventarioRepository.save(inventario2);
        };
    }
}
