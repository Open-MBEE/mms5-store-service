package org.openmbee.mms5.plugins

import com.amazonaws.ClientConfiguration
import com.amazonaws.auth.AWSCredentials
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.model.AmazonS3Exception
import com.amazonaws.services.s3.model.GetObjectRequest
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.PutObjectRequest
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.config.*
import io.ktor.routing.*
import org.openmbee.mms5.lib.MimeTypes
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.util.*

fun Application.configureStorage() {
    val s3Storage = S3Storage(getS3ConfigValues(environment.config))

    routing {
        authenticate {
            put("upload") {

            }

            get("file") {

            }
        }
    }
}

class S3Storage(s3Config: S3Config) {
    private val s3Client = getClient(s3Config)
    private val bucket = s3Config.bucket

    private val logger = LoggerFactory.getLogger(javaClass)

    fun get(location: String?): ByteArray {
        val rangeObjectRequest = GetObjectRequest(bucket, location)
        return try {
            s3Client.getObject(rangeObjectRequest).objectContent.readAllBytes()
        } catch (ioe: IOException) {
            throw ioe
        }
    }

    fun getPreSignedUrl(location: String): String {
        return s3Client
            .generatePresignedUrl(bucket, location, Date.from(Instant.now().plusSeconds(10 * 60)))
            .toString()
    }

    fun store(data: ByteArray, filename: String): String {
        val location = buildLocation(filename, MimeTypes.Text.TTL.extension)
        val om = ObjectMetadata()
        om.contentType = MimeTypes.Text.TTL.contentType
        om.contentLength = data.size.toLong()
        val por = PutObjectRequest(bucket, location, ByteArrayInputStream(data), om)
        try {
            s3Client.putObject(por)
        } catch (e: RuntimeException) {
            logger.error("Error storing artifact: ", e)
            throw e
        }
        return location
    }

    private fun buildLocation(filename: String, extension: String): String {
        val today = LocalDate.now()
        return java.lang.String.format(
            "%d/%s.%s",
            today,
            filename,
            extension
        )
    }

    private fun getExtension(mime: String): String {
        var extension = ""
        try {
            extension = MimeTypes.Text.TTL.extension
        } catch (e: Exception) {
            logger.error("Error getting extension: ", e)
        }
        return extension
    }

    private fun getClient(s3ConfigValues: S3Config): AmazonS3 {
        val clientConfiguration = ClientConfiguration()
        clientConfiguration.signerOverride = "AWSS3V4SignerType"
        val builder: AmazonS3ClientBuilder = AmazonS3ClientBuilder
            .standard()
            .withEndpointConfiguration(
                AwsClientBuilder.EndpointConfiguration(
                    s3ConfigValues.endpoint,
                    s3ConfigValues.region
                )
            )
            .withPathStyleAccessEnabled(true)
            .withClientConfiguration(clientConfiguration)
        val s3Client = if (s3ConfigValues.accessKey.isNotEmpty() && s3ConfigValues.secretKey.isNotEmpty()) {
            val credentials: AWSCredentials = BasicAWSCredentials(s3ConfigValues.accessKey, s3ConfigValues.secretKey)
            builder.withCredentials(AWSStaticCredentialsProvider(credentials)).build()
        } else {
            builder.withCredentials(DefaultAWSCredentialsProviderChain.getInstance()).build()
        }
        if (!s3Client.doesBucketExistV2(s3ConfigValues.bucket)) {
            try {
                s3Client.createBucket(s3ConfigValues.bucket)
            } catch (e: AmazonS3Exception) {
                throw Exception(e)
            }
        }

        return s3Client
    }
}

data class S3Config(
    val region: String,
    val bucket: String,
    val endpoint: String,
    val accessKey: String,
    val secretKey: String
)

fun getS3ConfigValues(config: ApplicationConfig): S3Config {
    val region = config.propertyOrNull("s3.region")?.getString() ?: ""
    val bucket = config.propertyOrNull("s3.bucket")?.getString() ?: ""
    val endpoint = config.propertyOrNull("s3.endpoint")?.getString() ?: ""
    val accessKey = config.propertyOrNull("s3.accessKey")?.getString() ?: ""
    val secretKey = config.propertyOrNull("s3.secretKey")?.getString() ?: ""
    return S3Config(
        region,
        bucket,
        endpoint,
        accessKey,
        secretKey
    )
}