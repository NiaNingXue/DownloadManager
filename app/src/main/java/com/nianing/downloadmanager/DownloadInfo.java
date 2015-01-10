package com.nianing.downloadmanager;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Pair;

import com.nianing.downloadmanager.Downloads.Columns;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Stores information about an individual download.
 */
public class DownloadInfo{
    // TODO: move towards these in-memory objects being sources of truth, and
    // periodically pushing to provider.

    public static class Reader {
        private Cursor mCursor;
        private DownloadProvider mDownloadProvider;

        public Reader(Cursor cursor) {
            mCursor = cursor;
        }

        public DownloadInfo newDownloadInfo(Context context, SystemFacade systemFacade,
                DownloadProvider downloadProvider) {
            final DownloadInfo info = new DownloadInfo(context, systemFacade,downloadProvider);
            mDownloadProvider = downloadProvider;
            updateFromDatabase(info);
            readRequestHeaders(info);
            return info;
        }

        public void updateFromDatabase(DownloadInfo info) {
            info.mId = getLong(Downloads.Columns._ID);
            info.mUri = getString(Downloads.Columns.COLUMN_URI);
            info.mFileName = getString(Downloads.Columns._DATA);
            info.mDestination = getString(Downloads.Columns.COLUMN_DESTINATION);
            info.mETag = getString(Constants.ETAG);
            info.mStatus = getInt(Downloads.Columns.COLUMN_STATUS);
            info.mNumFailed = getInt(Downloads.Columns.COLUMN_FAILED_CONNECTIONS);
            int retryRedirect = getInt(Constants.RETRY_AFTER_X_REDIRECT_COUNT);
            info.mRetryAfter = retryRedirect & 0xfffffff;
            info.mLastMod = getLong(Downloads.Columns.COLUMN_LAST_MODIFICATION);
            info.mPackage = getString(Downloads.Columns.COLUMN_PACKAGE);
            info.mExtras = getString(Downloads.Columns.COLUMN_EXTRAS);
            info.mCookies = getString(Downloads.Columns.COLUMN_COOKIE_DATA);
            info.mUserAgent = getString(Downloads.Columns.COLUMN_USER_AGENT);
            info.mReferer = getString(Downloads.Columns.COLUMN_REFERER);
            info.mTotalBytes = getLong(Downloads.Columns.COLUMN_TOTAL_BYTES);
            info.mCurrentBytes = getLong(Downloads.Columns.COLUMN_CURRENT_BYTES);
            info.mDeleted = getInt(Downloads.Columns.COLUMN_DELETED) == 1;
            info.mAllowedNetworkTypes = getInt(Downloads.Columns.COLUMN_ALLOWED_NETWORK_TYPES);
            info.mTitle = getString(Downloads.Columns.COLUMN_TITLE);
            info.mDescription = getString(Downloads.Columns.COLUMN_DESCRIPTION);

            synchronized (this) {
                info.mControl = getInt(Downloads.Columns.COLUMN_CONTROL);
            }
        }

        private void readRequestHeaders(DownloadInfo info) {
            info.mRequestHeaders.clear();
            Cursor cursor = mDownloadProvider.queryRequestHeaders(info.mId);
            try {
                int headerIndex = cursor.getColumnIndexOrThrow(Downloads.Columns.RequestHeaders.COLUMN_HEADER);
                int valueIndex = cursor.getColumnIndexOrThrow(Downloads.Columns.RequestHeaders.COLUMN_VALUE);
                for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                    addHeader(info, cursor.getString(headerIndex), cursor.getString(valueIndex));
                }
            } finally {
                cursor.close();
            }

            if (info.mCookies != null) {
                addHeader(info, "Cookie", info.mCookies);
            }
            if (info.mReferer != null) {
                addHeader(info, "Referer", info.mReferer);
            }
        }

        private void addHeader(DownloadInfo info, String header, String value) {
            info.mRequestHeaders.add(Pair.create(header, value));
        }

        private String getString(String column) {
            int index = mCursor.getColumnIndexOrThrow(column);
            String s = mCursor.getString(index);
            return (TextUtils.isEmpty(s)) ? null : s;
        }

        private Integer getInt(String column) {
            return mCursor.getInt(mCursor.getColumnIndexOrThrow(column));
        }

