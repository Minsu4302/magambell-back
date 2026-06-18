package com.magambell.server.favorite.app.port.out.response;

import com.magambell.server.goods.domain.enums.SaleStatus;
import java.time.LocalDateTime;
import java.util.List;

public record FavoriteStoreListDTOResponse(
        Long storeId,
        String storeName,
        String address,
        List<String> imageUrl,
        String goodsName,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Integer originPrice,
        Integer discount,
        Integer salePrice,
        Integer quantity,
        SaleStatus saleStatus
) {
    public FavoriteStoreListDTOResponse {
        imageUrl = imageUrl == null ? List.of() : imageUrl;
    }
    public String getStoreId() {
        return String.valueOf(storeId);
    }
}
