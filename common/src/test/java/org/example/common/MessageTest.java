package org.example.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MessageTest {

    @Test
    void testFullConstructorStoresAllFields() {
        Message msg = new Message(Message.Type.GUESS, "player1", "A", 3, "A, B, C");

        assertEquals(Message.Type.GUESS, msg.getType());
        assertEquals("player1", msg.getSenderId());
        assertEquals("A", msg.getContent());
        assertEquals(3, msg.getErrorCount());
        assertEquals("A, B, C", msg.getUsedLetters());
    }

    @Test
    void testShortConstructorDefaultsToZeroErrorsAndEmptyLetters() {
        Message msg = new Message(Message.Type.CONNECT, "SERVER", "Bienvenue !");

        assertEquals(0, msg.getErrorCount());
        assertEquals("", msg.getUsedLetters());
    }

    @Test
    void testNullUsedLettersBecomesEmptyString() {
        Message msg = new Message(Message.Type.CONNECT, "SERVER", "hello", 0, null);

        assertEquals("", msg.getUsedLetters());
    }

    @Test
    void testToStringContainsTypeAndContent() {
        Message msg = new Message(Message.Type.WIN, "SERVER", "Bravo !");
        String str = msg.toString();

        assertTrue(str.contains("WIN"));
        assertTrue(str.contains("Bravo !"));
    }

    @Test
    void testAllMessageTypesAreConstructible() {
        for (Message.Type type : Message.Type.values()) {
            Message msg = new Message(type, "sender", "content");
            assertEquals(type, msg.getType());
        }
    }

}
