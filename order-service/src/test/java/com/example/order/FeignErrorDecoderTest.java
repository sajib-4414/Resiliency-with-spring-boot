package com.example.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.order.InventoryExceptions.InventoryRateLimitedException;
import com.example.order.InventoryExceptions.InventoryUnavailableException;
import com.example.order.InventoryExceptions.UnknownSkuException;
import feign.FeignException;
import feign.Request;
import feign.Response;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import org.junit.jupiter.api.Test;

//unit test case for feign client error decoder

class FeignErrorDecoderTest {

    private final FeignErrorDecoder decoder = new FeignErrorDecoder();

    @Test
    void maps404ToUnknownSku() {
        assertThat(decoder.decode("InventoryClient#getStock", response(404)))
                .isInstanceOf(UnknownSkuException.class);
    }

    @Test
    void maps429ToRateLimited() {
        assertThat(decoder.decode("InventoryClient#getStock", response(429)))
                .isInstanceOf(InventoryRateLimitedException.class);
    }

    @Test
    void maps5xxToUnavailable() {
        assertThat(decoder.decode("InventoryClient#getStock", response(500)))
                .isInstanceOf(InventoryUnavailableException.class);
        assertThat(decoder.decode("InventoryClient#getStock", response(503)))
                .isInstanceOf(InventoryUnavailableException.class);
    }

    @Test
    void otherStatusesFallBackToDefaultFeignException() {
        assertThat(decoder.decode("InventoryClient#getStock", response(400)))
                .isInstanceOf(FeignException.class);
    }

    private Response response(int status) {
        Request request = Request.create(
                Request.HttpMethod.GET, "/inventory/book", Collections.emptyMap(), null, StandardCharsets.UTF_8, null);
        return Response.builder()
                .status(status)
                .reason("test")
                .request(request)
                .headers(Collections.emptyMap())
                .build();
    }
}
