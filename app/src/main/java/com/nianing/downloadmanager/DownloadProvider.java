package com.nianing.downloadmanager;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Allows application to interact with the download manager.
 */
public final class DownloadProvider{
    /** Database filename */
    private static final String DB_NAME = "downloads.db";
    /** Current database version */
    private static final int DB_VERSION = 1;
    /** Name of table in the database */
    private static final String DB_TABLE = "downloads";


    private static final String[] sAppReadableColumnsArray = new String[] {
        Downloads.Columns._ID,
        Downloads.Columns.COLUMN_APP_DATA,
        Downloads.Columns._DATA,
        Downloads.Columns.COLUMN_DESTINATION,
        Downloads.Columns.COLUMN_CONTROL,
        Downloads.Columns.COLUMN_STATUS,
        Downloads.Columns.COLUMN_LAST_MODIFICATION,
        Downloads.Columns.COLUMN_PACKAGE,
        Downloads.Columns.COLUMN_TOTAL_BYTES,
        Downloads.Columns.COLUMN_CURRENT_BYTES,
        Downloads.Columns.COLUMN_TITLE,
        Downloads.Columns.COLUMN_DESCRIPTION,
        Downloads.Columns.COLUMN_URI,
        Downloads.Columns.COLUMN_DELETED
    };

    private static final HashSet<String> sAppReadableColumnsSet;

    static {
        sAppReadableColumnsSet = new HashSet<String>();
        for (int i = 0; i < sAppReadableColumnsArray.length; ++i) {
            sAppReadableColumnsSet.add(sAppReadableColumnsArray[i]);
        }
    }

    /** The database that lies underneath this content provider */
    private SQLiteOpenHelper mOpenHelper = null;

    SystemFacade mSystemFacade;

    /**
     * This class encapsulates a SQL where clause and its parameters.  It makes it possible for
     * to return both pieces of information, and provides some utility logic to ease piece-by-piece
     * construction of selections.
     */
    private static class SqlSelection {
        public StringBuilder mWhereClause = new StringBuilder();
        public List<String> mParameters = new ArrayList<String>();

        public <T> void appendClause(String newClause, final T... parameters) {
            if (TextUtils.isEmpty(newClause)) {
                return;
            }
            if (mWhereClause.length() != 0) {
                mWhereClause.append(" AND ");
            }
            mWhereClause.append("(");
            mWhereClause.append(newClause);
            mWhereClause.append(")");
            if (parameters != null) {
                for (Object parameter : parameters) {
                    mParameters.add(parameter.toString());
                }
            }
        }

        public String getSelection() {
            return mWhereClause.toString();
        }

        public String[] getParameters() {
            String[] array = new String[mParameters.size()];
            return mParameters.toArray(array);
        }
    }

    /**
     * Creates and updated database on demand when opening it.
     * Helper class to create database the first time the provider is
     * initialized and upgrade it when a new version of the provider needs
     * an updated version of the database.
     */
    private final class DatabaseHelper extends SQLiteOpenHelper {
        public DatabaseHelper(final Context context) {
            super(context, DB_NAME, null, DB_VERSION);
        }

        /**
         * Creates database the first time we try to open it.
         */
        @Override
        public void onCreate(final SQLiteDatabase db) {
            createDownloadsTable(db);
            createHeadersTable(db);
        }

        @Override
        public void onUpgrade(final SQLiteDatabase db, int oldV, final int newV) {
        }

        /**
         * Add a column to a table using ALTER TABLE.
         * @param dbTable name of the table
         * @param columnName name of the column to add
         * @param columnDefinition SQL for the column definition
         */
        private void addColumn(SQLiteDatabase db, String dbTable, String columnName,
                               String columnDefinition) {
            db.execSQL("ALTER TABLE " + dbTable + " ADD COLUMN " + columnName + " "
                       + columnDefinition);
        }

