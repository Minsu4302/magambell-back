package com.magambell.server.goods.adapter.out.persistence;

import java.time.LocalDateTime;

public record GoodsQuantityResponse(
        Long goodsId,
        Integer beforeQuantity,
        Integer quantity,
        LocalDateTime updatedAt
) {
}
