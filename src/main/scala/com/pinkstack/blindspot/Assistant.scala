package com.pinkstack.blindspot

import com.openai.models.ChatModel
import com.openai.models.chat.completions.ChatCompletionCreateParams.Builder
import com.openai.models.chat.completions.*
import com.pinkstack.blindspot.Grid.Row
import com.pinkstack.blindspot.Model.ItemKind.{Movie, Show}
import com.pinkstack.blindspot.clients.OpanAI
import com.pinkstack.blindspot.clients.OpanAI.BuildF
import zio.*
import zio.ZIO.logInfo
import zio.http.{Request, ServerSentEvent}
import zio.stream.ZStream

import java.util
import java.util.UUID
import scala.jdk.CollectionConverters.*
enum ChatMessageRole:
  case Assistant, User, System, Developer

final case class Message(id: UUID, message: String, role: ChatMessageRole)
object Message:
  def fromAssistant(id: UUID, message: String): Message                     = Message(id, message, role = ChatMessageRole.Assistant)
  def fromUser(id: UUID, message: String): Message                          = Message(id, message, role = ChatMessageRole.User)
  def fromSystem(message: String, id: UUID = UUID.randomUUID()): Message    =
    Message(id, message, role = ChatMessageRole.System)
  def fromDeveloper(message: String, id: UUID = UUID.randomUUID()): Message =
    Message(id, message, role = ChatMessageRole.Developer)

