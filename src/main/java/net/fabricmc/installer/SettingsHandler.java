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
 *
 * @author: FabricMC
 * @modified arremiasz
 */

package net.fabricmc.installer;

import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;

import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JEditorPane;
import javax.swing.JFileChooser;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.HyperlinkEvent;

import org.zeroturnaround.zip.NameMapper;
import org.zeroturnaround.zip.ZipUtil;
import com.google.gson.Gson;

import net.fabricmc.installer.client.ClientInstaller;
import net.fabricmc.installer.client.ProfileInstaller;
import net.fabricmc.installer.launcher.MojangLauncherHelperWrapper;
import net.fabricmc.installer.util.ArgumentParser;
import net.fabricmc.installer.util.InstallerProgress;
import net.fabricmc.installer.util.MetaHandler;
import net.fabricmc.installer.util.NoopCaret;
import net.fabricmc.installer.util.Reference;
import net.fabricmc.installer.util.Utils;

public class SettingsHandler extends Handler {
	private JCheckBox createProfile;
	private JTextField modImportLocation;
	public JScrollPane scrollableTextArea;
	public JTextArea modrinthMods;
	public JList<String> importedMods;
	public JScrollPane scrollableModsList;
	public DefaultListModel<String> listModel = new DefaultListModel<>();
	public JButton buttonZip;

