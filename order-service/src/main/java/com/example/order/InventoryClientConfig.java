package com.example.order;

import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;

/**
 * Feign config for {@link InventoryClient} only. Deliberately NOT annotated with
 * {@code @Configuration}/{@code @Component}: component scanning would pick it up and make
 * these beans global for every Feign client.
 */
public class InventoryClientConfig {

    @Bean
    ErrorDecoder inventoryErrorDecoder() {
        return new FeignErrorDecoder();
    }
}
