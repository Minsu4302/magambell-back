package com.magambell.server.order.adapter.out.persistence;

import com.magambell.server.order.app.port.out.response.OrderListDTO;
import java.util.List;

public record OrderListResponse(List<OrderListDTO> orderListDTOList) {
    public OrderListResponse {
        orderListDTOList = List.copyOf(orderListDTOList);
    }
}
