package com.magambell.server.store.app.port.out.response;

import java.util.List;

public record StoreSearchResponse(
        List<StoreSearchItemDTO> stores,
        String nextCursor,
        Boolean hasNext
) {
    public StoreSearchResponse {
        stores = List.copyOf(stores);
    }
}
