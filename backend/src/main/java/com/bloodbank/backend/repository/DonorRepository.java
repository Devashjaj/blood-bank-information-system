package com.bloodbank.backend.repository;

import com.bloodbank.backend.model.Donor;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface DonorRepository extends MongoRepository<Donor, String> {
}
