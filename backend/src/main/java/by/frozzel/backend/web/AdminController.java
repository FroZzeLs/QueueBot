package by.frozzel.backend.web;

import by.frozzel.backend.config.HibernateUtil;
import by.frozzel.backend.model.*;
import by.frozzel.backend.security.AuthContext;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static boolean canAdmin(Student me) {
        return me != null && (me.isAdmin() || me.isSuperAdmin());
    }

    private static boolean canSuper(Student me) {
        return me != null && me.isSuperAdmin();
    }

    @GetMapping("/students")
    public ResponseEntity<?> listStudents() {
        Student me = AuthContext.getCurrentStudent();
        if (!canAdmin(me)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "UNAUTHORIZED"));

        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            List<Student> all = s.createQuery("FROM Student ORDER BY fio ASC", Student.class).list();
            List<Map<String, Object>> res = new ArrayList<>();
            for (Student st : all) {
                res.add(Map.of(
                        "id", st.getId(),
                        "fio", st.getFio(),
                        "subgroup", st.getSubgroup(),
                        "telegramTag", st.getTelegramTag(),
                        "isAdmin", st.isAdmin(),
                        "isSuperAdmin", st.isSuperAdmin()
                ));
            }
            return ResponseEntity.ok(res);
        }
    }

    @GetMapping("/subjects")
    public ResponseEntity<?> listSubjects() {
        Student me = AuthContext.getCurrentStudent();
        if (!canAdmin(me)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "UNAUTHORIZED"));

        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            List<Subject> all = s.createQuery("FROM Subject ORDER BY name ASC", Subject.class).list();
            List<Map<String, Object>> res = new ArrayList<>();
            for (Subject subj : all) {
                res.add(Map.of("id", subj.getId(), "name", subj.getName(), "deliveryType", subj.getDeliveryType().name()));
            }
            return ResponseEntity.ok(res);
        }
    }

    public static class AddStudentRequest {
        public String fio;
        public String telegramTag;
        public int subgroup;
    }

    @PostMapping("/students")
    public ResponseEntity<?> addStudent(@RequestBody AddStudentRequest req) {
        Student me = AuthContext.getCurrentStudent();
        if (!canAdmin(me)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "UNAUTHORIZED"));
        if (req.fio == null || req.fio.isBlank()) return ResponseEntity.badRequest().body(Map.of("error", "EMPTY_FIO"));
        if (req.telegramTag == null || req.telegramTag.isBlank()) return ResponseEntity.badRequest().body(Map.of("error", "EMPTY_TAG"));
        String tag = normalizeTag(req.telegramTag);
        if (req.subgroup != 1 && req.subgroup != 2) return ResponseEntity.badRequest().body(Map.of("error", "BAD_SUBGROUP"));

        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = s.beginTransaction();
            Student existing = s.createQuery("FROM Student WHERE telegramTag = :tag", Student.class)
                    .setParameter("tag", tag)
                    .setMaxResults(1)
                    .uniqueResult();
            if (existing != null) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "TAG_ALREADY_EXISTS"));
            }
            Student st = new Student(req.fio.trim(), req.subgroup, tag);
            s.persist(st);
            tx.commit();
            return ResponseEntity.ok(Map.of("ok", true, "studentId", st.getId()));
        }
    }

    @DeleteMapping("/students/{id}")
    public ResponseEntity<?> deleteStudent(@PathVariable("id") long id) {
        Student me = AuthContext.getCurrentStudent();
        if (!canAdmin(me)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "UNAUTHORIZED"));
        if (!canSuper(me) && me.getId() != id) {
            // в старой логике нельзя удалять админов кроме суперадмина; здесь упрощаем
        }

        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = s.beginTransaction();
            Student st = s.get(Student.class, id);
            if (st == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "NOT_FOUND"));
            if (st.isSuperAdmin() && !me.isSuperAdmin()) {
                tx.rollback();
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "CANNOT_DELETE_SUPER_ADMIN"));
            }
            if (st.isAdmin() && !me.isSuperAdmin()) {
                tx.rollback();
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "CANNOT_DELETE_ADMIN"));
            }
            s.remove(st);
            tx.commit();
            return ResponseEntity.ok(Map.of("ok", true));
        }
    }

    public static class UpdateStudentSubgroupRequest {
        public int subgroup;
    }

    @PatchMapping("/students/{id}/subgroup")
    public ResponseEntity<?> updateStudentSubgroup(@PathVariable("id") long id, @RequestBody UpdateStudentSubgroupRequest req) {
        Student me = AuthContext.getCurrentStudent();
        if (!canAdmin(me)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "UNAUTHORIZED"));
        if (req.subgroup != 1 && req.subgroup != 2) return ResponseEntity.badRequest().body(Map.of("error", "BAD_SUBGROUP"));

        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = s.beginTransaction();
            Student st = s.get(Student.class, id);
            if (st == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "NOT_FOUND"));
            st.setSubgroup(req.subgroup);
            s.merge(st);
            tx.commit();
            return ResponseEntity.ok(Map.of("ok", true));
        }
    }

    public static class UpdateStudentRequest {
        public String fio;
        public String telegramTag;
        public Integer subgroup;
    }

    @PutMapping("/students/{id}")
    public ResponseEntity<?> updateStudent(@PathVariable("id") long id, @RequestBody UpdateStudentRequest req) {
        Student me = AuthContext.getCurrentStudent();
        if (!canAdmin(me)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "UNAUTHORIZED"));
        
        if (req.fio != null && req.fio.isBlank()) return ResponseEntity.badRequest().body(Map.of("error", "EMPTY_FIO"));
        if (req.subgroup != null && (req.subgroup != 1 && req.subgroup != 2)) 
            return ResponseEntity.badRequest().body(Map.of("error", "BAD_SUBGROUP"));

        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = s.beginTransaction();
            Student st = s.get(Student.class, id);
            if (st == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "NOT_FOUND"));
            
            // Проверка уникальности telegramTag если он меняется
            if (req.telegramTag != null && !req.telegramTag.isBlank()) {
                String newTag = normalizeTag(req.telegramTag);
                if (!newTag.equals(st.getTelegramTag())) {
                    Student existing = s.createQuery("FROM Student WHERE telegramTag = :tag", Student.class)
                            .setParameter("tag", newTag)
                            .setMaxResults(1)
                            .uniqueResult();
                    if (existing != null && !existing.getId().equals(id)) {
                        tx.rollback();
                        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "TAG_ALREADY_EXISTS"));
                    }
                }
                st.setTelegramTag(newTag);
            }
            
            if (req.fio != null && !req.fio.isBlank()) {
                st.setFio(req.fio.trim());
            }
            if (req.subgroup != null) {
                st.setSubgroup(req.subgroup);
            }
            
            s.merge(st);
            tx.commit();
            return ResponseEntity.ok(Map.of("ok", true));
        }
    }

    @PutMapping("/me")
    public ResponseEntity<?> updateMe(@RequestBody UpdateStudentRequest req) {
        Student me = AuthContext.getCurrentStudent();
        if (me == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "UNAUTHORIZED"));
        
        if (req.fio != null && req.fio.isBlank()) return ResponseEntity.badRequest().body(Map.of("error", "EMPTY_FIO"));
        if (req.subgroup != null && (req.subgroup != 1 && req.subgroup != 2)) 
            return ResponseEntity.badRequest().body(Map.of("error", "BAD_SUBGROUP"));

        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = s.beginTransaction();
            Student st = s.get(Student.class, me.getId());
            if (st == null) {
                tx.rollback();
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "NOT_FOUND"));
            }
            
            // Проверка уникальности telegramTag если он меняется
            if (req.telegramTag != null && !req.telegramTag.isBlank()) {
                String newTag = normalizeTag(req.telegramTag);
                if (!newTag.equals(st.getTelegramTag())) {
                    Student existing = s.createQuery("FROM Student WHERE telegramTag = :tag", Student.class)
                            .setParameter("tag", newTag)
                            .setMaxResults(1)
                            .uniqueResult();
                    if (existing != null && !existing.getId().equals(st.getId())) {
                        tx.rollback();
                        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "TAG_ALREADY_EXISTS"));
                    }
                }
                st.setTelegramTag(newTag);
            }
            
            if (req.fio != null && !req.fio.isBlank()) {
                st.setFio(req.fio.trim());
            }
            if (req.subgroup != null) {
                st.setSubgroup(req.subgroup);
            }
            
            s.merge(st);
            tx.commit();
            return ResponseEntity.ok(Map.of("ok", true));
        }
    }

    public static class SetAdminRequest {
        public long studentId;
        public boolean enabled;
    }

    @PostMapping("/roles/admin")
    public ResponseEntity<?> setAdmin(@RequestBody SetAdminRequest req) {
        Student me = AuthContext.getCurrentStudent();
        if (!canSuper(me)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "NO_SUPER_RIGHTS"));

        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = s.beginTransaction();
            Student st = s.get(Student.class, req.studentId);
            if (st == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "NOT_FOUND"));
            st.setAdmin(req.enabled);
            s.merge(st);
            tx.commit();
            return ResponseEntity.ok(Map.of("ok", true));
        }
    }

    public static class AddSubjectRequest {
        public String name;
        public DeliveryType deliveryType;
    }

    @PostMapping("/subjects")
    public ResponseEntity<?> addSubject(@RequestBody AddSubjectRequest req) {
        Student me = AuthContext.getCurrentStudent();
        if (!canAdmin(me)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "UNAUTHORIZED"));
        if (req.name == null || req.name.isBlank()) return ResponseEntity.badRequest().body(Map.of("error", "EMPTY_NAME"));

        DeliveryType dt = req.deliveryType != null ? req.deliveryType : DeliveryType.INDIVIDUAL;
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = s.beginTransaction();
            Subject existing = s.createQuery("FROM Subject WHERE name = :n", Subject.class)
                    .setParameter("n", req.name.trim())
                    .setMaxResults(1)
                    .uniqueResult();
            if (existing != null) {
                tx.rollback();
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "SUBJECT_EXISTS"));
            }
            Subject subj = new Subject(req.name.trim(), dt);
            s.persist(subj);
            tx.commit();
            return ResponseEntity.ok(Map.of("ok", true, "subjectId", subj.getId()));
        }
    }

    @DeleteMapping("/subjects/{id}")
    public ResponseEntity<?> deleteSubject(@PathVariable("id") long id) {
        Student me = AuthContext.getCurrentStudent();
        if (!canAdmin(me)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "UNAUTHORIZED"));

        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = s.beginTransaction();
            Subject subj = s.get(Subject.class, id);
            if (subj == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "NOT_FOUND"));

            // очистка зависимостей (упрощённо)
            s.createMutationQuery("DELETE FROM TemporarySwap t WHERE t.subject.id = :sid").setParameter("sid", id).executeUpdate();
            s.createMutationQuery("DELETE FROM SwapRequest r WHERE r.subject.id = :sid").setParameter("sid", id).executeUpdate();
            s.createMutationQuery("DELETE FROM SkippedStudent ss WHERE ss.subject.id = :sid").setParameter("sid", id).executeUpdate();
            s.createMutationQuery("DELETE FROM SkippedBrigade sb WHERE sb.subject.id = :sid").setParameter("sid", id).executeUpdate();
            s.createMutationQuery("DELETE FROM QueueState qs WHERE qs.subject.id = :sid").setParameter("sid", id).executeUpdate();
            s.createMutationQuery("DELETE FROM BrigadeMember bm WHERE bm.brigade.subject.id = :sid").setParameter("sid", id).executeUpdate();
            s.createMutationQuery("DELETE FROM Brigade b WHERE b.subject.id = :sid").setParameter("sid", id).executeUpdate();

            s.remove(subj);
            tx.commit();
            return ResponseEntity.ok(Map.of("ok", true));
        }
    }

    public static class BrigadesCreateRequest {
        public List<List<Long>> brigades;
    }

    @PostMapping("/subjects/{subjectId}/brigades")
    public ResponseEntity<?> createBrigades(@PathVariable("subjectId") long subjectId, @RequestBody BrigadesCreateRequest req) {
        Student me = AuthContext.getCurrentStudent();
        if (!canAdmin(me)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "UNAUTHORIZED"));

        if (req.brigades == null || req.brigades.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "EMPTY_BRIGADES"));

        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = s.beginTransaction();
            Subject subj = s.get(Subject.class, subjectId);
            if (subj == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "SUBJECT_NOT_FOUND"));
            if (subj.getDeliveryType() != DeliveryType.BRIGADE) {
                tx.rollback();
                return ResponseEntity.badRequest().body(Map.of("error", "SUBJECT_IS_NOT_BRIGADE"));
            }

            List<Student> allStudents = s.createQuery("FROM Student", Student.class).list();
            Set<Long> allStudentIds = new HashSet<>();
            for (Student st : allStudents) allStudentIds.add(st.getId());

            Set<Long> used = new HashSet<>();
            for (List<Long> group : req.brigades) {
                for (Long id : group) used.add(id);
            }
            if (!used.equals(allStudentIds)) {
                tx.rollback();
                return ResponseEntity.badRequest().body(Map.of("error", "ALL_STUDENTS_MUST_BE_ASSIGNED_TO_BRIGADES"));
            }

            // проверить что нет повторов
            Set<Long> duplicates = new HashSet<>();
            for (List<Long> group : req.brigades) {
                for (Long id : group) {
                    if (!duplicates.add(id)) {
                        tx.rollback();
                        return ResponseEntity.badRequest().body(Map.of("error", "DUPLICATED_STUDENT_IN_BRIGADES"));
                    }
                }
            }

            // очистить старые бригады и очереди для предмета
            s.createMutationQuery("DELETE FROM TemporarySwap t WHERE t.subject.id = :sid").setParameter("sid", subjectId).executeUpdate();
            s.createMutationQuery("DELETE FROM SwapRequest r WHERE r.subject.id = :sid").setParameter("sid", subjectId).executeUpdate();
            s.createMutationQuery("DELETE FROM SkippedBrigade sb WHERE sb.subject.id = :sid").setParameter("sid", subjectId).executeUpdate();
            s.createMutationQuery("DELETE FROM QueueState qs WHERE qs.subject.id = :sid").setParameter("sid", subjectId).executeUpdate();
            s.createMutationQuery("DELETE FROM BrigadeMember bm WHERE bm.brigade.subject.id = :sid").setParameter("sid", subjectId).executeUpdate();
            s.createMutationQuery("DELETE FROM Brigade b WHERE b.subject.id = :sid").setParameter("sid", subjectId).executeUpdate();

            for (List<Long> memberIds : req.brigades) {
                memberIds = memberIds == null ? Collections.emptyList() : new ArrayList<>(memberIds);
                if (memberIds.isEmpty()) continue;

                List<Student> members = s.createQuery("FROM Student WHERE id IN (:ids)", Student.class)
                        .setParameter("ids", memberIds)
                        .list();

                members.sort(Comparator.comparing(Student::getFio));
                List<String> lastNames = new ArrayList<>();
                for (Student st : members) {
                    String[] parts = st.getFio().trim().split("\\s+");
                    lastNames.add(parts[0]);
                }
                String displayName = String.join(", ", lastNames);
                String sortKey = displayName;

                Brigade brigade = new Brigade(subj, displayName, sortKey);
                s.persist(brigade);
                s.flush();

                for (Long sid : memberIds) {
                    Student member = s.get(Student.class, sid);
                    BrigadeMember bm = new BrigadeMember();
                    bm.setBrigade(brigade);
                    bm.setStudent(member);
                    s.persist(bm);
                }
            }

            tx.commit();
            return ResponseEntity.ok(Map.of("ok", true));
        }
    }

    private static String normalizeTag(String raw) {
        if (raw == null) return null;
        String t = raw.trim();
        if (t.startsWith("@")) t = t.substring(1);
        return t;
    }
}

