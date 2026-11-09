package com.ruoyi.hospital.controller;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

/** Transaction lifecycle for controller tests with in-memory mock services, not rollback proof. */
final class PublicBookingTestTransactions
{
    private PublicBookingTestTransactions() { }

    static PlatformTransactionManager withoutResources()
    {
        return new AbstractPlatformTransactionManager() {
            @Override protected Object doGetTransaction() { return new Object(); }
            @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
            @Override protected void doCommit(DefaultTransactionStatus status) { }
            @Override protected void doRollback(DefaultTransactionStatus status) { }
        };
    }
}
