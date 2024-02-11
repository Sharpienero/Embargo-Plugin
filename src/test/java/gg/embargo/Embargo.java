package gg.embargo;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class Embargo
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(EmbargoPlugin.class);
		RuneLite.main(args);
	}
}