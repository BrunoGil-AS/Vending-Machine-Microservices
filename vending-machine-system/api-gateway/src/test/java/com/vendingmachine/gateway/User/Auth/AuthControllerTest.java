
package com.vendingmachine.gateway.User.Auth;

import com.vendingmachine.gateway.User.Login.DTO.LoginRequest;
import com.vendingmachine.gateway.User.Login.DTO.LoginResponse;
import com.vendingmachine.gateway.User.DTO.CreateUserRequest;
import com.vendingmachine.gateway.User.DTO.UpdateUserRequest;
import com.vendingmachine.gateway.User.DTO.UserResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@WebFluxTest(AuthController.class)
public class AuthControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private AuthService authService;

    @Test
    void login_successful() {
        LoginRequest loginRequest = new LoginRequest("admin", "admin123");
        LoginResponse loginResponse = LoginResponse.builder().token("test-token").build();
        when(authService.login(any(LoginRequest.class))).thenReturn(Mono.just(loginResponse));

        webTestClient.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(loginRequest)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.token").isEqualTo("test-token");
    }

    @Test
    void login_failure() {
        LoginRequest loginRequest = new LoginRequest("admin", "wrongpassword");
        when(authService.login(any(LoginRequest.class))).thenReturn(Mono.error(new RuntimeException("Invalid credentials")));

        webTestClient.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(loginRequest)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.message").isEqualTo("Invalid credentials");
    }

    @Test
    void createUser_successful() {
        CreateUserRequest createUserRequest = new CreateUserRequest("newuser", "password", "ADMIN");
        UserResponse userResponse = UserResponse.builder().id(1L).username("newuser").role("ADMIN").build();
        when(authService.createUser(any(CreateUserRequest.class))).thenReturn(Mono.just(userResponse));

        webTestClient.post().uri("/api/auth/users")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createUserRequest)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.username").isEqualTo("newuser");
    }

    @Test
    void updateUser_successful() {
        UpdateUserRequest updateUserRequest = new UpdateUserRequest("newpassword", "ADMIN", true);
        UserResponse userResponse = UserResponse.builder().id(1L).username("user").role("ADMIN").active(true).build();
        when(authService.updateUser(any(Long.class), any(UpdateUserRequest.class))).thenReturn(Mono.just(userResponse));

        webTestClient.put().uri("/api/auth/users/1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(updateUserRequest)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.active").isEqualTo(true);
    }

    @Test
    void deleteUser_successful() {
        when(authService.deleteUser(any(Long.class))).thenReturn(Mono.empty());

        webTestClient.delete().uri("/api/auth/users/1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.message").isEqualTo("User deleted successfully");
    }

    @Test
    void getUserById_successful() {
        UserResponse userResponse = UserResponse.builder().id(1L).username("user").build();
        when(authService.getUserById(1L)).thenReturn(Mono.just(userResponse));

        webTestClient.get().uri("/api/auth/users/1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.username").isEqualTo("user");
    }

    @Test
    void getAllUsers_successful() {
        UserResponse user1 = UserResponse.builder().id(1L).username("user1").build();
        UserResponse user2 = UserResponse.builder().id(2L).username("user2").build();
        when(authService.getAllUsers()).thenReturn(Flux.fromIterable(List.of(user1, user2)));

        webTestClient.get().uri("/api/auth/users")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.length()").isEqualTo(2);
    }
}
