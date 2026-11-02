package com.payflow.account.audit;

import com.payflow.account.security.AuthenticatedUser;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Spring AOP: a cross-cutting concern (auditing) kept out of business code.
 * Works through Spring's proxy, so it only fires on calls coming from another bean
 * (self-invocation inside the same class bypasses the proxy, just like @Transactional).
 */
@Aspect
@Component
public class AuditAspect {

    private static final Logger audit = LoggerFactory.getLogger("AUDIT");

    @Around("@annotation(auditable)")
    public Object around(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        long start = System.nanoTime();
        String actor = currentActor();
        try {
            Object result = joinPoint.proceed();
            audit.info("action={} actor={} outcome=SUCCESS tookMs={}",
                    auditable.action(), actor, (System.nanoTime() - start) / 1_000_000);
            return result;
        } catch (Throwable ex) {
            audit.warn("action={} actor={} outcome=FAILURE reason={} tookMs={}",
                    auditable.action(), actor, ex.getClass().getSimpleName(), (System.nanoTime() - start) / 1_000_000);
            throw ex;
        }
    }

    private static String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
            return "user:" + user.userId();
        }
        return "system";
    }
}
