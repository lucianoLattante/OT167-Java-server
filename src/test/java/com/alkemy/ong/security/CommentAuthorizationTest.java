package com.alkemy.ong.security;

import com.alkemy.ong.dto.CommentDto;
import com.alkemy.ong.exception.*;
import com.alkemy.ong.mapper.CommentMapper;
import com.alkemy.ong.model.Comment;
import com.alkemy.ong.model.News;
import com.alkemy.ong.repository.*;
import com.alkemy.ong.security.model.UserEntity;
import com.alkemy.ong.service.impl.CommentServiceImpl;
import org.junit.jupiter.api.*;
import org.springframework.context.MessageSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CommentAuthorizationTest {
    CommentRepository repository;
    CommentMapper mapper;
    UserRepository users;
    CommentServiceImpl service;
    Comment comment;
    @BeforeEach void setup() {
        repository = mock(CommentRepository.class); mapper = mock(CommentMapper.class); users = mock(UserRepository.class);
        service = new CommentServiceImpl(repository, mapper, mock(MessageSource.class), mock(NewsRepository.class), mock(PaginationMessage.class), users);
        var owner = new UserEntity(); owner.setId(1L); owner.setEmail("owner@example.test");
        var news = new News(); news.setId(10L);
        comment = new Comment(); comment.setId(42L); comment.setBody("original"); comment.setUser_id(owner); comment.setNews_id(news);
        when(repository.findById(42L)).thenReturn(Optional.of(comment));
        when(repository.save(any(Comment.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    Authentication actor(String name, String role) {
        return new UsernamePasswordAuthenticationToken(name, "unused", List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
    void as(String name, String role) { SecurityContextHolder.getContext().setAuthentication(actor(name, role)); }
    CommentDto input() { var dto = new CommentDto(); dto.setBody("edited"); dto.setUser_id(999L); dto.setNews_id(888L); return dto; }
    @Test void ownerCanDelete() {
        service.delete(actor("owner@example.test", "USER"), 42L);
        assertTrue(comment.getDeleted()); verify(repository).save(comment);
    }
    @Test void administratorCanDeleteAnotherUsersComment() {
        service.delete(actor("admin@example.test", "ADMIN"), 42L);
        assertTrue(comment.getDeleted()); verify(repository).save(comment);
    }
    @Test void strangerCannotDeleteOrReceiveSilentSuccess() {
        assertThrows(AccessDeniedException.class, () -> service.delete(actor("other@example.test", "USER"), 42L));
        assertFalse(comment.getDeleted()); verify(repository, never()).save(any());
    }
    @Test void missingCommentReturnsNotFound() {
        assertThrows(NotFoundException.class, () -> service.delete(actor("owner@example.test", "USER"), 999L));
        verify(repository, never()).save(any());
    }
    @Test void ownerCanEditBodyWithoutChangingAuthorOrNews() {
        as("owner@example.test", "USER");
        service.updateComment(42L, input());
        assertEquals("edited", comment.getBody()); assertEquals(1L, comment.getUser_id().getId()); assertEquals(10L, comment.getNews_id().getId());
        verify(mapper, never()).commentDto2Entity(any());
    }
    @Test void strangerCannotEdit() {
        as("other@example.test", "USER");
        assertThrows(AccessDeniedException.class, () -> service.updateComment(42L, input()));
        assertEquals("original", comment.getBody()); verify(repository, never()).save(any());
    }
    @Test void administratorCanEditButCannotReassignOwnership() {
        as("admin@example.test", "ADMIN"); service.updateComment(42L, input());
        assertEquals("edited", comment.getBody()); assertEquals(1L, comment.getUser_id().getId());
    }
    @Test void creationUsesAuthenticatedAuthorInsteadOfBodyUserId() {
        as("owner@example.test", "USER");
        when(users.findByEmail("owner@example.test")).thenReturn(comment.getUser_id());
        when(mapper.commentDto2Entity(any())).thenReturn(comment);
        var dto = input(); service.save(dto);
        assertEquals(1L, dto.getUser_id()); verify(repository).save(comment);
    }
    @Test void anonymousCannotCreate() {
        assertThrows(AccessDeniedException.class, () -> service.save(input()));
        verifyNoInteractions(users); verify(repository, never()).save(any());
    }
    @Test void blankBodyCannotMutateTheExistingComment() {
        as("owner@example.test", "USER"); var dto = input(); dto.setBody(" ");
        assertThrows(BadRequestException.class, () -> service.updateComment(42L, dto));
        assertEquals("original", comment.getBody()); verify(repository, never()).save(any());
    }
}
