package com.sushibot;

import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Contact;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;


import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


public class SushiBot extends TelegramLongPollingBot {

    // Добавьте в начало класса
    private static final int MAX_REQUESTS_PER_MINUTE = 20;
    private static final int MAX_BOOKINGS_PER_HOUR = 3;// 30 секунд

    private static final int SPAM_TIMEOUT_MS = 1000; // 1 секунда между сообщениями
    private static final int MAX_SPAM_ATTEMPTS = 5; // 5 попыток до блокировки

    private static final long BLOCK_DURATION_MS = 3600000; // 1 час блокировки
    private static final long PERMANENT_BLOCK_AFTER = 3; // 3 временных блокировки → перманентная

    private final Map<Long, Integer> spamAttempts = new HashMap<>();
    private final Map<Long, Long> blockedUsers = new HashMap<>();
    private final Map<Long, Integer> temporaryBlocksCount = new HashMap<>();
    private final Set<Long> permanentlyBlocked = new HashSet<>();

    private final Map<Long, List<Long>> userRequestTimestamps = new HashMap<>();
    private final Map<Long, List<Long>> userBookingTimestamps = new HashMap<>();
    private final Map<Long, Long> lastMessageTime = new HashMap<>();
    private final Map<Long, Long> lastIncomingMessageTime = new ConcurrentHashMap<>();

    private final Map<Long, String> userDatabase = new HashMap<>();
    private final Map<Long, String> bookingState = new HashMap<>();
    private final Map<Long, String> bookingData = new HashMap<>(); // Для хранения данных бронирования
    private final Map<String, String> menuLinks = new HashMap<>();
    private static final String WAITING_PHONE_START = "waiting_phone_start";
    private final Map<Long, String> userPhones = new HashMap<>(); // Для хранения номеров

    // Константы для статусов брони
    private static final String BOOKING_PENDING = "pending";
    private static final String BOOKING_CONFIRMED = "confirmed";
    private static final String BOOKING_REJECTED = "rejected";

    // Константы для callback данных
    private static final String CONFIRM_BOOKING = "confirm_booking_";
    private static final String REJECT_BOOKING = "reject_booking_";

    // ЗАМЕНИТЕ старые константы на эти
    private static final int MAX_GUESTS = 15;
    private static final String TIME_PATTERN = "^([0-1]?[0-9]|2[0-3]):[0-5][0-9]$";
    private static final String DATE_PATTERN = "^(0[1-9]|[12][0-9]|3[01])\\.(0[1-9]|1[0-2])\\.(20[2-9][0-9])$";
    private static final String NAME_PATTERN = "^[a-zA-Zа-яА-ЯёЁ\\s\\-]{2,50}$";

    // Map для хранения статусов брони
    private final Map<Long, String> bookingStatus = new HashMap<>();

    private final Map<Long, String> adminUsers = new HashMap<>();
    private final Long firstAdminChatId = 456073761L;
    private String firstAdminName = "Back";
    private int requestCount = 0;
    private final String botUsername;

    public SushiBot(String botUsername, String username) {
        super(new DefaultBotOptions());
        this.botUsername = botUsername;


        setBotCommands();
        initializeMenuLinks();

        System.out.println("✅ Бот инициализирован");
        System.out.println("✅ Админы добавлены: " + adminUsers);
        System.out.println("✅ Первый админ: " + firstAdminChatId);
    }



    // Модифицируйте проверку прав
    private boolean isAdmin(Long chatId) {
        // Если пользователь в списке админов - точно админ
        if (adminUsers.containsKey(chatId)) {
            return true;
        }

        // Если это первый админ И он не был удален (т.е. firstAdminChatId не null)
        return firstAdminChatId.equals(chatId);
    }

    private void initializeMenuLinks() {
        // Здесь укажите ваши реальные ссылки
        menuLinks.put("salads", "https://sushi-e.ru/salat");
        menuLinks.put("hot", "https://sushi-e.ru/hot");
        menuLinks.put("rolls", "https://sushi-e.ru/sushi");
        menuLinks.put("pizza", "https://sushi-e.ru/pizza");
        menuLinks.put("snacks", "https://sushi-e.ru/zakuski");
        menuLinks.put("desserts", "https://sushi-e.ru/desert");
    }

    private void setBotCommands() {
        List<BotCommand> commands = new ArrayList<>();
        commands.add(new BotCommand("start", "Запустить бота"));
        commands.add(new BotCommand("menu", "Меню ресторана"));
        commands.add(new BotCommand("book", "Забронировать столик"));
        commands.add(new BotCommand("promo", "Акции и скидки"));
        commands.add(new BotCommand("website", "Наш сайт"));
        commands.add(new BotCommand("help", "Помощь и поддержка"));
        commands.add(new BotCommand("contacts", "Контакты ресторана"));
        commands.add(new BotCommand("mystatus", "Статус бронирования"));

        // Только для админов добавим админские команды
        if (!adminUsers.isEmpty()) {
            commands.add(new BotCommand("admin", "Панель администратора"));
            commands.add(new BotCommand("broadcast", "Рассылка сообщений"));
        }

        try {
            execute(new SetMyCommands(commands, new BotCommandScopeDefault(), null));
            System.out.println("✅ Боковое меню команд установлено!");
        } catch (TelegramApiException e) {
            System.out.println("❌ Ошибка установки меню: " + e.getMessage());
        }
    }





    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public String getBotToken() {
        // Токен теперь передается через конструктор
        return super.getBotToken();
    }


    public void onUpdateReceived(Update update) {
        requestCount++;

        // Очистка каждые 50 запросов
        if (requestCount % 50 == 0) {
            cleanupOldData();
            System.out.println("🧹 Очистка данных выполнена");
        }

        if (update.hasMessage()) {
            Message message = update.getMessage();
            Long chatId = message.getChatId();
            String userName = message.getChat().getFirstName();

            // Проверка спама для ВХОДЯЩИХ сообщений (только для обычных пользователей)
            if (!isAdmin(chatId) && isIncomingSpam(chatId)) {
                registerSpamAttempt(chatId);
                sendMessage(chatId, "⚠️ Слишком много запросов! Подождите немного.");
                return;
            }

            // Проверка блокировки (для всех)
            if (isPermanentlyBlocked(chatId) || isTemporarilyBlocked(chatId)) {
                return;
            }

            // Обработка контакта
            if (message.hasContact()) {
                handleContactReceived(message);
                return;
            }

            // Обработка текстовых сообщений
            if (message.hasText()) {
                String messageText = message.getText();

                // Сохраняем пользователя
                userDatabase.put(chatId, userName);

                // Обновляем имя админа если нужно
                if (isAdmin(chatId)) {
                    updateAdminName(chatId, userName);
                }

                System.out.println("Сообщение от " + userName + " (ID: " + chatId + "): " + messageText);

                // Обработка специальных состояний (ВКЛЮЧАЯ отмену)
                if (handleSpecialStates(chatId, messageText, userName)) {
                    return;
                }

                // Обработка отмены
                if (isCancelCommand(messageText)) {
                    handleCancelAction(chatId, userName);
                    return;
                }

                // Обработка команд и кнопок
                if (messageText.startsWith("/")) {
                    handleCommand(messageText, chatId, userName);
                } else {
                    handleButton(messageText, chatId, userName);
                }
            }
        }
        // Обработка callback query
        else if (update.hasCallbackQuery()) {
            handleCallbackQuery(update.getCallbackQuery());
        }
    }

