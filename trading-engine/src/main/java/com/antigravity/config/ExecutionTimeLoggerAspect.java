package com.antigravity.config;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class ExecutionTimeLoggerAspect {

    private static final Logger log = LoggerFactory.getLogger(ExecutionTimeLoggerAspect.class);

    @Around("execution(* com.antigravity.agents..*(..))")
    public Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();

        Object proceed = joinPoint.proceed();

        long executionTime = System.currentTimeMillis() - start;

        log.debug("[Observability] {} executed in {} ms", joinPoint.getSignature(), executionTime);
        return proceed;
    }
}
