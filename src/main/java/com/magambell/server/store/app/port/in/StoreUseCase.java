package com.magambell.server.store.app.port.in;

import com.magambell.server.review.adapter.out.persistence.ReviewListResponse;
import com.magambell.server.store.adapter.out.persistence.*;
import com.magambell.server.store.app.port.in.request.*;
import com.magambell.server.store.app.port.out.response.OpenRegionListDTO;
import com.magambell.server.store.app.port.out.response.OwnerStoreDetailDTO;
import com.magambell.server.store.app.port.out.response.StoreSearchResponse;

import java.util.List;

public interface StoreUseCase {
    StoreImagesResponse registerStore(RegisterStoreServiceRequest request, Long userId);

    StoreListResponse getStoreList(SearchStoreListServiceRequest request);

    void storeApprove(StoreApproveServiceRequest request);

    StoreDetailResponse getStoreDetail(Long storeId);

    OwnerStoreDetailDTO getOwnerStoreInfo(Long userId);

    StoreListResponse getCloseStoreList(CloseStoreListServiceRequest request);

    StoreListResponse getMapStoreList(MapStoreListServiceRequest request);

    StoreAdminListResponse getWaitingStoreList(WaitingStoreListServiceRequest request);

    StoreImagesResponse getStoreImageList(Long userId, Long storeId);

    StoreImagesResponse editStoreImage(EditStoreImageServiceRequest request);

    void registerOpenRegion(RegisterOpenRegionServiceRequest request, Long userId);

    List<OpenRegionListDTO> getOpenRegionList(OpenRegionListServiceRequest request);

    StoreSearchResponse searchStores(StoreSearchServiceRequest request);

}
