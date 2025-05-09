/*
 * Copyright (c) 2017, Adam <Adam@sigterm.info>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins;

import java.awt.*;
import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
public @interface PluginDescriptor
{
    String DrDeath = "<html>[<font color=#FF0000>DD</font>]";
    String Bee = "<html>[<font color=#FFD700><b>B</b></font>] ";
    String Nate = "<html>[<font color=orange>N</font>] ";
    String Mocrosoft = "<html>[<font color=#b8f704M>M</font>] ";
    String OG = "<html>[<font color=#FF69B4>O</font>] ";
    String Default = "<html>[<font color=green>D</font>] ";
    String SaCo = "<html>[<font color=#0d937b>S</font>] ";
    String Bank = "<html>[<font color=#9900ff>B</font>] ";
    String Forn = "<html>[<font color=#AF2B1E>F</font>] ";
    String See1Duck = "<html>[<font color=#ffff1a>\uD83E\uDD86</font>] ";
    String TaFCat = "<html>[<font color=#ffff1a>\uD83D\uDC31</font>] ";
    String GMason = "<html>[<font color=#0077B6>G</font>] ";
    String Pumster = "<html>[<font color=#03ff4e>P</font>] ";
    String Basche = "<html>[<font color=#07A6F0>B</font>] ";
    String Vince = "<html>[<font color=#5bffe4>V</font>] ";
    String Basm = "<html>[<font color=#b3b3b3>W</font>] ";
    String Geoff = "<html>[<font color=#ffbc03>G</font>] ";
    String Bttqjs = "<html>[<font color=#e57373>J</font>] ";
    String zuk = "<html>[<font color=#5F9596>Z</font>] ";
    String GZ = "<html>[<font color=#0077B6>\u2728</font>] ";
	String VOX = "<html>[<font color=#5F0F40>\uD83C\uDF33</font>] ";
    String StickToTheScript = "<html>[<font color=#FF4F00>STTS</font>] ";
    String Gabulhas = "<html>[<font color=#F44FB0>Gab</font>] ";
    String zerozero ="<html>[<font color=#000000>00</font>] " ;
    String LiftedMango = "<html>[<font color=#FF7F50>\uD83E\uDD6D</font>] ";
    String eXioStorm = "<html>[<font color=#ff00dc>§</font>] "; Color stormColor = new Color(255, 0, 220);
    String Girdy = "<html>[<font color=#3DED97>\u01E5</font>] ";
    String Cicire = "<html>[<font color=#68ff00>Ci</font>] ";
    String Budbomber = "<html>[<font color='#0077B6'>bb</font>] ";
    String ChillX = "<html>[<font color=#05e1f5>C</font>] ";
    String Gage = "<html>[<font color=#00008B>Gage</font>] ";
	String Bradley = "<html>[<font color=#E32636>BR</font>] ";
	String Frosty = "<html>[<font color=#00FFFF>\u2744</font>] ";
	String Maxxin = "<html>[<font color='#8B0000'>MX</font>] ";



	String name();

	/**
	 * Internal name used in the config.
	 */
	String configName() default "";

	/**
	 * A short, one-line summary of the plugin.
	 */
	String description() default "";

	/**
	 * A list of plugin keywords, used (together with the name) when searching for plugins.
	 * Each tag should not contain any spaces, and should be fully lowercase.
	 */
	String[] tags() default {};

	/**
	 * A list of plugin names that are mutually exclusive with this plugin. Any plugins
	 * with a name or conflicts value that matches this will be disabled when this plugin
	 * is started
	 */
	String[] conflicts() default {};

	/**
	 * If this plugin should be defaulted to on. Plugin-Hub plugins should always
	 * have this set to true (the default), since having them off by defaults means
	 * the user has to install the plugin, then separately enable it, which is confusing.
	 */
	boolean enabledByDefault() default true;

    /**
     * always on
     */
    boolean alwaysOn() default false;

	/**
	 * Whether or not plugin is hidden from configuration panel
	 */
	boolean hidden() default false;

	boolean developerPlugin() default false;

	boolean loadInSafeMode() default true;

	boolean priority() default false;
}
