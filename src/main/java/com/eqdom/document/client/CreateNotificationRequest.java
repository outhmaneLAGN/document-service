package com.eqdom.document.client;

public record CreateNotificationRequest(Long recipientUserId, String recipientEmail, String type,
                                         String titre, String message, String entiteType, Long entiteId) {
}
