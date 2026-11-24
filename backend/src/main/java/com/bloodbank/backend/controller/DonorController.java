package com.bloodbank.backend.controller;

import com.bloodbank.backend.model.Donor;
import com.bloodbank.backend.repository.DonorRepository;
import com.bloodbank.backend.repository.UserRepository;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/donors")
@CrossOrigin(origins = "*")
public class DonorController {

    private final DonorRepository donorRepository;
    private final UserRepository userRepository;

    public DonorController(DonorRepository donorRepository, UserRepository userRepository) {
        this.donorRepository = donorRepository;
        this.userRepository = userRepository;
    }

    private boolean isCallerAdmin(String callerId) {
        if (callerId == null || callerId.isBlank()) return false;
        return userRepository.findById(callerId)
                .map(u -> "ADMIN".equalsIgnoreCase(u.getRole()) || "admin@bloodbank.com".equalsIgnoreCase(u.getEmail()))
                .orElse(false);
    }

    @GetMapping
    public List<Donor> getAllDonors() {
        return donorRepository.findAll();
    }

    @PostMapping
    public Donor createDonor(@RequestBody Donor donor) {
        return donorRepository.save(donor);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateDonor(
            @PathVariable String id,
            @RequestHeader(value = "X-User-Id", required = false) String callerId,
            @RequestBody Donor updated) {
        if (!isCallerAdmin(callerId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Admin access required"));
        }
        return donorRepository.findById(id)
                .map(d -> {
                    d.setName(updated.getName());
                    d.setAge(updated.getAge());
                    d.setBloodGroup(updated.getBloodGroup());
                    d.setPhone(updated.getPhone());
                    d.setCity(updated.getCity());
                    return ResponseEntity.ok(donorRepository.save(d));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteDonor(
            @PathVariable String id,
            @RequestHeader(value = "X-User-Id", required = false) String callerId) {
        if (!isCallerAdmin(callerId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Admin access required"));
        }
        if (!donorRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        donorRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }
}