    private boolean handleSpecialStates(Long chatId, String messageText, String userName) {
        // Сначала проверяем отмену - это должно быть ВНУТРИ обработки состояний
        if (isCancelCommand(messageText)) {
            handleCancelAction(chatId, userName);
            return true;
        }

        // Обработка состояния ожидания номера при старте
        if (WAITING_PHONE_START.equals(bookingState.get(chatId))) {
            if ("🚫 Пропустить".equals(messageText)) {
                userPhones.put(chatId, "Не указан");
                bookingState.remove(chatId);
                sendMessage(chatId, "⚠️ Вы можете добавить номер позже через /book");
                sendMainWelcomeMessage(chatId, userName);
            }
            return true;
        }

        // Обработка состояния ожидания номера для бронирования
        if ("waiting_phone_booking".equals(bookingState.get(chatId))) {
            if ("❌ Отменить".equals(messageText)) {
                bookingState.remove(chatId);
                sendMessage(chatId, "❌ Бронирование отменено");
                sendMainWelcomeMessage(chatId, userName);
            } else {
                sendMessage(chatId, "📞 Пожалуйста, нажмите кнопку 'Поделиться номером' или 'Отменить'");
            }
            return true;
        }

        // Обработка рассылки
        if ("waiting_broadcast".equals(bookingState.get(chatId))) {
            sendBroadcast(messageText);
            bookingState.remove(chatId);
            sendMessage(chatId, "✅ Рассылка отправлена " + userDatabase.size() + " пользователям!");
            return true;
        }

        // Обработка команды /addadmin
        if (messageText.startsWith("/addadmin") && isAdmin(chatId)) {
            handleAddAdmin(chatId, messageText);
            return true;
        }

        // Обработка состояния бронирования
        if (bookingState.containsKey(chatId)) {
            handleBookingResponse(chatId, messageText, userName);
            return true;
        }

        return false;
    }

    private void updateAdminName(Long chatId, String userName) {
        if (firstAdminChatId.equals(chatId)) {
            firstAdminName = userName;
        } else if (adminUsers.containsKey(chatId)) {
            adminUsers.put(chatId, userName);
        }
        System.out.println("✅ Обновлено имя админа: " + chatId + " - " + userName);
    }

    private boolean isCancelCommand(String messageText) {
        return "❌ Отменить".equals(messageText) ||
                "❌ Отменить бронь".equals(messageText) ||
                "Отменить".equals(messageText);
    }



    private void handleAddAdmin(Long chatId, String message) {
        try {
            String[] parts = message.split(" ");
            if (parts.length >= 2) {
                long newAdminId;
                try {
                    newAdminId = Long.parseLong(parts[1]);
                } catch (NumberFormatException e) {
                    sendMessage(chatId, "❌ Неверный формат chat_id. Используйте: `/addadmin 123456789`");
                    return;
                }

                // Проверяем, не является ли пользователь уже админом
                if (isAdmin(newAdminId)) {
                    sendMessage(chatId, "❌ Пользователь " + newAdminId + " уже является администратором");
                    return;
                }

                // Получаем имя пользователя из базы (если есть)
                String newAdminName = userDatabase.getOrDefault(newAdminId, "Неизвестно");

                // Если имени нет в базе, используем chat_id как временное имя
                if ("Неизвестно".equals(newAdminName)) {
                    newAdminName = "User_" + newAdminId;
                }

                // Добавляем в список админов
                adminUsers.put(newAdminId, newAdminName);

                sendMessage(chatId, "✅ Новый администратор добавлен!\n" +
                        "• ID: `" + newAdminId + "`\n" +
                        "• Имя: " + newAdminName + "\n\n" +
                        "Теперь у него есть доступ к админ-панели.");

                System.out.println("✅ Добавлен новый администратор: " + newAdminId + " - " + newAdminName);

            } else {
                sendMessage(chatId, "❌ Неправильный формат команды\n\nИспользование: `/addadmin <chat_id>`\nПример: `/addadmin 123456789`");
            }

        } catch (Exception e) {
            sendMessage(chatId, "❌ Ошибка при добавлении администратора: " + e.getMessage());
            System.out.println("❌ Ошибка в handleAddAdmin: " + e.getMessage());
        }
    }

    private void handleCallbackQuery(CallbackQuery callbackQuery) {
        String callbackData = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();
        Integer messageId = callbackQuery.getMessage().getMessageId();
        String userName = callbackQuery.getFrom().getFirstName();

        try {
            // Обработка кнопок подтверждения брони
            if (callbackData.startsWith(CONFIRM_BOOKING)) {
                handleConfirmBooking(callbackData, chatId, messageId);
            }
            else if (callbackData.startsWith(REJECT_BOOKING)) {
                handleRejectBooking(callbackData, chatId, messageId);
            }
            // Обработка меню
            if (callbackData.startsWith("menu_")) {
                handleMenuCallback(callbackData, chatId);
            }
            // Обработка админ-панели
            else if (callbackData.startsWith("admin_")) {
                handleAdminCallback(callbackData, chatId, messageId, userName);
            }

            // Подтверждаем обработку callback
            execute(new AnswerCallbackQuery(callbackQuery.getId()));

        } catch (TelegramApiException e) {
            System.out.println("Ошибка обработки callback: " + e.getMessage());
        }
    }

    private void handleConfirmBooking(String callbackData, Long adminChatId, Integer messageId) {
        try {
            Long userChatId = Long.parseLong(callbackData.replace(CONFIRM_BOOKING, ""));
            String userName = userDatabase.getOrDefault(userChatId, "Пользователь");
            String bookingDetails = bookingData.get(userChatId);

            // Обновляем статус брони
            bookingStatus.put(userChatId, BOOKING_CONFIRMED);

            // Отправляем уведомление пользователю
            String userMessage = "🎉 **Ваше бронирование подтверждено!**\n\n" +
                    "✅ Администратор подтвердил вашу заявку.\n\n" +
                    "**Детали брони:**\n" + bookingDetails +
                    "\nЖдем вас в указанное время! 🍣";

            sendMessage(userChatId, userMessage);

            // Обновляем сообщение у админа
            String adminMessage = "✅ **Бронь подтверждена!**\n\n" +
                    "👤 Пользователь: " + userName + "\n" +
                    "🆔 Chat ID: `" + userChatId + "`\n\n" +
                    "**Детали брони:**\n" + bookingDetails +
                    "\n**Статус:** ✅ Подтверждено";

            editAdminBookingMessage(adminChatId, messageId, adminMessage);

            System.out.println("✅ Бронь подтверждена для пользователя: " + userChatId);

        } catch (Exception e) {
            System.out.println("❌ Ошибка подтверждения брони: " + e.getMessage());
        }
    }
    // Методы обработки подтверждения и отклонения
    private void handleRejectBooking(String callbackData, Long adminChatId, Integer messageId) {
        try {
            Long userChatId = Long.parseLong(callbackData.replace(REJECT_BOOKING, ""));
            String userName = userDatabase.getOrDefault(userChatId, "Пользователь");
            String bookingDetails = bookingData.get(userChatId);

            // Обновляем статус брони
            bookingStatus.put(userChatId, BOOKING_REJECTED);

            // Отправляем уведомление пользователю
            String userMessage = """
                    ❌ **Ваше бронирование отклонено**
                    
                    К сожалению, администратор отклонил вашу заявку.
                    
                    **Причина:** Возможно, на выбранное время нет свободных столиков.
                    
                    Попробуйте выбрать другое время или свяжитесь с нами по телефону.""";

            sendMessage(userChatId, userMessage);

            // Обновляем сообщение у админа
            String adminMessage = "❌ **Бронь отклонена!**\n\n" +
                    "👤 Пользователь: " + userName + "\n" +
                    "🆔 Chat ID: `" + userChatId + "`\n\n" +
                    "**Детали брони:**\n" + bookingDetails +
                    "\n**Статус:** ❌ Отклонено";

            editAdminBookingMessage(adminChatId, messageId, adminMessage);

            System.out.println("❌ Бронь отклонена для пользователя: " + userChatId);

        } catch (Exception e) {
            System.out.println("❌ Ошибка отклонения брони: " + e.getMessage());
        }
    }

    private void editAdminBookingMessage(Long adminChatId, Integer messageId, String newText) {
        try {
            EditMessageText editMessage = new EditMessageText();
            editMessage.setChatId(adminChatId.toString());
            editMessage.setMessageId(messageId);
            editMessage.setText(newText);
            editMessage.setParseMode("Markdown");

            execute(editMessage);
        } catch (TelegramApiException e) {
            System.out.println("❌ Ошибка редактирования сообщения админу: " + e.getMessage());
        }
    }

