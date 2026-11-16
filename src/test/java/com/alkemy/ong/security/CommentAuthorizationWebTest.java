package com.alkemy.ong.security;

import com.alkemy.ong.controller.CommentController;
import com.alkemy.ong.exception.PaginationMessage;
import com.alkemy.ong.mapper.CommentMapper;
import com.alkemy.ong.model.Comment;
import com.alkemy.ong.repository.*;
import com.alkemy.ong.security.config.SecurityConfiguration;
import com.alkemy.ong.security.filter.JwtRequestFilter;
import com.alkemy.ong.security.model.UserEntity;
import com.alkemy.ong.security.service.*;
import com.alkemy.ong.service.impl.CommentServiceImpl;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CommentController.class)
@Import({ SecurityConfiguration.class, JwtRequestFilter.class, CommentServiceImpl.class })
class CommentAuthorizationWebTest {
    @Autowired MockMvc mvc;
    @MockBean UserDetailsCustomService details;
    @MockBean JwtUtils jwt;
    @MockBean CommentRepository comments;
    @MockBean CommentMapper mapper;
    @MockBean NewsRepository news;
    @MockBean UserRepository users;
    @MockBean PaginationMessage pagination;
    Comment comment;
    @BeforeEach void setup() {
        var owner = new UserEntity(); owner.setId(1L); owner.setEmail("owner@example.test");
        comment = new Comment(); comment.setId(42L); comment.setBody("original"); comment.setUser_id(owner);
        when(comments.findById(42L)).thenReturn(Optional.of(comment)); when(comments.existsById(42L)).thenReturn(true);
        when(comments.save(any(Comment.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }
    @Test @WithMockUser(username="other@example.test", roles="USER")
    void otherUserGets403WhenEditing() throws Exception {
        mvc.perform(put("/comments/42").contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"changed\"}")).andExpect(status().isForbidden());
        verify(comments, never()).save(any());
    }
    @Test @WithMockUser(username="owner@example.test", roles="USER")
    void ownerCanEditThroughSecurityChain() throws Exception {
        mvc.perform(put("/comments/42").contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"changed\",\"user_id\":999}")).andExpect(status().isOk());
        Assertions.assertEquals(1L, comment.getUser_id().getId()); verify(comments).save(comment);
    }
    @Test @WithMockUser(username="other@example.test", roles="USER")
    void otherUserGets403WhenDeleting() throws Exception {
        mvc.perform(delete("/comments/42")).andExpect(status().isForbidden()); verify(comments, never()).save(any());
    }
    @Test @WithMockUser(username="admin@example.test", roles="ADMIN")
    void administratorCanDeleteThroughSecurityChain() throws Exception {
        mvc.perform(delete("/comments/42")).andExpect(status().isOk()); verify(comments).save(comment);
    }
}