        private Long getLong(String column) {
            return mCursor.getLong(mCursor.getColumnIndexOrThrow(column));
        }
    }

    /**
     * Constants used to indicate network state for a specific download, after
     * applying any requested constraints.
     */
    public enum NetworkState {
        /**
         * The network is usable for the given download.
         */
        OK,

        /**
         * There is no network connectivity.
         */
        NO_CONNECTION,

        /**
         * The download exceeds the maximum size for this network.
         */
        UNUSABLE_DUE_TO_SIZE,

        /**
         * The download exceeds the recommended maximum size for this network,
         * the user must confirm for this download to proceed without WiFi.
         */
        RECOMMENDED_UNUSABLE_DUE_TO_SIZE,

        /**
         * The current connection is roaming, and the download can't proceed
         * over a roaming connection.
         */
        CANNOT_USE_ROAMING,

        /**
         * The app requesting the download specific that it can't use the
         * current network connection.
         */
        TYPE_DISALLOWED_BY_REQUESTOR,

        /**
         * Current network is blocked for requesting application.
         */
        BLOCKED;
    }

    /**
     * For intents used to notify the user that a download exceeds a size threshold, if this extra
     * is true, WiFi is required for this download size; otherwise, it is only recommended.
     */
    public static final String EXTRA_IS_WIFI_REQUIRED = "isWifiRequired";

    public long mId;
    public String mUri;
    public String mFileName;
    public String mDestination;
    public String mETag;
    public int mControl;
    public int mStatus;
    public int mNumFailed;
    public int mRetryAfter;
    public long mLastMod;
    public String mPackage;
    public String mExtras;
    public String mCookies;
    public String mUserAgent;
    public String mReferer;
    public long mTotalBytes;
    public long mCurrentBytes;
    public boolean mDeleted;
    public int mAllowedNetworkTypes;
    public String mTitle;
    public String mDescription;

    public int mFuzz;
    private List<Pair<String, String>> mRequestHeaders = new ArrayList<Pair<String, String>>();

    /**
     * Result of last {@link DownloadThread} started by
     * {@link #startDownloadIfReady(java.util.concurrent.ExecutorService)}.
     */
    private Future<?> mSubmittedTask;

    private DownloadThread mTask;

    private final Context mContext;
    private final SystemFacade mSystemFacade;
    private final DownloadProvider mDownloadProvider;

    private DownloadInfo(Context context, SystemFacade systemFacade,DownloadProvider downloadProvider) {
        mContext = context;
        mSystemFacade = systemFacade;
        mDownloadProvider = downloadProvider;
        mFuzz = Helpers.sRandom.nextInt(1001);
    }


    public Collection<Pair<String, String>> getHeaders() {
        return Collections.unmodifiableList(mRequestHeaders);
    }

    /**
     * Returns the time when a download should be restarted.
     */
    public long restartTime(long now) {
        if (mNumFailed == 0) {
            return now;
        }
        if (mRetryAfter > 0) {
            return mLastMod + mRetryAfter;
        }
        return mLastMod +
                Constants.RETRY_FIRST_DELAY *
                    (1000 + mFuzz) * (1 << (mNumFailed - 1));
    }

    /**
     * Returns whether this download should be enqueued.
     */
    private boolean isReadyToDownload() {
        if (mControl == Downloads.Columns.CONTROL_PAUSED) {
            // the download is paused, so it's not going to start
            return false;
        }
        switch (mStatus) {
            case 0: // status hasn't been initialized yet, this is a new download
            case Downloads.Columns.STATUS_PENDING: // download is explicit marked as ready to start
            case Downloads.Columns.STATUS_RUNNING: // download interrupted (process killed etc) while
                                                // running, without a chance to update the database
                return true;
            case Downloads.Columns.STATUS_WAITING_FOR_NETWORK:
            case Downloads.Columns.STATUS_QUEUED_FOR_WIFI:
                return checkCanUseNetwork() == NetworkState.OK;
            case Downloads.Columns.STATUS_WAITING_TO_RETRY:
                // download was waiting for a delayed restart
                final long now = mSystemFacade.currentTimeMillis();
                return restartTime(now) <= now;
            case Downloads.Columns.STATUS_DEVICE_NOT_FOUND_ERROR:
                // is the media mounted?
                return Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
            case Downloads.Columns.STATUS_INSUFFICIENT_SPACE_ERROR:
                // avoids repetition of retrying download
                return false;
        }
        return false;
    }

    /**
     * Returns whether this download is allowed to use the network.
     */
    public NetworkState checkCanUseNetwork() {
        final NetworkInfo info = mSystemFacade.getActiveNetworkInfo();
        if (info == null || !info.isConnected()) {
            return NetworkState.NO_CONNECTION;
        }
//        if (NetworkInfo.DetailedState.BLOCKED.equals(info.getDetailedState())) {
//            return NetworkState.BLOCKED;
//        }
        return checkIsNetworkTypeAllowed(info.getType());
    }

    /**
     * Check if this download can proceed over the given network type.
     * @param networkType a constant from ConnectivityManager.TYPE_*.
     * @return one of the NETWORK_* constants
     */
    private NetworkState checkIsNetworkTypeAllowed(int networkType) {
        final int flag = translateNetworkTypeToApiFlag(networkType);
        final boolean allowAllNetworkTypes = mAllowedNetworkTypes == ~0;
        if (!allowAllNetworkTypes && (flag & mAllowedNetworkTypes) == 0) {
            return NetworkState.TYPE_DISALLOWED_BY_REQUESTOR;
        }
        if (mTotalBytes <= 0) {
            return NetworkState.OK; // we don't know the size yet
        }
        if (networkType == ConnectivityManager.TYPE_WIFI) {
            return NetworkState.OK; // anything goes over wifi
        }
        return NetworkState.OK;
    }
    /**
     * Translate a ConnectivityManager.TYPE_* constant to the corresponding
     * DownloadManager.Request.NETWORK_* bit flag.
     */
    private int translateNetworkTypeToApiFlag(int networkType) {
        switch (networkType) {
            case ConnectivityManager.TYPE_MOBILE:
                return DownloadManager.Request.NETWORK_MOBILE;

            case ConnectivityManager.TYPE_WIFI:
                return DownloadManager.Request.NETWORK_WIFI;

            case ConnectivityManager.TYPE_BLUETOOTH:
                return DownloadManager.Request.NETWORK_BLUETOOTH;

            default:
                return 0;
        }
    }

    /**
     * If download is ready to start, and isn't already pending or executing,
     * create a {@link DownloadThread} and enqueue it into given
     * {@link java.util.concurrent.Executor}.
     *
     * @return If actively downloading.
     */
    public boolean startDownloadIfReady(ExecutorService executor) {
        synchronized (this) {
            final boolean isReady = isReadyToDownload();
            final boolean isActive = mSubmittedTask != null && !mSubmittedTask.isDone();
            if (isReady && !isActive) {
                if (mStatus != Columns.STATUS_RUNNING) {
                    mStatus = Columns.STATUS_RUNNING;
                    ContentValues values = new ContentValues();
                    values.put(Columns.COLUMN_STATUS, mStatus);
                    mDownloadProvider.update(mId, values, null, null);
                }

                mTask = new DownloadThread(mContext, mSystemFacade,this,mDownloadProvider);
                mSubmittedTask = executor.submit(mTask);
            }
            return isReady;
        }
    }

    /**
     * Return time when this download will be ready for its next action, in
     * milliseconds after given time.
     *
     * @return If {@code 0}, download is ready to proceed immediately. If
     *         {@link Long#MAX_VALUE}, then download has no future actions.
     */
    public long nextActionMillis(long now) {
        if (Downloads.Columns.isStatusCompleted(mStatus)) {
            return Long.MAX_VALUE;
        }
        if (mStatus != Downloads.Columns.STATUS_WAITING_TO_RETRY) {
            return 0;
        }
        long when = restartTime(now);
        if (when <= now) {
            return 0;
        }
        return when - now;
    }


    /**
     * Query and return status of requested download.
     */
    public static int queryDownloadStatus(DownloadProvider downloadProvider,long id) {
        final Cursor cursor = downloadProvider.query(id,new String[] { Downloads.Columns.COLUMN_STATUS }, null, null, null);
        try {
            if (cursor.moveToFirst()) {
                return cursor.getInt(0);
            } else {
                return Downloads.Columns.STATUS_PENDING;
            }
        } finally {
            cursor.close();
        }
    }
}
