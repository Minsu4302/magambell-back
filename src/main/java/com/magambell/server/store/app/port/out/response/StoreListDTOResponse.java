package com.magambell.server.store.app.port.out.response;

import com.magambell.server.goods.domain.enums.SaleStatus;
import java.time.LocalDateTime;
import java.util.Set;

public record StoreListDTOResponse(
        Long storeId,
        String storeName,
        Set<String> ImageUrl,
        Double latitude,
        Double longitude,
        String address,
        String goodsName,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Integer originPrice,
        Integer discount,
        Integer salePrice,
        Integer quantity,
        Double distance,
        Integer walkMinutes,
        SaleStatus saleStatus
) {
    public StoreListDTOResponse {
        ImageUrl = ImageUrl == null ? Set.of() : ImageUrl;
    }
    public String getStoreId() {
        return String.valueOf(storeId);
    }
}