        /**
         * Creates the table that'll hold the download information.
         */
        private void createDownloadsTable(SQLiteDatabase db) {
            try {
                db.execSQL("DROP TABLE IF EXISTS " + DB_TABLE);
                db.execSQL("CREATE TABLE " + DB_TABLE + "(" +
                        Downloads.Columns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                        Downloads.Columns.COLUMN_URI + " TEXT, " +
                        Constants.RETRY_AFTER_X_REDIRECT_COUNT + " INTEGER, " +
                        Downloads.Columns.COLUMN_APP_DATA + " TEXT, " +
                        Downloads.Columns._DATA + " TEXT, " +
                        Downloads.Columns.COLUMN_MIME_TYPE + " TEXT, " +
                        Downloads.Columns.COLUMN_DESTINATION + " TEXT, " +
                        Downloads.Columns.COLUMN_CONTROL + " INTEGER, " +
                        Downloads.Columns.COLUMN_STATUS + " INTEGER, " +
                        Downloads.Columns.COLUMN_FAILED_CONNECTIONS + " INTEGER, " +
                        Downloads.Columns.COLUMN_LAST_MODIFICATION + " BIGINT, " +
                        Downloads.Columns.COLUMN_PACKAGE + " TEXT, " +
                        Downloads.Columns.COLUMN_EXTRAS + " TEXT, " +
                        Downloads.Columns.COLUMN_COOKIE_DATA + " TEXT, " +
                        Downloads.Columns.COLUMN_USER_AGENT + " TEXT, " +
                        Downloads.Columns.COLUMN_REFERER + " TEXT, " +
                        Downloads.Columns.COLUMN_TOTAL_BYTES + " INTEGER, " +
                        Downloads.Columns.COLUMN_CURRENT_BYTES + " INTEGER, " +
                        Constants.ETAG + " TEXT, " +
                        Downloads.Columns.COLUMN_ERROR_MSG + " TEXT, " +
                        Downloads.Columns.COLUMN_TITLE + " TEXT, " +
                        Downloads.Columns.COLUMN_DESCRIPTION + " TEXT, " +
                        Downloads.Columns.COLUMN_ALLOWED_NETWORK_TYPES + " INTEGER NOT NULL DEFAULT 0, " +
                        Downloads.Columns.COLUMN_DELETED + " BOOLEAN NOT NULL DEFAULT 0 " +
                        " );");
            } catch (SQLException ex) {
                Log.e(Constants.TAG, "couldn't create table in downloads database");
                throw ex;
            }
        }

        private void createHeadersTable(SQLiteDatabase db) {
            db.execSQL("DROP TABLE IF EXISTS " + Downloads.Columns.RequestHeaders.HEADERS_DB_TABLE);
            db.execSQL("CREATE TABLE " + Downloads.Columns.RequestHeaders.HEADERS_DB_TABLE + "(" +
                       "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                       Downloads.Columns.RequestHeaders.COLUMN_DOWNLOAD_ID + " INTEGER NOT NULL," +
                       Downloads.Columns.RequestHeaders.COLUMN_HEADER + " TEXT NOT NULL," +
                       Downloads.Columns.RequestHeaders.COLUMN_VALUE + " TEXT NOT NULL" +
                       ");");
        }
    }
    
    private Context mContext;
    public DownloadProvider(Context context){
        mContext = context;
        init();
    }
    
    
    
    /**
     * Initializes the content provider when it is created.
     */
    public boolean init() {
        if (mSystemFacade == null) {
            mSystemFacade = new SystemFacade(mContext);
        }
        mOpenHelper = new DatabaseHelper(mContext);
        // start the DownloadService class. don't wait for the 1st download to be issued.
        // saves us by getting some initialization code in DownloadService out of the way.
        Context context = mContext;
        context.startService(new Intent(context, DownloadService.class));
        return true;
    }


