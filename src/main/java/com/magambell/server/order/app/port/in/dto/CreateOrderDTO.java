package com.magambell.server.order.app.port.in.dto;

import static com.magambell.server.order.domain.enums.OrderStatus.PENDING;

import com.magambell.server.goods.domain.entity.Goods;
import com.magambell.server.order.domain.entity.Order;
import com.magambell.server.order.domain.entity.OrderGoods;
import com.magambell.server.user.domain.entity.User;
import java.time.LocalDateTime;

// JPA 엔티티를 그대로 전달하는 내부 커맨드 객체이므로 방어 복사 대신 경고 억제
@SuppressWarnings({"EI_EXPOSE_REP2", "EI_EXPOSE_REP"})
public record CreateOrderDTO(
        User user,
        Goods goods,
        Integer quantity,
        Integer totalPrice,
        LocalDateTime pickupTime,
        String memo
) {

    public Order toOrder() {
        return Order.create(this, PENDING);
    }

    public OrderGoods toOrderGoods() {
        return OrderGoods.create(this);
    }
}
