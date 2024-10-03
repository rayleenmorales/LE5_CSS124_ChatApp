package com.guiyomi;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;

public class UserManager {
    private static final String DB_URL = "jdbc:sqlite:chat.db";

    //method to register a user
    public boolean registerUser(String firstName, String lastName, String password, File profilePicture) {
    String sql = "INSERT INTO users (first_name, last_name, password, profile_picture) VALUES (?, ?, ?, ?)";
    String hashedPassword = hashPassword(password);

    try (Connection conn = DriverManager.getConnection(DB_URL);
         PreparedStatement pstmt = conn.prepareStatement(sql)) {
        
        try (FileInputStream fis = new FileInputStream(profilePicture)) {
            pstmt.setString(1, firstName);
            pstmt.setString(2, lastName);
            pstmt.setString(3, hashedPassword);
            pstmt.setBinaryStream(4, fis, (int) profilePicture.length());
            pstmt.executeUpdate();
            System.out.println("User registered: " + firstName + " " + lastName);
            return true;
        } catch (IOException e) {
            System.out.println("Error during user registration: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    } catch (SQLException e) {
        System.out.println("Error during user registration: " + e.getMessage());
        e.printStackTrace();
        return false;
    }
}


    //method to login a user
    public boolean loginUser(String firstName, String lastName, String password) {
        String sql = "SELECT password FROM users WHERE first_name = ? AND last_name = ?";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, firstName);
            pstmt.setString(2, lastName);

            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String storedHashedPassword = rs.getString("password");
                return verifyPassword(password, storedHashedPassword);
            } else {
                System.out.println("Login failed: No user found with the provided credentials.");
                return false;
            }
        } catch (SQLException e) {
            System.out.println("SQL Exception during login: " + e.getMessage());
            return false;
        }
    }

    //method to hash the password using SHA-256
    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash); // Encode as Base64 for storage
        } catch (NoSuchAlgorithmException e) {
            System.out.println("Error hashing password: " + e.getMessage());
            return null;
        }
    }

    //method to verify the password by comparing with the hashed password
    private boolean verifyPassword(String password, String hashedPassword) {
        String hashedInputPassword = hashPassword(password);
        return hashedInputPassword != null && hashedInputPassword.equals(hashedPassword);
    }

    public byte[] getUserProfilePicture(String firstName, String lastName) {
        String sql = "SELECT profile_picture FROM users WHERE first_name = ? AND last_name = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, firstName);
            pstmt.setString(2, lastName);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getBytes("profile_picture");
            }
        } catch (SQLException e) {
            System.out.println("Error loading profile picture: " + e.getMessage());
        }
        return null;
    }


}
