package by.frozzel;

import by.frozzel.bot.QueueBot;
import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

        // 1. Читаем из файла
        String botName = dotenv.get("BOT_NAME");
        String botToken = dotenv.get("BOT_TOKEN");
        String secretToken = dotenv.get("ADMIN_SECRET");

        // 2. Если в файле нет, пробуем читать из системы (на случай Docker)
        if (botName == null) botName = System.getenv("BOT_NAME");
        if (botToken == null) botToken = System.getenv("BOT_TOKEN");
        if (secretToken == null) secretToken = System.getenv("ADMIN_SECRET");

        // Проверка
        if (botToken == null || botToken.isBlank()) {
            logger.error("Токен не найден! Заполните .env файл.");
            return;
        }

        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(new QueueBot(botName, botToken, secretToken));
            logger.info("Бот '{}' запущен!", botName);
        } catch (Exception e) {
            logger.error("Ошибка запуска", e);
        }
    }
}