    private void handleAdminCallback(String callbackData, Long chatId, Integer messageId, String userName) {
        try {
            switch (callbackData) {
                case "admin_panel":
                    sendAdminPanel(chatId);
                    break;
                case "admin_stats":
                    sendAdminStats(chatId, messageId);
                    break;
                case "admin_broadcast":
                    System.out.println("🔍 DEBUG: Нажата кнопка рассылки, chatId: " + chatId);
                    startBroadcast(chatId);
                    break;
                case "admin_back":
                    sendWelcomeMessage(chatId, userName);
                    break;
            }
        } catch (Exception e) {
            System.out.println("❌ Ошибка админ-панели: " + e.getMessage());
        }
    }

    private void handleMenuCallback(String callbackData, Long chatId) {
        switch (callbackData) {
            case "menu_salads":
                sendCategoryInfo(chatId, "🥗 Салаты", "salads");
                break;
            case "menu_hot":
                sendCategoryInfo(chatId, "🍲 Горячее", "hot");
                break;
            case "menu_rolls":
                sendCategoryInfo(chatId, "🍣 Роллы", "rolls");
                break;
            case "menu_pizza":
                sendCategoryInfo(chatId, "🍕 Пицца", "pizza");
                break;
            case "menu_snacks":
                sendCategoryInfo(chatId, "🍢 Закуски", "snacks");
                break;
            case "menu_desserts":
                sendCategoryInfo(chatId, "🍰 Десерты", "desserts");
                break;
            case "menu_back":
                sendWelcomeMessage(chatId, "Пользователь");
                break;
        }
    }

    private void sendCategoryInfo(Long chatId, String categoryName, String categoryKey) {
        String link = menuLinks.getOrDefault(categoryKey, "https://your-restaurant.com");

        String text = "📋 **" + categoryName + "**\n\n" +
                "Перейдите по ссылке, чтобы увидеть полное меню:\n" +
                link + "\n\n" +
                "Или выберите другую категорию:";

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        message.setParseMode("Markdown");
        message.setReplyMarkup(createMenuKeyboard()); // Возвращаем к той же клавиатуре

        try {
            execute(message);
        } catch (TelegramApiException e) {
            System.out.println("Ошибка отправки категории: " + e.getMessage());
        }
    }


    private void handleCommand(String command, Long chatId, String userName) {
        switch (command) {
            case "/start":
                // При старте всегда показываем приветствие с запросом номера
                sendWelcomeMessage(chatId, userName);
                break;
            case "/menu":
                showMenu(chatId);
                break;
            case "/book":
                startBooking(chatId);
                break;
            case "/promo":
                showPromotions(chatId);
                break;
            case "/website":
                openWebsite(chatId);
                break;
            case "/help":
                sendHelp(chatId);
                break;
            case "/contacts":
                showContacts(chatId);
                break;
            case "/admin":
                if (isAdmin(chatId)) {
                    sendAdminPanel(chatId);
                } else {
                    sendMessage(chatId, "❌ У вас нет прав доступа к админ-панели");
                }
                break;
            case "/broadcast":
                if (isAdmin(chatId)) {
                    startBroadcast(chatId);
                } else {
                    sendMessage(chatId, "❌ У вас нет прав на рассылку");
                }
                break;
            case "/addadmin": // Добавляем обработку команды
                if (isAdmin(chatId)) {
                    handleAddAdmin(chatId, command);
                } else {
                    sendMessage(chatId, "❌ У вас нет прав для добавления администраторов");
                }
                break;
            case "/admins":
                if (isAdmin(chatId)) {
                    sendAdminList(chatId);
                } else {
                    sendMessage(chatId, "❌ У вас нет прав для просмотра списка администраторов");
                }
                break;
            case "/adminstatus":
                if (isAdmin(chatId)) {
                    String status = "👑 Ваш статус администратора: ";
                    if (firstAdminChatId.equals(chatId)) {
                        status += "Главный администратор (нельзя удалить)";
                    } else if (adminUsers.containsKey(chatId)) {
                        status += "Обычный администратор (можно удалить)";
                    }
                    sendMessage(chatId, status);
                } else {
                    sendMessage(chatId, "❌ Вы не являетесь администратором");
                }
                break;
            case "/removeadmin":
                if (isAdmin(chatId)) {
                    handleRemoveAdmin(chatId, command);
                } else {
                    sendMessage(chatId, "❌ У вас нет прав для удаления администраторов");
                }
                break;
            case "/myinfo":
                String info = "👤 *Ваша информация:*\n\n" +
                        "• ID: `" + chatId + "`\n" +
                        "• Имя: " + userName + "\n" +
                        "• Статус: " + (isAdmin(chatId) ? "👑 Администратор" : "👤 Пользователь") + "\n";

                if (isAdmin(chatId)) {
                    info += "• Уровень: " +
                            (firstAdminChatId.equals(chatId) ? "Главный админ" : "Администратор") + "\n";
                }

                sendMessage(chatId, info);
                break;
            case "/mystatus":
                String status = bookingStatus.getOrDefault(chatId, "none");
                String statusMessage = switch (status) {
                    case BOOKING_PENDING -> """
                            ⏳ **Ваша заявка на рассмотрении**
                            
                            Администратор проверяет ваше бронирование.
                            Ожидайте ответа в течение 15 минут.""";
                    case BOOKING_CONFIRMED -> {
                        String bookingDetails = bookingData.getOrDefault(chatId, "");
                        yield "✅ **Ваше бронирование подтверждено!**\n\n" +
                                bookingDetails +
                                "\nЖдем вас в указанное время! 🍣";
                    }
                    case BOOKING_REJECTED -> """
                            ❌ **Бронирование отклонено**
                            
                            К сожалению, ваша заявка была отклонена.
                            Попробуйте выбрать другое время или свяжитесь с нами.""";
                    default -> """
                            📋 **У вас нет активных бронирований**
                            
                            Используйте /book чтобы забронировать столик.""";
                };

                sendMessage(chatId, statusMessage);
                break;
            case "/block":
                if (isAdmin(chatId)) {
                    handleBlockCommand(chatId, command);
                } else {
                    sendMessage(chatId, "❌ Нет прав");
                }
                break;

            case "/unblock":
                if (isAdmin(chatId)) {
                    handleUnblockCommand(chatId, command);
                } else {
                    sendMessage(chatId, "❌ Нет прав");
                }
                break;

            case "/blocklist":
                if (isAdmin(chatId)) {
                    showBlockList(chatId);
                } else {
                    sendMessage(chatId, "❌ Нет прав");
                }
                break;

            default:
                sendMessage(chatId, "Неизвестная команда 😢 Используйте меню слева");
        }
    }

    private void handleButton(String buttonText, Long chatId, String userName) {
        switch (buttonText) {
            case "🍣 Меню":
                showMenu(chatId);
                break;
            case "📞 Контакты":
                showContacts(chatId);
                break;
            case "👤 О нас":
                showAbout(chatId);
                break;
            case "🌐 Наш сайт":
                openWebsite(chatId);
                break;
            case "🎁 Акции":
                showPromotions(chatId);
                break;
            case "📅 Забронировать":
                startBooking(chatId);
                break;
            case "🔙 Назад":
                sendWelcomeMessage(chatId, userName);
                break;
            default:
                sendMessage(chatId, "Не понимаю команду 😢 Используйте кнопки или меню слева");
        }
    }

    private void handleCancelAction(Long chatId, String userName) {
        String currentState = bookingState.get(chatId);
        String canceledAction;

        if (currentState != null) {
            // Определяем тип действия по состоянию
            if (currentState.startsWith("waiting_")) {
                canceledAction = switch (currentState) {
                    case "waiting_broadcast" -> "Рассылка";
                    case "waiting_date", "waiting_time", "waiting_guests", "waiting_name" -> "Бронирование";
                    default -> "Действие";
                };
            } else {
                canceledAction = "Действие";
            }

            // Очищаем состояния
            bookingState.remove(chatId);
            bookingData.remove(chatId);

            // Сообщение об отмене с указанием типа
            sendMessage(chatId, "❌ " + canceledAction + " отменено");
        } else {
            // Если состояния нет, просто сообщаем об отмене
            sendMessage(chatId, "❌ Действие отменено");
        }

        // Возвращаем в главное меню
        sendMainWelcomeMessage(chatId, userName);
    }

