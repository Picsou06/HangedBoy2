package org.example.server;

import org.example.common.Message;
import java.io.*;
import java.net.Socket;

public class ClientHandler implements Runnable {

    private final Socket socket;
    private final GameServer server;
    private final String clientId;
    private ObjectOutputStream out;

    public ClientHandler(Socket socket, GameServer server, String clientId) {
        this.socket = socket;
        this.server = server;
        this.clientId = clientId;
    }

    @Override
    public void run() {
        try (ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            server.addClient(this);

            send(new Message(Message.Type.CONNECT, "SERVER", "Bienvenue " + clientId + " !"));
            server.broadcast(new Message(Message.Type.CONNECT, "SERVER", clientId + " a rejoint la partie"), this);
            server.sendCurrentStateTo(this);

            while (true) {
                Message msg = (Message) in.readObject();
                System.out.println("[SERVER] Received from " + clientId + ": " + msg);

                if (msg.getType() == Message.Type.GUESS) {
                    String content = msg.getContent();
                    if (!content.isEmpty()) {
                        server.guessLetter(content.charAt(0), this);
                    }
                } else {
                    server.broadcast(msg, null);
                }
            }
        } catch (EOFException | java.net.SocketException ignored) {
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("[SERVER] Error with " + clientId + ": " + e.getMessage());
        } finally {
            server.removeClient(this);
            server.broadcast(new Message(Message.Type.DISCONNECT, "SERVER", clientId + " a quitte la partie"), this);
            closeSocket();
        }
    }

    public void send(Message msg) {
        try {
            if (out != null) {
                out.writeObject(msg);
                out.flush();
            }
        } catch (IOException e) {
            System.err.println("[SERVER] Cannot send to " + clientId + ": " + e.getMessage());
        }
    }

    public void stop() {
        closeSocket();
    }

    private void closeSocket() {
        try {
            if (!socket.isClosed()) socket.close();
        } catch (IOException ignored) {
        }
    }

    public String getClientId() { return clientId; }
}
