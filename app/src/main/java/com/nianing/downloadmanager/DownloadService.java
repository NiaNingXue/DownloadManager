package com.nianing.downloadmanager;

import android.app.Service;
import android.content.Intent;
import android.database.Cursor;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.Process;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static android.text.format.DateUtils.MINUTE_IN_MILLIS;

public class DownloadService extends Service {

    private static final boolean DEBUG_LIFECYCLE = false;
    private static final String TAG = "DownloadService";

    SystemFacade mSystemFacade;


    /** Observer to get notified when the content observer's data changes */
    private DownloadManagerContentObserver mObserver;

    /** Class to handle Notification Manager updates */

    /**
     * The Service's view of the list of downloads, mapping download IDs to the corresponding info
     * object. This is kept independently from the content provider, and the Service only initiates
     * downloads based on this data, so that it can deal with situation where the data in the
     * content provider changes or disappears.
     */
    private final Map<Long, DownloadInfo> mDownloads = new HashMap();

    private final ExecutorService mExecutor = buildDownloadExecutor();

    private static ExecutorService buildDownloadExecutor() {
        final int maxConcurrent = 5;
        // Create a bounded thread pool for executing downloads; it creates
        // threads as needed (up to maximum) and reclaims them when finished.
        final ThreadPoolExecutor executor = new ThreadPoolExecutor(
                maxConcurrent, maxConcurrent, 10, TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>());
        return executor;
    }


    private HandlerThread mUpdateThread;
    private Handler mUpdateHandler;
    private DownloadProvider mDownloadProvider;

    /**
     * Receives notifications when the data in the content provider changes
     */
    private class DownloadManagerContentObserver  implements DownloadObserver{
        public DownloadManagerContentObserver() {
        }

        @Override
        public void onChange() {
            enqueueUpdate();
        }
    }

    /**
     * Returns an IBinder instance when someone wants to connect to this
     * service. Binding to this service is not allowed.
     * @throws UnsupportedOperationException
     */
    @Override
    public IBinder onBind(Intent i) {
        throw new UnsupportedOperationException("Cannot bind to Download Manager Service");
    }

    /**
     * Initializes the service when it is first created
     */
    @Override
    public void onCreate() {
        super.onCreate();

        if (mSystemFacade == null) {
            mSystemFacade = new SystemFacade(this);
        }
        mUpdateThread = new HandlerThread(TAG + "-UpdateThread");
        mUpdateThread.start();
        mUpdateHandler = new Handler(mUpdateThread.getLooper(), mUpdateCallback);

        mDownloadProvider = new DownloadProvider(this);
        mObserver = new DownloadManagerContentObserver();
        mDownloadProvider.setContentObserver(mObserver);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int returnValue = super.onStartCommand(intent, flags, startId);
        Log.v(Constants.TAG, "Service onStart");
        enqueueUpdate();
        return returnValue;
    }


    @Override
    public void onDestroy() {
        mUpdateThread.quit();
        super.onDestroy();
    }

    /**
     * Enqueue an {@link #updateLocked()} pass to occur in future.
     */
    private void enqueueUpdate() {
        mUpdateHandler.removeMessages(MSG_UPDATE);
        mUpdateHandler.obtainMessage(MSG_UPDATE).sendToTarget();
    }

    /**
     * Enqueue an {@link #updateLocked()} pass to occur after delay, usually to
     * catch any finished operations that didn't trigger an update pass.
     */
    private void enqueueFinalUpdate() {
        mUpdateHandler.removeMessages(MSG_FINAL_UPDATE);
        mUpdateHandler.sendMessageDelayed(mUpdateHandler.obtainMessage(MSG_FINAL_UPDATE),5 * MINUTE_IN_MILLIS);
    }

    private static final int MSG_UPDATE = 1;
    private static final int MSG_FINAL_UPDATE = 2;

    private Handler.Callback mUpdateCallback = new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            // Since database is current source of truth, our "active" status
            // depends on database state. We always get one final update pass
            // once the real actions have finished and persisted their state.
            final boolean isActive;
            synchronized (mDownloads) {
                isActive = updateLocked();
            }

