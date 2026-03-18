package org.example.server;

import org.example.common.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class GameServer {

    private static final Logger logger = LoggerFactory.getLogger(GameServer.class);

    public static final int DEFAULT_PORT = 25568;
    private static final int MAX_CLIENTS = 10;

    private static final Random RANDOM = new Random();

    private final int port;
    private final List<String> wordList;
    private ServerSocket serverSocket;
    private final ExecutorService threadPool = Executors.newFixedThreadPool(MAX_CLIENTS);
    private final List<ClientHandler> clients = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger clientCounter = new AtomicInteger(0);
    private volatile boolean running = false;
    private String currentWord;
    private String currentWordDisplay;
    private final Set<Character> guessedLetters = new LinkedHashSet<>();
    private int errorCount = 0;

    public GameServer(int port) {
        this.port = port;
        this.wordList = loadWordList();
        setNewWord();
    }

    private List<String> loadWordList() {
        var stream = getClass().getResourceAsStream("/francais.txt");
        if (stream == null) {
            logger.error("Word list not found");
            System.exit(1);
            return List.of();
        }

        try (var reader = new BufferedReader(new InputStreamReader(stream))) {
            List<String> words = reader.lines()
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .toList();
            if (words.isEmpty()) {
                logger.error("Word list is empty");
                System.exit(1);
            }
            return words;
        } catch (IOException e) {
            logger.error("Error reading word list: {}", e.getMessage());
            System.exit(1);
            return List.of();
        }
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;
        logger.info("Started on port {}", port);
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                String fallbackId = "Player-" + clientCounter.incrementAndGet();
                logger.info("New connection from {}", clientSocket.getInetAddress());
                ClientHandler handler = new ClientHandler(clientSocket, this, fallbackId);
                threadPool.submit(handler);
            } catch (IOException e) {
                if (running) {
                    logger.error("Accept error: {}", e.getMessage());
                }
            }
        }
    }

    public void guessLetter(char letter, ClientHandler sender) {
        letter = Character.toUpperCase(letter);

        final boolean correct;
        final boolean gameOver;
        final String usedLettersStr;
        final String wordDisplay;
        final int capturedErrorCount;
        final String capturedWord;

        synchronized (this) {
            if (guessedLetters.contains(letter)) {
                sender.send(new Message(
                        Message.Type.ERROR,
                        Message.SERVER_ID,
                        "Lettre deja utilisee: " + letter,
                        errorCount,
                        getUsedLettersString()
                ));
                return;
            }

            guessedLetters.add(letter);
            correct = currentWord.contains(String.valueOf(letter));

            if (correct) {
                currentWordDisplay = buildWordDisplay();
            } else {
                errorCount++;
            }

            capturedErrorCount = errorCount;
            wordDisplay = currentWordDisplay;
            usedLettersStr = getUsedLettersString();
            capturedWord = currentWord;
            gameOver = correct
                    ? !wordDisplay.contains("_")
                    : capturedErrorCount >= Message.MAX_ERRORS;
        }

        if (correct) {
            broadcast(new Message(Message.Type.CURRENT_WORD, sender.getClientId(), wordDisplay, capturedErrorCount, usedLettersStr), null);
        } else {
            broadcast(new Message(Message.Type.GUESS, sender.getClientId(), String.valueOf(letter), capturedErrorCount, usedLettersStr), null);
        }

        if (gameOver) {
            Message endMsg = correct
                    ? new Message(Message.Type.WIN, sender.getClientId(), "Le mot etait: " + capturedWord)
                    : new Message(Message.Type.LOSE, Message.SERVER_ID, "Perdu! Le mot etait: " + capturedWord);
            broadcast(endMsg, null);

            try {
                Thread.sleep(3000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }

            final String newWordDisplay;
            synchronized (this) {
                setNewWord();
                newWordDisplay = currentWordDisplay;
            }
            broadcast(new Message(Message.Type.NEW_GAME, Message.SERVER_ID, newWordDisplay, 0, ""), null);
        }
    }

    private String buildWordDisplay() {
        StringBuilder display = new StringBuilder();
        for (char c : currentWord.toCharArray()) {
            display.append(guessedLetters.contains(c) ? c + " " : "_ ");
        }
        return display.toString().trim();
    }

    private String getUsedLettersString() {
        return guessedLetters.stream().map(String::valueOf).collect(Collectors.joining(", "));
    }

    public void stop() {
        running = false;
        threadPool.shutdownNow();
        synchronized (clients) {
            for (ClientHandler client : clients) {
                client.stop();
            }
            clients.clear();
        }
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
        }
        logger.info("Stopped.");
    }

    public void broadcast(Message msg, ClientHandler exclude) {
        synchronized (clients) {
            for (ClientHandler client : clients) {
                if (client != exclude) {
                    client.send(msg);
                }
            }
        }
    }

    public synchronized void sendCurrentStateTo(ClientHandler handler) {
        handler.send(new Message(
                Message.Type.CURRENT_WORD,
                Message.SERVER_ID,
                currentWordDisplay,
                errorCount,
                getUsedLettersString()
        ));
    }

    public void addClient(ClientHandler handler) {
        clients.add(handler);
    }

    public void removeClient(ClientHandler handler) {
        clients.remove(handler);
        logger.info("{} disconnected. Active clients: {}", handler.getClientId(), clients.size());
    }

    public int getClientCount() {
        return clients.size();
    }

    public boolean isRunning() {
        return running;
    }

    private void setNewWord() {
        currentWord = wordList.get(RANDOM.nextInt(wordList.size())).toUpperCase();
        errorCount = 0;
        guessedLetters.clear();
        currentWordDisplay = buildWordDisplay();
        logger.info("New word selected: {}", currentWord);
    }

    public static void main(String[] args) {
        try {
            GameServer server = new GameServer(DEFAULT_PORT);
            Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
            server.start();
        } catch (IOException e) {
            logger.error("Failed to start: {}", e.getMessage());
        }
    }
}
