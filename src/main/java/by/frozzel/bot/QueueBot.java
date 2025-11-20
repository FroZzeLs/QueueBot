package by.frozzel.bot;

import by.frozzel.model.Student;
import by.frozzel.model.Subject;
import by.frozzel.model.SwapRequest;
import by.frozzel.service.QueueService;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class QueueBot extends TelegramLongPollingBot {

    private final String username;
    private final String secretAdminToken;

    private final QueueService service = new QueueService();
    private final Map<Long, BotState> userStates = new ConcurrentHashMap<>();
    private final Map<Long, Map<String, Object>> userData = new ConcurrentHashMap<>();

    // Constants
    private static final String BTN_BACK = "🔙 Назад";
    private static final String BTN_EDIT_GROUP = "👥 Редактирование списка группы";
    private static final String BTN_EDIT_SUBJECTS = "📚 Редактирование списка предметов";
    private static final String BTN_QUEUES_LIST = "📋 Список очередей";
    private static final String BTN_ADD_STUDENT = "➕ Добавление студента";
    private static final String BTN_DEL_STUDENT = "➖ Удаление студента";
    private static final String BTN_ASSIGN_ADMIN = "👑 Назначение администратора";
    private static final String BTN_ADD_SUBJECT = "➕ Добавить предмет";
    private static final String BTN_DEL_SUBJECT = "➖ Удалить предмет";
    private static final String BTN_QUEUE_GROUP = "👥 Групповая";
    private static final String BTN_QUEUE_SUBGROUP = "👤 Подгрупповая";
    private static final String BTN_SUBGROUP_1 = "1️⃣ Подгруппа 1";
    private static final String BTN_SUBGROUP_2 = "2️⃣ Подгруппа 2";
    private static final String BTN_MARK_PASSED = "✅ Отметить последнего сдавшего";
    private static final String BTN_REQ_SWAP = "🔄 Запросить смену очереди";
    private static final String BTN_LEAVE_QUEUE = "🏃 Сняться с очереди";

    public QueueBot(String username, String token, String secretAdminToken) {
        super(token);
        this.username = username;
        this.secretAdminToken = secretAdminToken;
    }

    @Override
    public String getBotUsername() { return username; }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasMessage() && update.getMessage().hasText()) {
                if (update.getMessage().getChat().isGroupChat() || update.getMessage().getChat().isSuperGroupChat()) {
                    handleGroupMessage(update);
                } else {
                    handlePrivateMessage(update);
                }
            } else if (update.hasCallbackQuery()) {
                handleCallback(update);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // --- GROUP LOGIC ---
    private void handleGroupMessage(Update update) throws TelegramApiException {
        String text = update.getMessage().getText();
        Long chatId = update.getMessage().getChatId();

        if (text.startsWith("/queue")) {
            List<Subject> subjects = service.getAllSubjects();
            if (subjects.isEmpty()) {
                sendMessage(chatId, "Список предметов пуст.");
                return;
            }
            SendMessage msg = new SendMessage();
            msg.setChatId(chatId);
            msg.setText("📚 Выберите предмет:");
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
            for (Subject s : subjects) {
                List<InlineKeyboardButton> row = new ArrayList<>();
                InlineKeyboardButton btn = new InlineKeyboardButton();
                btn.setText(s.getName());
                btn.setCallbackData("GRP_SUBJ_" + s.getId());
                row.add(btn);
                rows.add(row);
            }
            markup.setKeyboard(rows);
            msg.setReplyMarkup(markup);
            execute(msg);
        }
    }

    // --- PRIVATE LOGIC ---
    private void handlePrivateMessage(Update update) throws TelegramApiException {
        Long chatId = update.getMessage().getChatId();
        String text = update.getMessage().getText();
        String userTag = update.getMessage().getFrom().getUserName();
        if (userTag == null) userTag = "";

        service.registerChatId(userTag, chatId);

        if (text.startsWith("/claim_admin ")) {
            String providedToken = text.substring(13).trim();
            if (providedToken.equals(secretAdminToken)) {
                sendMessageWithBack(chatId, "👑 Токен принят. Введите ваше ФИО для назначения Суперадмином:");
                userStates.put(chatId, BotState.CLAIM_ADMIN_ENTER_FIO);
            } else {
                sendMessage(chatId, "❌ Неверный токен.");
            }
            return;
        }

        if (text.equals("/start")) {
            Student student = service.getStudentByChatId(chatId);
            if (student != null) {
                String msg = "👋 Привет, " + student.getFio() + "!";
                if (student.isAdmin()) msg += "\n👑 Вы администратор.";
                if (student.isSuperAdmin()) msg += " (Super)";
                sendMessage(chatId, msg);
                userStates.put(chatId, BotState.MAIN_MENU);
                showMainMenu(chatId);
            } else {
                sendMessage(chatId, "⛔ Вас нет в списке группы.\nПопросите администратора добавить вас, указав ваш тег: @" + userTag);
            }
            return;
        }

        BotState state = userStates.getOrDefault(chatId, BotState.MAIN_MENU);

        if (text.equals(BTN_BACK)) {
            goBack(chatId, state);
            return;
        }

        if (text.trim().isEmpty()) {
            sendMessage(chatId, "⚠️ Пустой ввод.");
            return;
        }

        switch (state) {
            case MAIN_MENU -> handleMainMenu(chatId, text);

            case CLAIM_ADMIN_ENTER_FIO -> {
                service.createOrUpdateSuperAdmin(text, chatId, userTag);
                sendMessage(chatId, "✅ Вы назначены Суперадмином: " + text);
                userStates.put(chatId, BotState.MAIN_MENU);
                showMainMenu(chatId);
            }

            case ADMIN_GROUP_MENU -> handleAdminGroupMenu(chatId, text);

            case ADD_STUDENT_NAME -> {
                userData.computeIfAbsent(chatId, k -> new HashMap<>()).put("temp_fio", text);
                userStates.put(chatId, BotState.ADD_STUDENT_SUBGROUP);
                sendReplyKeyboard(chatId, "🔢 Выберите номер подгруппы:", List.of("1", "2"));
            }
            case ADD_STUDENT_SUBGROUP -> {
                if (!text.equals("1") && !text.equals("2")) {
                    sendMessage(chatId, "⚠️ Введите 1 или 2.");
                    return;
                }
                userData.get(chatId).put("temp_sub", Integer.parseInt(text));
                userStates.put(chatId, BotState.ADD_STUDENT_TAG);
                sendMessageWithBack(chatId, "📧 Введите тег телеграм (без @):");
            }
            case ADD_STUDENT_TAG -> {
                Map<String, Object> data = userData.get(chatId);
                service.addStudent((String) data.get("temp_fio"), (Integer) data.get("temp_sub"), text);
                sendMessage(chatId, "✅ Студент успешно добавлен");
                userStates.put(chatId, BotState.ADMIN_GROUP_MENU);
                showAdminGroupMenu(chatId);
            }
            case REMOVE_STUDENT_NAME -> {
                Student admin = service.getStudentByChatId(chatId);
                Student target = resolveStudent(chatId, text);
                if (target == null) return;

                if (admin != null && admin.getId().equals(target.getId())) {
                    sendMessage(chatId, "⛔ Вы не можете удалить сами себя.");
                    return;
                }

                Long tCid = target.getChatId();
                int res = service.removeStudent(target.getFio(), admin);

                if (res == 1) {
                    sendMessage(chatId, "🗑️ Студент удалён: " + target.getFio());
                    if (tCid != null) {
                        try { sendMessage(tCid, "⛔ Вас удалили из списка."); } catch(Exception ignored){}
                    }
                } else if (res == -1) {
                    sendMessage(chatId, "⛔ Только Суперадмин может удалять админов.");
                } else {
                    sendMessage(chatId, "❌ Ошибка.");
                }
                userStates.put(chatId, BotState.ADMIN_GROUP_MENU);
                showAdminGroupMenu(chatId);
            }

            case ASSIGN_ADMIN_NAME -> {
                Student s = resolveStudent(chatId, text);
                if (s == null) return;
                if (service.makeAdmin(s.getFio())) {
                    sendMessage(chatId, "👑 Администратор назначен: " + s.getFio());
                    if(s.getChatId() != null) {
                        sendMessage(s.getChatId(), "👑 Вы назначены администратором! Напишите /start");
                    }
                } else {
                    sendMessage(chatId, "❌ Ошибка.");
                }
                userStates.put(chatId, BotState.ADMIN_GROUP_MENU);
                showAdminGroupMenu(chatId);
            }

            case ADMIN_SUBJECT_MENU -> handleAdminSubjectMenu(chatId, text);
            case ADD_SUBJECT_NAME -> {
                service.addSubject(text);
                sendMessage(chatId, "✅ Добавлено");
                userStates.put(chatId, BotState.ADMIN_SUBJECT_MENU);
                showAdminSubjectMenu(chatId);
            }
            case REMOVE_SUBJECT_NAME -> {
                service.removeSubject(text);
                sendMessage(chatId, "🗑️ Удалено");
                userStates.put(chatId, BotState.ADMIN_SUBJECT_MENU);
                showAdminSubjectMenu(chatId);
            }
            case QUEUE_LIST_MENU -> {
                List<Subject> subjects = service.getAllSubjects();
                boolean found = false;
                for (Subject s : subjects) {
                    if (s.getName().equals(text)) {
                        userData.computeIfAbsent(chatId, k -> new HashMap<>()).put("current_subject_id", s.getId());
                        found = true;
                        break;
                    }
                }
                if (found) {
                    userStates.put(chatId, BotState.QUEUE_TYPE_SELECTION);
                    sendReplyKeyboard(chatId, "🗂 Выберите тип очереди:", List.of(BTN_QUEUE_GROUP, BTN_QUEUE_SUBGROUP, BTN_BACK));
                } else {
                    sendMessage(chatId, "⚠️ Выберите предмет.");
                }
            }
            case QUEUE_TYPE_SELECTION -> {
                if (text.equals(BTN_QUEUE_GROUP)) {
                    showQueue(chatId, (Long) userData.get(chatId).get("current_subject_id"), true, 0);
                } else if (text.equals(BTN_QUEUE_SUBGROUP)) {
                    userStates.put(chatId, BotState.QUEUE_SUBGROUP_SELECTION);
                    sendReplyKeyboard(chatId, "🔢 Выберите подгруппу:", List.of(BTN_SUBGROUP_1, BTN_SUBGROUP_2, BTN_BACK));
                } else {
                    sendMessage(chatId, "⚠️ Выберите тип.");
                }
            }
            case QUEUE_SUBGROUP_SELECTION -> {
                if (text.equals(BTN_SUBGROUP_1)) {
                    showQueue(chatId, (Long) userData.get(chatId).get("current_subject_id"), false, 1);
                } else if (text.equals(BTN_SUBGROUP_2)) {
                    showQueue(chatId, (Long) userData.get(chatId).get("current_subject_id"), false, 2);
                } else {
                    sendMessage(chatId, "⚠️ Используйте кнопки.");
                }
            }
            case QUEUE_VIEW -> {
                if (text.equals(BTN_MARK_PASSED)) {
                    Student s = service.getStudentByChatId(chatId);
                    if (s != null && s.isAdmin()) {
                        userStates.put(chatId, BotState.MARK_LAST_PASSED_NAME);
                        sendMessageWithBack(chatId, "✍️ Введите ФИО сдавшего:");
                    } else {
                        sendMessage(chatId, "⛔ Нет прав.");
                    }
                } else if (text.equals(BTN_REQ_SWAP)) {
                    userStates.put(chatId, BotState.REQUEST_SWAP_NAME);
                    sendMessageWithBack(chatId, "🔄 Введите ФИО (или часть) для обмена:");
                } else if (text.equals(BTN_LEAVE_QUEUE)) {
                    try {
                        Student me = service.getStudentByChatId(chatId);
                        Long subId = (Long) userData.get(chatId).get("current_subject_id");
                        boolean isGr = (Boolean) userData.get(chatId).get("current_is_group");
                        service.leaveQueue(me.getId(), subId, isGr);
                        sendMessage(chatId, "🏃 Вы снялись с очереди.");
                        int currentSg = (int) userData.get(chatId).getOrDefault("current_subgroup", 0);
                        showQueue(chatId, subId, isGr, currentSg);
                    } catch (Exception e) {
                        sendMessage(chatId, "❌ " + e.getMessage());
                    }
                } else {
                    sendMessage(chatId, "⚠️ Используйте кнопки.");
                }
            }

            case MARK_LAST_PASSED_NAME -> {
                Student s = resolveStudent(chatId, text);
                if (s == null) return;
                try {
                    Long subId = (Long) userData.get(chatId).get("current_subject_id");
                    boolean isGr = (Boolean) userData.get(chatId).get("current_is_group");
                    service.setLastPassed(subId, isGr, s.getFio());
                    sendMessage(chatId, "✅ Отмечено: " + s.getFio());
                    if (s.getChatId() != null) {
                        Subject subj = service.getSubjectById(subId);
                        String sName = subj!=null ? subj.getName() : "";
                        String type = isGr ? "Групповая" : "Подгрупповая";
                        sendMessage(s.getChatId(), "✅ Вас отметили как сдавшего: " + sName + " (" + type + ")");
                    }
                    int currentSg = (int) userData.get(chatId).getOrDefault("current_subgroup", 0);
                    showQueue(chatId, subId, isGr, currentSg);
                } catch (IllegalArgumentException e) {
                    sendMessage(chatId, "❌ " + e.getMessage());
                }
            }

            case REQUEST_SWAP_NAME -> {
                Student target = resolveStudent(chatId, text);
                if (target == null) return;
                try {
                    Student sender = service.getStudentByChatId(chatId);
                    Long subId = (Long) userData.get(chatId).get("current_subject_id");
                    boolean isGr = (Boolean) userData.get(chatId).get("current_is_group");
                    Long reqId = service.createSwapRequest(sender.getId(), target.getFio(), subId, isGr);
                    sendMessage(chatId, "📤 Запрос отправлен " + target.getFio());
                    if (target.getChatId() != null) {
                        Subject subj = service.getSubjectById(subId);
                        String sName = subj!=null ? subj.getName() : "???";
                        String type = isGr ? "Групповая" : "Подгрупповая";
                        sendSwapNotification(target.getChatId(), sender.getFio(), reqId, sName, type);
                    } else {
                        sendMessage(chatId, "⚠️ У пользователя нет Telegram.");
                    }
                    int currentSg = (int) userData.get(chatId).getOrDefault("current_subgroup", 0);
                    showQueue(chatId, subId, isGr, currentSg);
                } catch (Exception e) {
                    sendMessage(chatId, "❌ " + e.getMessage());
                }
            }
        }
    }

    // --- CALLBACK ---

    private void handleCallback(Update update) throws TelegramApiException {
        String data = update.getCallbackQuery().getData();
        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Integer messageId = update.getCallbackQuery().getMessage().getMessageId();

        if (data.startsWith("GRP_")) {
            handleGroupCallback(chatId, messageId, data);
            return;
        }

        if (data.startsWith("SWAP_ACCEPT_")) {
            Long reqId = Long.parseLong(data.substring(12));
            SwapRequest req = service.getSwapRequest(reqId);
            if (req == null) {
                sendMessage(chatId, "❌ Устарело.");
                return;
            }
            Long requesterChatId = req.getRequester().getChatId();
            String targetName = req.getTarget().getFio();
            Long subjectId = req.getSubject().getId();
            boolean isGroup = req.isGroupQueue();
            int requesterSubgroup = req.getRequester().getSubgroup();
            try {
                service.executeSwap(reqId);
                EditMessageText edit = new EditMessageText();
                edit.setChatId(chatId);
                edit.setMessageId(messageId);
                edit.setText("✅ Вы ПРИНЯЛИ.");
                execute(edit);
                sendMessage(chatId, "✅ Обмен выполнен!");
                if (requesterChatId != null) {
                    List<String> q = service.getQueue(subjectId, isGroup, requesterSubgroup);
                    sendMessage(requesterChatId, "✅ " + targetName + " принял запрос.\n\n" + String.join("\n", q));
                }
            } catch (Exception e) {
                sendMessage(chatId, "❌ Ошибка.");
            }
        } else if (data.startsWith("SWAP_DECLINE_")) {
            Long reqId = Long.parseLong(data.substring(13));
            SwapRequest req = service.getSwapRequest(reqId);
            if (req != null) {
                Long requesterChatId = req.getRequester().getChatId();
                String targetName = req.getTarget().getFio();
                service.deleteSwapRequest(reqId);
                EditMessageText edit = new EditMessageText();
                edit.setChatId(chatId);
                edit.setMessageId(messageId);
                edit.setText("❌ Вы ОТКЛОНИЛИ.");
                execute(edit);
                if (requesterChatId != null) {
                    sendMessage(requesterChatId, "❌ " + targetName + " отклонил запрос.");
                }
            } else {
                sendMessage(chatId, "⚠️ Устарело.");
            }
        }
    }

    private void handleGroupCallback(Long chatId, Integer messageId, String data) throws TelegramApiException {
        if (data.startsWith("GRP_SUBJ_")) {
            Long subjId = Long.parseLong(data.substring(9));
            EditMessageText edit = new EditMessageText();
            edit.setChatId(chatId);
            edit.setMessageId(messageId);
            edit.setText("🗂 Выберите тип очереди:");
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
            List<InlineKeyboardButton> row = new ArrayList<>();
            InlineKeyboardButton btnGroup = new InlineKeyboardButton();
            btnGroup.setText("👥 Групповая");
            btnGroup.setCallbackData("GRP_TYPE_" + subjId + "_G");
            InlineKeyboardButton btnSub = new InlineKeyboardButton();
            btnSub.setText("👤 Подгрупповая");
            btnSub.setCallbackData("GRP_TYPE_" + subjId + "_S");
            row.add(btnGroup);
            row.add(btnSub);
            rows.add(row);
            markup.setKeyboard(rows);
            edit.setReplyMarkup(markup);
            execute(edit);
        } else if (data.startsWith("GRP_TYPE_")) {
            String[] parts = data.split("_");
            Long subjId = Long.parseLong(parts[2]);
            String type = parts[3];
            if (type.equals("G")) {
                displayGroupQueue(chatId, messageId, subjId, true, 0);
            } else {
                EditMessageText edit = new EditMessageText();
                edit.setChatId(chatId);
                edit.setMessageId(messageId);
                edit.setText("🔢 Выберите подгруппу:");
                InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> rows = new ArrayList<>();
                List<InlineKeyboardButton> row = new ArrayList<>();
                InlineKeyboardButton s1 = new InlineKeyboardButton();
                s1.setText("1️⃣ Подгруппа 1");
                s1.setCallbackData("GRP_VIEW_" + subjId + "_1");
                InlineKeyboardButton s2 = new InlineKeyboardButton();
                s2.setText("2️⃣ Подгруппа 2");
                s2.setCallbackData("GRP_VIEW_" + subjId + "_2");
                row.add(s1);
                row.add(s2);
                rows.add(row);
                markup.setKeyboard(rows);
                edit.setReplyMarkup(markup);
                execute(edit);
            }
        } else if (data.startsWith("GRP_VIEW_")) {
            String[] parts = data.split("_");
            Long subjId = Long.parseLong(parts[2]);
            int subNum = Integer.parseInt(parts[3]);
            displayGroupQueue(chatId, messageId, subjId, false, subNum);
        }
    }

    private void displayGroupQueue(Long chatId, Integer messageId, Long subjId, boolean isGroup, int subNum) throws TelegramApiException {
        Subject subject = service.getSubjectById(subjId);
        String subjectName = (subject != null) ? subject.getName() : "???";
        List<String> q = service.getQueue(subjId, isGroup, subNum);
        StringBuilder sb = new StringBuilder();
        sb.append("📚 *").append(subjectName).append("*\n");
        sb.append(isGroup ? "👥 *Групповая очередь:*" : "👤 *Подгруппа " + subNum + " очередь:*").append("\n\n");

        if (q.isEmpty()) sb.append("💤 Очередь пуста или все сдали.");
        else sb.append(String.join("\n", q));

        sb.append("\n\n⚠️ _Запросы на обмен местами отправляются только через личные сообщения с ботом._");
        EditMessageText edit = new EditMessageText();
        edit.setChatId(chatId);
        edit.setMessageId(messageId);
        edit.setText(sb.toString());
        edit.setParseMode("Markdown");
        edit.setReplyMarkup(null);
        execute(edit);
    }

    private Student resolveStudent(Long chatId, String text) throws TelegramApiException {
        Student exact = service.getStudentByFio(text);
        if (exact != null) return exact;
        List<Student> found = service.findStudents(text);
        if (found.isEmpty()) {
            sendMessage(chatId, "❌ Студент не найден.");
            return null;
        }
        if (found.size() == 1) {
            return found.get(0);
        }
        List<String> names = found.stream().map(Student::getFio).collect(Collectors.toList());
        names.add(BTN_BACK);
        sendReplyKeyboard(chatId, "🔍 Найдено несколько студентов. Выберите нужного:", names);
        return null;
    }

    private void handleMainMenu(Long chatId, String text) throws TelegramApiException {
        Student student = service.getStudentByChatId(chatId);
        if (student == null) return;
        boolean isAdmin = student.isAdmin();
        if (isAdmin) {
            switch (text) {
                case BTN_EDIT_GROUP -> {
                    StringBuilder sb = new StringBuilder("📜 *Текущий список группы:*\n");
                    List<Student> list = service.getAllStudents();
                    if (list.isEmpty()) sb.append("Пусто");
                    for (Student s : list) {
                        sb.append("👤 ").append(s.getFio()).append(" (").append(s.getSubgroup()).append(")\n");
                    }
                    sendMessage(chatId, sb.toString());
                    userStates.put(chatId, BotState.ADMIN_GROUP_MENU);
                    showAdminGroupMenu(chatId);
                }
                case BTN_EDIT_SUBJECTS -> {
                    StringBuilder sb = new StringBuilder("📚 *Список предметов:*\n");
                    List<Subject> list = service.getAllSubjects();
                    if (list.isEmpty()) sb.append("Пусто");
                    for (Subject s : list) {
                        sb.append("🔹 ").append(s.getName()).append("\n");
                    }
                    sendMessage(chatId, sb.toString());
                    userStates.put(chatId, BotState.ADMIN_SUBJECT_MENU);
                    showAdminSubjectMenu(chatId);
                }
                case BTN_QUEUES_LIST -> showSubjectsList(chatId);
                default -> sendMessage(chatId, "❓ Неизвестная команда");
            }
        } else {
            if (text.equals(BTN_QUEUES_LIST)) showSubjectsList(chatId);
            else sendMessage(chatId, "❓ Неизвестная команда");
        }
    }

    private void handleAdminGroupMenu(Long chatId, String text) throws TelegramApiException {
        switch (text) {
            case BTN_ADD_STUDENT -> { userStates.put(chatId, BotState.ADD_STUDENT_NAME); sendMessageWithBack(chatId, "✍️ Введите ФИО студента:"); }
            case BTN_DEL_STUDENT -> { userStates.put(chatId, BotState.REMOVE_STUDENT_NAME); sendMessageWithBack(chatId, "🗑️ Введите ФИО (или часть) студента для удаления:"); }
            case BTN_ASSIGN_ADMIN -> { userStates.put(chatId, BotState.ASSIGN_ADMIN_NAME); sendMessageWithBack(chatId, "👑 Введите ФИО (или часть) будущего админа:"); }
            case BTN_BACK -> { userStates.put(chatId, BotState.MAIN_MENU); showMainMenu(chatId); }
            default -> sendMessage(chatId, "⚠️ Выберите действие из меню.");
        }
    }

    private void handleAdminSubjectMenu(Long chatId, String text) throws TelegramApiException {
        switch (text) {
            case BTN_ADD_SUBJECT -> { userStates.put(chatId, BotState.ADD_SUBJECT_NAME); sendMessageWithBack(chatId, "✍️ Введите название предмета:"); }
            case BTN_DEL_SUBJECT -> { userStates.put(chatId, BotState.REMOVE_SUBJECT_NAME); sendMessageWithBack(chatId, "🗑️ Введите название предмета для удаления:"); }
            case BTN_BACK -> { userStates.put(chatId, BotState.MAIN_MENU); showMainMenu(chatId); }
            default -> sendMessage(chatId, "⚠️ Выберите действие из меню.");
        }
    }

    private void showQueue(Long chatId, Long subjectId, boolean isGroup, int subgroupNum) throws TelegramApiException {
        userData.computeIfAbsent(chatId, k -> new HashMap<>()).put("current_subject_id", subjectId);
        userData.get(chatId).put("current_is_group", isGroup);
        userData.get(chatId).put("current_subgroup", subgroupNum);

        Student student = service.getStudentByChatId(chatId);
        Subject subject = service.getSubjectById(subjectId);
        String subjectName = (subject != null) ? subject.getName() : "???";

        List<String> q = service.getQueue(subjectId, isGroup, subgroupNum);
        StringBuilder sb = new StringBuilder();
        sb.append("📚 *").append(subjectName).append("*\n");
        sb.append(isGroup ? "👥 *Групповая очередь:*" : "👤 *Подгруппа " + subgroupNum + " очередь:*").append("\n\n");

        if (q.isEmpty()) sb.append("💤 Очередь пуста или все сдали.");
        else sb.append(String.join("\n", q));

        userStates.put(chatId, BotState.QUEUE_VIEW);
        List<String> buttons = new ArrayList<>();
        if (student != null && student.isAdmin()) buttons.add(BTN_MARK_PASSED);
        buttons.add(BTN_REQ_SWAP);
        buttons.add(BTN_LEAVE_QUEUE);
        buttons.add(BTN_BACK);
        sendReplyKeyboard(chatId, sb.toString(), buttons);
    }

    private void showSubjectsList(Long chatId) throws TelegramApiException {
        userStates.put(chatId, BotState.QUEUE_LIST_MENU);
        List<Subject> subjects = service.getAllSubjects();
        List<String> names = new ArrayList<>();
        for (Subject s : subjects) names.add(s.getName());
        names.add(BTN_BACK);
        sendReplyKeyboard(chatId, "📚 Выберите предмет:", names);
    }

    // --- МЕТОДЫ ДЛЯ ОТОБРАЖЕНИЯ МЕНЮ (ДОБАВЛЕНЫ) ---

    private void showAdminGroupMenu(Long chatId) throws TelegramApiException {
        sendReplyKeyboard(chatId, "👥 Меню группы", List.of(BTN_ADD_STUDENT, BTN_DEL_STUDENT, BTN_ASSIGN_ADMIN, BTN_BACK));
    }

    private void showAdminSubjectMenu(Long chatId) throws TelegramApiException {
        sendReplyKeyboard(chatId, "📚 Меню предметов", List.of(BTN_ADD_SUBJECT, BTN_DEL_SUBJECT, BTN_BACK));
    }

    // ---------------------------------------------

    private void goBack(Long chatId, BotState currentState) throws TelegramApiException {
        if (currentState == BotState.QUEUE_VIEW || currentState == BotState.QUEUE_SUBGROUP_SELECTION) {
            userStates.put(chatId, BotState.QUEUE_TYPE_SELECTION);
            sendReplyKeyboard(chatId, "🗂 Выберите тип очереди:", List.of(BTN_QUEUE_GROUP, BTN_QUEUE_SUBGROUP, BTN_BACK));
        } else if (currentState == BotState.QUEUE_TYPE_SELECTION) {
            showSubjectsList(chatId);
        } else {
            userStates.put(chatId, BotState.MAIN_MENU);
            showMainMenu(chatId);
        }
    }

    private void showMainMenu(Long chatId) throws TelegramApiException {
        Student s = service.getStudentByChatId(chatId);
        List<String> buttons = new ArrayList<>();
        if (s != null && s.isAdmin()) {
            buttons.add(BTN_EDIT_GROUP);
            buttons.add(BTN_EDIT_SUBJECTS);
        }
        buttons.add(BTN_QUEUES_LIST);
        sendReplyKeyboard(chatId, "🏠 Главное меню", buttons);
    }

    private void sendReplyKeyboard(Long chatId, String text, List<String> buttons) throws TelegramApiException {
        SendMessage msg = new SendMessage();
        msg.setChatId(chatId);
        msg.setText(text);
        msg.setParseMode("Markdown");
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        List<KeyboardRow> keyboard = new ArrayList<>();
        for (String btn : buttons) {
            KeyboardRow row = new KeyboardRow();
            row.add(btn);
            keyboard.add(row);
        }
        keyboardMarkup.setKeyboard(keyboard);
        msg.setReplyMarkup(keyboardMarkup);
        execute(msg);
    }

    private void sendMessageWithBack(Long chatId, String text) throws TelegramApiException {
        sendReplyKeyboard(chatId, text, List.of(BTN_BACK));
    }

    private void sendMessage(Long chatId, String text) throws TelegramApiException {
        execute(new SendMessage(String.valueOf(chatId), text));
    }

    private void sendSwapNotification(Long targetChatId, String requesterName, Long requestId, String subjectName, String type) throws TelegramApiException {
        SendMessage msg = new SendMessage();
        msg.setChatId(targetChatId);
        String info = String.format(
                "🔄 *Запрос на обмен местами*\n\n" +
                        "👤 От: %s\n" +
                        "📚 Предмет: %s\n" +
                        "📋 Тип: %s\n\n" +
                        "Хотите поменяться местами?",
                requesterName, subjectName, type
        );
        msg.setText(info);
        msg.setParseMode("Markdown");
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();
        InlineKeyboardButton accept = new InlineKeyboardButton();
        accept.setText("✅ Принять");
        accept.setCallbackData("SWAP_ACCEPT_" + requestId);
        InlineKeyboardButton decline = new InlineKeyboardButton();
        decline.setText("❌ Отказать");
        decline.setCallbackData("SWAP_DECLINE_" + requestId);
        row.add(accept);
        row.add(decline);
        rows.add(row);
        markup.setKeyboard(rows);
        msg.setReplyMarkup(markup);
        execute(msg);
    }
}