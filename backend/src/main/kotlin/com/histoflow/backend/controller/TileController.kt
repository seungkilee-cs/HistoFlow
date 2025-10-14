package com.histoflow.backend.controller

import com.histoflow.backend.service.TileService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody

@RestController
@RequestMapping("/api/v1/tiles")
class TileController(private val tileService: TileService) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Serve DZI descriptor XML
     * Frontend request: GET /api/v1/tiles/test-image-001/image.dzi
     * MinIO object: test-image-001/image.dzi
     */
    @GetMapping("/{imageId}/image.dzi")
    fun getDziDescriptor(@PathVariable imageId: String): ResponseEntity<StreamingResponseBody> {
        logger.info("DZI descriptor requested: imageId={}", imageId)

        return try {
            val inputStream = tileService.getDziDescriptor(imageId)

            val body = StreamingResponseBody { outputStream ->
                inputStream.use { it.transferTo(outputStream) }
            }

            ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000")
                .body(body)

        } catch (e: IllegalArgumentException) {
            logger.error("DZI descriptor not found: {}", imageId)
            ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        }
    }

    /**
     * Serve individual tile JPEG
     * Frontend request: GET /api/v1/tiles/test-image-001/5/12_8.jpg
     * MinIO object: test-image-001/image_files/5/12_8.jpg
     */
    @GetMapping("/{imageId}/image_files/{level}/{coord}.jpg")
    fun getTile(
        @PathVariable imageId: String,
        @PathVariable level: Int,
        @PathVariable coord: String
    ): ResponseEntity<StreamingResponseBody> {
        // Parse "x_y" format from URL
        val (x, y) = coord.split("_").map { it.toInt() }
        logger.debug("Tile requested: imageId={}, level={}, x={}, y={}", imageId, level, x, y)

        return try {
            val inputStream = tileService.getTile(imageId, level, x, y)

            val body = StreamingResponseBody { outputStream ->
                inputStream.use { it.transferTo(outputStream) }
            }

            ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000")
                .body(body)

        } catch (e: IllegalArgumentException) {
            logger.error("Tile not found: imageId={}, level={}, x={}, y={}", imageId, level, x, y)
            ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        }
    }
}
