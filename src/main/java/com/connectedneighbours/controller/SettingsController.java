package com.connectedneighbours.controller;

import com.connectedneighbours.MainApp;
import com.connectedneighbours.config.ApiConfig;
import com.connectedneighbours.i18n.I18nManager;
import com.connectedneighbours.i18n.Language;
import com.connectedneighbours.theme.Theme;
import com.connectedneighbours.theme.ThemeManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.Socket;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class SettingsController {

	@FXML
	private ChoiceBox<String> schemeChoice;
	@FXML
	private TextField hostField;
	@FXML
	private TextField portField;
	@FXML
	private Label baseUrlLabel;
	@FXML
	private Label testResultLabel;
	@FXML
	private ComboBox<Theme> themeCombo;
	@FXML
	private Label themeHintLabel;
	@FXML
	private ComboBox<Language> languageCombo;
	@FXML
	private Label languageHintLabel;

	private volatile Task<Boolean> connectionTestTask;

	@FXML
	public void initialize() {
		schemeChoice.setItems(FXCollections.observableArrayList("http", "https"));
		schemeChoice.setValue(normalizeScheme(ApiConfig.getScheme()));

		hostField.setText(Objects.toString(ApiConfig.getHost(), ""));
		portField.setText(ApiConfig.getPortText());

		// Restreint le port aux chiffres.
		portField.setTextFormatter(new TextFormatter<>(change -> {
			String newText = change.getControlNewText();
			if (newText.isBlank() || newText.matches("\\d{0,5}")) {
				return change;
			}
			return null;
		}));

		schemeChoice.valueProperty().addListener((obs, o, n) -> {
			clearTestResult();
			refreshBaseUrlPreview();
		});
		hostField.textProperty().addListener((obs, o, n) -> {
			clearTestResult();
			refreshBaseUrlPreview();
		});
		portField.textProperty().addListener((obs, o, n) -> {
			clearTestResult();
			refreshBaseUrlPreview();
		});

		refreshBaseUrlPreview();
		initThemeCombo();
		initLanguageCombo();
	}

	private void initThemeCombo() {
		themeCombo.setCellFactory(lv -> cell(Theme::getDisplayName));
		themeCombo.setButtonCell(cell(Theme::getDisplayName));
		themeCombo.setItems(FXCollections.observableArrayList(ThemeManager.getAvailableThemes()));
		themeCombo.setValue(ThemeManager.getCurrent());
		// Pas d'application automatique sur sélection : l'utilisateur doit
		// cliquer sur « Recharger » pour persiste + appliquer le thème.
	}

	private void initLanguageCombo() {
		languageCombo.setCellFactory(lv -> cell(Language::getDisplayName));
		languageCombo.setButtonCell(cell(Language::getDisplayName));
		languageCombo.setItems(FXCollections.observableArrayList(I18nManager.getAvailableLanguages()));
		languageCombo.setValue(I18nManager.getCurrent());
		// Pas d'application automatique sur sélection : l'utilisateur doit
		// cliquer sur « Appliquer » pour persister + recharger les écrans.
	}

	private static <T> ListCell<T> cell(java.util.function.Function<T, String> text) {
		return new ListCell<>() {
			@Override
			protected void updateItem(T item, boolean empty) {
				super.updateItem(item, empty);
				setText(empty || item == null ? null : text.apply(item));
			}
		};
	}

	@FXML
	public void onReloadThemesClick() {
		// Rescan du dossier ./themes/ (récupère les nouveaux fichiers .css).
		ThemeManager.reloadCustomThemes();
		List<Theme> themes = ThemeManager.getAvailableThemes();
		Theme selected = themeCombo.getValue();
		themeCombo.setItems(FXCollections.observableArrayList(themes));
		// Re-sélectionne le thème choisi s'il est toujours disponible,
		// sinon retombe sur le thème courant persisté.
		Theme toApply = themes.contains(selected) ? selected : ThemeManager.getCurrent();
		themeCombo.setValue(toApply);
		// Persiste + applique le thème sélectionné.
		ThemeManager.setCurrent(toApply);
		// Scène de la fenêtre Paramètres (pop-up).
		if (hostField.getScene() != null) {
			ThemeManager.applyTheme(hostField.getScene());
		}
		// Scène de la fenêtre principale (propriétaire du pop-up) pour que
		// le thème s'applique immédiatement au dashboard/incidents derrière.
		applyThemeToOwner();
	}

	private void applyThemeToOwner() {
		if (hostField.getScene() == null) return;
		javafx.stage.Window settingsWin = hostField.getScene().getWindow();
		if (settingsWin instanceof javafx.stage.Stage settingsStage) {
			javafx.stage.Window owner = settingsStage.getOwner();
			if (owner != null && owner.getScene() != null) {
				ThemeManager.applyTheme(owner.getScene());
			}
		}
	}

	/**
	 * Change de langue "à chaud" : persiste le choix, recharge l'écran
	 * propriétaire (dashboard/incidents, via {@link MainApp#reloadCurrentScreen()})
	 * puis reconstruit cette fenêtre Paramètres elle-même sur son {@link Stage}
	 * existant (position/modalité/owner préservés).
	 */
	@FXML
	public void onApplyLanguageClick() {
		Language selected = languageCombo.getValue();
		if (selected == null || hostField.getScene() == null) return;

		// Capturés avant toute mutation : les champs @FXML de cette instance
		// ne doivent plus être ré-interrogés après le remplacement de la scène.
		Stage stage = (Stage) hostField.getScene().getWindow();
		Window owner = stage.getOwner();

		I18nManager.setCurrent(selected);

		reloadOwnerScreen(owner);
		reloadSettingsWindow(stage);
	}

	private void reloadOwnerScreen(Window owner) {
		if (owner instanceof Stage ownerStage && ownerStage.getUserData() instanceof MainApp app) {
			app.reloadCurrentScreen();
		}
	}

	private void reloadSettingsWindow(Stage stage) {
		try {
			FXMLLoader loader = new FXMLLoader(
					getClass().getResource("/com/connectedneighbours/fxml/settings.fxml")
			);
			loader.setResources(I18nManager.getBundle());
			Parent root = loader.load();

			double width = stage.getScene() != null ? stage.getScene().getWidth() : 720;
			double height = stage.getScene() != null ? stage.getScene().getHeight() : 520;
			Scene newScene = new Scene(root, width, height);
			ThemeManager.applyTheme(newScene);
			stage.setScene(newScene);
		} catch (IOException e) {
			Alert alert = new Alert(Alert.AlertType.ERROR);
			alert.setTitle(I18nManager.tr("common.error.title"));
			alert.setContentText(I18nManager.tr("settings.reload.error", e.getMessage()));
			alert.showAndWait();
		}
	}

	@FXML
	public void onSaveClick() {
		ApiSettings settings;
		try {
			settings = readSettingsStrict();
		} catch (IllegalArgumentException e) {
			showError(e.getMessage());
			return;
		}

		ApiConfig.setScheme(settings.scheme());
		ApiConfig.setHost(settings.host());
		ApiConfig.setPort(settings.portText());

		Alert alert = new Alert(Alert.AlertType.INFORMATION,
				I18nManager.tr("settings.save.success.message", settings.baseUrl()), ButtonType.OK);
		alert.setTitle(I18nManager.tr("settings.save.success.title"));
		alert.setHeaderText(I18nManager.tr("settings.save.success.header"));
		alert.showAndWait();

		closeWindow();
	}

	@FXML
	public void onCancelClick() {
		closeWindow();
	}

	@FXML
	public void onCloseClick() {
		closeWindow();
	}

	@FXML
	public void onResetClick() {
		schemeChoice.setValue(ApiConfig.DEFAULT_SCHEME);
		hostField.setText(ApiConfig.DEFAULT_HOST);
		portField.setText(String.valueOf(ApiConfig.DEFAULT_PORT));
	}

	@FXML
	public void onTestConnectionClick() {
		ApiSettings settings;
		try {
			settings = readSettingsStrict();
		} catch (IllegalArgumentException e) {
			showError(e.getMessage());
			return;
		}

		if (connectionTestTask != null && connectionTestTask.isRunning()) {
			return;
		}

		int connectivityPort = settings.portForConnectivity();
		setTestResult(I18nManager.tr("settings.test.inProgress", connectivityPort), Color.GRAY);

		Task<Boolean> task = new Task<>() {
			@Override
			protected Boolean call() {
				return testSocket(settings.host(), connectivityPort);
			}
		};
		connectionTestTask = task;

		task.setOnSucceeded(evt -> {
			Boolean ok = task.getValue();
			if (Boolean.TRUE.equals(ok)) {
				setTestResult(I18nManager.tr("settings.test.ok"), Color.web("#27ae60"));
			} else {
				setTestResult(I18nManager.tr("settings.test.fail"), Color.web("#e74c3c"));
			}
		});
		task.setOnFailed(evt -> setTestResult(I18nManager.tr("settings.test.error"), Color.web("#e74c3c")));

		Thread t = new Thread(task, "api-connection-test");
		t.setDaemon(true);
		t.start();
	}

	private void refreshBaseUrlPreview() {
		String scheme = normalizeScheme(schemeChoice.getValue());
		String host = hostField.getText() != null ? hostField.getText().trim() : "";
		String portTxt = portField.getText() != null ? portField.getText().trim() : "";

		if (scheme.isBlank() || host.isBlank()) {
			baseUrlLabel.setText("—");
			return;
		}
		String hostForUrl = host.contains(":") && !(host.startsWith("[") && host.endsWith("]"))
				? "[" + host + "]"
				: host;
		if (portTxt.isBlank()) {
			baseUrlLabel.setText(scheme + "://" + hostForUrl);
			return;
		}
		if (!portTxt.matches("\\d+")) {
			baseUrlLabel.setText("—");
			return;
		}
		baseUrlLabel.setText(scheme + "://" + hostForUrl + ":" + portTxt);
	}

	private ApiSettings readSettingsStrict() {
		String scheme = normalizeScheme(schemeChoice.getValue());
		String hostInput = hostField.getText() != null ? hostField.getText().trim() : "";
		String portInput = portField.getText() != null ? portField.getText().trim() : "";

		if (hostInput.isBlank()) {
			throw new IllegalArgumentException(I18nManager.tr("settings.host.required"));
		}

		// Permet de coller directement une URL (ex: https://api.example.com:3000)
		if (hostInput.contains("://")) {
			try {
				URI uri = new URI(hostInput);
				if (uri.getScheme() != null && !uri.getScheme().isBlank()) {
					scheme = normalizeScheme(uri.getScheme());
					schemeChoice.setValue(scheme);
				}
				if (uri.getHost() != null && !uri.getHost().isBlank()) {
					hostInput = uri.getHost();
					hostField.setText(hostInput);
				}
				if (uri.getPort() != -1) {
					portInput = String.valueOf(uri.getPort());
					portField.setText(portInput);
				} else {
					// URL sans port explicite => on vide le champ pour refléter "pas de port".
					portInput = "";
					portField.setText("");
				}
			} catch (URISyntaxException ignored) {
				// On retombe sur la validation classique ci-dessous.
			}
		}

		// Permet de coller "host:port" dans le champ hôte.
		hostInput = maybeExtractPortFromHost(hostInput);
		portInput = portField.getText() != null ? portField.getText().trim() : portInput;

		// Normalisation IPv6 : on autorise [::1] mais on stocke/teste ::1.
		if (hostInput.startsWith("[") && hostInput.endsWith("]") && hostInput.length() > 2) {
			hostInput = hostInput.substring(1, hostInput.length() - 1);
			hostField.setText(hostInput);
		}
		if (hostInput.contains(" ")) {
			throw new IllegalArgumentException(I18nManager.tr("settings.host.noSpaces"));
		}

		// Port optionnel.
		if (!portInput.isBlank()) {
			int port;
			try {
				port = Integer.parseInt(portInput);
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException(I18nManager.tr("settings.port.invalid"), e);
			}
			if (port <= 0 || port > 65535) {
				throw new IllegalArgumentException(I18nManager.tr("settings.port.range"));
			}
		}

		if (scheme.isBlank()) {
			scheme = ApiConfig.DEFAULT_SCHEME;
		}

		return new ApiSettings(scheme, hostInput, portInput);
	}

	private String maybeExtractPortFromHost(String hostInput) {
		String h = hostInput;
		int firstColon = h.indexOf(':');
		int lastColon = h.lastIndexOf(':');
		// On ne parse que les cas simples "host:port" (un seul ':') afin d'éviter les IPv6.
		if (firstColon > 0 && firstColon == lastColon) {
			String maybePort = h.substring(lastColon + 1);
			if (maybePort.matches("\\d{1,5}")) {
				String hostOnly = h.substring(0, lastColon);
				portField.setText(maybePort);
				hostField.setText(hostOnly);
				return hostOnly;
			}
		}
		return h;
	}

	private static String normalizeScheme(String scheme) {
		String s = scheme == null ? "" : scheme.trim().toLowerCase(Locale.ROOT);
		if (s.equals("https")) {
			return "https";
		}
		return "http";
	}

	private static boolean testSocket(String host, int port) {
		try (Socket socket = new Socket()) {
			socket.connect(new InetSocketAddress(host, port), 2000);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	private void setTestResult(String msg, Color color) {
		if (testResultLabel == null) {
			return;
		}
		Platform.runLater(() -> {
			testResultLabel.setText(msg);
			testResultLabel.setTextFill(color);
		});
	}

	private void clearTestResult() {
		if (testResultLabel != null) {
			testResultLabel.setText("");
		}
	}

	private void closeWindow() {
		if (hostField == null || hostField.getScene() == null) {
			return;
		}
		Stage stage = (Stage) hostField.getScene().getWindow();
		if (stage != null) {
			stage.close();
		}
	}

	private void showError(String msg) {
		Alert alert = new Alert(Alert.AlertType.ERROR);
		alert.setTitle(I18nManager.tr("common.error.title"));
		alert.setHeaderText(I18nManager.tr("settings.invalid.title"));
		alert.setContentText(msg);
		alert.showAndWait();
	}

	private record ApiSettings(String scheme, String host, String portText) {
		int portForConnectivity() {
			if (portText == null || portText.isBlank()) {
				return "https".equals(scheme) ? 443 : 80;
			}
			try {
				return Integer.parseInt(portText.trim());
			} catch (NumberFormatException e) {
				return "https".equals(scheme) ? 443 : 80;
			}
		}

		String baseUrl() {
			String hostForUrl = host.contains(":") && !(host.startsWith("[") && host.endsWith("]"))
					? "[" + host + "]"
					: host;
			if (portText == null || portText.isBlank()) {
				return scheme + "://" + hostForUrl;
			}
			return scheme + "://" + hostForUrl + ":" + portText.trim();
		}
	}
}
