package com.magambell.server.goods.adapter.in.web;

import com.magambell.server.goods.app.port.in.request.RegisterGoodsServiceRequest;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDateTime;
import java.util.List;

public record RegisterGoodsRequest(

        String name,

        @NotNull(message = "판매 시작 시간은 선택해 주세요.")
        LocalDateTime startTime,

        @NotNull(message = "판매 마감 시간은 선택해 주세요.")
        LocalDateTime endTime,

        @Positive(message = "판매 개수는 1개 이상 이어야 합니다.")
        Integer quantity,

        @PositiveOrZero(message = "정가는 0원 이상 이어야 합니다.")
        Integer originalPrice,

        @PositiveOrZero(message = "할인율은 0% 이상 이어야 합니다.")
        Integer discount,

        @PositiveOrZero(message = "판매가는 0원 이상 이어야 합니다.")
        Integer salePrice,

        List<GoodsImagesRegister> goodsImagesRegisters

) {
    public RegisterGoodsServiceRequest toService() {
        return new RegisterGoodsServiceRequest(
                resolveName(), startTime, endTime, quantity, originalPrice, discount, salePrice, goodsImagesRegisters
        );
    }

    private String resolveName() {
        if (name != null && !name.isBlank()) {
            return name;
        }

        if (goodsImagesRegisters == null || goodsImagesRegisters.isEmpty()) {
            return null;
        }

        return goodsImagesRegisters.stream()
                .map(GoodsImagesRegister::goodsName)
                .filter(goodsName -> goodsName != null && !goodsName.isBlank())
                .findFirst()
                .orElse(null);
    }
}
