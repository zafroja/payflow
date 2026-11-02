package com.payflow.account.event;

public record AccountOpenedEvent(String accountNumber, Long userId, String type) {
}
