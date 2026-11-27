package com.golemaddons.ui;

import net.minecraft.client.gui.screens.Screen;

/** A screen that shows another screen inside a pane (the section screens). Lets tests find the page's widgets. */
public interface PaneHost {
	/** The page shown in the pane, or null. */
	Screen page();

	/** Where the page's (0, 0) is on screen. */
	int pageOffsetX();

	int pageOffsetY();
}
