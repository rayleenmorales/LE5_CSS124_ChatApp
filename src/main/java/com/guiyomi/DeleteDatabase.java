package com.guiyomi;

import java.io.File;

public class DeleteDatabase {
    public static void main(String[] args) {
        File dbFile = new File("chat.db");
        if (dbFile.delete()) {
            System.out.println("Database deleted successfully.");
        } else {
            System.out.println("Failed to delete the database.");
        }
    }
}
