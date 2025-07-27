import {v4 as uuidv4} from 'uuid';

export type UUID = string;

export interface Message {
  id: UUID;
  message: string;
  fromUser: boolean;
}

export function buildUserMessage(message: string): Message {
  return {
    id: uuidv4(),
    message: message,
    fromUser: true
  }
}

export function buildAssistantMessage(message: string): Message {
  const [id, token] = message.split("||", 2);
  return {
    id,
    message: token,
    fromUser: false
  }
}
