package com.alkemy.ong.security;

import com.alkemy.ong.config.AmazonS3Configuration;
import com.alkemy.ong.model.Role;
import com.alkemy.ong.repository.RoleRepository;
import com.alkemy.ong.repository.UserRepository;
import com.alkemy.ong.seeder.UserSeeder;
import com.alkemy.ong.service.impl.PhotoServiceImpl;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.PutObjectRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DemoStartupAndUploadTest {
    @Test void normalStartupInitializesRolesWithoutCreatingAccounts() {
        UserRepository users = mock(UserRepository.class);
        RoleRepository roles = mock(RoleRepository.class);
        when(roles.save(any(Role.class))).thenAnswer(call -> call.getArgument(0));
        new UserSeeder(users, roles, new BCryptPasswordEncoder(), new MockEnvironment()).run();
        verifyNoInteractions(users);
        verify(roles, times(2)).save(any(Role.class));
    }

    @Test void invalidAndOversizedImagesNeverReachStorage() {
        AmazonS3 storage = mock(AmazonS3.class);
        PhotoServiceImpl photos = photos(storage);
        var mislabelled = new MockMultipartFile("file", "fake.png", "image/png", new byte[] {1, 2});
        var oversized = new MockMultipartFile("file", "large.png", "image/png", new byte[5 * 1024 * 1024 + 1]);
        assertEquals(415, assertThrows(ResponseStatusException.class, () -> photos.uploadImage(mislabelled)).getRawStatusCode());
        assertEquals(413, assertThrows(ResponseStatusException.class, () -> photos.uploadImage(oversized)).getRawStatusCode());
        verifyNoInteractions(storage);
    }

    @Test void safelyNamedTemporaryFileIsRemovedAfterStorageFailure() throws Exception {
        AmazonS3 storage = mock(AmazonS3.class);
        PhotoServiceImpl photos = photos(storage);
        var image = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "png", image);
        var request = new MockMultipartFile("file", "ignored-file.png", "image/png", image.toByteArray());
        AtomicReference<Path> temp = new AtomicReference<>();
        when(storage.putObject(any(PutObjectRequest.class))).thenAnswer(call -> {
            PutObjectRequest upload = call.getArgument(0);
            assertTrue(upload.getKey().matches("[0-9a-f-]+\\.png"));
            assertEquals("image/png", upload.getMetadata().getContentType());
            temp.set(upload.getFile().toPath());
            assertTrue(Files.isRegularFile(temp.get()));
            throw new IllegalStateException("synthetic storage failure");
        });
        assertEquals(502, assertThrows(ResponseStatusException.class, () -> photos.uploadImage(request)).getRawStatusCode());
        assertNotNull(temp.get());
        assertFalse(Files.exists(temp.get()));
    }

    private PhotoServiceImpl photos(AmazonS3 storage) {
        var config = mock(AmazonS3Configuration.class);
        when(config.getBucketName()).thenReturn("fixture");
        var photos = new PhotoServiceImpl();
        ReflectionTestUtils.setField(photos, "s3client", storage);
        ReflectionTestUtils.setField(photos, "amazonS3Configuration", config);
        return photos;
    }
}
