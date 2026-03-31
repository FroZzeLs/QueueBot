package by.frozzel.bot;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.User;

import java.util.*;

public class QueueNotificationBot extends TelegramLongPollingBot {
    private static final Logger log = LoggerFactory.getLogger(QueueNotificationBot.class);

    private final String botUsername;
    private final String internalSecret;
    private final String backendBaseUrl;
    private final RestTemplate restTemplate;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, Set<Long>> notifiedUsers = new HashMap<>(); // key: subjectId_queueKind_subgroup, value: set of chatIds notified
    private final Map<Long, Long> lastNotificationTime = new HashMap<>(); // chatId -> last notification time

    public QueueNotificationBot(String botUsername, String token, String internalSecret, String backendBaseUrl) {
        super(token);
        this.botUsername = botUsername;
        this.internalSecret = internalSecret;
        this.backendBaseUrl = backendBaseUrl;
        this.restTemplate = new RestTemplate();
    }

    public void startMonitoring() {
        new Thread(() -> {
            while (true) {
                try {
                    checkAllQueues();
                    Thread.sleep(30000); // Check every 30 seconds
                } catch (Exception e) {
                    log.error("Monitoring error", e);
                    try {
                        Thread.sleep(30000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }, "QueueMonitor").start();
    }

    private void checkAllQueues() {
        try {
            // Get all subjects
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (internalSecret != null && !internalSecret.isBlank()) {
                headers.add("X-TG-BOT-SECRET", internalSecret);
            }
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> resp = restTemplate.exchange(
                    backendBaseUrl + "/api/subjects",
                    HttpMethod.GET,
                    entity,
                    Map.class
            );

            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                List<Map<String, Object>> subjects = (List<Map<String, Object>>) resp.getBody().get("subjects");
                for (Map<String, Object> subject : subjects) {
                    long subjectId = ((Number) subject.get("id")).longValue();
                    String subjectName = (String) subject.get("name");
                    String deliveryType = (String) subject.get("deliveryType");

                    // Check COMMON queue
                    checkQueueForNotifications(subjectId, subjectName, deliveryType, "COMMON", null);

                    // Check SUBGROUP queues (1 and 2)
                    if ("INDIVIDUAL".equals(deliveryType) || "BRIGADE".equals(deliveryType)) {
                        checkQueueForNotifications(subjectId, subjectName, deliveryType, "SUBGROUP", 1);
                        checkQueueForNotifications(subjectId, subjectName, deliveryType, "SUBGROUP", 2);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to check queues", e);
        }
    }

    private void checkQueueForNotifications(long subjectId, String subjectName, String deliveryType, String queueKind, Integer subgroupNum) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("subjectId", subjectId);
            body.put("queueKind", queueKind);
            if (subgroupNum != null) body.put("subgroupNum", subgroupNum);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (internalSecret != null && !internalSecret.isBlank()) {
                headers.add("X-TG-BOT-SECRET", internalSecret);
            }
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> resp = restTemplate.exchange(
                    backendBaseUrl + "/api/tg/queue/monitor",
                    HttpMethod.POST,
                    entity,
                    Map.class
            );

            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                List<Map<String, Object>> users = (List<Map<String, Object>>) resp.getBody().get("users");
                String queueLabel = "COMMON".equals(queueKind) ? "Общая очередь" : "Подгруппа " + subgroupNum;
                String deliveryLabel = "INDIVIDUAL".equals(deliveryType) ? "Индивидуально" : "Бригады";

                for (Map<String, Object> user : users) {
                    int position = ((Number) user.get("position")).intValue();
                    long chatId = ((Number) user.get("chatId")).longValue();

                    // Only notify if position is 1 (first in queue)
                    if (position == 1) {
                        String key = subjectId + "_" + queueKind + "_" + (subgroupNum != null ? subgroupNum : "null");
                        Set<Long> notified = notifiedUsers.computeIfAbsent(key, k -> new HashSet<>());

                        // Throttle: don't notify more than once every 2 minutes per chat
                        Long lastTime = lastNotificationTime.get(chatId);
                        long now = System.currentTimeMillis();
                        if (lastTime != null && now - lastTime < 120000) {
                            continue;
                        }

                        if (!notified.contains(chatId)) {
                            // Send notification
                            String myPosition = "1";
                            sendFirstInQueueNotification(chatId, subjectId, subjectName, queueLabel, deliveryLabel, subgroupNum, myPosition);
                            notified.add(chatId);
                            lastNotificationTime.put(chatId, now);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to check queue for subject " + subjectId, e);
        }
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update == null) return;

            if (update.hasMessage() && update.getMessage().hasText()) {
                handleMessage(update);
            } else if (update.hasCallbackQuery()) {
                handleCallback(update.getCallbackQuery());
            }
        } catch (Exception e) {
            log.error("Bot update failed", e);
        }
    }

    private void handleMessage(Update update) throws TelegramApiException {
        Message msg = update.getMessage();
        String text = msg.getText();
        Long chatId = msg.getChatId();
        User from = msg.getFrom();
        String userName = from != null ? from.getUserName() : null;
        if (text == null) return;

        if ("/start".equalsIgnoreCase(text.trim())) {
            if (userName == null || userName.isBlank()) {
                sendText(chatId, "Пожалуйста, укажите публичный @username в Telegram, чтобы сайт мог вас найти.");
                return;
            }

            // Register chatId into backend
            Map<String, Object> body = Map.of(
                    "telegramTag", userName,
                    "chatId", chatId
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (internalSecret != null && !internalSecret.isBlank()) {
                headers.add("X-TG-BOT-SECRET", internalSecret);
            }

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> resp = restTemplate.exchange(
                    backendBaseUrl + "/api/tg/register-chat",
                    HttpMethod.POST,
                    entity,
                    Map.class
            );

            if (!resp.getStatusCode().is2xxSuccessful()) {
                sendText(chatId, "Вас нет в списке. Обратитесь к администратору для регистрации вашего тега.");
                return;
            }

            sendText(chatId, "Готово! Теперь вы можете использовать бота для управления очередями.\n\n" +
                    "Команды:\n" +
                    "/auth - Получить код для входа на сайт\n" +
                    "/subjects - Список предметов\n" +
                    "/join <ID_предмета> [тип] [подгр] - Вступить в очередь\n" +
                    "/leave <ID_предмета> [тип] [подгр] - Покинуть очередь\n" +
                    "/status <ID_предмета> [тип] [подгр] - Проверить позицию\n" +
                    "/resetpassword <новый_пароль> - Сбросить пароль\n" +
                    "/help - Помощь");
        }

        else if ("/resetpassword".equalsIgnoreCase(text.trim().split(" ")[0])) {
            // Reset password: /resetpassword <newPassword>
            String[] parts = text.split(" ");
            if (parts.length < 2) {
                sendText(chatId, "Использование: /resetpassword <новый_пароль>\nПример: /resetpassword MyNewPass123");
                return;
            }
            String newPassword = parts[1];
            if (newPassword.length() < 6) {
                sendText(chatId, "Пароль должен быть не менее 6 символов.");
                return;
            }

            Map<String, Object> body = Map.of(
                    "chatId", chatId,
                    "newPassword", newPassword
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (internalSecret != null && !internalSecret.isBlank()) {
                headers.add("X-TG-BOT-SECRET", internalSecret);
            }
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            try {
                ResponseEntity<Map> resp = restTemplate.exchange(
                        backendBaseUrl + "/api/tg-auth/reset-password",
                        HttpMethod.POST,
                        entity,
                        Map.class
                );

                if (resp.getStatusCode().is2xxSuccessful()) {
                    sendText(chatId, "✅ Пароль успешно сброшен. Теперь вы можете войти на сайт с новым паролем.");
                } else {
                    String error = resp.getBody() != null ? (String) resp.getBody().get("error") : "UNKNOWN";
                    sendText(chatId, "❌ Ошибка сброса пароля: " + error);
                }
            } catch (Exception e) {
                sendText(chatId, "Ошибка: " + e.getMessage());
            }
        }

        else if ("/auth".equalsIgnoreCase(text.trim())) {
            // Request auth token
            Map<String, Object> body = Map.of("chatId", chatId);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (internalSecret != null && !internalSecret.isBlank()) {
                headers.add("X-TG-BOT-SECRET", internalSecret);
            }
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            try {
                ResponseEntity<Map> resp = restTemplate.exchange(
                        backendBaseUrl + "/api/tg-auth/request-token",
                        HttpMethod.POST,
                        entity,
                        Map.class
                );

                if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                    String token = (String) resp.getBody().get("token");
                    String expiresAt = (String) resp.getBody().get("expiresAt");
                    sendText(chatId, "Ваш код для входа на сайт: `" + token + "`\n" +
                            "Код действителен до: " + expiresAt + "\n\n" +
                            "Используйте этот код на странице входа сайта.");
                } else {
                    sendText(chatId, "Ошибка получения кода. Убедитесь, что вы зарегистрированы.");
                }
            } catch (Exception e) {
                sendText(chatId, "Ошибка: " + e.getMessage());
            }
        }

        else if ("/subjects".equalsIgnoreCase(text.trim())) {
            // List all subjects
            try {
                HttpHeaders headers = new HttpHeaders();
                if (internalSecret != null && !internalSecret.isBlank()) {
                    headers.add("X-TG-BOT-SECRET", internalSecret);
                }
                HttpEntity<Void> entity = new HttpEntity<>(headers);

                ResponseEntity<Map> resp = restTemplate.exchange(
                        backendBaseUrl + "/api/subjects",
                        HttpMethod.GET,
                        entity,
                        Map.class
                );

                if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                    List<Map<String, Object>> subjects = (List<Map<String, Object>>) resp.getBody().get("subjects");
                    StringBuilder sb = new StringBuilder("📚 Предметы:\n\n");
                    for (Map<String, Object> subj : subjects) {
                        sb.append("• ").append(subj.get("name")).append(" (ID: ").append(subj.get("id")).append(")\n");
                    }
                    sendText(chatId, sb.toString());
                } else {
                    sendText(chatId, "Ошибка получения списка предметов.");
                }
            } catch (Exception e) {
                sendText(chatId, "Ошибка: " + e.getMessage());
            }
        }

        else if (text.toLowerCase().startsWith("/join")) {
            // Join queue: /join <subjectId> [queueKind] [subgroup]
            String[] parts = text.split(" ");
            if (parts.length < 2) {
                sendText(chatId, "Использование: /join <ID_предмета> [common|subgroup] [подгруппа]\nПример: /join 1 common");
                return;
            }

            long subjectId;
            try {
                subjectId = Long.parseLong(parts[1]);
            } catch (NumberFormatException e) {
                sendText(chatId, "Неверный ID предмета.");
                return;
            }

            String queueKind = "common";
            Integer subgroupNum = null;
            if (parts.length >= 3) {
                queueKind = parts[2].toLowerCase();
                if ("subgroup".equals(queueKind) && parts.length >= 4) {
                    try {
                        subgroupNum = Integer.parseInt(parts[3]);
                    } catch (NumberFormatException e) {
                        sendText(chatId, "Неверный номер подгруппы.");
                        return;
                    }
                }
            }

            Map<String, Object> body = new HashMap<>();
            body.put("chatId", chatId);
            body.put("subjectId", subjectId);
            body.put("queueKind", queueKind);
            if (subgroupNum != null) body.put("subgroupNum", subgroupNum);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (internalSecret != null && !internalSecret.isBlank()) {
                headers.add("X-TG-BOT-SECRET", internalSecret);
            }
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            try {
                ResponseEntity<Map> resp = restTemplate.exchange(
                        backendBaseUrl + "/api/tg/queue/join",
                        HttpMethod.POST,
                        entity,
                        Map.class
                );

                if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                    sendText(chatId, "✅ " + resp.getBody().getOrDefault("message", "Вы вступили в очередь."));
                } else {
                    String error = resp.getBody() != null ? (String) resp.getBody().get("error") : "UNKNOWN";
                    sendText(chatId, "❌ Ошибка: " + error);
                }
            } catch (Exception e) {
                sendText(chatId, "Ошибка: " + e.getMessage());
            }
        }

        else if (text.toLowerCase().startsWith("/leave")) {
            // Leave queue: /leave <subjectId> [queueKind] [subgroup]
            String[] parts = text.split(" ");
            if (parts.length < 2) {
                sendText(chatId, "Использование: /leave <ID_предмета> [common|subgroup] [подгруппа]\nПример: /leave 1 common");
                return;
            }

            long subjectId;
            try {
                subjectId = Long.parseLong(parts[1]);
            } catch (NumberFormatException e) {
                sendText(chatId, "Неверный ID предмета.");
                return;
            }

            String queueKind = "common";
            Integer subgroupNum = null;
            if (parts.length >= 3) {
                queueKind = parts[2].toLowerCase();
                if ("subgroup".equals(queueKind) && parts.length >= 4) {
                    try {
                        subgroupNum = Integer.parseInt(parts[3]);
                    } catch (NumberFormatException e) {
                        sendText(chatId, "Неверный номер подгруппы.");
                        return;
                    }
                }
            }

            Map<String, Object> body = new HashMap<>();
            body.put("chatId", chatId);
            body.put("subjectId", subjectId);
            body.put("queueKind", queueKind);
            if (subgroupNum != null) body.put("subgroupNum", subgroupNum);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (internalSecret != null && !internalSecret.isBlank()) {
                headers.add("X-TG-BOT-SECRET", internalSecret);
            }
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            try {
                ResponseEntity<Map> resp = restTemplate.exchange(
                        backendBaseUrl + "/api/tg/queue/leave",
                        HttpMethod.POST,
                        entity,
                        Map.class
                );

                if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                    sendText(chatId, "✅ " + resp.getBody().getOrDefault("message", "Вы покинули очередь."));
                } else {
                    String error = resp.getBody() != null ? (String) resp.getBody().get("error") : "UNKNOWN";
                    sendText(chatId, "❌ Ошибка: " + error);
                }
            } catch (Exception e) {
                sendText(chatId, "Ошибка: " + e.getMessage());
            }
        }

        else if (text.toLowerCase().startsWith("/status")) {
            // Check status: /status <subjectId> [queueKind] [subgroup]
            String[] parts = text.split(" ");
            if (parts.length < 2) {
                sendText(chatId, "Использование: /status <ID_предмета> [common|subgroup] [подгруппа]\nПример: /status 1 common");
                return;
            }

            long subjectId;
            try {
                subjectId = Long.parseLong(parts[1]);
            } catch (NumberFormatException e) {
                sendText(chatId, "Неверный ID предмета.");
                return;
            }

            String queueKind = "common";
            Integer subgroupNum = null;
            if (parts.length >= 3) {
                queueKind = parts[2].toLowerCase();
                if ("subgroup".equals(queueKind) && parts.length >= 4) {
                    try {
                        subgroupNum = Integer.parseInt(parts[3]);
                    } catch (NumberFormatException e) {
                        sendText(chatId, "Неверный номер подгруппы.");
                        return;
                    }
                }
            }

            Map<String, Object> body = new HashMap<>();
            body.put("chatId", chatId);
            body.put("subjectId", subjectId);
            body.put("queueKind", queueKind);
            if (subgroupNum != null) body.put("subgroupNum", subgroupNum);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (internalSecret != null && !internalSecret.isBlank()) {
                headers.add("X-TG-BOT-SECRET", internalSecret);
            }
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            try {
                ResponseEntity<Map> resp = restTemplate.exchange(
                        backendBaseUrl + "/api/tg/queue/status",
                        HttpMethod.POST,
                        entity,
                        Map.class
                );

                if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                    Boolean inQueue = (Boolean) resp.getBody().get("inQueue");
                    Integer position = (Integer) resp.getBody().get("position");
                    if (Boolean.TRUE.equals(inQueue)) {
                        sendText(chatId, "📊 Вы в очереди на позиции: " + position);
                    } else {
                        sendText(chatId, "📊 Вы не в очереди.");
                    }
                } else {
                    String error = resp.getBody() != null ? (String) resp.getBody().get("error") : "UNKNOWN";
                    sendText(chatId, "❌ Ошибка: " + error);
                }
            } catch (Exception e) {
                sendText(chatId, "Ошибка: " + e.getMessage());
            }
        }

        else if ("/help".equalsIgnoreCase(text.trim())) {
            sendText(chatId, "Команды бота:\n\n" +
                    "/start - Начало работы\n" +
                    "/auth - Получить код для входа на сайт\n" +
                    "/subjects - Список предметов\n" +
                    "/join <ID> [тип] [подгр] - Вступить в очередь\n" +
                    "/leave <ID> [тип] [подгр] - Покинуть очередь\n" +
                    "/status <ID> [тип] [подгр] - Проверить позицию\n" +
                    "/help - Эта справка\n\n" +
                    "Типы очереди: common (общая), subgroup (по подгруппам)");
        }
    }

    private void handleCallback(CallbackQuery cb) throws TelegramApiException {
        if (cb == null || cb.getData() == null) return;

        String data = cb.getData();
        Long chatId = cb.getMessage() != null ? cb.getMessage().getChatId() : null;
        if (chatId == null) return;

        if (data.startsWith("SWAP_ACCEPT_")) {
            long requestId = Long.parseLong(data.substring("SWAP_ACCEPT_".length()));
            Map<String, Object> result = callAccept(requestId, chatId);
            sendText(chatId, String.valueOf(result.getOrDefault("messageToTarget", "✅ Принято.")));

            Object requesterChatIdObj = result.get("requesterChatId");
            if (requesterChatIdObj != null) {
                long requesterChatId = Long.parseLong(String.valueOf(requesterChatIdObj));
                String messageToRequester = String.valueOf(result.getOrDefault("messageToRequester", ""));
                if (messageToRequester != null && !messageToRequester.isBlank()) {
                    sendText(requesterChatId, messageToRequester);
                }
            }

        } else if (data.startsWith("SWAP_DECLINE_")) {
            long requestId = Long.parseLong(data.substring("SWAP_DECLINE_".length()));
            Map<String, Object> result = callDecline(requestId, chatId);
            sendText(chatId, String.valueOf(result.getOrDefault("messageToTarget", "❌ Отклонено.")));

            Object requesterChatIdObj = result.get("requesterChatId");
            if (requesterChatIdObj != null) {
                long requesterChatId = Long.parseLong(String.valueOf(requesterChatIdObj));
                String messageToRequester = String.valueOf(result.getOrDefault("messageToRequester", ""));
                if (messageToRequester != null && !messageToRequester.isBlank()) {
                    sendText(requesterChatId, messageToRequester);
                }
            }

        } else if (data.startsWith("QUEUE_RESPOND_")) {
            String action = data.substring("QUEUE_RESPOND_".length());
            long messageId = cb.getMessage().getMessageId();
            Map<String, Object> result = callQueueRespond(chatId, messageId, action);
            String responseMsg = String.valueOf(result.getOrDefault("message", "Обработка..."));
            sendText(chatId, responseMsg);

            // Optionally notify next in queue could be sent here
        }

        // answer callback to remove "loading" state
        AnswerCallbackQuery ans = new AnswerCallbackQuery();
        ans.setCallbackQueryId(cb.getId());
        ans.setText("OK");
        ans.setShowAlert(false);
        execute(ans);
    }

    private Map<String, Object> callAccept(long requestId, long chatId) {
        Map<String, Object> body = Map.of("chatId", chatId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (internalSecret != null && !internalSecret.isBlank()) {
            headers.add("X-TG-BOT-SECRET", internalSecret);
        }
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<Map> resp = restTemplate.exchange(
                backendBaseUrl + "/api/tg/swap-requests/" + requestId + "/accept",
                HttpMethod.POST,
                entity,
                Map.class
        );
        return resp.getBody() != null ? resp.getBody() : new HashMap<>();
    }

    private Map<String, Object> callDecline(long requestId, long chatId) {
        Map<String, Object> body = Map.of("chatId", chatId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (internalSecret != null && !internalSecret.isBlank()) {
            headers.add("X-TG-BOT-SECRET", internalSecret);
        }
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<Map> resp = restTemplate.exchange(
                backendBaseUrl + "/api/tg/swap-requests/" + requestId + "/decline",
                HttpMethod.POST,
                entity,
                Map.class
        );
        return resp.getBody() != null ? resp.getBody() : new HashMap<>();
    }

    private Map<String, Object> callQueueRespond(long chatId, long messageId, String action) {
        Map<String, Object> body = Map.of(
                "chatId", chatId,
                "action", action
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (internalSecret != null && !internalSecret.isBlank()) {
            headers.add("X-TG-BOT-SECRET", internalSecret);
        }
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<Map> resp = restTemplate.exchange(
                backendBaseUrl + "/api/tg/queue/respond",
                HttpMethod.POST,
                entity,
                Map.class
        );
        return resp.getBody() != null ? resp.getBody() : new HashMap<>();
    }

    public void sendFirstInQueueNotification(long chatId,
                                               long subjectId,
                                               String subjectName,
                                               String queueTypeLabel,
                                               String deliveryTypeLabel,
                                               Integer subgroupNum,
                                               String myPosition) throws TelegramApiException {
        String text = "🎉 Ваша очередь!\n\n" +
                "📚 " + subjectName + "\n" +
                "📋 Тип: " + deliveryTypeLabel + "\n" +
                "🗂 Очередь: " + queueTypeLabel + (subgroupNum != null ? " (" + subgroupNum + ")" : "") + "\n\n" +
                "Вы на " + myPosition + " месте. Что хотите сделать?";

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        InlineKeyboardButton complete = new InlineKeyboardButton("✅ Я сдал");
        complete.setCallbackData("QUEUE_RESPOND_COMPLETE");

        InlineKeyboardButton leave = new InlineKeyboardButton("❌ Покинуть очередь");
        leave.setCallbackData("QUEUE_RESPOND_LEAVE");

        rows.add(List.of(complete, leave));
        markup.setKeyboard(rows);

        SendMessage sm = new SendMessage();
        sm.setChatId(String.valueOf(chatId));
        sm.setText(text);
        sm.setReplyMarkup(markup);
        execute(sm);
    }

    public void sendSwapRequestNotification(long targetChatId,
                                             long requestId,
                                             String subjectName,
                                             String deliveryTypeLabel,
                                             String queueTypeLabel,
                                             Integer subgroupNum,
                                             String requesterName) throws TelegramApiException {
        String pairText = "📚 " + subjectName;
        String typeText = "📋 Тип: " + deliveryTypeLabel;
        String queueText = "🗂 Очередь: " + queueTypeLabel + (subgroupNum != null ? " (" + subgroupNum + ")" : "");

        String text = "🔄 Запрос на обмен местами\n\n" +
                "👤 От: " + requesterName + "\n" +
                pairText + "\n" +
                typeText + "\n" +
                queueText + "\n\n" +
                "Хотите поменяться местами?";

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        InlineKeyboardButton accept = new InlineKeyboardButton("✅ Принять");
        accept.setCallbackData("SWAP_ACCEPT_" + requestId);
        InlineKeyboardButton decline = new InlineKeyboardButton("❌ Отклонить");
        decline.setCallbackData("SWAP_DECLINE_" + requestId);

        rows.add(List.of(accept, decline));
        markup.setKeyboard(rows);

        SendMessage sm = new SendMessage();
        sm.setChatId(String.valueOf(targetChatId));
        sm.setText(text);
        sm.setReplyMarkup(markup);
        execute(sm);
    }

    private void sendText(long chatId, String text) throws TelegramApiException {
        SendMessage sm = new SendMessage();
        sm.setChatId(String.valueOf(chatId));
        sm.setText(text);
        execute(sm);
    }
}

