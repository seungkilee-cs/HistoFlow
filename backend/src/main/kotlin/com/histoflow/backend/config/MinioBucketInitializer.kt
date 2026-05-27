package com.histoflow.backend.config

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.HeadBucketRequest
import software.amazon.awssdk.services.s3.model.NoSuchBucketException
import software.amazon.awssdk.services.s3.model.S3Exception

@Component
class MinioBucketInitializer(
    private val s3Client: S3Client,
    private val props: MinioProperties
) : ApplicationRunner {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        if (!props.initializeBuckets) {
            logger.info("MinIO bucket initialization disabled")
            return
        }

        listOf(props.buckets.uploads, props.buckets.tiles)
            .filter { it.isNotBlank() }
            .distinct()
            .forEach { ensureBucketExists(it) }
    }

    private fun ensureBucketExists(bucketName: String) {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build())
            logger.debug("MinIO bucket '{}' already exists", bucketName)
        } catch (_: NoSuchBucketException) {
            createBucket(bucketName)
        } catch (ex: S3Exception) {
            if (ex.statusCode() == 404) {
                createBucket(bucketName)
            } else {
                throw ex
            }
        }
    }

    private fun createBucket(bucketName: String) {
        logger.info("Creating MinIO bucket '{}'", bucketName)
        s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build())
    }
}
