package com.bloodbank.backend.repository;

import com.bloodbank.backend.model.Receiver;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ReceiverRepository extends MongoRepository<Receiver, String> {
}