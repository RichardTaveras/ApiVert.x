package com.myapp;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class App extends AbstractVerticle {
    private static final Logger logger = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(new App());
        vertx.deployVerticle(new TaskProcessorVerticle());
        vertx.deployVerticle(new PushNotificationVerticle());
    }

    @Override
    public void start(Promise<Void> startPromise) {
        Router router = Router.router(vertx);

        // Manejar el cuerpo de las solicitudes
        router.route().handler(BodyHandler.create());

        // Endpoint de salud
        router.get("/health").handler(this::healthCheck);

        // Endpoint para registrar tokens
        router.post("/register").handler(this::registerToken);

        // Endpoint para procesar tareas
        router.post("/tasks").handler(this::processTask);

        // Endpoint para enviar notificaciones
        router.post("/sendNotification").handler(this::sendNotification);

        // Iniciar el servidor HTTP y manejar las solicitudes y los WebSockets
        vertx.createHttpServer()
            .requestHandler(router)
            .webSocketHandler(this::handleWebSocket)
            .listen(8081, http -> {
                if (http.succeeded()) {
                    logger.info("Servidor HTTP iniciado en el puerto 8081");
                    startPromise.complete();
                } else {
                    logger.error("Error al iniciar el servidor HTTP: {}", http.cause().getMessage());
                    startPromise.fail(http.cause());
                }
            });
    }

    private void healthCheck(RoutingContext context) {
        context.response().end("OK");
    }

    private void registerToken(RoutingContext context) {
        JsonObject body = context.body().asJsonObject();
        if (body == null || !body.containsKey("token")) {
            context.response().setStatusCode(400).end("Invalid request: missing token");
            return;
        }
        String token = body.getString("token");
        vertx.sharedData().getLocalMap("deviceTokens").put(token, true);
        logger.info("Token registrado: {}", token);
        context.response().setStatusCode(200).end("Token registrado exitosamente");
    }

    private void processTask(RoutingContext context) {
        JsonObject body = context.body().asJsonObject();
        if (body == null || !body.containsKey("taskId")) {
            context.response().setStatusCode(400).end("Invalid request: missing taskId");
            return;
        }
        int taskId = body.getInteger("taskId");
        String description = body.getString("description", "No description");

        JsonObject taskData = new JsonObject()
                .put("taskId", taskId)
                .put("description", description);

        vertx.eventBus().<String>request("process.task", taskData, reply -> {
            if (reply.succeeded()) {
                context.response().setStatusCode(200).end(reply.result().body());
            } else {
                context.response().setStatusCode(500).end("Failed to process task: " + reply.cause().getMessage());
            }
        });
    }

    private void sendNotification(RoutingContext context) {
        JsonObject body = context.body().asJsonObject();
        String title = body.getString("title", "Título por defecto");
        String message = body.getString("message", "Mensaje por defecto");

        JsonObject notificationData = new JsonObject()
                .put("title", title)
                .put("body", message);

        // Publicar en el Event Bus
        vertx.eventBus().publish("send.push.notification", notificationData);

        context.response().setStatusCode(200).end("Notificación enviada");
    }

    private void handleWebSocket(ServerWebSocket webSocket) {
        if (webSocket.path().startsWith("/taskStatus/")) {
            String taskId = webSocket.path().substring("/taskStatus/".length());
            logger.info("Conexión WebSocket abierta para la tarea ID: {}", taskId);

            vertx.eventBus().consumer("task.status." + taskId, message -> {
                webSocket.writeTextMessage(message.body().toString());
            });

            webSocket.closeHandler(v -> logger.info("Conexión WebSocket cerrada para la tarea ID: {}", taskId));
        } else {
            webSocket.reject();
        }
    }
}
