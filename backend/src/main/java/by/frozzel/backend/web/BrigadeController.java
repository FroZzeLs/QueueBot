package by.frozzel.backend.web;

import by.frozzel.backend.config.HibernateUtil;
import by.frozzel.backend.model.Brigade;
import by.frozzel.backend.model.BrigadeMember;
import by.frozzel.backend.model.Student;
import by.frozzel.backend.model.Subject;
import org.hibernate.Session;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
public class BrigadeController {

    @GetMapping("/brigades")
    public ResponseEntity<?> listBrigades(@RequestParam long subjectId) {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            List<Brigade> brigades = s.createQuery(
                            "FROM Brigade b WHERE b.subject.id = :sid ORDER BY b.sortKey ASC",
                            Brigade.class)
                    .setParameter("sid", subjectId)
                    .list();
            List<Map<String, Object>> res = new ArrayList<>();
            for (Brigade b : brigades) {
                res.add(Map.of(
                        "id", b.getId(),
                        "displayName", b.getDisplayName()
                ));
            }
            return ResponseEntity.ok(Map.of("brigades", res));
        }
    }

    @GetMapping("/brigades/subject")
    public ResponseEntity<?> listBrigadesWithMembers(@RequestParam long subjectId) {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            List<Brigade> brigades = s.createQuery(
                            "FROM Brigade b WHERE b.subject.id = :sid ORDER BY b.sortKey ASC",
                            Brigade.class)
                    .setParameter("sid", subjectId)
                    .list();
            
            List<Map<String, Object>> res = new ArrayList<>();
            for (Brigade b : brigades) {
                List<BrigadeMember> ms = s.createQuery(
                                "FROM BrigadeMember bm WHERE bm.brigade.id = :bid ORDER BY bm.student.fio ASC",
                                BrigadeMember.class)
                        .setParameter("bid", b.getId())
                        .list();
                
                List<Long> memberIds = new ArrayList<>();
                List<String> memberNames = new ArrayList<>();
                for (BrigadeMember bm : ms) {
                    Student st = bm.getStudent();
                    memberIds.add(st.getId());
                    memberNames.add(st.getFio());
                }
                
                res.add(Map.of(
                        "id", b.getId(),
                        "displayName", b.getDisplayName(),
                        "memberIds", memberIds,
                        "memberNames", memberNames
                ));
            }
            return ResponseEntity.ok(Map.of("brigades", res));
        }
    }

    @GetMapping("/brigades/{brigadeId}/members")
    public ResponseEntity<?> members(@PathVariable long brigadeId) {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Brigade brigade = s.get(Brigade.class, brigadeId);
            if (brigade == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "BRIGADE_NOT_FOUND"));

            List<BrigadeMember> ms = s.createQuery(
                            "FROM BrigadeMember bm WHERE bm.brigade.id = :bid ORDER BY bm.student.fio ASC",
                            BrigadeMember.class)
                    .setParameter("bid", brigadeId)
                    .list();

            List<Map<String, Object>> res = new ArrayList<>();
            for (BrigadeMember bm : ms) {
                Student st = bm.getStudent();
                res.add(Map.of("id", st.getId(), "fio", st.getFio(), "telegramTag", st.getTelegramTag()));
            }
            return ResponseEntity.ok(Map.of("brigadeId", brigadeId, "members", res));
        }
    }

    @GetMapping("/brigades/all-with-subjects")
    public ResponseEntity<?> getAllBrigadesWithSubjects() {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            List<Brigade> allBrigades = s.createQuery(
                            "FROM Brigade b ORDER BY b.subject.name ASC, b.sortKey ASC",
                            Brigade.class)
                    .list();
            
            Map<Long, List<Map<String, Object>>> subjectBrigades = new HashMap<>();
            for (Brigade b : allBrigades) {
                long subjectId = b.getSubject().getId();
                String subjectName = b.getSubject().getName();
                
                List<BrigadeMember> ms = s.createQuery(
                                "FROM BrigadeMember bm WHERE bm.brigade.id = :bid",
                                BrigadeMember.class)
                        .setParameter("bid", b.getId())
                        .list();
                
                List<Long> memberIds = new ArrayList<>();
                for (BrigadeMember bm : ms) {
                    memberIds.add(bm.getStudent().getId());
                }
                
                Map<String, Object> brigadeInfo = Map.of(
                        "id", b.getId(),
                        "displayName", b.getDisplayName(),
                        "memberIds", memberIds
                );
                
                subjectBrigades
                    .computeIfAbsent(subjectId, k -> new ArrayList<>())
                    .add(brigadeInfo);
            }
            
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map.Entry<Long, List<Map<String, Object>>> entry : subjectBrigades.entrySet()) {
                Subject subj = s.get(Subject.class, entry.getKey());
                result.add(Map.of(
                        "subjectId", entry.getKey(),
                        "subjectName", subj.getName(),
                        "brigades", entry.getValue()
                ));
            }
            
            return ResponseEntity.ok(Map.of("subjects", result));
        }
    }
}
