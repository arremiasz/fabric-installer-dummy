/*
 * Copyright (c) 2016, 2017, 2018, 2019 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.fabricmc.installer.client;

import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Toolkit;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.HyperlinkEvent;

import net.fabricmc.installer.Handler;
import net.fabricmc.installer.InstallerGui;
import net.fabricmc.installer.LoaderVersion;
import net.fabricmc.installer.Main;
import net.fabricmc.installer.launcher.MojangLauncherHelperWrapper;
import net.fabricmc.installer.util.ArgumentParser;
import net.fabricmc.installer.util.InstallerProgress;
import net.fabricmc.installer.util.MetaHandler;
import net.fabricmc.installer.util.NoopCaret;
import net.fabricmc.installer.util.Reference;
import net.fabricmc.installer.util.Utils;

public class ClientHandler extends Handler {
	private JCheckBox createProfile;

	@Override
	public JPanel makePanel(InstallerGui installerGui) {
		JPanel pane = new JPanel(new GridBagLayout());
		pane.setBorder(new EmptyBorder(4, 4, 4, 4));

		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(VERTICAL_SPACING, HORIZONTAL_SPACING, VERTICAL_SPACING, HORIZONTAL_SPACING);
		c.gridx = c.gridy = 0;

		setupPane1(pane, c, installerGui);

		addRow(pane, c, "prompt.game.version", gameVersionComboBox = new JComboBox<>(), createSpacer(), snapshotCheckBox = new JCheckBox(Utils.BUNDLE.getString("option.show.snapshots")));
		snapshotCheckBox.setSelected(true);
		snapshotCheckBox.addActionListener(e -> {
			if (Main.GAME_VERSION_META.isComplete()) {
				updateGameVersions();
			}
		});

		Main.GAME_VERSION_META.onComplete(versions -> {
			updateGameVersions();
			//requestedMCVersion = "1.20.6"; // this is the string that should be set in the setup launcher and will be static after the fact

			for (int i = 0; i <= gameVersionComboBox.getItemCount(); i++) {
				if (i == gameVersionComboBox.getItemCount()) {
					throw new RuntimeException("Failed to find Minecraft version " + requestedMCVersion + " in the list of versions");
				} else if (gameVersionComboBox.getItemAt(i).equals(requestedMCVersion)) {
					gameVersionComboBox.setSelectedIndex(i);
					break;
				} else if (requestedMCVersion == null) {
					requestedMCVersion = gameVersionComboBox.getItemAt(0);
					gameVersionComboBox.setSelectedIndex(0);
					break;
				}
			}
		});

		addRow(pane, c, "prompt.loader.version", loaderVersionComboBox = new JComboBox<>());

		addRow(pane, c, "prompt.select.location", installLocation = new JTextField(20), selectFolderButton = new JButton());
		selectFolderButton.setText("...");
		selectFolderButton.setPreferredSize(new Dimension(installLocation.getPreferredSize().height, installLocation.getPreferredSize().height));
		selectFolderButton.addActionListener(e -> InstallerGui.selectInstallLocation(() -> installLocation.getText(), s -> installLocation.setText(s)));

		setupPane2(pane, c, installerGui);

		addRow(pane, c, null, statusLabel = new JLabel());
		statusLabel.setText(Utils.BUNDLE.getString("prompt.loading.versions"));

		addLastRow(pane, c, null, buttonInstall = new JButton(Utils.BUNDLE.getString("prompt.install")));
		buttonInstall.addActionListener(e -> {
			buttonInstall.setEnabled(false);
			install();
		});

		Main.LOADER_META.onComplete(versions -> {
			int stableIndex = -1;

			for (int i = 0; i < versions.size(); i++) {
				MetaHandler.GameVersion version = versions.get(i);
				loaderVersionComboBox.addItem(version.getVersion());

				if (version.isStable()) {
					stableIndex = i;
				}
			}

			//loaderVersionComboBox.addItem(SELECT_CUSTOM_ITEM);

			//If no stable versions are found, default to the latest version
			if (stableIndex == -1) {
				stableIndex = 0;
			}

			//requestedFabricVersion = "0.16.10"; // this is the string that should be set in the setup launcher and will be static after the fact

			for (int i = 0; i <= versions.size(); i++) {
				if (i == versions.size()) {
					throw new RuntimeException("Failed to find Fabric version " + requestedFabricVersion + " in the list of versions");
				} else if (versions.get(i).getVersion().equals(requestedFabricVersion)) {
					stableIndex = i;
					break;
				} else if (requestedFabricVersion == null) {
					requestedFabricVersion = versions.get(0).getVersion();
					break;
				}
			}

			loaderVersionComboBox.setSelectedIndex(stableIndex);
			statusLabel.setText(Utils.BUNDLE.getString("prompt.ready.install"));
		});

		pane.remove(0);
		pane.remove(1);
		gameVersionComboBox.setVisible(false);
		loaderVersionComboBox.setVisible(false);
		snapshotCheckBox.setVisible(false);
		pane.remove(4);

		return pane;
	}

	@Override
	public String name() {
		return "Client";
	}

	@Override
	public void install() {
		if (MojangLauncherHelperWrapper.isMojangLauncherOpen()) {
			showLauncherOpenMessage();
			return;
		}

		doInstall();
		doManualModInstall();
	}

	// pulls mods from modrinth
	private void doModInstall() {
	}

	// exe has to be in a directory with a folder containing mods
	private void doManualModInstall() {
		new Thread(() -> {
			// check if the mods directory exists
			Path mcDir = Paths.get(installLocation.getText());
			Path modsDir = mcDir.resolve("mods");

			// check if a "mods" directory exists in the same directory as the exe
			String exePath = System.getProperty("user.dir");

			Path exeDir = Paths.get(exePath);
			Path exeModsDir = exeDir.resolve("mods");

			if (Files.notExists(exeModsDir)) {
				showFailedMessage(Utils.BUNDLE.getString("prompt.exception.missing"));
				throw new RuntimeException(Utils.BUNDLE.getString("prompt.exception.missing"));
			}

			// if there are files in the mods directory, pause the thread and ask the user if they want to overwrite them
			try {
				if (Files.exists(modsDir) && Files.list(modsDir).count() > 0) {
					int result = JOptionPane.showConfirmDialog(null, Utils.BUNDLE.getString("prompt.install.mods.overwrite"), Utils.BUNDLE.getString("prompt.install.mods.title"), JOptionPane.YES_NO_OPTION);

					if (result != JOptionPane.YES_OPTION) {
						return;
					}
				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			}

			// copy mods from the exe's mods directory to the minecraft mods directory
			try {
				Files.walk(exeModsDir).forEach(source -> {
					Path destination = modsDir.resolve(exeModsDir.relativize(source));

					try {
						Files.copy(source, destination);
					} catch (IOException e) {
						e.printStackTrace();
					}
				});
			} catch (IOException e) {
				e.printStackTrace();
			}

			//showInstalledModsMessage();
			SwingUtilities.invokeLater(this::showInstalledModsMessage);
		}).start();
	}

	private void doInstall() {
		String gameVersion = (String) gameVersionComboBox.getSelectedItem();
		LoaderVersion loaderVersion = queryLoaderVersion();
		if (loaderVersion == null) return;

		System.out.println("Installing");

		new Thread(() -> {
			try {
				updateProgress(new MessageFormat(Utils.BUNDLE.getString("progress.installing")).format(new Object[]{loaderVersion.name}));
				Path mcPath = Paths.get(installLocation.getText());

				if (!Files.exists(mcPath)) {
					throw new RuntimeException(Utils.BUNDLE.getString("progress.exception.no.launcher.directory"));
				}

				final ProfileInstaller profileInstaller = new ProfileInstaller(mcPath);
				ProfileInstaller.LauncherType launcherType = null;

				if (createProfile.isSelected()) {
					List<ProfileInstaller.LauncherType> types = profileInstaller.getInstalledLauncherTypes();

					if (types.size() == 0) {
						throw new RuntimeException(Utils.BUNDLE.getString("progress.exception.no.launcher.profile"));
					} else if (types.size() == 1) {
						launcherType = types.get(0);
					} else {
						launcherType = showLauncherTypeSelection();

						if (launcherType == null) {
							// canceled
							statusLabel.setText(Utils.BUNDLE.getString("prompt.ready.install"));
							return;
						}
					}
				}

				String profileName = ClientInstaller.install(mcPath, gameVersion, loaderVersion, this);

				if (createProfile.isSelected()) {
					if (launcherType == null) {
						throw new RuntimeException(Utils.BUNDLE.getString("progress.exception.no.launcher.profile"));
					}

					profileInstaller.setupProfile(profileName, gameVersion, launcherType);
				}

				SwingUtilities.invokeLater(() -> showInstalledMessage(loaderVersion.name, gameVersion, mcPath.resolve("mods")));
			} catch (Exception e) {
				error(e);
			} finally {
				buttonInstall.setEnabled(true);
			}
		}).start();
	}

	private void showFailedMessage(String error) {
		JEditorPane pane = new JEditorPane("text/html", "<html><body style=\"" + buildEditorPaneStyle() + "\">" + error + "</body></html>");
		pane.setBackground(new Color(0, 0, 0, 0));
		pane.setEditable(false);
		pane.setCaret(new NoopCaret());

		final Image iconImage = Toolkit.getDefaultToolkit().getImage(ClassLoader.getSystemClassLoader().getResource("icon.png"));
		JOptionPane.showMessageDialog(null, pane, Utils.BUNDLE.getString("prompt.exception.occurrence"), JOptionPane.INFORMATION_MESSAGE, new ImageIcon(iconImage.getScaledInstance(64, 64, Image.SCALE_DEFAULT)));
	}

	private void showInstalledModsMessage() {
		JEditorPane pane = new JEditorPane("text/html", "<html><body style=\"" + buildEditorPaneStyle() + "\">" + Utils.BUNDLE.getString("prompt.install.mods.successful") + "</body></html>");
		pane.setBackground(new Color(0, 0, 0, 0));
		pane.setEditable(false);
		pane.setCaret(new NoopCaret());

		final Image iconImage = Toolkit.getDefaultToolkit().getImage(ClassLoader.getSystemClassLoader().getResource("icon.png"));
		JOptionPane.showMessageDialog(null, pane, Utils.BUNDLE.getString("prompt.install.mods.successful"), JOptionPane.INFORMATION_MESSAGE, new ImageIcon(iconImage.getScaledInstance(64, 64, Image.SCALE_DEFAULT)));
	}

	private void showInstalledMessage(String loaderVersion, String gameVersion, Path modsDirectory) {
		JEditorPane pane = new JEditorPane("text/html", "<html><body style=\"" + buildEditorPaneStyle() + "\">" + new MessageFormat(Utils.BUNDLE.getString("prompt.install.successful")).format(new Object[]{loaderVersion, gameVersion, Reference.FABRIC_API_URL}) + "</body></html>");
		pane.setBackground(new Color(0, 0, 0, 0));
		pane.setEditable(false);
		pane.setCaret(new NoopCaret());

		pane.addHyperlinkListener(e -> {
			try {
				if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
					if (e.getDescription().equals("fabric://mods")) {
						Desktop.getDesktop().open(modsDirectory.toRealPath().toFile());
					} else if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
						Desktop.getDesktop().browse(e.getURL().toURI());
					} else {
						throw new UnsupportedOperationException("Failed to open " + e.getURL().toString());
					}
				}
			} catch (Throwable throwable) {
				error(throwable);
			}
		});

		final Image iconImage = Toolkit.getDefaultToolkit().getImage(ClassLoader.getSystemClassLoader().getResource("icon.png"));
		JOptionPane.showMessageDialog(null, pane, Utils.BUNDLE.getString("prompt.install.successful.title"), JOptionPane.INFORMATION_MESSAGE, new ImageIcon(iconImage.getScaledInstance(64, 64, Image.SCALE_DEFAULT)));
	}

	private ProfileInstaller.LauncherType showLauncherTypeSelection() {
		Object[] options = {Utils.BUNDLE.getString("prompt.launcher.type.xbox"), Utils.BUNDLE.getString("prompt.launcher.type.win32")};

		int result = JOptionPane.showOptionDialog(null, Utils.BUNDLE.getString("prompt.launcher.type.body"), Utils.BUNDLE.getString("installer.title"), JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);

		if (result == JOptionPane.CLOSED_OPTION) {
			return null;
		}

		return result == JOptionPane.YES_OPTION ? ProfileInstaller.LauncherType.MICROSOFT_STORE : ProfileInstaller.LauncherType.WIN32;
	}

	private void showLauncherOpenMessage() {
		int result = JOptionPane.showConfirmDialog(null, Utils.BUNDLE.getString("prompt.launcher.open.body"), Utils.BUNDLE.getString("prompt.launcher.open.tile"), JOptionPane.YES_NO_OPTION);

		if (result == JOptionPane.YES_OPTION) {
			doInstall();
		} else {
			buttonInstall.setEnabled(true);
		}
	}

	@Override
	public void installCli(ArgumentParser args) throws Exception {
		Path path = Paths.get(args.getOrDefault("dir", () -> Utils.findDefaultInstallDir().toString()));

		if (!Files.exists(path)) {
			throw new FileNotFoundException("Launcher directory not found at " + path);
		}

		String gameVersion = getGameVersion(args);
		LoaderVersion loaderVersion = new LoaderVersion(getLoaderVersion(args));

		String profileName = ClientInstaller.install(path, gameVersion, loaderVersion, InstallerProgress.CONSOLE);

		if (args.has("noprofile")) {
			return;
		}

		ProfileInstaller profileInstaller = new ProfileInstaller(path);
		List<ProfileInstaller.LauncherType> types = profileInstaller.getInstalledLauncherTypes();
		ProfileInstaller.LauncherType launcherType = null;

		if (args.has("launcher")) {
			launcherType = ProfileInstaller.LauncherType.valueOf(args.get("launcher").toUpperCase(Locale.ROOT));
		}

		if (launcherType == null) {
			if (types.size() == 0) {
				throw new FileNotFoundException("Could not find a valid launcher profile .json");
			} else if (types.size() == 1) {
				// Only 1 launcher type found, install to that.
				launcherType = types.get(0);
			} else {
				throw new FileNotFoundException("Multiple launcher installations were found, please specify the target launcher using -launcher");
			}
		}

		profileInstaller.setupProfile(profileName, gameVersion, launcherType);
	}

	@Override
	public String cliHelp() {
		return "-dir <install dir> -mcversion <minecraft version, default latest> -loader <loader version, default latest> -launcher [win32, microsoft_store]";
	}

	@Override
	public void setupPane2(JPanel pane, GridBagConstraints c, InstallerGui installerGui) {
		addRow(pane, c, null, createProfile = new JCheckBox(Utils.BUNDLE.getString("option.create.profile"), true));

		installLocation.setText(Utils.findDefaultInstallDir().toString());
	}
}
