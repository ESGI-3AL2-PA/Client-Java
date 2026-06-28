package com.connectedneighbours;

import com.connectedneighbours.repository.DatabaseManager;

import java.sql.SQLException;

public class Launcher {
    public static void main(String[] args) throws SQLException {
        if (args.length > 0 && args[0].equals("--console")) {
            new ConsoleApp().run();
        } else {
            MainApp.main(args);
        }
        DatabaseManager.close();

    }
}