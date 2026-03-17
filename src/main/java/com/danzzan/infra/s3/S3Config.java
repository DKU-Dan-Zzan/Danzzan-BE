package com.danzzan.infra.s3;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class S3Config {

    /**
     * 설정 시 StaticCredentialsProvider 사용 (로컬 개발 시 application-local.yml 등에 설정).
     * 미설정이면 DefaultCredentialsProvider(환경변수, ~/.aws/credentials 등) 사용.
     */
    @Value("${aws.access-key-id:}")
    private String accessKeyId;

    @Value("${aws.secret-access-key:}")
    private String secretAccessKey;

    @Bean
    public AwsCredentialsProvider awsCredentialsProvider() {
        if (accessKeyId != null && !accessKeyId.isBlank()
                && secretAccessKey != null && !secretAccessKey.isBlank()) {
            return StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKeyId.trim(), secretAccessKey.trim()));
        }
        return DefaultCredentialsProvider.builder().build();
    }

    @Bean
    public S3Client s3Client(
            @Value("${aws.region}") String region,
            AwsCredentialsProvider awsCredentialsProvider) {
        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(awsCredentialsProvider)
                .build();
    }

    @Bean
    public S3Presigner s3Presigner(
            @Value("${aws.region}") String region,
            AwsCredentialsProvider awsCredentialsProvider) {
        return S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(awsCredentialsProvider)
                .build();
    }
}