    /**
     * Inserts a row in the database
     */
    public long insert(final ContentValues values) {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();

        ContentValues filteredValues = new ContentValues();
        copyString(Downloads.Columns.COLUMN_URI, values, filteredValues);
        copyString(Downloads.Columns.COLUMN_APP_DATA, values, filteredValues);
        copyString(Downloads.Columns.COLUMN_MIME_TYPE, values, filteredValues);
        copyString(Downloads.Columns.COLUMN_DESTINATION, values, filteredValues);
        copyString(Downloads.Columns.COLUMN_PACKAGE, values, filteredValues);

        //checkFileUriDestination(values);
        // copy the control column as is
        copyInteger(Downloads.Columns.COLUMN_CONTROL, values, filteredValues);
        filteredValues.put(Downloads.Columns.COLUMN_STATUS, Downloads.Columns.STATUS_PENDING);
        filteredValues.put(Downloads.Columns.COLUMN_TOTAL_BYTES, -1);
        filteredValues.put(Downloads.Columns.COLUMN_CURRENT_BYTES, 0);

        // set lastupdate to current time
        long lastMod = mSystemFacade.currentTimeMillis();
        filteredValues.put(Downloads.Columns.COLUMN_LAST_MODIFICATION, lastMod);
        // copy some more columns as is
        copyString(Downloads.Columns.COLUMN_EXTRAS, values, filteredValues);
        copyString(Downloads.Columns.COLUMN_COOKIE_DATA, values, filteredValues);
        copyString(Downloads.Columns.COLUMN_USER_AGENT, values, filteredValues);
        copyString(Downloads.Columns.COLUMN_REFERER, values, filteredValues);

        // copy some more columns as is
        copyStringWithDefault(Downloads.Columns.COLUMN_TITLE, values, filteredValues, "");
        copyStringWithDefault(Downloads.Columns.COLUMN_DESCRIPTION, values, filteredValues, "");
        copyInteger(Downloads.Columns.COLUMN_ALLOWED_NETWORK_TYPES, values, filteredValues);

        long rowID = db.insert(DB_TABLE, null, filteredValues);
        if (rowID == -1) {
            Log.d(Constants.TAG, "couldn't insert into downloads database");
            return -1;
        }
        insertRequestHeaders(db,rowID,values);
        if(mObserber!=null){
            mObserber.onChange();
        }
        // Always start service to handle notifications and/or scanning
        final Context context = mContext;
        context.startService(new Intent(context, DownloadService.class));
        return rowID;
    }


    /**
     * Remove column from values, and throw a SecurityException if the value isn't within the
     * specified allowedValues.
     */
    private void enforceAllowedValues(ContentValues values, String column,
            Object... allowedValues) {
        Object value = values.get(column);
        values.remove(column);
        for (Object allowedValue : allowedValues) {
            if (value == null && allowedValue == null) {
                return;
            }
            if (value != null && value.equals(allowedValue)) {
                return;
            }
        }
        throw new SecurityException("Invalid value for " + column + ": " + value);
    }

    /**
     * Starts a database query
     */
    public Cursor query(final long id, String[] projection,
             final String selection, final String[] selectionArgs,
             final String sort) {

        Helpers.validateSelection(selection, sAppReadableColumnsSet);

        SQLiteDatabase db = mOpenHelper.getReadableDatabase();

        SqlSelection fullSelection = getWhereClause(id, selection, selectionArgs);

        Cursor ret = db.query(DB_TABLE, projection, fullSelection.getSelection(),
                fullSelection.getParameters(), null, null, sort);

        return ret;
    }

    /**
     * Insert request headers for a download into the DB.
     *  insertRequestHeaders(db, rowID, values);
     */
    public void insertRequestHeaders(SQLiteDatabase db,long downloadId, ContentValues values) {
        ContentValues rowValues = new ContentValues();
        rowValues.put(Downloads.Columns.RequestHeaders.COLUMN_DOWNLOAD_ID, downloadId);
        for (Map.Entry<String, Object> entry : values.valueSet()) {
            String key = entry.getKey();
            if (key.startsWith(Downloads.Columns.RequestHeaders.INSERT_KEY_PREFIX)) {
                String headerLine = entry.getValue().toString();
                if (!headerLine.contains(":")) {
                    throw new IllegalArgumentException("Invalid HTTP header line: " + headerLine);
                }
                String[] parts = headerLine.split(":", 2);
                rowValues.put(Downloads.Columns.RequestHeaders.COLUMN_HEADER, parts[0].trim());
                rowValues.put(Downloads.Columns.RequestHeaders.COLUMN_VALUE, parts[1].trim());
                db.insert(Downloads.Columns.RequestHeaders.HEADERS_DB_TABLE, null, rowValues);
            }
        }
//        db.close();
    }

    /**
     * Handle a query for the custom request headers registered for a download.
     */
    public Cursor queryRequestHeaders(final long id) {
        SQLiteDatabase db = mOpenHelper.getReadableDatabase();
        String where = Downloads.Columns.RequestHeaders.COLUMN_DOWNLOAD_ID + "=" + id;
        String[] projection = new String[] {Downloads.Columns.RequestHeaders.COLUMN_HEADER,
                                            Downloads.Columns.RequestHeaders.COLUMN_VALUE};
        return db.query(Downloads.Columns.RequestHeaders.HEADERS_DB_TABLE, projection, where,
                        null, null, null, null);
    }

