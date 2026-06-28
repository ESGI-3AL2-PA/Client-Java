package com.connectedneighbours.controller;

import com.connectedneighbours.AppContext;
import com.connectedneighbours.auth.SsoAuthService;
import com.connectedneighbours.auth.exeption.MfaRequiredException;
import com.connectedneighbours.model.User;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;

/**
 * Écran de connexion SSO. Envoie email/password au auth-service ; si MFA requis
 * (HTTP 202), bascule en mode saisie du code TOTP puis appelle /auth/login/mfa.
 * Toute la logique réseau tourne sur un thread d'arrière-plan (Task).
 */
public class LoginController {

    @FXML
    private StackPane root;
    @FXML
    private Label subtitleLabel;
    @FXML
    private TextField emailField;
    @FXML
    private Label passwordLabel;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Label mfaLabel;
    @FXML
    private TextField mfaCodeField;
    @FXML
    private Button loginButton;
    @FXML
    private Label errorLabel;

    private AppContext appContext;
    private Runnable onSuccess;
    private String pendingMfaToken;

    public void setAppContext(AppContext ctx) {
        this.appContext = ctx;
    }

    public void setOnSuccess(Runnable onSuccess) {
        this.onSuccess = onSuccess;
    }

    @FXML
    public void initialize() {
        emailField.setOnAction(e -> passwordField.requestFocus());
        passwordField.setOnAction(e -> onLoginClick());
        mfaCodeField.setOnAction(e -> onLoginClick());
    }

    @FXML
    public void onLoginClick() {
        if (appContext == null) return;
        hideError();
        loginButton.setDisable(true);

        if (pendingMfaToken != null) {
            runMfa();
        } else {
            runLogin();
        }
    }

    private void runLogin() {
        String email = emailField.getText();
        String password = passwordField.getText();

        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            showError("Email et mot de passe requis.");
            loginButton.setDisable(false);
            return;
        }

        SsoAuthService auth = appContext.getAuthService();
        Task<User> task = new Task<>() {
            @Override
            protected User call() throws Exception {
                return auth.login(email, password);
            }
        };
        task.setOnSucceeded(e -> onLoginOk(task.getValue()));
        task.setOnFailed(e -> {
            Throwable t = task.getException();
            if (t instanceof MfaRequiredException mfa) {
                enterMfaMode(mfa.getMfaToken());
            } else {
                showError("Connexion échouée : " + t.getMessage());
                loginButton.setDisable(false);
            }
        });
        run(task);
    }

    private void runMfa() {
        String code = mfaCodeField.getText();
        if (code == null || !code.matches("\\d{6}")) {
            showError("Le code TOTP doit comporter 6 chiffres.");
            loginButton.setDisable(false);
            return;
        }
        SsoAuthService auth = appContext.getAuthService();
        Task<User> task = new Task<>() {
            @Override
            protected User call() throws Exception {
                return auth.completeMfa(pendingMfaToken, code);
            }
        };
        task.setOnSucceeded(e -> onLoginOk(task.getValue()));
        task.setOnFailed(e -> {
            showError("Code TOTP invalide : " + task.getException().getMessage());
            loginButton.setDisable(false);
        });
        run(task);
    }

    private void enterMfaMode(String mfaToken) {
        this.pendingMfaToken = mfaToken;
        Platform.runLater(() -> {
            subtitleLabel.setText("Authentification à deux facteurs");
            passwordLabel.setManaged(false);
            passwordLabel.setVisible(false);
            passwordField.setManaged(false);
            passwordField.setVisible(false);
            mfaLabel.setManaged(true);
            mfaLabel.setVisible(true);
            mfaCodeField.setManaged(true);
            mfaCodeField.setVisible(true);
            mfaCodeField.clear();
            mfaCodeField.requestFocus();
            loginButton.setText("Valider le code");
            loginButton.setDisable(false);
        });
    }

    private void onLoginOk(User user) {
        appContext.setCurrentUser(user);
        Platform.runLater(() -> {
            loginButton.setDisable(false);
            if (onSuccess != null) {
                onSuccess.run();
            }
        });
    }

    private void showError(String msg) {
        Platform.runLater(() -> {
            errorLabel.setText(msg);
            errorLabel.setManaged(true);
            errorLabel.setVisible(true);
        });
    }

    private void hideError() {
        errorLabel.setText("");
        errorLabel.setManaged(false);
        errorLabel.setVisible(false);
    }

    private void run(Task<?> task) {
        Thread t = new Thread(task, "sso-login");
        t.setDaemon(true);
        t.start();
    }
}
