package com.nianing.downloadmanager;

import android.os.Environment;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.IOException;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Some helper functions for the download manager
 */
public class Helpers {
    public static Random sRandom = new Random(SystemClock.uptimeMillis());

    /** Regex used to parse content-disposition headers */
    private static final Pattern CONTENT_DISPOSITION_PATTERN =
            Pattern.compile("attachment;\\s*filename\\s*=\\s*\"([^\"]*)\"");

    private static final Object sUniqueLock = new Object();

    private Helpers() {
    }

    /*
     * Parse the Content-Disposition HTTP Header. The format of the header
     * is defined here: http://www.w3.org/Protocols/rfc2616/rfc2616-sec19.html
     * This header provides a filename for content that is going to be
     * downloaded to the file system. We only support the attachment type.
     */
    private static String parseContentDisposition(String contentDisposition) {
        try {
            Matcher m = CONTENT_DISPOSITION_PATTERN.matcher(contentDisposition);
            if (m.find()) {
                return m.group(1);
            }
        } catch (IllegalStateException ex) {
             // This function is defined as returning null when it can't parse the header
        }
        return null;
    }

    /**
     * Creates a filename (where the file should be saved) from info about a download.
     */
    static void generateSaveFile(String path,long contentLength,
            StorageManager storageManager) throws StopRequestException {
        if (contentLength < 0) {
            contentLength = 0;
        }
        storageManager.verifySpace(path,contentLength);
        synchronized (sUniqueLock) {
            try {
                File file = new File(path);
                if (!file.getParentFile().exists()) {
                    file.getParentFile().mkdirs();
                }
                file.createNewFile();
            } catch (IOException e) {
                throw new StopRequestException(Downloads.Columns.STATUS_FILE_ERROR,
                        "Failed to create target file " + path, e);
            }
        }
    }



    private static String chooseExtensionFromMimeType(String mimeType, boolean useDefaults) {
        String extension = null;
        if (mimeType != null) {
            extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
            if (extension != null) {
                if (Constants.LOGVV) {
                    Log.v(Constants.TAG, "adding extension from type");
                }
                extension = "." + extension;
            } else {
                if (Constants.LOGVV) {
                    Log.v(Constants.TAG, "couldn't find extension for " + mimeType);
                }
            }
        }
        if (extension == null) {
            if (mimeType != null && mimeType.toLowerCase().startsWith("text/")) {
                if (mimeType.equalsIgnoreCase("text/html")) {
                    if (Constants.LOGVV) {
                        Log.v(Constants.TAG, "adding default html extension");
                    }
                    extension = Constants.DEFAULT_DL_HTML_EXTENSION;
                } else if (useDefaults) {
                    if (Constants.LOGVV) {
                        Log.v(Constants.TAG, "adding default text extension");
                    }
                    extension = Constants.DEFAULT_DL_TEXT_EXTENSION;
                }
            } else if (useDefaults) {
                if (Constants.LOGVV) {
                    Log.v(Constants.TAG, "adding default binary extension");
                }
                extension = Constants.DEFAULT_DL_BINARY_EXTENSION;
            }
        }
        return extension;
    }



