package com.sushibot;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

public class Main {
    public static void main(String[] args) {
        try {
            System.out.println("Запускаю SushiBot на Render...");

            // ЗАПУСКАЕМ HTTP СЕРВЕР ПЕРВЫМ ДЕЛОМ
            startHealthCheckServer();

            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            SushiBot bot = new SushiBot();
            botsApi.registerBot(bot);

            System.out.println("✅ SushiBot запущен! 🍣");
            System.out.println("🤖 Бот работает в облаке Render");

            // Бесконечный цикл
            keepApplicationRunning();

        } catch (Exception e) {
            System.out.println("❌ Ошибка: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void startHealthCheckServer() throws IOException {
        // Получаем порт из переменных окружения Render
        String portEnv = System.getenv("PORT");
        int port = (portEnv != null) ? Integer.parseInt(portEnv) : 8080;

        System.out.println("🚀 Запуск HTTP сервера на порту: " + port);

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new HealthCheckHandler());
        server.createContext("/health", new HealthCheckHandler());
        server.setExecutor(null);
        server.start();

        System.out.println("✅ HTTP сервер успешно запущен на порту: " + port);
    }

    static class HealthCheckHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String response = "✅ SushiBot is running! 🍣";
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
            exchange.sendResponseHeaders(200, response.getBytes("UTF-8").length);

            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes("UTF-8"));
            os.close();

            System.out.println("📊 Health check received");
        }
    }

    private static void keepApplicationRunning() {
        try {
            while (true) {
                Thread.sleep(30000); // 30 секунд
                System.out.println("❤️  Бот жив и работает...");
            }
        } catch (InterruptedException e) {
            System.out.println("Приложение завершено");
        }
    }
}