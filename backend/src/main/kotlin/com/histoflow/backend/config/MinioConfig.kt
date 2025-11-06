package com.histoflow.backend.config

import io.minio.MinioClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.S3Configuration
import java.net.URI

@Configuration
class MinioConfig(private val props: MinioProperties) {

    @Bean
    fun s3Client(): S3Client {
        return S3Client.builder()
            .endpointOverride(URI.create(props.endpoint))
            .region(Region.US_EAST_1)
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(props.accessKey, props.secretKey)
                )
            )
            .forcePathStyle(true) // Required for MinIO
            .build()
    }

@Bean
fun s3Presigner(): S3Presigner {
    return S3Presigner.builder()
        // correctly use publicEndpoint so browser can reach the URLs
        .endpointOverride(URI.create(props.publicEndpoint))
        .region(Region.US_EAST_1)
        .credentialsProvider(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(props.accessKey, props.secretKey)
            )
        )
        // force path-style URLs (required for MinIO local dev)
        .serviceConfiguration(
            S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build()
        )
        .build()
}


    @Bean
    fun minioClient(): MinioClient {
        return MinioClient.builder()
            .endpoint(props.endpoint)
            .credentials(props.accessKey, props.secretKey)
            .build()
    }
}
