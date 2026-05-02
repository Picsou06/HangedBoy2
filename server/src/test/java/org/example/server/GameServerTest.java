package org.example.server;

import org.example.common.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GameServerTest {

    @Mock
    private ClientHandler mockSender;

    @Mock
    private ClientHandler mockOtherClient;

    private GameServer server;

    @BeforeEach
    void setUp() throws Exception {
        server = new GameServer(0);
        forceWord(server, "JAVA");
    }

    @Test
    void testInitialWordDisplayContainsOnlyUnderscores() throws Exception {
        String display = getField(server, "currentWordDisplay");
        assertTrue(display.chars().allMatch(c -> c == '_' || c == ' '),
                "Le display initial ne doit contenir que des underscores");
    }

    @Test
    void testInitialErrorCountIsZero() throws Exception {
        assertEquals(0, getIntField(server, "errorCount"));
    }

    @Test
    void testCorrectGuessBroadcastsUpdatedWordDisplay() {
        server.addClient(mockSender);
        server.guessLetter('J', mockSender);

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(mockSender, atLeastOnce()).send(captor.capture());

        boolean hasCurrent = captor.getAllValues().stream()
                .anyMatch(m -> m.getType() == Message.Type.CURRENT_WORD
                        && m.getContent().contains("J"));
        assertTrue(hasCurrent, "Un message CURRENT_WORD avec 'J' doit être diffusé");
    }

    @Test
    void testCorrectGuessDoesNotIncrementErrorCount() throws Exception {
        server.addClient(mockSender);
        server.guessLetter('J', mockSender);

        assertEquals(0, getIntField(server, "errorCount"));
    }

    @Test
    void testIncorrectGuessIncrementsErrorCount() throws Exception {
        server.addClient(mockSender);
        server.guessLetter('Z', mockSender);

        assertEquals(1, getIntField(server, "errorCount"));
    }

    @Test
    void testIncorrectGuessBroadcastsGuessMessage() {
        server.addClient(mockSender);
        server.guessLetter('Z', mockSender);

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(mockSender, atLeastOnce()).send(captor.capture());

        boolean hasGuess = captor.getAllValues().stream()
                .anyMatch(m -> m.getType() == Message.Type.GUESS);
        assertTrue(hasGuess, "Un message GUESS doit être diffusé pour une mauvaise lettre");
    }

    @Test
    void testAlreadyGuessedLetterSendsErrorToSenderOnly() {
        server.addClient(mockSender);
        server.guessLetter('J', mockSender);
        clearInvocations(mockSender);

        server.guessLetter('J', mockSender);

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(mockSender).send(captor.capture());
        assertEquals(Message.Type.ERROR, captor.getValue().getType());
    }

    @Test
    void testAlreadyGuessedLetterDoesNotChangeErrorCount() throws Exception {
        server.addClient(mockSender);
        server.guessLetter('Z', mockSender);
        int errorsBefore = getIntField(server, "errorCount");

        server.guessLetter('Z', mockSender);
        int errorsAfter = getIntField(server, "errorCount");

        assertEquals(errorsBefore, errorsAfter);
    }

    @Test
    void testWinGameBroadcastsWinMessage() {
        server.addClient(mockSender);
        for (char c : "JAV".toCharArray()) {
            server.guessLetter(c, mockSender);
        }

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(mockSender, atLeastOnce()).send(captor.capture());

        boolean hasWin = captor.getAllValues().stream()
                .anyMatch(m -> m.getType() == Message.Type.WIN);
        assertTrue(hasWin, "Un message WIN doit être diffusé quand le mot est trouvé");
    }

    @Test
    void testLoseGameBroadcastsLoseMessageAfterMaxErrors() {
        server.addClient(mockSender);
        for (char c : "BCDEFGHIKL".toCharArray()) {
            server.guessLetter(c, mockSender);
        }

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(mockSender, atLeastOnce()).send(captor.capture());

        boolean hasLose = captor.getAllValues().stream()
                .anyMatch(m -> m.getType() == Message.Type.LOSE);
        assertTrue(hasLose, "Un message LOSE doit être diffusé après " + Message.MAX_ERRORS + " erreurs");
    }

    @Test
    void testBroadcastSendsToAllClientsExceptExcluded() {
        server.addClient(mockSender);
        server.addClient(mockOtherClient);

        Message msg = new Message(Message.Type.CONNECT, "SERVER", "test");
        server.broadcast(msg, mockSender);

        verify(mockOtherClient).send(msg);
        verify(mockSender, never()).send(msg);
    }

    @Test
    void testBroadcastWithNullExcludeReachesAllClients() {
        server.addClient(mockSender);
        server.addClient(mockOtherClient);

        Message msg = new Message(Message.Type.NEW_GAME, "SERVER", "_ _ _ _");
        server.broadcast(msg, null);

        verify(mockSender).send(msg);
        verify(mockOtherClient).send(msg);
    }

    @Test
    void testAddAndRemoveClientUpdatesCount() {
        assertEquals(0, server.getClientCount());
        server.addClient(mockSender);
        assertEquals(1, server.getClientCount());
        server.addClient(mockOtherClient);
        assertEquals(2, server.getClientCount());
        server.removeClient(mockSender);
        assertEquals(1, server.getClientCount());
    }

    @Test
    void testSendCurrentStateSendsCurrentWordMessageToClient() {
        server.sendCurrentStateTo(mockSender);

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(mockSender).send(captor.capture());
        assertEquals(Message.Type.CURRENT_WORD, captor.getValue().getType());
    }

    @Test
    void testSetNewWordResetsErrorCountAndGuessedLetters() throws Exception {
        server.addClient(mockSender);
        server.guessLetter('Z', mockSender);

        server.setNewWord();

        assertEquals(0, getIntField(server, "errorCount"));
        Set<?> guessed = getField(server, "guessedLetters");
        assertTrue(guessed.isEmpty());
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

    @SuppressWarnings("unchecked")
    private void forceWord(GameServer srv, String word) throws Exception {
        Field currentWord = GameServer.class.getDeclaredField("currentWord");
        currentWord.setAccessible(true);
        currentWord.set(srv, word);

        Field guessedLetters = GameServer.class.getDeclaredField("guessedLetters");
        guessedLetters.setAccessible(true);
        ((Set<Character>) guessedLetters.get(srv)).clear();

        Field errorCount = GameServer.class.getDeclaredField("errorCount");
        errorCount.setAccessible(true);
        errorCount.set(srv, 0);

        StringBuilder display = new StringBuilder();
        for (int i = 0; i < word.length(); i++) {
            display.append(i < word.length() - 1 ? "_ " : "_");
        }
        Field displayField = GameServer.class.getDeclaredField("currentWordDisplay");
        displayField.setAccessible(true);
        displayField.set(srv, display.toString());
    }
}
