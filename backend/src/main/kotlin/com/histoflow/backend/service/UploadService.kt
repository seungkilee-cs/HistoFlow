package com.histoflow.backend.service

import com.histoflow.backend.config.MinioProperties
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.CompletedPart
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest
import software.amazon.awssdk.services.s3.model.HeadBucketRequest
import software.amazon.awssdk.services.s3.model.NoSuchBucketException
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest
import software.amazon.awssdk.services.s3.model.UploadPartRequest
import java.time.Duration
import java.util.UUID

@Service
class UploadService(private val s3Client: S3Client, private val s3Presigner: S3Presigner, private val props: MinioProperties) {

    fun initiateMultipartUpload(filename: String, contentType: String?, partSizeHint: Long?, datasetName: String?): InitiateResult {
        val imageId = UUID.randomUUID().toString()
        val resolvedDatasetName = datasetName?.takeIf { it.isNotBlank() } ?: filename
        val key = "uploads/$imageId-$filename"
        ensureBucketExists(props.buckets.uploads)
        val req = CreateMultipartUploadRequest.builder()
            // For raw slide uploads we want to store the parts in the 'uploads' bucket
            // (configured as minio.buckets.uploads -> unprocessed-slides). Use that
            // so frontend-initiated multipart uploads land in the raw uploads bucket.
            .bucket(props.buckets.uploads)
            .key(key)
            .contentType(contentType)
            .build()

        val resp: CreateMultipartUploadResponse = s3Client.createMultipartUpload(req)
        val uploadId = resp.uploadId()

        // Choose a part size (fallback to hint or 16MB)
        val partSize = partSizeHint?.coerceAtLeast(5 * 1024 * 1024) ?: (16 * 1024 * 1024)

        return InitiateResult(uploadId, key, partSize, imageId, resolvedDatasetName)
    }

    fun presignUploadPart(uploadId: String, key: String, partNumber: Int, expirySeconds: Long = 60 * 60): String {
        val uploadPartRequest = UploadPartRequest.builder()
            .bucket(props.buckets.uploads)
            .key(key)
            .uploadId(uploadId)
            .partNumber(partNumber)
            .build()

        val presignRequest = UploadPartPresignRequest.builder()
            .uploadPartRequest(uploadPartRequest)
            .signatureDuration(Duration.ofSeconds(expirySeconds))
            .build()

        val presigned = s3Presigner.presignUploadPart(presignRequest)
        return presigned.url().toString()
    }

    fun completeMultipartUpload(uploadId: String, key: String, parts: List<Pair<Int, String>>) {
        val completedParts = parts.map { (partNumber, etag) ->
            CompletedPart.builder().partNumber(partNumber).eTag(etag).build()
        }
        val completedMultipart = CompletedMultipartUpload.builder().parts(completedParts).build()

        val req = CompleteMultipartUploadRequest.builder()
            .bucket(props.buckets.uploads)
            .key(key)
            .uploadId(uploadId)
            .multipartUpload(completedMultipart)
            .build()

        s3Client.completeMultipartUpload(req)
    }

    fun abortMultipartUpload(uploadId: String, key: String) {
        val req = AbortMultipartUploadRequest.builder()
            .bucket(props.buckets.uploads)
            .key(key)
            .uploadId(uploadId)
            .build()
        s3Client.abortMultipartUpload(req)
    }

    private fun ensureBucketExists(bucketName: String) {
        try {
            s3Client.headBucket(
                HeadBucketRequest.builder()
                    .bucket(bucketName)
                    .build()
            )
        } catch (_: NoSuchBucketException) {
            s3Client.createBucket(
                CreateBucketRequest.builder()
                    .bucket(bucketName)
                    .build()
            )
        }
    }

}

data class InitiateResult(
    val uploadId: String,
    val key: String,
    val partSize: Long,
    val imageId: String,
    val datasetName: String
)
