package com.pinkstack.blindspot.clients

import zio.*
import zio.test.{test, *}
import zio.test.Assertion.*
import com.openai.models.ChatModel
import com.pinkstack.blindspot.Prompts

object OpenAISpec extends ZIOSpecDefault:
  private val enabled: Boolean = false
  private def openAIConfig     = ZLayer.fromZIO(
    TestSystem.putEnv("OPENAI_API_KEY", java.lang.System.getenv("OPENAI_API_KEY"))
  )

  val devPrompt  = "Be analytical and provide a detailed explanation of the following:"
  val userPrompt = "Star Wars franchise is not available in India. Explain why it should be and why it should not be."

  def spec = suite("OpenAI")(
    test("non-streaming response") {
      val program = for
        openAI <- ZIO.service[OpanAI]
        _      <-
          openAI
            .completions(
              _.model(ChatModel.GPT_3_5_TURBO)
                .maxCompletionTokens(1048)
                .addSystemMessage(Prompts.systemPrompt)
                .addDeveloperMessage(devPrompt)
                .addUserMessage(userPrompt)
            )
            .runForeach(p => zio.Console.printLine(p))
      yield assertCompletes
      program.provide(openAIConfig >>> OpanAI.liveWithClient)
    }.when(enabled),
    test("async responses") {
      val program = for
        openAI       <- ZIO.service[OpanAI]
        finalMessage <- Promise.make[Throwable, String]
        _            <-
          openAI
            .completionStream(
              _.model(ChatModel.GPT_3_5_TURBO)
                .maxCompletionTokens(1048)
                .addSystemMessage(Prompts.systemPrompt)
                .addDeveloperMessage(devPrompt)
                .addUserMessage(userPrompt),
              onComplete = m => finalMessage.succeed(m).unit
            )
            .runForeach(p => zio.Console.print(p))
        message      <- finalMessage.await
      yield assertTrue(!(message.isEmpty))
      program.provide(openAIConfig >>> OpanAI.liveWithAsyncClient)
    }
  )
