package com.magambell.server.notification.adapter;

import com.magambell.server.common.Response;
import com.magambell.server.common.security.CustomUserDetails;
import com.magambell.server.common.swagger.BaseResponse;
import com.magambell.server.notification.adapter.in.web.CheckStoreOpenServiceRequest;
import com.magambell.server.notification.adapter.in.web.SaveFcmTokenRequest;
import com.magambell.server.notification.adapter.in.web.SaveStoreOpenFcmTokenRequest;
import com.magambell.server.notification.adapter.out.persistence.CheckUserStoreOpenNotificationResponse;
import com.magambell.server.notification.adapter.out.persistence.StoreOpenSubscriberCountResponse;
import com.magambell.server.notification.app.port.in.NotificationUseCase;
import com.magambell.server.notification.app.port.in.request.DeleteStoreOpenFcmTokenServiceRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Tag(name = "Notification", description = "Notification API")
@RequiredArgsConstructor
@RequestMapping("/api/v1/notification")
@RestController
public class NotificationController {

    private final NotificationUseCase notificationUseCase;

    @Operation(summary = "매장 오픈 FCM 토큰 등록")
    @ApiResponse(responseCode = "200", content = {
            @Content(schema = @Schema(implementation = BaseResponse.class))})
    @PostMapping("/store")
    public Response<BaseResponse> saveStoreOpenToken(
            @RequestBody @Validated SaveStoreOpenFcmTokenRequest request,
            @AuthenticationPrincipal final CustomUserDetails customUserDetails
    ) {
        log.info("매장 오픈 알림 구독 요청 - storeId: {}, userId: {}, fcmToken: {}", 
                request.storeId(), customUserDetails.userId(), 
                request.fcmToken() != null ? request.fcmToken().substring(0, Math.min(50, request.fcmToken().length())) : "null");
        notificationUseCase.saveStoreOpenToken(request.toService(customUserDetails.userId()));
        log.info("매장 오픈 알림 구독 완료 - storeId: {}, userId: {}", request.storeId(), customUserDetails.userId());
        return new Response<>();
    }

    @Operation(summary = "매장 오픈 FCM 토큰 삭제")
    @ApiResponse(responseCode = "200", content = {
            @Content(schema = @Schema(implementation = BaseResponse.class))})
    @DeleteMapping("/store/{storeId}")
    public Response<BaseResponse> deleteStoreOpenToken(
            @PathVariable Long storeId,
            @AuthenticationPrincipal final CustomUserDetails customUserDetails
    ) {
        notificationUseCase.deleteStoreOpenToken(new DeleteStoreOpenFcmTokenServiceRequest(storeId,
                customUserDetails.userId()));
        return new Response<>();
    }

    @Operation(summary = "FCM 토큰 등록")
    @ApiResponse(responseCode = "200", content = {
            @Content(schema = @Schema(implementation = BaseResponse.class))})
    @PostMapping("")
    public Response<BaseResponse> saveToken(
            @RequestBody @Validated SaveFcmTokenRequest request,
            @AuthenticationPrincipal final CustomUserDetails customUserDetails
    ) {
        notificationUseCase.saveToken(request.toService(customUserDetails.userId()));
        return new Response<>();
    }

    @Operation(summary = "FCM 발송 테스트")
    @ApiResponse(responseCode = "200", content = {
            @Content(schema = @Schema(implementation = BaseResponse.class))})
    @GetMapping("")
    public Response<BaseResponse> testSendToken(
            @AuthenticationPrincipal final CustomUserDetails customUserDetails
    ) {
        //todo 테스트 완료 후 삭제 예정
        notificationUseCase.testSendToken(customUserDetails.userId());
        return new Response<>();
    }

    @Operation(summary = "고객 매장 오픈 알림 여부 조회")
    @ApiResponse(responseCode = "200", content = {
            @Content(schema = @Schema(implementation = CheckUserStoreOpenNotificationResponse.class))})
    @GetMapping("/store/{storeId}")
    public Response<CheckUserStoreOpenNotificationResponse> checkStoreOpen(
            @PathVariable Long storeId,
            @AuthenticationPrincipal final CustomUserDetails customUserDetails
    ) {
        boolean exists = notificationUseCase.checkUserStoreOpen(new CheckStoreOpenServiceRequest(storeId,
                customUserDetails.userId()));
        return new Response<>(new CheckUserStoreOpenNotificationResponse(exists));
    }

    @Operation(summary = "매장 오픈 알림 신청자 수 조회")
    @ApiResponse(responseCode = "200", content = {
            @Content(schema = @Schema(implementation = StoreOpenSubscriberCountResponse.class))})
    @GetMapping("/store/{storeId}/subscribers")
    public Response<StoreOpenSubscriberCountResponse> getStoreOpenSubscriberCount(
            @PathVariable Long storeId
    ) {
        long subscriberCount = notificationUseCase.getStoreOpenSubscriberCount(storeId);
        return new Response<>(new StoreOpenSubscriberCountResponse(subscriberCount));
    }
}
