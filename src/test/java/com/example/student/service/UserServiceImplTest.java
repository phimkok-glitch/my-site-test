package com.example.student.service;

import com.example.student.dto.user.UserRequest;
import com.example.student.dto.user.UserResponse;
import com.example.student.exception.EmailAlreadyExistsException;
import com.example.student.exception.EntityNotFoundException;
import com.example.student.mapper.UserMapper;
import com.example.student.model.Role;
import com.example.student.model.User;
import com.example.student.repository.UserRepository;
import com.example.student.service.impl.UserServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    void saveUser() {
        UserRequest request = new UserRequest("user@mail.com", "123", Set.of(Role.TEACHER));
        UserResponse response = new UserResponse();

        when(userRepository.existsByEmail("user@mail.com")).thenReturn(false);
        when(passwordEncoder.encode("123")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(userMapper.toResponse(any(User.class))).thenReturn(response);

        assertEquals(response, userService.save(request));
        verify(passwordEncoder).encode("123");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void saveUserWithExistingEmail() {
        UserRequest request = new UserRequest("user@mail.com", "123", Set.of(Role.TEACHER));

        when(userRepository.existsByEmail("user@mail.com")).thenReturn(true);

        assertThrows(EmailAlreadyExistsException.class, () -> userService.save(request));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void findByEmailNotFound() {
        when(userRepository.findByEmail("none@mail.com")).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> userService.findByEmail("none@mail.com"));
    }

    @Test
    void updateUserRoles() {
        User user = new User();
        user.setEmail("user@mail.com");
        user.setRoles(Set.of(Role.TEACHER));
        UserRequest request = new UserRequest("user@mail.com", null, Set.of(Role.ADMIN));

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(userMapper.toResponse(user)).thenReturn(new UserResponse());

        userService.updateUser(1L, request);

        assertEquals(Set.of(Role.ADMIN), user.getRoles());
        verify(userRepository).save(user);
    }

    @Test
    void updateUserNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> userService.updateUser(1L, new UserRequest()));
    }

    @Test
    void deleteUser() {
        userService.deleteById(1L);

        verify(userRepository).deleteById(1L);
    }
}
