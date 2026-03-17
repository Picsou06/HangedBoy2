package org.example.server;

import org.example.common.Message;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class GameServer {

    public static final int DEFAULT_PORT = 25568;
    private static final int MAX_CLIENTS = 10;

    private final int port;
    private ServerSocket serverSocket;
    private final ExecutorService threadPool = Executors.newFixedThreadPool(MAX_CLIENTS);
    private final List<ClientHandler> clients = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger clientCounter = new AtomicInteger(0);
    private volatile boolean running = false;
    private String currentWord;
    private int errorCount = 0;

    public GameServer(int port) {
        this.port = port;
        setNewWord();
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;
        System.out.println("[SERVER] Started on port " + port);

        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                String clientId = "Player-" + clientCounter.incrementAndGet();
                System.out.println("[SERVER] New connection: " + clientId + " (" + clientSocket.getInetAddress() + ")");

                ClientHandler handler = new ClientHandler(clientSocket, this, clientId);
                threadPool.submit(handler);
                handler.send(
            } catch (IOException e) {
                if (running) {
                    System.err.println("[SERVER] Accept error: " + e.getMessage());
                }
            }
        }
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
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {
        }
        System.out.println("[SERVER] Stopped.");
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

    public void addClient(ClientHandler handler) {
        clients.add(handler);
    }

    public void removeClient(ClientHandler handler) {
        clients.remove(handler);
        System.out.println("[SERVER] " + handler.getClientId() + " disconnected. Active clients: " + clients.size());
    }

    public int getClientCount() {
        return clients.size();
    }

    public boolean isRunning() {
        return running;
    }

    public void setNewWord() {
        try (var stream = getClass().getResourceAsStream("/francais.txt")) {
            if (stream == null) throw new IOException("Word list not found");
            try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(stream))) {
                List<String> words = reader.lines().toList();
                if (words.isEmpty()) throw new IOException("Word list is empty");
                currentWord = words.get((int) (Math.random() * words.size()));
                errorCount = 0;
                System.out.println("[SERVER] New word selected: " + currentWord);
            }
        } catch (IOException e) {
            System.err.println("[SERVER] Error reading word list: " + e.getMessage());
            this.stop();
        }
    }

    public static void main(String[] args) throws IOException {
        try {
            String initialWord = new GameServer(DEFAULT_PORT);
            GameServer server = new GameServer(DEFAULT_PORT, initialWord);
            Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
            server.start();
        } catch (IOException e) {
            System.err.println("[SERVER] Failed to start: " + e.getMessage());
        }
    }
}
