package com.insurance.productservice.service;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.SetBucketPolicyArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MinioStorageServiceTest {

    @Mock
    private MinioClient minioClient;

    private MinioStorageService service;

    @BeforeEach
    void setUp() {
        service = new MinioStorageService(minioClient);
        ReflectionTestUtils.setField(service, "bucketName", "product-images");
        ReflectionTestUtils.setField(service, "externalUrl", "http://minio.local");
    }

    @Test
    void initCreatesBucketAndPolicyWhenBucketIsMissing() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);

        service.init();

        verify(minioClient).makeBucket(any(MakeBucketArgs.class));
        verify(minioClient).setBucketPolicy(any(SetBucketPolicyArgs.class));
    }

    @Test
    void initDoesNotCreateBucketWhenItAlreadyExists() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

        service.init();

        verify(minioClient, never()).makeBucket(any(MakeBucketArgs.class));
        verify(minioClient, never()).setBucketPolicy(any(SetBucketPolicyArgs.class));
    }

    @Test
    void uploadFileStoresObjectAndReturnsPublicUrlWithOriginalExtension() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "product.png",
                "image/png",
                "image-bytes".getBytes()
        );

        String url = service.uploadFile(file);

        assertThat(url).startsWith("http://minio.local/product-images/");
        assertThat(url).endsWith(".png");
        verify(minioClient).putObject(any(PutObjectArgs.class));
    }

    @Test
    void uploadFileWrapsStorageErrors() throws Exception {
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("broken.jpg");
        when(file.getInputStream()).thenThrow(new IOException("stream failed"));

        assertThatThrownBy(() -> service.uploadFile(file))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to upload file");
    }
}
