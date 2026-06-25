package com.magambell.server.store.app.port.out.response;

import java.time.LocalDateTime;
import java.util.Set;

public record StoreSearchItemDTO(
        Long storeId,
        String storeName,
        Set<String> imageUrls,
        String address,
        Double latitude,
        Double longitude,
        String description,
        LocalDateTime createdAt
) {
    public StoreSearchItemDTO {
        imageUrls = imageUrls == null ? Set.of() : imageUrls;
    }
    public String getStoreId() {
        return String.valueOf(storeId);
    }
}
