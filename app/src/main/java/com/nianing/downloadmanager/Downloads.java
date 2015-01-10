package com.nianing.downloadmanager;

public final class Downloads {
    private Downloads() {}

    public static final class Columns{
        private Columns() {}

        public static final String _ID = "_id";

        /**
         * The name of the column containing the URI of the data being downloaded.
         * <P>Type: TEXT</P>
         * <P>Owner can Init/Read</P>
         */
        public static final String COLUMN_URI = "uri";

        /**
         * The name of the column containing application-specific data.
         * <P>Type: TEXT</P>
         * <P>Owner can Init/Read/Write</P>
         */
        public static final String COLUMN_APP_DATA = "entity";

        /**
         * The name of the column containing the filename where the downloaded data
         * was actually stored.
         * <P>Type: TEXT</P>
         * <P>Owner can Read</P>
         */
        public static final String _DATA = "_data";

        /**
         * The name of the column containing the MIME type of the downloaded data.
         * <P>Type: TEXT</P>
         * <P>Owner can Init/Read</P>
         */
        public static final String COLUMN_MIME_TYPE = "mimetype";

        /**
         * The name of the column containing the flag that controls the destination
         * of the download. See the DESTINATION_* constants for a list of legal values.
         * <P>Type: INTEGER</P>
         * <P>Owner can Init</P>
         */
        public static final String COLUMN_DESTINATION = "destination";

        /**
         * The name of the column containing the current control state  of the download.
         * Applications can write to this to control (pause/resume) the download.
         * the CONTROL_* constants for a list of legal values.
         * <P>Type: INTEGER</P>
         * <P>Owner can Read</P>
         */
        public static final String COLUMN_CONTROL = "control";

        /**
         * The name of the column containing the current status of the download.
         * Applications can read this to follow the progress of each download. See
         * the STATUS_* constants for a list of legal values.
         * <P>Type: INTEGER</P>
         * <P>Owner can Read</P>
         */
        public static final String COLUMN_STATUS = "status";

        /**
         * The name of the column containing the date at which some interesting
         * status changed in the download. Stored as a System.currentTimeMillis()
         * value.
         * <P>Type: BIGINT</P>
         * <P>Owner can Read</P>
         */
        public static final String COLUMN_LAST_MODIFICATION = "lastmod";

        /**
         * The name of the column containing the package name of the application
         * that initiating the download. The download manager will send
         * notifications to a component in this package when the download completes.
         * <P>Type: TEXT</P>
         * <P>Owner can Init/Read</P>
         */
        public static final String COLUMN_PACKAGE = "package";

        /**
         * If extras are specified when requesting a download they will be provided in the intent that
         * is sent to the specified class and package when a download has finished.
         * <P>Type: TEXT</P>
         * <P>Owner can Init</P>
         */
        public static final String COLUMN_EXTRAS = "extras";

        /**
         * The name of the column contain the values of the cookie to be used for
         * the download. This is used directly as the value for the Cookie: HTTP
         * header that gets sent with the request.
         * <P>Type: TEXT</P>
         * <P>Owner can Init</P>
         */
        public static final String COLUMN_COOKIE_DATA = "cookiedata";

        /**
         * The name of the column containing the user agent that the initiating
         * application wants the download manager to use for this download.
         * <P>Type: TEXT</P>
         * <P>Owner can Init</P>
         */
        public static final String COLUMN_USER_AGENT = "useragent";

        /**
         * The name of the column containing the referer (sic) that the initiating
         * application wants the download manager to use for this download.
         * <P>Type: TEXT</P>
         * <P>Owner can Init</P>
         */
        public static final String COLUMN_REFERER = "referer";

        /**
         * The name of the column containing the total size of the file being
         * downloaded.
         * <P>Type: INTEGER</P>
         * <P>Owner can Read</P>
         */
        public static final String COLUMN_TOTAL_BYTES = "total_bytes";

        /**
         * The name of the column containing the size of the part of the file that
         * has been downloaded so far.
         * <P>Type: INTEGER</P>
         * <P>Owner can Read</P>
         */
        public static final String COLUMN_CURRENT_BYTES = "current_bytes";

        /**
         * The name of the column where the initiating application can provided the
         * title of this download. The title will be displayed ito the user in the
         * list of downloads.
         * <P>Type: TEXT</P>
         * <P>Owner can Init/Read/Write</P>
         */
        public static final String COLUMN_TITLE = "title";

        /**
         * The name of the column where the initiating application can provide the
         * description of this download. The description will be displayed to the
         * user in the list of downloads.
         * <P>Type: TEXT</P>
         * <P>Owner can Init/Read/Write</P>
         */
        public static final String COLUMN_DESCRIPTION = "description";

        /**
         * The name of the column holding a bitmask of allowed network types.  This is only used for
         * public API downloads.
         * <P>Type: INTEGER</P>
         * <P>Owner can Init/Read</P>
         */
        public static final String COLUMN_ALLOWED_NETWORK_TYPES = "allowed_network_types";

