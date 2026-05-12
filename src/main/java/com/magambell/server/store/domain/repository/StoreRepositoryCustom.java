package com.magambell.server.store.domain.repository;

import com.magambell.server.store.adapter.out.persistence.StoreDetailResponse;
import com.magambell.server.store.app.port.in.request.CloseStoreListServiceRequest;
import com.magambell.server.store.app.port.in.request.MapStoreListServiceRequest;
import com.magambell.server.store.app.port.in.request.OpenRegionListServiceRequest;
import com.magambell.server.store.app.port.in.request.SearchStoreListServiceRequest;
import com.magambell.server.store.app.port.in.request.StoreSearchServiceRequest;
import com.magambell.server.store.app.port.out.response.OpenRegionListDTO;
import com.magambell.server.store.app.port.out.response.OwnerStoreDetailDTO;
import com.magambell.server.store.app.port.out.response.StoreAdminListDTO;
import com.magambell.server.store.app.port.out.response.StoreListDTOResponse;
import com.magambell.server.store.app.port.out.response.StoreSearchItemDTO;
import com.magambell.server.store.domain.entity.Store;
import com.magambell.server.store.domain.entity.StoreImage;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;

public interface StoreRepositoryCustom {

    List<StoreListDTOResponse> getStoreList(SearchStoreListServiceRequest request, Pageable pageable);

    Optional<StoreDetailResponse> getStoreDetail(Long storeId);

    Optional<OwnerStoreDetailDTO> getOwnerStoreInfo(Long userId);

    List<StoreListDTOResponse> getCloseStoreList(CloseStoreListServiceRequest request);

    List<StoreListDTOResponse> getMapStoreList(MapStoreListServiceRequest request);

    List<StoreAdminListDTO> getWaitingStoreList(Pageable pageable);

    List<StoreAdminListDTO> getAllApprovedStores();

    Optional<Long> findOwnerIdByStoreId(Long storeId);

    List<StoreImage> getStoreImageList(Long storeId);

    Store getStoreAndStoreImages(Long storeId);

    List<StoreSearchItemDTO> searchStores(StoreSearchServiceRequest request);

}
