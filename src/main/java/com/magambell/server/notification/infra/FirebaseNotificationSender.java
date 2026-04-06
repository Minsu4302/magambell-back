package com.magambell.server.notification.infra;

import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FirebaseNotificationSender {

    public void send(String fcmToken, String title, String body) throws FirebaseMessagingException {
        send(fcmToken, title, body, Map.of());
    }

    public void send(String fcmToken, String title, String body, Map<String, String> data)
            throws FirebaseMessagingException {
        log.info("FCM 알림 전송 시작 - token: {}, title: {}, body: {}", fcmToken, title, body);
        Message.Builder messageBuilder = Message.builder()
                .setToken(fcmToken)
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .setApnsConfig(ApnsConfig.builder()
                        .setAps(Aps.builder()
                                .setSound("default")
                                .build())
                        .build());

        if (data != null && !data.isEmpty()) {
            messageBuilder.putAllData(data);
        }

        Message message = messageBuilder.build();

        String messageId = FirebaseMessaging.getInstance().send(message);
        log.info("FCM 알림 전송 성공 - token: {}, messageId: {}", fcmToken, messageId);
    }
}
