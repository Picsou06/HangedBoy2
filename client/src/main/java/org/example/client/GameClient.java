package org.example.client;

import org.example.common.Message;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.*;
import java.net.Socket;

public class GameClient {

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
        try (ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
            while (connected) {
                Message msg = (Message) in.readObject();
                handleMessage(msg);
            }
        } catch (EOFException | java.net.SocketException ignored) {
        } catch (IOException | ClassNotFoundException e) {
            if (connected) {
                fatalError = "Erreur de reception : " + e.getMessage();
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
                if (statusMessage.equals("Connexion au serveur...")) {
                    statusMessage = "Devinez le mot !";
                }
            }
            case GUESS -> {
                errorCount = msg.getErrorCount();
                usedLetters = msg.getUsedLetters();
                statusMessage = "Mauvaise lettre : " + msg.getContent();
            }
            case ERROR -> {
                errorCount = msg.getErrorCount();
                usedLetters = msg.getUsedLetters();
            }
            case WIN  -> statusMessage = "BRAVO ! " + msg.getContent();
            case LOSE -> statusMessage = "PERDU... " + msg.getContent();
            case NEW_GAME -> {
                currentWordDisplay = msg.getContent();
                errorCount = 0;
                usedLetters = "";
                statusMessage = "Nouvelle partie ! Devinez le mot.";
            }
            default -> statusMessage = msg.getContent();
        }
        redraw();
    }

    synchronized void showFatalError(String message) {
        int termWidth = 80;
        int termHeight = 24;
        if (terminal != null) {
            int w = terminal.getWidth();
            int h = terminal.getHeight();
            if (w > 0) termWidth = w;
            if (h > 0) termHeight = h;
        }

        StringBuilder sb = new StringBuilder();

        if (lastLineCount > 0) {
            int linesToGoUp = lastLineCount + (userInputPending ? 1 : 0);
            sb.append("\033[").append(linesToGoUp).append("A\r\033[J");
        }

        int topPad = Math.max(0, (termHeight - 3) / 2);
        for (int i = 0; i < topPad; i++) sb.append("\n");

        sb.append("  ").append(centerPad("[ ERREUR ]", termWidth - 4)).append("\n");
        sb.append("\n");
        sb.append("  ").append(centerPad(message, termWidth - 4)).append("\n");

        System.out.print(sb);
        System.out.flush();
    }

    synchronized void redraw() {
        int termWidth = 80;
        if (terminal != null) {
            int w = terminal.getWidth();
            if (w > 0) termWidth = w;
        }

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
            "",
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

        int newlineCount = 0;
        for (int i = 0; i < display.length(); i++) {
            if (display.charAt(i) == '\n') newlineCount++;
        }

        StringBuilder sb = new StringBuilder();
        if (lastLineCount > 0) {
            int linesToGoUp = lastLineCount + (userInputPending ? 1 : 0);
            sb.append("\033[").append(linesToGoUp).append("A");
            sb.append("\r");
            sb.append("\033[J");
        }
        userInputPending = false;
        lastLineCount = newlineCount;

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
        if (!connected) throw new IOException("Not connected to server");
        out.writeObject(msg);
        out.flush();
    }

    public void disconnect() {
        connected = false;
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
        if (terminal != null) {
            try { terminal.close(); } catch (IOException ignored) {}
        }
    }

    public boolean isConnected() { return connected; }

    public static void main(String[] args) throws IOException {
        Terminal terminal = null;
        try {
            terminal = TerminalBuilder.builder().system(true).build();
        } catch (IOException e) {
            try {
                terminal = TerminalBuilder.builder().dumb(true).build();
            } catch (IOException ignored) {}
        }

        System.out.print("\033[2J\033[H");
        System.out.flush();

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Adresse du serveur (defaut: localhost) : ");
        String host = reader.readLine();
        if (host == null) return;
        host = host.trim();
        if (host.isEmpty()) host = "localhost";

        System.out.print("Port (defaut: 25568) : ");
        String portStr = reader.readLine();
        if (portStr == null) return;
        portStr = portStr.trim();
        int port = 25568;
        if (!portStr.isEmpty()) {
            try {
                port = Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                System.out.println("Port invalide, utilisation du port par defaut 25568.");
            }
        }
        if (port < 1 || port > 65535) {
            System.out.println("Port hors limites, utilisation du port par defaut 25568.");
            port = 25568;
        }

        GameClient client = new GameClient(host, port, terminal);
        try {
            client.connect();
        } catch (IOException e) {
            client.showFatalError("Impossible de se connecter : " + e.getMessage());
            client.disconnect();
            System.exit(1);
        }

        while (client.isConnected()) {
            String line = reader.readLine();
            if (line == null) break;

            line = line.trim().toUpperCase();

            if (line.isEmpty()) {
                client.userInputPending = true;
                client.statusMessage = "Entrez une lettre.";
                client.redraw();
                continue;
            }

            char letter = line.charAt(0);
            if (!Character.isLetter(letter)) {
                client.userInputPending = true;
                client.statusMessage = "Entrez une lettre valide (A-Z).";
                client.redraw();
                continue;
            }

            try {
                client.userInputPending = true;
                client.send(new Message(Message.Type.GUESS, "Client", String.valueOf(letter)));
            } catch (IOException e) {
                client.showFatalError("Erreur d'envoi : " + e.getMessage());
                client.disconnect();
                System.exit(1);
            }
        }

        client.disconnect();
        System.out.println("\nDeconnecte. A bientot !");
    }
}
