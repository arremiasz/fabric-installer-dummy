/*
 * @author arremiasz
 */

package net.fabricmc.installer;

public class Settings {
	public String MCVersion;
	public String FabricVersion;
	public boolean snapshots;
	public String[] Modrinth;

	public Settings(String mc, String fabric, boolean snapshots) {
		MCVersion = mc;
		FabricVersion = fabric;
		this.snapshots = snapshots;
	}

	public Settings(String mc, String fabric, String[] mods) {
		MCVersion = mc;
		FabricVersion = fabric;
		Modrinth = mods;
	}

	public Settings(String mc, String fabric, boolean snapshots, String[] mods) {
		MCVersion = mc;
		FabricVersion = fabric;
		this.snapshots = snapshots;
		Modrinth = mods;
	}
}
