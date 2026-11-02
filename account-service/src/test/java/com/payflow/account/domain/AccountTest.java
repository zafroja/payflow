package com.payflow.account.domain;

import com.payflow.account.exception.AccountNotActiveException;
import com.payflow.account.exception.InsufficientFundsException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Plain unit tests: the money rules live in the entity, so no Spring context is needed. */
class AccountTest {

    private Account newAccount() {
        return new Account("PF0000000001", AccountType.CHEQUING, "CAD");
    }

    @Test
    void creditThenDebitUpdatesBalance() {
        Account account = newAccount();
        account.credit(new BigDecimal("100.00"));
        account.debit(new BigDecimal("40.25"));
        assertThat(account.getBalance()).isEqualByComparingTo("59.75");
    }

    @Test
    void cannotOverdraw() {
        Account account = newAccount();
        account.credit(new BigDecimal("10.00"));
        assertThatThrownBy(() -> account.debit(new BigDecimal("10.01")))
                .isInstanceOf(InsufficientFundsException.class);
        assertThat(account.getBalance()).isEqualByComparingTo("10.00");
    }

    @Test
    void frozenAccountRejectsMovements() {
        Account account = newAccount();
        account.freeze();
        assertThatThrownBy(() -> account.credit(BigDecimal.ONE))
                .isInstanceOf(AccountNotActiveException.class);
    }

    @Test
    void rejectsNonPositiveAmounts() {
        Account account = newAccount();
        assertThatThrownBy(() -> account.credit(BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
