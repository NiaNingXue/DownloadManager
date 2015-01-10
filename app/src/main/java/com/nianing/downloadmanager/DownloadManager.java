package com.nianing.downloadmanager;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.CursorWrapper;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Pair;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * The download manager is a system service that handles long-running HTTP downloads. Clients may
 * request that a URI be downloaded to a particular destination file. The download manager will
 * conduct the download in the background, taking care of HTTP interactions and retrying downloads
 * after failures or across connectivity changes and system reboots.
 *
 * Apps that request downloads through this API should register a broadcast receiver for
 * download in a notification or from the downloads UI.
 *
 * Note that the application must have the {@link android.Manifest.permission#INTERNET}
 * permission to use this class.
 */
public class DownloadManager {

    /**
     * An identifier for a particular download, unique across the system.  Clients use this ID to
     * make subsequent calls related to the download.
     */
    public final static String COLUMN_ID = Downloads.Columns._ID;

    /**
     * The client-supplied title for this download.  This will be displayed in system notifications.
     * Defaults to the empty string.
     */
    public final static String COLUMN_TITLE = Downloads.Columns.COLUMN_TITLE;

    /**
     * The client-supplied description of this download.  This will be displayed in system
     * notifications.  Defaults to the empty string.
     */
    public final static String COLUMN_DESCRIPTION = Downloads.Columns.COLUMN_DESCRIPTION;

    /**
     * URI to be downloaded.
     */
    public final static String COLUMN_URI = Downloads.Columns.COLUMN_URI;

    /**
     * Internet Media Type of the downloaded file.  If no value is provided upon creation, this will
     * initially be null and will be filled in based on the server's response once the download has
     * started.
     *
     * @see <a href="http://www.ietf.org/rfc/rfc1590.txt">RFC 1590, defining Media Types</a>
     */
    public final static String COLUMN_MEDIA_TYPE = "media_type";

    /**
     * Total size of the download in bytes.  This will initially be -1 and will be filled in once
     * the download starts.
     */
    public final static String COLUMN_TOTAL_SIZE_BYTES = "total_size";

    /**
     * Uri where downloaded file will be stored.  If a destination is supplied by client, that URI
     * will be used here.  Otherwise, the value will initially be null and will be filled in with a
     * generated URI once the download has started.
     */
    public final static String COLUMN_LOCAL_URI = "local_uri";

    /**
     * The pathname of the file where the download is stored.
     */
    public final static String COLUMN_LOCAL_FILENAME = "local_filename";

    /**
     * Current status of the download, as one of the STATUS_* constants.
     */
    public final static String COLUMN_STATUS = Downloads.Columns.COLUMN_STATUS;

    /**
     * Provides more detail on the status of the download.  Its meaning depends on the value of
     * {@link #COLUMN_STATUS}.
     *
     * When {@link #COLUMN_STATUS} is {@link #STATUS_FAILED}, this indicates the type of error that
     * occurred.  If an HTTP error occurred, this will hold the HTTP status code as defined in RFC
     * 2616.  Otherwise, it will hold one of the ERROR_* constants.
     *
     * When {@link #COLUMN_STATUS} is {@link #STATUS_PAUSED}, this indicates why the download is
     * paused.  It will hold one of the PAUSED_* constants.
     *
     * If {@link #COLUMN_STATUS} is neither {@link #STATUS_FAILED} nor {@link #STATUS_PAUSED}, this
     * column's value is undefined.
     *
     * @see <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec6.html#sec6.1.1">RFC 2616
     * status codes</a>
     */
    public final static String COLUMN_REASON = "reason";

    /**
     * Number of bytes download so far.
     */
    public final static String COLUMN_BYTES_DOWNLOADED_SO_FAR = "bytes_so_far";

    /**
     * Timestamp when the download was last modified, in {@link System#currentTimeMillis
     * System.currentTimeMillis()} (wall clock time in UTC).
     */
    public final static String COLUMN_LAST_MODIFIED_TIMESTAMP = "last_modified_timestamp";


    /**
     * Value of {@link #COLUMN_STATUS} when the download is waiting to start.
     */
    public final static int STATUS_PENDING = 1 << 0;

    /**
     * Value of {@link #COLUMN_STATUS} when the download is currently running.
     */
    public final static int STATUS_RUNNING = 1 << 1;

    /**
     * Value of {@link #COLUMN_STATUS} when the download is waiting to retry or resume.
     */
    public final static int STATUS_PAUSED = 1 << 2;

    /**
     * Value of {@link #COLUMN_STATUS} when the download has successfully completed.
     */
    public final static int STATUS_SUCCESSFUL = 1 << 3;

    /**
     * Value of {@link #COLUMN_STATUS} when the download has failed (and will not be retried).
     */
    public final static int STATUS_FAILED = 1 << 4;

    /**
     * Value of COLUMN_ERROR_CODE when the download has completed with an error that doesn't fit
     * under any other error code.
     */
    public final static int ERROR_UNKNOWN = 1000;

    /**
     * Value of {@link #COLUMN_REASON} when a storage issue arises which doesn't fit under any
     * other error code. Use the more specific {@link #ERROR_INSUFFICIENT_SPACE} and
     * {@link #ERROR_DEVICE_NOT_FOUND} when appropriate.
     */
    public final static int ERROR_FILE_ERROR = 1001;

    /**
     * Value of {@link #COLUMN_REASON} when an HTTP code was received that download manager
     * can't handle.
     */
    public final static int ERROR_UNHANDLED_HTTP_CODE = 1002;

    /**
     * Value of {@link #COLUMN_REASON} when an error receiving or processing data occurred at
     * the HTTP level.
     */
    public final static int ERROR_HTTP_DATA_ERROR = 1004;

    /**
     * Value of {@link #COLUMN_REASON} when there were too many redirects.
     */
    public final static int ERROR_TOO_MANY_REDIRECTS = 1005;

    /**
     * Value of {@link #COLUMN_REASON} when there was insufficient storage space. Typically,
     * this is because the SD card is full.
     */
    public final static int ERROR_INSUFFICIENT_SPACE = 1006;

    /**
     * Value of {@link #COLUMN_REASON} when no external storage device was found. Typically,
     * this is because the SD card is not mounted.
     */
    public final static int ERROR_DEVICE_NOT_FOUND = 1007;

    /**
     * Value of {@link #COLUMN_REASON} when some possibly transient error occurred but we can't
     * resume the download.
     */
    public final static int ERROR_CANNOT_RESUME = 1008;

    /**
     * Value of {@link #COLUMN_REASON} when the requested destination file already exists (the
     * download manager will not overwrite an existing file).
     */
    public final static int ERROR_FILE_ALREADY_EXISTS = 1009;

    /**
     * Value of {@link #COLUMN_REASON} when the download has failed because of
     */
    public final static int ERROR_BLOCKED = 1010;

    /**
     * Value of {@link #COLUMN_REASON} when the download is paused because some network error
     * occurred and the download manager is waiting before retrying the request.
     */
    public final static int PAUSED_WAITING_TO_RETRY = 1;

    /**
     * Value of {@link #COLUMN_REASON} when the download is waiting for network connectivity to
     * proceed.
     */
    public final static int PAUSED_WAITING_FOR_NETWORK = 2;

    /**
     * Value of {@link #COLUMN_REASON} when the download exceeds a size limit for downloads over
     * the mobile network and the download manager is waiting for a Wi-Fi connection to proceed.
     */
    public final static int PAUSED_QUEUED_FOR_WIFI = 3;

    /**
     * Value of {@link #COLUMN_REASON} when the download is paused for some other reason.
     */
    public final static int PAUSED_UNKNOWN = 4;

    /**
     * Broadcast intent action sent by the download manager when a download completes.
     */
    public final static String ACTION_DOWNLOAD_COMPLETE = "android.intent.action.DOWNLOAD_COMPLETE";

    /**
     * Intent extra included with to start DownloadApp in
     * sort-by-size mode.
     */
    public final static String INTENT_EXTRAS_SORT_BY_SIZE =
            "android.app.DownloadManager.extra_sortBySize";

    /**
     * Intent extra included with {@link #ACTION_DOWNLOAD_COMPLETE} intents, indicating the ID (as a
     * long) of the download that just completed.
     */
    public static final String EXTRA_DOWNLOAD_ID = "extra_download_id";


    /**
     * columns to request from DownloadProvider.
     */
    public static final String[] UNDERLYING_COLUMNS = new String[] {
        Downloads.Columns._ID,
        Downloads.Columns._DATA + " AS " + COLUMN_LOCAL_FILENAME,
        Downloads.Columns.COLUMN_DESTINATION,
        Downloads.Columns.COLUMN_TITLE,
        Downloads.Columns.COLUMN_DESCRIPTION,
        Downloads.Columns.COLUMN_URI,
        Downloads.Columns.COLUMN_STATUS,
        Downloads.Columns.COLUMN_MIME_TYPE + " AS " + COLUMN_MEDIA_TYPE,
        Downloads.Columns.COLUMN_TOTAL_BYTES + " AS " + COLUMN_TOTAL_SIZE_BYTES,
        Downloads.Columns.COLUMN_LAST_MODIFICATION + " AS " + COLUMN_LAST_MODIFIED_TIMESTAMP,
        Downloads.Columns.COLUMN_CURRENT_BYTES + " AS " + COLUMN_BYTES_DOWNLOADED_SO_FAR,
        /* add the following 'computed' columns to the cursor.
         * they are not 'returned' by the database, but their inclusion
         * eliminates need to have lot of methods in CursorTranslator
         */
        "'placeholder' AS " + COLUMN_LOCAL_URI,
        "'placeholder' AS " + COLUMN_REASON
    };

    public static class Request {
        /**
         * Bit flag for {@link #setAllowedNetworkTypes} corresponding to
         * {@link android.net.ConnectivityManager#TYPE_MOBILE}.
         */
        public static final int NETWORK_MOBILE = 1 << 0;

        /**
         * Bit flag for {@link #setAllowedNetworkTypes} corresponding to
         * {@link android.net.ConnectivityManager#TYPE_WIFI}.
         */
        public static final int NETWORK_WIFI = 1 << 1;

        /**
         * Bit flag for {@link #setAllowedNetworkTypes} corresponding to
         * {@link android.net.ConnectivityManager#TYPE_BLUETOOTH}.
         */
        public static final int NETWORK_BLUETOOTH = 1 << 2;

        private Uri mUri;
        private String mPath;
        private List<Pair<String, String>> mRequestHeaders = new ArrayList<Pair<String, String>>();
        private CharSequence mFileName;
        private CharSequence mTitle;
        private CharSequence mDescription;
        private int mAllowedNetworkTypes = ~0; // default to all network types allowed

        /**
         * @param uri the HTTP URI to download.
         */
        public Request(Uri uri) {
            if (uri == null) {
                throw new NullPointerException();
            }
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equals("http") && !scheme.equals("https"))) {
                throw new IllegalArgumentException("Can only download HTTP/HTTPS URIs: " + uri);
            }
            mUri = uri;
        }

        Request(String uriString) {
            mUri = Uri.parse(uriString);
        }

        public Request setDestinationPath( String path) {
            mPath = path;
            final File file = new File(path);
            if (file.exists()) {
                if (file.isDirectory()) {
                    throw new IllegalStateException(file.getAbsolutePath() +
                            " already exists and is a directory");
                }
            }
            return this;
        }


        /**
         * Add an HTTP header to be included with the download request.  The header will be added to
         * the end of the list.
         * @param header HTTP header name
         * @param value header value
         * @return this object
         * @see <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec4.html#sec4.2">HTTP/1.1
         *      Message Headers</a>
         */
        public Request addRequestHeader(String header, String value) {
            if (header == null) {
                throw new NullPointerException("header cannot be null");
            }
            if (header.contains(":")) {
                throw new IllegalArgumentException("header may not contain ':'");
            }
            if (value == null) {
                value = "";
            }
            mRequestHeaders.add(Pair.create(header, value));
            return this;
        }

        /**
         * Set the title of this download, to be displayed in notifications (if enabled).  If no
         * title is given, a default one will be assigned based on the download filename, once the
         * download starts.
         * @return this object
         */
        public Request setTitle(CharSequence title) {
            mTitle = title;
            return this;
        }

        public Request setFileName(CharSequence filename) {
            mFileName = filename;
            return this;
        }

        /**
         * Set a description of this download, to be displayed in notifications (if enabled)
         * @return this object
         */
        public Request setDescription(CharSequence description) {
            mDescription = description;
            return this;
        }

        /**
         * Restrict the types of networks over which this download may proceed.
         * By default, all network types are allowed.
         *
         * @param flags any combination of the NETWORK_* bit flags.
         * @return this object
         */
        public Request setAllowedNetworkTypes(int flags) {
            mAllowedNetworkTypes = flags;
            return this;
        }


        /**
         * @return ContentValues to be passed to DownloadProvider.insert()
         */
        ContentValues toContentValues(String packageName) {
            ContentValues values = new ContentValues();
            assert mUri != null;
            values.put(Downloads.Columns.COLUMN_URI, mUri.toString());
            values.put(Downloads.Columns.COLUMN_PACKAGE, packageName);

            values.put(Downloads.Columns.COLUMN_DESTINATION,mPath);
            if (!mRequestHeaders.isEmpty()) {
                encodeHttpHeaders(values);
            }
            putIfNonNull(values, Downloads.Columns.COLUMN_TITLE, mTitle);
            putIfNonNull(values, Downloads.Columns.COLUMN_DESCRIPTION, mDescription);

            values.put(Downloads.Columns.COLUMN_ALLOWED_NETWORK_TYPES, mAllowedNetworkTypes);
            return values;
        }

        private void encodeHttpHeaders(ContentValues values) {
            int index = 0;
            for (Pair<String, String> header : mRequestHeaders) {
                String headerString = header.first + ": " + header.second;
                values.put(Downloads.Columns.RequestHeaders.INSERT_KEY_PREFIX + index, headerString);
                index++;
            }
        }

        private void putIfNonNull(ContentValues contentValues, String key, Object value) {
            if (value != null) {
                contentValues.put(key, value.toString());
            }
        }
    }

    /**
     * This class may be used to filter download manager queries.
     */
    public static class Query {
        /**
         * Constant for use with {@link #orderBy}
         * @hide
         */
        public static final int ORDER_ASCENDING = 1;

        /**
         * Constant for use with {@link #orderBy}
         * @hide
         */
        public static final int ORDER_DESCENDING = 2;

        private long[] mIds = null;
        private Integer mStatusFlags = null;
        private String mOrderByColumn = Downloads.Columns.COLUMN_LAST_MODIFICATION;
        private int mOrderDirection = ORDER_DESCENDING;

        /**
         * Include only the downloads with the given IDs.
         * @return this object
         */
        public Query setFilterById(long... ids) {
            mIds = ids;
            return this;
        }

        /**
         * Include only downloads with status matching any the given status flags.
         * @param flags any combination of the STATUS_* bit flags
         * @return this object
         */
        public Query setFilterByStatus(int flags) {
            mStatusFlags = flags;
            return this;
        }


        /**
         * Change the sort order of the returned Cursor.
         *
         * @param column one of the COLUMN_* constants; currently, only
         *         {@link #COLUMN_LAST_MODIFIED_TIMESTAMP} and {@link #COLUMN_TOTAL_SIZE_BYTES} are
         *         supported.
         * @param direction either {@link #ORDER_ASCENDING} or {@link #ORDER_DESCENDING}
         * @return this object
         * @hide
         */
        public Query orderBy(String column, int direction) {
            if (direction != ORDER_ASCENDING && direction != ORDER_DESCENDING) {
                throw new IllegalArgumentException("Invalid direction: " + direction);
            }

            if (column.equals(COLUMN_LAST_MODIFIED_TIMESTAMP)) {
                mOrderByColumn = Downloads.Columns.COLUMN_LAST_MODIFICATION;
            } else if (column.equals(COLUMN_TOTAL_SIZE_BYTES)) {
                mOrderByColumn = Downloads.Columns.COLUMN_TOTAL_BYTES;
            } else {
                throw new IllegalArgumentException("Cannot order by " + column);
            }
            mOrderDirection = direction;
            return this;
        }

        /**
         * Run this query using the given ContentResolver.
         * @param projection the projection to pass to ContentResolver.query()
         * @return the Cursor returned by ContentResolver.query()
         */
        Cursor runQuery(DownloadProvider provider, String[] projection) {
            List<String> selectionParts = new ArrayList<String>();
            String[] selectionArgs = null;

            if (mIds != null) {
                selectionParts.add(getWhereClauseForIds(mIds));
                selectionArgs = getWhereArgsForIds(mIds);
            }

            if (mStatusFlags != null) {
                List<String> parts = new ArrayList<String>();
                if ((mStatusFlags & STATUS_PENDING) != 0) {
                    parts.add(statusClause("=", Downloads.Columns.STATUS_PENDING));
                }
                if ((mStatusFlags & STATUS_RUNNING) != 0) {
                    parts.add(statusClause("=", Downloads.Columns.STATUS_RUNNING));
                }
                if ((mStatusFlags & STATUS_PAUSED) != 0) {
                    parts.add(statusClause("=", Downloads.Columns.STATUS_PAUSED_BY_APP));
                    parts.add(statusClause("=", Downloads.Columns.STATUS_WAITING_TO_RETRY));
                    parts.add(statusClause("=", Downloads.Columns.STATUS_WAITING_FOR_NETWORK));
                    parts.add(statusClause("=", Downloads.Columns.STATUS_QUEUED_FOR_WIFI));
                }
                if ((mStatusFlags & STATUS_SUCCESSFUL) != 0) {
                    parts.add(statusClause("=", Downloads.Columns.STATUS_SUCCESS));
                }
                if ((mStatusFlags & STATUS_FAILED) != 0) {
                    parts.add("(" + statusClause(">=", 400)
                              + " AND " + statusClause("<", 600) + ")");
                }
                selectionParts.add(joinStrings(" OR ", parts));
            }

            // only return rows which are not marked 'deleted = 1'
            selectionParts.add(Downloads.Columns.COLUMN_DELETED + " != '1'");

            String selection = joinStrings(" AND ", selectionParts);
            String orderDirection = (mOrderDirection == ORDER_ASCENDING ? "ASC" : "DESC");
            String orderBy = mOrderByColumn + " " + orderDirection;

            return provider.query(-1, projection, selection, selectionArgs, orderBy);
        }

        private String joinStrings(String joiner, Iterable<String> parts) {
            StringBuilder builder = new StringBuilder();
            boolean first = true;
            for (String part : parts) {
                if (!first) {
                    builder.append(joiner);
                }
                builder.append(part);
                first = false;
            }
            return builder.toString();
        }

        private String statusClause(String operator, int value) {
            return Downloads.Columns.COLUMN_STATUS + operator + "'" + value + "'";
        }
    }

    private String mPackageName;
    private DownloadProvider mDownloadProvider;
    private Context mContext;
    private static DownloadManager mDownloadManager;

    /**
     */
    private DownloadManager(Context context) {
        mContext = context;
        mDownloadProvider = new DownloadProvider(context);
    }

    public static DownloadManager getInstance(Context context){
        if(mDownloadManager==null){
            mDownloadManager  = new DownloadManager(context);
        }
        return mDownloadManager;
    }

    /**
     * Enqueue a new download.  The download will start automatically once the download manager is
     * ready to execute it and connectivity is available.
     *
     * @param request the parameters specifying this download
     * @return an ID for the download, unique across the system.  This ID is used to make future
     * calls related to this download.
     */
    public long enqueue(Request request) {
        ContentValues values = request.toContentValues(mPackageName);
        long id = mDownloadProvider.insert(values);
        return id;
    }

    /**
     * Marks the specified download as 'to be deleted'. This is done when a completed download
     * is to be removed but the row was stored without enough info to delete the corresponding
     * metadata from Mediaprovider database. Actual cleanup of this row is done in DownloadService.
     *
     * @param ids the IDs of the downloads to be marked 'deleted'
     * @return the number of downloads actually updated
     * @hide
     */
    public int markRowDeleted(long... ids) {
        if (ids == null || ids.length == 0) {
            // called with nothing to remove!
            throw new IllegalArgumentException("input param 'ids' can't be null");
        }
        ContentValues values = new ContentValues();
        values.put(Downloads.Columns.COLUMN_DELETED, 1);
        // if only one id is passed in, then include it in the uri itself.
        // this will eliminate a full database scan in the download service.
        if (ids.length == 1) {
            return mDownloadProvider.update(ids[0], values,null, null);
        } 
        return mDownloadProvider.update(-1,values, getWhereClauseForIds(ids),getWhereArgsForIds(ids));
    }

    /**
     * Cancel downloads and remove them from the download manager.  Each download will be stopped if
     * it was running, and it will no longer be accessible through the download manager.
     * If there is a downloaded file, partial or complete, it is deleted.
     *
     * @param ids the IDs of the downloads to remove
     * @return the number of downloads actually removed
     */
    public int remove(long... ids) {
        return markRowDeleted(ids);
    }

    public int stop(long... ids){
        if (ids == null || ids.length == 0) {
            // called with nothing to remove!
            throw new IllegalArgumentException("input param 'ids' can't be null");
        }
        ContentValues values = new ContentValues();
        values.put(Downloads.Columns.COLUMN_CONTROL, Downloads.Columns.CONTROL_PAUSED);
        // if only one id is passed in, then include it in the uri itself.
        // this will eliminate a full database scan in the download service.
        if (ids.length == 1) {
            return mDownloadProvider.update(ids[0], values,null, null);
        }
        return 0;
    }

    public int start(long... ids){
        if (ids == null || ids.length == 0) {
            // called with nothing to remove!
            throw new IllegalArgumentException("input param 'ids' can't be null");
        }
        ContentValues values = new ContentValues();
        values.put(Downloads.Columns.COLUMN_CONTROL, Downloads.Columns.CONTROL_RUN);
        values.put(Downloads.Columns.COLUMN_STATUS, Downloads.Columns.STATUS_PENDING);
        // if only one id is passed in, then include it in the uri itself.
        // this will eliminate a full database scan in the download service.
        if (ids.length == 1) {
            return mDownloadProvider.update(ids[0], values,null, null);
        }
        return 0;
    }
    /**
     * Query the download manager about downloads that have been requested.
     * @param query parameters specifying filters for this query
     * @return a Cursor over the result set of downloads, with columns consisting of all the
     * COLUMN_* constants.
     */
    public Cursor query(Query query) {
        Cursor underlyingCursor = query.runQuery(mDownloadProvider, UNDERLYING_COLUMNS);
        if (underlyingCursor == null) {
            return null;
        }
        return new CursorTranslator(underlyingCursor);
    }


    /**
     * Restart the given downloads, which must have already completed (successfully or not).  This
     * method will only work when called from within the download manager's process.
     * @param ids the IDs of the downloads
     */
    public void restartDownload(long... ids) {
        Cursor cursor = query(new Query().setFilterById(ids));
        try {
            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                int status = cursor.getInt(cursor.getColumnIndex(COLUMN_STATUS));
                if (status != STATUS_SUCCESSFUL && status != STATUS_FAILED) {
                    throw new IllegalArgumentException("Cannot restart incomplete download: "
                            + cursor.getLong(cursor.getColumnIndex(COLUMN_ID)));
                }
            }
        } finally {
            cursor.close();
        }

        ContentValues values = new ContentValues();
        values.put(Downloads.Columns.COLUMN_CURRENT_BYTES, 0);
        values.put(Downloads.Columns.COLUMN_TOTAL_BYTES, -1);
        values.putNull(Downloads.Columns._DATA);
        values.put(Downloads.Columns.COLUMN_STATUS, Downloads.Columns.STATUS_PENDING);
        values.put(Downloads.Columns.COLUMN_FAILED_CONNECTIONS, 0);
        mDownloadProvider.update(-1, values, getWhereClauseForIds(ids), getWhereArgsForIds(ids));
    }


    /**
     * Adds a file to the downloads database system, so it could appear in Downloads App
     * (and thus become eligible for management by the Downloads App).
     * <p>
     * It is helpful to make the file scannable by MediaScanner by setting the param
     * isMediaScannerScannable to true. It makes the file visible in media managing
     * applications such as Gallery App, which could be a useful purpose of using this API.
     *
     * @param title the title that would appear for this file in Downloads App.
     * @param description the description that would appear for this file in Downloads App.
     * @param isMediaScannerScannable true if the file is to be scanned by MediaScanner. Files
     * scanned by MediaScanner appear in the applications used to view media (for example,
     * Gallery app).
     * @param mimeType mimetype of the file.
     * @param path absolute pathname to the file. The file should be world-readable, so that it can
     * be managed by the Downloads App and any other app that is used to read it (for example,
     * Gallery app to display the file, if the file contents represent a video/image).
     * @param length length of the downloaded file
     * @param showNotification true if a notification is to be sent, false otherwise
     * @return  an ID for the download entry added to the downloads app, unique across the system
     * This ID is used to make future calls related to this download.
     */
    public long addCompletedDownload(String title, String description,
            boolean isMediaScannerScannable, String mimeType, String path, long length,
            boolean showNotification) {
        return addCompletedDownload(title, description, isMediaScannerScannable, mimeType, path,
                length, showNotification, false);
    }

    public long addCompletedDownload(String title, String description,
            boolean isMediaScannerScannable, String mimeType, String path, long length,
            boolean showNotification, boolean allowWrite) {
        // make sure the input args are non-null/non-zero
        validateArgumentIsNonEmpty("title", title);
        validateArgumentIsNonEmpty("description", description);
        validateArgumentIsNonEmpty("path", path);
        validateArgumentIsNonEmpty("mimeType", mimeType);
        if (length < 0) {
            throw new IllegalArgumentException(" invalid value for param: totalBytes");
        }

        // if there is already an entry with the given path name in downloads.db, return its id
        Request request = new Request(NON_DOWNLOADMANAGER_DOWNLOAD)
                .setTitle(title)
                .setDescription(description);
        ContentValues values = request.toContentValues(null);
//        values.put(Downloads.Columns.COLUMN_DESTINATION,
//                Downloads.Columns.DESTINATION_NON_DOWNLOADMANAGER_DOWNLOAD);
        values.put(Downloads.Columns._DATA, path);
        values.put(Downloads.Columns.COLUMN_STATUS, Downloads.Columns.STATUS_SUCCESS);
        values.put(Downloads.Columns.COLUMN_TOTAL_BYTES, length);
        values.put(Downloads.Columns.COLUMN_ALLOW_WRITE, allowWrite ? 1 : 0);
        long id = mDownloadProvider.insert(values);
        if (id == -1) {
            return -1;
        }
        return id;
    }

    private static final String NON_DOWNLOADMANAGER_DOWNLOAD =
            "non-dwnldmngr-download-dont-retry2download";

    private static void validateArgumentIsNonEmpty(String paramName, String val) {
        if (TextUtils.isEmpty(val)) {
            throw new IllegalArgumentException(paramName + " can't be null");
        }
    }


    /**
     * Get a parameterized SQL WHERE clause to select a bunch of IDs.
     */
    static String getWhereClauseForIds(long[] ids) {
        StringBuilder whereClause = new StringBuilder();
        whereClause.append("(");
        for (int i = 0; i < ids.length; i++) {
            if (i > 0) {
                whereClause.append("OR ");
            }
            whereClause.append(Downloads.Columns._ID);
            whereClause.append(" = ? ");
        }
        whereClause.append(")");
        return whereClause.toString();
    }

    /**
     * Get the selection args for a clause returned by {@link #getWhereClauseForIds(long[])}.
     */
    static String[] getWhereArgsForIds(long[] ids) {
        String[] whereArgs = new String[ids.length];
        for (int i = 0; i < ids.length; i++) {
            whereArgs[i] = Long.toString(ids[i]);
        }
        return whereArgs;
    }

    /**
     * This class wraps a cursor returned by DownloadProvider -- the "underlying cursor" -- and
     * presents a different set of columns, those defined in the DownloadManager.COLUMN_* constants.
     * Some columns correspond directly to underlying values while others are computed from
     * underlying data.
     */
    private static class CursorTranslator extends CursorWrapper {

        public CursorTranslator(Cursor cursor) {
            super(cursor);
        }

        @Override
        public int getInt(int columnIndex) {
            return (int) getLong(columnIndex);
        }

        @Override
        public long getLong(int columnIndex) {
            if (getColumnName(columnIndex).equals(COLUMN_REASON)) {
                return getReason(super.getInt(getColumnIndex(Downloads.Columns.COLUMN_STATUS)));
            } else if (getColumnName(columnIndex).equals(COLUMN_STATUS)) {
                return translateStatus(super.getInt(getColumnIndex(Downloads.Columns.COLUMN_STATUS)));
            } else {
                return super.getLong(columnIndex);
            }
        }

        @Override
        public String getString(int columnIndex) {
            return (getColumnName(columnIndex).equals(COLUMN_LOCAL_URI)) ? getLocalUri() :
                    super.getString(columnIndex);
        }

        private String getLocalUri() {
            long destinationType = getLong(getColumnIndex(Downloads.Columns.COLUMN_DESTINATION));
/*
            if (destinationType == Downloads.Columns.DESTINATION_FILE_URI ||
                    destinationType == Downloads.Columns.DESTINATION_EXTERNAL ||
                    destinationType == Downloads.Columns.DESTINATION_NON_DOWNLOADMANAGER_DOWNLOAD) {
                String localPath = getString(getColumnIndex(COLUMN_LOCAL_FILENAME));
                if (localPath == null) {
                    return null;
                }
                return Uri.fromFile(new File(localPath)).toString();
            }
*/

            // return content URI for cache download
            long downloadId = getLong(getColumnIndex(Downloads.Columns._ID));
            return downloadId+"";
        }

        private long getReason(int status) {
            switch (translateStatus(status)) {
                case STATUS_FAILED:
                    return getErrorCode(status);

                case STATUS_PAUSED:
                    return getPausedReason(status);

                default:
                    return 0; // arbitrary value when status is not an error
            }
        }

        private long getPausedReason(int status) {
            switch (status) {
                case Downloads.Columns.STATUS_WAITING_TO_RETRY:
                    return PAUSED_WAITING_TO_RETRY;

                case Downloads.Columns.STATUS_WAITING_FOR_NETWORK:
                    return PAUSED_WAITING_FOR_NETWORK;

                case Downloads.Columns.STATUS_QUEUED_FOR_WIFI:
                    return PAUSED_QUEUED_FOR_WIFI;

                default:
                    return PAUSED_UNKNOWN;
            }
        }

        private long getErrorCode(int status) {
            if ((400 <= status && status < Downloads.Columns.MIN_ARTIFICIAL_ERROR_STATUS)
                    || (500 <= status && status < 600)) {
                // HTTP status code
                return status;
            }

            switch (status) {
                case Downloads.Columns.STATUS_FILE_ERROR:
                    return ERROR_FILE_ERROR;

                case Downloads.Columns.STATUS_UNHANDLED_HTTP_CODE:
                case Downloads.Columns.STATUS_UNHANDLED_REDIRECT:
                    return ERROR_UNHANDLED_HTTP_CODE;

                case Downloads.Columns.STATUS_HTTP_DATA_ERROR:
                    return ERROR_HTTP_DATA_ERROR;

                case Downloads.Columns.STATUS_TOO_MANY_REDIRECTS:
                    return ERROR_TOO_MANY_REDIRECTS;

                case Downloads.Columns.STATUS_INSUFFICIENT_SPACE_ERROR:
                    return ERROR_INSUFFICIENT_SPACE;

                case Downloads.Columns.STATUS_DEVICE_NOT_FOUND_ERROR:
                    return ERROR_DEVICE_NOT_FOUND;

                case Downloads.Columns.STATUS_CANNOT_RESUME:
                    return ERROR_CANNOT_RESUME;

                case Downloads.Columns.STATUS_FILE_ALREADY_EXISTS_ERROR:
                    return ERROR_FILE_ALREADY_EXISTS;

                default:
                    return ERROR_UNKNOWN;
            }
        }

        private int translateStatus(int status) {
            switch (status) {
                case Downloads.Columns.STATUS_PENDING:
                    return STATUS_PENDING;

                case Downloads.Columns.STATUS_RUNNING:
                    return STATUS_RUNNING;

                case Downloads.Columns.STATUS_PAUSED_BY_APP:
                case Downloads.Columns.STATUS_WAITING_TO_RETRY:
                case Downloads.Columns.STATUS_WAITING_FOR_NETWORK:
                case Downloads.Columns.STATUS_QUEUED_FOR_WIFI:
                    return STATUS_PAUSED;

                case Downloads.Columns.STATUS_SUCCESS:
                    return STATUS_SUCCESSFUL;

                default:
                    assert Downloads.Columns.isStatusError(status);
                    return STATUS_FAILED;
            }
        }
    }
}
