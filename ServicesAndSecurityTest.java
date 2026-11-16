package com.example.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.student.dto.StudentRequest;
import com.example.student.dto.StudentResponse;
import com.example.student.dto.StudentUpdateRequest;
import com.example.student.dto.user.UserRequest;
import com.example.student.dto.user.UserResponse;
import com.example.student.exception.EmailAlreadyExistsException;
import com.example.student.exception.EntityNotFoundException;
import com.example.student.model.Role;
import com.example.student.model.User;
import com.example.student.security.JwtService;
import com.example.student.service.CustomUserDetailsService;
import com.example.student.service.StudentService;
import com.example.student.service.UserService;
import io.jsonwebtoken.JwtException;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/** Проверки сервисного слоя, JWT и загрузки пользователей напрямую, без HTTP. */
@SpringBootTest
class ServicesAndSecurityTest {

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserService userService;

    @Autowired
    private StudentService studentService;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    // -------------------------------------------------------------------- JWT

    @Test
    void jwt_generateAndParse() {
        String token = jwtService.generateToken("a@test.com", Set.of(Role.ADMIN, Role.TEACHER));

        assertThat(jwtService.extractUsername(token)).isEqualTo("a@test.com");
        assertThat(jwtService.extractRoles(token)).containsExactlyInAnyOrder(Role.ADMIN, Role.TEACHER);
    }

    @Test
    void jwt_validateToken_checksUsername() {
        String token = jwtService.generateToken("a@test.com", Set.of(Role.ADMIN));
        UserDetails same = org.springframework.security.core.userdetails.User
                .withUsername("a@test.com").password("x").authorities("ROLE_ADMIN").build();
        UserDetails other = org.springframework.security.core.userdetails.User
                .withUsername("b@test.com").password("x").authorities("ROLE_ADMIN").build();

        assertThat(jwtService.validateToken(token, same)).isTrue();
        assertThat(jwtService.validateToken(token, other)).isFalse();
    }

    @Test
    void jwt_tamperedToken_isRejected() {
        String token = jwtService.generateToken("a@test.com", Set.of(Role.ADMIN));

        assertThatThrownBy(() -> jwtService.extractUsername(token + "x")).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> jwtService.extractUsername("garbage")).isInstanceOf(JwtException.class);
    }

    // ---------------------------------------------------- CustomUserDetailsService

    @Test
    void userDetails_loadsExistingUser() {
        UserDetails details = userDetailsService.loadUserByUsername("admin@example.com");

        assertThat(details.getUsername()).isEqualTo("admin@example.com");
        assertThat(details.getAuthorities().stream().map(Object::toString).toList()).containsExactly("ROLE_ADMIN");
        assertThat(details.getPassword()).isNotEqualTo("admin"); // хранится хэш
    }

    @Test
    void userDetails_unknownUser_throws() {
        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("nobody@test.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    // -------------------------------------------------------------- UserService

    @Test
    void userService_existsAndFindByEmail() {
        assertThat(userService.existsByEmail("teacher@example.com")).isTrue();
        assertThat(userService.existsByEmail("missing@test.com")).isFalse();

        User found = userService.findByEmail("teacher@example.com");
        assertThat(found.getRoles()).containsExactly(Role.TEACHER);

        assertThatThrownBy(() -> userService.findByEmail("missing@test.com"))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void userService_register() {
        UserRequest request = new UserRequest("registered@test.com", "secret", new HashSet<>(Set.of(Role.TEACHER)));

        assertThat(userService.register(request)).isTrue();
        assertThat(userService.existsByEmail("registered@test.com")).isTrue();
        // повторная регистрация и пустой email не проходят
        assertThat(userService.register(request)).isFalse();
        assertThat(userService.register(new UserRequest(null, "secret", new HashSet<>()))).isFalse();
    }

    @Test
    void userService_register_withoutPassword_keepsItEmpty() {
        UserRequest request = new UserRequest("nopass@test.com", "", new HashSet<>(Set.of(Role.TEACHER)));

        assertThat(userService.register(request)).isTrue();
        assertThat(userService.findByEmail("nopass@test.com").getPassword()).isEmpty();
    }

    @Test
    void userService_saveUpdateDelete() {
        UserResponse saved = userService.save(
                new UserRequest("svc@test.com", "pw", new HashSet<>(Set.of(Role.TEACHER))));
        assertThat(saved.getId()).isPositive();
        assertThat(saved.getRoles()).containsExactly(Role.TEACHER);

        assertThatThrownBy(() -> userService.save(
                new UserRequest("svc@test.com", "pw", new HashSet<>(Set.of(Role.ADMIN)))))
                .isInstanceOf(EmailAlreadyExistsException.class);

        userService.updateUser(new UserRequest("svc@test.com", null, new HashSet<>(Set.of(Role.ADMIN))));
        assertThat(userService.findByEmail("svc@test.com").getRoles()).containsExactly(Role.ADMIN);

        assertThat(userService.findAll().getResponses()).extracting(UserResponse::getEmail).contains("svc@test.com");

        userService.deleteById(saved.getId());
        assertThat(userService.existsByEmail("svc@test.com")).isFalse();
    }

    // ----------------------------------------------------------- StudentService

    @Test
    void studentService_saveUpdateDelete() {
        StudentResponse saved = studentService.save(new StudentRequest("Svc", "Student", "123", "svc@student.com"));
        assertThat(saved.getName()).isEqualTo("Svc");

        studentService.update(saved.getId(), new StudentUpdateRequest("999", null));
        assertThat(studentService.findById(saved.getId()).getPhoneNumber()).isEqualTo("999");
        assertThat(studentService.findById(saved.getId()).getEmail()).isEqualTo("svc@student.com");

        assertThat(studentService.findAll().getTotalCount()).isPositive();

        studentService.deleteById(saved.getId());
        assertThatThrownBy(() -> studentService.findById(saved.getId()))
                .isInstanceOf(EntityNotFoundException.class);
    }
}