    private boolean isBookingLimitExceeded(Long chatId) {
        try {

            // Админы不受限制
            if (isAdmin(chatId)) {
                return false;
            }

            long currentTime = System.currentTimeMillis();
            List<Long> bookings = userBookingTimestamps.getOrDefault(chatId, new ArrayList<>());

            // Удаляем старые бронирования (старше 1 часа)
            bookings.removeIf(time -> currentTime - time > 3600000);

            if (bookings.size() >= MAX_BOOKINGS_PER_HOUR) {
                System.out.println("🚫 Лимит бронирований: превышен лимит от " + chatId);
                return true;
            }

            return false;

        } catch (Exception e) {
            System.out.println("❌ Ошибка проверки лимита брони: " + e.getMessage());
            return false;
        }
    }



    private boolean isSpam(Long chatId) {
        try {
            long currentTime = System.currentTimeMillis();

            // Проверка перманентной блокировки
            if (permanentlyBlocked.contains(chatId)) {
                System.out.println("🚫 Перманентно заблокирован: " + chatId);
                return true;
            }

            // Проверка временной блокировки
            Long blockEndTime = blockedUsers.get(chatId);
            if (blockEndTime != null && currentTime < blockEndTime) {
                System.out.println("🚫 Временно заблокирован: " + chatId);
                return true;
            }

            // Если блокировка закончилась - снимаем её
            if (blockEndTime != null && currentTime >= blockEndTime) {
                blockedUsers.remove(chatId);
                System.out.println("✅ Разблокирован: " + chatId);
            }

            // Админы不受限制
            if (isAdmin(chatId)) {
                return false;
            }

            // УБРАНА проверка слишком частых сообщений - она неправильно работала
            // так как запоминала время сообщений бота, а не пользователя

            // Проверка количества запросов в минуту (ТОЛЬКО для входящих)
            List<Long> requests = userRequestTimestamps.getOrDefault(chatId, new ArrayList<>());

            // Удаляем старые запросы (старше 1 минуты)
            requests.removeIf(time -> currentTime - time > 60000);

            if (requests.size() >= MAX_REQUESTS_PER_MINUTE) {
                System.out.println("🚫 Спам-защита: превышен лимит запросов от " + chatId);
                return true;
            }

            // ЗАПОМИНАЕМ ТОЛЬКО ВХОДЯЩИЕ ЗАПРОСЫ (добавляем текущее время)
            requests.add(currentTime);
            userRequestTimestamps.put(chatId, requests);

            return false;

        } catch (Exception e) {
            System.out.println("❌ Ошибка в антиспаме: " + e.getMessage());
            return false;
        }
    }

    private boolean isIncomingSpam(Long chatId) {
        try {
            long currentTime = System.currentTimeMillis();

            // Админы不受限制
            if (isAdmin(chatId)) {
                return false;
            }

            // Проверка перманентной блокировки
            if (permanentlyBlocked.contains(chatId)) {
                return true;
            }

            // Проверка временной блокировки
            Long blockEndTime = blockedUsers.get(chatId);
            if (blockEndTime != null && currentTime < blockEndTime) {
                return true;
            }

            // Проверка слишком частых входящих сообщений
            Long lastIncomingTime = lastIncomingMessageTime.get(chatId);
            if (lastIncomingTime != null && (currentTime - lastIncomingTime) < SPAM_TIMEOUT_MS) {
                System.out.println("🚫 Спам-защита: слишком частые сообщения от " + chatId);
                return true;
            }

            // Запоминаем время ВХОДЯЩЕГО сообщения пользователя
            lastIncomingMessageTime.put(chatId, currentTime);

            // Проверка количества запросов в минуту
            List<Long> requests = userRequestTimestamps.getOrDefault(chatId, new ArrayList<>());
            requests.removeIf(time -> currentTime - time > 60000);

            if (requests.size() >= MAX_REQUESTS_PER_MINUTE) {
                System.out.println("🚫 Спам-защита: превышен лимит запросов от " + chatId);
                return true;
            }

            requests.add(currentTime);
            userRequestTimestamps.put(chatId, requests);

            return false;

        } catch (Exception e) {
            System.out.println("❌ Ошибка в проверке входящего спама: " + e.getMessage());
            return false;
        }
    }

    private boolean isPermanentlyBlocked(Long chatId) {
        return permanentlyBlocked.contains(chatId);
    }

    private boolean isTemporarilyBlocked(Long chatId) {
        Long blockEndTime = blockedUsers.get(chatId);
        return blockEndTime != null && System.currentTimeMillis() < blockEndTime;
    }

    private void registerSpamAttempt(Long chatId) {
        try {
            int attempts = spamAttempts.getOrDefault(chatId, 0) + 1;
            spamAttempts.put(chatId, attempts);

            System.out.println("⚠️ Спам-попытка от " + chatId + ": " + attempts + "/" + MAX_SPAM_ATTEMPTS);

            // Если превышен лимит - блокируем
            if (attempts >= MAX_SPAM_ATTEMPTS) {
                blockUser(chatId);
            }

        } catch (Exception e) {
            System.out.println("❌ Ошибка регистрации спама: " + e.getMessage());
        }
    }

    private void blockUser(Long chatId) {
        try {
            int blockCount = temporaryBlocksCount.getOrDefault(chatId, 0) + 1;
            temporaryBlocksCount.put(chatId, blockCount);

            boolean permanent = false;

            // Если много временных блокировок - перманентная блокировка
            if (blockCount >= PERMANENT_BLOCK_AFTER) {
                permanentlyBlocked.add(chatId);
                blockedUsers.remove(chatId);
                spamAttempts.remove(chatId);

                System.out.println("🔒 ПЕРМАНЕНТНАЯ блокировка: " + chatId);
                sendMessage(chatId, "🚫 Ваш аккаунт заблокирован навсегда за спам.");



            } else {
                // Временная блокировка
                long blockEndTime = System.currentTimeMillis() + BLOCK_DURATION_MS;
                blockedUsers.put(chatId, blockEndTime);
                spamAttempts.put(chatId, 0); // Сбрасываем счетчик

                long minutesLeft = BLOCK_DURATION_MS / 60000;
                System.out.println("🔒 Временная блокировка: " + chatId + " на " + minutesLeft + " мин");

                sendMessage(chatId, "🚫 **Вы заблокированы на " + minutesLeft + " минут!**\n\n" +
                        "Причина: превышено количество спам-попыток.\n" +
                        "Блокировка №" + blockCount + " из " + PERMANENT_BLOCK_AFTER + "\n\n" +
                        "⚠️ После " + PERMANENT_BLOCK_AFTER + " блокировок аккаунт будет заблокирован навсегда.");


            }


            String userName = userDatabase.getOrDefault(chatId, "Неизвестно");
            notifyAdminsAboutBlock(chatId, userName, permanent);


        } catch (Exception e) {
            System.out.println("❌ Ошибка блокировки: " + e.getMessage());
        }
    }

    private void handleBlockCommand(Long adminChatId, String message) {
        try {
            String[] parts = message.split(" ");
            if (parts.length == 2) {
                long userId = Long.parseLong(parts[1]);
                blockUser(userId);
                sendMessage(adminChatId, "✅ Пользователь " + userId + " заблокирован");
            } else {
                sendMessage(adminChatId, "❌ Формат: /block <user_id>");
            }
        } catch (Exception e) {
            sendMessage(adminChatId, "❌ Ошибка: " + e.getMessage());
        }
    }

    private void handleUnblockCommand(Long adminChatId, String message) {
        try {
            String[] parts = message.split(" ");
            if (parts.length == 2) {
                long userId = Long.parseLong(parts[1]);
                unblockUser(userId);
                sendMessage(adminChatId, "✅ Пользователь " + userId + " разблокирован");
            } else {
                sendMessage(adminChatId, "❌ Формат: /unblock <user_id>");
            }
        } catch (Exception e) {
            sendMessage(adminChatId, "❌ Ошибка: " + e.getMessage());
        }
    }

