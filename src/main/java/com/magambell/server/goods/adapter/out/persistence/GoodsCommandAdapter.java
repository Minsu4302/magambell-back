package com.magambell.server.goods.adapter.out.persistence;

import com.magambell.server.common.annotation.Adapter;
import com.magambell.server.common.s3.S3InputPort;
import com.magambell.server.common.s3.dto.ImageRegister;
import com.magambell.server.common.s3.dto.TransformedImageDTO;
import com.magambell.server.goods.adapter.in.web.GoodsImagesRegister;
import com.magambell.server.goods.app.port.in.dto.RegisterGoodsDTO;
import com.magambell.server.goods.app.port.out.GoodsCommandPort;
import com.magambell.server.goods.app.port.out.response.EditGoodsImageResponseDTO;
import com.magambell.server.goods.app.port.out.response.GoodsPreSignedUrlImage;
import com.magambell.server.goods.app.port.out.response.GoodsRegisterResponseDTO;
import com.magambell.server.goods.domain.entity.Goods;
import com.magambell.server.goods.domain.entity.GoodsImage;
import com.magambell.server.goods.domain.repository.GoodsImageRepository;
import com.magambell.server.goods.domain.repository.GoodsRepository;
import com.magambell.server.store.domain.entity.Store;
import lombok.RequiredArgsConstructor;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Adapter
public class GoodsCommandAdapter implements GoodsCommandPort {

    private static final String IMAGE_PREFIX = "GOODS";

    private final GoodsRepository goodsRepository;
    private final S3InputPort s3InputPort;
    private final GoodsImageRepository goodsImageRepository;

    @Override
    public GoodsRegisterResponseDTO registerGoods(final RegisterGoodsDTO dto) {
        Store store = dto.store();
        Goods goods = dto.toGoods();
        List<GoodsImagesRegister> goodsImagesRegisters = dto.goodsImagesRegisters();

        store.addGoods(goods);
        Goods goodsEntity = goodsRepository.save(goods);

        List<TransformedImageDTO> transformedImageDTOS = s3InputPort.saveImages(IMAGE_PREFIX,
                dto.toImage(), goodsEntity.getId());

        List<GoodsPreSignedUrlImage> goodsPreSignedUrlImages = addImagesAndGetPreSignedUrlImage(transformedImageDTOS);

        List<GoodsImage> goodsImageList = new ArrayList<>();
        for (int i = 0; i < goodsImagesRegisters.size(); i++) {
            goodsImageList.add(
                    GoodsImage.builder()
                            .goods(goodsEntity)
                            .goodsName(goodsImagesRegisters.get(i).goodsName())
                            .imageUrl(transformedImageDTOS.get(i).getUrl())
                            .build()
            );
        }

        goodsImageRepository.saveAll(goodsImageList);

        return new GoodsRegisterResponseDTO(goodsPreSignedUrlImages);
    }

    @Override
    public EditGoodsImageResponseDTO editGoodsImage(Goods goods, List<GoodsImagesRegister> goodsImagesRegisters) {
        // 기존 이미지 조회 및 Map 생성 (key 기준)
        List<GoodsImage> existingImages = goodsImageRepository.findByGoodsId(goods.getId());
        Map<String, GoodsImage> existingImageMap = existingImages.stream()
                .collect(Collectors.toMap(
                        img -> extractKeyFromUrl(img.getImageUrl()),
                        img -> img
                ));

        List<GoodsPreSignedUrlImage> goodsPreSignedUrlImages = new ArrayList<>();
        List<GoodsImage> imagesToSave = new ArrayList<>();
        List<ImageRegister> newImagesToUpload = new ArrayList<>();
        Map<Integer, GoodsImagesRegister> newImageIndexMap = new HashMap<>();

        // 요청 이미지 처리
        for (int i = 0; i < goodsImagesRegisters.size(); i++) {
            GoodsImagesRegister register = goodsImagesRegisters.get(i);
            String requestKey = resolveImageKey(register);

            // 기존 이미지인지 확인
            if (existingImageMap.containsKey(requestKey)) {
                // 기존 이미지 - ID 유지, goodsName만 업데이트
                GoodsImage existingImage = existingImageMap.get(requestKey);
                
                if (!existingImage.getGoodsName().equals(register.goodsName())) {
                    existingImage.setGoodsName(register.goodsName());
                }
                
                imagesToSave.add(existingImage);
                // 기존 이미지는 presignedUrl null
                goodsPreSignedUrlImages.add(new GoodsPreSignedUrlImage(register.id(), null));
                
                // 유지되는 이미지는 맵에서 제거
                existingImageMap.remove(requestKey);
            } else {
                // 새 이미지 - S3 업로드 필요
                newImagesToUpload.add(new ImageRegister(register.id(), requestKey));
                newImageIndexMap.put(newImagesToUpload.size() - 1, register);
            }
        }

        // 새 이미지 S3 업로드
        if (!newImagesToUpload.isEmpty()) {
            List<TransformedImageDTO> transformedImageDTOS = s3InputPort.saveImages(IMAGE_PREFIX, newImagesToUpload, goods.getId());
            
            for (int i = 0; i < transformedImageDTOS.size(); i++) {
                TransformedImageDTO transformedImage = transformedImageDTOS.get(i);
                GoodsImagesRegister register = newImageIndexMap.get(i);
                
                GoodsImage newImage = GoodsImage.builder()
                        .goods(goods)
                        .goodsName(register.goodsName())
                        .imageUrl(transformedImage.getUrl())
                        .build();
                imagesToSave.add(newImage);
                
                goodsPreSignedUrlImages.add(new GoodsPreSignedUrlImage(transformedImage.id(), transformedImage.putUrl()));
            }
        }

        // 삭제된 이미지 제거 (맵에 남아있는 것들)
        List<GoodsImage> imagesToDelete = new ArrayList<>(existingImageMap.values());
        if (!imagesToDelete.isEmpty()) {
            goodsImageRepository.deleteAll(imagesToDelete);
        }
        
        // 이미지 저장 (기존 업데이트 + 새 이미지 추가)
        goodsImageRepository.saveAll(imagesToSave);

        return new EditGoodsImageResponseDTO(goods.getId(), goodsPreSignedUrlImages);
    }

    private String resolveImageKey(final GoodsImagesRegister register) {
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


    private List<GoodsPreSignedUrlImage> addImagesAndGetPreSignedUrlImage(final List<TransformedImageDTO> imageDTOList) {
        return imageDTOList.stream()
                .map(imageDTO -> {
                    return new GoodsPreSignedUrlImage(imageDTO.id(), imageDTO.putUrl());
                })
                .toList();
    }
}
