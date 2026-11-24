package com.alkemy.ong.service.impl;

import com.alkemy.ong.exception.NotFoundException;
import com.alkemy.ong.service.PhotoService;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.DeleteObjectRequest;
import com.amazonaws.services.s3.model.PutObjectRequest;
import lombok.RequiredArgsConstructor;
import com.alkemy.ong.config.AmazonS3Configuration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import javax.annotation.PostConstruct;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.amazonaws.services.s3.model.ObjectMetadata;
import java.io.IOException;
import java.util.Date;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional
public class PhotoServiceImpl implements PhotoService {

    private AmazonS3 s3client;

    @Autowired
    private AmazonS3Configuration amazonS3Configuration;
    @Autowired
    private MessageSource messageSource;
    
    private static final long MAX_UPLOAD_BYTES = 5L * 1024 * 1024;

    @Override
    public String uploadImage(MultipartFile multipartFile) {
        Path temp = null;
        try {
            temp = convertMultiPartToFile(multipartFile).toPath();
            String type = imageType(temp);
            String key = UUID.randomUUID() + ("image/png".equals(type) ? ".png" : ".jpg");
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(type);
            metadata.setContentLength(Files.size(temp));
            s3client.putObject(new PutObjectRequest(amazonS3Configuration.getBucketName(), key, temp.toFile())
                    .withMetadata(metadata).withCannedAcl(CannedAccessControlList.PublicRead));
            return amazonS3Configuration.getEndpointUrl() + "/" + amazonS3Configuration.getBucketName() + "/" + key;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Image storage unavailable");
        } finally {
            if (temp != null) removeTemporaryFile(temp);
        }
    }

    @Override
    public File convertMultiPartToFile(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image is required");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Image exceeds 5 MiB");
        }
        Path temp = Files.createTempFile("ong-upload-", ".img");
        try (InputStream input = file.getInputStream()) {
            byte[] bytes = input.readNBytes((int) MAX_UPLOAD_BYTES + 1);
            if (bytes.length > MAX_UPLOAD_BYTES) {
                throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Image exceeds 5 MiB");
            }
            Files.write(temp, bytes);
            return temp.toFile();
        } catch (IOException | RuntimeException e) {
            removeTemporaryFile(temp);
            throw e;
        }
    }

    private String imageType(Path file) {
        try (ImageInputStream stream = ImageIO.createImageInputStream(file.toFile())) {
            if (stream != null) {
                Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
                if (readers.hasNext()) {
                    ImageReader reader = readers.next();
                    try {
                        reader.setInput(stream, true, true);
                        String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                        if (("png".equals(format) || "jpeg".equals(format))
                                && reader.getWidth(0) <= 4096 && reader.getHeight(0) <= 4096
                                && reader.read(0) != null) {
                            return "png".equals(format) ? "image/png" : "image/jpeg";
                        }
                    } finally {
                        reader.dispose();
                    }
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // Treat unreadable data as invalid input.
        }
        throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "A valid PNG or JPEG image is required");
    }

    private void removeTemporaryFile(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            file.toFile().deleteOnExit();
        }
    }

    @Override
    public String generateFileName(MultipartFile multiPart) {
        return new Date().getTime() + "-" + multiPart.getOriginalFilename().replace(" ", "_");
    }

    @Override
    @PostConstruct
    public void initializeAmazon() {
        AWSCredentials credentials = new BasicAWSCredentials(amazonS3Configuration.getAccessKey(), amazonS3Configuration.getSecretKey());
        this.s3client = new AmazonS3Client(credentials);
    }

    @Override
    public void deleteObject(MultipartFile multipartFile) {
        String fileName = generateFileName(multipartFile);
        try {
            if(s3client.doesObjectExist(amazonS3Configuration.getBucketName(),fileName)){
                s3client.deleteObject(new DeleteObjectRequest(amazonS3Configuration.getBucketName(),fileName));
            }
        } catch (Exception e) {
            throw new NotFoundException(messageSource.getMessage("slide.not.found", new Object[]{fileName.toString()}, Locale.ENGLISH));
        }
    }
}
