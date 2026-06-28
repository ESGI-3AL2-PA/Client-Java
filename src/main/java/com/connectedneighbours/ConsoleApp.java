package com.connectedneighbours;

import com.connectedneighbours.repository.ApiClient;
import com.connectedneighbours.service.SyncService;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Scanner;

public class ConsoleApp {
    private final Scanner scanner = new Scanner(System.in);

    public void run() throws SQLException, IOException {
        System.out.println("=== Connected Neighbours — Admin ===");
        boolean running = true;


        while (running) {

            System.out.println("\n1. Voir les incidents");
            System.out.println("2. Voir les statistiques");
            System.out.println("3. Synchroniser");
            System.out.println("4. Pull");
            System.out.println("0. Quitter");
            System.out.print("Choix : ");
            switch (scanner.nextLine().trim()) {
                case "1":
                    System.out.println("[TODO] Liste des incidents...");
                    break;
                case "2":
                    System.out.println("[TODO] Statistiques...");
                    break;
                case "3":
                    System.out.println("[TODO] Synchronisation...");
                    break;
                case "4":
                    ApiClient apiClient = new ApiClient();
                    SyncService syncService = new SyncService(apiClient);
                    syncService.pullRemoteUsers();
                    syncService.pullRemoteIncidents();
                    break;
                case "5":
                case "0":
                    running = false;
                    break;
                default:
                    System.out.println("Choix invalide.");
                    break;
            }
        }
        System.out.println("Au revoir !");
    }
}