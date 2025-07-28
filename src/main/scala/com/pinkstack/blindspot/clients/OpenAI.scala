package com.pinkstack.blindspot.clients

import com.openai.client.okhttp.{OpenAIOkHttpClient, OpenAIOkHttpClientAsync}
import com.openai.client.{OpenAIClient, OpenAIClientAsync}
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.openai.models.chat.completions.ChatCompletionCreateParams.Builder
import zio.*
import zio.ZIO.fromOption
import zio.stream.ZStream

final class OpenAI private(
  // Clients have nothing in common; so this trick is used.
  private val client: OpenAIClient = null,
  private val asyncClient: OpenAIClientAsync = null
):
  import OpenAI.*
  private def buildParams(buildWith: BuildF) =
    ZIO.attempt(
      buildWith(ChatCompletionCreateParams.builder()).build()
    )

  def completions(buildWith: BuildF): ZStream[Any, Throwable, String] = for
    params <- ZStream.fromZIO(buildParams(buildWith))
    out    <-
      ZStream.fromJavaStream(
        client
          .chat()
          .completions()
          .create(params)
          .choices()
          .stream()
          .flatMap(_.message().content().stream())
      )
  yield out

  def completionsAsString(buildWith: BuildF): Task[String] =
    completions(buildWith).runCollect.map(_.mkString)

  def completionStream(
    buildWith: BuildF,
    onComplete: String => ZIO[Any, Throwable, Unit] = _ => ZIO.unit
  ): ZStream[Any, Throwable, String] = for
    params           <- ZStream.fromZIO(buildParams(buildWith))
    assistantResponse = new StringBuilder()
    tokens           <-
      ZStream.async[Any, Throwable, String] { cb =>
        val future = asyncClient
          .chat()
          .completions()
          .createStreaming(params)
          .subscribe(
            _.choices()
              .stream()
              .flatMap(_.delta().content().stream())
              .forEach { (content: String) =>
                cb(ZIO.succeed(Chunk.single(content)))
                assistantResponse.append(content)
              }
          )
          .onCompleteFuture()

        val completionEffect =
          ZIO
            .fromCompletableFuture(future)
            .foldCauseZIO(
              cause => ZIO.attempt(cb(ZIO.fail(Some(cause.squash)))),
              _ => ZIO.attempt(cb(ZIO.fail(None))).zipLeft(onComplete(assistantResponse.toString()))
            )

        Unsafe.unsafe(implicit unsafe => Runtime.default.unsafe.fork(completionEffect))
      }
  yield tokens

object OpenAI:
  type BuildF = Builder => Builder

  trait Error(val message: String) extends Throwable:
    override def getMessage: String = message

  final case class ConfigurationError(override val message: String) extends Error(message)

  private def readOpenAIAPIKey: Task[String] = for
    maybeOpenAPIKey <- System.env("OPENAI_API_KEY")
    openAPIKey      <- fromOption(maybeOpenAPIKey).orElseFail(ConfigurationError(s"Missing OPENAI_API_KEY"))
  yield openAPIKey

  def completionStream(
    buildWith: BuildF,
    onComplete: String => ZIO[Any, Throwable, Unit] = _ => ZIO.unit
  ): ZStream[OpenAI, Throwable, String] =
    ZStream.serviceWithStream[OpenAI](_.completionStream(buildWith, onComplete))

  def liveWithClient: TaskLayer[OpenAI] = ZLayer.scoped:
    for
      openAPIKey <- readOpenAIAPIKey
      client      = OpenAIOkHttpClient.builder().apiKey(openAPIKey).build()
    yield new OpenAI(client, asyncClient = null)

  def liveWithAsyncClient: TaskLayer[OpenAI] = ZLayer.scoped:
    for
      openAPIKey <- readOpenAIAPIKey
      client      = OpenAIOkHttpClientAsync.builder().apiKey(openAPIKey).build()
    yield new OpenAI(client = null, asyncClient = client)
