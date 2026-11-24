package com.bloodbank.backend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "receivers")
public class Receiver {

    @Id
    private String id;
    private String trackingId;
    private String name;
    private String bloodGroup;
    private int unitsRequired;
    private String urgencyLevel = "Normal"; // Normal, Urgent, Emergency
    private String requiredByDate;
    private String phone;
    private String city;
    private String status = "Pending"; // Pending, Approved, Rejected, Partially Fulfilled, Fulfilled

    public Receiver() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTrackingId() { return trackingId; }
    public void setTrackingId(String trackingId) { this.trackingId = trackingId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getBloodGroup() { return bloodGroup; }
    public void setBloodGroup(String bloodGroup) { this.bloodGroup = bloodGroup; }

    public int getUnitsRequired() { return unitsRequired; }
    public void setUnitsRequired(int unitsRequired) { this.unitsRequired = unitsRequired; }

    public String getUrgencyLevel() { return urgencyLevel; }
    public void setUrgencyLevel(String urgencyLevel) { this.urgencyLevel = urgencyLevel; }

    public String getRequiredByDate() { return requiredByDate; }
    public void setRequiredByDate(String requiredByDate) { this.requiredByDate = requiredByDate; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}