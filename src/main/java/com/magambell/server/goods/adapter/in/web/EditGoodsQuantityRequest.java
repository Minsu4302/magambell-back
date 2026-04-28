package com.magambell.server.goods.adapter.in.web;

import com.magambell.server.goods.app.port.in.request.EditGoodsQuantityServiceRequest;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record EditGoodsQuantityRequest(
    @NotNull(message = "판매 개수를 입력해 주세요.")
        @Positive(message = "판매 개수는 1개 이상 이어야 합니다.")
        Integer quantity
) {
    public EditGoodsQuantityServiceRequest toService(final Long goodsId, final Long userId) {
        return new EditGoodsQuantityServiceRequest(goodsId, quantity, userId);
    }
}
