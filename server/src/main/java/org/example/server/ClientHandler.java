package org.example.server;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;

import org.example.common.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientHandler implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(ClientHandler.class);

    private final Socket socket;
    private final GameServer server;
    private volatile String clientId;
    private ObjectOutputStream out;

    public ClientHandler(Socket socket, GameServer server, String fallbackId) {
        this.socket = socket;
        this.server = server;
        this.clientId = fallbackId;
    }

    @Override
    public void run() {
        try (ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();

            Message connectMsg = (Message) in.readObject();
            if (connectMsg.getType() == Message.Type.CONNECT) {
                String candidate = connectMsg.getContent().trim();
                if (candidate.matches("[A-Za-z0-9\\-]{1,16}")) {
                    clientId = candidate;
                }
            }
            logger.info("Player identified as: {}", clientId);

            server.addClient(this);

            send(new Message(Message.Type.CONNECT, Message.SERVER_ID, "Bienvenue " + clientId + " !"));
            server.broadcast(new Message(Message.Type.CONNECT, Message.SERVER_ID, clientId + " a rejoint la partie"), this);
            server.sendCurrentStateTo(this);

            while (!socket.isClosed()) {
                Message msg = (Message) in.readObject();
                logger.debug("Received from {}: {}", clientId, msg);

                if (msg.getType() == Message.Type.GUESS) {
                    String content = msg.getContent();
                    if (!content.isEmpty()) {
                        server.guessLetter(content.charAt(0), this);
                    }
                } else {
                    server.broadcast(msg, null);
                }
            }
        } catch (EOFException | SocketException ignored) {
        } catch (IOException | ClassNotFoundException e) {
            logger.error("Error with {}: {}", clientId, e.getMessage());
        } finally {
            server.removeClient(this);
            server.broadcast(new Message(Message.Type.DISCONNECT, Message.SERVER_ID, clientId + " a quitte la partie"), this);
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
            logger.warn("Cannot send to {}: {}", clientId, e.getMessage());
        }
    }

    public void stop() {
        closeSocket();
    }

    private void closeSocket() {
        try {
            if (!socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
    }

    public String getClientId() {
        return clientId;
    }
}
