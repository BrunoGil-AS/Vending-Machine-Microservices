package com.vendingmachine.gateway.User.DTO;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
/**
 * Update User Request DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateUserRequest {
    
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;
    
    private String role;
    
    private Boolean active;
}
