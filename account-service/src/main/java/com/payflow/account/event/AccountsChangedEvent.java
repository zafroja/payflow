package com.payflow.account.event;

import java.util.Set;

/** Published inside a transaction whenever balances or status change. */
public record AccountsChangedEvent(Set<String> accountNumbers) {
}
