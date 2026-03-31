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
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TgController.class);

    public static class QueueMonitoringRequest {
        public long subjectId;
        public String queueKind;
        public Integer subgroupNum;
    }

    @PostMapping("/queue/monitor")
    public ResponseEntity<?> monitorQueue(@RequestBody QueueMonitoringRequest req) {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            QueueKind qk;
            try {
                qk = QueueKind.valueOf(req.queueKind.toUpperCase(Locale.ROOT));
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(Map.of("error", "BAD_QUEUE_KIND"));
            }

            Subject subject = s.get(Subject.class, req.subjectId);
            if (subject == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "SUBJECT_NOT_FOUND"));

            List<Map<String, Object>> users = new ArrayList<>();

            if (subject.getDeliveryType() == DeliveryType.INDIVIDUAL) {
                List<Student> active = QueueEngine.buildActiveIndividualQueue(s, subject, qk, req.subgroupNum);
                for (int i = 0; i < active.size(); i++) {
                    Student st = active.get(i);
                    if (st.getChatId() != null) {
                        users.add(Map.of(
                                "chatId", st.getChatId(),
                                "position", i + 1,
                                "studentId", st.getId(),
                                "fio", st.getFio()
                        ));
                    }
                }
            } else {
                List<Brigade> active = QueueEngine.buildActiveBrigadeQueue(s, subject, qk, req.subgroupNum);
                for (int i = 0; i < active.size(); i++) {
                    Brigade b = active.get(i);
                    // Get all students in this brigade
                    List<BrigadeMember> members = s.createQuery(
                                    "FROM BrigadeMember bm WHERE bm.brigade.id = :bid ORDER BY bm.student.fio ASC",
                                    BrigadeMember.class)
                            .setParameter("bid", b.getId())
                            .list();
                    for (BrigadeMember bm : members) {
                        Student st = bm.getStudent();
                        if (st.getChatId() != null) {
                            users.add(Map.of(
                                    "chatId", st.getChatId(),
                                    "position", i + 1,
                                    "brigadeId", b.getId(),
                                    "brigadeName", b.getDisplayName(),
                                    "studentId", st.getId(),
                                    "fio", st.getFio()
                            ));
                        }
                    }
                }
            }

            return ResponseEntity.ok(Map.of("users", users));
        }
    }

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

    public static class TgQueueJoinRequest {
        public long chatId;
        public long subjectId;
        public String queueKind;
        public Integer subgroupNum;
    }

    @PostMapping("/queue/join")
    public ResponseEntity<?> tgJoinQueue(@RequestBody TgQueueJoinRequest req) {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Student st = s.createQuery("FROM Student WHERE chatId = :chatId", Student.class)
                    .setParameter("chatId", req.chatId)
                    .setMaxResults(1)
                    .uniqueResult();
            if (st == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "BOT_USER_NOT_REGISTERED"));
            }

            QueueKind qk;
            try {
                qk = QueueKind.valueOf(req.queueKind.toUpperCase(Locale.ROOT));
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(Map.of("error", "BAD_QUEUE_KIND"));
            }

            Subject subject = s.get(Subject.class, req.subjectId);
            if (subject == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "SUBJECT_NOT_FOUND"));

            QueueEngine.joinQueue(s, st, subject, qk, req.subgroupNum);
            return ResponseEntity.ok(Map.of("ok", true, "message", "Вы вступили в очередь."));
        }
    }

    public static class TgQueueLeaveRequest {
        public long chatId;
        public long subjectId;
        public String queueKind;
        public Integer subgroupNum;
    }

    @PostMapping("/queue/leave")
    public ResponseEntity<?> tgLeaveQueue(@RequestBody TgQueueLeaveRequest req) {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Student st = s.createQuery("FROM Student WHERE chatId = :chatId", Student.class)
                    .setParameter("chatId", req.chatId)
                    .setMaxResults(1)
                    .uniqueResult();
            if (st == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "BOT_USER_NOT_REGISTERED"));
            }

            QueueKind qk;
            try {
                qk = QueueKind.valueOf(req.queueKind.toUpperCase(Locale.ROOT));
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(Map.of("error", "BAD_QUEUE_KIND"));
            }

            Subject subject = s.get(Subject.class, req.subjectId);
            if (subject == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "SUBJECT_NOT_FOUND"));

            QueueEngine.leaveQueue(s, st, subject, qk, req.subgroupNum);
            return ResponseEntity.ok(Map.of("ok", true, "message", "Вы покинули очередь."));
        }
    }

    public static class TgQueueStatusRequest {
        public long chatId;
        public long subjectId;
        public String queueKind;
        public Integer subgroupNum;
    }

    @PostMapping("/queue/status")
    public ResponseEntity<?> tgQueueStatus(@RequestBody TgQueueStatusRequest req) {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Student st = s.createQuery("FROM Student WHERE chatId = :chatId", Student.class)
                    .setParameter("chatId", req.chatId)
                    .setMaxResults(1)
                    .uniqueResult();
            if (st == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "BOT_USER_NOT_REGISTERED"));
            }

            QueueKind qk;
            try {
                qk = QueueKind.valueOf(req.queueKind.toUpperCase(Locale.ROOT));
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(Map.of("error", "BAD_QUEUE_KIND"));
            }

            Subject subject = s.get(Subject.class, req.subjectId);
            if (subject == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "SUBJECT_NOT_FOUND"));

            boolean inQueue = QueueEngine.isUserInQueue(s, st, subject, qk, req.subgroupNum);
            int position = -1;
            String myBrigadeId = null;

            if (inQueue) {
                if (subject.getDeliveryType() == DeliveryType.INDIVIDUAL) {
                    List<Student> active = QueueEngine.buildActiveIndividualQueue(s, subject, qk, req.subgroupNum);
                    for (int i = 0; i < active.size(); i++) {
                        if (Objects.equals(active.get(i).getId(), st.getId())) {
                            position = i + 1;
                            break;
                        }
                    }
                } else {
                    Brigade myBrigade = QueueEngine.findBrigadeByStudent(s, subject, st, qk, req.subgroupNum);
                    if (myBrigade != null) {
                        List<Brigade> active = QueueEngine.buildActiveBrigadeQueue(s, subject, qk, req.subgroupNum);
                        for (int i = 0; i < active.size(); i++) {
                            if (Objects.equals(active.get(i).getId(), myBrigade.getId())) {
                                position = i + 1;
                                myBrigadeId = String.valueOf(myBrigade.getId());
                                break;
                            }
                        }
                    }
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("inQueue", inQueue);
            result.put("position", position);
            if (myBrigadeId != null) result.put("brigadeId", myBrigadeId);
            return ResponseEntity.ok(result);
        }
    }

    public static class TgRespondRequest {
        public long chatId;
        public long subjectId;
        public String queueKind;
        public Integer subgroupNum;
        public String action; // "COMPLETE" or "LEAVE"
    }

    @PostMapping("/queue/respond")
    public ResponseEntity<?> tgRespond(@RequestBody TgRespondRequest req) {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Student st = s.createQuery("FROM Student WHERE chatId = :chatId", Student.class)
                    .setParameter("chatId", req.chatId)
                    .setMaxResults(1)
                    .uniqueResult();
            if (st == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "BOT_USER_NOT_REGISTERED"));
            }

            QueueKind qk;
            try {
                qk = QueueKind.valueOf(req.queueKind.toUpperCase(Locale.ROOT));
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(Map.of("error", "BAD_QUEUE_KIND"));
            }

            Subject subject = s.get(Subject.class, req.subjectId);
            if (subject == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "SUBJECT_NOT_FOUND"));

            // Check if user is actually in the queue
            boolean inQueue = QueueEngine.isUserInQueue(s, st, subject, qk, req.subgroupNum);
            if (!inQueue) {
                return ResponseEntity.badRequest().body(Map.of("error", "NOT_IN_QUEUE"));
            }

            // Check if user is at the front of the queue
            int position = -1;
            Student physicalStudent = null;
            Brigade physicalBrigade = null;

            if (subject.getDeliveryType() == DeliveryType.INDIVIDUAL) {
                List<Student> active = QueueEngine.buildActiveIndividualQueue(s, subject, qk, req.subgroupNum);
                for (int i = 0; i < active.size(); i++) {
                    if (Objects.equals(active.get(i).getId(), st.getId())) {
                        position = i;
                        break;
                    }
                }
                if (position != 0) {
                    return ResponseEntity.badRequest().body(Map.of("error", "NOT_YOUR_TURN", "position", position + 1));
                }
                physicalStudent = st;
            } else {
                Brigade myBrigade = QueueEngine.findBrigadeByStudent(s, subject, st, qk, req.subgroupNum);
                if (myBrigade == null) {
                    return ResponseEntity.badRequest().body(Map.of("error", "NOT_IN_BRIGADE"));
                }
                List<Brigade> active = QueueEngine.buildActiveBrigadeQueue(s, subject, qk, req.subgroupNum);
                for (int i = 0; i < active.size(); i++) {
                    if (Objects.equals(active.get(i).getId(), myBrigade.getId())) {
                        position = i;
                        break;
                    }
                }
                if (position != 0) {
                    return ResponseEntity.badRequest().body(Map.of("error", "NOT_YOUR_TURN", "position", position + 1));
                }
                physicalBrigade = myBrigade;
            }

            Transaction tx = s.beginTransaction();

            if ("COMPLETE".equals(req.action)) {
                // Mark as passed - advance queue
                QueueEngine.setLastPassed(s, st, subject, qk, req.subgroupNum);
                tx.commit();

                // Get updated queue for notification
                List<Map<String, Object>> queueInfo = getQueueInfo(s, subject, qk, req.subgroupNum);
                return ResponseEntity.ok(Map.of(
                        "ok", true,
                        "message", "✅ Вы отметили сдачу. Очередь продвинута.",
                        "queue", queueInfo
                ));
            } else if ("LEAVE".equals(req.action)) {
                // User leaves queue
                QueueEngine.leaveQueue(s, st, subject, qk, req.subgroupNum);
                tx.commit();

                // Get updated queue for notification
                List<Map<String, Object>> queueInfo = getQueueInfo(s, subject, qk, req.subgroupNum);
                return ResponseEntity.ok(Map.of(
                        "ok", true,
                        "message", "❌ Вы покинули очередь.",
                        "queue", queueInfo
                ));
            } else {
                tx.rollback();
                return ResponseEntity.badRequest().body(Map.of("error", "INVALID_ACTION"));
            }
        }
    }

    private List<Map<String, Object>> getQueueInfo(Session s, Subject subject, QueueKind queueKind, Integer subgroupNum) {
        if (subject.getDeliveryType() == DeliveryType.INDIVIDUAL) {
            List<Student> items = QueueEngine.buildActiveIndividualQueue(s, subject, queueKind, subgroupNum);
            List<Map<String, Object>> res = new ArrayList<>();
            for (int i = 0; i < items.size(); i++) {
                Student st = items.get(i);
                res.add(Map.of("position", i + 1, "id", st.getId(), "fio", st.getFio(), "subgroup", st.getSubgroup()));
            }
            return res;
        } else {
            List<Brigade> items = QueueEngine.buildActiveBrigadeQueue(s, subject, queueKind, subgroupNum);
            List<Map<String, Object>> res = new ArrayList<>();
            for (int i = 0; i < items.size(); i++) {
                Brigade b = items.get(i);
                res.add(Map.of("position", i + 1, "id", b.getId(), "displayName", b.getDisplayName()));
            }
            return res;
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

