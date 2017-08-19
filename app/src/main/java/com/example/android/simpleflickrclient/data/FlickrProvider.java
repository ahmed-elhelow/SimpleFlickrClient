/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.android.simpleflickrclient.data;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;

public class FlickrProvider extends ContentProvider {
    private final String LOG_TAG = FlickrProvider.class.getSimpleName();

    // The URI Matcher used by this content provider.
    private static final UriMatcher sUriMatcher = buildUriMatcher();
    private FlickrDbHelper mOpenHelper;

    static final int FLICKR_IMAGE = 100;
    static final int FLICKR_IMAGE_with_id = 200;

    private static final SQLiteQueryBuilder sFlickrImageQueryBuilder;

    static {
        sFlickrImageQueryBuilder = new SQLiteQueryBuilder();

        sFlickrImageQueryBuilder.setTables(FlickrContract.FlickrImages.TABLE_NAME);
    }

    private static final String sFlickrImageSelection =
            FlickrContract.FlickrImages.TABLE_NAME +
                    "." + FlickrContract.FlickrImages.COLUMN_IMAGE_ID + " = ?";


    private Cursor getFlickrImageByID(
            Uri uri, String[] projection, String sortOrder) {
        long id = FlickrContract.FlickrImages.getIDFromUri(uri);

        return sFlickrImageQueryBuilder.query(mOpenHelper.getReadableDatabase(),
                projection,
                sFlickrImageSelection,
                new String[]{Long.toString(id)},
                null,
                null,
                sortOrder
        );
    }

    static UriMatcher buildUriMatcher() {

        UriMatcher wURIMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        final String authority = FlickrContract.CONTENT_AUTHORITY;
        wURIMatcher.addURI(authority, FlickrContract.PATH_FLICKR , FLICKR_IMAGE);
        wURIMatcher.addURI(authority, FlickrContract.PATH_FLICKR + "/#", FLICKR_IMAGE_with_id);

        return wURIMatcher;
    }

    @Override
    public boolean onCreate() {
        mOpenHelper = new FlickrDbHelper(getContext());
        return true;
    }


    @Override
    public String getType(Uri uri) {

        // Use the Uri Matcher to determine what kind of URI this is.
        final int match = sUriMatcher.match(uri);

        switch (match) {
            case FLICKR_IMAGE:
                return FlickrContract.FlickrImages.CONTENT_ITEM_TYPE;
            case FLICKR_IMAGE_with_id:
                return FlickrContract.FlickrImages.CONTENT_ITEM_TYPE;
            default:
                throw new UnsupportedOperationException("Unknown uri: " + uri);
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        Cursor retCursor;

        final int match = sUriMatcher.match(uri);

        switch (sUriMatcher.match(uri)) {
            case FLICKR_IMAGE_with_id: {
                retCursor = getFlickrImageByID(uri, projection, sortOrder);
                break;
            }
            default:
                throw new UnsupportedOperationException("Unknown uri: " + uri);
        }
        retCursor.setNotificationUri(getContext().getContentResolver(), uri);
        return retCursor;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        final SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        final int match = sUriMatcher.match(uri);
        Uri returnUri;

        switch (match) {
            case FLICKR_IMAGE_with_id: {
                long id = db.insert(FlickrContract.FlickrImages.TABLE_NAME, null, values);
                if (id >= 0)
                    returnUri = FlickrContract.FlickrImages.buildImageUri(id);
                else
                    throw new android.database.SQLException("Failed to insert row into " + uri);
                break;
            }
            default:
                throw new UnsupportedOperationException("Unknown uri: " + uri);
        }
        getContext().getContentResolver().notifyChange(uri, null);
        return returnUri;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        final SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        final int match = sUriMatcher.match(uri);
        int rowsDeleted;
        // this makes delete all rows return the number of rows deleted
        if (null == selection) selection = "1";
        switch (match) {
            case FLICKR_IMAGE:
                rowsDeleted = db.delete(
                        FlickrContract.FlickrImages.TABLE_NAME, selection, selectionArgs);
                break;
            default:
                throw new UnsupportedOperationException("Unknown uri: " + uri);
        }
        // Because a null deletes all rows
        if (rowsDeleted != 0) {
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return rowsDeleted;
    }

    @Override
    public int update(
            Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        final SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        final int match = sUriMatcher.match(uri);
        int rowsUpdated;

        switch (match) {
            case FLICKR_IMAGE:
                rowsUpdated = db.update(FlickrContract.FlickrImages.TABLE_NAME, values, selection,
                        selectionArgs);
                break;
            default:
                throw new UnsupportedOperationException("Unknown uri: " + uri);
        }
        if (rowsUpdated != 0) {
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return rowsUpdated;
    }

    @Override
    public int bulkInsert(Uri uri, ContentValues[] values) {
        final SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        final int match = sUriMatcher.match(uri);
        switch (match) {
            case FLICKR_IMAGE:
                db.beginTransaction();
                int returnCount = 0;
                try {
                    for (ContentValues value : values) {
                        long id = db.insert(FlickrContract.FlickrImages.TABLE_NAME, null, value);
                        if (id != -1) {
                            returnCount++;
                        }
                    }
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
                getContext().getContentResolver().notifyChange(uri, null);
                return returnCount;
            default:
                return super.bulkInsert(uri, values);
        }
    }

    public static long getProfilesCount(Context context) {
        FlickrDbHelper mOpenHelper = new FlickrDbHelper(context);
        SQLiteDatabase db = mOpenHelper.getReadableDatabase();
        long cnt = DatabaseUtils.queryNumEntries(db, FlickrContract.FlickrImages.TABLE_NAME);
        db.close();
        return cnt;
    }
}