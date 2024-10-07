package com.myapp;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.*;
import com.google.auth.oauth2.GoogleCredentials;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;

public class PushNotificationVerticle extends AbstractVerticle {
    private static final Logger logger = LoggerFactory.getLogger(PushNotificationVerticle.class);
    private FirebaseMessaging firebaseMessaging;

    @Override
    public void start(Promise<Void> startPromise) {
        try {
            // Configurar Firebase
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(new FileInputStream("src/main/resources/serviceAccountKey.json")))
                    .build();

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
            }

            firebaseMessaging = FirebaseMessaging.getInstance();

            // Consumir mensajes del Event Bus
            vertx.eventBus().consumer("send.push.notification", message -> {
                JsonObject notificationData = (JsonObject) message.body();
                String title = notificationData.getString("title");
                String body = notificationData.getString("body");

                // Obtener los tokens registrados
                Map<String, Boolean> deviceTokens = vertx.sharedData().getLocalMap("deviceTokens");

                for (String token : deviceTokens.keySet()) {
                    Notification notification = Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build();

                    Message msg = Message.builder()
                            .setNotification(notification)
                            .setToken(token)
                            .build();

                    ApiFuture<String> response = firebaseMessaging.sendAsync(msg);

                    // Manejar el resultado de la notificación
                    ApiFutures.addCallback(response, new ApiFutureCallback<String>() {
                        @Override
                        public void onFailure(Throwable throwable) {
                            logger.error("Error al enviar la notificación al token {}: {}", token, throwable.getMessage());
                        }

                        @Override
                        public void onSuccess(String result) {
                            logger.info("Notificación enviada al token {}: {}", token, result);
                        }
                    }, MoreExecutors.directExecutor());
                }
            });

            startPromise.complete();
        } catch (IOException e) {
            logger.error("Error al inicializar Firebase: {}", e.getMessage());
            startPromise.fail(e);
        }
    }
}
