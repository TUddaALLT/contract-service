package com.fpt.servicecontract.contract.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;

@Entity
@Table
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {
    @Id
    @UuidGenerator
    private String id;
    private String title;
    private String message;
    private String entityId;
    private String senderId;
    private String recipientId;
    private LocalDateTime createdDate;
    private Boolean markedDeleted;
    private Boolean markRead;
}
