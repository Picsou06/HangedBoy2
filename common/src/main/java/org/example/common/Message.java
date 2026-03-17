package org.example.common;

import java.io.Serializable;

public class Message implements Serializable {

    private static final long serialVersionUID = 2L;

    public static final int MAX_ERRORS = 10;

    public enum Type {
        CONNECT, DISCONNECT, GUESS, NEW_GAME, WIN, LOSE, CURRENT_WORD, ERROR
    }

    private final Type type;
    private final String senderId;
    private final String content;
    private final int errorCount;
    private final String usedLetters;

    public Message(Type type, String senderId, String content) {
        this(type, senderId, content, 0, "");
    }

    public Message(Type type, String senderId, String content, int errorCount, String usedLetters) {
        this.type = type;
        this.senderId = senderId;
        this.content = content;
        this.errorCount = errorCount;
        this.usedLetters = usedLetters != null ? usedLetters : "";
    }

    public Type getType() { return type; }
    public String getSenderId() { return senderId; }
    public String getContent() { return content; }
    public int getErrorCount() { return errorCount; }
    public String getUsedLetters() { return usedLetters; }

    @Override
    public String toString() {
        return "[" + type + "] " + senderId + ": " + content + " (errors=" + errorCount + ")";
    }
}
