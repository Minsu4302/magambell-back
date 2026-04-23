package com.magambell.server.goods.app.port.in.request;

public record EditGoodsQuantityServiceRequest(
        Long goodsId,
        Integer quantity,
        Long userId
) {
}
