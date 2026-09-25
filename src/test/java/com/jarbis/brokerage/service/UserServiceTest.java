package com.jarbis.brokerage.service;

import com.jarbis.brokerage.entity.User;
import com.jarbis.brokerage.exception.EmailAlreadyExistsException;
import com.jarbis.brokerage.exception.UserNotFoundException;
import com.jarbis.brokerage.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Service layer tests for User management following TDD principles.
 * Tests cover all business logic and edge cases.
 */
class UserServiceTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        userService = new UserService(userRepository, passwordEncoder);
    }

    @Nested
    class CreateUserTests {

        @Test
        void testCreateUserSuccessfully() {
            String fullName = "John Doe";
            String email = "john@example.com";
            String password = "plainPassword123";

            when(userRepository.existsByEmail(email)).thenReturn(false);
            when(passwordEncoder.encode(password)).thenReturn("hashedPassword123");
            
            User savedUser = new User(fullName, email, "hashedPassword123");
            savedUser.setId(1L);
            when(userRepository.save(any(User.class))).thenReturn(savedUser);

            User result = userService.createUser(fullName, email, password);

            assertNotNull(result);
            assertEquals(1L, result.getId());
            assertEquals(fullName, result.getFullName());
            assertEquals(email, result.getEmail());
            verify(userRepository).existsByEmail(email);
            verify(passwordEncoder).encode(password);
            verify(userRepository).save(any(User.class));
        }

        @Test
        void testCreateUserWithDuplicateEmailThrowsException() {
            String email = "duplicate@example.com";
            
            when(userRepository.existsByEmail(email)).thenReturn(true);

            assertThrows(EmailAlreadyExistsException.class,
                    () -> userService.createUser("John", email, "password"));
            
            verify(userRepository).existsByEmail(email);
            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        void testCreateUserWithEmptyEmail() {
            when(userRepository.existsByEmail("")).thenReturn(false);
            when(passwordEncoder.encode("password")).thenReturn("hashed");
            
            User savedUser = new User("John", "", "hashed");
            savedUser.setId(1L);
            when(userRepository.save(any(User.class))).thenReturn(savedUser);

            User result = userService.createUser("John", "", "password");

            assertNotNull(result);
            assertEquals("", result.getEmail());
        }

        @Test
        void testCreateUserWithSpecialCharactersInName() {
            String specialName = "José María O'Connor";
            String email = "jose@example.com";
            
            when(userRepository.existsByEmail(email)).thenReturn(false);
            when(passwordEncoder.encode("password")).thenReturn("hashed");
            
            User savedUser = new User(specialName, email, "hashed");
            savedUser.setId(1L);
            when(userRepository.save(any(User.class))).thenReturn(savedUser);

            User result = userService.createUser(specialName, email, "password");

            assertEquals(specialName, result.getFullName());
        }
    }

    @Nested
    class GetUserTests {

        @Test
        void testGetUserByIdSuccess() {
            User user = new User("John", "john@example.com", "hashed");
            user.setId(1L);

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            User result = userService.getUserById(1L);

            assertNotNull(result);
            assertEquals(1L, result.getId());
            assertEquals("John", result.getFullName());
            verify(userRepository).findById(1L);
        }

        @Test
        void testGetUserByIdNotFoundThrowsException() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(UserNotFoundException.class,
                    () -> userService.getUserById(999L));
            verify(userRepository).findById(999L);
        }

        @Test
        void testGetUserByEmailSuccess() {
            User user = new User("Jane", "jane@example.com", "hashed");
            user.setId(2L);

            when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));

            User result = userService.getUserByEmail("jane@example.com");

            assertNotNull(result);
            assertEquals(2L, result.getId());
            verify(userRepository).findByEmail("jane@example.com");
        }

        @Test
        void testGetUserByEmailNotFoundThrowsException() {
            when(userRepository.findByEmail("nonexistent@example.com")).thenReturn(Optional.empty());

            assertThrows(UserNotFoundException.class,
                    () -> userService.getUserByEmail("nonexistent@example.com"));
        }
    }

    @Nested
    class UpdateUserTests {

        @Test
        void testUpdateUserSuccessfully() {
            User existingUser = new User("Old Name", "old@example.com", "hashed");
            existingUser.setId(1L);

            when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));
            when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
            when(userRepository.save(any(User.class))).thenReturn(existingUser);

            userService.updateUser(1L, "New Name", "new@example.com");

            assertEquals("New Name", existingUser.getFullName());
            assertEquals("new@example.com", existingUser.getEmail());
            verify(userRepository).save(existingUser);
        }

        @Test
        void testUpdateUserToExistingEmailThrowsException() {
            User existingUser = new User("John", "john@example.com", "hashed");
            existingUser.setId(1L);

            when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));
            when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

            assertThrows(EmailAlreadyExistsException.class,
                    () -> userService.updateUser(1L, "John", "taken@example.com"));

            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        void testUpdateUserWithNullValuesPreservesExisting() {
            User existingUser = new User("John", "john@example.com", "hashed");
            existingUser.setId(1L);

            when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));
            when(userRepository.save(any(User.class))).thenReturn(existingUser);

            userService.updateUser(1L, null, null);

            assertEquals("John", existingUser.getFullName());
            assertEquals("john@example.com", existingUser.getEmail());
        }

        @Test
        void testUpdateUserNonExistentThrowsException() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(UserNotFoundException.class,
                    () -> userService.updateUser(999L, "Name", "email@example.com"));
        }
    }

    @Nested
    class DeleteUserTests {

        @Test
        void testDeleteUserSuccessfully() {
            User user = new User("John", "john@example.com", "hashed");
            user.setId(1L);

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            userService.deleteUser(1L);

            verify(userRepository).delete(user);
        }

        @Test
        void testDeleteNonExistentUserThrowsException() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(UserNotFoundException.class,
                    () -> userService.deleteUser(999L));

            verify(userRepository, never()).delete(any(User.class));
        }
    }

    @Nested
    class VerifyPasswordTests {

        @Test
        void testVerifyPasswordSuccess() {
            User user = new User("John", "john@example.com", "hashedPassword123");

            when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("plainPassword", "hashedPassword123")).thenReturn(true);

            boolean result = userService.verifyPassword("john@example.com", "plainPassword");

            assertTrue(result);
            verify(passwordEncoder).matches("plainPassword", "hashedPassword123");
        }

        @Test
        void testVerifyPasswordFailsWithWrongPassword() {
            User user = new User("John", "john@example.com", "hashedPassword123");

            when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrongPassword", "hashedPassword123")).thenReturn(false);

            boolean result = userService.verifyPassword("john@example.com", "wrongPassword");

            assertFalse(result);
        }

        @Test
        void testVerifyPasswordWithNonExistentUserReturnsFalse() {
            when(userRepository.findByEmail("nonexistent@example.com")).thenReturn(Optional.empty());

            boolean result = userService.verifyPassword("nonexistent@example.com", "password");

            assertFalse(result);
            verify(passwordEncoder, never()).matches(anyString(), anyString());
        }
    }

    @Nested
    class VerifyUserExistsTests {

        @Test
        void testVerifyUserExistsSuccess() {
            when(userRepository.existsById(1L)).thenReturn(true);

            assertDoesNotThrow(() -> userService.verifyUserExists(1L));

            verify(userRepository).existsById(1L);
        }

        @Test
        void testVerifyUserExistsThrowsExceptionWhenNotFound() {
            when(userRepository.existsById(999L)).thenReturn(false);

            assertThrows(UserNotFoundException.class,
                    () -> userService.verifyUserExists(999L));
        }
    }
}

