package com.registry.storesapp;

import java.sql.Connection;

public class  TestConnection {
    public static void main(String[] args) {
        try (Connection conn = DBConnection.getConnection()) {
            System.out.println("Connected to StoresDB successfully!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
