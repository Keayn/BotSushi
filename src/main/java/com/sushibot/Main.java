package com.sushibot;


import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;


import java.util.Scanner;




public class Main {
    public static void main(String[] args) {
        try {
            System.out.println("Запускаю SushiBot...");

            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            SushiBot bot = new SushiBot();
            botsApi.registerBot(bot);

            System.out.println("✅ SushiBot запущен! 🍣");
            System.out.println("Нажмите Ctrl+C для остановки");

            // Запускаем поток для обработки команд рассылки
            startBroadcastListener(bot);

        } catch (TelegramApiException e) {
            System.out.println("❌ Ошибка: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void startBroadcastListener(SushiBot bot) {
        new Thread(() -> {
            Scanner scanner = new Scanner(System.in);
            System.out.println("\n📢 Для рассылки введите 'broadcast':");
            System.out.println("📝 Для выхода введите 'exit'");

            while (true) {
                String input = scanner.nextLine();

                if ("exit".equalsIgnoreCase(input)) {
                    System.out.println("Завершение работы...");
                    break;
                }

                if ("broadcast".equalsIgnoreCase(input)) {
                    System.out.println("Введите сообщение для рассылки:");
                    String message = scanner.nextLine();

                    if (message.trim().isEmpty()) {
                        System.out.println("❌ Сообщение не может быть пустым!");
                        continue;
                    }

                    System.out.println("Начинаю рассылку...");
                    bot.sendBroadcast(message); // Теперь этот метод должен быть public
                    System.out.println("Рассылка завершена!");
                } else {
                    System.out.println("❌ Неизвестная команда. Введите 'broadcast' для рассылки или 'exit' для выхода");
                }
            }

            scanner.close();
        }).start();
    }
}