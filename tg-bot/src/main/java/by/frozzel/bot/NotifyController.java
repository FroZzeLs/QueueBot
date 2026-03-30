package by.frozzel.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.Map;

@RestController
@RequestMapping("/api/tg/notify")
public class NotifyController {
    private static final Logger log = LoggerFactory.getLogger(NotifyController.class);

    private final QueueNotificationBot bot;
    private final String internalSecret;

    public NotifyController(QueueNotificationBot bot, @Value("${TG_BOT_INTERNAL_SECRET:}") String internalSecret) {
        this.bot = bot;
        this.internalSecret = internalSecret;
    }

    public static class SwapRequestNotificationBody {
        public long targetChatId;
        public long requestId;
        public String subjectName;
        public String deliveryTypeLabel; // "Индивидуальный" / "Бригадный"
        public String queueTypeLabel; // "Общая очередь" / "Подгрупповая очередь"
        public Integer subgroupNum;
        public String requesterName;
    }

    @PostMapping("/swap-request")
    public ResponseEntity<?> notifySwapRequest(@RequestBody SwapRequestNotificationBody body,
                                               @RequestHeader(value = "X-TG-BOT-SECRET", required = false) String secret) {
        if (internalSecret != null && !internalSecret.isBlank()) {
            if (secret == null || !internalSecret.equals(secret)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "INVALID_SECRET"));
            }
        }

        try {
            bot.sendSwapRequestNotification(
                    body.targetChatId,
                    body.requestId,
                    body.subjectName,
                    body.deliveryTypeLabel,
                    body.queueTypeLabel,
                    body.subgroupNum,
                    body.requesterName
            );
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (TelegramApiException e) {
            log.error("Telegram send failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "TELEGRAM_SEND_FAILED"));
        }
    }
}

