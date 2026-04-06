package com.magambell.server.store.adapter.out.persistence;

import com.magambell.server.common.annotation.Adapter;
import com.magambell.server.common.enums.ErrorCode;
import com.magambell.server.common.exception.DuplicateException;
import com.magambell.server.common.exception.NotFoundException;
import com.magambell.server.common.exception.TokenExpiredException;
import com.magambell.server.common.s3.S3InputPort;
import com.magambell.server.common.s3.dto.ImageRegister;
import com.magambell.server.common.s3.dto.TransformedImageDTO;
import com.magambell.server.store.adapter.in.web.StoreImagesRegister;
import com.magambell.server.store.app.port.in.dto.OpenRegionDTO;
import com.magambell.server.store.app.port.in.dto.RegisterStoreDTO;
import com.magambell.server.store.app.port.out.StoreCommandPort;
import com.magambell.server.store.app.port.out.response.EditStoreImageResponseDTO;
import com.magambell.server.store.app.port.out.response.StorePreSignedUrlImage;
import com.magambell.server.store.app.port.out.response.StoreRegisterResponseDTO;
import com.magambell.server.store.domain.entity.OpenRegion;
import com.magambell.server.store.domain.entity.Store;
import com.magambell.server.store.domain.entity.StoreImage;
import com.magambell.server.store.domain.repository.OpenRegionRepository;
import com.magambell.server.store.domain.repository.StoreRepository;
import com.magambell.server.user.domain.entity.User;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Adapter
public class StoreCommandAdapter implements StoreCommandPort {

    private static final String IMAGE_PREFIX = "STORE";

    private final StoreRepository storeRepository;
    private final S3InputPort s3InputPort;
    private final OpenRegionRepository openRegionRepository;

    @Override
    public StoreRegisterResponseDTO registerStore(final RegisterStoreDTO dto) {
        User user = dto.user();
        Store store = dto.toEntity();
        user.addStore(store);
        List<TransformedImageDTO> transformedImageDTOS = s3InputPort.saveImages(IMAGE_PREFIX,
                dto.toImage(), user);
        List<StorePreSignedUrlImage> storePreSignedUrlImages = addImagesAndGetPreSignedUrlImage(transformedImageDTOS,
                store);

        storeRepository.save(store);

        return new StoreRegisterResponseDTO(store.getId(), storePreSignedUrlImages);
    }

    @Override
    public void storeApprove(final Long storeId) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.STORE_NOT_FOUND));
        store.approve();
    }

    @Override
    public EditStoreImageResponseDTO editStoreImage(final Store store,
                                                    final List<StoreImagesRegister> storeImagesRegisters) {
        Map<String, String> existingImageUrlByKey = store.getStoreImages().stream()
                .collect(java.util.stream.Collectors.toMap(
                        image -> extractKeyFromUrl(image.getName()),
                        StoreImage::getName,
                        (left, right) -> left
                ));

        store.getStoreImages().clear();

        List<StorePreSignedUrlImage> storePreSignedUrlImages = new ArrayList<>();
        List<ImageRegister> newImagesToUpload = new ArrayList<>();
        Map<Integer, Integer> uploadIndexToOrder = new HashMap<>();

        for (StoreImagesRegister register : storeImagesRegisters) {
            String imageKey = resolveImageKey(register);
            Integer imageOrder = register.id();

            if (existingImageUrlByKey.containsKey(imageKey)) {
                store.addStoreImage(StoreImage.create(existingImageUrlByKey.get(imageKey), imageOrder));
                storePreSignedUrlImages.add(new StorePreSignedUrlImage(imageOrder, null));
            } else {
                uploadIndexToOrder.put(newImagesToUpload.size(), imageOrder);
                newImagesToUpload.add(new ImageRegister(imageOrder, imageKey));
            }
        }

        if (!newImagesToUpload.isEmpty()) {
            User user = store.getUser();
            List<TransformedImageDTO> transformedImageDTOS = s3InputPort.saveImages(IMAGE_PREFIX, newImagesToUpload, user);

            for (int i = 0; i < transformedImageDTOS.size(); i++) {
                TransformedImageDTO transformedImageDTO = transformedImageDTOS.get(i);
                Integer imageOrder = uploadIndexToOrder.get(i);
                store.addStoreImage(StoreImage.create(transformedImageDTO.getUrl(), imageOrder));
                storePreSignedUrlImages.add(new StorePreSignedUrlImage(imageOrder, transformedImageDTO.putUrl()));
            }
        }

        return new EditStoreImageResponseDTO(store.getId(), storePreSignedUrlImages);
    }

    private String resolveImageKey(final StoreImagesRegister register) {
        if (register.key() != null && !register.key().isBlank()) {
            return extractKeyFromUrl(register.key());
        }
        return extractKeyFromUrl(register.imageUrl());
    }

    private String extractKeyFromUrl(final String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return "";
        }

        String candidate = imageUrl;
        try {
            URI uri = URI.create(imageUrl);
            String path = uri.getPath();
            if (path != null && !path.isBlank()) {
                candidate = path;
            } else {
                candidate = stripQueryAndFragment(imageUrl);
            }
        } catch (IllegalArgumentException ignored) {
            candidate = stripQueryAndFragment(imageUrl);
        }

        int lastSlashIndex = candidate.lastIndexOf('/');
        if (lastSlashIndex >= 0 && lastSlashIndex < candidate.length() - 1) {
            return candidate.substring(lastSlashIndex + 1);
        }
        return candidate;
    }

    private String stripQueryAndFragment(final String value) {
        int queryIndex = value.indexOf('?');
        int fragmentIndex = value.indexOf('#');

        int cutIndex = -1;
        if (queryIndex >= 0 && fragmentIndex >= 0) {
            cutIndex = Math.min(queryIndex, fragmentIndex);
        } else if (queryIndex >= 0) {
            cutIndex = queryIndex;
        } else if (fragmentIndex >= 0) {
            cutIndex = fragmentIndex;
        }

        if (cutIndex >= 0) {
            return value.substring(0, cutIndex);
        }
        return value;
    }

    @Override
    public void registerOpenRegion(final OpenRegionDTO dto) {
        openRegionRepository.findByUserAndRegionEntity(dto.user(), dto.region())
                .ifPresent(region -> {
                    throw new DuplicateException(ErrorCode.DUPLICATE_OPEN_REGION_REQUEST);
                });

        openRegionRepository.save(OpenRegion.create(dto));
    }

    private List<StorePreSignedUrlImage> addImagesAndGetPreSignedUrlImage(final List<TransformedImageDTO> imageDTOList,
                                                                          final Store store) {
        return imageDTOList.stream()
                .map(imageDTO -> {
                    store.addStoreImage(StoreImage.create(imageDTO.getUrl(), imageDTO.id()));
                    return new StorePreSignedUrlImage(imageDTO.id(), imageDTO.putUrl());
                })
                .toList();
    }
}
