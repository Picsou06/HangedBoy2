package org.example.common;

import java.io.Serializable;

public class Message implements Serializable {

    public enum Type {
        CONNECT, DISCONNECT, GUESS, NEW_GAME, CURRENT_WORD, ERROR
    }

    private final Type type;
    private final String senderId;
    private final String content;

    public Message(Type type, String senderId, String content) {
        this.type = type;
        this.senderId = senderId;
        this.content = content;
    }

    public Type getType() { return type; }
    public String getSenderId() { return senderId; }
    public String getContent() { return content; }

    @Override
    public String toString() {
        return "[" + type + "] " + senderId + ": " + content;
    }
}
