package org.example.client;

import org.example.common.Message;
import org.jline.terminal.Terminal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GameClientTest {

    @Mock
    private Terminal mockTerminal;

    @Test
    void testIsConnectedReturnsFalseBeforeConnect() {
        GameClient client = new GameClient("localhost", 25568, mockTerminal);
        assertFalse(client.isConnected());
    }

    @Test
    void testHandleCurrentWordUpdatesDisplayErrorsAndLetters() throws Exception {
        when(mockTerminal.getWidth()).thenReturn(80);
        GameClient client = new GameClient("localhost", 25568, mockTerminal);

        invoke(client, new Message(Message.Type.CURRENT_WORD, "Server", "J _ V _", 1, "J, Z"));

        assertEquals("J _ V _", getField(client, "currentWordDisplay"));
        assertEquals(1,         getIntField(client, "errorCount"));
        assertEquals("J, Z",    getField(client, "usedLetters"));
    }

    @Test
    void testHandleGuessUpdatesErrorCountUsedLettersAndStatus() throws Exception {
        when(mockTerminal.getWidth()).thenReturn(80);
        GameClient client = new GameClient("localhost", 25568, mockTerminal);

        invoke(client, new Message(Message.Type.GUESS, "Server", "Z", 1, "Z"));

        assertEquals(1,   getIntField(client, "errorCount"));
        assertEquals("Z", getField(client, "usedLetters"));
        String status = getField(client, "statusMessage");
        assertTrue(status.contains("Z"), "Le statut doit mentionner la mauvaise lettre");
    }

    @Test
    void testHandleWinSetsStatusContainingBravo() throws Exception {
        when(mockTerminal.getWidth()).thenReturn(80);
        GameClient client = new GameClient("localhost", 25568, mockTerminal);

        invoke(client, new Message(Message.Type.WIN, "Server", "Bravo ! Le mot etait JAVA"));

        String status = getField(client, "statusMessage");
        assertTrue(status.contains("Bravo"), "Le statut doit indiquer la victoire");
    }

    @Test
    void testHandleLoseSetsStatusContainingPerdu() throws Exception {
        when(mockTerminal.getWidth()).thenReturn(80);
        GameClient client = new GameClient("localhost", 25568, mockTerminal);

        invoke(client, new Message(Message.Type.LOSE, "Server", "Perdu ! Le mot etait JAVA"));

        String status = getField(client, "statusMessage");
        assertTrue(status.contains("Perdu"), "Le statut doit indiquer la défaite");
    }

    @Test
    void testHandleNewGameResetsStateCompletely() throws Exception {
        when(mockTerminal.getWidth()).thenReturn(80);
        GameClient client = new GameClient("localhost", 25568, mockTerminal);

        invoke(client, new Message(Message.Type.GUESS, "Server", "Z", 3, "A, B, Z"));
        invoke(client, new Message(Message.Type.NEW_GAME, "Server", "_ _ _ _", 0, ""));

        assertEquals("_ _ _ _", getField(client, "currentWordDisplay"));
        assertEquals(0,          getIntField(client, "errorCount"));
        assertEquals("",         getField(client, "usedLetters"));
    }

    @Test
    void testHandleErrorUpdatesErrorCountAndLetters() throws Exception {
        when(mockTerminal.getWidth()).thenReturn(80);
        GameClient client = new GameClient("localhost", 25568, mockTerminal);

        invoke(client, new Message(Message.Type.ERROR, "Server", "Lettre deja utilisee", 2, "A, B"));

        assertEquals(2,      getIntField(client, "errorCount"));
        assertEquals("A, B", getField(client, "usedLetters"));
    }

    @Test
    void testSendThrowsIOExceptionWhenNotConnected() {
        GameClient client = new GameClient("localhost", 25568, mockTerminal);
        Message msg = new Message(Message.Type.GUESS, "client", "A");
        assertThrows(IOException.class, () -> client.send(msg));
    }

    @Test
    void testSendWritesMessageIntoOutputStreamWhenConnected() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);

        GameClient client = new GameClient("localhost", 25568, mockTerminal);
        injectField(client, "out", oos);
        injectField(client, "connected", true);

        client.send(new Message(Message.Type.GUESS, "client", "A"));

        assertTrue(baos.size() > 0, "Le message doit être sérialisé dans le flux");
    }

    @Test
    void testRedrawUsesTerminalWidthFromMock() throws Exception {
        when(mockTerminal.getWidth()).thenReturn(120);
        GameClient client = new GameClient("localhost", 25568, mockTerminal);

        invoke(client, new Message(Message.Type.CURRENT_WORD, "Server", "_ _", 0, ""));

        verify(mockTerminal, atLeastOnce()).getWidth();
    }

    private void invoke(GameClient client, Message msg) throws Exception {
        Method m = GameClient.class.getDeclaredMethod("handleMessage", Message.class);
        m.setAccessible(true);
        m.invoke(client, msg);
    }

    @SuppressWarnings("unchecked")
    private <T> T getField(Object obj, String name) throws Exception {
        Field f = obj.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return (T) f.get(obj);
    }

    private int getIntField(Object obj, String name) throws Exception {
        Field f = obj.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.getInt(obj);
    }

    private void injectField(Object obj, String name, Object value) throws Exception {
        Field f = obj.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(obj, value);
    }
}
