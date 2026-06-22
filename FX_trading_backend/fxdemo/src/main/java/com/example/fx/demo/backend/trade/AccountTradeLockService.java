package com.example.fx.demo.backend.trade;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

@Component
public class AccountTradeLockService {

    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public <T> T withAccountLock(String accountNumber, Supplier<T> action) {
        ReentrantLock lock = locks.computeIfAbsent(accountNumber, ignored -> new ReentrantLock());
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }
}
