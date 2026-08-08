package com.learnnexus.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * S3-compatible object storage. Points at MinIO locally and at real S3 in
 * deployed environments — only {@code app.storage.endpoint} and the credentials
 * differ, so the code path is identical in both.
 */
@Configuration
@RequiredArgsConstructor
public class StorageConfig {

    private final AppProperties properties;

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .endpointOverride(URI.create(properties.storage().endpoint()))
                .region(Region.of(properties.storage().region()))
                .credentialsProvider(credentials())
                .serviceConfiguration(S3Configuration.builder()
                        // MinIO serves buckets as path segments rather than sub-domains.
                        .pathStyleAccessEnabled(properties.storage().pathStyleAccess())
                        .build())
                .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                // Presigned URLs are handed to the browser, so they must be signed
                // against the endpoint the browser can actually reach.
                .endpointOverride(URI.create(properties.storage().publicEndpoint()))
                .region(Region.of(properties.storage().region()))
                .credentialsProvider(credentials())
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(properties.storage().pathStyleAccess())
                        .build())
                .build();
    }

    private StaticCredentialsProvider credentials() {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(
                properties.storage().accessKey(), properties.storage().secretKey()));
    }
}
