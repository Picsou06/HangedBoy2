package org.example.common;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class SecureObjectInputStreamTest {

    @Test
    void testAllowedClassDeserialization() throws IOException, ClassNotFoundException {
        Message message = new Message(Message.Type.CONNECT, "user1", "Hello");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(message);
        }

        try (SecureObjectInputStream sois = new SecureObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
            Message deserialized = (Message) sois.readObject();
            assertEquals(message.getType(), deserialized.getType());
            assertEquals(message.getSenderId(), deserialized.getSenderId());
            assertEquals(message.getContent(), deserialized.getContent());
        }
    }

    @Test
    void testAllowedArrayDeserialization() throws IOException, ClassNotFoundException {
        String[] stringArray = new String[]{"test1", "test2"};
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(stringArray);
        }

        try (SecureObjectInputStream sois = new SecureObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
            String[] deserialized = (String[]) sois.readObject();
            assertEquals("test1", deserialized[0]);
            assertEquals("test2", deserialized[1]);
        }
    }

    @Test
    void testDisallowedClassDeserializationThrowsException() throws IOException {
        MaliciousMock malicious = new MaliciousMock();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(malicious);
        }

        try (SecureObjectInputStream sois = new SecureObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
            assertThrows(InvalidClassException.class, sois::readObject);
        }
    }

    private static class MaliciousMock implements Serializable {
        private static final long serialVersionUID = 1L;
    }
}