final case class Assistant() {}
object Assistant:

  private val toOpenAIHistory: List[Message] => List[ChatCompletionMessageParam] = _.map:
    case Message(_, message, ChatMessageRole.System)                            =>
      ChatCompletionMessageParam.ofSystem(ChatCompletionSystemMessageParam.builder().content(message).build())
    case com.pinkstack.blindspot.Message(_, message, ChatMessageRole.Assistant) =>
      ChatCompletionMessageParam.ofAssistant(ChatCompletionAssistantMessageParam.builder().content(message).build())
    case com.pinkstack.blindspot.Message(_, message, ChatMessageRole.User)      =>
      ChatCompletionMessageParam.ofUser(ChatCompletionUserMessageParam.builder().content(message).build())
    case com.pinkstack.blindspot.Message(_, message, ChatMessageRole.Developer) =>
      ChatCompletionMessageParam.ofDeveloper(ChatCompletionDeveloperMessageParam.builder().content(message).build())

  private def gridToPrompt(grid: List[Row]): String =
    "You know about movies and availability (in countries and streaming providers): " ++ grid
      .filter(_.kind == Movie)
      .map(m =>
        s"\n TITLE: ${m.title} \nRELESED YEAR: ${m.releaseYear})\n " +
          s"\n- IMDB SCORE: ${m.imdbScore.getOrElse("none")}, VOTES: ${m.imdbVotes.getOrElse("none")}" +
          s"\n- DESCRIPTION: ${m.description}" +
          s"\n- AVAIABLE IN COUNTRIES: ${getAvailableCountries(m)}." +
          s"\n- AVAIABLE IN PLATFORMS: ${getAvailablePlatforms(m)}."
      )
      .mkString(", ") ++ ". " ++
      "You also know shows: " ++ grid
        .filter(_.kind == Show)
        .map(m =>
          s"\n TITLE: ${m.title} \nRELESED YEAR: ${m.releaseYear})\n " +
            s"\n- IMDB SCORE: ${m.imdbScore.getOrElse("none")}, VOTES: ${m.imdbVotes.getOrElse("none")}" +
            s"\n- DESCRIPTION: ${m.description}" +
            s"\n- AVAIABLE IN COUNTRIES: ${getAvailableCountries(m)}." +
            s"\n- AVAIABLE IN PLATFORMS: ${getAvailablePlatforms(m)}."
        )
        .mkString(", ")

  private def getAvailableCountries(row: Row): String =
    row.providerTags.toList.flatMap(_.split(":").headOption.toList).distinct.mkString(", ")

  private def getAvailablePlatforms(row: Row): String =
    val slugs = row.providerTags.toList.flatMap(_.split(":").lastOption.toList).toSet
    Model.supportedPlatforms.filter(p => p.value.subsetOf(slugs)).map(_.name).mkString(", ")

  private def sessionBuilder(
    sessionID: UUID,
    memory: AssistantMemory,
    grid: List[Row],
    userInput: Option[String],
    maxCompletionTokens: Int = 2048
  ): (List[Message], BuildF) = memory.getSessionHistory(sessionID) -> userInput match
    case Nil -> _ =>
      // New session
      val messages @ List(systemM, gridM) = List(
        Message.fromSystem(Prompts.systemPrompt),
        Message.fromDeveloper(gridToPrompt(grid) ++ "\nGreet the user and ask him about one of the movies or shows.")
      )

      messages -> { (b: Builder) =>
        b.model(ChatModel.GPT_3_5_TURBO)
          .maxCompletionTokens(maxCompletionTokens)
          .addSystemMessage(systemM.message)
          .addDeveloperMessage(gridM.message)
      }

    case oldMessages -> Some(userInput) =>
      println("---- you are here ----")
      // Old messages + new user input
      val messages @ List(systemM, developerM, userM) = List(
        Message.fromSystem(Prompts.intro),
        Message.fromDeveloper(
          gridToPrompt(grid) ++
            """These are the movies and shows user is interested in.
              |If you don't know about them. Ask user to find it in the UI first.""".stripMargin
        ),
        Message.fromUser(UUID.randomUUID(), userInput)
      )
      messages -> { (b: Builder) =>
        b.model(ChatModel.GPT_3_5_TURBO)
          .messages(toOpenAIHistory(oldMessages).asJava)
          .maxCompletionTokens(maxCompletionTokens)
          .addSystemMessage(systemM.message)
          .addDeveloperMessage(developerM.message) // Inject stuff!
          .addUserMessage(userM.message)
      }

    case oldMessages -> None =>
      // Old messages + no user input
      val messages @ List(systemPromptMessage, greetAgainMessage) = List(
        Message.fromSystem(Prompts.intro),
        Message.fromDeveloper(Prompts.greetAgain)
      )
      messages -> { (b: Builder) =>
        b.model(ChatModel.GPT_3_5_TURBO)
          .messages(toOpenAIHistory(oldMessages).asJava)
          .maxCompletionTokens(maxCompletionTokens)
          .addSystemMessage(systemPromptMessage.message)
          .addDeveloperMessage(greetAgainMessage.message) // Inject stuff!
      }

  def sseStreamFromRequest(
    openAI: OpanAI,
    memory: AssistantMemory,
    grid: List[Row],
    request: Request
  ): ZStream[Any, Nothing, ServerSentEvent[String]] = for
    _        <- ZStream.unit
    userInput = request.queryOrElse[Option[String]]("input", None)
    sessionID = UUID.fromString(request.queryOrElse[String]("sessionID", UUID.randomUUID().toString))
    messageID = UUID.randomUUID()
    _        <- ZStream.logInfo("Using sessionID: " + sessionID)

    _ <- ZStream.fromZIO(logInfo(s"Responding to session ${sessionID} with user input: \"${userInput}\""))
    _  = memory.getSessionHistory(sessionID).foreach(m => println(s"> ${m.role} - ${m.message}"))

    (messages, buildWith) = sessionBuilder(sessionID, memory, grid, userInput)
    _                     = memory.appendMessages(sessionID, messages)

    token <-
      OpanAI
        .completionStream(
          buildWith = buildWith,
          onComplete =
            message => ZIO.succeed(memory.appendMessage(sessionID, Message.fromAssistant(messageID, message)))
        )
        .catchAll(th => ZStream.fromIterable(List(s"[ERROR] ${th}")))
        .provideLayer(ZLayer.succeed(openAI))
  yield ServerSentEvent(messageID.toString ++ "||" ++ token)
