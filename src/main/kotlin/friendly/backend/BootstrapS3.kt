package friendly.backend

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.s3.S3Client
import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.net.url.Url
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO

suspend fun bootstrapS3(): S3Context? {
    val endpoint = System.getenv("FRIENDLY_S3_ENDPOINT") ?: return null
    val accessKeyId = System.getenv("FRIENDLY_S3_ACCESS_KEY_ID") ?: return null
    val secretAccessKey =
        System.getenv("FRIENDLY_S3_SECRET_ACCESS_KEY") ?: return null
    val friendlyBucket =
        System.getenv("FRIENDLY_S3_FRIENDLY_BUCKET") ?: return null
    val friendlyPreuploadBucket =
        System.getenv("FRIENDLY_S3_FRIENDLY_PREUPLOAD_BUCKET") ?: return null

    val sdk = S3Client.fromEnvironment {
        region = "auto"
        endpointUrl = Url.parse(endpoint)
        credentialsProvider = StaticCredentialsProvider(
            Credentials(accessKeyId, secretAccessKey),
        )
    }

    val httpClient = HttpClient(CIO)

    return S3Context(
        sdk = sdk,
        friendlyBucket = friendlyBucket,
        friendlyPreuploadBucket = friendlyPreuploadBucket,
        endpoint = endpoint,
        httpClient = httpClient,
    )
}
