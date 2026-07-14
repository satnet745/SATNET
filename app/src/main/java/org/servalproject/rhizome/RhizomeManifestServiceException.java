/*
 * SATNET maintenance note:
 * This file is maintained as part of SATNET and builds on historical upstream work.
 * Copyright (C) 2011 The Serval Project.
 * Licensed under GPL-3.0-or-later; see LICENSE-SOFTWARE.md.
 */

package org.servalproject.rhizome;


/**
 * Thrown when a Rhizome manifest is too long to fit in a limited-size byte stream.
 *
 * @author Andrew Bettison <andrew@servalproject.com>
 */
public class RhizomeManifestServiceException extends Exception {
	private static final long serialVersionUID = 1L;
	private String mService;
	private String mExpectedService;

	public RhizomeManifestServiceException(String service, String expectedService) {
		super("manifest has service=" + service + ", expecting " + expectedService);
		mService = service;
		mExpectedService = expectedService;
	}

}
