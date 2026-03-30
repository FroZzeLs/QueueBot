package by.frozzel.backend.web;

import by.frozzel.backend.config.HibernateUtil;
import by.frozzel.backend.model.*;
import by.frozzel.backend.service.QueueEngine;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/tg")
public class TgController {

    public static class RegisterChatRequest {
        public String telegramTag;
        public long chatId;
    }

    @PostMapping("/register-chat")
    public ResponseEntity<?> registerChat(@RequestBody RegisterChatRequest req,
                                           @RequestHeader(value = "X-TG-BOT-SECRET", required = false) String secret) {
        String expected = System.getenv("TG_BOT_INTERNAL_SECRET");
        if (expected != null && !expected.isBlank()) {
            if (secret == null || !expected.equals(secret)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "INVALID_SECRET"));
            }
        }
        String tag = normalizeTag(req.telegramTag);
        if (tag == null || tag.isBlank()) return ResponseEntity.badRequest().body(Map.of("error", "EMPTY_TAG"));

        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = s.beginTransaction();
            Student st = s.createQuery("FROM Student WHERE telegramTag = :tag", Student.class)
                    .setParameter("tag", tag)
                    .setMaxResults(1)
                    .uniqueResult();
            if (st == null) {
                tx.rollback();
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "STUDENT_NOT_FOUND"));
            }
            st.setChatId(req.chatId);
            s.merge(st);
            tx.commit();
            return ResponseEntity.ok(Map.of("ok", true));
        }
    }

    public static class ChatActionRequest {
        public long chatId;
    }

    @PostMapping("/swap-requests/{id}/accept")
    public ResponseEntity<?> accept(@PathVariable long id,
                                     @RequestBody ChatActionRequest req,
                                     @RequestHeader(value = "X-TG-BOT-SECRET", required = false) String secret) {
        String expected = System.getenv("TG_BOT_INTERNAL_SECRET");
        if (expected != null && !expected.isBlank()) {
            if (secret == null || !expected.equals(secret)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "INVALID_SECRET"));
            }
        }
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Student acceptor = s.createQuery("FROM Student WHERE chatId = :cid", Student.class)
                    .setParameter("cid", req.chatId)
                    .setMaxResults(1)
                    .uniqueResult();
            if (acceptor == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "BOT_USER_NOT_REGISTERED"));

            SwapRequest sw = s.get(SwapRequest.class, id);
            if (sw == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "REQUEST_NOT_FOUND"));
            if (sw.getTargetNotifyStudent() == null || !Objects.equals(sw.getTargetNotifyStudent().getId(), acceptor.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "NOT_YOUR_REQUEST"));
            }

            // Execute
            Transaction tx = s.beginTransaction();
            Subject subject = sw.getSubject();

            if (subject.getDeliveryType() == DeliveryType.INDIVIDUAL) {
                TemporarySwap ts = new TemporarySwap();
                ts.setSubject(subject);
                ts.setQueueKind(sw.getQueueKind());
                ts.setSubgroupNum(sw.getSubgroupNum());
                ts.setStudent1(sw.getRequesterStudent());
                ts.setStudent2(sw.getTargetStudent());
                s.persist(ts);
            } else {
                TemporarySwap ts = new TemporarySwap();
                ts.setSubject(subject);
                ts.setQueueKind(sw.getQueueKind());
                ts.setSubgroupNum(sw.getSubgroupNum());
                ts.setBrigade1(sw.getRequesterBrigade());
                ts.setBrigade2(sw.getTargetBrigade());
                s.persist(ts);
            }

            s.remove(sw);
            tx.commit();

            Student requester = sw.getRequesterNotifyStudent();
            String messageToTarget = "✅ Вы ПРИНЯЛИ. Обмен выполнен.";
            String messageToRequester = buildQueueMessageForRequester(s, requester, subject, sw.getQueueKind(), sw.getSubgroupNum());
            return ResponseEntity.ok(Map.of(
                    "messageToTarget", messageToTarget,
                    "messageToRequester", messageToRequester,
                    "requesterChatId", requester != null ? requester.getChatId() : null
            ));
        }
    }

    @PostMapping("/swap-requests/{id}/decline")
    public ResponseEntity<?> decline(@PathVariable long id,
                                     @RequestBody ChatActionRequest req,
                                     @RequestHeader(value = "X-TG-BOT-SECRET", required = false) String secret) {
        String expected = System.getenv("TG_BOT_INTERNAL_SECRET");
        if (expected != null && !expected.isBlank()) {
            if (secret == null || !expected.equals(secret)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "INVALID_SECRET"));
            }
        }
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Student acceptor = s.createQuery("FROM Student WHERE chatId = :cid", Student.class)
                    .setParameter("cid", req.chatId)
                    .setMaxResults(1)
                    .uniqueResult();
            if (acceptor == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "BOT_USER_NOT_REGISTERED"));

            SwapRequest sw = s.get(SwapRequest.class, id);
            if (sw == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "REQUEST_NOT_FOUND"));
            if (sw.getTargetNotifyStudent() == null || !Objects.equals(sw.getTargetNotifyStudent().getId(), acceptor.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "NOT_YOUR_REQUEST"));
            }

            Transaction tx = s.beginTransaction();
            s.remove(sw);
            tx.commit();

            Student requester = sw.getRequesterNotifyStudent();
            String messageToTarget = "❌ Вы ОТКЛОНИЛИ запрос на обмен.";
            String messageToRequester = "❌ Запрос на обмен был отклонен.";
            return ResponseEntity.ok(Map.of(
                    "messageToTarget", messageToTarget,
                    "messageToRequester", messageToRequester,
                    "requesterChatId", requester != null ? requester.getChatId() : null
            ));
        }
    }

    private static String buildQueueMessageForRequester(Session s,
                                                          Student requester,
                                                          Subject subject,
                                                          QueueKind queueKind,
                                                          Integer subgroupNum) {
        String subjectName = subject.getName();
        String queueLabel = (queueKind == QueueKind.COMMON)
                ? "👥 Общая очередь:"
                : "👤 Подгруппа " + subgroupNum + " очередь:";

        if (subject.getDeliveryType() == DeliveryType.INDIVIDUAL) {
            List<Student> items = QueueEngine.buildActiveIndividualQueue(s, subject, queueKind, subgroupNum);
            if (items.isEmpty()) return "📚 " + subjectName + "\n" + queueLabel + "\n\n💤 Очередь пуста или все сдали.";
            StringBuilder sb = new StringBuilder();
            sb.append("📚 *").append(subjectName).append("*\n");
            sb.append(queueLabel).append("\n\n");
            for (int i = 0; i < items.size(); i++) {
                sb.append(i + 1).append(". ").append(items.get(i).getFio()).append("\n");
            }
            return sb.toString();
        }

        List<Brigade> items = QueueEngine.buildActiveBrigadeQueue(s, subject, queueKind, subgroupNum);
        if (items.isEmpty()) return "📚 " + subjectName + "\n" + queueLabel + "\n\n💤 Очередь пуста или все сдали.";
        StringBuilder sb = new StringBuilder();
        sb.append("📚 *").append(subjectName).append("*\n");
        sb.append(queueLabel).append("\n\n");
        for (int i = 0; i < items.size(); i++) {
            sb.append(i + 1).append(". ").append(items.get(i).getDisplayName()).append("\n");
        }
        return sb.toString();
    }

    private static String normalizeTag(String raw) {
        if (raw == null) return null;
        String t = raw.trim();
        if (t.startsWith("@")) t = t.substring(1);
        return t;
    }
}

