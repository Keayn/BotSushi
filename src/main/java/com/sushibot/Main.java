package com.sushibot;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

public class Main {
    public static void main(String[] args) {
        try {
            System.out.println("🚀 Запускаю SushiBot на Amvera...");

            // Получаем токен из переменных окружения
            String botToken = System.getenv("BOT_TOKEN");
            String botUsername = System.getenv("BOT_USERNAME");

            if (botToken == null || botUsername == null) {
                System.out.println("❌ Ошибка: Не заданы BOT_TOKEN или BOT_USERNAME");
                return;
            }

            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            SushiBot bot = new SushiBot(botToken, botUsername);
            botsApi.registerBot(bot);

            System.out.println("✅ SushiBot успешно запущен!");
            System.out.println("🤖 Бот: @" + botUsername);
            System.out.println("🌐 Хостинг: Amvera");

            keepApplicationRunning();

        } catch (TelegramApiException e) {
            System.out.println("❌ Ошибка: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void keepApplicationRunning() {
        try {
            while (true) {
                Thread.sleep(60000);
                System.out.println("⏰ Бот работает... " + new java.util.Date());
            }
        } catch (InterruptedException e) {
            System.out.println("Приложение завершено");
        }
    }
}