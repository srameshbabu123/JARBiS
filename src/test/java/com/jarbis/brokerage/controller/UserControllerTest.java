package com.jarbis.brokerage.controller;

import com.jarbis.brokerage.dto.request.CreateUserRequestDto;
import com.jarbis.brokerage.dto.response.UserResponseDto;
import com.jarbis.brokerage.entity.User;
import com.jarbis.brokerage.exception.EmailAlreadyExistsException;
import com.jarbis.brokerage.exception.UserNotFoundException;
import com.jarbis.brokerage.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test cases for UserController following TDD principles.
 * Tests cover CRUD operations and edge cases.
 */
class UserControllerTest {

    private UserService userService;
    private UserController userController;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        userController = new UserController(userService);
    }

    @Nested
    class CreateUserTests {

        @Test
        void testCreateUserSuccess() {
            CreateUserRequestDto requestDto = new CreateUserRequestDto(
                    "John Doe",
                    "john@example.com",
                    "password123"
            );
            User user = new User("John Doe", "john@example.com", "hashedPassword");
            user.setId(1L);

            when(userService.createUser("John Doe", "john@example.com", "password123"))
                    .thenReturn(user);

            ResponseEntity<UserResponseDto> response = userController.createUser(requestDto);

            assertEquals(HttpStatus.CREATED, response.getStatusCode());
            assertEquals(1L, response.getBody().getId());
            assertEquals("John Doe", response.getBody().getFullName());
            assertEquals("john@example.com", response.getBody().getEmail());
            verify(userService).createUser("John Doe", "john@example.com", "password123");
        }

        @Test
        void testCreateUserWithDuplicateEmailThrowsException() {
            CreateUserRequestDto requestDto = new CreateUserRequestDto(
                    "Jane Doe",
                    "duplicate@example.com",
                    "password123"
            );

            when(userService.createUser("Jane Doe", "duplicate@example.com", "password123"))
                    .thenThrow(new EmailAlreadyExistsException("duplicate@example.com"));

            assertThrows(EmailAlreadyExistsException.class,
                    () -> userController.createUser(requestDto));
            verify(userService).createUser("Jane Doe", "duplicate@example.com", "password123");
        }

        @Test
        void testCreateUserWithNullFieldsHandledByService() {
            CreateUserRequestDto requestDto = new CreateUserRequestDto(null, null, null);
            
            // Service should handle validation
            when(userService.createUser(null, null, null))
                    .thenThrow(new IllegalArgumentException("Required fields cannot be null"));

            assertThrows(IllegalArgumentException.class,
                    () -> userController.createUser(requestDto));
        }
    }

    @Nested
    class GetUserTests {

        @Test
        void testGetUserByIdSuccess() {
            User user = new User("Alice", "alice@example.com", "hashedPassword");
            user.setId(5L);

            when(userService.getUserById(5L)).thenReturn(user);

            ResponseEntity<UserResponseDto> response = userController.getUserById(5L);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(5L, response.getBody().getId());
            assertEquals("Alice", response.getBody().getFullName());
            verify(userService).getUserById(5L);
        }

        @Test
        void testGetUserByIdNotFoundThrowsException() {
            when(userService.getUserById(999L))
                    .thenThrow(new UserNotFoundException("User not found with id: 999"));

            assertThrows(UserNotFoundException.class,
                    () -> userController.getUserById(999L));
            verify(userService).getUserById(999L);
        }
    }

    @Nested
    class UpdateUserTests {

        @Test
        void testUpdateUserSuccess() {
            CreateUserRequestDto requestDto = new CreateUserRequestDto(
                    "Alice Updated",
                    "alice.updated@example.com",
                    null // Password not used in update
            );
            User updatedUser = new User("Alice Updated", "alice.updated@example.com", "hashedPassword");
            updatedUser.setId(5L);

            when(userService.updateUser(5L, "Alice Updated", "alice.updated@example.com"))
                    .thenReturn(updatedUser);

            ResponseEntity<UserResponseDto> response = userController.updateUser(5L, requestDto);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals("Alice Updated", response.getBody().getFullName());
            assertEquals("alice.updated@example.com", response.getBody().getEmail());
            verify(userService).updateUser(5L, "Alice Updated", "alice.updated@example.com");
        }

        @Test
        void testUpdateUserToExistingEmailThrowsException() {
            CreateUserRequestDto requestDto = new CreateUserRequestDto(
                    "Bob",
                    "existing@example.com",
                    null
            );

            when(userService.updateUser(5L, "Bob", "existing@example.com"))
                    .thenThrow(new EmailAlreadyExistsException("existing@example.com"));

            assertThrows(EmailAlreadyExistsException.class,
                    () -> userController.updateUser(5L, requestDto));
        }

        @Test
        void testUpdateNonExistentUserThrowsException() {
            CreateUserRequestDto requestDto = new CreateUserRequestDto("Bob", "bob@example.com", null);

            when(userService.updateUser(999L, "Bob", "bob@example.com"))
                    .thenThrow(new UserNotFoundException("User not found with id: 999"));

            assertThrows(UserNotFoundException.class,
                    () -> userController.updateUser(999L, requestDto));
        }
    }

    @Nested
    class DeleteUserTests {

        @Test
        void testDeleteUserSuccess() {
            ResponseEntity<Void> response = userController.deleteUser(5L);

            assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
            verify(userService).deleteUser(5L);
        }

        @Test
        void testDeleteNonExistentUserThrowsException() {
            doThrow(new UserNotFoundException("User not found with id: 999"))
                    .when(userService).deleteUser(999L);

            assertThrows(UserNotFoundException.class,
                    () -> userController.deleteUser(999L));
            verify(userService).deleteUser(999L);
        }
    }

    @Nested
    class GetUserBalanceTests {

        @Test
        void testGetUserBalanceSuccess() {
            when(userService.getUserBalance(5L)).thenReturn(15000.0);

            ResponseEntity<Double> response = userController.getUserBalance(5L);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(15000.0, response.getBody());
            verify(userService).getUserBalance(5L);
        }

        @Test
        void testGetUserBalanceForUserWithNoAccounts() {
            when(userService.getUserBalance(6L)).thenReturn(0.0);

            ResponseEntity<Double> response = userController.getUserBalance(6L);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(0.0, response.getBody());
            verify(userService).getUserBalance(6L);
        }
    }
}

