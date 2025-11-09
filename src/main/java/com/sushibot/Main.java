package com.sushibot;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

public class Main {
    public static void main(String[] args) {
        try {
            System.out.println("🚀 Запускаю SushiBot на Railway...");

            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            SushiBot bot = new SushiBot();
            botsApi.registerBot(bot);

            System.out.println("✅ SushiBot успешно запущен! 🍣");
            System.out.println("🤖 Бот работает в облаке Railway");

            // Бесконечный цикл
            while (true) {
                Thread.sleep(60000);
                System.out.println("❤️ Бот жив и работает...");
            }

        } catch (Exception e) {
            System.out.println("❌ Ошибка запуска: " + e.getMessage());
            e.printStackTrace();
        }
    }
}