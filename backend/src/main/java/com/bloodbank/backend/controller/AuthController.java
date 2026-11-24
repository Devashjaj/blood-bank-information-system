package com.bloodbank.backend.controller;

import com.bloodbank.backend.model.User;
import com.bloodbank.backend.repository.UserRepository;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final UserRepository userRepository;
    private final MongoTemplate mongoTemplate;

    public AuthController(UserRepository userRepository, MongoTemplate mongoTemplate) {
        this.userRepository = userRepository;
        this.mongoTemplate = mongoTemplate;
    }

    private boolean isCallerAdmin(String callerId) {
        if (callerId == null || callerId.isBlank()) return false;
        return userRepository.findById(callerId)
                .map(u -> "ADMIN".equalsIgnoreCase(u.getRole()) || "admin@bloodbank.com".equalsIgnoreCase(u.getEmail()))
                .orElse(false);
    }

    private long countAdmins() {
        return userRepository.findAll().stream()
                .filter(u -> "ADMIN".equalsIgnoreCase(u.getRole()) || "admin@bloodbank.com".equalsIgnoreCase(u.getEmail()))
                .count();
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody User user) {
        if (userRepository.findByEmail(user.getEmail()).isPresent()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Email already registered!"));
        }
        Document settings = mongoTemplate.findById("default", Document.class, "system_settings");
        if (settings != null && Boolean.FALSE.equals(settings.getBoolean("publicRegistration")) && userRepository.count() > 0) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Public registration is currently disabled by Admin."));
        }

        if (userRepository.count() == 0 || "admin@bloodbank.com".equalsIgnoreCase(user.getEmail())) {
            user.setRole("ADMIN");
        } else {
            user.setRole("USER");
        }
        User saved = userRepository.save(user);
        return ResponseEntity.ok(Map.of(
                "id", saved.getId(),
                "name", saved.getName(),
                "email", saved.getEmail(),
                "role", saved.getRole()
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> creds) {
        String email = creds.get("email");
        String password = creds.get("password");

        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isPresent() && userOpt.get().getPassword().equals(password)) {
            User u = userOpt.get();
            Document stateDoc = mongoTemplate.findById("user_states", Document.class, "admin_metadata");
            if (stateDoc != null && Boolean.TRUE.equals(stateDoc.getBoolean("sus_" + u.getId()))) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "Your account is suspended by Administrator."));
            }
            String role = (u.getRole() != null) ? u.getRole() : "USER";
            if ("admin@bloodbank.com".equalsIgnoreCase(u.getEmail()) && !"ADMIN".equalsIgnoreCase(u.getRole())) {
                role = "ADMIN";
                u.setRole("ADMIN");
                userRepository.save(u);
            }
            return ResponseEntity.ok(Map.of(
                    "id", u.getId(),
                    "name", u.getName(),
                    "email", u.getEmail(),
                    "role", role
            ));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "Invalid email or password!"));
    }

    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers(@RequestHeader(value = "X-User-Id", required = false) String callerId) {
        if (!isCallerAdmin(callerId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Admin access required"));
        }
        Document stateDoc = mongoTemplate.findById("user_states", Document.class, "admin_metadata");
        ArrayList out = new ArrayList();
        for (User u : userRepository.findAll()) {
            boolean suspended = stateDoc != null && Boolean.TRUE.equals(stateDoc.getBoolean("sus_" + u.getId()));
            Map item = new LinkedHashMap();
            item.put("id", u.getId());
            item.put("name", u.getName() != null ? u.getName() : "");
            item.put("email", u.getEmail() != null ? u.getEmail() : "");
            item.put("role", u.getRole() != null ? u.getRole() : "USER");
            item.put("suspended", suspended);
            out.add(item);
        }
        return ResponseEntity.ok(out);
    }

    @PutMapping("/users/{id}/role")
    public ResponseEntity<?> updateUserRole(
            @PathVariable String id,
            @RequestHeader(value = "X-User-Id", required = false) String callerId,
            @RequestBody Map<String, String> body) {
        if (!isCallerAdmin(callerId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Admin access required"));
        }
        Optional<User> targetOpt = userRepository.findById(id);
        if (targetOpt.isEmpty()) return ResponseEntity.notFound().build();

        User target = targetOpt.get();
        String newRole = body.getOrDefault("role", "USER");

        if ("ADMIN".equalsIgnoreCase(target.getRole()) && !"ADMIN".equalsIgnoreCase(newRole) && countAdmins() <= 1) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Cannot demote the last remaining Admin!"));
        }

        target.setRole(newRole);
        userRepository.save(target);
        return ResponseEntity.ok(Map.of("message", "Role updated", "role", target.getRole()));
    }

    @PutMapping("/users/{id}/suspend")
    public ResponseEntity<?> toggleSuspendUser(
            @PathVariable String id,
            @RequestHeader(value = "X-User-Id", required = false) String callerId,
            @RequestBody Map<String, Boolean> body) {
        if (!isCallerAdmin(callerId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Admin access required"));
        }
        Optional<User> targetOpt = userRepository.findById(id);
        if (targetOpt.isEmpty()) return ResponseEntity.notFound().build();
        if ("ADMIN".equalsIgnoreCase(targetOpt.get().getRole()) && countAdmins() <= 1) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Cannot suspend the last remaining Admin!"));
        }
        Document stateDoc = mongoTemplate.findById("user_states", Document.class, "admin_metadata");
        if (stateDoc == null) stateDoc = new Document("_id", "user_states");
        boolean suspend = Boolean.TRUE.equals(body.get("suspended"));
        stateDoc.put("sus_" + id, suspend);
        mongoTemplate.save(stateDoc, "admin_metadata");
        return ResponseEntity.ok(Map.of("suspended", suspend));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(
            @PathVariable String id,
            @RequestHeader(value = "X-User-Id", required = false) String callerId) {
        if (!isCallerAdmin(callerId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Admin access required"));
        }
        Optional<User> targetOpt = userRepository.findById(id);
        if (targetOpt.isEmpty()) return ResponseEntity.notFound().build();

        User target = targetOpt.get();
        if ("ADMIN".equalsIgnoreCase(target.getRole()) && countAdmins() <= 1) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Cannot delete the last remaining Admin!"));
        }
        if (id.equals(callerId)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "You cannot delete your own active Admin account!"));
        }

        userRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "User deleted"));
    }

    @GetMapping("/admin-meta")
    public Map<String, Object> getAdminMeta() {
        Document meta = mongoTemplate.findById("global_admin_meta", Document.class, "admin_metadata");
        if (meta == null) {
            meta = new Document("_id", "global_admin_meta")
                    .append("stockAdj", new Document())
                    .append("expiredUnits", new Document())
                    .append("donorStatus", new Document())
                    .append("hospitalVerified", new Document())
                    .append("requestMeta", new Document())
                    .append("stockTxns", new ArrayList<>());
            mongoTemplate.save(meta, "admin_metadata");
        }
        return meta;
    }

    @PutMapping("/admin-meta")
    public ResponseEntity<?> saveAdminMeta(
            @RequestHeader(value = "X-User-Id", required = false) String callerId,
            @RequestBody Map<String, Object> payload) {
        if (!isCallerAdmin(callerId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Admin access required"));
        }
        Document meta = new Document("_id", "global_admin_meta");
        meta.putAll(payload);
        meta.put("_id", "global_admin_meta");
        mongoTemplate.save(meta, "admin_metadata");
        return ResponseEntity.ok(meta);
    }

    @GetMapping("/settings")
    public Map<String, Object> getSettings() {
        Document doc = mongoTemplate.findById("default", Document.class, "system_settings");
        if (doc == null) {
            doc = new Document("_id", "default")
                    .append("emailNotif", true)
                    .append("lowStockAlert", true)
                    .append("emergencyAlert", true)
                    .append("autoStockUpdate", true)
                    .append("publicRegistration", true);
            mongoTemplate.save(doc, "system_settings");
        }
        return doc;
    }

    @PutMapping("/settings")
    public ResponseEntity<?> updateSettings(
            @RequestHeader(value = "X-User-Id", required = false) String callerId,
            @RequestBody Map<String, Object> payload) {
        if (!isCallerAdmin(callerId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Admin access required"));
        }
        Document doc = new Document("_id", "default");
        doc.putAll(payload);
        doc.put("_id", "default");
        mongoTemplate.save(doc, "system_settings");
        return ResponseEntity.ok(doc);
    }

    @GetMapping("/logs")
    public List<Document> getLogs() {
        Query q = new Query().with(Sort.by(Sort.Direction.DESC, "createdAt")).limit(15);
        return mongoTemplate.find(q, Document.class, "activity_logs");
    }

    @PostMapping("/logs")
    public Document addLog(@RequestBody Map<String, String> body) {
        String timeStr = LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm a"));
        Document doc = new Document()
                .append("msg", body.getOrDefault("msg", "Activity recorded"))
                .append("time", timeStr)
                .append("createdAt", System.currentTimeMillis());
        return mongoTemplate.save(doc, "activity_logs");
    }
}