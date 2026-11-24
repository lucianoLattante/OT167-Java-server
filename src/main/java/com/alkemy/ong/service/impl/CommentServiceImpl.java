package com.alkemy.ong.service.impl;

import com.alkemy.ong.dto.CommentDto;
import com.alkemy.ong.exception.MessagePag;
import com.alkemy.ong.exception.NotFoundException;
import com.alkemy.ong.exception.PaginationMessage;
import com.alkemy.ong.mapper.CommentMapper;
import com.alkemy.ong.model.Comment;
import com.alkemy.ong.model.News;
import com.alkemy.ong.repository.CommentRepository;
import com.alkemy.ong.repository.NewsRepository;
import com.alkemy.ong.repository.UserRepository;
import com.alkemy.ong.exception.BadRequestException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import com.alkemy.ong.service.CommentService;
import org.springframework.data.domain.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.security.core.Authentication;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.WebRequest;

import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class CommentServiceImpl implements CommentService {

    private static final int SIZE_PAG_10 = 10;

    private final CommentRepository commentRepository;
    private final CommentMapper commentMapper;
    private final MessageSource messageSource;
    private final NewsRepository newsRepository;
    private final PaginationMessage paginationMessage;
    private final UserRepository userRepository;


   /*@Override
    public Comment save(Comment comment) {
        if (comment != null) {

            if (!comment.getBody().isBlank()) {
                throw new NullPointerException(messageSource.getMessage("comment.not.null", null, Locale.ENGLISH));
            }

            return commentRepository.save(comment);

        } else {
            throw new NullPointerException(messageSource.getMessage("comment.not.null", null, Locale.ENGLISH));
        }
    }*/

    @Override
    public CommentDto updateComment(Long id, CommentDto dto) {
        Comment comment = commentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Comment not found"));
        requireOwnerOrAdmin(currentAuthentication(), comment);
        requireBody(dto);
        // Author, news, creation time and identity are immutable through this endpoint.
        comment.setBody(dto.getBody());
        return commentMapper.commentEntity2Dto(commentRepository.save(comment));
    }

    @Override
    public List<CommentDto> getAllCommentsByIdNews(Long id, int page) {
        News news = newsRepository.findById(id).orElseThrow(() -> new NotFoundException(messageSource.getMessage("not.found", null, Locale.ENGLISH)));

        return commentRepository.findForNews(id, PageRequest.of(page, SIZE_PAG_10, Sort.by("id")))
                .stream().map(commentMapper::commentEntity2Dto).collect(Collectors.toList());
    }

    @Override
    public void delete(Authentication authentication, Long id) {
        Comment comment = commentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Comment not found"));
        requireOwnerOrAdmin(authentication, comment);
        comment.setDeleted(true);
        commentRepository.save(comment);
    }

    private Authentication currentAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private void requireOwnerOrAdmin(Authentication authentication, Comment comment) {
        requireAuthenticated(authentication);
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        boolean owner = comment.getUser_id() != null
                && authentication.getName().equals(comment.getUser_id().getEmail());
        if (!admin && !owner) throw new AccessDeniedException("Access denied");
    }

    private void requireAuthenticated(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException("Authentication required");
        }
    }

    private void requireBody(CommentDto dto) {
        if (dto == null || dto.getBody() == null || dto.getBody().isBlank()) {
            throw new BadRequestException("Comment body is required");
        }
    }

    @Override
    public void existId (Long id){
        if (!commentRepository.existsById(id)) {
                throw new NotFoundException(messageSource.getMessage("comment.not.found", null, Locale.ENGLISH));
        }
    }


    public MessagePag getAllComments(int page, WebRequest request) {
        Sort sort = Sort.by(Sort.Direction.DESC,"creationDate");
        Page commentPage = commentRepository.findAll(PageRequest.of(page, SIZE_PAG_10,sort));
        return paginationMessage.messageInfo(commentPage, commentMapper.listCommentsDto(commentPage.getContent()), request);
    }

    @Override
    public CommentDto save(CommentDto dto) {
        Authentication authentication = currentAuthentication();
        requireAuthenticated(authentication);
        requireBody(dto);
        var owner = userRepository.findByEmail(authentication.getName());
        if (owner == null) throw new AccessDeniedException("Unknown account");
        dto.setUser_id(owner.getId());
        Comment comment = commentMapper.commentDto2Entity(dto);
        return commentMapper.commentEntity2Dto(commentRepository.save(comment));
    }

}


