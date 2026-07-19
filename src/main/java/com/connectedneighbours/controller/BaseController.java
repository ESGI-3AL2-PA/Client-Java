package com.connectedneighbours.controller;

import com.connectedneighbours.AppContext;
import com.connectedneighbours.MainApp;
import com.connectedneighbours.service.ConflictService;
import com.connectedneighbours.i18n.I18nManager;
import com.connectedneighbours.service.SyncService;
import com.connectedneighbours.service.SyncStatus;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
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
    /**
     * Badge des conflits. Masqué tant que le push n'en a levé aucun.
     */
    @FXML
    protected Button conflictsButton;

    // Dépendances
    protected AppContext appContext;
    protected SyncService syncService;

    protected boolean reloginRequested = false;

    /**
     * Seule référence forte au listener de quartier : l'abonnement est faible, donc
     * il vit exactement aussi longtemps que ce contrôleur — ni plus, ni moins.
     */
    private ChangeListener<String> districtListener;

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
        if (conflictsButton != null) {
            conflictsButton.setVisible(false);
            conflictsButton.setManaged(false);
        }
        if (syncService != null) {
            syncService.setStatusListener(this::updateSyncUI);
            syncService.setConflictListener(this::showConflictBadge);
            syncService.setRejectionListener(this::showRejections);
            restoreSyncUI();
        }
    }

    /**
     * Réaffiche l'état de synchronisation déjà connu du service. Une navigation
     * reconstruit la scène entière : sans cette restauration, l'écran repart du
     * défaut FXML et annonce « Hors-ligne » jusqu'au prochain tick, badge de
     * conflits et date de dernière sync perdus au passage.
     */
    private void restoreSyncUI() {
        SyncStatus known = syncService.getLastStatus();
        if (known != null) {
            renderSyncStatus(known);
        }
        if (lastSyncLabel != null && syncService.getLastSyncAt() != null) {
            lastSyncLabel.setText(
                    I18nManager.tr("common.sync.lastSync", syncService.getLastSyncAt().format(DATE_FMT)));
        }
        showConflictBadge(syncService.getPendingConflictCount());
    }

    /**
     * Lève le badge quand un push a été mis en quarantaine. Il reste affiché
     * tant que l'opérateur n'a pas traité les conflits : c'est le seul endroit
     * où ils peuvent l'être.
     */
    protected void showConflictBadge(int count) {
        if (conflictsButton == null || count <= 0) {
            return;
        }
        conflictsButton.setVisible(true);
        conflictsButton.setManaged(true);
        conflictsButton.setText("Conflits (" + count + ")");
    }

    /**
     * Les refus d'autorisation n'ouvrent aucun écran : il n'y a rien à
     * arbitrer, la modification a été abandonnée et l'opérateur doit le savoir.
     */
    protected void showRejections(java.util.List<String> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Modifications refusées");
        alert.setHeaderText("Ces modifications n'ont pas pu être synchronisées et ont été abandonnées.");
        alert.setContentText(String.join("\n", messages));
        alert.show();
    }

    /**
     * Ouvre l'écran de résolution des conflits.
     * Méthode {@code @FXML} référencée par {@code onAction="#onConflictsClick"}.
     */
    @FXML
    public void onConflictsClick() {
        if (appContext == null) {
            return;
        }
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    getClass().getResource("/com/connectedneighbours/fxml/conflicts.fxml")
            );
            ConflictService conflictService = new ConflictService(appContext.getSyncApiClient());
            loader.setControllerFactory(cls -> {
                if (cls == ConflictController.class) {
                    return new ConflictController(conflictService);
                }
                try {
                    return cls.getDeclaredConstructors()[0].newInstance();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            javafx.scene.Parent root = loader.load();

            Stage stage = new Stage();
            stage.setTitle("Conflits — Connected Neighbours");
            stage.initOwner(conflictsButton.getScene().getWindow());
            stage.initModality(javafx.stage.Modality.WINDOW_MODAL);

            javafx.scene.Scene scene = new javafx.scene.Scene(root, 1000, 700);
            com.connectedneighbours.theme.ThemeManager.applyTheme(scene);

            stage.setScene(scene);
            stage.showAndWait();
        } catch (Exception e) {
            showError("Impossible d'ouvrir l'écran des conflits : " + e.getMessage());
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
        renderSyncStatus(status);
        switch (status) {
            case SUCCESS -> {
                if (lastSyncLabel != null) {
                    lastSyncLabel.setText(
                            I18nManager.tr("common.sync.lastSync", LocalDateTime.now().format(DATE_FMT)));
                }
                onSyncSuccess();
            }
            case AUTH_REQUIRED -> triggerRelogin();
            default -> {
            }
        }
    }

    /**
     * Peint la barre de synchronisation, sans effet de bord. Séparé de
     * {@link #updateSyncUI(SyncStatus)} pour que la restauration après une
     * navigation puisse réafficher l'état sans relancer un {@code loadData()}
     * ni un re-login.
     * <p>
     * Les champs sont testés : tous les écrans n'embarquent pas la barre de sync.
     */
    private void renderSyncStatus(SyncStatus status) {
        String text;
        Color color;
        boolean syncDisabled;
        switch (status) {
            case OFFLINE -> {
                text = I18nManager.tr("common.sync.offline");
                color = Color.GRAY;
                syncDisabled = false;
            }
            case SYNCING -> {
                text = I18nManager.tr("common.sync.syncing");
                color = Color.ORANGE;
                syncDisabled = true;
            }
            case SUCCESS -> {
                text = I18nManager.tr("common.sync.success");
                color = Color.GREEN;
                syncDisabled = false;
            }
            case ERROR -> {
                text = I18nManager.tr("common.sync.error");
                color = Color.RED;
                syncDisabled = false;
            }
            case AUTH_REQUIRED -> {
                text = I18nManager.tr("common.sync.authRequired");
                color = Color.ORANGE;
                syncDisabled = true;
            }
            default -> {
                return;
            }
        }
        if (syncStatusLabel != null) {
            syncStatusLabel.setText(text);
        }
        if (syncStatusDot != null) {
            syncStatusDot.setFill(color);
        }
        if (syncNowButton != null) {
            syncNowButton.setDisable(syncDisabled);
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
     * S'abonne aux changements de quartier. À appeler dans {@code initialize()}
     * des écrans dont les données sont scopées.
     * <p>
     * Listener faible : l'{@link AppContext} survit aux navigations, alors que les
     * contrôleurs sont jetés à chaque changement d'écran. Une référence forte les
     * maintiendrait tous en vie et rejouerait {@code onDistrictChanged()} sur des
     * écrans qui ne sont plus affichés.
     */
    protected void setupDistrictScope() {
        if (appContext == null) return;
        districtListener = (obs, old, current) -> onDistrictChanged();
        appContext.activeDistrictIdProperty().addListener(new WeakChangeListener<>(districtListener));
    }

    /**
     * Hook appelé quand le quartier consulté change. Implémentation par défaut :
     * ne rien faire.
     */
    protected void onDistrictChanged() {
        // no-op par défaut
    }

    /**
     * Déclenche le re-login navigateur via {@link MainApp#backToLogin()}.
     * Protégé par {@link #reloginRequested} pour éviter les appels multiples.
     */
    protected void triggerRelogin() {
        if (reloginRequested) return;
        if (syncNowButton == null || syncNowButton.getScene() == null) return;
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
