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

    public QueueNotificationBot(String botUsername, String token, String internalSecret, String backendBaseUrl) {
        super(token);
        this.botUsername = botUsername;
        this.internalSecret = internalSecret;
        this.backendBaseUrl = backendBaseUrl;
        this.restTemplate = new RestTemplate();
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

            sendText(chatId, "Готово! Теперь откройте сайт и войдите по вашему telegram_tag. Если вход впервые — установите пароль.");
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

