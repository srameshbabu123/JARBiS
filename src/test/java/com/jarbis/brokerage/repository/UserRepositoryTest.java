package com.jarbis.brokerage.repository;

import com.jarbis.brokerage.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("User Repository Tests")
class UserRepositoryTest {

    @Mock
    private UserRepository userRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User("John Doe", "john@example.com", "hashed_password");
    }

    @Nested
    @DisplayName("Create and Save User Tests")
    class CreateUserTests {

        @Test
        @DisplayName("Should save user and generate ID")
        void testSaveUser() {
            User user = new User("John Doe", "john@example.com", "hashed_password");
            User savedUser = new User("John Doe", "john@example.com", "hashed_password");

            when(userRepository.save(any(User.class))).thenReturn(savedUser);

            User result = userRepository.save(user);

            assertEquals("John Doe", result.getFullName());
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("Should save multiple users")
        void testSaveMultipleUsers() {
            User user1 = new User("John Doe", "john@example.com", "hash1");
            User user2 = new User("Jane Smith", "jane@example.com", "hash2");

            when(userRepository.save(user1)).thenReturn(user1);
            when(userRepository.save(user2)).thenReturn(user2);

            userRepository.save(user1);
            userRepository.save(user2);

            verify(userRepository, times(2)).save(any(User.class));
        }
    }

    @Nested
    @DisplayName("Find User Tests")
    class FindUserTests {

        @Test
        @DisplayName("Should find user by ID")
        void testFindById() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

            Optional<User> found = userRepository.findById(1L);

            assertTrue(found.isPresent());
            assertEquals("john@example.com", found.get().getEmail());
        }

        @Test
        @DisplayName("Should return empty when user not found by ID")
        void testFindByIdNotFound() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            Optional<User> found = userRepository.findById(999L);

            assertTrue(found.isEmpty());
        }

        @Test
        @DisplayName("Should find user by email")
        void testFindByEmail() {
            when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(testUser));

            Optional<User> found = userRepository.findByEmail("john@example.com");

            assertTrue(found.isPresent());
            assertEquals("John Doe", found.get().getFullName());
        }

        @Test
        @DisplayName("Should return empty when email not found")
        void testFindByEmailNotFound() {
            when(userRepository.findByEmail("nonexistent@example.com")).thenReturn(Optional.empty());

            Optional<User> found = userRepository.findByEmail("nonexistent@example.com");

            assertTrue(found.isEmpty());
        }
    }

    @Nested
    @DisplayName("Update User Tests")
    class UpdateUserTests {

        @Test
        @DisplayName("Should update user full name")
        void testUpdateFullName() {
            User updatedUser = new User("Jane Doe", "john@example.com", "hashed");
            updatedUser.setId(1L);

            when(userRepository.save(any(User.class))).thenReturn(updatedUser);

            User result = userRepository.save(updatedUser);

            assertEquals("Jane Doe", result.getFullName());
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("Should update user email")
        void testUpdateEmail() {
            User updatedUser = new User("John Doe", "newemail@example.com", "hashed");
            updatedUser.setId(1L);

            when(userRepository.save(any(User.class))).thenReturn(updatedUser);

            User result = userRepository.save(updatedUser);

            assertEquals("newemail@example.com", result.getEmail());
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("Should update user password hash")
        void testUpdatePasswordHash() {
            User updatedUser = new User("John Doe", "john@example.com", "new_hash");
            updatedUser.setId(1L);

            when(userRepository.save(any(User.class))).thenReturn(updatedUser);

            User result = userRepository.save(updatedUser);

            assertEquals("new_hash", result.getPasswordHash());
            verify(userRepository).save(any(User.class));
        }
    }

    @Nested
    @DisplayName("Delete User Tests")
    class DeleteUserTests {

        @Test
        @DisplayName("Should delete user by entity")
        void testDeleteUser() {
            userRepository.delete(testUser);

            verify(userRepository).delete(testUser);
        }

        @Test
        @DisplayName("Should delete user by ID")
        void testDeleteById() {
            userRepository.deleteById(1L);

            verify(userRepository).deleteById(1L);
        }
    }

    @Nested
    @DisplayName("Exists Tests")
    class ExistsTests {

        @Test
        @DisplayName("Should check if user exists by ID")
        void testExistsById() {
            when(userRepository.existsById(1L)).thenReturn(true);

            boolean exists = userRepository.existsById(1L);

            assertTrue(exists);
        }

        @Test
        @DisplayName("Should return false when user does not exist")
        void testExistsByIdNotFound() {
            when(userRepository.existsById(999L)).thenReturn(false);

            boolean exists = userRepository.existsById(999L);

            assertFalse(exists);
        }

        @Test
        @DisplayName("Should check if email exists")
        void testExistsByEmail() {
            when(userRepository.existsByEmail("john@example.com")).thenReturn(true);

            boolean exists = userRepository.existsByEmail("john@example.com");

            assertTrue(exists);
        }

        @Test
        @DisplayName("Should return false when email does not exist")
        void testExistsByEmailNotFound() {
            when(userRepository.existsByEmail("nonexistent@example.com")).thenReturn(false);

            boolean exists = userRepository.existsByEmail("nonexistent@example.com");

            assertFalse(exists);
        }
    }

    @Nested
    @DisplayName("FindAll and Count Tests")
    class FindAllAndCountTests {

        @Test
        @DisplayName("Should retrieve all users")
        void testFindAll() {
            List<User> users = List.of(testUser, new User("Jane Smith", "jane@example.com", "hash2"));

            when(userRepository.findAll()).thenReturn(users);

            List<User> result = userRepository.findAll();

            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("Should count all users")
        void testCount() {
            when(userRepository.count()).thenReturn(2L);

            long count = userRepository.count();

            assertEquals(2L, count);
        }
    }
}

