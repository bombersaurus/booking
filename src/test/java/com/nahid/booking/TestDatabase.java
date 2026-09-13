package com.nahid.booking;

import org.springframework.jdbc.core.JdbcTemplate;

/** Fixtures for the disposable Testcontainers database only. Never point these at the development database. */
final class TestDatabase {
    private TestDatabase() {}

    /** Empties every table and recreates the ISSUED and RESERVED system accounts. */
    static void reset(JdbcTemplate jdbc) {
        jdbc.execute("""
                TRUNCATE ledger_entries, credit_transactions, credit_accounts, bookings, class_sessions, users
                RESTART IDENTITY CASCADE""");
        jdbc.update("INSERT INTO credit_accounts (kind) VALUES ('ISSUED'), ('RESERVED')");
    }

    /** Adds a member with a credit account and, like migration 3, a GRANT of their starting credits. */
    static long addMember(JdbcTemplate jdbc, String email, long credits) {
        Long user = jdbc.queryForObject("INSERT INTO users (email) VALUES (?) RETURNING id", Long.class, email);
        Long account = jdbc.queryForObject(
                "INSERT INTO credit_accounts (user_id, kind) VALUES (?, 'MEMBER') RETURNING id", Long.class, user);
        Long issued = jdbc.queryForObject("SELECT id FROM credit_accounts WHERE kind = 'ISSUED'", Long.class);
        Long grant = jdbc.queryForObject("INSERT INTO credit_transactions (kind) VALUES ('GRANT') RETURNING id", Long.class);
        jdbc.update("INSERT INTO ledger_entries (transaction_id, account_id, amount) VALUES (?, ?, ?), (?, ?, ?)",
                grant, issued, -credits, grant, account, credits);
        jdbc.update("UPDATE credit_accounts SET balance = balance - ? WHERE id = ?", credits, issued);
        jdbc.update("UPDATE credit_accounts SET balance = balance + ? WHERE id = ?", credits, account);
        return user;
    }

    static long addClass(JdbcTemplate jdbc, String name, int capacity, long creditCost) {
        return jdbc.queryForObject("""
                INSERT INTO class_sessions (name, starts_at, capacity, credit_cost)
                VALUES (?, now() + interval '2 days', ?, ?) RETURNING id""", Long.class, name, capacity, creditCost);
    }

    /** Ledger transactions whose entries do not sum to zero. */
    static long unbalancedTransactions(JdbcTemplate jdbc) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM (SELECT transaction_id FROM ledger_entries
                GROUP BY transaction_id HAVING sum(amount) <> 0) unbalanced""", Long.class);
    }

    /** Accounts whose stored balance differs from the sum of their ledger entries, e.g. after a lost update. */
    static long driftedAccounts(JdbcTemplate jdbc) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM credit_accounts a WHERE a.balance <>
                COALESCE((SELECT sum(e.amount) FROM ledger_entries e WHERE e.account_id = a.id), 0)""", Long.class);
    }
}
