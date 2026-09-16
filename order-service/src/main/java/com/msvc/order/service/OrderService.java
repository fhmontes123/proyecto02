package com.msvc.order.service;

import com.msvc.order.dto.InventarioResponse;
import com.msvc.order.dto.OrderLineItemDto;
import com.msvc.order.dto.OrderRequest;
import com.msvc.order.entity.Order;
import com.msvc.order.entity.OrderLineItems;
import com.msvc.order.event.OrderPlacedEvent;
import com.msvc.order.repository.OrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
public class OrderService {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private WebClient.Builder webClientBuilder;

    @Autowired
    private KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;

    // Procesa y guarda un nuevo pedido
    public String placeOrder(OrderRequest orderRequest) {
        Order order = new Order();

        // Asignar al pedido un identificador unico
        order.setNumeroPedido(UUID.randomUUID().toString());

        // Transformar lista de DTOs a Entidades
        List<OrderLineItems> orderLineItems = orderRequest.getOrderLineItemDtoList()
                .stream()                           // Convierte la lista en un flujo para procesamiento funcional
                .map(this::mapToDto)                // Aplica la conversion a cada elemento
                .collect(Collectors.toList());      // Recolecta los resultados en una nueva lista

        // Establece la relacion bidireccional
        order.setOrderLineItems(orderLineItems);

        // Obtener solo los codigos SKU de cada producto en una lista ej. ["iphone_15", "samsung_s23", "xiaomi_13"]
        List<String> codigoSku = order.getOrderLineItems().stream()
                .map(OrderLineItems::getCodigoSku)
                .collect(Collectors.toList());

        // Llamar a inventario-service para verificar disponibilidad
        InventarioResponse[] inventarioResponseArray = webClientBuilder.build().get()
                .uri("http://inventario-service/api/inventario", uriBuilder -> uriBuilder
                        .queryParam("codigoSku", codigoSku).build())
                .retrieve()
                .bodyToMono(InventarioResponse[].class)
                .block();

        // Validar que la respuesta no sea nula
        if (inventarioResponseArray == null || inventarioResponseArray.length == 0) {
            throw new IllegalArgumentException("No se pudo obtener información del inventario");
        }

        // Separar productos que NO existen
        List<String> productosNoExistentes = Arrays.stream(inventarioResponseArray)
                .filter(resp -> !resp.isExiste())
                .map(InventarioResponse::getCodigoSku)
                .collect(Collectors.toList());

        // Si hay productos que no existen, retornar mensaje con la lista
        if (!productosNoExistentes.isEmpty()) {
            return String.format(
                    "Pedido no procesado. Los siguientes productos no existen: %s",
                    String.join(", ", productosNoExistentes)
            );
        }

        // Separar productos SIN stock (existen pero no hay stock)
        List<String> productosSinStock = Arrays.stream(inventarioResponseArray)
                .filter(resp -> resp.isExiste() && !resp.isInStock())
                .map(InventarioResponse::getCodigoSku)
                .collect(Collectors.toList());

        // Si hay productos sin stock, retornar mensaje con la lista
        if (!productosSinStock.isEmpty()) {
            return String.format(
                    "Pedido no procesado. Los siguientes productos no tienen stock: %s",
                    String.join(", ", productosSinStock)
            );
        }

        // Todos los productos existen y tienen stock.
        orderRepository.save(order); // Guardar pedido

        // Una vez guardado el pedido enviamos un mensaje a Kafka (Broker)
        kafkaTemplate.send("notificacionTopic", new OrderPlacedEvent(order.getNumeroPedido()));

        return "Pedido ordenado con exito";
    }

    private OrderLineItems mapToDto(OrderLineItemDto orderLineItemDto) {
        OrderLineItems orderLineItems = new OrderLineItems();
        orderLineItems.setPrecio(orderLineItemDto.getPrecio());
        orderLineItems.setCantidad(orderLineItemDto.getCantidad());
        orderLineItems.setCodigoSku(orderLineItemDto.getCodigoSku());
        return orderLineItems;
    }
}
