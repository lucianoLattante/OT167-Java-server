package com.alkemy.ong.security;

import com.alkemy.ong.controller.UserController;
import com.alkemy.ong.exception.MessageResponse;
import com.alkemy.ong.security.config.SecurityConfiguration;
import com.alkemy.ong.security.filter.JwtRequestFilter;
import com.alkemy.ong.security.mapper.UserMapper;
import com.alkemy.ong.security.service.JwtUtils;
import com.alkemy.ong.security.service.UserDetailsCustomService;
import com.alkemy.ong.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@Import({SecurityConfiguration.class, JwtRequestFilter.class, JwtUtils.class})
class UserDeletionSecurityTest {
    private static final String TEST_KEY = newKey();
    private static String newKey() {
        byte[] bytes = new byte[32];
        new java.security.SecureRandom().nextBytes(bytes);
        return java.util.Base64.getEncoder().encodeToString(bytes);
    }
    @org.springframework.test.context.DynamicPropertySource
    static void properties(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("security.jwt.secret", () -> TEST_KEY);
    }
    @Autowired MockMvc mvc;
    @MockBean UserDetailsCustomService userDetails;
    @Autowired JwtUtils jwt;
    @MockBean UserService users;
    @MockBean UserMapper mapper;
    @MockBean MessageResponse messages;

    @Test @WithMockUser(roles = "USER")
    void ordinaryUserCannotDeleteAnotherUser() throws Exception {
        mvc.perform(delete("/users/42")).andExpect(status().isForbidden());
        verifyNoInteractions(users);
    }
    @Test @WithMockUser(roles = "ADMIN")
    void administratorCanDeleteUser() throws Exception {
        mvc.perform(delete("/users/42")).andExpect(status().isNoContent());
        verify(users).delete(42L);
    }
    @Test
    void anonymousRequestCannotReachDeletion() throws Exception {
        mvc.perform(delete("/users/42")).andExpect(status().is3xxRedirection());
        verifyNoInteractions(users);
    }
    @Test void invalidBearerIsRejectedBeforeUserLookup() throws Exception {
        mvc.perform(delete("/users/42").header("Authorization", "Bearer invalid"))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(users, userDetails);
    }
    @Test void validAdminBearerCanDelete() throws Exception {
        var admin = org.springframework.security.core.userdetails.User.withUsername("admin@example.test")
            .password("unused").roles("ADMIN").build();
        when(userDetails.loadUserByUsername(admin.getUsername())).thenReturn(admin);
        mvc.perform(delete("/users/42").header("Authorization", "Bearer " + jwt.generateToken(admin)))
            .andExpect(status().isNoContent());
        verify(users).delete(42L);
    }
    @Test void validUserBearerCannotDelete() throws Exception {
        var user = org.springframework.security.core.userdetails.User.withUsername("user@example.test")
            .password("unused").roles("USER").build();
        when(userDetails.loadUserByUsername(user.getUsername())).thenReturn(user);
        mvc.perform(delete("/users/42").header("Authorization", "Bearer " + jwt.generateToken(user)))
            .andExpect(status().isForbidden());
        verifyNoInteractions(users);
    }
    @Test void disabledUserBearerIsRejected() throws Exception {
        var user = org.springframework.security.core.userdetails.User.withUsername("admin@example.test")
            .password("unused").roles("ADMIN").disabled(true).build();
        when(userDetails.loadUserByUsername(user.getUsername())).thenReturn(user);
        mvc.perform(delete("/users/42").header("Authorization", "Bearer " + jwt.generateToken(user)))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(users);
    }
}
