package com.sushibot;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

public class Main {
    public static void main(String[] args) {
        try {
            System.out.println("Запускаю SushiBot на Render...");

            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            SushiBot bot = new SushiBot();
            botsApi.registerBot(bot);

            System.out.println("✅ SushiBot запущен! 🍣");
            System.out.println("🤖 Бот работает в облаке Render");

            // УБИРАЕМ Scanner и добавляем бесконечный цикл для Render
            keepApplicationRunning();

        } catch (TelegramApiException e) {
            System.out.println("❌ Ошибка: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void keepApplicationRunning() {
        // Бесконечный цикл чтобы приложение не завершалось на Render
        try {
            System.out.println("🔄 Приложение работает...");
            while (true) {
                Thread.sleep(60000); // Ждем 60 секунд
                System.out.println("⏰ Бот все еще работает...");
            }
        } catch (InterruptedException e) {
            System.out.println("Приложение завершено");
        }
    }
}