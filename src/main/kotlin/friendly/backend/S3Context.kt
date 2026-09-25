package friendly.backend

import aws.sdk.kotlin.services.s3.S3Client
import io.ktor.client.HttpClient

data class S3Context(
    val sdk: S3Client,
    val friendlyBucket: String,
    val friendlyPreuploadBucket: String,
    val endpoint: String,
    val httpClient: HttpClient,
)
