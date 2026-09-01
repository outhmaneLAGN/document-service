package com.eqdom.document.client;

public record AuditEventRequest(String action, String entite, Long entiteId,
                                 String ancienneValeur, String nouvelleValeur) {
}
