package com.example.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.student.exception.EmailAlreadyExistsException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Общий интеграционный тест: проходит по всем ручкам
 * (AuthController, StudentController, UserController) через полную цепочку
 * Spring Security + JWT. База данных - H2 в памяти (см. src/test/resources/application.yml).
 * Тестовые данные (3 пользователя и 5 студентов) создаёт AppInitializer при старте.
 */
@SpringBootTest
class ApiIntegrationTest {

    private static final String STUDENTS = "/api/v1/student";
    private static final String USERS = "/api/v1/users";
    private static final String LOGIN = "/api/v1/auth/login";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    // ---------------------------------------------------------------- helpers

    private static String loginBody(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private String loginAs(String email, String password) throws Exception {
        String body = mvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Matcher m = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
        assertThat(m.find()).as("в ответе login есть token").isTrue();
        return m.group(1);
    }

    private String admin() throws Exception {
        return loginAs("admin@example.com", "admin");
    }

    private String teacher() throws Exception {
        return loginAs("teacher@example.com", "teacher");
    }

    private String superAdmin() throws Exception {
        return loginAs("superadmin@example.com", "superAdmin");
    }

    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    private static long idOf(String json) {
        Matcher m = Pattern.compile("\"id\"\\s*:\\s*(\\d+)").matcher(json);
        assertThat(m.find()).as("в ответе есть id").isTrue();
        return Long.parseLong(m.group(1));
    }

    private long createStudent(String token, String name, String phone) throws Exception {
        String json = "{\"name\":\"" + name + "\",\"surname\":\"Tester\","
                + "\"phone_number\":\"" + phone + "\",\"email\":\"" + name + "@test.com\"}";
        String body = mvc.perform(auth(post(STUDENTS + "/save"), token).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(name))
                .andReturn().getResponse().getContentAsString();
        return idOf(body);
    }

    private long createUser(String token, String email, String password, String role) throws Exception {
        String json = "{\"email\":\"" + email + "\",\"password\":\"" + password
                + "\",\"roles\":[\"" + role + "\"]}";
        String body = mvc.perform(auth(post(USERS + "/save"), token).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andReturn().getResponse().getContentAsString();
        return idOf(body);
    }

    // ------------------------------------------------------------------- auth

    @Test
    void login_success_returnsTokenAndRoles() throws Exception {
        mvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("admin@example.com", "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.type").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_ADMIN"));
    }

    @Test
    void login_eachRoleGetsItsOwnAuthority() throws Exception {
        mvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("teacher@example.com", "teacher")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("ROLE_TEACHER"));
        mvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("superadmin@example.com", "superAdmin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("ROLE_SUPER_ADMIN"));
    }

    @Test
    void login_wrongPassword_isRejected() throws Exception {
        mvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("admin@example.com", "wrong")))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void login_unknownUser_isRejected() throws Exception {
        mvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("nobody@example.com", "whatever")))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void login_invalidEmail_returnsBadRequest() throws Exception {
        mvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("not-an-email", "admin")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_blankPassword_returnsBadRequest() throws Exception {
        mvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("admin@example.com", "")))
                .andExpect(status().isBadRequest());
    }

    // --------------------------------------------------------------- security

    @Test
    void protectedEndpoints_withoutToken_areRejected() throws Exception {
        mvc.perform(get(STUDENTS)).andExpect(status().is4xxClientError());
        mvc.perform(get(USERS)).andExpect(status().is4xxClientError());
    }

    @Test
    void protectedEndpoint_withGarbageToken_returns401() throws Exception {
        mvc.perform(get(STUDENTS).header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void token_ofDeletedUser_returns401() throws Exception {
        String sa = superAdmin();
        long id = createUser(sa, "ghost@test.com", "ghostpw", "TEACHER");
        String ghostToken = loginAs("ghost@test.com", "ghostpw");

        mvc.perform(auth(delete(USERS + "/" + id), sa)).andExpect(status().isOk());

        mvc.perform(get(STUDENTS).header("Authorization", "Bearer " + ghostToken))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------- students: read (ADMIN, TEACHER)

    @Test
    void students_findAll_asAdminAndTeacher() throws Exception {
        for (String token : new String[] {admin(), teacher()}) {
            mvc.perform(auth(get(STUDENTS), token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.response").isArray())
                    .andExpect(jsonPath("$.totalCount").isNumber())
                    .andExpect(jsonPath("$.response[0].name").isNotEmpty());
        }
    }

    @Test
    void students_findById_andInfo() throws Exception {
        String token = teacher();
        long id = createStudent(admin(), "Findme", "111");

        mvc.perform(auth(get(STUDENTS + "/" + id), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("Findme"))
                .andExpect(jsonPath("$.phoneNumber").value("111"))
                .andExpect(jsonPath("$.email").value("Findme@test.com"));

        mvc.perform(auth(get(STUDENTS + "/info/" + id), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.surname").value("Tester"));
    }

    @Test
    void students_findById_notFound_returns404() throws Exception {
        mvc.perform(auth(get(STUDENTS + "/987654"), admin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.statusCode").value(404))
                .andExpect(jsonPath("$.message", containsString("not found")));
        mvc.perform(auth(get(STUDENTS + "/info/987654"), admin()))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------- students: write (ADMIN only)

    @Test
    void students_create_update_delete_lifecycle() throws Exception {
        String token = admin();
        long id = createStudent(token, "Lifecycle", "100");

        // обновляем телефон и email
        mvc.perform(auth(put(STUDENTS + "/" + id), token)
                        .content("{\"phoneNumber\":\"555\",\"email\":\"new@test.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
        mvc.perform(auth(get(STUDENTS + "/" + id), token))
                .andExpect(jsonPath("$.phoneNumber").value("555"))
                .andExpect(jsonPath("$.email").value("new@test.com"));

        // частичное обновление: только email, телефон остаётся
        mvc.perform(auth(put(STUDENTS + "/" + id), token).content("{\"email\":\"only@test.com\"}"))
                .andExpect(status().isOk());
        mvc.perform(auth(get(STUDENTS + "/" + id), token))
                .andExpect(jsonPath("$.phoneNumber").value("555"))
                .andExpect(jsonPath("$.email").value("only@test.com"));

        // удаляем
        mvc.perform(auth(delete(STUDENTS + "/" + id), token)).andExpect(status().isOk());
        mvc.perform(auth(get(STUDENTS + "/" + id), token)).andExpect(status().isNotFound());
    }

    @Test
    void students_update_unknownId_returns404() throws Exception {
        mvc.perform(auth(put(STUDENTS + "/987654"), admin()).content("{\"email\":\"a@b.c\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void students_delete_unknownId_isOk() throws Exception {
        mvc.perform(auth(delete(STUDENTS + "/987654"), admin())).andExpect(status().isOk());
    }

    @Test
    void students_listGrowsAfterCreate() throws Exception {
        String token = admin();
        String before = mvc.perform(auth(get(STUDENTS), token)).andReturn().getResponse().getContentAsString();
        createStudent(token, "Counter", "1");
        String after = mvc.perform(auth(get(STUDENTS), token)).andReturn().getResponse().getContentAsString();
        assertThat(after.length()).isGreaterThan(before.length());
        assertThat(after).contains("Counter");
    }

    @Test
    void students_teacher_cannotWrite() throws Exception {
        String token = teacher();
        mvc.perform(auth(post(STUDENTS + "/save"), token).content("{\"name\":\"X\",\"surname\":\"Y\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(auth(put(STUDENTS + "/1"), token).content("{\"email\":\"a@b.c\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(auth(delete(STUDENTS + "/1"), token)).andExpect(status().isForbidden());
    }

    @Test
    void students_superAdmin_hasNoAccess() throws Exception {
        String token = superAdmin();
        mvc.perform(auth(get(STUDENTS), token)).andExpect(status().isForbidden());
        mvc.perform(auth(get(STUDENTS + "/1"), token)).andExpect(status().isForbidden());
        mvc.perform(auth(get(STUDENTS + "/info/1"), token)).andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------- users (SUPER_ADMIN)

    @Test
    void users_findAll_asSuperAdmin() throws Exception {
        mvc.perform(auth(get(USERS), superAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responses").isArray())
                .andExpect(jsonPath("$.totalCount").isNumber())
                .andExpect(content().string(containsString("admin@example.com")));
    }

    @Test
    void users_create_update_delete_lifecycle() throws Exception {
        String sa = superAdmin();
        long id = createUser(sa, "lifecycle@test.com", "pw12345", "TEACHER");

        // новый пользователь может войти и получает свою роль
        mvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("lifecycle@test.com", "pw12345")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("ROLE_TEACHER"));

        // меняем роль на ADMIN
        mvc.perform(auth(put(USERS + "/" + id), sa)
                        .content("{\"email\":\"lifecycle@test.com\",\"roles\":[\"ADMIN\"]}"))
                .andExpect(status().isOk());
        mvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("lifecycle@test.com", "pw12345")))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_ADMIN"));

        // удаляем - войти больше нельзя
        mvc.perform(auth(delete(USERS + "/" + id), sa)).andExpect(status().isOk());
        mvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("lifecycle@test.com", "pw12345")))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void users_create_duplicateEmail_throwsEmailAlreadyExists() throws Exception {
        String sa = superAdmin();
        createUser(sa, "dup@test.com", "pw", "TEACHER");

        // для этого исключения в GlobalExceptionHandler нет обработчика, оно уходит наверх
        assertThatThrownBy(() -> mvc.perform(auth(post(USERS + "/save"), sa)
                .content("{\"email\":\"dup@test.com\",\"password\":\"pw\",\"roles\":[\"TEACHER\"]}")))
                .hasRootCauseInstanceOf(EmailAlreadyExistsException.class);
    }

    @Test
    void users_update_unknownEmail_returns404() throws Exception {
        mvc.perform(auth(put(USERS + "/1"), superAdmin())
                        .content("{\"email\":\"missing@test.com\",\"roles\":[\"ADMIN\"]}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void users_adminAndTeacher_haveNoAccess() throws Exception {
        for (String token : new String[] {admin(), teacher()}) {
            mvc.perform(auth(get(USERS), token)).andExpect(status().isForbidden());
            mvc.perform(auth(post(USERS + "/save"), token)
                            .content("{\"email\":\"x@test.com\",\"password\":\"p\",\"roles\":[\"ADMIN\"]}"))
                    .andExpect(status().isForbidden());
            mvc.perform(auth(put(USERS + "/1"), token)
                            .content("{\"email\":\"x@test.com\",\"roles\":[\"ADMIN\"]}"))
                    .andExpect(status().isForbidden());
            mvc.perform(auth(delete(USERS + "/1"), token)).andExpect(status().isForbidden());
        }
    }
}