    /**
     * Checks whether the filename looks legitimate
     */
    static boolean isFilenameValid(String filename, File downloadsDataDir) {
        final String[] whitelist;
        try {
            filename = new File(filename).getCanonicalPath();
            whitelist = new String[] {
                    downloadsDataDir.getCanonicalPath(),
                    Environment.getDownloadCacheDirectory().getCanonicalPath(),
                    Environment.getExternalStorageDirectory().getCanonicalPath(),
            };
        } catch (IOException e) {
            return false;
        }

        for (String test : whitelist) {
            if (filename.startsWith(test)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Checks whether this looks like a legitimate selection parameter
     */
    public static void validateSelection(String selection, Set<String> allowedColumns) {
        try {
            if (TextUtils.isEmpty(selection)) {
                return;
            }
            Lexer lexer = new Lexer(selection, allowedColumns);
            parseExpression(lexer);
            if (lexer.currentToken() != Lexer.TOKEN_END) {
                throw new IllegalArgumentException("syntax error");
            }
        } catch (RuntimeException ex) {
            if (Constants.LOGV) {
                Log.d(Constants.TAG, "invalid selection [" + selection + "] triggered " + ex);
            } else if (false) {
                Log.d(Constants.TAG, "invalid selection triggered " + ex);
            }
            throw ex;
        }

    }

    // expression <- ( expression ) | statement [AND_OR ( expression ) | statement] *
    //             | statement [AND_OR expression]*
    private static void parseExpression(Lexer lexer) {
        for (;;) {
            // ( expression )
            if (lexer.currentToken() == Lexer.TOKEN_OPEN_PAREN) {
                lexer.advance();
                parseExpression(lexer);
                if (lexer.currentToken() != Lexer.TOKEN_CLOSE_PAREN) {
                    throw new IllegalArgumentException("syntax error, unmatched parenthese");
                }
                lexer.advance();
            } else {
                // statement
                parseStatement(lexer);
            }
            if (lexer.currentToken() != Lexer.TOKEN_AND_OR) {
                break;
            }
            lexer.advance();
        }
    }

    // statement <- COLUMN COMPARE VALUE
    //            | COLUMN IS NULL
    private static void parseStatement(Lexer lexer) {
        // both possibilities start with COLUMN
        if (lexer.currentToken() != Lexer.TOKEN_COLUMN) {
            throw new IllegalArgumentException("syntax error, expected column name");
        }
        lexer.advance();

        // statement <- COLUMN COMPARE VALUE
        if (lexer.currentToken() == Lexer.TOKEN_COMPARE) {
            lexer.advance();
            if (lexer.currentToken() != Lexer.TOKEN_VALUE) {
                throw new IllegalArgumentException("syntax error, expected quoted string");
            }
            lexer.advance();
            return;
        }

        // statement <- COLUMN IS NULL
        if (lexer.currentToken() == Lexer.TOKEN_IS) {
            lexer.advance();
            if (lexer.currentToken() != Lexer.TOKEN_NULL) {
                throw new IllegalArgumentException("syntax error, expected NULL");
            }
            lexer.advance();
            return;
        }

        // didn't get anything good after COLUMN
        throw new IllegalArgumentException("syntax error after column name");
    }

    /**
     * A simple lexer that recognizes the words of our restricted subset of SQL where clauses
     */
    private static class Lexer {
        public static final int TOKEN_START = 0;
        public static final int TOKEN_OPEN_PAREN = 1;
        public static final int TOKEN_CLOSE_PAREN = 2;
        public static final int TOKEN_AND_OR = 3;
        public static final int TOKEN_COLUMN = 4;
        public static final int TOKEN_COMPARE = 5;
        public static final int TOKEN_VALUE = 6;
        public static final int TOKEN_IS = 7;
        public static final int TOKEN_NULL = 8;
        public static final int TOKEN_END = 9;

        private final String mSelection;
        private final Set<String> mAllowedColumns;
        private int mOffset = 0;
        private int mCurrentToken = TOKEN_START;
        private final char[] mChars;

        public Lexer(String selection, Set<String> allowedColumns) {
            mSelection = selection;
            mAllowedColumns = allowedColumns;
            mChars = new char[mSelection.length()];
            mSelection.getChars(0, mChars.length, mChars, 0);
            advance();
        }

        public int currentToken() {
            return mCurrentToken;
        }

        public void advance() {
            char[] chars = mChars;

            // consume whitespace
            while (mOffset < chars.length && chars[mOffset] == ' ') {
                ++mOffset;
            }

            // end of input
            if (mOffset == chars.length) {
                mCurrentToken = TOKEN_END;
                return;
            }

            // "("
            if (chars[mOffset] == '(') {
                ++mOffset;
                mCurrentToken = TOKEN_OPEN_PAREN;
                return;
            }

            // ")"
            if (chars[mOffset] == ')') {
                ++mOffset;
                mCurrentToken = TOKEN_CLOSE_PAREN;
                return;
            }

            // "?"
            if (chars[mOffset] == '?') {
                ++mOffset;
                mCurrentToken = TOKEN_VALUE;
                return;
            }

            // "=" and "=="
            if (chars[mOffset] == '=') {
                ++mOffset;
                mCurrentToken = TOKEN_COMPARE;
                if (mOffset < chars.length && chars[mOffset] == '=') {
                    ++mOffset;
                }
                return;
            }

            // ">" and ">="
            if (chars[mOffset] == '>') {
                ++mOffset;
                mCurrentToken = TOKEN_COMPARE;
                if (mOffset < chars.length && chars[mOffset] == '=') {
                    ++mOffset;
                }
                return;
            }

            // "<", "<=" and "<>"
            if (chars[mOffset] == '<') {
                ++mOffset;
                mCurrentToken = TOKEN_COMPARE;
                if (mOffset < chars.length && (chars[mOffset] == '=' || chars[mOffset] == '>')) {
                    ++mOffset;
                }
                return;
            }

            // "!="
            if (chars[mOffset] == '!') {
                ++mOffset;
                mCurrentToken = TOKEN_COMPARE;
                if (mOffset < chars.length && chars[mOffset] == '=') {
                    ++mOffset;
                    return;
                }
                throw new IllegalArgumentException("Unexpected character after !");
            }

            // columns and keywords
            // first look for anything that looks like an identifier or a keyword
            //     and then recognize the individual words.
            // no attempt is made at discarding sequences of underscores with no alphanumeric
            //     characters, even though it's not clear that they'd be legal column names.
            if (isIdentifierStart(chars[mOffset])) {
                int startOffset = mOffset;
                ++mOffset;
                while (mOffset < chars.length && isIdentifierChar(chars[mOffset])) {
                    ++mOffset;
                }
                String word = mSelection.substring(startOffset, mOffset);
                if (mOffset - startOffset <= 4) {
                    if (word.equals("IS")) {
                        mCurrentToken = TOKEN_IS;
                        return;
                    }
                    if (word.equals("OR") || word.equals("AND")) {
                        mCurrentToken = TOKEN_AND_OR;
                        return;
                    }
                    if (word.equals("NULL")) {
                        mCurrentToken = TOKEN_NULL;
                        return;
                    }
                }
                if (mAllowedColumns.contains(word)) {
                    mCurrentToken = TOKEN_COLUMN;
                    return;
                }
                throw new IllegalArgumentException("unrecognized column or keyword");
            }

            // quoted strings
            if (chars[mOffset] == '\'') {
                ++mOffset;
                while (mOffset < chars.length) {
                    if (chars[mOffset] == '\'') {
                        if (mOffset + 1 < chars.length && chars[mOffset + 1] == '\'') {
                            ++mOffset;
                        } else {
                            break;
                        }
                    }
                    ++mOffset;
                }
                if (mOffset == chars.length) {
                    throw new IllegalArgumentException("unterminated string");
                }
                ++mOffset;
                mCurrentToken = TOKEN_VALUE;
                return;
            }

            // anything we don't recognize
            throw new IllegalArgumentException("illegal character: " + chars[mOffset]);
        }

        private static final boolean isIdentifierStart(char c) {
            return c == '_' ||
                    (c >= 'A' && c <= 'Z') ||
                    (c >= 'a' && c <= 'z');
        }

        private static final boolean isIdentifierChar(char c) {
            return c == '_' ||
                    (c >= 'A' && c <= 'Z') ||
                    (c >= 'a' && c <= 'z') ||
                    (c >= '0' && c <= '9');
        }
    }

    /**
     * Replace invalid filename characters according to
     * specifications of the VFAT.
     * @note Package-private due to testing.
     */
    private static String replaceInvalidVfatCharacters(String filename) {
        final char START_CTRLCODE = 0x00;
        final char END_CTRLCODE = 0x1f;
        final char QUOTEDBL = 0x22;
        final char ASTERISK = 0x2A;
        final char SLASH = 0x2F;
        final char COLON = 0x3A;
        final char LESS = 0x3C;
        final char GREATER = 0x3E;
        final char QUESTION = 0x3F;
        final char BACKSLASH = 0x5C;
        final char BAR = 0x7C;
        final char DEL = 0x7F;
        final char UNDERSCORE = 0x5F;

        StringBuffer sb = new StringBuffer();
        char ch;
        boolean isRepetition = false;
        for (int i = 0; i < filename.length(); i++) {
            ch = filename.charAt(i);
            if ((START_CTRLCODE <= ch &&
                ch <= END_CTRLCODE) ||
                ch == QUOTEDBL ||
                ch == ASTERISK ||
                ch == SLASH ||
                ch == COLON ||
                ch == LESS ||
                ch == GREATER ||
                ch == QUESTION ||
                ch == BACKSLASH ||
                ch == BAR ||
                ch == DEL){
                if (!isRepetition) {
                    sb.append(UNDERSCORE);
                    isRepetition = true;
                }
            } else {
                sb.append(ch);
                isRepetition = false;
            }
        }
        return sb.toString();
    }
}
