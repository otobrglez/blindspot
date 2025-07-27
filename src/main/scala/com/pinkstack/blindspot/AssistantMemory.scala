package com.pinkstack.blindspot

import zio.{ULayer, ZLayer}

import java.util
import java.util.UUID
import java.util.concurrent.{ConcurrentHashMap, ConcurrentMap}
import scala.jdk.CollectionConverters.*

final class AssistantMemory(
  private val memory: ConcurrentMap[AssistantMemory.SessionID, util.ArrayList[Message]] = new ConcurrentHashMap()
) extends AnyRef:
  def appendMessage(sessionID: AssistantMemory.SessionID, message: Message): Unit = synchronized {
    memory.computeIfAbsent(sessionID, _ => new util.ArrayList[Message]()).add(message)
  }

  def appendMessages(sessionID: AssistantMemory.SessionID, messages: Seq[Message]): Unit = synchronized {
    val sessionMessages = memory.computeIfAbsent(sessionID, _ => new util.ArrayList[Message]())
    messages.foreach(sessionMessages.add)
  }

  def getSessionHistory(sessionID: AssistantMemory.SessionID): List[Message] =
    Option(memory.get(sessionID))
      .map(_.asScala.toList)
      .getOrElse(List.empty)

  def hasSession(sessionID: AssistantMemory.SessionID): Boolean =
    memory.containsKey(sessionID)

object AssistantMemory:
  type SessionID = UUID
  def live: ULayer[AssistantMemory] = ZLayer.succeed(new AssistantMemory())
