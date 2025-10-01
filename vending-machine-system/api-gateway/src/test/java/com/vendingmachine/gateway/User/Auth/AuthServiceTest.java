
package com.vendingmachine.gateway.User.Auth;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.vendingmachine.gateway.User.AdminUser;
import com.vendingmachine.gateway.User.AdminUserRepository;
import com.vendingmachine.gateway.User.Login.DTO.LoginRequest;
import com.vendingmachine.gateway.User.DTO.CreateUserRequest;
import com.vendingmachine.gateway.User.JWT.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {

    @Mock
    private AdminUserRepository adminUserRepository;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private AuthService authService;

    private AdminUser adminUser;

    @BeforeEach
    void setUp() {
        String passwordHash = BCrypt.withDefaults().hashToString(12, "admin123".toCharArray());
        adminUser = AdminUser.builder()
                .id(1L)
                .username("admin")
                .passwordHash(passwordHash)
                .role("SUPER_ADMIN")
                .active(true)
                .build();
    }

    @Test
    void login_successful() {
        LoginRequest loginRequest = new LoginRequest("admin", "admin123");

        when(adminUserRepository.findByUsernameAndActiveTrue("admin")).thenReturn(Mono.just(adminUser));
        when(jwtUtil.generateToken(anyString(), anyString())).thenReturn("test-token");
        when(jwtUtil.getExpirationTime()).thenReturn(3600L);

        StepVerifier.create(authService.login(loginRequest))
                .expectNextMatches(loginResponse -> 
                    loginResponse.getToken().equals("test-token") && 
                    loginResponse.getUsername().equals("admin")
                )
                .verifyComplete();
    }

    @Test
    void login_userNotFound() {
        LoginRequest loginRequest = new LoginRequest("unknown", "password");
        when(adminUserRepository.findByUsernameAndActiveTrue("unknown")).thenReturn(Mono.empty());

        StepVerifier.create(authService.login(loginRequest))
                .expectErrorMessage("Invalid username or password")
                .verify();
    }

    @Test
    void login_invalidPassword() {
        LoginRequest loginRequest = new LoginRequest("admin", "wrongpassword");
        when(adminUserRepository.findByUsernameAndActiveTrue("admin")).thenReturn(Mono.just(adminUser));

        StepVerifier.create(authService.login(loginRequest))
                .expectErrorMessage("Invalid username or password")
                .verify();
    }

    @Test
    void createUser_successful() {
        CreateUserRequest createUserRequest = new CreateUserRequest("newuser", "password", "ADMIN");
        AdminUser newUser = AdminUser.builder().id(2L).username("newuser").role("ADMIN").build();

        when(adminUserRepository.existsByUsername("newuser")).thenReturn(Mono.just(false));
        when(adminUserRepository.save(any(AdminUser.class))).thenReturn(Mono.just(newUser));

        StepVerifier.create(authService.createUser(createUserRequest))
                .expectNextMatches(userResponse -> userResponse.getUsername().equals("newuser"))
                .verifyComplete();
    }

    @Test
    void createUser_usernameExists() {
        CreateUserRequest createUserRequest = new CreateUserRequest("admin", "password", "ADMIN");
        when(adminUserRepository.existsByUsername("admin")).thenReturn(Mono.just(true));

        StepVerifier.create(authService.createUser(createUserRequest))
                .expectErrorMessage("Username already exists")
                .verify();
    }
}
