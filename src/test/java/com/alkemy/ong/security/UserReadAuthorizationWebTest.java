package com.alkemy.ong.security;

import com.alkemy.ong.controller.UserController;
import com.alkemy.ong.exception.MessageResponse;
import com.alkemy.ong.security.config.SecurityConfiguration;
import com.alkemy.ong.security.filter.JwtRequestFilter;
import com.alkemy.ong.security.mapper.UserMapper;
import com.alkemy.ong.security.model.UserEntity;
import com.alkemy.ong.security.service.JwtUtils;
import com.alkemy.ong.security.service.UserDetailsCustomService;
import com.alkemy.ong.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import({SecurityConfiguration.class, JwtRequestFilter.class})
class UserReadAuthorizationWebTest {
    @Autowired MockMvc mvc;
    @MockBean UserService users;
    @MockBean UserDetailsCustomService details;
    @MockBean JwtUtils jwt;
    @MockBean UserMapper mapper;
    @MockBean MessageResponse messages;

    @Test void listingIsAdminOnly() throws Exception {
        mvc.perform(get("/users").with(user("member@example.test").roles("USER"))).andExpect(status().isForbidden());
        verifyNoInteractions(users);
        mvc.perform(get("/users").with(user("admin@example.test").roles("ADMIN"))).andExpect(status().isOk());
        verify(users).getAllUsers();
    }

    @Test void individualReadChecksOwnerBeforeResolvingTarget() throws Exception {
        UserEntity owner = new UserEntity();
        owner.setId(5L);
        when(users.findByEmail("member@example.test")).thenReturn(owner);
        when(users.findUserById(5L)).thenReturn(Optional.of(owner));
        mvc.perform(get("/users/6").with(user("member@example.test").roles("USER"))).andExpect(status().isForbidden());
        verify(users, never()).findUserById(6L);
        mvc.perform(get("/users/5").with(user("member@example.test").roles("USER"))).andExpect(status().isOk());
        verify(users).findUserById(5L);
    }
}
