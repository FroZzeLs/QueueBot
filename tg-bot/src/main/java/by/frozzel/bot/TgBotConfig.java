package by.frozzel.bot;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Configuration
public class TgBotConfig {

    @Bean
    public QueueNotificationBot queueNotificationBot(
            @Value("${BOT_NAME:}") String botName,
            @Value("${BOT_TOKEN:}") String botToken,
            @Value("${TG_BOT_INTERNAL_SECRET:}") String internalSecret,
            @Value("${BACKEND_BASE_URL:}") String backendBaseUrl) throws Exception {

        QueueNotificationBot bot = new QueueNotificationBot(botName, botToken, internalSecret, backendBaseUrl);

        // Чтобы docker-compose поднимался даже без токена, регистрацию делаем только если токен задан.
        if (botToken != null && !botToken.isBlank() && botName != null && !botName.isBlank()) {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(bot);
            
            // Start monitoring thread after bot is registered
            bot.startMonitoring();
        } else {
            System.out.println("TG bot token/botName not set; Telegram polling disabled.");
        }

        return bot;
    }
}

