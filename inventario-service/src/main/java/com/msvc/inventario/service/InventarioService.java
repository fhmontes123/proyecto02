package com.msvc.inventario.service;

import com.msvc.inventario.dto.InventarioResponse;
import com.msvc.inventario.entity.Inventario;
import com.msvc.inventario.repository.InventarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class InventarioService {

    private final InventarioRepository inventarioRepository;

    public InventarioService(InventarioRepository inventarioRepository) {
        this.inventarioRepository = inventarioRepository;
    }

    @Transactional(readOnly = true)
    public List<InventarioResponse> isInStock(List<String> listaCodigosSku) {
        // 1. Buscar todos los que existen en BD
        List<Inventario> inventarioEncontrado = inventarioRepository.findByCodigoSkuIn(listaCodigosSku);

        // 2. Crear un mapa para busqueda rapida de SKU en Inventario
        Map<String, Inventario> inventarioMap = inventarioEncontrado.stream()
                .collect(Collectors.toMap(Inventario::getCodigoSku, Function.identity()));

        // 3. Recorrer TODOS los SKUs solicitados (existan o no)
        return listaCodigosSku.stream()
                .map(sku -> {
                    Inventario inventario = inventarioMap.get(sku);
                    if (inventario != null) {
                        // Producto EXISTE
                        return InventarioResponse.builder()
                                .codigoSku(sku)
                                .inStock(inventario.getCantidad() > 0)
                                .existe(true)
                                .build();
                    } else {
                        // Producto NO EXISTE
                        return InventarioResponse.builder()
                                .codigoSku(sku)
                                .inStock(false)
                                .existe(false)
                                .build();
                    }
                })
                .collect(Collectors.toList());
    }
}
