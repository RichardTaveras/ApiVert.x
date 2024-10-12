package com.myapp;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorkerTaskVerticle extends AbstractVerticle {
    private static final Logger logger = LoggerFactory.getLogger(WorkerTaskVerticle.class);

    @Override
    public void start(Promise<Void> startPromise) {
        // Registrar el consumidor en el EventBus para procesar tareas
        vertx.eventBus().consumer("worker.process.task", this::processTask);
        startPromise.complete();
    }

    private void processTask(Message<JsonObject> message) {
        JsonObject taskData = message.body();
        int taskId = taskData.getInteger("taskId");
        String description = taskData.getString("description", "No description");

        logger.info("Received task ID: {}, Description: {}", taskId, description);

        try {
            // Simulación de una tarea intensiva
            logger.info("Processing task ID: {} in thread {}", taskId, Thread.currentThread().getName());
            Thread.sleep(5000); // Simular procesamiento intensivo

            logger.info("Task ID: {} completed", taskId);

            // Responder al mensaje original
            message.reply(new JsonObject().put("status", "completed"));
        } catch (Exception e) {
            logger.error("Task ID: {} failed", taskId, e);
            message.fail(500, e.getMessage());
        }
    }
}
