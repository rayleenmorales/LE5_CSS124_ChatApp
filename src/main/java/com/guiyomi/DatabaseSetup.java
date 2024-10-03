package com.guiyomi;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseSetup {
    public static void main(String[] args) {
        String url = "jdbc:sqlite:chat.db";

        String createUsersTable = "CREATE TABLE IF NOT EXISTS users (\n"
                + "    id INTEGER PRIMARY KEY AUTOINCREMENT,\n"
                + "    first_name TEXT NOT NULL,\n"
                + "    last_name TEXT NOT NULL,\n"
                + "    password TEXT NOT NULL,\n"
                + "    profile_picture BLOB,\n"
                + "    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP\n"
                + ");";

        String createMessagesTable = "CREATE TABLE IF NOT EXISTS messages (\n"
                + "    id INTEGER PRIMARY KEY AUTOINCREMENT,\n"
                + "    sender TEXT NOT NULL,\n"
                + "    receiver TEXT NOT NULL,\n"
                + "    message TEXT,\n"
                + "    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP\n"
                + ");";

        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {
            stmt.execute(createUsersTable);
            stmt.execute(createMessagesTable);
            System.out.println("Database and tables created successfully.");
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }
}

