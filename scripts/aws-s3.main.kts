#!/usr/bin/env kotlin

@file:DependsOn("aws.sdk.kotlin:aws-core-jvm:1.9.4")
@file:DependsOn("aws.sdk.kotlin:s3-jvm:1.9.4")
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-io-core-jvm:0.8.0")
@file:DependsOn("io.ktor:ktor-client-core-jvm:3.3.0")
@file:DependsOn("io.ktor:ktor-client-cio-jvm:3.3.0")

import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.Path
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.Buffer
import kotlinx.io.buffered
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.CoroutineScope
import kotlin.time.Duration.Companion.minutes
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.asByteWriteChannel
import io.ktor.utils.io.copyTo
import io.ktor.client.HttpClient
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.request.put
import io.ktor.client.request.get
import io.ktor.client.request.setBody
import io.ktor.http.contentType
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.putObject
import aws.sdk.kotlin.services.s3.presigners.presignPutObject
import aws.sdk.kotlin.services.s3.presigners.presignGetObject
import aws.sdk.kotlin.services.s3.model.PutObjectRequest
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.smithy.kotlin.runtime.net.url.Url
import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.content.fromFile
import aws.smithy.kotlin.runtime.content.toByteStream
import aws.smithy.kotlin.runtime.io.SdkSource
import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import kotlinx.coroutines.runBlocking

println("Hello world!")

// todo: use sink
println(SystemFileSystem.list(Path(".")))

val path: Path = Path("scripts/aws-test-image.png")
val pathCopy: Path = Path("scripts/aws-test-image-copy.png")

val source: Source = SystemFileSystem.source(path).buffered()
val sink: Sink = SystemFileSystem.sink(pathCopy).buffered()
val size = SystemFileSystem.metadataOrNull(path)?.size ?: error("file does not exist")

val accountId = "163b65df61d5a567ef13fa3380c72f78"
val accessKeyId = "c9471f724721e050936ad2ee8cefa7a5"
val secretAccessKey = "616c6d976624ca68abd7bbb0413060aaa26f6a3a4489ce829bcae52a3d523a65"
val friendlyBucket = "friendly"

runBlocking {
    val httpClient = HttpClient()
    val client = S3Client.fromEnvironment {
        region = "auto"
        endpointUrl = Url.parse("https://$accountId.r2.cloudflarestorage.com")
        credentialsProvider = StaticCredentialsProvider(
            Credentials(accessKeyId, secretAccessKey),
        )
    }

    try {
        val uploadUrl = client.presignPutObject(
            input = PutObjectRequest {
                bucket = friendlyBucket
                key = "hi"
            },
            duration = 15.minutes,
        ).url
        println(uploadUrl)
        val putResponse = httpClient.put(uploadUrl.toString()) {
            setBody(object : OutgoingContent.ReadChannelContent() {
                override val contentType = ContentType.Application.OctetStream
                override val contentLength = 1_000_000L
                override fun readFrom() = ByteReadChannel(source)
            })
        }
        println(putResponse.bodyAsText())
        val downloadUrl = client.presignGetObject(
            input = GetObjectRequest {
                bucket = friendlyBucket
                key = "hi"
            },
            duration = 15.minutes,
        ).url
        println(downloadUrl)
        val getResponse = httpClient.get(downloadUrl.toString())
        if (getResponse.status != HttpStatusCode.OK) {
            print(getResponse)
            print(": ")
            println(getResponse.bodyAsText())
        }
        getResponse.bodyAsChannel().copyTo(sink.asByteWriteChannel())
        // val bucketName = "<BUCKET_NAME>"
        // println("\nObjects in bucket '${bucketName}':")
        // r2Client.listObjects { bucket = bucketName }.contents?.forEach {
        //     println("* ${it.key} (size: ${it.size} bytes, modified: ${it.lastModified})")
        // }
    } finally {
        client.close()
        source.close()
        sink.close()
    }
}
