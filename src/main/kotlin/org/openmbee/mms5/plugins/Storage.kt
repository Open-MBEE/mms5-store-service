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
import io.ktor.features.*
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.*


class S3Storage {
    private val logger = LoggerFactory.getLogger(javaClass)
    private var s3Client: AmazonS3? = null

    @Value("\${s3.endpoint}")
    private val ENDPOINT: String? = null

    @Value("\${s3.access_key:#{null}}")
    private val ACCESS_KEY: Optional<String>? = null

    @Value("\${s3.secret_key:#{null}}")
    private val SECRET_KEY: Optional<String>? = null

    @Value("\${s3.region}")
    private val REGION: String? = null

    @Value("\${s3.bucket:#{null}}")
    private val BUCKET: Optional<String>? = null
    private val mimeTypes: MimeTypes = MimeTypes.getDefaultMimeTypes()
    private val client: AmazonS3?
        private get() {
            if (s3Client == null) {
                val clientConfiguration = ClientConfiguration()
                clientConfiguration.setSignerOverride("AWSS3V4SignerType")
                val builder: AmazonS3ClientBuilder = AmazonS3ClientBuilder
                    .standard()
                    .withEndpointConfiguration(AwsClientBuilder.EndpointConfiguration(ENDPOINT, REGION))
                    .withPathStyleAccessEnabled(true)
                    .withClientConfiguration(clientConfiguration)
                s3Client = if (ACCESS_KEY!!.isPresent && SECRET_KEY!!.isPresent) {
                    val credentials: AWSCredentials = BasicAWSCredentials(ACCESS_KEY.get(), SECRET_KEY.get())
                    builder.withCredentials(AWSStaticCredentialsProvider(credentials)).build()
                } else {
                    builder.withCredentials(DefaultAWSCredentialsProviderChain.getInstance()).build()
                }
                if (!s3Client.doesBucketExistV2(bucket)) {
                    try {
                        s3Client.createBucket(bucket)
                    } catch (e: AmazonS3Exception) {
                        throw InternalErrorException(e)
                    }
                }
            }
            return s3Client
        }

    operator fun get(location: String?, element: ElementJson?, mimetype: String?): ByteArray {
        val rangeObjectRequest = GetObjectRequest(bucket, location)
        return try {
            client.getObject(rangeObjectRequest).getObjectContent().readAllBytes()
        } catch (ioe: IOException) {
            throw NotFoundException(ioe)
        }
    }

    fun store(data: ByteArray, element: ElementJson, mimetype: String): String {
        val location = buildLocation(element, mimetype)
        val om = ObjectMetadata()
        om.setContentType(mimetype)
        om.setContentLength(data.size)
        val por = PutObjectRequest(bucket, location, ByteArrayInputStream(data), om)
        try {
            client.putObject(por)
        } catch (e: RuntimeException) {
            logger.error("Error storing artifact: ", e)
            throw InternalErrorException(e)
        }
        return location
    }

    private fun buildLocation(element: ElementJson, mimetype: String): String {
        val today = Date()
        return java.lang.String.format(
            "%s/%s/%s/%d",
            element.getProjectId(),
            element.getId(),
            getExtension(mimetype),
            today.time
        )
    }

    private fun getExtension(mime: String): String {
        var extension = ""
        try {
            extension = mimeTypes.forName(mime).getExtension().substring(1)
        } catch (e: Exception) {
            logger.error("Error getting extension: ", e)
        }
        return extension
    }

    private val bucket: String
        private get() {
            var bucket = "mms"
            if (BUCKET!!.isPresent) {
                bucket = BUCKET.get()
            }
            return bucket
        }
}