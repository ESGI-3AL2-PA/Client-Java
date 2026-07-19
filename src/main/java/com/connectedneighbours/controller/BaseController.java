package com.connectedneighbours.controller;

import com.connectedneighbours.AppContext;
import com.connectedneighbours.MainApp;
import com.connectedneighbours.i18n.I18nManager;
import com.connectedneighbours.service.SyncService;
import com.connectedneighbours.service.SyncStatus;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Classe parente des contrôleurs JavaFX.
 */
public class BaseController {


    protected static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    // Include du Header
    @FXML
    protected HeaderController headerController;

    // Barre de synchronisation
    @FXML
    protected Circle syncStatusDot;
    @FXML
    protected Label syncStatusLabel;
    @FXML
    protected Label lastSyncLabel;
    @FXML
    protected Button syncNowButton;

    // Dépendances
    protected AppContext appContext;
    protected SyncService syncService;

    protected boolean reloginRequested = false;

    public BaseController() {
    }

    public BaseController(AppContext appContext, SyncService syncService) {
        this.appContext = appContext;
        this.syncService = syncService;
    }


    public AppContext getAppContext() {
        return appContext;
    }

    public void setAppContext(AppContext appContext) {
        this.appContext = appContext;
    }

    public SyncService getSyncService() {
        return syncService;
    }

    public void setSyncService(SyncService syncService) {
        this.syncService = syncService;
    }

    public HeaderController getHeaderController() {
        return headerController;
    }

    //  Initialisation — à appeler depuis initialize() du sous-contrôleur 

    /**
     * Enregistre le listener de synchronisation auprès du {@link SyncService}.
     * À appeler dans {@code initialize()} du sous-contrôleur, une fois les
     * dépendances injectées.
     */
    protected void setupSync() {
        if (syncService != null) {
            syncService.setStatusListener(this::updateSyncUI);
        }
    }

    /**
     * Signale la page active dans le header de navigation.
     * À appeler dans {@code initialize()} du sous-contrôleur.
     *
     * @param page la page courante
     */
    protected void setupHeader(Page page) {
        if (headerController != null) {
            headerController.setActivePage(page);
        }
    }

    //  Synchronisation 

    /**
     * Déclenche une synchronisation manuelle.
     * Méthode {@code @FXML} référencée par {@code onAction="#onSyncNowClick"}
     * dans les FXML des écrans.
     */
    @FXML
    public void onSyncNowClick() {
        if (syncService != null) {
            syncNowButton.setDisable(true);
            syncService.syncNow();
        }
    }

    /**
     * Met à jour la barre de synchronisation en fonction du statut reçu.
     * <p>
     * Méthode {@code final} : les sous-contrôleurs personnalisent le comportement
     * via {@link #onSyncSuccess()} (appelé sur {@link SyncStatus#SUCCESS}).
     *
     * @param status le statut de synchronisation courant
     */
    protected final void updateSyncUI(SyncStatus status) {
        switch (status) {
            case OFFLINE -> {
                syncStatusLabel.setText(I18nManager.tr("common.sync.offline"));
                syncStatusDot.setFill(Color.GRAY);
                syncNowButton.setDisable(false);
            }
            case SYNCING -> {
                syncStatusLabel.setText(I18nManager.tr("common.sync.syncing"));
                syncStatusDot.setFill(Color.ORANGE);
                syncNowButton.setDisable(true);
            }
            case SUCCESS -> {
                syncStatusLabel.setText(I18nManager.tr("common.sync.success"));
                syncStatusDot.setFill(Color.GREEN);
                syncNowButton.setDisable(false);
                lastSyncLabel.setText(I18nManager.tr("common.sync.lastSync", LocalDateTime.now().format(DATE_FMT)));
                onSyncSuccess();
            }
            case ERROR -> {
                syncStatusLabel.setText(I18nManager.tr("common.sync.error"));
                syncStatusDot.setFill(Color.RED);
                syncNowButton.setDisable(false);
            }
            case AUTH_REQUIRED -> {
                syncStatusLabel.setText(I18nManager.tr("common.sync.authRequired"));
                syncStatusDot.setFill(Color.ORANGE);
                syncNowButton.setDisable(true);
                triggerRelogin();
            }
        }
    }

    /**
     * Hook appelé après une synchronisation réussie. Implémentation par défaut :
     * ne rien faire. Les sous-contrôleurs peuvent surcharger pour recharger
     * leurs données (ex. {@code loadData()}).
     */
    protected void onSyncSuccess() {
        // no-op par défaut
    }

    /**
     * Déclenche le re-login navigateur via {@link MainApp#backToLogin()}.
     * Protégé par {@link #reloginRequested} pour éviter les appels multiples.
     */
    protected void triggerRelogin() {
        if (reloginRequested) return;
        reloginRequested = true;
        Stage stage = (Stage) syncNowButton.getScene().getWindow();
        Object mainApp = stage.getUserData();
        if (mainApp instanceof MainApp app) {
            app.backToLogin();
        }
    }

    //  Helpers UI 

    /**
     * Affiche une boîte de dialogue d'erreur.
     *
     * @param msg le message d'erreur à afficher
     */
    protected void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(I18nManager.tr("common.error.title"));
        alert.setContentText(msg);
        alert.showAndWait();
    }
}
