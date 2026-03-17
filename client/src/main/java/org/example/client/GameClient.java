package org.example.client;

import org.example.common.Message;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.*;
import java.net.Socket;

public class GameClient {

    private final String host;
    private final int port;
    private Socket socket;
    private ObjectOutputStream out;
    private Thread listenerThread;
    private volatile boolean connected = false;
    private String currentWordState;
    private char[] guessedLetters;


    public GameClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void connect() throws IOException {
        socket = new Socket(host, port);
        out = new ObjectOutputStream(socket.getOutputStream());
        out.flush();
        connected = true;
        System.out.println("[CLIENT] Connected to server " + host + ":" + port);

        listenerThread = new Thread(this::listenForMessages, "client-listener");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    private void listenForMessages() {
        try (ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
            while (connected) {
                Message msg = (Message) in.readObject();
                System.out.println("[CLIENT] Received: " + msg);
                onMessageReceived(msg);
            }
        } catch (EOFException | java.net.SocketException ignored) {
        } catch (IOException | ClassNotFoundException e) {
            if (connected) {
                System.err.println("[CLIENT] Receive error: " + e.getMessage());
            }
        } finally {
            connected = false;
        }
    }

    protected void onMessageReceived(Message msg) {
    }

    // Afficher un message centrée dans la console (au millieu de l'ecran et clear le reste de la page)
    public void ShowMessage(String message) {
        System.out.print("\033[H\033[2J");
        System.out.flush();
        int consoleHeight = 30; // Valeur par défaut
        try (Terminal terminal = TerminalBuilder.builder().dumb(true).build()) {
            int h = terminal.getHeight();
            if (h > 0) consoleHeight = h;
        } catch (IOException ignored) {}
        int padding = (consoleHeight - 1) / 2;
        for (int i = 0; i < padding; i++) {
            System.out.println();
        }
        System.out.println(message);
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
        } catch (IOException ignored) {
        }
        System.out.println("[CLIENT] Disconnected.");
    }

    public boolean isConnected() { return connected; }

    public static void main() throws IOException {
        // Clear la console
        System.out.print("\033[H\033[2J");
        System.out.flush();
        // Demander a l'utilisateur le host et le port en direct
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        System.out.print("Enter server host (default: localhost): ");
        String host = reader.readLine().trim();
        if (host.isEmpty()) host = "localhost";
        System.out.print("Enter server port (default: 25568): ");
        String portStr = reader.readLine().trim();
        int port = 25568;
        if (!portStr.isEmpty()) {
            try {
                port = Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                System.out.println("Invalid port number, using default 25568.");
            }
        }
        if (port < 1 || port > 65535) {
            System.out.println("Port number out of range, using default 25568.");
            port = 25568;
        }
        GameClient client = new GameClient(host, port);
        try {
            client.connect();
        } catch (IOException e) {
            System.err.println("Failed to connect to server: " + e.getMessage());
        }
        if (client.isConnected()) {

        }
    }
}
