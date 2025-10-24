package com.histoflow.backend.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.net.URI

@Configuration
class MinioConfig(private val props: MinioProperties) {

    @ For creating pre signed url that frontend requests
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

    fun s3Presigner(): S3Presigner {
        return S3Presigner.builder()
            .endpointOverride(URI.create(props.endpoint))
            .region(Region.US_EAST_1)
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(props.accessKey, props.secretKey)
                )
            )
            .build()
    }
}
