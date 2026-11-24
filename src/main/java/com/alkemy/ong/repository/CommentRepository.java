package com.alkemy.ong.repository;
import com.alkemy.ong.model.Comment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {
    @Query("select c from Comment c where c.news_id.id = :newsId")
    List<Comment> findForNews(@Param("newsId") Long newsId, Pageable page);
}