	@Override
	public JPanel makePanel(InstallerGui installerGui) {
		JPanel pane = new JPanel(new GridBagLayout());
		pane.setBorder(new EmptyBorder(4, 4, 4, 4));

		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(VERTICAL_SPACING, HORIZONTAL_SPACING, VERTICAL_SPACING, HORIZONTAL_SPACING);
		c.gridx = c.gridy = 0;

		setupPane1(pane, c, installerGui);

		addRow(pane, c, "prompt.game.version", gameVersionComboBox = new JComboBox<>(), createSpacer(), snapshotCheckBox = new JCheckBox(Utils.BUNDLE.getString("option.show.snapshots")));
		snapshotCheckBox.setSelected(SNAPSHOTS);
		snapshotCheckBox.addActionListener(e -> {
			if (Main.GAME_VERSION_META.isComplete()) {
				updateGameVersions();
				SNAPSHOTS = !SNAPSHOTS;
			}
		});
		gameVersionComboBox.addActionListener(e -> {
			buttonInstall.setEnabled(true);
		});

		Main.GAME_VERSION_META.onComplete(versions -> {
			updateGameVersions();

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
		loaderVersionComboBox.addActionListener(e -> {
			buttonInstall.setEnabled(true);
		});

		// add a row for "mod files" that lets import jar files from the user's computer
		addRow(pane, c, "prompt.install.location", modImportLocation = new JTextField(), selectFolderButton = new JButton(Utils.BUNDLE.getString("prompt.select.folder")));
		selectFolderButton.addActionListener(e -> {
			JFileChooser fileChooser = new JFileChooser();
			fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			fileChooser.setAcceptAllFileFilterUsed(false);

			if (fileChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
				modImportLocation.setText(fileChooser.getSelectedFile().getAbsolutePath());
				buttonInstall.setEnabled(true);
			}
		});
		modImportLocation.setEditable(false);

		//add a row for an area that displays the files in the mods folder in modImportLocation
		addRow(pane, c, "prompt.install.list", scrollableModsList = new JScrollPane(importedMods = new JList<>(listModel)));
		scrollableModsList.setPreferredSize(new Dimension(200, 100));
		scrollableModsList.setMinimumSize(new Dimension(200, 100));
		updateListModel();

		// add a row for jscrollpane with a jtextarea inside it that lets the user input modrinth mod urls
		addRow(pane, c, "prompt.install.mods.modrinth", scrollableTextArea = new JScrollPane(modrinthMods = new JTextArea()));
		scrollableTextArea.setPreferredSize(new Dimension(200, 100));
		scrollableTextArea.setMinimumSize(new Dimension(200, 100));
		modrinthMods.setLineWrap(true);
		modrinthMods.setWrapStyleWord(true);
		modrinthMods.setEditable(true);
		modrinthMods.addKeyListener(new KeyListener() {
			@Override
			public void keyPressed(KeyEvent e) {
				buttonInstall.setEnabled(true);
			}

			@Override
			public void keyReleased(KeyEvent e) {
			}

			@Override
			public void keyTyped(KeyEvent e) {
			}
		});

		if (mods != null) {
			for (String mod : mods) {
				modrinthMods.append(mod + "\n");
			}
		}

		addRow(pane, c, null, buttonInstall = new JButton(Utils.BUNDLE.getString("prompt.save")));
		buttonInstall.addActionListener(e -> {
			String exePath = System.getProperty("user.dir");

			if (exePath != null) {
				Path path = Paths.get(exePath);

				try {
					if (Files.exists(path) && Files.list(path).count() > 1) {
						int result = JOptionPane.showConfirmDialog(null, Utils.BUNDLE.getString("prompt.save.overwrite"), Utils.BUNDLE.getString("prompt.save.saving"), JOptionPane.YES_NO_OPTION);

						if (result != JOptionPane.YES_OPTION) {
							return;
						}
					}
				} catch (IOException ex) {
					throw new RuntimeException(ex);
				}
			}

			requestedMCVersion = gameVersionComboBox.getSelectedItem().toString();
			requestedFabricVersion = loaderVersionComboBox.getSelectedItem().toString();
			mods = new String[modrinthMods.getText().split("\n").length];

			for (int i = 0; i < mods.length; i++) {
				mods[i] = modrinthMods.getText().split("\n")[i];
			}

			Gson jsonObject = new Gson();
			Settings settings = new Settings(requestedMCVersion, requestedFabricVersion, SNAPSHOTS, mods);
			String jsonString = jsonObject.toJson(settings);

			try (FileWriter file = new FileWriter("config.json")) {
				file.write(jsonString);
				file.flush();
			} catch (IOException ex) {
				ex.printStackTrace();
			}

			// copy all files from modImportLocation to a folder called mods in the directory of the executable
			if (modImportLocation.getText() != null && !modImportLocation.getText().isEmpty()) {
				Path modsPath = Paths.get(System.getProperty("user.dir"), "mods");

				try {
					if (Files.exists(modsPath)) {
						Files.walk(modsPath).forEach(source -> {
							try {
								Files.delete(source);
							} catch (IOException ex) {
								//ex.printStackTrace();
							}
						});
					}

					Files.walk(Paths.get(modImportLocation.getText())).forEach(source -> {
						Path destination = modsPath.resolve(Paths.get(modImportLocation.getText()).relativize(source));

						try {
							Files.copy(source, destination);
						} catch (IOException ex) {
							//ex.printStackTrace();
						}
					});
				} catch (IOException ex) {
					ex.printStackTrace();
				}
			}

			updateListModel();
			buttonInstall.setEnabled(false);
			buttonZip.setEnabled(true);
		});

		addLastRow(pane, c, null, buttonZip = new JButton(Utils.BUNDLE.getString("prompt.zip")));
		buttonZip.setEnabled(false);
		buttonZip.addActionListener(e -> {
			buttonZip.setEnabled(false);

			try {
				createZip();
			} catch (IOException ex) {
				showFailedMessage("Unable to create zip.");
				ex.printStackTrace();
				buttonZip.setEnabled(true);
			}
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

			for (int i = 0; i <= versions.size(); i++) {
				if (i == versions.size()) {
					throw new RuntimeException("Failed to find Fabric version " + requestedFabricVersion + " in the list of versions");
				} else if (versions.get(i).getVersion().equals(requestedFabricVersion)) {
					loaderVersionComboBox.setSelectedIndex(i);
					break;
				} else if (requestedFabricVersion == null) {
					requestedFabricVersion = versions.get(stableIndex).getVersion();
					loaderVersionComboBox.setSelectedIndex(stableIndex);
					break;
				}
			}
		});

		return pane;
	}

	@Override
	public String name() {
		return "Settings";
	}

	@Override
	public void install() {
		if (MojangLauncherHelperWrapper.isMojangLauncherOpen()) {
			showLauncherOpenMessage();
			return;
		}

		doInstall();
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

	protected void updateListModel() {
		Path modsPath = Paths.get(System.getProperty("user.dir"), "mods");
		listModel.clear();

		try {
			if (Files.exists(modsPath)) {
				Files.walk(modsPath).forEach(source -> {
					if (!source.getFileName().toString().equals("mods")) {
						listModel.addElement(source.getFileName().toString());
					}
				});
			}
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}

	private void showFailedMessage(String error) {
		JEditorPane pane = new JEditorPane("text/html", "<html><body style=\"" + buildEditorPaneStyle() + "\">" + error + "</body></html>");
		pane.setBackground(new Color(0, 0, 0, 0));
		pane.setEditable(false);
		pane.setCaret(new NoopCaret());

		final Image iconImage = Toolkit.getDefaultToolkit().getImage(ClassLoader.getSystemClassLoader().getResource("icon.png"));
		JOptionPane.showMessageDialog(null, pane, Utils.BUNDLE.getString("prompt.exception.occurrence"), JOptionPane.INFORMATION_MESSAGE, new ImageIcon(iconImage.getScaledInstance(64, 64, Image.SCALE_DEFAULT)));
	}

	private void showMessage(String prompt) {
		JEditorPane pane = new JEditorPane("text/html", "<html><body style=\"" + buildEditorPaneStyle() + "\">" + prompt + "</body></html>");
		pane.setBackground(new Color(0, 0, 0, 0));
		pane.setEditable(false);
		pane.setCaret(new NoopCaret());

		final Image iconImage = Toolkit.getDefaultToolkit().getImage(ClassLoader.getSystemClassLoader().getResource("icon.png"));
		JOptionPane.showMessageDialog(null, pane, Utils.BUNDLE.getString("installer.title"), JOptionPane.INFORMATION_MESSAGE, new ImageIcon(iconImage.getScaledInstance(64, 64, Image.SCALE_DEFAULT)));
	}

	public void createZip() throws IOException {
		// to zip: fabric launcher, mods folder, config.json
		Path exePath = Paths.get(System.getProperty("user.dir"), "fabric-mod-installer-tool.exe");
		Path modsPath = Paths.get(System.getProperty("user.dir"), "/mods");
		Path configPath = Paths.get(System.getProperty("user.dir"), "config.json");
		Path[] paths = {exePath, modsPath};

		if (Files.exists(modsPath)) {
			ZipUtil.pack(new File("mods/"), new File("fabric-mod-installer-tool.zip"), new NameMapper() {
				public String map(String name) {
					return "mods/" + name;
				}
			});

			ZipUtil.addEntry(new File("fabric-mod-installer-tool.zip"), "config.json", configPath.toFile());
			ZipUtil.addEntry(new File("fabric-mod-installer-tool.zip"), "fabric-mod-installer-tool.exe", exePath.toFile());
		} else {
			ZipUtil.packEntry(exePath.toFile(), new File("fabric-mod-installer-tool.zip"));
			ZipUtil.addEntry(new File("fabric-mod-installer-tool.zip"), "config.json", configPath.toFile());
		}

		showMessage("Zip created successfully.");
	}
}
