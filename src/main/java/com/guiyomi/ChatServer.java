package com.guiyomi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatServer {
    private static final int PORT = 12345;
    private static Map<String, PrintWriter> clientWriters = new ConcurrentHashMap<>();
    private static ExecutorService pool = Executors.newFixedThreadPool(10);

    public static void main(String[] args) {
        System.out.println("Chat server started...");
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                pool.execute(new ClientHandler(clientSocket));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class ClientHandler implements Runnable {
        private Socket socket;
        private PrintWriter out;
        private String username;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                //get the username from the client as the first message
                username = in.readLine();
                if (username != null) {
                    clientWriters.put(username, out);
                }

                String message;
                while ((message = in.readLine()) != null) {
                    //parse the message to identify recipient
                    String[] parts = message.split(":", 3);
                    if (parts.length == 3) {
                        String sender = parts[0].trim();
                        String receiver = parts[1].trim();
                        String msgContent = parts[2].trim();

                        //send the message only to the intended recipient
                        PrintWriter recipientWriter = clientWriters.get(receiver);
                        if (recipientWriter != null) {
                            recipientWriter.println(sender + ":" + receiver + ":" + msgContent);
                        }

                        //also send the message to the sender to update their own chat area
                        PrintWriter senderWriter = clientWriters.get(sender);
                        if (senderWriter != null) {
                            senderWriter.println(sender + ":You:" + msgContent);
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (username != null) {
                    clientWriters.remove(username);
                }
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
