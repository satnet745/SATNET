/*
 * SATNET maintenance note:
 * This file is maintained as part of SATNET and builds on historical upstream work.
 * Copyright (C) 2012 The Serval Project.
 * Licensed under GPL-3.0-or-later; see LICENSE-SOFTWARE.md.
 */
package org.servalproject.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import org.servalproject.satnet.ui.SatnetMapsActivity;

/**
 * Backwards-compatible shim that forwards legacy launcher routes into SATNET Maps.
 */
public class MapsActivity extends Activity {

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onCreate(android.os.Bundle)
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		startActivity(new Intent(this, SatnetMapsActivity.class));
		finish();
	}

}