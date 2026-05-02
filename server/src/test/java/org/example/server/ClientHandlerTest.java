package org.example.server;

import org.example.common.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.*;
import java.lang.reflect.Field;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClientHandlerTest {

    @Mock
    private GameServer mockServer;

    @Mock
    private Socket mockSocket;

    @Test
    void testGetClientIdReturnsAssignedId() {
        ClientHandler handler = new ClientHandler(mockSocket, mockServer, "player42");
        assertEquals("player42", handler.getClientId());
    }

    @Test
    void testSendWritesSerializedMessageToOutputStream() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);

        ClientHandler handler = new ClientHandler(mockSocket, mockServer, "player1");
        injectField(handler, "out", oos);

        Message msg = new Message(Message.Type.CONNECT, "SERVER", "Bienvenue !");
        handler.send(msg);

        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()));
        Message received = (Message) ois.readObject();

        assertEquals(Message.Type.CONNECT, received.getType());
        assertEquals("Bienvenue !", received.getContent());
    }

    @Test
    void testSendDoesNothingWhenOutputStreamIsNull() {
        ClientHandler handler = new ClientHandler(mockSocket, mockServer, "player1");
        assertDoesNotThrow(() ->
                handler.send(new Message(Message.Type.CONNECT, "SERVER", "test")));
    }

    @Test
    void testRunHandlesGuessMessageAndDelegatesToServer() throws Exception {
        ByteArrayInputStream bais = buildInputStream(
                new Message(Message.Type.GUESS, "client", "A"));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        when(mockSocket.getInputStream()).thenReturn(bais);
        when(mockSocket.getOutputStream()).thenReturn(baos);
        when(mockSocket.isClosed()).thenReturn(false);

        ClientHandler handler = new ClientHandler(mockSocket, mockServer, "player1");
        handler.run();

        verify(mockServer).addClient(handler);
        verify(mockServer).guessLetter('A', handler);
        verify(mockServer).removeClient(handler);
    }

    @Test
    void testRunIgnoresEmptyGuessContent() throws Exception {
        ByteArrayInputStream bais = buildInputStream(
                new Message(Message.Type.GUESS, "client", ""));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        when(mockSocket.getInputStream()).thenReturn(bais);
        when(mockSocket.getOutputStream()).thenReturn(baos);
        when(mockSocket.isClosed()).thenReturn(false);

        ClientHandler handler = new ClientHandler(mockSocket, mockServer, "player1");
        handler.run();

        verify(mockServer, never()).guessLetter(anyChar(), any());
    }

    @Test
    void testRunBroadcastsNonGuessMessages() throws Exception {
        ByteArrayInputStream bais = buildInputStream(
                new Message(Message.Type.CONNECT, "client", "salut"));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        when(mockSocket.getInputStream()).thenReturn(bais);
        when(mockSocket.getOutputStream()).thenReturn(baos);
        when(mockSocket.isClosed()).thenReturn(false);

        ClientHandler handler = new ClientHandler(mockSocket, mockServer, "player1");
        handler.run();

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(mockServer, atLeastOnce()).broadcast(captor.capture(), isNull());
        assertTrue(captor.getAllValues().stream()
                .anyMatch(m -> m.getType() == Message.Type.CONNECT
                        && "salut".equals(m.getContent())));

        verify(mockServer, never()).guessLetter(anyChar(), any());
    }

    @Test
    void testRunSendsWelcomeMessageToNewClient() throws Exception {
        ByteArrayInputStream bais = buildInputStream();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        when(mockSocket.getInputStream()).thenReturn(bais);
        when(mockSocket.getOutputStream()).thenReturn(baos);
        when(mockSocket.isClosed()).thenReturn(false);

        ClientHandler handler = new ClientHandler(mockSocket, mockServer, "Alice");
        handler.run();

        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()));
        Message welcome = (Message) ois.readObject();

        assertEquals(Message.Type.CONNECT, welcome.getType());
        assertTrue(welcome.getContent().contains("Alice"));
    }

    @Test
    void testStopClosesSocket() throws Exception {
        when(mockSocket.isClosed()).thenReturn(false);
        ClientHandler handler = new ClientHandler(mockSocket, mockServer, "player1");
        handler.stop();
        verify(mockSocket).close();
    }

    private ByteArrayInputStream buildInputStream(Message... messages) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(buf);
        for (Message m : messages) {
            oos.writeObject(m);
        }
        oos.flush();
        return new ByteArrayInputStream(buf.toByteArray());
    }

    private void injectField(Object obj, String name, Object value) throws Exception {
        Field f = obj.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(obj, value);
    }
}
