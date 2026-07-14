/*
 * SATNET maintenance note:
 * This file is maintained as part of SATNET and builds on historical upstream work.
 * Copyright (C) 2011 The Serval Project.
 * Licensed under GPL-3.0-or-later; see LICENSE-SOFTWARE.md.
 */

package org.servalproject.rhizome;

import java.io.File;

/**
 * Thrown when a Rhizome manifest is too long to fit in a limited-size byte stream.
 *
 * @author Andrew Bettison <andrew@servalproject.com>
 */
public class RhizomeManifestSizeException extends Exception {
	private static final long serialVersionUID = 1L;
	private long mSize;
	private long mMaxSize;

	public RhizomeManifestSizeException(String message, long size, long maxSize) {
		super(message + " (" + size + "bytes exceeds " + maxSize + ")");
		mSize = size;
		mMaxSize = maxSize;
	}

	public RhizomeManifestSizeException(File manifestFile, long maxSize) {
		this(manifestFile.toString(), manifestFile.length(), maxSize);
	}

}
