package org.example.client;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Reader;
import java.net.Socket;
import java.net.SocketException;

import org.example.common.Message;
import org.example.common.SecureObjectInputStream;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GameClient {

    private static final Logger logger = LoggerFactory.getLogger(GameClient.class);
    private static final int DEFAULT_PORT = 25568;

    private static final String[][] HANGMAN_STAGES = {
        {"       ", "       ", "       ", "       ", "       ", "       ", "       "},
        {"       ", "       ", "       ", "       ", "       ", "       ", "========="},
        {"      |", "      |", "      |", "      |", "      |", "      |", "========="},
        {"   ---+", "      |", "      |", "      |", "      |", "      |", "========="},
        {"  +---+", "  |   |", "      |", "      |", "      |", "      |", "========="},
        {"  +---+", "  |   |", "  O   |", "      |", "      |", "      |", "========="},
        {"  +---+", "  |   |", "  O   |", "  |   |", "      |", "      |", "========="},
        {"  +---+", "  |   |", "  O   |", "  |   |", " /    |", "      |", "========="},
        {"  +---+", "  |   |", "  O   |", "  |   |", " / \\  |", "      |", "========="},
        {"  +---+", "  |   |", "  O   |", " /|   |", " / \\  |", "      |", "========="},
        {"  +---+", "  |   |", "  O   |", " /|\\  |", " / \\  |", "      |", "========="}
    };

    private final String host;
    private final int port;
    private Socket socket;
    private ObjectOutputStream out;
    private Thread listenerThread;
    private volatile boolean connected = false;

    private volatile String currentWordDisplay = "";
    private volatile int errorCount = 0;
    private volatile String usedLetters = "";
    private volatile String statusMessage = "Connexion au serveur...";
    private volatile String lastProposer = "";

    private Terminal terminal;

    private int lastLineCount = 0;
    private volatile boolean userInputPending = false;

    public GameClient(String host, int port, Terminal terminal) {
        this.host = host;
        this.port = port;
        this.terminal = terminal;
    }

    public void connect() throws IOException {
        socket = new Socket(host, port);
        out = new ObjectOutputStream(socket.getOutputStream());
        out.flush();
        connected = true;

        listenerThread = new Thread(this::listenForMessages, "client-listener");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    private void listenForMessages() {
        String fatalError = null;
        try (ObjectInputStream in = new SecureObjectInputStream(socket.getInputStream())) {
            while (connected) {
                Message msg = (Message) in.readObject();
                handleMessage(msg);
            }
        } catch (EOFException | SocketException ignored) {
        } catch (IOException | ClassNotFoundException e) {
            if (connected) {
                fatalError = "Erreur de reception : " + e.getMessage();
                logger.error("Reception error: {}", e.getMessage());
            }
        } finally {
            connected = false;
            if (fatalError != null) {
                showFatalError(fatalError);
                disconnect();
                System.exit(1);
            } else {
                statusMessage = "Deconnecte du serveur.";
                redraw();
            }
        }
    }

    private void handleMessage(Message msg) {
        switch (msg.getType()) {
            case CURRENT_WORD -> {
                currentWordDisplay = msg.getContent();
                errorCount = msg.getErrorCount();
                usedLetters = msg.getUsedLetters();
                if (!msg.getSenderId().equals(Message.SERVER_ID)) {
                    lastProposer = msg.getSenderId();
                    statusMessage = msg.getSenderId() + " a trouve une lettre !";
                } else if (statusMessage.equals("Connexion au serveur...")) {
                    statusMessage = "Devinez le mot !";
                }
            }
            case GUESS -> {
                errorCount = msg.getErrorCount();
                usedLetters = msg.getUsedLetters();
                lastProposer = msg.getSenderId();
                statusMessage = msg.getSenderId() + " : mauvaise lettre (" + msg.getContent() + ")";
            }
            case ERROR -> {
                errorCount = msg.getErrorCount();
                usedLetters = msg.getUsedLetters();
            }
            case WIN  -> statusMessage = "BRAVO " + msg.getSenderId() + " ! " + msg.getContent();
            case LOSE -> statusMessage = "PERDU... " + msg.getContent();
            case NEW_GAME -> {
                currentWordDisplay = msg.getContent();
                errorCount = 0;
                usedLetters = "";
                lastProposer = "";
                statusMessage = "Nouvelle partie ! Devinez le mot.";
            }
            default -> statusMessage = msg.getContent();
        }
        redraw();
    }

    private int getTerminalWidth() {
        if (terminal != null) {
            int w = terminal.getWidth();
            if (w > 0) return w;
        }
        return 80;
    }

    private int getTerminalHeight() {
        if (terminal != null) {
            int h = terminal.getHeight();
            if (h > 0) return h;
        }
        return 24;
    }

    private void clearPreviousOutput(StringBuilder sb) {
        if (lastLineCount > 0) {
            int linesToGoUp = lastLineCount + (userInputPending ? 1 : 0);
            sb.append("\033[").append(linesToGoUp).append("A\r\033[J");
        }
    }

    private int countRenderedLines(String text, int width) {
        if (width <= 0) return 0;
        int lines = 0;
        int lineLen = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                lines += Math.max(1, (lineLen + width - 1) / width);
                lineLen = 0;
            } else {
                lineLen++;
            }
        }
        lines += Math.max(1, (lineLen + width - 1) / width);
        return lines;
    }

    synchronized void showFatalError(String message) {
        int termWidth = getTerminalWidth();
        int termHeight = getTerminalHeight();

        StringBuilder sb = new StringBuilder();
        clearPreviousOutput(sb);

        int topPad = Math.max(0, (termHeight - 3) / 2);
        for (int i = 0; i < topPad; i++) {
            sb.append("\n");
        }

        sb.append("  ").append(centerPad("[ ERREUR ]", termWidth - 4)).append("\n");
        sb.append("\n");
        sb.append("  ").append(centerPad(message, termWidth - 4)).append("\n");

        System.out.print(sb);
        System.out.flush();
    }

    synchronized void redraw() {
        int termWidth = getTerminalWidth();

        int stage = Math.min(errorCount, Message.MAX_ERRORS);
        String[] hangman = HANGMAN_STAGES[stage];

        StringBuilder display = new StringBuilder();

        String title = " LE PENDU ";
        String border = "=".repeat(termWidth - 4);
        display.append("  ").append(border).append("\n");
        display.append("  ").append(centerPad(title, termWidth - 4)).append("\n");
        display.append("  ").append(border).append("\n\n");

        String[] info = {
            "",
            "Mot:      " + (currentWordDisplay.isEmpty() ? "..." : currentWordDisplay),
            "",
            "Lettres:  " + (usedLetters.isEmpty() ? "aucune" : usedLetters),
            "Erreurs:  " + errorCount + " / " + Message.MAX_ERRORS,
            "Dernier:  " + (lastProposer.isEmpty() ? "-" : lastProposer),
            ""
        };

        for (int i = 0; i < hangman.length; i++) {
            String h = "  " + hangman[i];
            String left = h + " ".repeat(Math.max(0, 18 - h.length()));
            String right = info[i].isEmpty() ? "" : "    " + info[i];
            display.append(left).append(right).append("\n");
        }

        display.append("\n  ").append("-".repeat(Math.min(termWidth - 4, 60))).append("\n");

        if (!statusMessage.isEmpty()) {
            display.append("\n  > ").append(statusMessage).append("\n");
        }

        display.append("\n  Entrez une lettre : ");

        int renderedLines = countRenderedLines(display.toString(), termWidth);

        StringBuilder sb = new StringBuilder();
        if (lastLineCount > 0) {
            clearPreviousOutput(sb);
        } else {
            sb.append("\033[2J\033[H");
        }
        userInputPending = false;
        lastLineCount = renderedLines;

        sb.append(display);
        System.out.print(sb);
        System.out.flush();
    }

    private String centerPad(String text, int width) {
        if (text.length() >= width) return text;
        int pad = (width - text.length()) / 2;
        return " ".repeat(pad) + text;
    }

    public void send(Message msg) throws IOException {
        if (!connected) {
            throw new IOException("Not connected to server");
        }
        out.writeObject(msg);
        out.flush();
    }

    public void disconnect() {
        connected = false;
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
        if (terminal != null) {
            try {
                terminal.close();
            } catch (IOException ignored) {
            }
        }
    }

    public boolean isConnected() {
        return connected;
    }

    private static boolean isInteractive(Terminal terminal) {
        return terminal != null
                && !Terminal.TYPE_DUMB.equals(terminal.getType())
                && !Terminal.TYPE_DUMB_COLOR.equals(terminal.getType());
    }

    public void runGame(String pseudo, BufferedReader input) throws IOException {
        while (isConnected()) {
            userInputPending = true;
            String line = input.readLine();
            userInputPending = false;
            if (line == null) break;
            if (line.isEmpty()) continue;
            char letter = Character.toUpperCase(line.trim().charAt(0));
            if (!Character.isLetter(letter)) continue;
            try {
                send(new Message(Message.Type.GUESS, pseudo, String.valueOf(letter)));
            } catch (IOException e) {
                logger.error("Erreur d'envoi : {}", e.getMessage());
                disconnect();
                break;
            }
        }
    }

    private static Terminal buildTerminal() {
        try {
            return TerminalBuilder.builder().system(true).build();
        } catch (IOException e) {
            try {
                return TerminalBuilder.builder().dumb(true).build();
            } catch (IOException ignored) {
                return null;
            }
        }
    }

    private static String[] parseArgsOrPrompt(boolean interactive) throws IOException {
        if (interactive) {
            System.out.print("\033[2J\033[H");
            System.out.flush();
        }

        BufferedReader setupReader = new BufferedReader(new InputStreamReader(System.in));

        String pseudo;
        while (true) {
            System.out.print("Votre pseudo : ");
            pseudo = setupReader.readLine();
            if (pseudo == null) return null;
            pseudo = pseudo.trim();
            if (pseudo.matches("[A-Za-z0-9\\-]{1,16}")) break;
            logger.warn("Pseudo invalide. Utilisez uniquement des lettres, chiffres et '-' (1-16 caracteres).");
        }

        System.out.print("Adresse du serveur (defaut: localhost) : ");
        String hostInput = setupReader.readLine();
        if (hostInput == null) return null;
        String host = hostInput.trim().isEmpty() ? "localhost" : hostInput.trim();

        System.out.print("Port (defaut: 25568) : ");
        String portStr = setupReader.readLine();
        if (portStr == null) return null;

        int port = DEFAULT_PORT;
        portStr = portStr.trim();
        if (!portStr.isEmpty()) {
            try {
                port = Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                logger.warn("Port invalide, utilisation du port par defaut {}.", DEFAULT_PORT);
            }
        }
        if (port < 1 || port > 65535) {
            logger.warn("Port hors limites, utilisation du port par defaut {}.", DEFAULT_PORT);
            port = DEFAULT_PORT;
        }

        return new String[]{pseudo, host, String.valueOf(port)};
    }

    private void runInteractiveGame(String pseudo, Terminal interactiveTerminal) {
        Attributes savedAttributes = interactiveTerminal.enterRawMode();
        try {
            Reader gameReader = interactiveTerminal.reader();
            while (isConnected()) {
                int c = gameReader.read();
                if (c == -1 || c == 3 || c == 4) break;
                char letter = Character.toUpperCase((char) c);
                if (!Character.isLetter(letter)) {
                    statusMessage = "Entrez une lettre valide (A-Z).";
                    redraw();
                    continue;
                }
                try {
                    send(new Message(Message.Type.GUESS, pseudo, String.valueOf(letter)));
                } catch (IOException e) {
                    showFatalError("Erreur d'envoi : " + e.getMessage());
                    disconnect();
                    System.exit(1);
                }
            }
        } catch (IOException e) {
            showFatalError("Erreur de lecture : " + e.getMessage());
        } finally {
            interactiveTerminal.setAttributes(savedAttributes);
        }
    }

    public static void main(String[] args) throws IOException {
        Terminal terminal = buildTerminal();
        boolean interactive = isInteractive(terminal);

        String pseudo;
        String host;
        int port = DEFAULT_PORT;

        if (args.length >= 1) {
            pseudo = args[0];
            host = args.length >= 2 ? args[1] : "localhost";
            if (args.length >= 3) {
                try {
                    port = Integer.parseInt(args[2]);
                } catch (NumberFormatException ignored) {
                }
            }
        } else {
            String[] setup = parseArgsOrPrompt(interactive);
            if (setup == null) return;
            pseudo = setup[0];
            host = setup[1];
            port = Integer.parseInt(setup[2]);
        }

        GameClient client = new GameClient(host, port, interactive ? terminal : null);
        try {
            client.connect();
            client.send(new Message(Message.Type.CONNECT, pseudo, pseudo));
        } catch (IOException e) {
            client.showFatalError("Impossible de se connecter : " + e.getMessage());
            client.disconnect();
            System.exit(1);
        }

        if (interactive) {
            client.runInteractiveGame(pseudo, terminal);
        } else {
            client.runGame(pseudo, new BufferedReader(new InputStreamReader(System.in)));
        }

        client.disconnect();
        logger.info("Deconnecte. A bientot !");
    }
}
