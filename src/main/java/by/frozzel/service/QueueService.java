package by.frozzel.service;

import by.frozzel.config.HibernateUtil;
import by.frozzel.model.*;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;

import java.util.*;

public class QueueService {

    // --- ПОИСК СТУДЕНТОВ ---
    public List<Student> findStudents(String partialName) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "FROM Student WHERE lower(fio) LIKE lower(:name) ORDER BY fio";
            return session.createQuery(hql, Student.class)
                    .setParameter("name", "%" + partialName + "%")
                    .list();
        }
    }

    // --- УПРАВЛЕНИЕ СТУДЕНТАМИ ---
    public List<Student> getAllStudents() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery("FROM Student ORDER BY fio", Student.class).list();
        }
    }

    public void addStudent(String fio, int subgroup, String tag) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            String cleanTag = tag.startsWith("@") ? tag.substring(1) : tag;
            Student s = new Student(fio, subgroup, cleanTag);
            session.persist(s);
            tx.commit();
        }
    }

    public int removeStudent(String fio, Student requester) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();

            Student target = session.createQuery("FROM Student WHERE fio = :fio", Student.class)
                    .setParameter("fio", fio).uniqueResult();

            if (target != null) {
                // Если удаляемый - админ, а тот кто удаляет - не суперадмин -> запрет
                if (target.isAdmin() && !requester.isSuperAdmin()) {
                    return -1;
                }

                Long sId = target.getId();

                // Очистка всех зависимостей
                session.createMutationQuery("DELETE FROM SwapRequest WHERE requester.id = :id OR target.id = :id")
                        .setParameter("id", sId).executeUpdate();
                session.createMutationQuery("DELETE FROM TemporarySwap WHERE student1.id = :id OR student2.id = :id")
                        .setParameter("id", sId).executeUpdate();
                session.createMutationQuery("DELETE FROM SkippedStudent WHERE student.id = :id")
                        .setParameter("id", sId).executeUpdate();
                session.createMutationQuery("UPDATE QueueState SET lastPassedStudent = null WHERE lastPassedStudent.id = :id")
                        .setParameter("id", sId).executeUpdate();

                session.remove(target);
                tx.commit();
                return 1;
            }
            return 0;
        }
    }

    public boolean makeAdmin(String fio) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            Student s = session.createQuery("FROM Student WHERE fio = :fio", Student.class)
                    .setParameter("fio", fio).uniqueResult();
            if (s != null) {
                s.setAdmin(true);
                session.merge(s);
                tx.commit();
                return true;
            }
            return false;
        }
    }

    public void createOrUpdateSuperAdmin(String fio, Long chatId, String tag) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            Student s = session.createQuery("FROM Student WHERE fio=:f", Student.class)
                    .setParameter("f", fio).uniqueResult();

            if (s == null) {
                s = session.createQuery("FROM Student WHERE chatId=:c", Student.class)
                        .setParameter("c", chatId).uniqueResult();
            }

            String cleanTag = tag.startsWith("@") ? tag.substring(1) : tag;
            if (s == null) {
                s = new Student(fio, 1, cleanTag);
                s.setChatId(chatId);
            } else {
                s.setFio(fio);
            }
            s.setAdmin(true);
            s.setSuperAdmin(true);
            session.merge(s);
            tx.commit();
        }
    }

    public Student getStudentByChatId(Long chatId) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery("FROM Student WHERE chatId = :cid", Student.class)
                    .setParameter("cid", chatId)
                    .setMaxResults(1)
                    .uniqueResult();
        }
    }

    public Student getStudentByFio(String fio) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery("FROM Student WHERE fio = :fio", Student.class)
                    .setParameter("fio", fio).uniqueResult();
        }
    }

    public void registerChatId(String tag, Long chatId) {
        if (tag == null || tag.isEmpty()) return;
        String cleanTag = tag.startsWith("@") ? tag.substring(1) : tag;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            List<Student> students = session.createQuery("FROM Student", Student.class).list();
            for(Student s : students) {
                String dbTag = s.getTelegramTag();
                if (dbTag != null && (dbTag.equalsIgnoreCase(cleanTag) || dbTag.equalsIgnoreCase("@" + cleanTag))) {
                    if (s.getChatId() == null || !s.getChatId().equals(chatId)) {
                        s.setChatId(chatId);
                        session.merge(s);
                    }
                }
            }
            tx.commit();
        }
    }

    // --- УПРАВЛЕНИЕ ПРЕДМЕТАМИ ---
    public List<Subject> getAllSubjects() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery("FROM Subject ORDER BY name", Subject.class).list();
        }
    }

    public Subject getSubjectById(Long id) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.get(Subject.class, id);
        }
    }

    public void addSubject(String name) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            Long count = session.createQuery("SELECT count(s) FROM Subject s WHERE s.name = :n", Long.class)
                    .setParameter("n", name).uniqueResult();
            if (count == 0) {
                session.persist(new Subject(name));
            }
            tx.commit();
        }
    }

    public void removeSubject(String name) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            Subject s = session.createQuery("FROM Subject WHERE name=:n", Subject.class)
                    .setParameter("n", name).uniqueResult();
            if(s != null) {
                Long id = s.getId();
                session.createMutationQuery("DELETE FROM QueueState WHERE subject.id=:id").setParameter("id", id).executeUpdate();
                session.createMutationQuery("DELETE FROM TemporarySwap WHERE subject.id=:id").setParameter("id", id).executeUpdate();
                session.createMutationQuery("DELETE FROM SkippedStudent WHERE subject.id=:id").setParameter("id", id).executeUpdate();
                session.remove(s);
                tx.commit();
            }
        }
    }

    // --- ЛОГИКА ОЧЕРЕДИ ---

    public void leaveQueue(Long studentId, Long subjectId, boolean isGroup) throws Exception {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();

            Long count = session.createQuery(
                            "SELECT count(s) FROM SkippedStudent s WHERE s.student.id=:sid AND s.subject.id=:subid AND s.isGroupQueue=:ig", Long.class)
                    .setParameter("sid", studentId)
                    .setParameter("subid", subjectId)
                    .setParameter("ig", isGroup)
                    .uniqueResult();

            if (count > 0) throw new Exception("Вы уже снялись с этой очереди.");

            Student st = session.get(Student.class, studentId);
            Subject sb = session.get(Subject.class, subjectId);
            session.persist(new SkippedStudent(st, sb, isGroup));

            // Удаляем активные свапы ушедшего
            session.createMutationQuery("DELETE FROM TemporarySwap WHERE (student1.id=:sid OR student2.id=:sid) AND subject.id=:subid AND isGroupQueue=:ig")
                    .setParameter("sid", studentId)
                    .setParameter("subid", subjectId)
                    .setParameter("ig", isGroup)
                    .executeUpdate();

            tx.commit();
        }
    }

    // Приватный метод для построения списка объектов (используется и для вывода, и для логики обмена)
    private List<Student> getActiveQueueList(Session session, Long subjectId, boolean isGroup, int subgroupNum) {
        // 1. Базовый список
        StringBuilder hql = new StringBuilder("FROM Student s ");
        if (!isGroup) hql.append("WHERE s.subgroup = :sg ");
        hql.append("ORDER BY s.fio");

        Query<Student> q = session.createQuery(hql.toString(), Student.class);
        if(!isGroup) q.setParameter("sg", subgroupNum);
        List<Student> list = q.list();

        if (list.isEmpty()) return new ArrayList<>();

        // 2. Ротация
        List<Student> rotated = new ArrayList<>(list);
        QueueState state = session.createQuery("FROM QueueState WHERE subject.id=:sid AND isGroupQueue=:ig", QueueState.class)
                .setParameter("sid", subjectId)
                .setParameter("ig", isGroup)
                .uniqueResult();

        if (state != null && state.getLastPassedStudent() != null) {
            Long lastId = state.getLastPassedStudent().getId();
            int splitIndex = -1;
            for(int i=0; i<rotated.size(); i++) {
                if (rotated.get(i).getId().equals(lastId)) {
                    splitIndex = i;
                    break;
                }
            }
            if (splitIndex != -1 && splitIndex < rotated.size() - 1) {
                List<Student> passed = new ArrayList<>(rotated.subList(0, splitIndex + 1));
                List<Student> active = new ArrayList<>(rotated.subList(splitIndex + 1, rotated.size()));
                rotated.clear();
                rotated.addAll(active);
                rotated.addAll(passed);
            }
        }

        // 3. Фильтр Skipped
        List<Long> skippedIds = session.createQuery(
                        "SELECT s.student.id FROM SkippedStudent s WHERE s.subject.id=:sid AND s.isGroupQueue=:ig", Long.class)
                .setParameter("sid", subjectId)
                .setParameter("ig", isGroup)
                .list();

        rotated.removeIf(s -> skippedIds.contains(s.getId()));

        // 4. Свапы
        List<TemporarySwap> swaps = session.createQuery(
                        "FROM TemporarySwap WHERE subject.id=:sid AND isGroupQueue=:ig", TemporarySwap.class)
                .setParameter("sid", subjectId)
                .setParameter("ig", isGroup)
                .list();

        for (TemporarySwap swap : swaps) {
            int i1 = -1, i2 = -1;
            for(int i=0; i<rotated.size(); i++) {
                if(rotated.get(i).getId().equals(swap.getStudent1().getId())) i1 = i;
                if(rotated.get(i).getId().equals(swap.getStudent2().getId())) i2 = i;
            }
            if(i1 != -1 && i2 != -1) Collections.swap(rotated, i1, i2);
        }
        return rotated;
    }

    public List<String> getQueue(Long subjectId, boolean isGroup, int subgroupNum) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            List<Student> active = getActiveQueueList(session, subjectId, isGroup, subgroupNum);
            List<String> res = new ArrayList<>();
            for(int i=0; i<active.size(); i++) {
                res.add((i+1) + ". " + active.get(i).getFio());
            }
            return res;
        }
    }

    public void setLastPassed(Long subjectId, boolean isGroup, String fio) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();

            Student physical = session.createQuery("FROM Student WHERE fio=:f", Student.class)
                    .setParameter("f", fio).uniqueResult();

            if (physical == null) throw new IllegalArgumentException("Студент не найден");

            // --- ВОССОЗДАНИЕ ОЧЕРЕДИ ДЛЯ ПОИСКА "ЭФФЕКТИВНОГО" СТУДЕНТА ---

            // Шаг А: База
            StringBuilder hql = new StringBuilder("FROM Student s ");
            if (!isGroup) hql.append("WHERE s.subgroup = :sg ");
            hql.append("ORDER BY s.fio");
            Query<Student> q = session.createQuery(hql.toString(), Student.class);
            if(!isGroup) q.setParameter("sg", physical.getSubgroup());
            List<Student> original = q.list();

            // Шаг Б: Ротация (старая)
            QueueState oldState = session.createQuery(
                            "FROM QueueState WHERE subject.id=:sid AND isGroupQueue=:ig", QueueState.class)
                    .setParameter("sid", subjectId)
                    .setParameter("ig", isGroup)
                    .uniqueResult();

            List<Student> rotated = new ArrayList<>(original);
            if (oldState != null && oldState.getLastPassedStudent() != null) {
                Long lastId = oldState.getLastPassedStudent().getId();
                int splitIndex = -1;
                for(int i=0; i<rotated.size(); i++) {
                    if (rotated.get(i).getId().equals(lastId)) { splitIndex = i; break; }
                }
                if (splitIndex != -1 && splitIndex < rotated.size() - 1) {
                    List<Student> passed = new ArrayList<>(rotated.subList(0, splitIndex + 1));
                    List<Student> active = new ArrayList<>(rotated.subList(splitIndex + 1, rotated.size()));
                    rotated.clear(); rotated.addAll(active); rotated.addAll(passed);
                }
            }

            // Шаг В: Свапы (БЕЗ фильтрации skipped, чтобы найти позицию)
            List<Student> swapped = new ArrayList<>(rotated);
            List<TemporarySwap> swaps = session.createQuery(
                            "FROM TemporarySwap WHERE subject.id=:sid AND isGroupQueue=:ig", TemporarySwap.class)
                    .setParameter("sid", subjectId).setParameter("ig", isGroup).list();

            for (TemporarySwap swap : swaps) {
                int i1 = -1, i2 = -1;
                for(int i=0; i<swapped.size(); i++) {
                    if(swapped.get(i).getId().equals(swap.getStudent1().getId())) i1 = i;
                    if(swapped.get(i).getId().equals(swap.getStudent2().getId())) i2 = i;
                }
                if(i1 != -1 && i2 != -1) Collections.swap(swapped, i1, i2);
            }

            // Шаг Г: Находим индекс того, кто сдал физически
            int idx = -1;
            for(int i=0; i<swapped.size(); i++) {
                if(swapped.get(i).getId().equals(physical.getId())) { idx = i; break; }
            }

            if (idx == -1) throw new IllegalArgumentException("Студент не найден в структуре очереди");

            // Тот, кто ДОЛЖЕН был быть на этом месте
            Student effective = rotated.get(idx);

            // Сохраняем
            if(oldState == null) {
                oldState = new QueueState();
                oldState.setSubject(session.get(Subject.class, subjectId));
                oldState.setGroupQueue(isGroup);
            }
            oldState.setLastPassedStudent(effective);
            session.merge(oldState);

            // Очистка
            session.createMutationQuery("DELETE FROM TemporarySwap WHERE subject.id=:sid AND isGroupQueue=:ig")
                    .setParameter("sid", subjectId).setParameter("ig", isGroup).executeUpdate();
            session.createMutationQuery("DELETE FROM SkippedStudent WHERE subject.id=:sid AND isGroupQueue=:ig")
                    .setParameter("sid", subjectId).setParameter("ig", isGroup).executeUpdate();

            tx.commit();
        }
    }

    public Long createSwapRequest(Long requesterId, String targetFio, Long subjectId, boolean isGroup) throws Exception {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            Student target = session.createQuery("FROM Student WHERE fio=:f", Student.class)
                    .setParameter("f", targetFio).uniqueResult();

            if (target == null) throw new Exception("Студент не найден");
            if (requesterId.equals(target.getId())) throw new Exception("Нельзя меняться с собой");

            Student requester = session.get(Student.class, requesterId);
            if (!isGroup && requester.getSubgroup() != target.getSubgroup()) {
                throw new Exception("Разные подгруппы");
            }

            // Проверка наличия в активной очереди
            List<Student> activeQueue = getActiveQueueList(session, subjectId, isGroup, requester.getSubgroup());

            if (activeQueue.stream().noneMatch(s -> s.getId().equals(target.getId()))) {
                throw new Exception("Цель не в очереди (снялся/сдал)");
            }
            if (activeQueue.stream().noneMatch(s -> s.getId().equals(requester.getId()))) {
                throw new Exception("Вы не в очереди (снялись/сдали)");
            }

            SwapRequest req = new SwapRequest();
            req.setRequester(requester);
            req.setTarget(target);
            req.setSubject(session.get(Subject.class, subjectId));
            req.setGroupQueue(isGroup);

            session.persist(req);
            tx.commit();
            return req.getId();
        }
    }

    public SwapRequest getSwapRequest(Long id) {
        try(Session s = HibernateUtil.getSessionFactory().openSession()){
            return s.get(SwapRequest.class, id);
        }
    }

    public void executeSwap(Long requestId) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            SwapRequest req = session.get(SwapRequest.class, requestId);
            if(req != null) {
                TemporarySwap swap = new TemporarySwap();
                swap.setSubject(req.getSubject());
                swap.setGroupQueue(req.isGroupQueue());
                swap.setStudent1(req.getRequester());
                swap.setStudent2(req.getTarget());
                session.persist(swap);
                session.remove(req);
            }
            tx.commit();
        }
    }

    public void deleteSwapRequest(Long requestId) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            SwapRequest req = session.get(SwapRequest.class, requestId);
            if(req != null) session.remove(req);
            tx.commit();
        }
    }
}