    private void showBlockList(Long adminChatId) {
        StringBuilder blockList = new StringBuilder();
        blockList.append("🚫 **Заблокированные пользователи:**\n\n");

        // Временно заблокированные
        if (blockedUsers.isEmpty()) {
            blockList.append("• Временно заблокированных: нет\n");
        } else {
            blockList.append("• Временно заблокированные:\n");
            for (Map.Entry<Long, Long> entry : blockedUsers.entrySet()) {
                long timeLeft = (entry.getValue() - System.currentTimeMillis()) / 60000;
                blockList.append("  - ").append(entry.getKey()).append(" (осталось: ").append(timeLeft).append(" мин)\n");
            }
        }

        // Перманентно заблокированные
        if (permanentlyBlocked.isEmpty()) {
            blockList.append("• Перманентно заблокированных: нет\n");
        } else {
            blockList.append("• Перманентно заблокированные:\n");
            for (Long userId : permanentlyBlocked) {
                blockList.append("  - ").append(userId).append("\n");
            }
        }

        sendMessage(adminChatId, blockList.toString());
    }

    private void unblockUser(Long chatId) {
        blockedUsers.remove(chatId);
        spamAttempts.put(chatId, 0);
        System.out.println("✅ Вручную разблокирован: " + chatId);
    }




    private void notifyAdminsAboutBlock(Long userId, String userName, boolean permanent) {
        String message = permanent ?
                "🔒 **Перманентная блокировка!**\n\n" :
                "🚫 **Временная блокировка!**\n\n";

        message += "• Пользователь: " + userName + "\n" +
                "• ID: `" + userId + "`\n" +
                "• Причина: спам-атака\n" +
                "• Время: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));

        // Отправляем всем админам
        for (Long adminId : adminUsers.keySet()) {
            sendMessage(adminId, message);
        }

        sendMessage(firstAdminChatId, message);
    }


    // 📅 Функционал бронирования столика
    private void startBooking(Long chatId) {



        if (isBookingLimitExceeded(chatId)) {
            sendMessage(chatId, "❌ **Превышен лимит бронирований!**\n\n" +
                    "Вы можете создавать не более " + MAX_BOOKINGS_PER_HOUR + " бронирований в час.\n" +
                    "Пожалуйста, подождите или свяжитесь с администрацией по телефону.");
            return;
        }

        // Проверяем, есть ли номер
        if (!userPhones.containsKey(chatId) || "Не указан".equals(userPhones.get(chatId))) {
            requestPhoneForBooking(chatId);
            return;
        }

        bookingState.put(chatId, "waiting_date");
        bookingData.put(chatId, "");

        String text = """
                📅 **Бронирование столика**
                
                Пожалуйста, введите дату бронирования:
                • Формат: **ДД.ММ.ГГГГ** (например: 10.09.2025)
                • Год: **2025 или позднее**
                • Дата должна быть **не раньше сегодняшнего дня**
                """;

        sendMessageWithCancel(chatId, text);
    }

    private void requestPhoneForBooking(Long chatId) {
        String text = """
            📞 **Для бронирования нужен ваш номер телефона**
            
            Пожалуйста, поделитесь номером для связи:
            • Подтверждение брони
            • Экстренные уведомления
            • Связь с администрацией""";

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        message.setParseMode("Markdown");
        message.setReplyMarkup(createPhoneKeyboard()); // ← Выделенный метод!

        try {
            execute(message);
            bookingState.put(chatId, "waiting_phone_booking");
        } catch (TelegramApiException e) {
            System.out.println("Ошибка запроса номера: " + e.getMessage());
        }
    }

    // Выделенный метод создания клавиатуры
    private ReplyKeyboardMarkup createPhoneKeyboard() {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row = new KeyboardRow();
        KeyboardButton phoneButton = new KeyboardButton("📞 Поделиться номером");
        phoneButton.setRequestContact(true);
        row.add(phoneButton);

        KeyboardRow row2 = new KeyboardRow();
        row2.add("❌ Отменить");

        keyboard.add(row);
        keyboard.add(row2);

        keyboardMarkup.setKeyboard(keyboard);
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(true);

        return keyboardMarkup;
    }