            if (isActive) {
                // Still doing useful work, keep service alive. These active
                // tasks will trigger another update pass when they're finished.

                // Enqueue delayed update pass to catch finished operations that
                // didn't trigger an update pass; these are bugs.
                enqueueFinalUpdate();

            } else {
                // No active tasks, and any pending update messages can be
                // ignored, since any updates important enough to initiate tasks
                // will always be delivered with a new startId.

                //mUpdateThread.quit();
                Log.i(TAG,"no active task");
            }
            return true;
        }
    };

    /**
     * Update {@link #mDownloads} to match {@link DownloadProvider} state.
     * Depending on current download state it may enqueue {@link DownloadThread}
     * notifications, and/or schedule future actions with {@link android.app.AlarmManager}.
     * <p>
     * Should only be called from {@link #mUpdateThread} as after being
     * requested through {@link #enqueueUpdate()}.
     *
     * @return If there are active tasks being processed, as of the database
     *         snapshot taken in this update.
     */
    private boolean updateLocked() {
        final long now = mSystemFacade.currentTimeMillis();

        boolean isActive = false;
        long nextActionMillis = Long.MAX_VALUE;

        final Set<Long> staleIds = new HashSet(mDownloads.keySet());

        final Cursor cursor = mDownloadProvider.query(-1,null, null, null, null);
        try {
            final DownloadInfo.Reader reader = new DownloadInfo.Reader(cursor);
            final int idColumn = cursor.getColumnIndexOrThrow(Downloads.Columns._ID);
            while (cursor.moveToNext()) {
                final long id = cursor.getLong(idColumn);
                staleIds.remove(id);

                DownloadInfo info = mDownloads.get(id);
                if (info != null) {
                    updateDownload(reader, info, now);
                } else {
                    info = insertDownloadLocked(reader, now);
                }

                if (info.mDeleted) {
                    deleteFileIfExists(info.mDestination);
                    mDownloadProvider.delete(info.mId, null, null);

                } else {
                    // Kick off download task if ready
                    final boolean activeDownload = info.startDownloadIfReady(mExecutor);
                    isActive |= activeDownload;
                }

                // Keep track of nearest next action
                nextActionMillis = Math.min(info.nextActionMillis(now), nextActionMillis);
            }
        } finally {
            cursor.close();
        }

        // Clean up stale downloads that disappeared
        for (Long id : staleIds) {
            deleteDownloadLocked(id);
        }
        Intent intent = new Intent();
        intent.setAction(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        intent.putExtra("Download",(HashMap)mDownloads);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        return isActive;
    }

    /**
     * Keeps a local copy of the info about a download, and initiates the
     * download if appropriate.
     */
    private DownloadInfo insertDownloadLocked(DownloadInfo.Reader reader, long now) {
        final DownloadInfo info = reader.newDownloadInfo(this, mSystemFacade,mDownloadProvider);
        mDownloads.put(info.mId, info);
        return info;
    }

    /**
     * Updates the local copy of the info about a download.
     */
    private void updateDownload(DownloadInfo.Reader reader, DownloadInfo info, long now) {
        reader.updateFromDatabase(info);
    }

    /**
     * Removes the local copy of the info about a download.
     */
    private void deleteDownloadLocked(long id) {
        DownloadInfo info = mDownloads.get(id);
        if (info.mStatus == Downloads.Columns.STATUS_RUNNING) {
            info.mStatus = Downloads.Columns.STATUS_CANCELED;
        }
        if (info.mFileName != null) {
            if (Constants.LOGVV) {
                Log.d(TAG, "deleteDownloadLocked() deleting " + info.mFileName);
            }
            deleteFileIfExists(info.mFileName);
        }
        mDownloads.remove(info.mId);
    }

    private void deleteFileIfExists(String path) {
        if (!TextUtils.isEmpty(path)) {
            if (Constants.LOGVV) {
                Log.d(TAG, "deleteFileIfExists() deleting " + path);
            }
            final File file = new File(path);
            if (file.exists() && !file.delete()) {
                Log.w(TAG, "file: '" + path + "' couldn't be deleted");
            }
        }
    }
}