        /**
         * Set to true if this download is deleted. It is completely removed from the database
         * when MediaProvider database also deletes the metadata asociated with this downloaded file.
         * <P>Type: BOOLEAN</P>
         * <P>Owner can Read</P>
         */
        public static final String COLUMN_DELETED = "deleted";


        /**
         * The column with errorMsg for a failed downloaded.
         * Used only for debugging purposes.
         * <P>Type: TEXT</P>
         */
        public static final String COLUMN_ERROR_MSG = "errorMsg";


        /** The column that is used to count retries */
        public static final String COLUMN_FAILED_CONNECTIONS = "numfailed";

        public static final String COLUMN_ALLOW_WRITE = "allow_write";

        /**
         * This download is allowed to run.
         */
        public static final int CONTROL_RUN = 0;

        /**
         * This download must pause at the first opportunity.
         */
        public static final int CONTROL_PAUSED = 1;

        /*
         * Lists the states that the download manager can set on a download
         * to notify applications of the download progress.
         * The codes follow the HTTP families:<br>
         * 1xx: informational<br>
         * 2xx: success<br>
         * 3xx: redirects (not used by the download manager)<br>
         * 4xx: client errors<br>
         * 5xx: server errors
         */

        /**
         * Returns whether the status is informational (i.e. 1xx).
         */
        public static boolean isStatusInformational(int status) {
            return (status >= 100 && status < 200);
        }

        /**
         * Returns whether the status is a success (i.e. 2xx).
         */
        public static boolean isStatusSuccess(int status) {
            return (status >= 200 && status < 300);
        }

        /**
         * Returns whether the status is an error (i.e. 4xx or 5xx).
         */
        public static boolean isStatusError(int status) {
            return (status >= 400 && status < 600);
        }

        /**
         * Returns whether the status is a client error (i.e. 4xx).
         */
        public static boolean isStatusClientError(int status) {
            return (status >= 400 && status < 500);
        }

        /**
         * Returns whether the status is a server error (i.e. 5xx).
         */
        public static boolean isStatusServerError(int status) {
            return (status >= 500 && status < 600);
        }

        /**
         * Returns whether the download has completed (either with success or
         * error).
         */
        public static boolean isStatusCompleted(int status) {
            return (status >= 200 && status < 300) || (status >= 400 && status < 600);
        }

        /**
         * This download hasn't stated yet
         */
        public static final int STATUS_PENDING = 190;

        /**
         * This download has started
         */
        public static final int STATUS_RUNNING = 192;

        /**
         * This download has been paused by the owning app.
         */
        public static final int STATUS_PAUSED_BY_APP = 193;

        /**
         * This download encountered some network error and is waiting before retrying the request.
         */
        public static final int STATUS_WAITING_TO_RETRY = 194;

        /**
         * This download is waiting for network connectivity to proceed.
         */
        public static final int STATUS_WAITING_FOR_NETWORK = 195;

        /**
         * This download exceeded a size limit for mobile networks and is waiting for a Wi-Fi
         * connection to proceed.
         */
        public static final int STATUS_QUEUED_FOR_WIFI = 196;

        /**
         * This download couldn't be completed due to insufficient storage
         * space.  Typically, this is because the SD card is full.
         */
        public static final int STATUS_INSUFFICIENT_SPACE_ERROR = 198;

        /**
         * This download couldn't be completed because no external storage
         * device was found.  Typically, this is because the SD card is not
         * mounted.
         */
        public static final int STATUS_DEVICE_NOT_FOUND_ERROR = 199;

        /**
         * This download has successfully completed.
         * Warning: there might be other status values that indicate success
         * in the future.
         * Use isSucccess() to capture the entire category.
         */
        public static final int STATUS_SUCCESS = 200;

        /**
         * This request couldn't be parsed. This is also used when processing
         * requests with unknown/unsupported URI schemes.
         */
        public static final int STATUS_BAD_REQUEST = 400;

        /**
         * This download can't be performed because the content type cannot be
         * handled.
         */
        public static final int STATUS_NOT_ACCEPTABLE = 406;

        /**
         * This download cannot be performed because the length cannot be
         * determined accurately. This is the code for the HTTP error "Length
         * Required", which is typically used when making requests that require
         * a content length but don't have one, and it is also used in the
         * client when a response is received whose length cannot be determined
         * accurately (therefore making it impossible to know when a download
         * completes).
         */
        public static final int STATUS_LENGTH_REQUIRED = 411;

        /**
         * This download was interrupted and cannot be resumed.
         * This is the code for the HTTP error "Precondition Failed", and it is
         * also used in situations where the client doesn't have an ETag at all.
         */
        public static final int STATUS_PRECONDITION_FAILED = 412;

        /**
         * The lowest-valued error status that is not an actual HTTP status code.
         */
        public static final int MIN_ARTIFICIAL_ERROR_STATUS = 488;

