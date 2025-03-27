# fabric-mod-installer-tool
An Installer for Fabric. For the vanilla launcher. 
Configurable mod setup tool to send to your friends who struggle with mod installs.
This is designed for one user to set up the exe, and to share the ZIP output with friends and have them
press the install button for an easy mod installation that doesn't require guidance.

## Download
You may find the latest release at https://github.com/arremiasz/fabric-mod-installer-tool/releases

# How to use

## Setup
The person who wants to setup mods will download the exe into its own directory (this is not required but 
recommended since the tool is creating files and will overwrite existing ones). You'll head to the `Settings` tab
and select the `Minecraft Version` and Fabric `Loader Version`. If you have mod jars you'd like to use from your 
computer, you can import them using the `Select Folder` button. They will show up in the `Imported Mods` list once
you select `Save and Import`. The `Imported Mods` list shows mods currently in the `mods` folder which is located
in the same directory as the exe. You may also import mods directly from Modrinth by pasting the mod URL or slug
into the text box labed `Import from Modrinth` (see Features for more information). Once you have all your settings,
you can press the `Save and Import` button to save your settings into `config.json` and copy your mods into the
`mods` folder. Once you have saved and imported, you may `Zip Files` to create a zip file to share with your friends.
This may take a while depending on the number of mods you have and the size of them.
All they will need to do is extract the zip and run the exe and press the `Install` button on the `Client` tab.

## Installing from Zip
The person who receives the zip will extract the contents into a folder. They will run the exe, select a `Launcher Location`
if needed, and press the `Install` button. They do not need to configure the settings or import anything.

# Features

## Import Mods
You can import mods from a folder to be installed. These are saved in the `mods` folder in the same directory 
as the exe. To clear the imports, simply delete this folder or import new mods which will overwrite the old ones.

## Modrinth Support:
Paste links or the slug (ex. https:\/\/modrinth.com/mod/`slug`) in their own lines to download and 
install mods from Modrinth. The installer will automatically grab the latest version of the mod matching the 
Minecraft version selected. If a specific version is desired, download the mod and import it using 
the tool instead.

# Authors
arremiasz - https://github.com/arremiasz - Main Developer\
aidanfoss - https://github.com/aidanfoss - Modrinth API Caller