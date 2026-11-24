package com.bloodbank.backend.controller;

import com.bloodbank.backend.model.Receiver;
import com.bloodbank.backend.repository.ReceiverRepository;
import com.bloodbank.backend.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/receivers")
@CrossOrigin(origins = "*")
public class ReceiverController {

    private final ReceiverRepository receiverRepository;
    private final UserRepository userRepository;

    public ReceiverController(ReceiverRepository receiverRepository, UserRepository userRepository) {
        this.receiverRepository = receiverRepository;
        this.userRepository = userRepository;
    }

    private boolean isCallerAdmin(String callerId) {
        if (callerId == null || callerId.isBlank()) return false;
        return userRepository.findById(callerId)
                .map(u -> "ADMIN".equalsIgnoreCase(u.getRole()) || "admin@bloodbank.com".equalsIgnoreCase(u.getEmail()))
                .orElse(false);
    }

    @GetMapping
    public List<Receiver> getAllReceivers() {
        return receiverRepository.findAll();
    }

    @PostMapping
    public Receiver createReceiver(@RequestBody Receiver receiver) {
        if (receiver.getTrackingId() == null || receiver.getTrackingId().isBlank()) {
            int rand = ThreadLocalRandom.current().nextInt(100000, 999999);
            receiver.setTrackingId("REQ-" + rand);
        }
        if (receiver.getStatus() == null || receiver.getStatus().isBlank()) {
            receiver.setStatus("Pending");
        }
        if (receiver.getUrgencyLevel() == null || receiver.getUrgencyLevel().isBlank()) {
            receiver.setUrgencyLevel("Normal");
        }
        return receiverRepository.save(receiver);
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateReceiverStatus(
            @PathVariable String id,
            @RequestHeader(value = "X-User-Id", required = false) String callerId,
            @RequestBody Map<String, String> body) {
        if (!isCallerAdmin(callerId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Admin access required"));
        }
        return receiverRepository.findById(id)
                .map(r -> {
                    r.setStatus(body.getOrDefault("status", "Pending"));
                    return ResponseEntity.ok(receiverRepository.save(r));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteReceiver(
            @PathVariable String id,
            @RequestHeader(value = "X-User-Id", required = false) String callerId) {
        if (!isCallerAdmin(callerId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Admin access required"));
        }
        if (!receiverRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        receiverRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }
}