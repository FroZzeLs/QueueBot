package by.frozzel.backend.service;

import by.frozzel.backend.config.HibernateUtil;
import by.frozzel.backend.model.*;
import by.frozzel.backend.security.AuthContext;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class QueueEngine {

    public static List<Student> buildActiveIndividualQueue(Session session, Subject subject, QueueKind queueKind, Integer subgroupNum) {
        List<Student> rotated = buildRotatedIndividualBase(session, subject, queueKind, subgroupNum);

        // filter skipped
        List<Long> skippedIds = session.createQuery(
                        "SELECT s.student.id FROM SkippedStudent s " +
                                "WHERE s.subject.id = :sid AND s.queueKind = :qk AND (s.subgroupNum = :sg OR (:sg IS NULL AND s.subgroupNum IS NULL))",
                        Long.class)
                .setParameter("sid", subject.getId())
                .setParameter("qk", queueKind)
                .setParameter("sg", subgroupNum)
                .list();
        rotated.removeIf(s -> skippedIds.contains(s.getId()));

        // apply temporary swaps (student-student)
        List<TemporarySwap> swaps = session.createQuery(
                        "FROM TemporarySwap t WHERE t.subject.id = :sid AND t.queueKind = :qk AND " +
                                "((t.subgroupNum = :sg) OR (:sg IS NULL AND t.subgroupNum IS NULL)) AND t.student1 IS NOT NULL AND t.student2 IS NOT NULL",
                        TemporarySwap.class)
                .setParameter("sid", subject.getId())
                .setParameter("qk", queueKind)
                .setParameter("sg", subgroupNum)
                .list();

        for (TemporarySwap swap : swaps) {
            int i1 = indexOfStudent(rotated, swap.getStudent1());
            int i2 = indexOfStudent(rotated, swap.getStudent2());
            if (i1 != -1 && i2 != -1) Collections.swap(rotated, i1, i2);
        }

        return rotated;
    }

    private static List<Student> buildRotatedIndividualBase(Session session, Subject subject, QueueKind queueKind, Integer subgroupNum) {
        List<Student> original = session.createQuery("FROM Student s WHERE (:qk = 'COMMON' OR s.subgroup = :sg) ORDER BY s.fio", Student.class)
                .setParameter("qk", queueKind.name())
                .setParameter("sg", subgroupNum)
                .list();

        QueueState state = getQueueState(session, subject, queueKind, subgroupNum);

        if (original.isEmpty()) return new ArrayList<>();
        if (state == null || state.getLastPassedStudent() == null || state.getLastPassedStudent().getId() == null) {
            return new ArrayList<>(original);
        }

        List<Student> rotated = new ArrayList<>(original);
        Long lastId = state.getLastPassedStudent().getId();
        int splitIndex = -1;
        for (int i = 0; i < rotated.size(); i++) {
            if (Objects.equals(rotated.get(i).getId(), lastId)) {
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
        return rotated;
    }

    private static QueueState getQueueState(Session session, Subject subject, QueueKind queueKind, Integer subgroupNum) {
        return session.createQuery(
                        "FROM QueueState qs WHERE qs.subject.id = :sid AND qs.queueKind = :qk AND " +
                                "((qs.subgroupNum = :sg) OR (:sg IS NULL AND qs.subgroupNum IS NULL))",
                        QueueState.class)
                .setParameter("sid", subject.getId())
                .setParameter("qk", queueKind)
                .setParameter("sg", subgroupNum)
                .uniqueResult();
    }

    private static int indexOfStudent(List<Student> list, Student s) {
        if (s == null || s.getId() == null) return -1;
        for (int i = 0; i < list.size(); i++) {
            if (Objects.equals(list.get(i).getId(), s.getId())) return i;
        }
        return -1;
    }

    public static List<Brigade> buildActiveBrigadeQueue(Session session, Subject subject, QueueKind queueKind, Integer subgroupNum) {
        List<Brigade> rotated = buildRotatedBrigadeBase(session, subject, queueKind, subgroupNum);

        // filter skipped
        List<Long> skippedIds = session.createQuery(
                        "SELECT sb.brigade.id FROM SkippedBrigade sb " +
                                "WHERE sb.subject.id = :sid AND sb.queueKind = :qk AND " +
                                "((sb.subgroupNum = :sg) OR (:sg IS NULL AND sb.subgroupNum IS NULL))",
                        Long.class)
                .setParameter("sid", subject.getId())
                .setParameter("qk", queueKind)
                .setParameter("sg", subgroupNum)
                .list();
        rotated.removeIf(b -> skippedIds.contains(b.getId()));

        // apply temporary swaps (brigade-brigade)
        List<TemporarySwap> swaps = session.createQuery(
                        "FROM TemporarySwap t WHERE t.subject.id = :sid AND t.queueKind = :qk AND " +
                                "((t.subgroupNum = :sg) OR (:sg IS NULL AND t.subgroupNum IS NULL)) AND t.brigade1 IS NOT NULL AND t.brigade2 IS NOT NULL",
                        TemporarySwap.class)
                .setParameter("sid", subject.getId())
                .setParameter("qk", queueKind)
                .setParameter("sg", subgroupNum)
                .list();

        for (TemporarySwap swap : swaps) {
            int i1 = indexOfBrigade(rotated, swap.getBrigade1());
            int i2 = indexOfBrigade(rotated, swap.getBrigade2());
            if (i1 != -1 && i2 != -1) Collections.swap(rotated, i1, i2);
        }

        return rotated;
    }

    private static List<Brigade> buildRotatedBrigadeBase(Session session, Subject subject, QueueKind queueKind, Integer subgroupNum) {
        List<Brigade> original;
        if (queueKind == QueueKind.COMMON) {
            original = session.createQuery(
                            "FROM Brigade b WHERE b.subject.id = :sid ORDER BY b.sortKey ASC",
                            Brigade.class)
                    .setParameter("sid", subject.getId())
                    .list();
        } else {
            original = session.createQuery(
                            "SELECT DISTINCT b FROM Brigade b " +
                                    "JOIN BrigadeMember bm ON bm.brigade.id = b.id " +
                                    "JOIN Student s ON s.id = bm.student.id " +
                                    "WHERE b.subject.id = :sid AND s.subgroup = :sg " +
                                    "ORDER BY b.sortKey ASC",
                            Brigade.class)
                    .setParameter("sid", subject.getId())
                    .setParameter("sg", subgroupNum)
                    .list();
        }

        QueueState state = getQueueState(session, subject, queueKind, subgroupNum);
        if (original.isEmpty()) return new ArrayList<>();
        if (state == null || state.getLastPassedBrigade() == null || state.getLastPassedBrigade().getId() == null) {
            return new ArrayList<>(original);
        }

        List<Brigade> rotated = new ArrayList<>(original);
        Long lastId = state.getLastPassedBrigade().getId();
        int splitIndex = -1;
        for (int i = 0; i < rotated.size(); i++) {
            if (Objects.equals(rotated.get(i).getId(), lastId)) {
                splitIndex = i;
                break;
            }
        }
        if (splitIndex != -1 && splitIndex < rotated.size() - 1) {
            List<Brigade> passed = new ArrayList<>(rotated.subList(0, splitIndex + 1));
            List<Brigade> active = new ArrayList<>(rotated.subList(splitIndex + 1, rotated.size()));
            rotated.clear();
            rotated.addAll(active);
            rotated.addAll(passed);
        }
        return rotated;
    }

    private static int indexOfBrigade(List<Brigade> list, Brigade b) {
        if (b == null || b.getId() == null) return -1;
        for (int i = 0; i < list.size(); i++) {
            if (Objects.equals(list.get(i).getId(), b.getId())) return i;
        }
        return -1;
    }

    public static void leaveQueue(Session session, Student me, Subject subject, QueueKind queueKind, Integer subgroupNum) {
        if (subject.getDeliveryType() == DeliveryType.INDIVIDUAL) {
            // prevent duplicates
            Long count = session.createQuery(
                            "SELECT COUNT(s) FROM SkippedStudent s WHERE s.student.id = :id AND s.subject.id = :sid AND s.queueKind = :qk AND " +
                                    "((s.subgroupNum = :sg) OR (:sg IS NULL AND s.subgroupNum IS NULL))",
                            Long.class)
                    .setParameter("id", me.getId())
                    .setParameter("sid", subject.getId())
                    .setParameter("qk", queueKind)
                    .setParameter("sg", subgroupNum)
                    .uniqueResult();
            if (count != null && count > 0) {
                throw new IllegalStateException("Вы уже снялись с этой очереди.");
            }

            Transaction tx = session.beginTransaction();
            SkippedStudent ss = new SkippedStudent(me, subject, queueKind, subgroupNum);
            session.persist(ss);

            // delete temporary swaps involving me
            session.createMutationQuery(
                            "DELETE FROM TemporarySwap t WHERE t.subject.id = :sid AND t.queueKind = :qk AND " +
                                    "((t.subgroupNum = :sg) OR (:sg IS NULL AND t.subgroupNum IS NULL)) AND " +
                                    "((t.student1.id = :id) OR (t.student2.id = :id))")
                    .setParameter("sid", subject.getId())
                    .setParameter("qk", queueKind)
                    .setParameter("sg", subgroupNum)
                    .setParameter("id", me.getId())
                    .executeUpdate();

            tx.commit();
            return;
        }

        // brigade-based: skip entire brigade containing the user
        Brigade myBrigade = findBrigadeByStudent(session, subject, me);
        if (myBrigade == null) throw new IllegalStateException("Вы не состоите в бригаде по этому предмету.");

        Long count = session.createQuery(
                        "SELECT COUNT(s) FROM SkippedBrigade s WHERE s.brigade.id = :bid AND s.subject.id = :sid AND s.queueKind = :qk AND " +
                                "((s.subgroupNum = :sg) OR (:sg IS NULL AND s.subgroupNum IS NULL))",
                        Long.class)
                .setParameter("bid", myBrigade.getId())
                .setParameter("sid", subject.getId())
                .setParameter("qk", queueKind)
                .setParameter("sg", subgroupNum)
                .uniqueResult();
        if (count != null && count > 0) {
            throw new IllegalStateException("Вы уже снялись с этой очереди.");
        }

        Transaction tx = session.beginTransaction();
        session.persist(new SkippedBrigade(myBrigade, subject, queueKind, subgroupNum));
        session.createMutationQuery(
                        "DELETE FROM TemporarySwap t WHERE t.subject.id = :sid AND t.queueKind = :qk AND " +
                                "((t.subgroupNum = :sg) OR (:sg IS NULL AND t.subgroupNum IS NULL)) AND " +
                                "((t.brigade1.id = :bid) OR (t.brigade2.id = :bid))")
                .setParameter("sid", subject.getId())
                .setParameter("qk", queueKind)
                .setParameter("sg", subgroupNum)
                .setParameter("bid", myBrigade.getId())
                .executeUpdate();
        tx.commit();
    }

    private static Brigade findBrigadeByStudent(Session session, Subject subject, Student me) {
        return session.createQuery(
                        "FROM Brigade b WHERE b.subject.id = :sid AND EXISTS (" +
                                "SELECT bm.id FROM BrigadeMember bm WHERE bm.brigade.id = b.id AND bm.student.id = :me)",
                        Brigade.class)
                .setParameter("sid", subject.getId())
                .setParameter("me", me.getId())
                .setMaxResults(1)
                .uniqueResult();
    }

    public static void setLastPassed(Session session, Student physicalStudent, Subject subject, QueueKind queueKind, Integer subgroupNum) {
        Transaction tx = session.beginTransaction();
        QueueState state = getQueueState(session, subject, queueKind, subgroupNum);

        if (subject.getDeliveryType() == DeliveryType.INDIVIDUAL) {
            if (state == null) {
                state = new QueueState();
                state.setSubject(subject);
                state.setQueueKind(queueKind);
                state.setSubgroupNum(subgroupNum);
            }

            List<Student> original = session.createQuery(
                            "FROM Student s WHERE (:qk = 'COMMON' OR s.subgroup = :sg) ORDER BY s.fio",
                            Student.class)
                    .setParameter("qk", queueKind.name())
                    .setParameter("sg", subgroupNum)
                    .list();

            if (original.isEmpty()) throw new IllegalArgumentException("Очередь пуста.");

            List<Student> rotated = rotateIndividual(original, state.getLastPassedStudent() != null ? state.getLastPassedStudent().getId() : null);

            List<TemporarySwap> swaps = session.createQuery(
                            "FROM TemporarySwap t WHERE t.subject.id = :sid AND t.queueKind = :qk AND " +
                                    "((t.subgroupNum = :sg) OR (:sg IS NULL AND t.subgroupNum IS NULL)) AND t.student1 IS NOT NULL AND t.student2 IS NOT NULL",
                            TemporarySwap.class)
                    .setParameter("sid", subject.getId())
                    .setParameter("qk", queueKind)
                    .setParameter("sg", subgroupNum)
                    .list();

            List<Student> swapped = new ArrayList<>(rotated);
            for (TemporarySwap swap : swaps) {
                int i1 = indexOfStudent(swapped, swap.getStudent1());
                int i2 = indexOfStudent(swapped, swap.getStudent2());
                if (i1 != -1 && i2 != -1) Collections.swap(swapped, i1, i2);
            }

            int idx = indexOfStudent(swapped, physicalStudent);
            if (idx < 0) throw new IllegalArgumentException("Студент не найден в структуре очереди");
            Student effective = rotated.get(idx);
            state.setLastPassedStudent(effective);
            state.setLastPassedBrigade(null);
            session.merge(state);

            // clear temp swaps + skipped
            session.createMutationQuery(
                            "DELETE FROM TemporarySwap t WHERE t.subject.id = :sid AND t.queueKind = :qk AND " +
                                    "((t.subgroupNum = :sg) OR (:sg IS NULL AND t.subgroupNum IS NULL))")
                    .setParameter("sid", subject.getId())
                    .setParameter("qk", queueKind)
                    .setParameter("sg", subgroupNum)
                    .executeUpdate();
            session.createMutationQuery(
                            "DELETE FROM SkippedStudent ss WHERE ss.subject.id = :sid AND ss.queueKind = :qk AND " +
                                    "((ss.subgroupNum = :sg) OR (:sg IS NULL AND ss.subgroupNum IS NULL))")
                    .setParameter("sid", subject.getId())
                    .setParameter("qk", queueKind)
                    .setParameter("sg", subgroupNum)
                    .executeUpdate();
        } else {
            Brigade physicalBrigade = findBrigadeByStudent(session, subject, physicalStudent);
            if (physicalBrigade == null) throw new IllegalArgumentException("Физический студент не найден в бригаде.");

            if (state == null) {
                state = new QueueState();
                state.setSubject(subject);
                state.setQueueKind(queueKind);
                state.setSubgroupNum(subgroupNum);
            }

            List<Brigade> original = buildBaseBrigadesForRotation(session, subject, queueKind, subgroupNum);
            if (original.isEmpty()) throw new IllegalArgumentException("Очередь пуста.");

            List<Brigade> rotated = rotateBrigades(original, state.getLastPassedBrigade() != null ? state.getLastPassedBrigade().getId() : null);

            List<TemporarySwap> swaps = session.createQuery(
                            "FROM TemporarySwap t WHERE t.subject.id = :sid AND t.queueKind = :qk AND " +
                                    "((t.subgroupNum = :sg) OR (:sg IS NULL AND t.subgroupNum IS NULL)) AND t.brigade1 IS NOT NULL AND t.brigade2 IS NOT NULL",
                            TemporarySwap.class)
                    .setParameter("sid", subject.getId())
                    .setParameter("qk", queueKind)
                    .setParameter("sg", subgroupNum)
                    .list();

            List<Brigade> swapped = new ArrayList<>(rotated);
            for (TemporarySwap swap : swaps) {
                int i1 = indexOfBrigade(swapped, swap.getBrigade1());
                int i2 = indexOfBrigade(swapped, swap.getBrigade2());
                if (i1 != -1 && i2 != -1) Collections.swap(swapped, i1, i2);
            }

            int idx = indexOfBrigade(swapped, physicalBrigade);
            if (idx < 0) throw new IllegalArgumentException("Бригада не найдена в структуре очереди");
            Brigade effective = rotated.get(idx);
            state.setLastPassedBrigade(effective);
            state.setLastPassedStudent(null);
            session.merge(state);

            // clear temp swaps + skipped brigades
            session.createMutationQuery(
                            "DELETE FROM TemporarySwap t WHERE t.subject.id = :sid AND t.queueKind = :qk AND " +
                                    "((t.subgroupNum = :sg) OR (:sg IS NULL AND t.subgroupNum IS NULL))")
                    .setParameter("sid", subject.getId())
                    .setParameter("qk", queueKind)
                    .setParameter("sg", subgroupNum)
                    .executeUpdate();
            session.createMutationQuery(
                            "DELETE FROM SkippedBrigade sb WHERE sb.subject.id = :sid AND sb.queueKind = :qk AND " +
                                    "((sb.subgroupNum = :sg) OR (:sg IS NULL AND sb.subgroupNum IS NULL))")
                    .setParameter("sid", subject.getId())
                    .setParameter("qk", queueKind)
                    .setParameter("sg", subgroupNum)
                    .executeUpdate();
        }

        tx.commit();
    }

    private static List<Student> rotateIndividual(List<Student> original, Long lastPassedId) {
        if (lastPassedId == null) return new ArrayList<>(original);
        List<Student> rotated = new ArrayList<>(original);
        int splitIndex = -1;
        for (int i = 0; i < rotated.size(); i++) {
            if (Objects.equals(rotated.get(i).getId(), lastPassedId)) {
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
        return rotated;
    }

    private static List<Brigade> buildBaseBrigadesForRotation(Session session, Subject subject, QueueKind queueKind, Integer subgroupNum) {
        // base participants before skipped removal
        if (queueKind == QueueKind.COMMON) {
            return session.createQuery("FROM Brigade b WHERE b.subject.id = :sid ORDER BY b.sortKey ASC", Brigade.class)
                    .setParameter("sid", subject.getId())
                    .list();
        }
        return session.createQuery(
                        "SELECT DISTINCT b FROM Brigade b " +
                                "JOIN BrigadeMember bm ON bm.brigade.id = b.id " +
                                "JOIN Student s ON s.id = bm.student.id " +
                                "WHERE b.subject.id = :sid AND s.subgroup = :sg " +
                                "ORDER BY b.sortKey ASC",
                        Brigade.class)
                .setParameter("sid", subject.getId())
                .setParameter("sg", subgroupNum)
                .list();
    }

    private static List<Brigade> rotateBrigades(List<Brigade> original, Long lastPassedId) {
        if (lastPassedId == null) return new ArrayList<>(original);
        List<Brigade> rotated = new ArrayList<>(original);
        int splitIndex = -1;
        for (int i = 0; i < rotated.size(); i++) {
            if (Objects.equals(rotated.get(i).getId(), lastPassedId)) {
                splitIndex = i;
                break;
            }
        }
        if (splitIndex != -1 && splitIndex < rotated.size() - 1) {
            List<Brigade> passed = new ArrayList<>(rotated.subList(0, splitIndex + 1));
            List<Brigade> active = new ArrayList<>(rotated.subList(splitIndex + 1, rotated.size()));
            rotated.clear();
            rotated.addAll(active);
            rotated.addAll(passed);
        }
        return rotated;
    }

    public static Long createSwapRequest(Session session,
                                          Student requesterStudent,
                                          Subject subject,
                                          QueueKind queueKind,
                                          Integer subgroupNum,
                                          Long targetStudentIdOrBrigadeId,
                                          Long targetNotifyStudentId,
                                          boolean brigadeSwap) {
        Transaction tx = session.beginTransaction();
        QueueState state = getQueueState(session, subject, queueKind, subgroupNum);

        if (!brigadeSwap) {
            Student requester = requesterStudent;
            Student target = session.get(Student.class, targetStudentIdOrBrigadeId);
            if (target == null) throw new IllegalArgumentException("Цель не найдена.");
            if (Objects.equals(requester.getId(), target.getId())) throw new IllegalArgumentException("Нельзя меняться с собой.");

            List<Student> active = buildActiveIndividualQueue(session, subject, queueKind, subgroupNum);
            if (active.stream().noneMatch(s -> Objects.equals(s.getId(), requester.getId())))
                throw new IllegalStateException("Вы не в очереди.");
            if (active.stream().noneMatch(s -> Objects.equals(s.getId(), target.getId())))
                throw new IllegalStateException("Цель не в очереди.");

            SwapRequest req = new SwapRequest();
            req.setSubject(subject);
            req.setQueueKind(queueKind);
            req.setSubgroupNum(subgroupNum);
            req.setRequesterStudent(requester);
            req.setTargetStudent(target);
            req.setTargetNotifyStudent(target);
            req.setRequesterNotifyStudent(requester);
            session.persist(req);
            tx.commit();
            return req.getId();
        }

        Brigade requesterBrigade = findBrigadeByStudent(session, subject, requesterStudent);
        if (requesterBrigade == null) throw new IllegalStateException("Вы не состоите в бригаде по этому предмету.");

        Brigade targetBrigade = session.get(Brigade.class, targetStudentIdOrBrigadeId);
        if (targetBrigade == null) throw new IllegalArgumentException("Целевая бригада не найдена.");
        if (!Objects.equals(targetBrigade.getSubject().getId(), subject.getId())) throw new IllegalArgumentException("Целевая бригада не относится к паре.");
        Student targetNotify = session.get(Student.class, targetNotifyStudentId);
        if (targetNotify == null) throw new IllegalArgumentException("Целевой студент не найден.");
        // ensure targetNotify belongs to targetBrigade
        boolean belongs = session.createQuery(
                        "SELECT COUNT(bm.id) FROM BrigadeMember bm WHERE bm.brigade.id = :bid AND bm.student.id = :sid", Long.class)
                .setParameter("bid", targetBrigade.getId())
                .setParameter("sid", targetNotify.getId())
                .uniqueResult() > 0;
        if (!belongs) throw new IllegalArgumentException("Выбранный студент не состоит в целевой бригаде.");

        if (Objects.equals(requesterBrigade.getId(), targetBrigade.getId())) throw new IllegalArgumentException("Нельзя меняться с собственной бригадой.");

        List<Brigade> activeBrigades = buildActiveBrigadeQueue(session, subject, queueKind, subgroupNum);
        if (activeBrigades.stream().noneMatch(b -> Objects.equals(b.getId(), requesterBrigade.getId())))
            throw new IllegalStateException("Ваша бригада не в очереди.");
        if (activeBrigades.stream().noneMatch(b -> Objects.equals(b.getId(), targetBrigade.getId())))
            throw new IllegalStateException("Целевая бригада не в очереди.");

        SwapRequest req = new SwapRequest();
        req.setSubject(subject);
        req.setQueueKind(queueKind);
        req.setSubgroupNum(subgroupNum);
        req.setRequesterBrigade(requesterBrigade);
        req.setTargetBrigade(targetBrigade);
        req.setTargetNotifyStudent(targetNotify);
        req.setRequesterNotifyStudent(requesterStudent);
        session.persist(req);
        tx.commit();
        return req.getId();
    }

    public static SwapRequest getSwapRequest(Session session, Long requestId) {
        return session.get(SwapRequest.class, requestId);
    }

    public static List<Object> acceptSwap(Session session, Long requestId, Student acceptorByChat) {
        Transaction tx = session.beginTransaction();
        SwapRequest req = session.get(SwapRequest.class, requestId);
        if (req == null) {
            tx.commit();
            throw new IllegalArgumentException("Запрос не найден.");
        }

        if (req.getTargetNotifyStudent() == null || !Objects.equals(req.getTargetNotifyStudent().getId(), acceptorByChat.getId())) {
            tx.commit();
            throw new SecurityException("Нет прав на принятие запроса.");
        }

        Subject subject = req.getSubject();

        if (subject.getDeliveryType() == DeliveryType.INDIVIDUAL) {
            TemporarySwap ts = new TemporarySwap();
            ts.setSubject(subject);
            ts.setQueueKind(req.getQueueKind());
            ts.setSubgroupNum(req.getSubgroupNum());
            ts.setStudent1(req.getRequesterStudent());
            ts.setStudent2(req.getTargetStudent());
            session.persist(ts);
        } else {
            TemporarySwap ts = new TemporarySwap();
            ts.setSubject(subject);
            ts.setQueueKind(req.getQueueKind());
            ts.setSubgroupNum(req.getSubgroupNum());
            ts.setBrigade1(req.getRequesterBrigade());
            ts.setBrigade2(req.getTargetBrigade());
            session.persist(ts);
        }

        session.remove(req);
        tx.commit();
        return Collections.emptyList();
    }

    public static void declineSwap(Session session, Long requestId, Student acceptor) {
        Transaction tx = session.beginTransaction();
        SwapRequest req = session.get(SwapRequest.class, requestId);
        if (req == null) {
            tx.commit();
            return;
        }
        if (req.getTargetNotifyStudent() != null && Objects.equals(req.getTargetNotifyStudent().getId(), acceptor.getId())) {
            session.remove(req);
        }
        tx.commit();
    }

    public static List<Student> getActiveIndividualQueueForUser(Session session, Subject subject, QueueKind queueKind, Integer subgroupNum) {
        return buildActiveIndividualQueue(session, subject, queueKind, subgroupNum);
    }
}

