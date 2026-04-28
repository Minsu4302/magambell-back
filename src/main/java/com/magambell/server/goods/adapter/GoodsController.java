package com.magambell.server.goods.adapter;

import com.magambell.server.common.Response;
import com.magambell.server.common.security.CustomUserDetails;
import com.magambell.server.common.swagger.BaseResponse;
import com.magambell.server.goods.adapter.in.web.ChangeGoodsStatusRequest;
import com.magambell.server.goods.adapter.in.web.EditGoodsQuantityRequest;
import com.magambell.server.goods.adapter.in.web.EditGoodsImagesRequest;
import com.magambell.server.goods.adapter.in.web.EditGoodsRequest;
import com.magambell.server.goods.adapter.in.web.RegisterGoodsRequest;
import com.magambell.server.goods.adapter.out.persistence.GoodsImagesResponse;
import com.magambell.server.goods.adapter.out.persistence.GoodsQuantityResponse;
import com.magambell.server.goods.app.port.in.GoodsUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@Tag(name = "Goods", description = "Goods API")
@RequiredArgsConstructor
@RequestMapping("/api/v1/goods")
@RestController
public class GoodsController {

    private final GoodsUseCase goodsUseCase;

    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "바이트백 등록")
    @ApiResponse(responseCode = "200", content = {
            @Content(schema = @Schema(implementation = GoodsImagesResponse.class))})
    @PostMapping("")
    public Response<GoodsImagesResponse> registerGoods(
            @RequestBody @Validated final RegisterGoodsRequest request,
            @AuthenticationPrincipal final CustomUserDetails customUserDetails
    ) {
        return new Response<>(goodsUseCase.registerGoods(request.toService(), customUserDetails.userId()));
    }

    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "바이트백 판매 여부 변경")
    @ApiResponse(responseCode = "200", content = {
            @Content(schema = @Schema(implementation = BaseResponse.class))})
    @PatchMapping("/status")
    public Response<BaseResponse> changeGoodsStatus(
            @RequestBody @Validated final ChangeGoodsStatusRequest request,
            @AuthenticationPrincipal final CustomUserDetails customUserDetails
    ) {
        goodsUseCase.changeGoodsStatus(request.toService(customUserDetails.userId()), LocalDateTime.now());
        return new Response<>();
    }

    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "바이트백 변경")
    @ApiResponse(responseCode = "200", content = {
            @Content(schema = @Schema(implementation = GoodsImagesResponse.class))})
    @PatchMapping("")
    public Response<GoodsImagesResponse> editGoods(
            @RequestBody @Validated final EditGoodsRequest request,
            @AuthenticationPrincipal final CustomUserDetails customUserDetails
    ) {

        return new Response<>(goodsUseCase.editGoods(request.toService(customUserDetails.userId())));

    }

    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "바이트백 재고 변경")
    @ApiResponse(responseCode = "200", content = {
            @Content(schema = @Schema(implementation = GoodsQuantityResponse.class))})
    @PatchMapping("/{goodsId}/quantity")
    public Response<GoodsQuantityResponse> editGoodsQuantity(
            @PathVariable final Long goodsId,
            @RequestBody @Validated final EditGoodsQuantityRequest request,
            @AuthenticationPrincipal final CustomUserDetails customUserDetails
    ) {
        return new Response<>(goodsUseCase.editGoodsQuantity(request.toService(goodsId, customUserDetails.userId())));
    }

    @PreAuthorize("hasRole('OWNER')")
    @Operation(summary = "바이트백 이미지만 변경")
    @ApiResponse(responseCode = "200", content = {
            @Content(schema = @Schema(implementation = GoodsImagesResponse.class))})
    @PatchMapping("/images")
    public Response<GoodsImagesResponse> editGoodsImages(
            @RequestBody @Validated final EditGoodsImagesRequest request,
            @AuthenticationPrincipal final CustomUserDetails customUserDetails
    ) {

        return new Response<>(goodsUseCase.editGoodsImages(request.toService(customUserDetails.userId())));

    }
}
