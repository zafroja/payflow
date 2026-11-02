package com.payflow.account.event;

import com.payflow.account.service.AccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Spring application events decouple side effects from the core write path.
 */
@Component
public class AccountEventListeners {

    private static final Logger log = LoggerFactory.getLogger(AccountEventListeners.class);

    private final CacheManager cacheManager;

    public AccountEventListeners(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    /**
     * Evict AFTER the commit, not inside the transaction. Evicting earlier opens a race:
     * another request could re-read the old balance from the DB and put it back in Redis
     * before our commit lands, leaving a stale cache entry.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void evictChangedAccounts(AccountsChangedEvent event) {
        Cache cache = cacheManager.getCache(AccountService.ACCOUNTS_CACHE);
        if (cache != null) {
            event.accountNumbers().forEach(cache::evict);
        }
    }

    /** Runs on a background thread after commit; a slow notification never delays the API call. */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendWelcomeNotification(AccountOpenedEvent event) {
        log.info("Notify user {}: your {} account {} is open", event.userId(), event.type(), event.accountNumber());
    }
}
