package by.frozzel.backend.web;

import by.frozzel.backend.config.HibernateUtil;
import by.frozzel.backend.model.*;
import by.frozzel.backend.security.AuthContext;
import by.frozzel.backend.service.QueueEngine;
import jakarta.validation.Valid;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@RestController
@RequestMapping("/api")
public class QueueController {
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    @GetMapping("/subjects")
    public Map<String, List<Map<String, Object>>> getSubjects() {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            List<Subject> subjects = s.createQuery("FROM Subject ORDER BY name ASC", Subject.class).list();
            List<Map<String, Object>> res = new ArrayList<>();
            for (Subject subj : subjects) {
                res.add(Map.of(
                        "id", subj.getId(),
                        "name", subj.getName(),
                        "deliveryType", subj.getDeliveryType().name()
                ));
            }
            return Map.of("subjects", res);
        }
    }

    @GetMapping("/queue/active")
    public ResponseEntity<?> getActiveQueue(@RequestParam long subjectId,
                                             @RequestParam String queueKind,
                                             @RequestParam(required = false) Integer subgroupNum) {
        QueueKind qk;
        try {
            qk = QueueKind.valueOf(queueKind.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "BAD_QUEUE_KIND"));
        }

        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Subject subject = s.get(Subject.class, subjectId);
            if (subject == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "SUBJECT_NOT_FOUND"));

            if (subject.getDeliveryType() == DeliveryType.INDIVIDUAL) {
                List<Student> items = QueueEngine.buildActiveIndividualQueue(s, subject, qk, subgroupNum);
                List<Map<String, Object>> res = new ArrayList<>();
                for (int i = 0; i < items.size(); i++) {
                    Student st = items.get(i);
                    res.add(Map.of("position", i + 1, "id", st.getId(), "fio", st.getFio(), "subgroup", st.getSubgroup()));
                }
                return ResponseEntity.ok(Map.of("queueType", qk.name(), "items", res));
            }

            List<Brigade> items = QueueEngine.buildActiveBrigadeQueue(s, subject, qk, subgroupNum);
            List<Map<String, Object>> res = new ArrayList<>();
            for (int i = 0; i < items.size(); i++) {
                Brigade b = items.get(i);
                res.add(Map.of("position", i + 1, "id", b.getId(), "displayName", b.getDisplayName()));
            }
            return ResponseEntity.ok(Map.of("queueType", qk.name(), "items", res));
        }
    }

    public static class LeaveQueueRequest {
        public long subjectId;
        public String queueKind;
        public Integer subgroupNum;
    }

    @PostMapping("/queue/leave")
    public ResponseEntity<?> leaveQueue(@RequestBody @Valid LeaveQueueRequest req) {
        Student me = AuthContext.getCurrentStudent();
        if (me == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "UNAUTHORIZED"));

        QueueKind qk;
        try {
            qk = QueueKind.valueOf(req.queueKind.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "BAD_QUEUE_KIND"));
        }

        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Subject subject = s.get(Subject.class, req.subjectId);
            if (subject == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "SUBJECT_NOT_FOUND"));

            QueueEngine.leaveQueue(s, me, subject, qk, req.subgroupNum);
            notifyQueueUpdate(subject.getId(), qk.name(), req.subgroupNum);
            return ResponseEntity.ok(Map.of("state", "LEFT"));
        }
    }

    public static class JoinQueueRequest {
        public long subjectId;
        public String queueKind;
        public Integer subgroupNum;
    }

    @PostMapping("/queue/join")
    public ResponseEntity<?> joinQueue(@RequestBody @Valid JoinQueueRequest req) {
        Student me = AuthContext.getCurrentStudent();
        if (me == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "UNAUTHORIZED"));

        QueueKind qk;
        try {
            qk = QueueKind.valueOf(req.queueKind.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "BAD_QUEUE_KIND"));
        }

        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Subject subject = s.get(Subject.class, req.subjectId);
            if (subject == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "SUBJECT_NOT_FOUND"));

            QueueEngine.joinQueue(s, me, subject, qk, req.subgroupNum);
            notifyQueueUpdate(subject.getId(), qk.name(), req.subgroupNum);
            return ResponseEntity.ok(Map.of("state", "JOINED"));
        }
    }

    public static class QueueStatusRequest {
        public long subjectId;
        public String queueKind;
        public Integer subgroupNum;
    }

    @PostMapping("/queue/status")
    public ResponseEntity<?> getQueueStatus(@RequestBody @Valid QueueStatusRequest req) {
        Student me = AuthContext.getCurrentStudent();
        if (me == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "UNAUTHORIZED"));

        QueueKind qk;
        try {
            qk = QueueKind.valueOf(req.queueKind.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "BAD_QUEUE_KIND"));
        }

        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Subject subject = s.get(Subject.class, req.subjectId);
            if (subject == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "SUBJECT_NOT_FOUND"));

            boolean inQueue = QueueEngine.isUserInQueue(s, me, subject, qk, req.subgroupNum);
            return ResponseEntity.ok(Map.of("inQueue", inQueue));
        }
    }

    public static class MarkLastPassedRequest {
        public long subjectId;
        public String queueKind;
        public Integer subgroupNum;
        // физический студент, который фактически сдал (для индивидуального)
        public Long physicalStudentId;
        // бригада, которая сдала (для бригадного)
        public Long brigadeId;
    }

    @PostMapping("/queue/mark-last-passed")
    public ResponseEntity<?> markLastPassed(@RequestBody @Valid MarkLastPassedRequest req) {
        Student me = AuthContext.getCurrentStudent();
        if (me == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "UNAUTHORIZED"));
        if (!(me.isAdmin() || me.isSuperAdmin())) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "NO_ADMIN_RIGHTS"));

        QueueKind qk;
        try {
            qk = QueueKind.valueOf(req.queueKind.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "BAD_QUEUE_KIND"));
        }

        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Subject subject = s.get(Subject.class, req.subjectId);
            if (subject == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "SUBJECT_NOT_FOUND"));
            
            if (subject.getDeliveryType() == DeliveryType.INDIVIDUAL) {
                if (req.physicalStudentId == null) return ResponseEntity.badRequest().body(Map.of("error", "NO_PHYSICAL_STUDENT"));
                Student physical = s.get(Student.class, req.physicalStudentId);
                if (physical == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "PHYSICAL_NOT_FOUND"));
                QueueEngine.setLastPassed(s, physical, subject, qk, req.subgroupNum);
            } else {
                if (req.brigadeId == null) return ResponseEntity.badRequest().body(Map.of("error", "NO_BRIGADE_ID"));
                Brigade brigade = s.get(Brigade.class, req.brigadeId);
                if (brigade == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "BRIGADE_NOT_FOUND"));
                // Find any student from the brigade to use as physical student
                Student anyMember = s.createQuery(
                        "SELECT bm.student FROM BrigadeMember bm WHERE bm.brigade.id = :bid ORDER BY bm.student.fio", Student.class)
                        .setParameter("bid", req.brigadeId)
                        .setMaxResults(1)
                        .uniqueResult();
                if (anyMember == null) return ResponseEntity.badRequest().body(Map.of("error", "BRIGADE_HAS_NO_MEMBERS"));
                QueueEngine.setLastPassed(s, anyMember, subject, qk, req.subgroupNum);
            }
            notifyQueueUpdate(subject.getId(), qk.name(), req.subgroupNum);
            return ResponseEntity.ok(Map.of("state", "MARKED"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    public static class SelfMarkRequest {
        public long subjectId;
        public String queueKind;
        public Integer subgroupNum;
    }

    @PostMapping("/queue/self-mark")
    public ResponseEntity<?> selfMark(@RequestBody @Valid SelfMarkRequest req) {
        Student me = AuthContext.getCurrentStudent();
        if (me == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "UNAUTHORIZED"));

        QueueKind qk;
        try {
            qk = QueueKind.valueOf(req.queueKind.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "BAD_QUEUE_KIND"));
        }

        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Subject subject = s.get(Subject.class, req.subjectId);
            if (subject == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "SUBJECT_NOT_FOUND"));

            if (subject.getDeliveryType() == DeliveryType.INDIVIDUAL) {
                List<Student> active = QueueEngine.buildActiveIndividualQueue(s, subject, qk, req.subgroupNum);
                if (active.isEmpty()) {
                    return ResponseEntity.badRequest().body(Map.of("error", "QUEUE_IS_EMPTY"));
                }
                Student first = active.get(0);
                if (!Objects.equals(first.getId(), me.getId())) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "NOT_FIRST_IN_QUEUE"));
                }
                QueueEngine.setLastPassed(s, me, subject, qk, req.subgroupNum);
            } else {
                Brigade myBrigade = QueueEngine.findBrigadeByStudent(s, subject, me, qk, req.subgroupNum);
                if (myBrigade == null) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "NOT_IN_BRIGADE_QUEUE"));
                }
                List<Brigade> active = QueueEngine.buildActiveBrigadeQueue(s, subject, qk, req.subgroupNum);
                if (active.isEmpty()) {
                    return ResponseEntity.badRequest().body(Map.of("error", "QUEUE_IS_EMPTY"));
                }
                Brigade first = active.get(0);
                if (!Objects.equals(first.getId(), myBrigade.getId())) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "NOT_FIRST_IN_QUEUE"));
                }
                // Find any member of the brigade to use as physical student
                Student anyMember = s.createQuery(
                                "SELECT bm.student FROM BrigadeMember bm WHERE bm.brigade.id = :bid ORDER BY bm.student.fio", Student.class)
                        .setParameter("bid", myBrigade.getId())
                        .setMaxResults(1)
                        .uniqueResult();
                if (anyMember == null) {
                    return ResponseEntity.badRequest().body(Map.of("error", "BRIGADE_HAS_NO_MEMBERS"));
                }
                QueueEngine.setLastPassed(s, anyMember, subject, qk, req.subgroupNum);
            }
            notifyQueueUpdate(subject.getId(), qk.name(), req.subgroupNum);
            return ResponseEntity.ok(Map.of("state", "SELF_MARKED"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    public static class CreateSwapRequestBody {
        public long subjectId;
        public String queueKind;
        public Integer subgroupNum;

        // индивидуальный
        public Long targetStudentId;

        // бригадный
        public Long targetBrigadeId;
        public Long targetNotifyStudentId;
    }

    @PostMapping("/swap-requests")
    public ResponseEntity<?> createSwapRequest(@RequestBody @Valid CreateSwapRequestBody req) {
        Student me = AuthContext.getCurrentStudent();
        if (me == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "UNAUTHORIZED"));

        QueueKind qk;
        try {
            qk = QueueKind.valueOf(req.queueKind.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "BAD_QUEUE_KIND"));
        }

        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Subject subject = s.get(Subject.class, req.subjectId);
            if (subject == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "SUBJECT_NOT_FOUND"));

            String tgBaseUrl = System.getenv("TG_BOT_BASE_URL");
            String tgSecret = System.getenv("TG_BOT_INTERNAL_SECRET");
            RestTemplate restTemplate = tgBaseUrl != null ? new RestTemplate() : null;

            if (subject.getDeliveryType() == DeliveryType.INDIVIDUAL) {
                if (req.targetStudentId == null) return ResponseEntity.badRequest().body(Map.of("error", "NO_TARGET_STUDENT"));
                Long requestId = QueueEngine.createSwapRequest(s, me, subject, qk, req.subgroupNum, req.targetStudentId, req.targetStudentId, false);

                if (restTemplate != null) {
                    SwapRequest swap = s.get(SwapRequest.class, requestId);
                    if (swap != null && swap.getTargetNotifyStudent() != null && swap.getTargetNotifyStudent().getChatId() != null) {
                        Map<String, Object> body = Map.of(
                                "targetChatId", swap.getTargetNotifyStudent().getChatId(),
                                "requestId", requestId,
                                "subjectName", subject.getName(),
                                "deliveryTypeLabel", "Индивидуальный",
                                "queueTypeLabel", qk == QueueKind.COMMON ? "Общая очередь" : "Подгрупповая очередь",
                                "subgroupNum", req.subgroupNum,
                                "requesterName", me.getFio()
                        );
                        HttpHeaders headers = new HttpHeaders();
                        headers.setContentType(MediaType.APPLICATION_JSON);
                        if (tgSecret != null && !tgSecret.isBlank()) headers.add("X-TG-BOT-SECRET", tgSecret);
                        restTemplate.postForEntity(tgBaseUrl + "/api/tg/notify/swap-request", new HttpEntity<>(body, headers), Map.class);
                    }
                }
                return ResponseEntity.ok(Map.of("requestId", requestId, "deliveryType", "INDIVIDUAL"));
            }

            if (req.targetBrigadeId == null || req.targetNotifyStudentId == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "NO_TARGET_BRIGADE_OR_NOTIFY_STUDENT"));
            }
            Long requestId = QueueEngine.createSwapRequest(s, me, subject, qk, req.subgroupNum, req.targetBrigadeId, req.targetNotifyStudentId, true);

            if (restTemplate != null) {
                SwapRequest swap = s.get(SwapRequest.class, requestId);
                if (swap != null && swap.getTargetNotifyStudent() != null && swap.getTargetNotifyStudent().getChatId() != null) {
                    Map<String, Object> body = Map.of(
                            "targetChatId", swap.getTargetNotifyStudent().getChatId(),
                            "requestId", requestId,
                            "subjectName", subject.getName(),
                            "deliveryTypeLabel", "Бригадный",
                            "queueTypeLabel", qk == QueueKind.COMMON ? "Общая очередь" : "Подгрупповая очередь",
                            "subgroupNum", req.subgroupNum,
                            "requesterName", me.getFio()
                    );
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    if (tgSecret != null && !tgSecret.isBlank()) headers.add("X-TG-BOT-SECRET", tgSecret);
                    restTemplate.postForEntity(tgBaseUrl + "/api/tg/notify/swap-request", new HttpEntity<>(body, headers), Map.class);
                }
            }

            return ResponseEntity.ok(Map.of("requestId", requestId, "deliveryType", "BRIGADE"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/queue/my-brigade")
    public ResponseEntity<?> getMyBrigade(@RequestParam long subjectId, @RequestParam String queueKind, @RequestParam(required = false) Integer subgroupNum) {
        Student me = AuthContext.getCurrentStudent();
        if (me == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "UNAUTHORIZED"));
        
        QueueKind qk;
        try {
            qk = QueueKind.valueOf(queueKind.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "BAD_QUEUE_KIND"));
        }
        
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Subject subject = s.get(Subject.class, subjectId);
            if (subject == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "SUBJECT_NOT_FOUND"));
            
            if (subject.getDeliveryType() != DeliveryType.BRIGADE) {
                return ResponseEntity.ok(Map.of("brigadeId", null));
            }
            
            Brigade myBrigade = QueueEngine.findBrigadeByStudent(s, subject, me, qk, subgroupNum);
            return ResponseEntity.ok(Map.of("brigadeId", myBrigade != null ? myBrigade.getId() : null));
        }
    }

    @GetMapping("/queue/stream")
    public SseEmitter streamQueueUpdates() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emitters.add(emitter);
        
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError((e) -> emitters.remove(emitter));
        
        return emitter;
    }

    private void notifyQueueUpdate(long subjectId, String queueKind, Integer subgroupNum) {
        String eventData = String.format("{\"subjectId\":%d,\"queueKind\":\"%s\",\"subgroupNum\":%s}",
                subjectId, queueKind, subgroupNum != null ? subgroupNum : "null");
        
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(eventData);
            } catch (Exception e) {
                emitters.remove(emitter);
            }
        }
    }
}

