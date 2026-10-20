package com.jarbis.brokerage.service;

import com.jarbis.brokerage.entity.User;
import com.jarbis.brokerage.repository.RefreshTokenRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RefreshTokenServiceContextTest {
    @Test
    void springSelectsProductionConstructorWhenTestConstructorAlsoExists() {
        RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(RefreshTokenRepository.class, () -> repository);
            context.register(RefreshTokenService.class);
            context.refresh();
            var issued = context.getBean(RefreshTokenService.class).issue(new User());
            assertNotNull(issued.rawValue());
            verify(repository).save(any());
        }
    }
}