    /**
     * Delete request headers for downloads matching the given query.
     * deleteRequestHeaders(db, selection.getSelection(), selection.getParameters());
     */
    public void deleteRequestHeaders(String where, String[] whereArgs) {
        SQLiteDatabase db = mOpenHelper.getReadableDatabase();
        String[] projection = new String[] {Downloads.Columns._ID};
        Cursor cursor = db.query(DB_TABLE, projection, where, whereArgs, null, null, null, null);
        try {
            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                long id = cursor.getLong(0);
                String idWhere = Downloads.Columns.RequestHeaders.COLUMN_DOWNLOAD_ID + "=" + id;
                db.delete(Downloads.Columns.RequestHeaders.HEADERS_DB_TABLE, idWhere, null);
            }
        } finally {
            cursor.close();
        }
    }

    /**
     * Updates a row in the database
     */
    public int update(final long id, final ContentValues values, final String where, final String[] whereArgs) {

        Helpers.validateSelection(where, sAppReadableColumnsSet);

        SQLiteDatabase db = mOpenHelper.getWritableDatabase();

        int count;
        boolean startService = false;

        if (values.containsKey(Downloads.Columns.COLUMN_DELETED)) {
            if (values.getAsInteger(Downloads.Columns.COLUMN_DELETED) == 1) {
                startService = true;
            }
        }

        ContentValues filteredValues = values;
        String filename = values.getAsString(Downloads.Columns._DATA);
        if (filename != null) {
            Cursor c = query(id, new String[]{ Downloads.Columns.COLUMN_TITLE }, null, null, null);
            if (!c.moveToFirst() || !TextUtils.isEmpty(c.getString(0))) {
                values.put(Downloads.Columns.COLUMN_TITLE, new File(filename).getName());
            }
            c.close();
        }

        Integer status = values.getAsInteger(Downloads.Columns.COLUMN_STATUS);
        boolean isRestart = status != null && status == Downloads.Columns.STATUS_PENDING;
        if (isRestart) {
            startService = true;
        }

        SqlSelection selection = getWhereClause(id, where, whereArgs);
        if (filteredValues.size() > 0) {
            count = db.update(DB_TABLE, filteredValues, selection.getSelection(),
                    selection.getParameters());
        } else {
            count = 0;
        }

        if(mObserber!=null){
            mObserber.onChange();
        }
        if (startService) {
            Context context = mContext;
            context.startService(new Intent(context, DownloadService.class));
        }
//        db.close();
        return count;
    }


    private SqlSelection getWhereClause(final long id, final String where, final String[] whereArgs) {
        SqlSelection selection = new SqlSelection();
        selection.appendClause(where, whereArgs);
        if(id > 0){
            selection.appendClause(Downloads.Columns._ID + " = ?",id);
        }
        return selection;
    }

    /**
     * Deletes a row in the database
     */
    public int delete(final long id, final String where, final String[] whereArgs) {

        Helpers.validateSelection(where, sAppReadableColumnsSet);

        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int count;
        SqlSelection selection = getWhereClause(id, where, whereArgs);
        final Cursor cursor = db.query(DB_TABLE, new String[] {
                Downloads.Columns._ID ,Downloads.Columns.COLUMN_DESTINATION}, selection.getSelection(), selection.getParameters(),
                null, null, null);
        try {
            while (cursor.moveToNext()) {
                final long tempId = cursor.getLong(1);
                //todo delete file

            }
        } finally {
            cursor.close();
        }

        count = db.delete(DB_TABLE, selection.getSelection(), selection.getParameters());

        if(mObserber!=null){
            mObserber.onChange();
        }
        return count;
    }

    private static final void copyInteger(String key, ContentValues from, ContentValues to) {
        Integer i = from.getAsInteger(key);
        if (i != null) {
            to.put(key, i);
        }
    }

    private static final void copyBoolean(String key, ContentValues from, ContentValues to) {
        Boolean b = from.getAsBoolean(key);
        if (b != null) {
            to.put(key, b);
        }
    }

    private static final void copyString(String key, ContentValues from, ContentValues to) {
        String s = from.getAsString(key);
        if (s != null) {
            to.put(key, s);
        }
    }

    private static final void copyStringWithDefault(String key, ContentValues from,
            ContentValues to, String defaultValue) {
        copyString(key, from, to);
        if (!to.containsKey(key)) {
            to.put(key, defaultValue);
        }
    }

    private DownloadObserver mObserber;
    public void setContentObserver(DownloadObserver observer){
        mObserber = observer;
    }

}
