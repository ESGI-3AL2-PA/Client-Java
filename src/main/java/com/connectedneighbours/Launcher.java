package com.connectedneighbours;

import com.connectedneighbours.repository.DatabaseManager;

import java.io.IOException;
import java.sql.SQLException;

public class Launcher {
    public static void main(String[] args) throws SQLException, IOException {
        MainApp.main(args);
        DatabaseManager.close();
    }
}