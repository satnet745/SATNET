/*
 * SATNET maintenance note:
 * This file is maintained as part of SATNET and builds on historical upstream work.
 * Copyright (C) 2011 The Serval Project.
 * Licensed under GPL-3.0-or-later; see LICENSE-SOFTWARE.md.
 */

package org.servalproject.rhizome;

import java.text.NumberFormat;

import org.servalproject.R;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.widget.TextView;

public class RhizomeStorage extends Activity
{
	// the statistics of the SD card
	private StatFs stats;
	// the state of the external storage
	private String externalStorageState;

	// the total size of the SD card
	private double totalSize;
	// the available free space
	private double freeSpace;
	// a String to store the SD card information
	private String outputInfo;
	// a TextView to output the SD card state
	private TextView tv_state;
	// a TextView to output the SD card information
	private TextView tv_info;
	// set the number format output
	private NumberFormat numberFormat;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.rhizome_main);

		// initialize the Text Views with the data at the main.xml file
		tv_state = (TextView) findViewById(R.id.state);
		tv_info = (TextView) findViewById(R.id.info);

		// get external storage (SD card) state
		externalStorageState = Environment.getExternalStorageState();

		// checks if the SD card is attached to the Android device
		if (externalStorageState.equals(Environment.MEDIA_MOUNTED)
				|| externalStorageState.equals(Environment.MEDIA_UNMOUNTED)
				|| externalStorageState
						.equals(Environment.MEDIA_MOUNTED_READ_ONLY))
		{
			// obtain the stats from the root of the SD card.
			stats = new StatFs(Environment.getExternalStorageDirectory()
					.getPath());

			// total usable size
			totalSize = stats.getBlockCount() * stats.getBlockSize();

			// initialize the NumberFormat object
			numberFormat = NumberFormat.getInstance();
			// disable grouping
			numberFormat.setGroupingUsed(false);
			// display numbers with two decimal places
			numberFormat.setMaximumFractionDigits(2);

			// Output the SD card's total size in gigabytes, megabytes,
			// kilobytes and bytes
			String totalSizeText = getString(R.string.rhizome_storage_total_size,
					numberFormat.format((totalSize / 1073741824)),
					numberFormat.format((totalSize / 1048576)));
			// + "Size in kilobytes: " + numberFormat.format((totalSize /
			// (double)1024)) + " KB \n"
			// + "Size in bytes: " + numberFormat.format(totalSize) + " B \n";

			// available free space
			freeSpace = stats.getAvailableBlocks() * stats.getBlockSize();

			// Output the SD card's available free space in gigabytes,
			// megabytes, kilobytes and bytes
			String remainingSpaceText = getString(R.string.rhizome_storage_remaining_space,
					numberFormat.format((freeSpace / 1073741824)),
					numberFormat.format((freeSpace / 1048576)));
			outputInfo = getString(R.string.rhizome_storage_summary,
					totalSizeText, remainingSpaceText);
			// + "Size in kilobytes: " + numberFormat.format((freeSpace /
			// (double)1024)) + " KB \n"
			// + "Size in bytes: " + numberFormat.format(freeSpace) + " B \n";

			// output the SD card state
			tv_state.setText(R.string.rhizome_storage_found);
			// output the SD card info
			tv_info.setText(outputInfo);
		}
		else // external storage was not found
		{
			// output the SD card state
			tv_state.setTextColor(Color.RED);
			tv_state.setText(getString(R.string.rhizome_storage_not_found,
					externalStorageState));
		}
	}
}
