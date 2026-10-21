package com.jarbis.brokerage.service;

import com.jarbis.brokerage.entity.Account;
import com.jarbis.brokerage.entity.User;
import com.jarbis.brokerage.enums.AccountType;
import com.jarbis.brokerage.enums.Currency;
import com.jarbis.brokerage.repository.AccountRepository;
import com.jarbis.brokerage.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** Real PostgreSQL: H2/mocks do not reproduce a write lock in a read-only transaction. */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ClientAccess.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfEnvironmentVariable(named = "JARBIS_TEST_POSTGRES_URL", matches = ".+")
class ClientAccessPostgresTest {
    @Autowired ClientAccess access;
    @Autowired UserRepository users;
    @Autowired AccountRepository accounts;
    @Autowired PlatformTransactionManager transactions;
    private Long userId;
    private Long accountId;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        // This test is run only against the isolated disposable CI database.
        registry.add("spring.datasource.url", () -> System.getenv("JARBIS_TEST_POSTGRES_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("JARBIS_TEST_POSTGRES_USER"));
        registry.add("spring.datasource.password", () -> System.getenv("JARBIS_TEST_POSTGRES_PASSWORD"));
    }

    @BeforeEach void createOwnedAccount() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            User user = users.save(new User("Ownership Test", UUID.randomUUID() + "@example.test", "not-a-login"));
            Account account = accounts.save(new Account(AccountType.INVESTMENT, Currency.USD, 0.0, user));
            userId = user.getId(); accountId = account.getId();
        });
    }

    @Test void ownershipCheckWorksInReadOnlyPostgresTransaction() {
        TransactionTemplate read = new TransactionTemplate(transactions);
        read.setReadOnly(true);
        read.executeWithoutResult(status -> {
            assertTrue(access.ownsAccount(userId, accountId));
            assertFalse(access.ownsAccount(userId + 1000000, accountId));
            assertFalse(access.ownsAccount(userId, accountId + 1000000));
        });
    }

    @Test void existingAccountMutationLockStillWorksInWritableTransaction() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            Account account = accounts.findById(accountId).orElseThrow();
            account.setBalance(25.0);
            accounts.flush();
        });
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            assertEquals(25.0, accounts.findById(accountId).orElseThrow().getBalance());
        });
    }
}