    private void notifyAdminsAboutNewBooking(Long userChatId, String userName, String bookingDetails) {
        // Дополнительная проверка перед отправкой админам
        String phone = userPhones.getOrDefault(userChatId, "Не указан");

        String adminMessage = "📋 **Новая заявка на бронирование!**\n\n" +
                "👤 Пользователь: " + userName + "\n" +
                "📞 Телефон: " + phone + "\n" +
                "🆔 Chat ID: `" + userChatId + "`\n" +
                "⏰ Время: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")) + "\n\n" +
                "**Детали брони:**\n" + bookingDetails + "\n" +
                "**Статус:** ⏳ Ожидает подтверждения\n" +
                "**Проверка:** ✅ Данные прошли валидацию";

        // Отправляем всем админам
        for (Long adminId : adminUsers.keySet()) {
            sendBookingConfirmationMessage(adminId, userChatId, userName, adminMessage);
        }

        sendBookingConfirmationMessage(firstAdminChatId, userChatId, userName, adminMessage);
    }

    private void sendBookingConfirmationMessage(Long adminId, Long userChatId, String userName, String message) {
        InlineKeyboardMarkup keyboard = createBookingConfirmationKeyboard(userChatId);

        SendMessage adminMsg = new SendMessage();
        adminMsg.setChatId(adminId.toString());
        adminMsg.setText(message);
        adminMsg.setParseMode("Markdown");
        adminMsg.setReplyMarkup(keyboard);

        try {
            execute(adminMsg);
            System.out.println("✅ Уведомление отправлено админу: " + adminId);
        } catch (TelegramApiException e) {
            System.out.println("❌ Ошибка отправки уведомления админу " + adminId + ": " + e.getMessage());
        }
    }

    private void handleBookingResponse(Long chatId, String message, String userName) {
        String state = bookingState.get(chatId);
        String currentData = bookingData.get(chatId);

        switch (state) {
            case "waiting_date":
                // Валидация даты
                if (!isValidDate(message)) {
                    sendMessage(chatId, """
                            ❌ **Неверная дата!**
                            
                            • Формат: ДД.ММ.ГГГГ (например: 05.12.2025)
                            • Год должен быть 2025 или позднее
                            • Дата должна быть реальной и не в прошлом
                            
                            Попробуйте еще раз:""");
                    return;
                }
                bookingState.put(chatId, "waiting_time");
                bookingData.put(chatId, "Дата: " + message + "\n");
                sendMessageWithCancel(chatId, """
                        ⏰ **Выберите время:**
                        
                        Введите время бронирования:
                        • Формат: **ЧЧ:MM** (например: 19:30)
                        • Мы работаем: **11:00 - 23:00**
                        
                        Пример правильного времени: `19:30`""");
                break;

            case "waiting_time":
                // Валидация времени
                if (!isValidTime(message)) {
                    sendMessage(chatId, """
                            ❌ **Неверное время!**
                            
                            • Формат: ЧЧ:MM (например: 19:30)
                            • Мы работаем с 11:00 до 23:00
                            • Время должно быть в пределах рабочего дня
                            
                            Попробуйте еще раз:""");
                    return;
                }
                bookingState.put(chatId, "waiting_guests");
                bookingData.put(chatId, currentData + "Время: " + message + "\n");
                sendMessageWithCancel(chatId, "👥 **Количество гостей:**\n\nВведите количество человек:\n" +
                        "• Минимум: **1** человек\n" +
                        "• Максимум: **" + MAX_GUESTS + "** человек\n" +
                        "• Для компаний больше " + MAX_GUESTS + " человек звоните 📞\n\n" +
                        "Пример: `4`");
                break;

            case "waiting_guests":
                // Валидация количества гостей
                if (!isValidGuests(message)) {
                    sendMessage(chatId, "❌ **Неверное количество гостей!**\n\n" +
                            "• Введите число от 1 до " + MAX_GUESTS + "\n" +
                            "• Для компаний больше " + MAX_GUESTS + " человек звоните по телефону\n\n" +
                            "Попробуйте еще раз:");
                    return;
                }
                int guests = Integer.parseInt(message);
                bookingState.put(chatId, "waiting_name");
                bookingData.put(chatId, currentData + "Гости: " + guests + " человек\n");
                sendMessageWithCancel(chatId, """
                        📝 **Ваше имя:**
                        
                        Введите ваше имя для брони:
                        • Только буквы (можно пробелы и дефисы)
                        • Минимум 2 буквы
                        • Максимум 50 символов
                        
                        Пример: `Иван Иванов` или `Анна-Мария`""");
                break;

            case "waiting_name":
                // Валидация имени
                if (!isValidName(message)) {
                    sendMessage(chatId, """
                            ❌ **Неверное имя!**
                            
                            • Только буквы, пробелы и дефисы
                            • Минимум 2 буквы
                            • Максимум 50 символов
                            
                            Попробуйте еще раз:""");
                    return;
                }
                bookingState.remove(chatId);
                String phone = userPhones.getOrDefault(chatId, "Не указан");
                String bookingDetails = currentData +
                        "• Имя: " + message + "\n" +
                        "• Телефон: " + phone + "\n";
                bookingData.put(chatId, bookingDetails);

                bookingStatus.put(chatId, BOOKING_PENDING);

                String successText = "✅ **Заявка на бронирование отправлена!**\n\n" +
                        "Администратор проверит вашу заявку и подтвердит бронь.\n" +
                        "Ожидайте ответа в течение 15 минут.\n\n" +
                        "**Детали заявки:**\n" + bookingDetails;

                long currentTime = System.currentTimeMillis();
                List<Long> bookings = userBookingTimestamps.getOrDefault(chatId, new ArrayList<>());
                bookings.add(currentTime);
                userBookingTimestamps.put(chatId, bookings);

                sendMessage(chatId, successText);

                notifyAdminsAboutNewBooking(chatId, userName, bookingDetails);
                break;
        }
    }

    // ЗАМЕНИТЕ методы валидации на эти
    private boolean isValidDate(String date) {
        try {
            // Проверяем базовый формат
            if (!date.matches(DATE_PATTERN)) {
                return false;
            }

            // Парсим дату
            String[] parts = date.split("\\.");
            int day = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            int year = Integer.parseInt(parts[2]);

            // Проверяем год (2024 или позднее)
            if (year < 2024) {
                return false;
            }

            // Проверяем реальность даты
            if (month == 2) {
                // Февраль
                boolean isLeap = (year % 4 == 0) && (year % 100 != 0 || year % 400 == 0);
                if (day > (isLeap ? 29 : 28)) return false;
            } else if (month == 4 || month == 6 || month == 9 || month == 11) {
                // Месяцы с 30 днями
                if (day > 30) return false;
            } else {
                // Месяцы с 31 днем
                if (day > 31) return false;
            }

            // Проверяем, что дата не в прошлом
            LocalDate inputDate = LocalDate.of(year, month, day);
            LocalDate today = LocalDate.now();
            return !inputDate.isBefore(today);

        } catch (Exception e) {
            System.out.println("❌ Ошибка валидации даты: " + e.getMessage());
            return false;
        }
    }

    private boolean isValidTime(String time) {
        try {
            // Проверяем базовый формат
            if (!time.matches(TIME_PATTERN)) {
                return false;
            }

            // Парсим время
            String[] parts = time.split(":");
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);

            // Проверяем рабочие часы (11:00 - 23:00)
            if (hour < 11 || hour > 23) {
                return false;
            }

            // Если 23:00, минуты должны быть 00
            return hour != 23 || minute == 0;

        } catch (Exception e) {
            System.out.println("❌ Ошибка валидации времени: " + e.getMessage());
            return false;
        }
    }

    private boolean isValidGuests(String guests) {
        try {
            int count = Integer.parseInt(guests.trim());
            return count >= 1 && count <= MAX_GUESTS;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isValidName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }

        // Проверяем базовый формат
        if (!name.trim().matches(NAME_PATTERN)) {
            return false;
        }

        // Проверяем, что есть хотя бы 2 буквы
        String cleanName = name.replaceAll("[^a-zA-Zа-яА-ЯёЁ]", "");
        return cleanName.length() >= 2;
    }



    // 📢 Функционал рассылки
    private void startBroadcast(Long chatId) {

        bookingState.put(chatId, "waiting_broadcast");

        String text = "📢 *Создание рассылки*\n\n" +
                "Введите сообщение для рассылки всем " + userDatabase.size() + " пользователям:\n\n" +
                "💡 *Подсказка:* Используйте Markdown для форматирования:\n" +
                "• *жирный*\n" +
                "• _курсив_\n" +
                "• [ссылка](https://example.com)";

        sendMessageWithCancel(chatId, text);
    }


    private void editMessage(Long chatId, Integer messageId, String text, InlineKeyboardMarkup keyboard) {
        try {
            EditMessageText message = new EditMessageText();
            message.setChatId(chatId.toString());
            message.setMessageId(messageId);
            message.setText(text);
            message.setParseMode("Markdown");
            message.setReplyMarkup(keyboard);

            execute(message);
        } catch (TelegramApiException e) {
            System.out.println("Ошибка редактирования сообщения: " + e.getMessage());
        }
    }

    public void sendBroadcast(String message) {
        System.out.println("🔍 DEBUG: sendBroadcast вызван с сообщением: " + message);

        int success = 0;
        int failed = 0;
        int spamBlocked = 0;
        long startTime = System.currentTimeMillis();

        if (userDatabase.isEmpty()) {
            System.out.println("❌ Нет пользователей для рассылки");
            return;
        }

        System.out.println("📢 Начинаю рассылку для " + userDatabase.size() + " пользователей...");

        for (Long chatId : userDatabase.keySet()) {
            try {
                // Проверяем спам для каждого пользователя
                if (isSpam(chatId)) {
                    spamBlocked++;
                    continue;
                }

                SendMessage broadcastMessage = new SendMessage();
                broadcastMessage.setChatId(chatId.toString());
                broadcastMessage.setText("📢 *Важное уведомление от SushiBar!*\n\n" + message + "\n\n_Сообщение отправлено администратором_");
                broadcastMessage.setParseMode("Markdown");

                execute(broadcastMessage);
                success++;

                // Небольшая задержка чтобы не превысить лимиты Telegram
                Thread.sleep(50);

            } catch (Exception e) {
                failed++;
                System.out.println("❌ Ошибка рассылки для " + chatId + ": " + e.getMessage());
            }
        }

        long duration = (System.currentTimeMillis() - startTime) / 1000;
        System.out.println("✅ Рассылка завершена за " + duration + " сек: " +
                "Успешно - " + success + ", " +
                "Неудачно - " + failed + ", " +
                "Заблокировано спамом - " + spamBlocked);
    }

    // Метод для периодической очистки старых данных
    private void cleanupOldData() {
        long currentTime = System.currentTimeMillis();

        // Очищаем старые timestamp запросов (старше 5 минут)
        for (List<Long> requests : userRequestTimestamps.values()) {
            requests.removeIf(time -> currentTime - time > 300000);
        }

        // Очищаем старые timestamp бронирований (старше 24 часов)
        for (List<Long> bookings : userBookingTimestamps.values()) {
            bookings.removeIf(time -> currentTime - time > 86400000);
        }

        // Очищаем старые записи о последних сообщениях (старше 1 часа)
        lastMessageTime.entrySet().removeIf(entry -> currentTime - entry.getValue() > 3600000);
    }

    // 👑 Панель администратора
    private void sendAdminPanel(Long chatId) {
        String adminInfo = "👑 *Панель администратора*\n\n" +
                "• Ваш chat_id: `" + chatId + "`\n" +
                "• Всего пользователей: " + userDatabase.size() + "\n" +
                "• Статус: ✅ Активен\n\n" +
                "Выберите действие:";

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(adminInfo);
        message.setParseMode("Markdown");
        message.setReplyMarkup(createAdminKeyboard());

        try {
            execute(message);
        } catch (TelegramApiException e) {
            System.out.println("Ошибка отправки админ-панели: " + e.getMessage());
            // Отправляем без форматирования в случае ошибки
            sendMessageWithoutFormatting(chatId, adminInfo, createAdminKeyboard());
        }
    }






    private void sendAdminStats(Long chatId, Integer messageId) {
        String stats = "📊 *Статистика бота*\n\n" +
                "• 👥 Всего пользователей: " + userDatabase.size() + "\n" +
                "• 🏢 Администраторов: " + (adminUsers.size() + 1) + "\n" +
                "• ⚡ Статус: ✅ Онлайн\n\n" +
                "Последнее обновление: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));

        if (messageId != null) {
            editMessage(chatId, messageId, stats, createAdminKeyboard());
        } else {
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText(stats);
            message.setParseMode("Markdown");
            message.setReplyMarkup(createAdminKeyboard());

            try {
                execute(message);
            } catch (TelegramApiException e) {
                System.out.println("Ошибка отправки статистики: " + e.getMessage());
            }
        }
    }

    private void sendAdminList(Long chatId) {
        StringBuilder adminList = new StringBuilder();
        adminList.append("👑 *Список администраторов:*\n\n");

        // Главный администратор
        String firstName = firstAdminName != null ? firstAdminName : "Неизвестно";
        adminList.append("• 👑 *Главный админ :*\n")
                .append("   ID: `").append(firstAdminChatId).append("`\n")
                .append("   Имя: ").append(firstName).append("\n\n");

        // Обычные администраторы
        if (adminUsers.isEmpty()) {
            adminList.append("• 📭 Дополнительных администраторов нет\n");
        } else {
            adminList.append("• 👥 *Дополнительные админы :*\n");
            int count = 1;
            for (Map.Entry<Long, String> admin : adminUsers.entrySet()) {
                // Пропускаем первого админа, если он есть в обоих списках
                if (firstAdminChatId.equals(admin.getKey())) {
                    continue;
                }
                adminList.append("  ").append(count).append(". ID: `").append(admin.getKey())
                        .append("`\n     Имя: ").append(admin.getValue()).append("\n");
                count++;
            }
        }

        adminList.append("\n💡 *Команды:*\n`/addadmin <id>` - добавить админа\n`/removeadmin <id>` - удалить админа");

        sendMessage(chatId, adminList.toString());
    }

    private void handleRemoveAdmin(Long chatId, String message) {
        try {
            String[] parts = message.split(" ");
            if (parts.length == 2) {
                long adminIdToRemove = Long.parseLong(parts[1]);

                // Нельзя удалить самого себя
                if (adminIdToRemove == chatId) {
                    sendMessage(chatId, "❌ Нельзя удалить самого себя");
                    return;
                }

                // Нельзя удалить первого админа
                if (firstAdminChatId.equals(adminIdToRemove)) {
                    sendMessage(chatId, "❌ Нельзя удалить главного администратора");
                    return;
                }

                if (adminUsers.containsKey(adminIdToRemove)) {
                    String removedAdminName = adminUsers.get(adminIdToRemove);
                    adminUsers.remove(adminIdToRemove);
                    sendMessage(chatId, "✅ Администратор удален!\n" +
                            "• ID: `" + adminIdToRemove + "`\n" +
                            "• Имя: " + removedAdminName);
                    System.out.println("❌ Удален администратор: " + adminIdToRemove + " - " + removedAdminName);
                } else {
                    sendMessage(chatId, "❌ Пользователь `" + adminIdToRemove + "` не найден в списке администраторов");
                }

            } else {
                sendMessage(chatId, "❌ Неправильный формат команды\n\nИспользование: `/removeadmin <chat_id>`\nПример: `/removeadmin 123456789`");
            }

        } catch (NumberFormatException e) {
            sendMessage(chatId, "❌ Неверный формат chat_id");
        } catch (Exception e) {
            sendMessage(chatId, "❌ Ошибка при удалении администратора: " + e.getMessage());
        }
    }


    private InlineKeyboardMarkup createAdminKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Первый ряд
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(createInlineButton("📊 Статистика", "admin_stats"));
        row1.add(createInlineButton("📢 Рассылка", "admin_broadcast"));

        // Второй ряд
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        row2.add(createInlineButton("🔄 Обновить", "admin_panel"));
        row2.add(createInlineButton("🔙 Назад", "admin_back"));

        rows.add(row1);
        rows.add(row2);

        markup.setKeyboard(rows);
        return markup;
    }

    private InlineKeyboardButton createInlineButton(String text, String callbackData) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(callbackData);
        return button;
    }

    private InlineKeyboardMarkup createBookingConfirmationKeyboard(Long userChatId) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Первый ряд - кнопки подтверждения/отклонения
        List<InlineKeyboardButton> row1 = new ArrayList<>();

        InlineKeyboardButton confirmBtn = new InlineKeyboardButton();
        confirmBtn.setText("✅ Подтвердить");
        confirmBtn.setCallbackData(CONFIRM_BOOKING + userChatId);

        InlineKeyboardButton rejectBtn = new InlineKeyboardButton();
        rejectBtn.setText("❌ Отклонить");
        rejectBtn.setCallbackData(REJECT_BOOKING + userChatId);

        row1.add(confirmBtn);
        row1.add(rejectBtn);

        // Второй ряд - кнопка просмотра профиля
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton profileBtn = new InlineKeyboardButton();
        profileBtn.setText("👤 Профиль пользователя");
        profileBtn.setUrl("tg://user?id=" + userChatId);
        row2.add(profileBtn);

        rows.add(row1);
        rows.add(row2);

        markup.setKeyboard(rows);
        return markup;
    }

    private void sendMessageWithoutFormatting(Long chatId, String text, Object keyboard) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text.replace("*", "").replace("`", ""));

        if (keyboard instanceof InlineKeyboardMarkup) {
            message.setReplyMarkup((InlineKeyboardMarkup) keyboard);
        } else if (keyboard instanceof ReplyKeyboardMarkup) {
            message.setReplyMarkup((ReplyKeyboardMarkup) keyboard);
        }

        try {
            execute(message);
        } catch (TelegramApiException e) {
            System.out.println("Ошибка отправки сообщения без форматирования: " + e.getMessage());
        }
    }





    // 🎨 Главное меню
    private void sendWelcomeMessage(Long chatId, String userName) {
        // Если номер уже есть - показываем главное меню
        if (userPhones.containsKey(chatId)) {
            sendMainWelcomeMessage(chatId, userName);
            return;
        }

        // Если номера нет - запрашиваем его
        String text = """
            👋 **Добро пожаловать в SushiBar!** 🍣
            
            Для удобства бронирования и связи, нам нужен ваш номер телефона.
            
            📋 **Это безопасно:**
            • Номер используется только для подтверждения брони
            • Мы не передаем данные третьим лицам
            • Можно пропустить и добавить позже
            
            Нажмите кнопку ниже чтобы поделиться номером 👇""";

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        message.setParseMode("Markdown");
        message.setReplyMarkup(createWelcomeKeyboard()); // ← Выделенный метод!

        try {
            execute(message);
            bookingState.put(chatId, WAITING_PHONE_START);
        } catch (TelegramApiException e) {
            System.out.println("Ошибка запроса номера: " + e.getMessage());
        }
    }

    // Выделенный метод создания клавиатуры для приветствия
    private ReplyKeyboardMarkup createWelcomeKeyboard() {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        KeyboardButton phoneButton = new KeyboardButton("📞 Поделиться номером");
        phoneButton.setRequestContact(true);
        row1.add(phoneButton);

        KeyboardRow row2 = new KeyboardRow();
        row2.add("🚫 Пропустить");

        keyboard.add(row1);
        keyboard.add(row2);

        keyboardMarkup.setKeyboard(keyboard);
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(true);

        return keyboardMarkup;
    }

    private void handleContactReceived(Message message) {
        Long chatId = message.getChatId();
        Contact contact = message.getContact();
        String userName = message.getChat().getFirstName();

        if (contact != null && contact.getPhoneNumber() != null) {
            String phoneNumber = formatPhoneNumber(contact.getPhoneNumber());
            userPhones.put(chatId, phoneNumber);

            String state = bookingState.get(chatId);
            bookingState.remove(chatId); // Сбрасываем состояние

            if (WAITING_PHONE_START.equals(state)) {
                // Если номер запрашивали при старте
                sendMessage(chatId, "✅ Отлично! Номер сохранен: " + phoneNumber);
                sendMainWelcomeMessage(chatId, userName); // ТОЛЬКО при старте
            } else {
                // Если номер отправили во время бронирования
                sendMessage(chatId, "✅ Номер сохранен! Продолжаем бронирование...");
                // НЕ вызываем приветствие, продолжаем бронирование
                startBooking(chatId); // Возвращаем к бронированию
            }

            System.out.println("✅ Номер сохранен для " + userName + ": " + phoneNumber);
        }
    }

    private String formatPhoneNumber(String phoneNumber) {
        if (phoneNumber == null) return "Не указан";

        // Убираем все нецифровые символы
        String cleaned = phoneNumber.replaceAll("[^0-9]", "");

        // Форматируем в российский формат
        String s = "+7 " + cleaned.substring(1, 4) + " " + cleaned.substring(4, 7) + " " + cleaned.substring(7, 9) + " " + cleaned.substring(9);
        if (cleaned.startsWith("7") && cleaned.length() == 11) {
            return s;
        } else if (cleaned.startsWith("8") && cleaned.length() == 11) {
            return s;
        } else if (cleaned.length() == 10) {
            return "+7 " + cleaned.substring(0, 3) + " " + cleaned.substring(3, 6) + " " + cleaned.substring(6, 8) + " " + cleaned.substring(8);
        }

        return phoneNumber; // Возвращаем как есть
    }

    private void sendMainWelcomeMessage(Long chatId, String userName) {
        String phone = userPhones.getOrDefault(chatId, "Не указан");
        String phoneInfo = "\n📞 Ваш номер: " + phone;

        String text = "Привет, " + userName + "! 👋\n" +
                "Добро пожаловать в SushiBar! 🍣" +
                phoneInfo + "\n\n" +
                "Выберите действие:";

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        message.setReplyMarkup(createMainMenuKeyboard());

        try {
            execute(message);
        } catch (TelegramApiException e) {
            System.out.println("Ошибка отправки: " + e.getMessage());
        }
    }

    private ReplyKeyboardMarkup createMainMenuKeyboard() {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboard = new ArrayList<>();

        // Первый ряд
        KeyboardRow row1 = new KeyboardRow();
        row1.add("🍣 Меню");

        // Второй ряд
        KeyboardRow row2 = new KeyboardRow();
        row2.add("📅 Забронировать");
        row2.add("🎁 Акции");

        // Третий ряд
        KeyboardRow row3 = new KeyboardRow();
        row3.add("🌐 Наш сайт");
        row3.add("📞 Контакты");

        keyboard.add(row1);
        keyboard.add(row2);
        keyboard.add(row3);

        keyboardMarkup.setKeyboard(keyboard);
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);

        return keyboardMarkup;
    }

    private InlineKeyboardMarkup createMenuKeyboard() {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Первый ряд
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(createButton("🥗 Салаты", "menu_salads"));
        row1.add(createButton("🍲 Горячее", "menu_hot"));

        // Второй ряд
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        row2.add(createButton("🍣 Роллы", "menu_rolls"));
        row2.add(createButton("🍕 Пицца", "menu_pizza"));

        // Третий ряд
        List<InlineKeyboardButton> row3 = new ArrayList<>();
        row3.add(createButton("🍢 Закуски", "menu_snacks"));
        row3.add(createButton("🍰 Десерты", "menu_desserts"));

        // Четвертый ряд - кнопка назад
        List<InlineKeyboardButton> row4 = new ArrayList<>();
        row4.add(createButton("🔙 Назад", "menu_back"));

        rows.add(row1);
        rows.add(row2);
        rows.add(row3);
        rows.add(row4);

        markup.setKeyboard(rows);
        return markup;
    }

    private InlineKeyboardButton createButton(String text, String callbackData) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(callbackData);
        return button;
    }


    private void showMenu(Long chatId) {
        String text = """
                🍣 **Выберите категорию меню:**
                
                • 🥗 Салаты - свежие и вкусные
                • 🍲 Горячее - согревающие блюда
                • 🍣 Роллы - наши хиты
                **• 🍕 Пицца - итальянская классика
                • 🍢 Закуски - легкие перекусы
                • 🍰 Десерты - сладкое завершение""";

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        message.setParseMode("Markdown");
        message.setReplyMarkup(createMenuKeyboard());

        try {
            execute(message);
        } catch (TelegramApiException e) {
            System.out.println("Ошибка отправки меню: " + e.getMessage());
        }
    }

    private void showContacts(Long chatId) {
        String text = """
                📞 **Наши контакты:**
                
                📍 Адрес: ул. Доковская, 1а
                📞 Телефон: +7 (902) 179-21-03
                ⏰ Время работы:
                 Пн-Чт: 11:00 - 23:00
                 Пт-Сб: 11:00 - 2:00
                 Воскресенье: 11:00 - 23:00
                🚗 Доставка: 30-45 минут""";

        sendMessageWithBackButton(chatId, text);
    }

    private void showAbout(Long chatId) {
        String text = """
                👤 **О нас:**
                
                Мы готовим самые свежие и вкусные суши и роллы!
                Используем только качественные продукты.
                Бесплатная доставка от 1000 руб.!""";

        sendMessageWithBackButton(chatId, text);
    }

    // 🎁 Акции и промо
    private void showPromotions(Long chatId) {
        String text = """
                🧧Наши скидки и акции🎁
                
                Акция «Счастливые часы» - в будни с 14:00-17:00 скидка 10% от суммы чека✅
                
                Скидка - 7% от суммы чека при оплате наличными✅
                
                Скидка -3% от суммы чека при заказе через бот-помощника✅
                
                В ваш день рождения чизкейк в подарок🎁 (действительна при предъявлении подтверждающих документов)
                
                ‼️Внимание, акции действительны до 01.11.2025‼️
                ✅Скидки суммируются между собой✅
                """;

        sendMessageWithBackButton(chatId, text);
    }

    // 🌐 Открытие сайта
    private void openWebsite(Long chatId) {
        String text = """
                🌐 **Наш сайт:**
                
                Посетите наш сайт для полного меню и онлайн-заказов:
                https://sushi-e.ru
                
                📍 Также доступно в приложении!""";

        sendMessageWithBackButton(chatId, text);
    }

    private void sendHelp(Long chatId) {
        String text = """
                ℹ️ **Помощь по боту:**
                
                • Используйте **боковое меню** слева для быстрых команд
                • Или нажимайте на **кнопки** внизу
                
                **Доступные команды:**
                /start - начать работу
                /menu - показать меню
                /book - забронировать столик
                /mystatus - статус моего бронирования
                /promo - акции и скидки
                /website - наш сайт
                /contacts - контакты
                /help - эта справка""";

        sendMessage(chatId, text);
    }

    // 🎯 Вспомогательные методы
    private void sendMessageWithCancel(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        message.setParseMode("Markdown");

        // Создаем клавиатуру только с кнопкой отмены
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row = new KeyboardRow();
        row.add("❌ Отменить"); // Используем единый вариант

        keyboard.add(row);
        keyboardMarkup.setKeyboard(keyboard);
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(true);

        message.setReplyMarkup(keyboardMarkup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            System.out.println("Ошибка: " + e.getMessage());
        }
    }

    private void sendMessageWithBackButton(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        message.setParseMode("Markdown");

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row = new KeyboardRow();
        row.add("🔙 Назад");

        keyboard.add(row);
        keyboardMarkup.setKeyboard(keyboard);
        keyboardMarkup.setResizeKeyboard(true);

        message.setReplyMarkup(keyboardMarkup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            System.out.println("Ошибка: " + e.getMessage());
        }
    }

    private void sendMessage(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        message.setParseMode("Markdown");

        try {
            execute(message);
        } catch (TelegramApiException e) {
            System.out.println("Ошибка отправки: " + e.getMessage());
        }
    }
}