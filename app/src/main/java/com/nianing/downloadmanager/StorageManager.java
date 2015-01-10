package com.nianing.downloadmanager;


import android.content.Context;
import android.os.Environment;
import android.os.StatFs;
import android.util.Log;

import java.io.File;

/**
 * Manages the storage space consumed by Downloads Data dir. When space falls below
 * a threshold limit (set in resource xml files), starts cleanup of the Downloads data dir
 * to free up space.
 */
class StorageManager {

    /** see {@link android.os.Environment#getExternalStorageDirectory()} */
    private final File mExternalStorageDir;

    /** see {@link android.os.Environment#getDownloadCacheDirectory()} */
    private final File mSystemCacheDir;

    /** The downloaded files are saved to this dir. it is the value returned by
     * {@link android.content.Context#getCacheDir()}.
     */
    private final File mDownloadDataDir;

    /** how often do we need to perform checks on space to make sure space is available */
    private static final int FREQUENCY_OF_CHECKS_ON_SPACE_AVAILABILITY = 1024 * 1024; // 1MB
    private int mBytesDownloadedSinceLastCheckOnSpace = 0;

    /** misc members */
    private final Context mContext;

    public StorageManager(Context context) {
        mContext = context;
        mDownloadDataDir = getDownloadDataDirectory(context);
        mExternalStorageDir = Environment.getExternalStorageDirectory();
        mSystemCacheDir = Environment.getDownloadCacheDirectory();
    }


    void verifySpace(String path, long length) throws StopRequestException {
        resetBytesDownloadedSinceLastCheckOnSpace();
        File dir = null;
        if (Constants.LOGV) {
            Log.i(Constants.TAG, "in verifySpace,path: " + path + ", length: " + length);
        }
        if (path == null) {
            throw new IllegalArgumentException("path can't be null");
        }
        if (path.startsWith(mExternalStorageDir.getPath())) {
            dir = mExternalStorageDir;
        } else if (path.startsWith(mDownloadDataDir.getPath())) {
            dir = mDownloadDataDir;
        } else if (path.startsWith(mSystemCacheDir.getPath())) {
            dir = mSystemCacheDir;
        }
        if (dir == null) {
            throw new IllegalStateException("invalid combination of path: " + path);
        }
        findSpace(dir, length);
    }

    /**
     * finds space in the given filesystem (input param: root) to accommodate # of bytes
     * specified by the input param(targetBytes).
     * returns true if found. false otherwise.
     */
    private synchronized void findSpace(File root, long targetBytes)
            throws StopRequestException {
        if (targetBytes == 0) {
            return;
        }

        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            throw new StopRequestException(Downloads.Columns.STATUS_DEVICE_NOT_FOUND_ERROR,
                    "external media not mounted");
        }

        // is there enough space in the file system of the given param 'root'.
        long bytesAvailable = getAvailableBytesInFileSystemAtGivenRoot(root);
        if (bytesAvailable < targetBytes) {
            throw new StopRequestException(Downloads.Columns.STATUS_INSUFFICIENT_SPACE_ERROR,
                    "not enough free space in the filesystem rooted at: " + root +
                    " and unable to free any more");
        }
    }


    private long getAvailableBytesInFileSystemAtGivenRoot(File root) {
        StatFs stat = new StatFs(root.getPath());
        // put a bit of margin (in case creating the file grows the system by a few blocks)
        long availableBlocks = (long) stat.getAvailableBlocks() - 4;
        long size = stat.getBlockSize() * availableBlocks;
        if (Constants.LOGV) {
            Log.i(Constants.TAG, "available space (in bytes) in filesystem rooted at: " +
                    root.getPath() + " is: " + size);
        }
        return size;
    }



    public static File getDownloadDataDirectory(Context context) {
        return context.getCacheDir();
    }



    private synchronized void resetBytesDownloadedSinceLastCheckOnSpace() {
        mBytesDownloadedSinceLastCheckOnSpace = 0;
    }
}