        /**
         * The requested destination file already exists.
         */
        public static final int STATUS_FILE_ALREADY_EXISTS_ERROR = 488;

        /**
         * Some possibly transient error occurred, but we can't resume the download.
         */
        public static final int STATUS_CANNOT_RESUME = 489;

        /**
         * This download was canceled
         */
        public static final int STATUS_CANCELED = 490;

        /**
         * This download has completed with an error.
         * Warning: there will be other status values that indicate errors in
         * the future. Use isStatusError() to capture the entire category.
         */
        public static final int STATUS_UNKNOWN_ERROR = 491;

        /**
         * This download couldn't be completed because of a storage issue.
         * Typically, that's because the filesystem is missing or full.
         * Use the more specific {@link #STATUS_INSUFFICIENT_SPACE_ERROR}
         * and {@link #STATUS_DEVICE_NOT_FOUND_ERROR} when appropriate.
         */
        public static final int STATUS_FILE_ERROR = 492;

        /**
         * This download couldn't be completed because of an HTTP
         * redirect response that the download manager couldn't
         * handle.
         */
        public static final int STATUS_UNHANDLED_REDIRECT = 493;

        /**
         * This download couldn't be completed because of an
         * unspecified unhandled HTTP code.
         */
        public static final int STATUS_UNHANDLED_HTTP_CODE = 494;

        /**
         * This download couldn't be completed because of an
         * error receiving or processing data at the HTTP level.
         */
        public static final int STATUS_HTTP_DATA_ERROR = 495;

        /**
         * This download couldn't be completed because of an
         * HttpException while setting up the request.
         */
        public static final int STATUS_HTTP_EXCEPTION = 496;

        /**
         * This download couldn't be completed because there were
         * too many redirects.
         */
        public static final int STATUS_TOO_MANY_REDIRECTS = 497;

        /**
         * This download has failed because requesting application has been
         * blocked by
         *
         * @hide
         * @deprecated since behavior now uses
         *             {@link #STATUS_WAITING_FOR_NETWORK}
         */
        public static final int STATUS_BLOCKED = 498;

        public static String statusToString(int status) {
            switch (status) {
                case STATUS_PENDING: return "PENDING";
                case STATUS_RUNNING: return "RUNNING";
                case STATUS_PAUSED_BY_APP: return "PAUSED_BY_APP";
                case STATUS_WAITING_TO_RETRY: return "WAITING_TO_RETRY";
                case STATUS_WAITING_FOR_NETWORK: return "WAITING_FOR_NETWORK";
                case STATUS_QUEUED_FOR_WIFI: return "QUEUED_FOR_WIFI";
                case STATUS_INSUFFICIENT_SPACE_ERROR: return "INSUFFICIENT_SPACE_ERROR";
                case STATUS_DEVICE_NOT_FOUND_ERROR: return "DEVICE_NOT_FOUND_ERROR";
                case STATUS_SUCCESS: return "SUCCESS";
                case STATUS_BAD_REQUEST: return "BAD_REQUEST";
                case STATUS_NOT_ACCEPTABLE: return "NOT_ACCEPTABLE";
                case STATUS_LENGTH_REQUIRED: return "LENGTH_REQUIRED";
                case STATUS_PRECONDITION_FAILED: return "PRECONDITION_FAILED";
                case STATUS_FILE_ALREADY_EXISTS_ERROR: return "FILE_ALREADY_EXISTS_ERROR";
                case STATUS_CANNOT_RESUME: return "CANNOT_RESUME";
                case STATUS_CANCELED: return "CANCELED";
                case STATUS_UNKNOWN_ERROR: return "UNKNOWN_ERROR";
                case STATUS_FILE_ERROR: return "FILE_ERROR";
                case STATUS_UNHANDLED_REDIRECT: return "UNHANDLED_REDIRECT";
                case STATUS_UNHANDLED_HTTP_CODE: return "UNHANDLED_HTTP_CODE";
                case STATUS_HTTP_DATA_ERROR: return "HTTP_DATA_ERROR";
                case STATUS_HTTP_EXCEPTION: return "HTTP_EXCEPTION";
                case STATUS_TOO_MANY_REDIRECTS: return "TOO_MANY_REDIRECTS";
                case STATUS_BLOCKED: return "BLOCKED";
                default: return Integer.toString(status);
            }
        }

        /**
         * Constants related to HTTP request headers associated with each download.
         */
        public static class RequestHeaders {
            public static final String HEADERS_DB_TABLE = "request_headers";
            public static final String COLUMN_DOWNLOAD_ID = "download_id";
            public static final String COLUMN_HEADER = "header";
            public static final String COLUMN_VALUE = "value";

            /**
             * Path segment to add to a download URI to retrieve request headers
             */
            public static final String URI_SEGMENT = "headers";

            /**
             * Prefix for ContentValues keys that contain HTTP header lines, to be passed to
             * DownloadProvider.insert().
             */
            public static final String INSERT_KEY_PREFIX = "http_header_";
        }
    }
}
