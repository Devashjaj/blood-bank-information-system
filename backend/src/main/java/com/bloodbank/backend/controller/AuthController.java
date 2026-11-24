@PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody User user) {
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Email is required!"));
        }
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

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", saved.getId() != null ? saved.getId() : "");
        res.put("name", saved.getName() != null ? saved.getName() : "");
        res.put("email", saved.getEmail() != null ? saved.getEmail() : "");
        res.put("role", saved.getRole() != null ? saved.getRole() : "USER");
        return ResponseEntity.ok(res);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> creds) {
        String email = creds != null ? creds.get("email") : null;
        String password = creds != null ? creds.get("password") : null;

        if (email == null || password == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Email and password are required!"));
        }

        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isPresent()) {
            User u = userOpt.get();
            if (u.getPassword() != null && u.getPassword().equals(password)) {
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

                Map<String, Object> res = new LinkedHashMap<>();
                res.put("id", u.getId() != null ? u.getId() : "");
                res.put("name", u.getName() != null ? u.getName() : "");
                res.put("email", u.getEmail() != null ? u.getEmail() : "");
                res.put("role", role);
                return ResponseEntity.ok(res);
            }
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "Invalid email or password!"));
    }