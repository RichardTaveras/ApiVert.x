package com.myapp;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TaskProcessorVerticle extends AbstractVerticle {
    private static final Logger logger = LoggerFactory.getLogger(TaskProcessorVerticle.class);

    @Override
    public void start(Promise<Void> startPromise) {
        // Registra el consumidor en el Event Bus para procesar tareas
        vertx.eventBus().consumer("process.task", this::processTask);
        startPromise.complete();
    }

    private void processTask(Message<JsonObject> message) {
        JsonObject taskData = message.body();
        int taskId = taskData.getInteger("taskId");
        String description = taskData.getString("description", "No description");

        logger.info("Received task ID: {}, Description: {}", taskId, description);

        // Notificar al inicio del procesamiento
        sendTaskNotification(taskId, "en proceso", description);

        vertx.setTimer(5000, id -> {
            try {
                // Simulación de una tarea intensiva
                logger.info("Processing task ID: {} in thread {}", taskId, Thread.currentThread().getName());

                // Notificar cuando la tarea esté en proceso
                sendTaskNotification(taskId, "en proceso", description);

                Thread.sleep(5000); // Simular procesamiento intensivo

                logger.info("Task ID: {} completed", taskId);

                // Notificar cuando la tarea se haya completado
                sendTaskNotification(taskId, "completada", description);

                // Responder al mensaje original
                message.reply("Task completed successfully");
            } catch (Exception e) {
                logger.error("Task ID: {} failed", taskId, e);
                message.fail(500, e.getMessage());
            }
        });
    }

    private void sendTaskNotification(int taskId, String status, String description) {
        JsonObject notificationData = new JsonObject()
                .put("title", "Estado de la Tarea")
                .put("body", "La tarea con ID " + taskId + " está " + status + ". Descripción: " + description);

        // Publicar la notificación en el Event Bus para ser enviada por PushNotificationVerticle
        vertx.eventBus().publish("send.push.notification", notificationData);
    }
}
