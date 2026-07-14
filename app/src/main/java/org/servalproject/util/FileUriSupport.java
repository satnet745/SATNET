package org.servalproject.util;

import android.content.Context;
import android.net.Uri;

import androidx.core.content.FileProvider;

import org.servalproject.BuildConfig;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public final class FileUriSupport {
	private FileUriSupport() {
	}

	public static Uri getSharableUri(Context context, File sourceFile) throws IOException {
		return FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".fileprovider",
				ensureSharableFile(context, sourceFile));
	}

	public static File ensureSharableFile(Context context, File sourceFile) throws IOException {
		if (context == null) {
			throw new IOException("Context is required for file sharing");
		}
		if (sourceFile == null || !sourceFile.exists()) {
			throw new IOException("File is unavailable for sharing");
		}
		File canonical = sourceFile.getCanonicalFile();
		if (isUnder(context.getFilesDir(), canonical)
				|| isUnder(context.getCacheDir(), canonical)
				|| isUnder(context.getExternalFilesDir(null), canonical)
				|| isUnder(context.getExternalCacheDir(), canonical)) {
			return canonical;
		}
		File shareDir = new File(context.getCacheDir(), "share");
		if (!shareDir.exists() && !shareDir.mkdirs()) {
			throw new IOException("Unable to prepare file sharing cache");
		}
		File target = new File(shareDir, canonical.getName());
		copyFile(canonical, target);
		return target;
	}

	private static boolean isUnder(File parent, File child) throws IOException {
		if (parent == null || child == null) {
			return false;
		}
		String parentPath = parent.getCanonicalPath();
		String childPath = child.getCanonicalPath();
		return childPath.equals(parentPath) || childPath.startsWith(parentPath + File.separator);
	}

	private static void copyFile(File source, File target) throws IOException {
		FileInputStream inputStream = null;
		FileOutputStream outputStream = null;
		try {
			inputStream = new FileInputStream(source);
			outputStream = new FileOutputStream(target, false);
			byte[] buffer = new byte[8192];
			int read;
			while ((read = inputStream.read(buffer)) != -1) {
				outputStream.write(buffer, 0, read);
			}
			outputStream.flush();
		} finally {
			if (inputStream != null) {
				inputStream.close();
			}
			if (outputStream != null) {
				outputStream.close();
			}
		}
	}
}

