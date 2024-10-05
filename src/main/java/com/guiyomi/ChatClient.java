package com.guiyomi;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public class ChatClient extends Application {
    private UserManager userManager = new UserManager();
    private File profilePictureFile;
    private File attachmentFile;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private TextArea chatArea;
    private ListView<String> userList;
    private ImageView profileImageView;
    private Label userNameLabel;
    private String currentUser;
    private String selectedUser;
    private Set<String> existingUsers;  //set to store existing users
    private Map<String, List<String>> conversationMap;  //map to store conversations
    private static final String DB_URL = "jdbc:sqlite:chat.db";

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Chat App - Login");

        TextField firstNameField = new TextField();
        firstNameField.setPromptText("First Name");

        TextField lastNameField = new TextField();
        lastNameField.setPromptText("Last Name");

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password");

        Button chooseImageButton = new Button("Choose Profile Picture");
        chooseImageButton.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            profilePictureFile = fileChooser.showOpenDialog(primaryStage);
        });

        Button signupButton = new Button("Signup");
        signupButton.setOnAction(e -> {
            String firstName = firstNameField.getText();
            String lastName = lastNameField.getText();
            String password = passwordField.getText();

            if (profilePictureFile != null && userManager.registerUser(firstName, lastName, password, profilePictureFile)) {
                showAlert("Signup successful!");
                loadUserList();
            } else {
                showAlert("Failed to register user.");
            }
        });

        Button loginButton = new Button("Login");
        loginButton.setOnAction(e -> {
            String firstName = firstNameField.getText();
            String lastName = lastNameField.getText();
            String password = passwordField.getText();

            if (userManager.loginUser(firstName, lastName, password)) {
                currentUser = firstName + " " + lastName;
                showAlert("Login successful!");
                openChatWindow(primaryStage);
                loadProfilePicture(firstName, lastName);
                updateUserNameLabel(currentUser);
            } else {
                showAlert("Login failed. Check your credentials.");
            }
        });

        VBox layout = new VBox(10, firstNameField, lastNameField, passwordField, chooseImageButton, signupButton, loginButton);
        Scene scene = new Scene(layout, 300, 400);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void preloadAllConversations() {
        String sql = "SELECT sender, receiver, message FROM messages WHERE sender = ? OR receiver = ? ORDER BY id ASC";
    
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
    
            pstmt.setString(1, currentUser);
            pstmt.setString(2, currentUser);
    
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                String sender = rs.getString("sender");
                String receiver = rs.getString("receiver");
                String messageContent = rs.getString("message");
    
                String conversationKey = createConversationKey(sender, receiver);
                String formattedMessage = sender.equals(currentUser) ? "You: " + messageContent : sender + ": " + messageContent;
    
                conversationMap.putIfAbsent(conversationKey, new ArrayList<>());
                conversationMap.get(conversationKey).add(formattedMessage);
            }
        } catch (SQLException e) {
            System.out.println("Error preloading conversations: " + e.getMessage());
        }
    }
    

    private void openChatWindow(Stage primaryStage) {
        primaryStage.setTitle("Chat Room");
    
        chatArea = new TextArea();
        chatArea.setEditable(false);

        profileImageView = new ImageView(); //initialize the ImageView for the profile picture
        profileImageView.setFitWidth(100);
        profileImageView.setFitHeight(100);

        userNameLabel = new Label();
    
        TextField messageField = new TextField();
        messageField.setPromptText("Type your message...");
    
        Button sendButton = new Button("Send");
        sendButton.setOnAction(e -> sendMessage(messageField.getText()));

        Button chooseAttachmentButton = new Button("Choose Attachment");
        chooseAttachmentButton.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            attachmentFile = fileChooser.showOpenDialog(primaryStage);
            if (attachmentFile != null) {
                showAlert("Attachment selected: " + attachmentFile.getName());
            }
        });

        Button logoutButton = new Button("Logout");
        logoutButton.setOnAction(e -> handleLogout(primaryStage));
    
        userList = new ListView<>();
        userList.setPrefWidth(150);
        userList.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            selectedUser = newValue;
            loadChatHistory(currentUser, selectedUser); //load chat history upon selection
        });

        existingUsers = new HashSet<>();
        conversationMap = new HashMap<>();

        loadUserList();

        preloadAllConversations();

        new Thread(this::pollUserList).start();

        VBox profileSection = new VBox(10, profileImageView, logoutButton);
        HBox profileBox = new HBox(10, profileSection, userNameLabel);
        HBox chatLayout = new HBox(10, userList, chatArea);
        HBox buttonBox = new HBox(10, sendButton, chooseAttachmentButton);
        VBox mainLayout = new VBox(10, profileBox, chatLayout, messageField, buttonBox); 
        Scene chatScene = new Scene(mainLayout, 600, 500);
        primaryStage.setScene(chatScene);
        primaryStage.show();

        connectToServer();
        }
    
    //method to load the profile picture
    private void loadProfilePicture(String firstName, String lastName) {
        byte[] imageBytes = userManager.getUserProfilePicture(firstName, lastName);
        if (imageBytes != null) {
            Image image = new Image(new ByteArrayInputStream(imageBytes));
            Platform.runLater(() -> profileImageView.setImage(image)); //set the image in the ImageView
        } else {
            System.out.println("No profile picture found for this user.");
        }
    }

    // Method to update the user's name label
    private void updateUserNameLabel(String userName) {
        Platform.runLater(() -> userNameLabel.setText(userName)); //set the user's name in the label
    }

    private void connectToServer() {
        try {
            socket = new Socket("localhost", 12345);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            //send the username to the server for identification
            out.println(currentUser);

            new Thread(this::receiveMessages).start();
        } catch (IOException e) {
            showAlert("Failed to connect to the server.");
        }
    }

    private void receiveMessages() {
        try {
            String message;
            while ((message = in.readLine()) != null) {
                String finalMessage = message;
    
                //parse the message to extract the sender, receiver, and content
                String[] parts = finalMessage.split(":", 3);
                if (parts.length == 3) {
                    String sender = parts[0].trim();
                    String receiver = parts[1].trim();
                    String msgContent = parts[2].trim();
    
                    //create a unique key for the conversation (order-independent)
                    String conversationKey = createConversationKey(sender, receiver);
    
                    //only store the message if it is relevant to the current user
                    if (receiver.equals(currentUser) || sender.equals(currentUser)) {
                        conversationMap.putIfAbsent(conversationKey, new ArrayList<>());
                        conversationMap.get(conversationKey).add(sender.equals(currentUser) ? "You: " + msgContent : sender + ": " + msgContent);
    
                        //display the message only if the chat area is currently showing this conversation
                        if (selectedUser != null && conversationKey.equals(createConversationKey(currentUser, selectedUser))) {
                            Platform.runLater(() -> chatArea.appendText((sender.equals(currentUser) ? "You" : sender) + ": " + msgContent + "\n"));
                        }
                    }
                }
            }
        } catch (IOException e) {
            showAlert("Connection to server lost.");
        }
    }
    

    private void sendMessage(String message) {
        if (message.isEmpty() || out == null || selectedUser == null) {
            return;
        }
    
        //format message as "sender:receiver:message" for the server to process
        out.println(currentUser + ":" + selectedUser + ":" + message);
    
        //save message to the database
        saveMessageToDatabase(currentUser, selectedUser, message);
    
        //add the message to the conversation map for the sender
        String conversationKey = createConversationKey(currentUser, selectedUser);
        conversationMap.putIfAbsent(conversationKey, new ArrayList<>());
        conversationMap.get(conversationKey).add("You: " + message);
    
        //display the message in the chat area for the sender
        Platform.runLater(() -> chatArea.appendText("You: " + message + "\n"));
    }
    

    private void saveMessageToDatabase(String sender, String receiver, String message) {
        String sql = "INSERT INTO messages(sender, receiver, message) VALUES(?, ?, ?)";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, sender);
            pstmt.setString(2, receiver);
            pstmt.setString(3, message);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    private void loadUserList() {
        List<String> users = new ArrayList<>();
        String sql = "SELECT first_name, last_name FROM users";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String user = rs.getString("first_name") + " " + rs.getString("last_name");
                if (!user.equals(currentUser)) {
                    users.add(user);
                }
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        if (!existingUsers.equals(new HashSet<>(users))) {
            existingUsers.clear();
            existingUsers.addAll(users);
            Platform.runLater(() -> userList.getItems().setAll(users));
        }
    }

    private void pollUserList() {
        while (true) {
            try {
                Thread.sleep(5000); //poll every 5 seconds
                loadUserList();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void loadChatHistory(String sender, String receiver) {
        chatArea.clear();
        String conversationKey = createConversationKey(sender, receiver);
        List<String> messages = new ArrayList<>();
    
        String sql = "SELECT sender, message FROM messages WHERE (sender = ? AND receiver = ?) OR (sender = ? AND receiver = ?) ORDER BY id ASC";
    
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
    
            pstmt.setString(1, sender);
            pstmt.setString(2, receiver);
            pstmt.setString(3, receiver);
            pstmt.setString(4, sender);
    
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                String messageSender = rs.getString("sender");
                String messageContent = rs.getString("message");
                String formattedMessage = (messageSender.equals(sender)) ? "You: " + messageContent : messageSender + ": " + messageContent;
                messages.add(formattedMessage);
            }
        } catch (SQLException e) {
            System.out.println("Error loading chat history: " + e.getMessage());
        }
    
        //add loaded messages to conversationMap and display them in the chat area
        conversationMap.put(conversationKey, messages);
        messages.forEach(msg -> chatArea.appendText(msg + "\n"));
    }

    private void handleLogout(Stage primaryStage) {
        // Perform any necessary cleanup, such as closing the socket connection
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
 
        currentUser = null;
  
        start(primaryStage);
        System.out.println("User logged out successfully.");
    }
    

    //helper method to create a unique key for each conversation
    private String createConversationKey(String user1, String user2) {
        //order-independent key creation
        if (user1.compareTo(user2) < 0) {
            return user1 + ":" + user2;
        } else {
            return user2 + ":" + user1;
        }
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setContentText(message);
        alert.showAndWait();
    }

    @Override
    public void stop() throws Exception {
        if (socket != null) {
            socket.close();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
