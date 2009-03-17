/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.providers.im;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.content.ContentResolver;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.Im;
import android.text.TextUtils;
import android.util.Log;


import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * A content provider for IM
 */
public class ImProvider extends ContentProvider {
    private static final String LOG_TAG = "imProvider";
    private static final boolean DBG = false;

    private static final String AUTHORITY = "im";

    private static final boolean USE_CONTACT_PRESENCE_TRIGGER = false;

    private static final String TABLE_ACCOUNTS = "accounts";
    private static final String TABLE_PROVIDERS = "providers";
    private static final String TABLE_PROVIDER_SETTINGS = "providerSettings";

    private static final String TABLE_CONTACTS = "contacts";
    private static final String TABLE_CONTACTS_ETAG = "contactsEtag";
    private static final String TABLE_BLOCKED_LIST = "blockedList";
    private static final String TABLE_CONTACT_LIST = "contactList";
    private static final String TABLE_INVITATIONS = "invitations";
    private static final String TABLE_GROUP_MEMBERS = "groupMembers";
    private static final String TABLE_GROUP_MESSAGES = "groupMessages";
    private static final String TABLE_PRESENCE = "presence";
    private static final String USERNAME = "username";
    private static final String TABLE_CHATS = "chats";
    private static final String TABLE_AVATARS = "avatars";
    private static final String TABLE_SESSION_COOKIES = "sessionCookies";
    private static final String TABLE_MESSAGES = "messages";
    private static final String TABLE_OUTGOING_RMQ_MESSAGES = "outgoingRmqMessages";
    private static final String TABLE_LAST_RMQ_ID = "lastrmqid";
    private static final String TABLE_ACCOUNT_STATUS = "accountStatus";
    private static final String TABLE_BRANDING_RESOURCE_MAP_CACHE = "brandingResMapCache";

    private static final String DATABASE_NAME = "im.db";
    private static final int DATABASE_VERSION = 47;

    protected static final int MATCH_PROVIDERS = 1;
    protected static final int MATCH_PROVIDERS_BY_ID = 2;
    protected static final int MATCH_PROVIDERS_WITH_ACCOUNT = 3;
    protected static final int MATCH_ACCOUNTS = 10;
    protected static final int MATCH_ACCOUNTS_BY_ID = 11;
    protected static final int MATCH_CONTACTS = 18;
    protected static final int MATCH_CONTACTS_JOIN_PRESENCE = 19;
    protected static final int MATCH_CONTACTS_BAREBONE = 20;
    protected static final int MATCH_CHATTING_CONTACTS = 21;
    protected static final int MATCH_CONTACTS_BY_PROVIDER = 22;
    protected static final int MATCH_CHATTING_CONTACTS_BY_PROVIDER = 23;
    protected static final int MATCH_NO_CHATTING_CONTACTS_BY_PROVIDER = 24;
    protected static final int MATCH_ONLINE_CONTACTS_BY_PROVIDER = 25;
    protected static final int MATCH_OFFLINE_CONTACTS_BY_PROVIDER = 26;
    protected static final int MATCH_CONTACT = 27;
    protected static final int MATCH_CONTACTS_BULK = 28;
    protected static final int MATCH_ONLINE_CONTACT_COUNT = 30;
    protected static final int MATCH_BLOCKED_CONTACTS = 31;
    protected static final int MATCH_CONTACTLISTS = 32;
    protected static final int MATCH_CONTACTLISTS_BY_PROVIDER = 33;
    protected static final int MATCH_CONTACTLIST = 34;
    protected static final int MATCH_BLOCKEDLIST = 35;
    protected static final int MATCH_BLOCKEDLIST_BY_PROVIDER = 36;
    protected static final int MATCH_CONTACTS_ETAGS = 37;
    protected static final int MATCH_CONTACTS_ETAG = 38;
    protected static final int MATCH_PRESENCE = 40;
    protected static final int MATCH_PRESENCE_ID = 41;
    protected static final int MATCH_PRESENCE_BY_ACCOUNT = 42;
    protected static final int MATCH_PRESENCE_SEED_BY_ACCOUNT = 43;
    protected static final int MATCH_PRESENCE_BULK = 44;
    protected static final int MATCH_MESSAGES = 50;
    protected static final int MATCH_MESSAGES_BY_CONTACT = 51;
    protected static final int MATCH_MESSAGE = 52;
    protected static final int MATCH_GROUP_MESSAGES = 53;
    protected static final int MATCH_GROUP_MESSAGE_BY = 54;
    protected static final int MATCH_GROUP_MESSAGE = 55;
    protected static final int MATCH_GROUP_MEMBERS = 58;
    protected static final int MATCH_GROUP_MEMBERS_BY_GROUP = 59;
    protected static final int MATCH_AVATARS = 60;
    protected static final int MATCH_AVATAR = 61;
    protected static final int MATCH_AVATAR_BY_PROVIDER = 62;
    protected static final int MATCH_CHATS = 70;
    protected static final int MATCH_CHATS_BY_ACCOUNT = 71;
    protected static final int MATCH_CHATS_ID = 72;
    protected static final int MATCH_SESSIONS = 80;
    protected static final int MATCH_SESSIONS_BY_PROVIDER = 81;
    protected static final int MATCH_PROVIDER_SETTINGS = 90;
    protected static final int MATCH_PROVIDER_SETTINGS_BY_ID = 91;
    protected static final int MATCH_PROVIDER_SETTINGS_BY_ID_AND_NAME = 92;
    protected static final int MATCH_INVITATIONS = 100;
    protected static final int MATCH_INVITATION  = 101;
    protected static final int MATCH_OUTGOING_RMQ_MESSAGES = 110;
    protected static final int MATCH_OUTGOING_RMQ_MESSAGE = 111;
    protected static final int MATCH_OUTGOING_HIGHEST_RMQ_ID = 112;
    protected static final int MATCH_LAST_RMQ_ID = 113;
    protected static final int MATCH_ACCOUNTS_STATUS = 114;
    protected static final int MATCH_ACCOUNT_STATUS = 115;
    protected static final int MATCH_BRANDING_RESOURCE_MAP_CACHE = 120;


    protected final UriMatcher mUrlMatcher = new UriMatcher(UriMatcher.NO_MATCH);
    private final String mTransientDbName;

    private static final HashMap<String, String> sProviderAccountsProjectionMap;
    private static final HashMap<String, String> sContactsProjectionMap;
    private static final HashMap<String, String> sContactListProjectionMap;
    private static final HashMap<String, String> sBlockedListProjectionMap;

    private static final String PROVIDER_JOIN_ACCOUNT_TABLE =
            "providers LEFT OUTER JOIN accounts ON " +
                    "(providers._id = accounts.provider AND accounts.active = 1) " +
                    "LEFT OUTER JOIN accountStatus ON (accounts._id = accountStatus.account)";


    private static final String CONTACT_JOIN_PRESENCE_TABLE =
            "contacts LEFT OUTER JOIN presence ON (contacts._id = presence.contact_id)";

    private static final String CONTACT_JOIN_PRESENCE_CHAT_TABLE =
            CONTACT_JOIN_PRESENCE_TABLE +
                    " LEFT OUTER JOIN chats ON (contacts._id = chats.contact_id)";

    private static final String CONTACT_JOIN_PRESENCE_CHAT_AVATAR_TABLE =
            CONTACT_JOIN_PRESENCE_CHAT_TABLE +
                    " LEFT OUTER JOIN avatars ON (contacts.username = avatars.contact" +
                    " AND contacts.account = avatars.account_id)";

    private static final String BLOCKEDLIST_JOIN_AVATAR_TABLE =
            "blockedList LEFT OUTER JOIN avatars ON (blockedList.username = avatars.contact" +
            " AND blockedList.account = avatars.account_id)";

    /**
     * The where clause for filtering out blocked contacts
     */
    private static final String NON_BLOCKED_CONTACTS_WHERE_CLAUSE = "("
        + Im.Contacts.TYPE + " IS NULL OR "
        + Im.Contacts.TYPE + "!="
        + String.valueOf(Im.Contacts.TYPE_BLOCKED)
        + ")";

    private static final String BLOCKED_CONTACTS_WHERE_CLAUSE =
        "(contacts." + Im.Contacts.TYPE + "=" + Im.Contacts.TYPE_BLOCKED + ")";

    private static final String CONTACT_ID = TABLE_CONTACTS + '.' + Im.Contacts._ID;
    private static final String PRESENCE_CONTACT_ID = TABLE_PRESENCE + '.' + Im.Presence.CONTACT_ID;

    protected SQLiteOpenHelper mOpenHelper;
    private final String mDatabaseName;
    private final int mDatabaseVersion;

    private final String[] BACKFILL_PROJECTION = {
        Im.Chats._ID, Im.Chats.SHORTCUT, Im.Chats.LAST_MESSAGE_DATE
    };

    private final String[] FIND_SHORTCUT_PROJECTION = {
        Im.Chats._ID, Im.Chats.SHORTCUT
    };

    private class DatabaseHelper extends SQLiteOpenHelper {

        DatabaseHelper(Context context) {
            super(context, mDatabaseName, null, mDatabaseVersion);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {

            if (DBG) log("##### bootstrapDatabase");

            db.execSQL("CREATE TABLE " + TABLE_PROVIDERS + " (" +
                    "_id INTEGER PRIMARY KEY," +
                    "name TEXT," +       // eg AIM
                    "fullname TEXT," +   // eg AOL Instance Messenger
                    "category TEXT," +   // a category used for forming intent
                    "signup_url TEXT" +  // web url to visit to create a new account
                    ");");

            db.execSQL("CREATE TABLE " + TABLE_ACCOUNTS + " (" +
                    "_id INTEGER PRIMARY KEY," +
                    "name TEXT," +
                    "provider INTEGER," +
                    "username TEXT," +
                    "pw TEXT," +
                    "active INTEGER NOT NULL DEFAULT 0," +
                    "locked INTEGER NOT NULL DEFAULT 0," +
                    "keep_signed_in INTEGER NOT NULL DEFAULT 0," +
                    "last_login_state INTEGER NOT NULL DEFAULT 0," +
                    "UNIQUE (provider, username)" +
                    ");");

            createContactsTables(db);

            db.execSQL("CREATE TABLE " + TABLE_AVATARS + " (" +
                    "_id INTEGER PRIMARY KEY," +
                    "contact TEXT," +
                    "provider_id INTEGER," +
                    "account_id INTEGER," +
                    "hash TEXT," +
                    "data BLOB," +     // raw image data
                    "UNIQUE (account_id, contact)" +
                    ");");

            db.execSQL("CREATE TABLE " + TABLE_PROVIDER_SETTINGS + " (" +
                    "_id INTEGER PRIMARY KEY," +
                    "provider INTEGER," +
                    "name TEXT," +
                    "value TEXT," +
                    "UNIQUE (provider, name)" +
                    ");");

            db.execSQL("create TABLE " + TABLE_OUTGOING_RMQ_MESSAGES + " (" +
                    "_id INTEGER PRIMARY KEY," +
                    "rmq_id INTEGER," +
                    "type INTEGER," +
                    "ts INTEGER," +
                    "data TEXT" +
                    ");");

            db.execSQL("create TABLE " + TABLE_LAST_RMQ_ID + " (" +
                    "_id INTEGER PRIMARY KEY," +
                    "rmq_id INTEGER" +
                    ");");

            db.execSQL("create TABLE " + TABLE_BRANDING_RESOURCE_MAP_CACHE + " (" +
                    "_id INTEGER PRIMARY KEY," +
                    "provider_id INTEGER," +
                    "app_res_id INTEGER," +
                    "plugin_res_id INTEGER" +
                    ");");

            // clean up account specific data when an account is deleted.
            db.execSQL("CREATE TRIGGER account_cleanup " +
                    "DELETE ON " + TABLE_ACCOUNTS +
                    " BEGIN " +
                        "DELETE FROM " + TABLE_AVATARS + " WHERE account_id= OLD._id;" +
                    "END");

            // add a database trigger to clean up associated provider settings
            // while deleting a provider
            db.execSQL("CREATE TRIGGER provider_cleanup " +
                    "DELETE ON " + TABLE_PROVIDERS +
                    " BEGIN " +
                        "DELETE FROM " + TABLE_PROVIDER_SETTINGS + " WHERE provider= OLD._id;" +
                    "END");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.d(LOG_TAG, "Upgrading database from version " + oldVersion + " to " + newVersion);

            switch (oldVersion) {
                case 43:    // this is the db version shipped in Dream 1.0
                    // no-op: no schema changed from 43 to 44. The db version was changed to flush
                    // old provider settings, so new provider setting (including new name/value
                    // pairs) could be inserted by the plugins. 

                    // follow thru.
                case 44:
                    if (newVersion <= 44) {
                        return;
                    }

                    db.beginTransaction();
                    try {
                        // add category column to the providers table
                        db.execSQL("ALTER TABLE " + TABLE_PROVIDERS + " ADD COLUMN category TEXT;");
                        // add otr column to the contacts table
                        db.execSQL("ALTER TABLE " + TABLE_CONTACTS + " ADD COLUMN otr INTEGER;");

                        db.setTransactionSuccessful();
                    } catch (Throwable ex) {
                        Log.e(LOG_TAG, ex.getMessage(), ex);
                        break; // force to destroy all old data;
                    } finally {
                        db.endTransaction();
                    }

                case 45:
                    if (newVersion <= 45) {
                        return;
                    }

                    db.beginTransaction();
                    try {
                        // add an otr_etag column to contact etag table
                        db.execSQL(
                                "ALTER TABLE " + TABLE_CONTACTS_ETAG + " ADD COLUMN otr_etag TEXT;");
                        db.setTransactionSuccessful();
                    } catch (Throwable ex) {
                        Log.e(LOG_TAG, ex.getMessage(), ex);
                        break; // force to destroy all old data;
                    } finally {
                        db.endTransaction();
                    }

                case 46:
                    if (newVersion <= 46) {
                        return;
                    }

                    db.beginTransaction();
                    try {
                        // add branding resource map cache table
                        db.execSQL("create TABLE " + TABLE_BRANDING_RESOURCE_MAP_CACHE + " (" +
                                "_id INTEGER PRIMARY KEY," +
                                "provider_id INTEGER," +
                                "app_res_id INTEGER," +
                                "plugin_res_id INTEGER" +
                                ");");
                        db.setTransactionSuccessful();
                    } catch (Throwable ex) {
                        Log.e(LOG_TAG, ex.getMessage(), ex);
                        break; // force to destroy all old data;
                    } finally {
                        db.endTransaction();
                    }

                    return;
            }

            Log.w(LOG_TAG, "Couldn't upgrade db to " + newVersion + ". Destroying old data.");
            destroyOldTables(db);
            onCreate(db);
        }

        private void destroyOldTables(SQLiteDatabase db) {
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_PROVIDERS);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_ACCOUNTS);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_CONTACT_LIST);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_CONTACTS);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_CONTACTS_ETAG);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_AVATARS);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_PROVIDER_SETTINGS);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_OUTGOING_RMQ_MESSAGES);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_LAST_RMQ_ID);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_BRANDING_RESOURCE_MAP_CACHE);
        }

        private void createContactsTables(SQLiteDatabase db) {
            StringBuilder buf = new StringBuilder();
            String contactsTableName = TABLE_CONTACTS;

            // creating the "contacts" table
            buf.append("CREATE TABLE IF NOT EXISTS ");
            buf.append(contactsTableName);
            buf.append(" (");
            buf.append("_id INTEGER PRIMARY KEY,");
            buf.append("username TEXT,");
            buf.append("nickname TEXT,");
            buf.append("provider INTEGER,");
            buf.append("account INTEGER,");
            buf.append("contactList INTEGER,");
            buf.append("type INTEGER,");
            buf.append("subscriptionStatus INTEGER,");
            buf.append("subscriptionType INTEGER,");

            // the following are derived from Google Contact Extension, we don't include all
            // the attributes, just the ones we can use.
            // (see http://code.google.com/apis/talk/jep_extensions/roster_attributes.html)
            //
            // qc: quick contact (derived from message count)
            // rejected: if the contact has ever been rejected by the user
            buf.append("qc INTEGER,");
            buf.append("rejected INTEGER,");

            // Off the record status
            buf.append("otr INTEGER");
            
            buf.append(");");

            db.execSQL(buf.toString());

            buf.delete(0, buf.length());

            // creating contact etag table
            buf.append("CREATE TABLE IF NOT EXISTS ");
            buf.append(TABLE_CONTACTS_ETAG);
            buf.append(" (");
            buf.append("_id INTEGER PRIMARY KEY,");
            buf.append("etag TEXT,");
            buf.append("otr_etag TEXT,");
            buf.append("account INTEGER UNIQUE");
            buf.append(");");

            db.execSQL(buf.toString());

            buf.delete(0, buf.length());

            // creating the "contactList" table
            buf.append("CREATE TABLE IF NOT EXISTS ");
            buf.append(TABLE_CONTACT_LIST);
            buf.append(" (");
            buf.append("_id INTEGER PRIMARY KEY,");
            buf.append("name TEXT,");
            buf.append("provider INTEGER,");
            buf.append("account INTEGER");
            buf.append(");");

            db.execSQL(buf.toString());

            buf.delete(0, buf.length());

            // creating the "blockedList" table
            buf.append("CREATE TABLE IF NOT EXISTS ");
            buf.append(TABLE_BLOCKED_LIST);
            buf.append(" (");
            buf.append("_id INTEGER PRIMARY KEY,");
            buf.append("username TEXT,");
            buf.append("nickname TEXT,");
            buf.append("provider INTEGER,");
            buf.append("account INTEGER");
            buf.append(");");

            db.execSQL(buf.toString());
        }

        @Override
        public void onOpen(SQLiteDatabase db) {
            if (db.isReadOnly()) {
                Log.w(LOG_TAG, "ImProvider database opened in read only mode.");
                Log.w(LOG_TAG, "Transient tables not created.");
                return;
            }

            if (DBG) log("##### createTransientTables");

            // Create transient tables
            String cpDbName;
            db.execSQL("ATTACH DATABASE ':memory:' AS " + mTransientDbName + ";");
            cpDbName = mTransientDbName + ".";

            // message table (since the UI currently doesn't require saving message history
            // across IM sessions, store the message table in memory db only)
            db.execSQL("CREATE TABLE IF NOT EXISTS " + cpDbName + TABLE_MESSAGES + " (" +
                    "_id INTEGER PRIMARY KEY," +
                    "packet_id TEXT UNIQUE," +
                    "contact TEXT," +
                    "provider INTEGER," +
                    "account INTEGER," +
                    "body TEXT," +
                    "date INTEGER," +    // in seconds
                    "type INTEGER," +
                    "err_code INTEGER NOT NULL DEFAULT 0," +
                    "err_msg TEXT" +
                    ");");

            // presence
            db.execSQL("CREATE TABLE IF NOT EXISTS " + cpDbName + TABLE_PRESENCE + " ("+
                    "_id INTEGER PRIMARY KEY," +
                    "contact_id INTEGER UNIQUE," +
                    "jid_resource TEXT," +  // jid resource for the presence
                    "client_type INTEGER," + // client type
                    "priority INTEGER," +   // presence priority (XMPP)
                    "mode INTEGER," +       // presence mode
                    "status TEXT" +         // custom status
                    ");");

            // group chat invitations
            db.execSQL("CREATE TABLE IF NOT EXISTS " + cpDbName + TABLE_INVITATIONS + " (" +
                    "_id INTEGER PRIMARY KEY," +
                    "providerId INTEGER," +
                    "accountId INTEGER," +
                    "inviteId TEXT," +
                    "sender TEXT," +
                    "groupName TEXT," +
                    "note TEXT," +
                    "status INTEGER" +
                    ");");

            // group chat members
            db.execSQL("CREATE TABLE IF NOT EXISTS " + cpDbName + TABLE_GROUP_MEMBERS + " (" +
                    "_id INTEGER PRIMARY KEY," +
                    "groupId INTEGER," +
                    "username TEXT," +
                    "nickname TEXT" +
                    ");");

            // group chat messages
            db.execSQL("CREATE TABLE IF NOT EXISTS " + cpDbName + TABLE_GROUP_MESSAGES + " (" +
                    "_id INTEGER PRIMARY KEY," +
                    "packet_id TEXT UNIQUE," +
                    "contact TEXT," +
                    "groupId INTEGER," +
                    "body TEXT," +
                    "date INTEGER," +
                    "type INTEGER," +
                    "err_code INTEGER NOT NULL DEFAULT 0," +
                    "err_msg TEXT" +
                    ");");

            // chat sessions, including single person chats and group chats
            db.execSQL("CREATE TABLE IF NOT EXISTS " + cpDbName + TABLE_CHATS + " ("+
                    "_id INTEGER PRIMARY KEY," +
                    "contact_id INTEGER UNIQUE," +
                    "jid_resource TEXT," +  // the JID resource for the user, only for non-group chats
                    "groupchat INTEGER," +   // 1 if group chat, 0 if not TODO: remove this column
                    "last_unread_message TEXT," +  // the last unread message
                    "last_message_date INTEGER," +  // in seconds
                    "unsent_composed_message TEXT," + // a composed, but not sent message
                    "shortcut INTEGER" + // which of 10 slots (if any) this chat occupies
                    ");");

            db.execSQL("CREATE TABLE IF NOT EXISTS " + cpDbName + TABLE_ACCOUNT_STATUS + " (" +
                    "_id INTEGER PRIMARY KEY," +
                    "account INTEGER UNIQUE," +
                    "presenceStatus INTEGER," +
                    "connStatus INTEGER" +
                    ");"
            );

            /* when we moved the contact table out of transient_db and into the main db, the
               contact_cleanup and group_cleanup triggers don't work anymore. It seems we can't
               create triggers that reference objects in a different database!

            String contactsTableName = TABLE_CONTACTS;

            if (USE_CONTACT_PRESENCE_TRIGGER) {
                // Insert a default presence for newly inserted contact
                db.execSQL("CREATE TRIGGER IF NOT EXISTS " + cpDbName + "contact_create_presence " +
                        "INSERT ON " + cpDbName + contactsTableName +
                            " FOR EACH ROW WHEN NEW.type != " + Im.Contacts.TYPE_GROUP +
                                " OR NEW.type != " + Im.Contacts.TYPE_BLOCKED +
                            " BEGIN " +
                                "INSERT INTO presence (contact_id) VALUES (NEW._id);" +
                            " END");
            }

            db.execSQL("CREATE TRIGGER IF NOT EXISTS " + cpDbName + "contact_cleanup " +
                    "DELETE ON " + cpDbName + contactsTableName +
                       " BEGIN " +
                           "DELETE FROM presence WHERE contact_id = OLD._id;" +
                           "DELETE FROM chats WHERE contact_id = OLD._id;" +
                       "END");

            // Cleans up group members and group messages when a group chat is deleted
            db.execSQL("CREATE TRIGGER IF NOT EXISTS " + cpDbName + "group_cleanup " +
                    "DELETE ON " + cpDbName + contactsTableName +
                       " FOR EACH ROW WHEN OLD.type = " + Im.Contacts.TYPE_GROUP +
                       " BEGIN " +
                           "DELETE FROM groupMembers WHERE groupId = OLD._id;" +
                           "DELETE FROM groupMessages WHERE groupId = OLD._id;" +
                       " END");
            */

            // only store the session cookies in memory right now. This means
            // that we don't persist them across device reboot
            db.execSQL("CREATE TABLE IF NOT EXISTS " + cpDbName + TABLE_SESSION_COOKIES + " ("+
                    "_id INTEGER PRIMARY KEY," +
                    "provider INTEGER," +
                    "account INTEGER," +
                    "name TEXT," +
                    "value TEXT" +
                    ");");

        }
    }

    static {
        sProviderAccountsProjectionMap = new HashMap<String, String>();
        sProviderAccountsProjectionMap.put(Im.Provider._ID,
                "providers._id AS _id");
        sProviderAccountsProjectionMap.put(Im.Provider._COUNT,
                "COUNT(*) AS _account");
        sProviderAccountsProjectionMap.put(Im.Provider.NAME,
                "providers.name AS name");
        sProviderAccountsProjectionMap.put(Im.Provider.FULLNAME,
                "providers.fullname AS fullname");
        sProviderAccountsProjectionMap.put(Im.Provider.CATEGORY,
                "providers.category AS category");
        sProviderAccountsProjectionMap.put(Im.Provider.ACTIVE_ACCOUNT_ID,
                "accounts._id AS account_id");
        sProviderAccountsProjectionMap.put(Im.Provider.ACTIVE_ACCOUNT_USERNAME,
                "accounts.username AS account_username");
        sProviderAccountsProjectionMap.put(Im.Provider.ACTIVE_ACCOUNT_PW,
                "accounts.pw AS account_pw");
        sProviderAccountsProjectionMap.put(Im.Provider.ACTIVE_ACCOUNT_LOCKED,
                "accounts.locked AS account_locked");
        sProviderAccountsProjectionMap.put(Im.Provider.ACCOUNT_PRESENCE_STATUS,
                "accountStatus.presenceStatus AS account_presenceStatus");
        sProviderAccountsProjectionMap.put(Im.Provider.ACCOUNT_CONNECTION_STATUS,
                "accountStatus.connStatus AS account_connStatus");

        // contacts projection map
        sContactsProjectionMap = new HashMap<String, String>();

        // Base column
        sContactsProjectionMap.put(Im.Contacts._ID, "contacts._id AS _id");
        sContactsProjectionMap.put(Im.Contacts._COUNT, "COUNT(*) AS _count");

        // contacts column
        sContactsProjectionMap.put(Im.Contacts._ID, "contacts._id as _id");
        sContactsProjectionMap.put(Im.Contacts.USERNAME, "contacts.username as username");
        sContactsProjectionMap.put(Im.Contacts.NICKNAME, "contacts.nickname as nickname");
        sContactsProjectionMap.put(Im.Contacts.PROVIDER, "contacts.provider as provider");
        sContactsProjectionMap.put(Im.Contacts.ACCOUNT, "contacts.account as account");
        sContactsProjectionMap.put(Im.Contacts.CONTACTLIST, "contacts.contactList as contactList");
        sContactsProjectionMap.put(Im.Contacts.TYPE, "contacts.type as type");
        sContactsProjectionMap.put(Im.Contacts.SUBSCRIPTION_STATUS,
                "contacts.subscriptionStatus as subscriptionStatus");
        sContactsProjectionMap.put(Im.Contacts.SUBSCRIPTION_TYPE,
                "contacts.subscriptionType as subscriptionType");
        sContactsProjectionMap.put(Im.Contacts.QUICK_CONTACT, "contacts.qc as qc");
        sContactsProjectionMap.put(Im.Contacts.REJECTED, "contacts.rejected as rejected");

        // Presence columns
        sContactsProjectionMap.put(Im.Presence.CONTACT_ID,
                "presence.contact_id AS contact_id");
        sContactsProjectionMap.put(Im.Contacts.PRESENCE_STATUS,
                "presence.mode AS mode");
        sContactsProjectionMap.put(Im.Contacts.PRESENCE_CUSTOM_STATUS,
                "presence.status AS status");
        sContactsProjectionMap.put(Im.Contacts.CLIENT_TYPE,
                "presence.client_type AS client_type");

        // Chats columns
        sContactsProjectionMap.put(Im.Contacts.CHATS_CONTACT,
                "chats.contact_id AS chats_contact_id");
        sContactsProjectionMap.put(Im.Chats.JID_RESOURCE,
                "chats.jid_resource AS jid_resource");
        sContactsProjectionMap.put(Im.Chats.GROUP_CHAT,
                "chats.groupchat AS groupchat");
        sContactsProjectionMap.put(Im.Contacts.LAST_UNREAD_MESSAGE,
                "chats.last_unread_message AS last_unread_message");
        sContactsProjectionMap.put(Im.Contacts.LAST_MESSAGE_DATE,
                "chats.last_message_date AS last_message_date");
        sContactsProjectionMap.put(Im.Contacts.UNSENT_COMPOSED_MESSAGE,
                "chats.unsent_composed_message AS unsent_composed_message");
        sContactsProjectionMap.put(Im.Contacts.SHORTCUT, "chats.SHORTCUT AS shortcut");

        // Avatars columns
        sContactsProjectionMap.put(Im.Contacts.AVATAR_HASH, "avatars.hash AS avatars_hash");
        sContactsProjectionMap.put(Im.Contacts.AVATAR_DATA, "avatars.data AS avatars_data");

        // contactList projection map
        sContactListProjectionMap = new HashMap<String, String>();
        sContactListProjectionMap.put(Im.ContactList._ID,
                "contactList._id AS _id");
        sContactListProjectionMap.put(Im.ContactList._COUNT,
                "COUNT(*) AS _count");
        sContactListProjectionMap.put(Im.ContactList.NAME, "name");
        sContactListProjectionMap.put(Im.ContactList.PROVIDER, "provider");
        sContactListProjectionMap.put(Im.ContactList.ACCOUNT, "account");

        // blockedList projection map
        sBlockedListProjectionMap = new HashMap<String, String>();
        sBlockedListProjectionMap.put(Im.BlockedList._ID,
                "blockedList._id AS _id");
        sBlockedListProjectionMap.put(Im.BlockedList._COUNT,
                "COUNT(*) AS _count");
        sBlockedListProjectionMap.put(Im.BlockedList.USERNAME, "username");
        sBlockedListProjectionMap.put(Im.BlockedList.NICKNAME, "nickname");
        sBlockedListProjectionMap.put(Im.BlockedList.PROVIDER, "provider");
        sBlockedListProjectionMap.put(Im.BlockedList.ACCOUNT, "account");
        sBlockedListProjectionMap.put(Im.BlockedList.AVATAR_DATA,
                "avatars.data AS avatars_data");
    }

    public ImProvider() {
        this(AUTHORITY, DATABASE_NAME, DATABASE_VERSION);
    }

    protected ImProvider(String authority, String dbName, int dbVersion) {
        mDatabaseName = dbName;
        mDatabaseVersion = dbVersion;

        mTransientDbName = "transient_" + dbName.replace(".", "_");

        mUrlMatcher.addURI(authority, "providers", MATCH_PROVIDERS);
        mUrlMatcher.addURI(authority, "providers/#", MATCH_PROVIDERS_BY_ID);
        mUrlMatcher.addURI(authority, "providers/account", MATCH_PROVIDERS_WITH_ACCOUNT);

        mUrlMatcher.addURI(authority, "accounts", MATCH_ACCOUNTS);
        mUrlMatcher.addURI(authority, "accounts/#", MATCH_ACCOUNTS_BY_ID);

        mUrlMatcher.addURI(authority, "contacts", MATCH_CONTACTS);
        mUrlMatcher.addURI(authority, "contactsWithPresence", MATCH_CONTACTS_JOIN_PRESENCE);
        mUrlMatcher.addURI(authority, "contactsBarebone", MATCH_CONTACTS_BAREBONE);
        mUrlMatcher.addURI(authority, "contacts/#/#", MATCH_CONTACTS_BY_PROVIDER);
        mUrlMatcher.addURI(authority, "contacts/chatting", MATCH_CHATTING_CONTACTS);
        mUrlMatcher.addURI(authority, "contacts/chatting/#/#", MATCH_CHATTING_CONTACTS_BY_PROVIDER);
        mUrlMatcher.addURI(authority, "contacts/online/#/#", MATCH_ONLINE_CONTACTS_BY_PROVIDER);
        mUrlMatcher.addURI(authority, "contacts/offline/#/#", MATCH_OFFLINE_CONTACTS_BY_PROVIDER);
        mUrlMatcher.addURI(authority, "contacts/#", MATCH_CONTACT);
        mUrlMatcher.addURI(authority, "contacts/blocked", MATCH_BLOCKED_CONTACTS);
        mUrlMatcher.addURI(authority, "bulk_contacts", MATCH_CONTACTS_BULK);
        mUrlMatcher.addURI(authority, "contacts/onlineCount", MATCH_ONLINE_CONTACT_COUNT);

        mUrlMatcher.addURI(authority, "contactLists", MATCH_CONTACTLISTS);
        mUrlMatcher.addURI(authority, "contactLists/#/#", MATCH_CONTACTLISTS_BY_PROVIDER);
        mUrlMatcher.addURI(authority, "contactLists/#", MATCH_CONTACTLIST);
        mUrlMatcher.addURI(authority, "blockedList", MATCH_BLOCKEDLIST);
        mUrlMatcher.addURI(authority, "blockedList/#/#", MATCH_BLOCKEDLIST_BY_PROVIDER);

        mUrlMatcher.addURI(authority, "contactsEtag", MATCH_CONTACTS_ETAGS);
        mUrlMatcher.addURI(authority, "contactsEtag/#", MATCH_CONTACTS_ETAG);

        mUrlMatcher.addURI(authority, "presence", MATCH_PRESENCE);
        mUrlMatcher.addURI(authority, "presence/#", MATCH_PRESENCE_ID);
        mUrlMatcher.addURI(authority, "presence/account/#", MATCH_PRESENCE_BY_ACCOUNT);
        mUrlMatcher.addURI(authority, "seed_presence/account/#", MATCH_PRESENCE_SEED_BY_ACCOUNT);
        mUrlMatcher.addURI(authority, "bulk_presence", MATCH_PRESENCE_BULK);

        mUrlMatcher.addURI(authority, "messages", MATCH_MESSAGES);
        mUrlMatcher.addURI(authority, "messagesBy/#/#/*", MATCH_MESSAGES_BY_CONTACT);
        mUrlMatcher.addURI(authority, "messages/#", MATCH_MESSAGE);

        mUrlMatcher.addURI(authority, "groupMessages", MATCH_GROUP_MESSAGES);
        mUrlMatcher.addURI(authority, "groupMessagesBy/#", MATCH_GROUP_MESSAGE_BY);
        mUrlMatcher.addURI(authority, "groupMessages/#", MATCH_GROUP_MESSAGE);
        mUrlMatcher.addURI(authority, "groupMembers", MATCH_GROUP_MEMBERS);
        mUrlMatcher.addURI(authority, "groupMembers/#", MATCH_GROUP_MEMBERS_BY_GROUP);

        mUrlMatcher.addURI(authority, "avatars", MATCH_AVATARS);
        mUrlMatcher.addURI(authority, "avatars/#", MATCH_AVATAR);
        mUrlMatcher.addURI(authority, "avatarsBy/#/#", MATCH_AVATAR_BY_PROVIDER);
        mUrlMatcher.addURI(authority, "chats", MATCH_CHATS);
        mUrlMatcher.addURI(authority, "chats/account/#", MATCH_CHATS_BY_ACCOUNT);
        mUrlMatcher.addURI(authority, "chats/#", MATCH_CHATS_ID);

        mUrlMatcher.addURI(authority, "sessionCookies", MATCH_SESSIONS);
        mUrlMatcher.addURI(authority, "sessionCookiesBy/#/#", MATCH_SESSIONS_BY_PROVIDER);
        mUrlMatcher.addURI(authority, "providerSettings", MATCH_PROVIDER_SETTINGS);
        mUrlMatcher.addURI(authority, "providerSettings/#", MATCH_PROVIDER_SETTINGS_BY_ID);
        mUrlMatcher.addURI(authority, "providerSettings/#/*",
                MATCH_PROVIDER_SETTINGS_BY_ID_AND_NAME);

        mUrlMatcher.addURI(authority, "invitations", MATCH_INVITATIONS);
        mUrlMatcher.addURI(authority, "invitations/#", MATCH_INVITATION);

        mUrlMatcher.addURI(authority, "outgoingRmqMessages", MATCH_OUTGOING_RMQ_MESSAGES);
        mUrlMatcher.addURI(authority, "outgoingRmqMessages/#", MATCH_OUTGOING_RMQ_MESSAGE);
        mUrlMatcher.addURI(authority, "outgoingHighestRmqId", MATCH_OUTGOING_HIGHEST_RMQ_ID);
        mUrlMatcher.addURI(authority, "lastRmqId", MATCH_LAST_RMQ_ID);

        mUrlMatcher.addURI(authority, "accountStatus", MATCH_ACCOUNTS_STATUS);
        mUrlMatcher.addURI(authority, "accountStatus/#", MATCH_ACCOUNT_STATUS);

        mUrlMatcher.addURI(authority, "brandingResMapCache", MATCH_BRANDING_RESOURCE_MAP_CACHE);
    }

    @Override
    public boolean onCreate() {
        mOpenHelper = new DatabaseHelper(getContext());
        return true;
    }

    @Override
    public final int update(final Uri url, final ContentValues values,
            final String selection, final String[] selectionArgs) {

        int result = 0;
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            result = updateInternal(url, values, selection, selectionArgs);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        if (result > 0) {
            getContext().getContentResolver()
                    .notifyChange(url, null /* observer */, false /* sync */);
        }
        return result;
    }

    @Override
    public final int delete(final Uri url, final String selection,
            final String[] selectionArgs) {
        int result;
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            result = deleteInternal(url, selection, selectionArgs);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        if (result > 0) {
            getContext().getContentResolver()
                    .notifyChange(url, null /* observer */, false /* sync */);
        }
        return result;
    }

    @Override
    public final Uri insert(final Uri url, final ContentValues values) {
        Uri result;
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            result = insertInternal(url, values);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        if (result != null) {
            getContext().getContentResolver()
                    .notifyChange(url, null /* observer */, false /* sync */);
        }
        return result;
    }

    @Override
    public final Cursor query(final Uri url, final String[] projection,
            final String selection, final String[] selectionArgs,
            final String sortOrder) {
        return queryInternal(url, projection, selection, selectionArgs, sortOrder);
    }

    public Cursor queryInternal(Uri url, String[] projectionIn,
            String selection, String[] selectionArgs, String sort) {
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        StringBuilder whereClause = new StringBuilder();
        if(selection != null) {
            whereClause.append(selection);
        }
        String groupBy = null;
        String limit = null;

        // Generate the body of the query
        int match = mUrlMatcher.match(url);

        if (DBG) {
            log("query " + url + ", match " + match + ", where " + selection);
            if (selectionArgs != null) {
                for (String selectionArg : selectionArgs) {
                    log("     selectionArg: " + selectionArg);
                }
            }
        }

        switch (match) {
            case MATCH_PROVIDERS_BY_ID:
                appendWhere(whereClause, Im.Provider._ID, "=", url.getPathSegments().get(1));
                // fall thru.

            case MATCH_PROVIDERS:
                qb.setTables(TABLE_PROVIDERS);
                break;

            case MATCH_PROVIDERS_WITH_ACCOUNT:
                qb.setTables(PROVIDER_JOIN_ACCOUNT_TABLE);
                qb.setProjectionMap(sProviderAccountsProjectionMap);
                break;

            case MATCH_ACCOUNTS_BY_ID:
                appendWhere(whereClause, Im.Account._ID, "=", url.getPathSegments().get(1));
                // falls down
            case MATCH_ACCOUNTS:
                qb.setTables(TABLE_ACCOUNTS);
                break;

            case MATCH_CONTACTS:
                qb.setTables(CONTACT_JOIN_PRESENCE_CHAT_AVATAR_TABLE);
                qb.setProjectionMap(sContactsProjectionMap);
                break;

            case MATCH_CONTACTS_JOIN_PRESENCE:
                qb.setTables(CONTACT_JOIN_PRESENCE_TABLE);
                qb.setProjectionMap(sContactsProjectionMap);
                break;

            case MATCH_CONTACTS_BAREBONE:
                qb.setTables(TABLE_CONTACTS);
                break;

            case MATCH_CHATTING_CONTACTS:
                qb.setTables(CONTACT_JOIN_PRESENCE_CHAT_AVATAR_TABLE);
                qb.setProjectionMap(sContactsProjectionMap);
                appendWhere(whereClause, "chats.last_message_date IS NOT NULL");
                // no need to add the non blocked contacts clause because
                // blocked contacts can't have conversations.
                break;

            case MATCH_CONTACTS_BY_PROVIDER:
                buildQueryContactsByProvider(qb, whereClause, url);
                appendWhere(whereClause, NON_BLOCKED_CONTACTS_WHERE_CLAUSE);
                break;

            case MATCH_CHATTING_CONTACTS_BY_PROVIDER:
                buildQueryContactsByProvider(qb, whereClause, url);
                appendWhere(whereClause, "chats.last_message_date IS NOT NULL");
                // no need to add the non blocked contacts clause because
                // blocked contacts can't have conversations.
                break;

            case MATCH_NO_CHATTING_CONTACTS_BY_PROVIDER:
                buildQueryContactsByProvider(qb, whereClause, url);
                appendWhere(whereClause, "chats.last_message_date IS NULL");
                appendWhere(whereClause, NON_BLOCKED_CONTACTS_WHERE_CLAUSE);
                break;

            case MATCH_ONLINE_CONTACTS_BY_PROVIDER:
                buildQueryContactsByProvider(qb, whereClause, url);
                appendWhere(whereClause, Im.Contacts.PRESENCE_STATUS, "!=", Im.Presence.OFFLINE);
                appendWhere(whereClause, NON_BLOCKED_CONTACTS_WHERE_CLAUSE);
                break;

            case MATCH_OFFLINE_CONTACTS_BY_PROVIDER:
                buildQueryContactsByProvider(qb, whereClause, url);
                appendWhere(whereClause, Im.Contacts.PRESENCE_STATUS, "=", Im.Presence.OFFLINE);
                appendWhere(whereClause, NON_BLOCKED_CONTACTS_WHERE_CLAUSE);
                break;

            case MATCH_BLOCKED_CONTACTS:
                qb.setTables(CONTACT_JOIN_PRESENCE_CHAT_AVATAR_TABLE);
                qb.setProjectionMap(sContactsProjectionMap);
                appendWhere(whereClause, BLOCKED_CONTACTS_WHERE_CLAUSE);
                break;

            case MATCH_CONTACT:
                qb.setTables(CONTACT_JOIN_PRESENCE_CHAT_AVATAR_TABLE);
                qb.setProjectionMap(sContactsProjectionMap);
                appendWhere(whereClause, "contacts._id", "=", url.getPathSegments().get(1));
                break;

            case MATCH_ONLINE_CONTACT_COUNT:
                qb.setTables(CONTACT_JOIN_PRESENCE_CHAT_TABLE);
                qb.setProjectionMap(sContactsProjectionMap);
                appendWhere(whereClause, Im.Contacts.PRESENCE_STATUS, "!=", Im.Presence.OFFLINE);
                appendWhere(whereClause, "chats.last_message_date IS NULL");
                appendWhere(whereClause, NON_BLOCKED_CONTACTS_WHERE_CLAUSE);
                groupBy = Im.Contacts.CONTACTLIST;
                break;

            case MATCH_CONTACTLISTS_BY_PROVIDER:
                appendWhere(whereClause, Im.ContactList.ACCOUNT, "=",
                        url.getPathSegments().get(2));
                // fall through
            case MATCH_CONTACTLISTS:
                qb.setTables(TABLE_CONTACT_LIST);
                qb.setProjectionMap(sContactListProjectionMap);
                break;

            case MATCH_CONTACTLIST:
                qb.setTables(TABLE_CONTACT_LIST);
                appendWhere(whereClause, Im.ContactList._ID, "=", url.getPathSegments().get(1));
                break;

            case MATCH_BLOCKEDLIST:
                qb.setTables(BLOCKEDLIST_JOIN_AVATAR_TABLE);
                qb.setProjectionMap(sBlockedListProjectionMap);
                break;

            case MATCH_BLOCKEDLIST_BY_PROVIDER:
                qb.setTables(BLOCKEDLIST_JOIN_AVATAR_TABLE);
                qb.setProjectionMap(sBlockedListProjectionMap);
                appendWhere(whereClause, Im.BlockedList.ACCOUNT, "=",
                        url.getPathSegments().get(2));
                break;

            case MATCH_CONTACTS_ETAGS:
                qb.setTables(TABLE_CONTACTS_ETAG);
                break;

            case MATCH_CONTACTS_ETAG:
                qb.setTables(TABLE_CONTACTS_ETAG);
                appendWhere(whereClause, "_id", "=", url.getPathSegments().get(1));
                break;

            case MATCH_MESSAGES:
                qb.setTables(TABLE_MESSAGES);
                break;

            case MATCH_MESSAGES_BY_CONTACT:
                // we don't really need the provider id in query. account id
                // is enough.
                qb.setTables(TABLE_MESSAGES);
                appendWhere(whereClause, Im.Messages.ACCOUNT, "=",
                        url.getPathSegments().get(2));
                appendWhere(whereClause, Im.Messages.CONTACT, "=",
                    decodeURLSegment(url.getPathSegments().get(3)));
                break;

            case MATCH_MESSAGE:
                qb.setTables(TABLE_MESSAGES);
                appendWhere(whereClause, Im.Messages._ID, "=", url.getPathSegments().get(1));
                break;

            case MATCH_INVITATIONS:
                qb.setTables(TABLE_INVITATIONS);
                break;

            case MATCH_INVITATION:
                qb.setTables(TABLE_INVITATIONS);
                appendWhere(whereClause, Im.Invitation._ID, "=", url.getPathSegments().get(1));
                break;

            case MATCH_GROUP_MEMBERS:
                qb.setTables(TABLE_GROUP_MEMBERS);
                break;

            case MATCH_GROUP_MEMBERS_BY_GROUP:
                qb.setTables(TABLE_GROUP_MEMBERS);
                appendWhere(whereClause, Im.GroupMembers.GROUP, "=", url.getPathSegments().get(1));
                break;

            case MATCH_GROUP_MESSAGES:
                qb.setTables(TABLE_GROUP_MESSAGES);
                break;

            case MATCH_GROUP_MESSAGE_BY:
                qb.setTables(TABLE_GROUP_MESSAGES);
                appendWhere(whereClause, Im.GroupMessages.GROUP, "=",
                        url.getPathSegments().get(1));
                break;

            case MATCH_GROUP_MESSAGE:
                qb.setTables(TABLE_GROUP_MESSAGES);
                appendWhere(whereClause, Im.GroupMessages._ID, "=",
                        url.getPathSegments().get(1));
                break;

            case MATCH_AVATARS:
                qb.setTables(TABLE_AVATARS);
                break;

            case MATCH_AVATAR_BY_PROVIDER:
                qb.setTables(TABLE_AVATARS);
                appendWhere(whereClause, Im.Avatars.ACCOUNT, "=", url.getPathSegments().get(2));
                break;

            case MATCH_CHATS:
                qb.setTables(TABLE_CHATS);
                break;

            case MATCH_CHATS_ID:
                qb.setTables(TABLE_CHATS);
                appendWhere(whereClause, Im.Chats.CONTACT_ID, "=", url.getPathSegments().get(1));
                break;

            case MATCH_PRESENCE:
                qb.setTables(TABLE_PRESENCE);
                break;

            case MATCH_PRESENCE_ID:
                qb.setTables(TABLE_PRESENCE);
                appendWhere(whereClause, Im.Presence.CONTACT_ID, "=", url.getPathSegments().get(1));
                break;

            case MATCH_SESSIONS:
                qb.setTables(TABLE_SESSION_COOKIES);
                break;

            case MATCH_SESSIONS_BY_PROVIDER:
                qb.setTables(TABLE_SESSION_COOKIES);
                appendWhere(whereClause, Im.SessionCookies.ACCOUNT, "=", url.getPathSegments().get(2));
                break;

            case MATCH_PROVIDER_SETTINGS_BY_ID_AND_NAME:
                appendWhere(whereClause, Im.ProviderSettings.NAME, "=", url.getPathSegments().get(2));
                // fall through
            case MATCH_PROVIDER_SETTINGS_BY_ID:
                appendWhere(whereClause, Im.ProviderSettings.PROVIDER, "=", url.getPathSegments().get(1));
                // fall through
            case MATCH_PROVIDER_SETTINGS:
                qb.setTables(TABLE_PROVIDER_SETTINGS);
                break;

            case MATCH_OUTGOING_RMQ_MESSAGES:
                qb.setTables(TABLE_OUTGOING_RMQ_MESSAGES);
                break;

            case MATCH_OUTGOING_HIGHEST_RMQ_ID:
                qb.setTables(TABLE_OUTGOING_RMQ_MESSAGES);
                sort = "rmq_id DESC";
                limit = "1";
                break;

            case MATCH_LAST_RMQ_ID:
                qb.setTables(TABLE_LAST_RMQ_ID);
                limit = "1";
                break;

            case MATCH_ACCOUNTS_STATUS:
                qb.setTables(TABLE_ACCOUNT_STATUS);
                break;

            case MATCH_ACCOUNT_STATUS:
                qb.setTables(TABLE_ACCOUNT_STATUS);
                appendWhere(whereClause, Im.AccountStatus.ACCOUNT, "=",
                        url.getPathSegments().get(1));
                break;

            case MATCH_BRANDING_RESOURCE_MAP_CACHE:
                qb.setTables(TABLE_BRANDING_RESOURCE_MAP_CACHE);
                break;

            default:
                throw new IllegalArgumentException("Unknown URL " + url);
        }

        // run the query
        final SQLiteDatabase db = mOpenHelper.getReadableDatabase();
        Cursor c = null;

        try {
            c = qb.query(db, projectionIn, whereClause.toString(), selectionArgs,
                    groupBy, null, sort, limit);
            if (c != null) {
                switch(match) {
                case MATCH_CHATTING_CONTACTS:
                case MATCH_CONTACTS_BY_PROVIDER:
                case MATCH_CHATTING_CONTACTS_BY_PROVIDER:
                case MATCH_ONLINE_CONTACTS_BY_PROVIDER:
                case MATCH_OFFLINE_CONTACTS_BY_PROVIDER:
                case MATCH_CONTACTS_BAREBONE:
                case MATCH_CONTACTS_JOIN_PRESENCE:
                case MATCH_ONLINE_CONTACT_COUNT:
                    url = Im.Contacts.CONTENT_URI;
                    break;
                }
                if (DBG) log("set notify url " + url);
                c.setNotificationUri(getContext().getContentResolver(), url);
            }
        } catch (Exception ex) {
            Log.e(LOG_TAG, "query db caught ", ex);
        }

        return c;
    }

    private void buildQueryContactsByProvider(SQLiteQueryBuilder qb,
            StringBuilder whereClause, Uri url) {
        qb.setTables(CONTACT_JOIN_PRESENCE_CHAT_AVATAR_TABLE);
        qb.setProjectionMap(sContactsProjectionMap);
        // we don't really need the provider id in query. account id
        // is enough.
        appendWhere(whereClause, Im.Contacts.ACCOUNT, "=", url.getLastPathSegment());
    }

    @Override
    public String getType(Uri url) {
        int match = mUrlMatcher.match(url);
        switch (match) {
            case MATCH_PROVIDERS:
                return Im.Provider.CONTENT_TYPE;

            case MATCH_PROVIDERS_BY_ID:
                return Im.Provider.CONTENT_ITEM_TYPE;
            
            case MATCH_ACCOUNTS:
                return Im.Account.CONTENT_TYPE;

            case MATCH_ACCOUNTS_BY_ID:
                return Im.Account.CONTENT_ITEM_TYPE;

            case MATCH_CONTACTS:
            case MATCH_CONTACTS_BY_PROVIDER:
            case MATCH_ONLINE_CONTACTS_BY_PROVIDER:
            case MATCH_OFFLINE_CONTACTS_BY_PROVIDER:
            case MATCH_CONTACTS_BULK:
            case MATCH_CONTACTS_BAREBONE:
            case MATCH_CONTACTS_JOIN_PRESENCE:
                return Im.Contacts.CONTENT_TYPE;

            case MATCH_CONTACT:
                return Im.Contacts.CONTENT_ITEM_TYPE;

            case MATCH_CONTACTLISTS:
            case MATCH_CONTACTLISTS_BY_PROVIDER:
                return Im.ContactList.CONTENT_TYPE;

            case MATCH_CONTACTLIST:
                return Im.ContactList.CONTENT_ITEM_TYPE;

            case MATCH_BLOCKEDLIST:
            case MATCH_BLOCKEDLIST_BY_PROVIDER:
                return Im.BlockedList.CONTENT_TYPE;

            case MATCH_CONTACTS_ETAGS:
            case MATCH_CONTACTS_ETAG:
                return Im.ContactsEtag.CONTENT_TYPE;

            case MATCH_MESSAGES:
            case MATCH_MESSAGES_BY_CONTACT:
                return Im.Messages.CONTENT_TYPE;

            case MATCH_MESSAGE:
                return Im.Messages.CONTENT_ITEM_TYPE;

            case MATCH_GROUP_MESSAGES:
            case MATCH_GROUP_MESSAGE_BY:
                return Im.GroupMessages.CONTENT_TYPE;

            case MATCH_GROUP_MESSAGE:
                return Im.GroupMessages.CONTENT_ITEM_TYPE;

            case MATCH_PRESENCE:
            case MATCH_PRESENCE_BULK:
                return Im.Presence.CONTENT_TYPE;

            case MATCH_AVATARS:
                return Im.Avatars.CONTENT_TYPE;

            case MATCH_AVATAR:
                return Im.Avatars.CONTENT_ITEM_TYPE;

            case MATCH_CHATS:
                return Im.Chats.CONTENT_TYPE;

            case MATCH_CHATS_ID:
                return Im.Chats.CONTENT_ITEM_TYPE;

            case MATCH_INVITATIONS:
                return Im.Invitation.CONTENT_TYPE;

            case MATCH_INVITATION:
                return Im.Invitation.CONTENT_ITEM_TYPE;

            case MATCH_GROUP_MEMBERS:
            case MATCH_GROUP_MEMBERS_BY_GROUP:
                return Im.GroupMembers.CONTENT_TYPE;

            case MATCH_SESSIONS:
            case MATCH_SESSIONS_BY_PROVIDER:
                return Im.SessionCookies.CONTENT_TYPE;

            case MATCH_PROVIDER_SETTINGS:
                return Im.ProviderSettings.CONTENT_TYPE;

            case MATCH_ACCOUNTS_STATUS:
                return Im.AccountStatus.CONTENT_TYPE;

            case MATCH_ACCOUNT_STATUS:
                return Im.AccountStatus.CONTENT_ITEM_TYPE;

            default:
                throw new IllegalArgumentException("Unknown URL");
        }
    }

    // package scope for testing.
    boolean insertBulkContacts(ContentValues values) {
        //if (DBG) log("insertBulkContacts: begin");
        
        ArrayList<String> usernames = values.getStringArrayList(Im.Contacts.USERNAME);
        ArrayList<String> nicknames = values.getStringArrayList(Im.Contacts.NICKNAME);
        int usernameCount = usernames.size();
        int nicknameCount = nicknames.size();

        if (usernameCount != nicknameCount) {
            Log.e(LOG_TAG, "[ImProvider] insertBulkContacts: input bundle " +
                    "username & nickname lists have diff. length!");
            return false;
        }

        ArrayList<String> contactTypeArray = values.getStringArrayList(Im.Contacts.TYPE);
        ArrayList<String> subscriptionStatusArray =
                values.getStringArrayList(Im.Contacts.SUBSCRIPTION_STATUS);
        ArrayList<String> subscriptionTypeArray =
                values.getStringArrayList(Im.Contacts.SUBSCRIPTION_TYPE);
        ArrayList<String> quickContactArray = values.getStringArrayList(Im.Contacts.QUICK_CONTACT);
        ArrayList<String> rejectedArray = values.getStringArrayList(Im.Contacts.REJECTED);
        int sum = 0;
            
        final SQLiteDatabase db = mOpenHelper.getWritableDatabase();

        db.beginTransaction();
        try {
            Long provider = values.getAsLong(Im.Contacts.PROVIDER);
            Long account = values.getAsLong(Im.Contacts.ACCOUNT);
            Long listId = values.getAsLong(Im.Contacts.CONTACTLIST);

            ContentValues contactValues = new ContentValues();
            contactValues.put(Im.Contacts.PROVIDER, provider);
            contactValues.put(Im.Contacts.ACCOUNT, account);
            contactValues.put(Im.Contacts.CONTACTLIST, listId);
            ContentValues presenceValues = new ContentValues();
            presenceValues.put(Im.Presence.PRESENCE_STATUS,
                    Im.Presence.OFFLINE);

            for (int i=0; i<usernameCount; i++) {
                String username = usernames.get(i);
                String nickname = nicknames.get(i);
                int type = 0;
                int subscriptionStatus = 0;
                int subscriptionType = 0;
                int quickContact = 0;
                int rejected = 0;

                try {
                    type = Integer.parseInt(contactTypeArray.get(i));
                    if (subscriptionStatusArray != null) {
                        subscriptionStatus = Integer.parseInt(subscriptionStatusArray.get(i));
                    }
                    if (subscriptionTypeArray != null) {
                        subscriptionType = Integer.parseInt(subscriptionTypeArray.get(i));
                    }
                    if (quickContactArray != null) {
                        quickContact = Integer.parseInt(quickContactArray.get(i));
                    }
                    if (rejectedArray != null) {
                        rejected = Integer.parseInt(rejectedArray.get(i));
                    }
                } catch (NumberFormatException ex) {
                    Log.e(LOG_TAG, "insertBulkContacts: caught " + ex);
                }

                /*
                if (DBG) log("insertBulkContacts[" + i + "] username=" +
                        username + ", nickname=" + nickname + ", type=" + type +
                        ", subscriptionStatus=" + subscriptionStatus + ", subscriptionType=" +
                        subscriptionType + ", qc=" + quickContact);
                */

                contactValues.put(Im.Contacts.USERNAME, username);
                contactValues.put(Im.Contacts.NICKNAME, nickname);
                contactValues.put(Im.Contacts.TYPE, type);
                if (subscriptionStatusArray != null) {
                    contactValues.put(Im.Contacts.SUBSCRIPTION_STATUS, subscriptionStatus);
                }
                if (subscriptionTypeArray != null) {
                    contactValues.put(Im.Contacts.SUBSCRIPTION_TYPE, subscriptionType);
                }
                if (quickContactArray != null) {
                    contactValues.put(Im.Contacts.QUICK_CONTACT, quickContact);
                }
                if (rejectedArray != null) {
                    contactValues.put(Im.Contacts.REJECTED, rejected);
                }

                long rowId = 0;

                /* save this code for when we add constraint (account, username) to the contacts
                   table
                try {
                    rowId = db.insertOrThrow(TABLE_CONTACTS, USERNAME, contactValues);
                } catch (android.database.sqlite.SQLiteConstraintException ex) {
                    if (DBG) log("insertBulkContacts: insert " + username + " caught " + ex);
                    
                    // append username to the selection clause
                    updateSelection.delete(0, updateSelection.length());
                    updateSelection.append(Im.Contacts.USERNAME);
                    updateSelection.append("=?");
                    updateSelectionArgs[0] = username;

                    int updated = db.update(TABLE_CONTACTS, contactValues,
                            updateSelection.toString(), updateSelectionArgs);

                    if (DBG && updated != 1) {
                        log("insertBulkContacts: update " + username + " failed!");
                    }
                }
                */

                rowId = db.insert(TABLE_CONTACTS, USERNAME, contactValues);
                if (rowId > 0) {
                    sum++;
                    if (!USE_CONTACT_PRESENCE_TRIGGER) {
                        // seed the presence for the new contact
                        //if (DBG) log("seedPresence for pid " + rowId);
                        presenceValues.put(Im.Presence.CONTACT_ID, rowId);
                        db.insert(TABLE_PRESENCE, null, presenceValues);
                    }
                }
                
                // yield the lock if anyone else is trying to
                // perform a db operation here.
                db.yieldIfContended();
            }

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        // We know that we succeeded becuase endTransaction throws if the transaction failed.
        if (DBG) log("insertBulkContacts: added " + sum + " contacts!");
        return true;
    }

    // package scope for testing.
    int updateBulkContacts(ContentValues values, String userWhere) {
        ArrayList<String> usernames = values.getStringArrayList(Im.Contacts.USERNAME);
        ArrayList<String> nicknames = values.getStringArrayList(Im.Contacts.NICKNAME);

        int usernameCount = usernames.size();
        int nicknameCount = nicknames.size();

        if (usernameCount != nicknameCount) {
            Log.e(LOG_TAG, "[ImProvider] updateBulkContacts: input bundle " +
                    "username & nickname lists have diff. length!");
            return 0;
        }

        ArrayList<String> contactTypeArray = values.getStringArrayList(Im.Contacts.TYPE);
        ArrayList<String> subscriptionStatusArray =
                values.getStringArrayList(Im.Contacts.SUBSCRIPTION_STATUS);
        ArrayList<String> subscriptionTypeArray =
                values.getStringArrayList(Im.Contacts.SUBSCRIPTION_TYPE);
        ArrayList<String> quickContactArray = values.getStringArrayList(Im.Contacts.QUICK_CONTACT);
        ArrayList<String> rejectedArray = values.getStringArrayList(Im.Contacts.REJECTED);
        final SQLiteDatabase db = mOpenHelper.getWritableDatabase();

        db.beginTransaction();
        int sum = 0;

        try {
            Long provider = values.getAsLong(Im.Contacts.PROVIDER);
            Long account = values.getAsLong(Im.Contacts.ACCOUNT);

            ContentValues contactValues = new ContentValues();
            contactValues.put(Im.Contacts.PROVIDER, provider);
            contactValues.put(Im.Contacts.ACCOUNT, account);

            StringBuilder updateSelection = new StringBuilder();
            String[] updateSelectionArgs = new String[1];

            for (int i=0; i<usernameCount; i++) {
                String username = usernames.get(i);
                String nickname = nicknames.get(i);
                int type = 0;
                int subscriptionStatus = 0;
                int subscriptionType = 0;
                int quickContact = 0;
                int rejected = 0;

                try {
                    type = Integer.parseInt(contactTypeArray.get(i));
                    subscriptionStatus = Integer.parseInt(subscriptionStatusArray.get(i));
                    subscriptionType = Integer.parseInt(subscriptionTypeArray.get(i));
                    quickContact = Integer.parseInt(quickContactArray.get(i));
                    rejected = Integer.parseInt(rejectedArray.get(i));
                } catch (NumberFormatException ex) {
                    Log.e(LOG_TAG, "insertBulkContacts: caught " + ex);
                }

                if (DBG) log("updateBulkContacts[" + i + "] username=" +
                        username + ", nickname=" + nickname + ", type=" + type +
                        ", subscriptionStatus=" + subscriptionStatus + ", subscriptionType=" +
                        subscriptionType + ", qc=" + quickContact);

                contactValues.put(Im.Contacts.USERNAME, username);
                contactValues.put(Im.Contacts.NICKNAME, nickname);
                contactValues.put(Im.Contacts.TYPE, type);
                contactValues.put(Im.Contacts.SUBSCRIPTION_STATUS, subscriptionStatus);
                contactValues.put(Im.Contacts.SUBSCRIPTION_TYPE, subscriptionType);
                contactValues.put(Im.Contacts.QUICK_CONTACT, quickContact);
                contactValues.put(Im.Contacts.REJECTED, rejected);

                // append username to the selection clause
                updateSelection.delete(0, updateSelection.length());
                updateSelection.append(userWhere);
                updateSelection.append(" AND ");
                updateSelection.append(Im.Contacts.USERNAME);
                updateSelection.append("=?");

                updateSelectionArgs[0] = username;

                int numUpdated = db.update(TABLE_CONTACTS, contactValues,
                        updateSelection.toString(), updateSelectionArgs);
                if (numUpdated == 0) {
                    Log.e(LOG_TAG, "[ImProvider] updateBulkContacts: " +
                            " update failed for selection = " + updateSelection);
                } else {
                    sum += numUpdated;
                }

                // yield the lock if anyone else is trying to
                // perform a db operation here.
                db.yieldIfContended();
            }

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        if (DBG) log("updateBulkContacts: " + sum + " entries updated");
        return sum;
    }

    // constants definitions use for the query in seedInitialPresenceByAccount()
    private static final String[] CONTACT_ID_PROJECTION = new String[] {
            Im.Contacts._ID,    // 0
    };

    private static final int COLUMN_ID = 0;

    private static final String CONTACTS_WITH_NO_PRESENCE_SELECTION =
            Im.Contacts.ACCOUNT + "=?" + " AND " + Im.Contacts._ID +
                    " in (select " + CONTACT_ID + " from " + TABLE_CONTACTS +
                    " left outer join " + TABLE_PRESENCE + " on " + CONTACT_ID + '=' +
                    PRESENCE_CONTACT_ID + " where " + PRESENCE_CONTACT_ID + " IS NULL)";

    // selection args for the query.
    private String[] mQueryContactPresenceSelectionArgs = new String[1];

    /**
     * This method first performs a query for all the contacts (for the given account) that
     * don't have a presence entry in the presence table. Then for each of those contacts,
     * the method creates a presence row. The whole thing is done inside one database transaction
     * to increase performance.
     *
     * @param account the account of the contacts for which we want to create seed presence rows.
     */
    private void seedInitialPresenceByAccount(long account) {
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(TABLE_CONTACTS);
        qb.setProjectionMap(sContactsProjectionMap);

        mQueryContactPresenceSelectionArgs[0] = String.valueOf(account);

        final SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        db.beginTransaction();

        Cursor c = null;

        try {
            ContentValues presenceValues = new ContentValues();
            presenceValues.put(Im.Presence.PRESENCE_STATUS, Im.Presence.OFFLINE);
            presenceValues.put(Im.Presence.PRESENCE_CUSTOM_STATUS, "");

            // First: update all the presence for the account so they are offline
            StringBuilder buf = new StringBuilder();
            buf.append(Im.Presence.CONTACT_ID);
            buf.append(" in (select ");
            buf.append(Im.Contacts._ID);
            buf.append(" from ");
            buf.append(TABLE_CONTACTS);
            buf.append(" where ");
            buf.append(Im.Contacts.ACCOUNT);
            buf.append("=?) ");

            String selection = buf.toString();
            if (DBG) log("seedInitialPresence: reset presence selection=" + selection);

            int count = db.update(TABLE_PRESENCE, presenceValues, selection,
                    mQueryContactPresenceSelectionArgs);
            if (DBG) log("seedInitialPresence: reset " + count + " presence rows to OFFLINE");

            // second: add a presence row for each contact that doesn't have a presence
            if (DBG) {
                log("seedInitialPresence: contacts_with_no_presence_selection => " +
                        CONTACTS_WITH_NO_PRESENCE_SELECTION);
            }

            c = qb.query(db,
                    CONTACT_ID_PROJECTION,
                    CONTACTS_WITH_NO_PRESENCE_SELECTION,
                    mQueryContactPresenceSelectionArgs,
                    null, null, null, null);

            if (DBG) log("seedInitialPresence: found " + c.getCount() + " contacts w/o presence");

            count = 0;

            while (c.moveToNext()) {
                long id = c.getLong(COLUMN_ID);
                presenceValues.put(Im.Presence.CONTACT_ID, id);

                try {
                    if (db.insert(TABLE_PRESENCE, null, presenceValues) > 0) {
                        count++;
                    }
                } catch (SQLiteConstraintException ex) {
                    // we could possibly catch this exception, since there could be a presence
                    // row with the same contact_id. That's fine, just ignore the error
                    if (DBG) log("seedInitialPresence: insert presence for contact_id " + id +
                            " failed, caught " + ex);
                }
            }

            db.setTransactionSuccessful();

            if (DBG) log("seedInitialPresence: added " + count + " new presence rows");
        } finally {
            c.close();
            db.endTransaction();
        }
    }

    private int updateBulkPresence(ContentValues values, String userWhere, String[] whereArgs) {
        ArrayList<String> usernames = values.getStringArrayList(Im.Contacts.USERNAME);
        int count = usernames.size();
        Long account = values.getAsLong(Im.Contacts.ACCOUNT);

        ArrayList<String> priorityArray = values.getStringArrayList(Im.Presence.PRIORITY);
        ArrayList<String> modeArray = values.getStringArrayList(Im.Presence.PRESENCE_STATUS);
        ArrayList<String> statusArray = values.getStringArrayList(
                Im.Presence.PRESENCE_CUSTOM_STATUS);
        ArrayList<String> clientTypeArray = values.getStringArrayList(Im.Presence.CLIENT_TYPE);
        ArrayList<String> resourceArray = values.getStringArrayList(Im.Presence.JID_RESOURCE);

        // append username to the selection clause
        StringBuilder buf = new StringBuilder();

        if (!TextUtils.isEmpty(userWhere)) {
            buf.append(userWhere);
            buf.append(" AND ");
        }

        buf.append(Im.Presence.CONTACT_ID);
        buf.append(" in (select ");
        buf.append(Im.Contacts._ID);
        buf.append(" from ");
        buf.append(TABLE_CONTACTS);
        buf.append(" where ");
        buf.append(Im.Contacts.ACCOUNT);
        buf.append("=? AND ");

        // use username LIKE ? for case insensitive comparison
        buf.append(Im.Contacts.USERNAME);
        buf.append(" LIKE ?) AND (");

        buf.append(Im.Presence.PRIORITY);
        buf.append("<=? OR ");
        buf.append(Im.Presence.PRIORITY);
        buf.append(" IS NULL OR ");
        buf.append(Im.Presence.JID_RESOURCE);
        buf.append("=?)");

        String selection = buf.toString();

        if (DBG) log("updateBulkPresence: selection => " + selection);

        int numArgs = (whereArgs != null ? whereArgs.length + 4 : 4);
        String[] selectionArgs = new String[numArgs];
        int selArgsIndex = 0;

        if (whereArgs != null) {
            for (selArgsIndex=0; selArgsIndex<numArgs-1; selArgsIndex++) {
                selectionArgs[selArgsIndex] = whereArgs[selArgsIndex];
            }
        }

        final SQLiteDatabase db = mOpenHelper.getWritableDatabase();

        db.beginTransaction();
        int sum = 0;

        try {
            ContentValues presenceValues = new ContentValues();

            for (int i=0; i<count; i++) {
                String username = usernames.get(i);
                int priority = 0;
                int mode = 0;
                String status = statusArray.get(i);
                String jidResource = resourceArray == null ? "" : resourceArray.get(i);
                int clientType = Im.Presence.CLIENT_TYPE_DEFAULT;

                try {
                    if (priorityArray != null) {
                        priority = Integer.parseInt(priorityArray.get(i));
                    }
                    if (modeArray != null) {
                        mode = Integer.parseInt(modeArray.get(i));
                    }
                    if (clientTypeArray != null) {
                        clientType = Integer.parseInt(clientTypeArray.get(i));
                    }
                } catch (NumberFormatException ex) {
                    Log.e(LOG_TAG, "[ImProvider] updateBulkPresence: caught " + ex);
                }

                /*
                if (DBG) {
                    log("updateBulkPresence[" + i + "] username=" + username + ", priority=" +
                            priority + ", mode=" + mode + ", status=" + status + ", resource=" +
                            jidResource + ", clientType=" + clientType);
                }
                */

                if (modeArray != null) {
                    presenceValues.put(Im.Presence.PRESENCE_STATUS, mode);
                }
                if (priorityArray != null) {
                    presenceValues.put(Im.Presence.PRIORITY, priority);
                }
                presenceValues.put(Im.Presence.PRESENCE_CUSTOM_STATUS, status);
                if (clientTypeArray != null) {
                    presenceValues.put(Im.Presence.CLIENT_TYPE, clientType);
                }

                if (!TextUtils.isEmpty(jidResource)) {
                    presenceValues.put(Im.Presence.JID_RESOURCE, jidResource);
                }

                // fill in the selection args
                int idx = selArgsIndex;
                selectionArgs[idx++] = String.valueOf(account);
                selectionArgs[idx++] = username;
                selectionArgs[idx++] = String.valueOf(priority);
                selectionArgs[idx] = jidResource;

                int numUpdated = db.update(TABLE_PRESENCE,
                        presenceValues, selection, selectionArgs);
                if (numUpdated == 0) {
                    Log.e(LOG_TAG, "[ImProvider] updateBulkPresence: failed for " + username);
                } else {
                    sum += numUpdated;
                }

                // yield the lock if anyone else is trying to
                // perform a db operation here.
                db.yieldIfContended();
            }

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        if (DBG) log("updateBulkPresence: " + sum + " entries updated");
        return sum;
    }

    public Uri insertInternal(Uri url, ContentValues initialValues) {
        Uri resultUri = null;
        long rowID = 0;
        boolean notifyContactListContentUri = false;
        boolean notifyContactContentUri = false;
        boolean notifyMessagesContentUri = false;
        boolean notifyGroupMessagesContentUri = false;
        boolean notifyProviderAccountContentUri = false;

        final SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int match = mUrlMatcher.match(url);

        if (DBG) log("insert to " + url + ", match " + match);
        switch (match) {
            case MATCH_PROVIDERS:
                // Insert into the providers table
                rowID = db.insert(TABLE_PROVIDERS, "name", initialValues);
                if (rowID > 0) {
                    resultUri = Uri.parse(Im.Provider.CONTENT_URI + "/" + rowID);
                }
                notifyProviderAccountContentUri = true;
                break;

            case MATCH_ACCOUNTS:
                // Insert into the accounts table
                rowID = db.insert(TABLE_ACCOUNTS, "name", initialValues);
                if (rowID > 0) {
                    resultUri = Uri.parse(Im.Account.CONTENT_URI + "/" + rowID);
                }
                notifyProviderAccountContentUri = true;
                break;

            case MATCH_CONTACTS_BY_PROVIDER:
                appendValuesFromUrl(initialValues, url, Im.Contacts.PROVIDER,
                    Im.Contacts.ACCOUNT);
                // fall through
            case MATCH_CONTACTS:
            case MATCH_CONTACTS_BAREBONE:
                // Insert into the contacts table
                rowID = db.insert(TABLE_CONTACTS, "username", initialValues);
                if (rowID > 0) {
                    resultUri = Uri.parse(Im.Contacts.CONTENT_URI + "/" + rowID);
                }

                notifyContactContentUri = true;
                break;

            case MATCH_CONTACTS_BULK:
                if (insertBulkContacts(initialValues)) {
                    // notify change using the "content://im/contacts" url,
                    // so the change will be observed by listeners interested
                    // in contacts changes.
                    resultUri = Im.Contacts.CONTENT_URI;
                }
                notifyContactContentUri = true;
                break;

            case MATCH_CONTACTLISTS_BY_PROVIDER:
                appendValuesFromUrl(initialValues, url, Im.ContactList.PROVIDER,
                        Im.ContactList.ACCOUNT);
                // fall through
            case MATCH_CONTACTLISTS:
                // Insert into the contactList table
                rowID = db.insert(TABLE_CONTACT_LIST, "name", initialValues);
                if (rowID > 0) {
                    resultUri = Uri.parse(Im.ContactList.CONTENT_URI + "/" + rowID);
                }
                notifyContactListContentUri = true;
                break;

            case MATCH_BLOCKEDLIST_BY_PROVIDER:
                appendValuesFromUrl(initialValues, url, Im.BlockedList.PROVIDER,
                    Im.BlockedList.ACCOUNT);
                // fall through
            case MATCH_BLOCKEDLIST:
                // Insert into the blockedList table
                rowID = db.insert(TABLE_BLOCKED_LIST, "username", initialValues);
                if (rowID > 0) {
                    resultUri = Uri.parse(Im.BlockedList.CONTENT_URI + "/" + rowID);
                }

                break;

            case MATCH_CONTACTS_ETAGS:
                rowID = db.replace(TABLE_CONTACTS_ETAG, Im.ContactsEtag.ETAG, initialValues);
                if (rowID > 0) {
                    resultUri = Uri.parse(Im.ContactsEtag.CONTENT_URI + "/" + rowID);
                }
                break;

            case MATCH_MESSAGES_BY_CONTACT:
                appendValuesFromUrl(initialValues, url, Im.Messages.PROVIDER,
                    Im.Messages.ACCOUNT, Im.Messages.CONTACT);
                notifyMessagesContentUri = true;
                // fall through
            case MATCH_MESSAGES:
                // Insert into the messages table.
                rowID = db.insert(TABLE_MESSAGES, "contact", initialValues);
                if (rowID > 0) {
                    resultUri = Uri.parse(Im.Messages.CONTENT_URI + "/" + rowID);
                }

                break;

            case MATCH_INVITATIONS:
                rowID = db.insert(TABLE_INVITATIONS, null, initialValues);
                if (rowID > 0) {
                    resultUri = Uri.parse(Im.Invitation.CONTENT_URI + "/" + rowID);
                }
                break;

            case MATCH_GROUP_MEMBERS:
                rowID = db.insert(TABLE_GROUP_MEMBERS, "nickname", initialValues);
                if (rowID > 0) {
                    resultUri = Uri.parse(Im.GroupMembers.CONTENT_URI + "/" + rowID);
                }
                break;

            case MATCH_GROUP_MEMBERS_BY_GROUP:
                appendValuesFromUrl(initialValues, url, Im.GroupMembers.GROUP);
                rowID = db.insert(TABLE_GROUP_MEMBERS, "nickname", initialValues);
                if (rowID > 0) {
                    resultUri = Uri.parse(Im.GroupMembers.CONTENT_URI + "/" + rowID);
                }
                break;

            case MATCH_GROUP_MESSAGE_BY:
                appendValuesFromUrl(initialValues, url, Im.GroupMembers.GROUP);
                notifyGroupMessagesContentUri = true;
                // fall through
            case MATCH_GROUP_MESSAGES:
                rowID = db.insert(TABLE_GROUP_MESSAGES, "group", initialValues);
                if (rowID > 0) {
                    resultUri = Uri.parse(Im.GroupMessages.CONTENT_URI + "/" + rowID);
                }
                break;

            case MATCH_AVATAR_BY_PROVIDER:
                appendValuesFromUrl(initialValues, url, Im.Avatars.PROVIDER, Im.Avatars.ACCOUNT);
                // fall through
            case MATCH_AVATARS:
                // Insert into the avatars table
                rowID = db.replace(TABLE_AVATARS, "contact", initialValues);
                if (rowID > 0) {
                    resultUri = Uri.parse(Im.Avatars.CONTENT_URI + "/" + rowID);
                }
                break;

            case MATCH_CHATS_ID:
                appendValuesFromUrl(initialValues, url, Im.Chats.CONTACT_ID);
                // fall through
            case MATCH_CHATS:
                // Insert into the chats table
                initialValues.put(Im.Chats.SHORTCUT, -1);
                rowID = db.replace(TABLE_CHATS, Im.Chats.CONTACT_ID, initialValues);
                if (rowID > 0) {
                    resultUri = Uri.parse(Im.Chats.CONTENT_URI + "/" + rowID);
                    addToQuickSwitch(rowID);
                }
                notifyContactContentUri = true;
                break;

            case MATCH_PRESENCE:
                rowID = db.replace(TABLE_PRESENCE, null, initialValues);
                if (rowID > 0) {
                    resultUri = Uri.parse(Im.Presence.CONTENT_URI + "/" + rowID);
                }
                notifyContactContentUri = true;
                break;

            case MATCH_PRESENCE_SEED_BY_ACCOUNT:
                try {
                    seedInitialPresenceByAccount(Long.parseLong(url.getLastPathSegment()));
                    resultUri = Im.Presence.CONTENT_URI;
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException();
                }
                break;

            case MATCH_SESSIONS_BY_PROVIDER:
                appendValuesFromUrl(initialValues, url, Im.SessionCookies.PROVIDER,
                        Im.SessionCookies.ACCOUNT);
                // fall through
            case MATCH_SESSIONS:
                rowID = db.insert(TABLE_SESSION_COOKIES, null, initialValues);
                if(rowID > 0) {
                    resultUri = Uri.parse(Im.SessionCookies.CONTENT_URI + "/" + rowID);
                }
                break;

            case MATCH_PROVIDER_SETTINGS:
                rowID = db.replace(TABLE_PROVIDER_SETTINGS, null, initialValues);
                if (rowID > 0) {
                    resultUri = Uri.parse(Im.ProviderSettings.CONTENT_URI + "/" + rowID);
                }
                break;

            case MATCH_OUTGOING_RMQ_MESSAGES:
                rowID = db.insert(TABLE_OUTGOING_RMQ_MESSAGES, null, initialValues);
                if (rowID > 0) {
                    resultUri = Uri.parse(Im.OutgoingRmq.CONTENT_URI + "/" + rowID);
                }
                break;

            case MATCH_LAST_RMQ_ID:
                rowID = db.replace(TABLE_LAST_RMQ_ID, null, initialValues);
                if (rowID > 0) {
                    resultUri = Uri.parse(Im.LastRmqId.CONTENT_URI + "/" + rowID);
                }
                break;

            case MATCH_ACCOUNTS_STATUS:
                rowID = db.replace(TABLE_ACCOUNT_STATUS, null, initialValues);
                if (rowID > 0) {
                    resultUri = Uri.parse(Im.AccountStatus.CONTENT_URI + "/" + rowID);
                }
                notifyProviderAccountContentUri = true;
                break;

            case MATCH_BRANDING_RESOURCE_MAP_CACHE:
                rowID = db.insert(TABLE_BRANDING_RESOURCE_MAP_CACHE, null, initialValues);
                if (rowID > 0) {
                    resultUri = Uri.parse(Im.BrandingResourceMapCache.CONTENT_URI + "/" + rowID);
                }
                break;

            default:
                throw new UnsupportedOperationException("Cannot insert into URL: " + url);
        }
        // TODO: notify the data change observer?

        if (resultUri != null) {
            ContentResolver resolver = getContext().getContentResolver();

            // In most case, we query contacts with presence and chats joined, thus
            // we should also notify that contacts changes when presence or chats changed.
            if (notifyContactContentUri) {
                resolver.notifyChange(Im.Contacts.CONTENT_URI, null);
            }

            if (notifyContactListContentUri) {
                resolver.notifyChange(Im.ContactList.CONTENT_URI, null);
            }

            if (notifyMessagesContentUri) {
                resolver.notifyChange(Im.Messages.CONTENT_URI, null);
            }

            if (notifyGroupMessagesContentUri) {
                resolver.notifyChange(Im.GroupMessages.CONTENT_URI, null);
            }

            if (notifyProviderAccountContentUri) {
                if (DBG) log("notify insert for " + Im.Provider.CONTENT_URI_WITH_ACCOUNT);
                resolver.notifyChange(Im.Provider.CONTENT_URI_WITH_ACCOUNT,
                        null);
            }
        }
        return resultUri;
    }

    private void appendValuesFromUrl(ContentValues values, Uri url, String...columns){
        if(url.getPathSegments().size() <= columns.length) {
            throw new IllegalArgumentException("Not enough values in url");
        }
        for(int i = 0; i < columns.length; i++){
            if(values.containsKey(columns[i])){
                throw new UnsupportedOperationException("Cannot override the value for " + columns[i]);
            }
            values.put(columns[i], decodeURLSegment(url.getPathSegments().get(i + 1)));
        }
    }

    //  Quick-switch management
    //  The chat UI provides slots (0, 9, .., 1) for the first 10 chats.  This allows you to
    //  quickly switch between these chats by chording menu+#.  We number from the right end of
    //  the number row and move leftward to make an easier two-hand chord with the menu button
    //  on the left side of the keyboard.
    private void addToQuickSwitch(long newRow) {
        //  Since there are fewer than 10, there must be an empty slot.  Let's find it.
        int slot = findEmptyQuickSwitchSlot();

        if (slot == -1) {
            return;
        }

        updateSlotForChat(newRow, slot);
    }

    //  If there are more than 10 chats and one with a quick switch slot ends then pick a chat
    //  that doesn't have a slot and have it inhabit the newly emptied slot.
    private void backfillQuickSwitchSlots() {
        //  Find all the chats without a quick switch slot, and order
        Cursor c = query(Im.Chats.CONTENT_URI,
            BACKFILL_PROJECTION,
            Im.Chats.SHORTCUT + "=-1", null, Im.Chats.LAST_MESSAGE_DATE + " DESC");

        try {
            if (c.getCount() < 1) {
                return;
            }
        
            int slot = findEmptyQuickSwitchSlot();
        
            if (slot != -1) {
                c.moveToFirst();
            
                long id = c.getLong(c.getColumnIndex(Im.Chats._ID));
            
                updateSlotForChat(id, slot);
            }
        } finally {
            c.close();
        }
    }

    private int updateSlotForChat(long chatId, int slot) {
        ContentValues values = new ContentValues();
        
        values.put(Im.Chats.SHORTCUT, slot);
        
        return update(Im.Chats.CONTENT_URI, values, Im.Chats._ID + "=?",
            new String[] { Long.toString(chatId) });
    }

    private int findEmptyQuickSwitchSlot() {
        Cursor c = queryInternal(Im.Chats.CONTENT_URI, FIND_SHORTCUT_PROJECTION, null, null, null);
        final int N = c.getCount();

        try {
            //  If there are 10 or more chats then all the quick switch slots are already filled
            if (N >= 10) {
                return -1;
            }

            int slots = 0;
            int column = c.getColumnIndex(Im.Chats.SHORTCUT);
            
            //  The map is here because numbers go from 0-9, but we want to assign slots in
            //  0, 9, 8, ..., 1 order to match the right-to-left reading of the number row
            //  on the keyboard.
            int[] map = new int[] { 0, 9, 8, 7, 6, 5, 4, 3, 2, 1 };

            //  Mark all the slots that are in use
            //  The shortcuts represent actual keyboard number row keys, and not ordinals.
            //  So 7 would mean the shortcut is the 7 key on the keyboard and NOT the 7th
            //  shortcut.  The passing of slot through map[] below maps these keyboard key
            //  shortcuts into an ordinal bit position in the 'slots' bitfield.
            for (c.moveToFirst(); ! c.isAfterLast(); c.moveToNext()) {
                int slot = c.getInt(column);
                
                if (slot != -1) {
                    slots |= (1 << map[slot]);
                }
            }

            //  Try to find an empty one
            //  As we exit this, the push of i through map[] maps the ordinal bit position
            //  in the 'slots' bitfield onto a key on the number row of the device keyboard.
            //  The keyboard key is what is used to designate the shortcut.
            for (int i = 0; i < 10; i++) {
                if ((slots & (1 << i)) == 0) {
                    return map[i];
                }
            }
            
            return -1;
        } finally {
            c.close();
        }
    }

    /**
     * manual trigger for deleting contacts
     */
    private static final String DELETE_PRESENCE_SELECTION =
            Im.Presence.CONTACT_ID + " in (select " +
            PRESENCE_CONTACT_ID + " from " + TABLE_PRESENCE + " left outer join " + TABLE_CONTACTS +
            " on " + PRESENCE_CONTACT_ID + '=' + CONTACT_ID + " where " + CONTACT_ID + " IS NULL)";

    private static final String CHATS_CONTACT_ID = TABLE_CHATS + '.' + Im.Chats.CONTACT_ID;
    private static final String DELETE_CHATS_SELECTION = Im.Chats.CONTACT_ID + " in (select "+
            CHATS_CONTACT_ID + " from " + TABLE_CHATS + " left outer join " + TABLE_CONTACTS +
            " on " + CHATS_CONTACT_ID + '=' + CONTACT_ID + " where " + CONTACT_ID + " IS NULL)";

    private static final String GROUP_MEMBER_ID = TABLE_GROUP_MEMBERS + '.' + Im.GroupMembers.GROUP;
    private static final String DELETE_GROUP_MEMBER_SELECTION =
            Im.GroupMembers.GROUP + " in (select "+
            GROUP_MEMBER_ID + " from " + TABLE_GROUP_MEMBERS + " left outer join " + TABLE_CONTACTS +
            " on " + GROUP_MEMBER_ID + '=' + CONTACT_ID + " where " + CONTACT_ID + " IS NULL)";

    private static final String GROUP_MESSAGES_ID =
            TABLE_GROUP_MESSAGES + '.' + Im.GroupMessages.GROUP;
    private static final String DELETE_GROUP_MESSAGES_SELECTION =
            Im.GroupMessages.GROUP + " in (select "+ GROUP_MESSAGES_ID + " from " +
                    TABLE_GROUP_MESSAGES + " left outer join " + TABLE_CONTACTS + " on " +
                    GROUP_MESSAGES_ID + '=' + CONTACT_ID + " where " + CONTACT_ID + " IS NULL)";

    private void performContactRemovalCleanup(long contactId) {
        final SQLiteDatabase db = mOpenHelper.getWritableDatabase();

        if (contactId > 0) {
            deleteWithContactId(db, contactId, TABLE_PRESENCE, Im.Presence.CONTACT_ID);
            deleteWithContactId(db, contactId, TABLE_CHATS, Im.Chats.CONTACT_ID);
            deleteWithContactId(db, contactId, TABLE_GROUP_MEMBERS, Im.GroupMembers.GROUP);
            deleteWithContactId(db, contactId, TABLE_GROUP_MESSAGES, Im.GroupMessages.GROUP);
        } else {
            performComplexDelete(db, TABLE_PRESENCE, DELETE_PRESENCE_SELECTION, null);
            performComplexDelete(db, TABLE_CHATS, DELETE_CHATS_SELECTION, null);
            performComplexDelete(db, TABLE_GROUP_MEMBERS, DELETE_GROUP_MEMBER_SELECTION, null);
            performComplexDelete(db, TABLE_GROUP_MESSAGES, DELETE_GROUP_MESSAGES_SELECTION, null);
        }
    }

    private void deleteWithContactId(SQLiteDatabase db, long contactId,
            String tableName, String columnName) {
        db.delete(tableName, columnName + '=' + contactId, null /* selection args */);
    }

    private void performComplexDelete(SQLiteDatabase db, String tableName,
            String selection, String[] selectionArgs) {
        if (DBG) log("performComplexDelete for table " + tableName + ", selection => " + selection);
        int count = db.delete(tableName, selection, selectionArgs);
        if (DBG) log("performComplexDelete: deleted " + count + " rows");
    }

    public int deleteInternal(Uri url, String userWhere,
            String[] whereArgs) {
        String tableToChange;
        String idColumnName = null;
        String changedItemId = null;

        StringBuilder whereClause = new StringBuilder();
        if(userWhere != null) {
            whereClause.append(userWhere);
        }

        boolean notifyMessagesContentUri = false;
        boolean notifyGroupMessagesContentUri = false;
        boolean notifyContactListContentUri = false;
        boolean notifyProviderAccountContentUri = false;
        int match = mUrlMatcher.match(url);

        boolean contactDeleted = false;
        long deletedContactId = 0;

        boolean backfillQuickSwitchSlots = false;
        
        switch (match) {
            case MATCH_PROVIDERS:
                tableToChange = TABLE_PROVIDERS;
                notifyProviderAccountContentUri = true;
                break;

            case MATCH_ACCOUNTS_BY_ID:
                changedItemId = url.getPathSegments().get(1);
                // fall through
            case MATCH_ACCOUNTS:
                tableToChange = TABLE_ACCOUNTS;
                notifyProviderAccountContentUri = true;
                break;

            case MATCH_ACCOUNT_STATUS:
                changedItemId = url.getPathSegments().get(1);
                // fall through
            case MATCH_ACCOUNTS_STATUS:
                tableToChange = TABLE_ACCOUNT_STATUS;
                notifyProviderAccountContentUri = true;
                break;

            case MATCH_CONTACTS:
            case MATCH_CONTACTS_BAREBONE:
                tableToChange = TABLE_CONTACTS;
                contactDeleted = true;
                break;

            case MATCH_CONTACT:
                tableToChange = TABLE_CONTACTS;
                changedItemId = url.getPathSegments().get(1);

                try {
                    deletedContactId = Long.parseLong(changedItemId);
                } catch (NumberFormatException ex) {
                }

                contactDeleted = true;
                break;

            case MATCH_CONTACTS_BY_PROVIDER:
                tableToChange = TABLE_CONTACTS;
                appendWhere(whereClause, Im.Contacts.ACCOUNT, "=", url.getPathSegments().get(2));
                contactDeleted = true;
                break;

            case MATCH_CONTACTLISTS_BY_PROVIDER:
                appendWhere(whereClause, Im.ContactList.ACCOUNT, "=",
                        url.getPathSegments().get(2));
                // fall through
            case MATCH_CONTACTLISTS:
                tableToChange = TABLE_CONTACT_LIST;
                notifyContactListContentUri = true;
                break;

            case MATCH_CONTACTLIST:
                tableToChange = TABLE_CONTACT_LIST;
                changedItemId = url.getPathSegments().get(1);
                break;

            case MATCH_BLOCKEDLIST:
                tableToChange = TABLE_BLOCKED_LIST;
                break;

            case MATCH_BLOCKEDLIST_BY_PROVIDER:
                tableToChange = TABLE_BLOCKED_LIST;
                appendWhere(whereClause, Im.BlockedList.ACCOUNT, "=", url.getPathSegments().get(2));
                break;

            case MATCH_CONTACTS_ETAGS:
                tableToChange = TABLE_CONTACTS_ETAG;
                break;

            case MATCH_CONTACTS_ETAG:
                tableToChange = TABLE_CONTACTS_ETAG;
                changedItemId = url.getPathSegments().get(1);
                break;

            case MATCH_MESSAGES:
                tableToChange = TABLE_MESSAGES;
                break;

            case MATCH_MESSAGES_BY_CONTACT:
                tableToChange = TABLE_MESSAGES;
                appendWhere(whereClause, Im.Messages.ACCOUNT, "=",
                        url.getPathSegments().get(2));
                appendWhere(whereClause, Im.Messages.CONTACT, "=",
                    decodeURLSegment(url.getPathSegments().get(3)));
                notifyMessagesContentUri = true;
                break;

            case MATCH_MESSAGE:
                tableToChange = TABLE_MESSAGES;
                changedItemId = url.getPathSegments().get(1);
                notifyMessagesContentUri = true;
                break;

            case MATCH_GROUP_MEMBERS:
                tableToChange = TABLE_GROUP_MEMBERS;
                break;

            case MATCH_GROUP_MEMBERS_BY_GROUP:
                tableToChange = TABLE_GROUP_MEMBERS;
                appendWhere(whereClause, Im.GroupMembers.GROUP, "=", url.getPathSegments().get(1));
                break;

            case MATCH_GROUP_MESSAGES:
                tableToChange = TABLE_GROUP_MESSAGES;
                break;

            case MATCH_GROUP_MESSAGE_BY:
                tableToChange = TABLE_GROUP_MESSAGES;
                changedItemId = url.getPathSegments().get(1);
                idColumnName = Im.GroupMessages.GROUP;
                notifyGroupMessagesContentUri = true;
                break;

            case MATCH_GROUP_MESSAGE:
                tableToChange = TABLE_GROUP_MESSAGES;
                changedItemId = url.getPathSegments().get(1);
                notifyGroupMessagesContentUri = true;
                break;

            case MATCH_INVITATIONS:
                tableToChange = TABLE_INVITATIONS;
                break;

            case MATCH_INVITATION:
                tableToChange = TABLE_INVITATIONS;
                changedItemId = url.getPathSegments().get(1);
                break;

            case MATCH_AVATARS:
                tableToChange = TABLE_AVATARS;
                break;

            case MATCH_AVATAR:
                tableToChange = TABLE_AVATARS;
                changedItemId = url.getPathSegments().get(1);
                break;

            case MATCH_AVATAR_BY_PROVIDER:
                tableToChange = TABLE_AVATARS;
                changedItemId = url.getPathSegments().get(2);
                idColumnName = Im.Avatars.ACCOUNT;
                break;

            case MATCH_CHATS:
                tableToChange = TABLE_CHATS;
                backfillQuickSwitchSlots = true;
                break;

            case MATCH_CHATS_BY_ACCOUNT:
                tableToChange = TABLE_CHATS;

                if (whereClause.length() > 0) {
                    whereClause.append(" AND ");
                }
                whereClause.append(Im.Chats.CONTACT_ID);
                whereClause.append(" in (select ");
                whereClause.append(Im.Contacts._ID);
                whereClause.append(" from ");
                whereClause.append(TABLE_CONTACTS);
                whereClause.append(" where ");
                whereClause.append(Im.Contacts.ACCOUNT);
                whereClause.append("='");
                whereClause.append(url.getLastPathSegment());
                whereClause.append("')");

                if (DBG) log("deleteInternal (MATCH_CHATS_BY_ACCOUNT): sel => " +
                        whereClause.toString());

                changedItemId = null;
                break;

            case MATCH_CHATS_ID:
                tableToChange = TABLE_CHATS;
                changedItemId = url.getPathSegments().get(1);
                idColumnName = Im.Chats.CONTACT_ID;
                break;

            case MATCH_PRESENCE:
                tableToChange = TABLE_PRESENCE;
                break;

            case MATCH_PRESENCE_ID:
                tableToChange = TABLE_PRESENCE;
                changedItemId = url.getPathSegments().get(1);
                idColumnName = Im.Presence.CONTACT_ID;
                break;

            case MATCH_PRESENCE_BY_ACCOUNT:
                tableToChange = TABLE_PRESENCE;

                if (whereClause.length() > 0) {
                    whereClause.append(" AND ");
                }
                whereClause.append(Im.Presence.CONTACT_ID);
                whereClause.append(" in (select ");
                whereClause.append(Im.Contacts._ID);
                whereClause.append(" from ");
                whereClause.append(TABLE_CONTACTS);
                whereClause.append(" where ");
                whereClause.append(Im.Contacts.ACCOUNT);
                whereClause.append("='");
                whereClause.append(url.getLastPathSegment());
                whereClause.append("')");

                if (DBG) log("deleteInternal (MATCH_PRESENCE_BY_ACCOUNT): sel => " +
                        whereClause.toString());

                changedItemId = null;
                break;

            case MATCH_SESSIONS:
                tableToChange = TABLE_SESSION_COOKIES;
                break;

            case MATCH_SESSIONS_BY_PROVIDER:
                tableToChange = TABLE_SESSION_COOKIES;
                changedItemId = url.getPathSegments().get(2);
                idColumnName = Im.SessionCookies.ACCOUNT;
                break;

            case MATCH_PROVIDER_SETTINGS_BY_ID_AND_NAME:
                tableToChange = TABLE_PROVIDER_SETTINGS;

                String providerId = url.getPathSegments().get(1);
                String name = url.getPathSegments().get(2);

                appendWhere(whereClause, Im.ProviderSettings.PROVIDER, "=", providerId);
                appendWhere(whereClause, Im.ProviderSettings.NAME, "=", name);
                break;

            case MATCH_OUTGOING_RMQ_MESSAGES:
                tableToChange = TABLE_OUTGOING_RMQ_MESSAGES;
                break;

            case MATCH_LAST_RMQ_ID:
                tableToChange = TABLE_LAST_RMQ_ID;
                break;

            case MATCH_BRANDING_RESOURCE_MAP_CACHE:
                tableToChange = TABLE_BRANDING_RESOURCE_MAP_CACHE;
                break;

            default:
                throw new UnsupportedOperationException("Cannot delete that URL: " + url);
        }

        if (idColumnName == null) {
            idColumnName = "_id";
        }

        if (changedItemId != null) {
            appendWhere(whereClause, idColumnName, "=", changedItemId);
        }

        if (DBG) log("delete from " + url + " WHERE  " + whereClause);

        final SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int count = db.delete(tableToChange, whereClause.toString(), whereArgs);

        if (contactDeleted && count > 0) {
            // since the contact cleanup triggers no longer work for cross database tables,
            // we have to do it by hand here.
            performContactRemovalCleanup(deletedContactId);
        }

        if (count > 0) {
            // In most case, we query contacts with presence and chats joined, thus
            // we should also notify that contacts changes when presence or chats changed.
            if (match == MATCH_CHATS || match == MATCH_CHATS_ID
                    || match == MATCH_PRESENCE || match == MATCH_PRESENCE_ID
                    || match == MATCH_CONTACTS_BAREBONE) {
                getContext().getContentResolver().notifyChange(Im.Contacts.CONTENT_URI, null);
            } else if (notifyMessagesContentUri) {
                getContext().getContentResolver().notifyChange(Im.Messages.CONTENT_URI, null);
            } else if (notifyGroupMessagesContentUri) {
                getContext().getContentResolver().notifyChange(Im.GroupMessages.CONTENT_URI, null);
            } else if (notifyContactListContentUri) {
                getContext().getContentResolver().notifyChange(Im.ContactList.CONTENT_URI, null);
            } else if (notifyProviderAccountContentUri) {
                if (DBG) log("notify delete for " + Im.Provider.CONTENT_URI_WITH_ACCOUNT);
                getContext().getContentResolver().notifyChange(Im.Provider.CONTENT_URI_WITH_ACCOUNT,
                        null);
            }
            
            if (backfillQuickSwitchSlots) {
                backfillQuickSwitchSlots();
            }
        }

        return count;
    }

    public int updateInternal(Uri url, ContentValues values, String userWhere,
            String[] whereArgs) {
        String tableToChange;
        String idColumnName = null;
        String changedItemId = null;
        int count;

        StringBuilder whereClause = new StringBuilder();
        if(userWhere != null) {
            whereClause.append(userWhere);
        }

        boolean notifyMessagesContentUri = false;
        boolean notifyGroupMessagesContentUri = false;
        boolean notifyContactListContentUri = false;
        boolean notifyProviderAccountContentUri = false;

        int match = mUrlMatcher.match(url);
        switch (match) {
            case MATCH_PROVIDERS_BY_ID:
                changedItemId = url.getPathSegments().get(1);
                // fall through
            case MATCH_PROVIDERS:
                tableToChange = TABLE_PROVIDERS;
                break;

            case MATCH_ACCOUNTS_BY_ID:
                changedItemId = url.getPathSegments().get(1);
                // fall through
            case MATCH_ACCOUNTS:
                tableToChange = TABLE_ACCOUNTS;
                notifyProviderAccountContentUri = true;
                break;

            case MATCH_ACCOUNT_STATUS:
                changedItemId = url.getPathSegments().get(1);
                // fall through
            case MATCH_ACCOUNTS_STATUS:
                tableToChange = TABLE_ACCOUNT_STATUS;
                notifyProviderAccountContentUri = true;
                break;

            case MATCH_CONTACTS:
            case MATCH_CONTACTS_BAREBONE:
                tableToChange = TABLE_CONTACTS;
                break;

            case MATCH_CONTACTS_BY_PROVIDER:
                tableToChange = TABLE_CONTACTS;
                changedItemId = url.getPathSegments().get(2);
                idColumnName = Im.Contacts.ACCOUNT;
                break;

            case MATCH_CONTACT:
                tableToChange = TABLE_CONTACTS;
                changedItemId = url.getPathSegments().get(1);
                break;

            case MATCH_CONTACTS_BULK:
                count = updateBulkContacts(values, userWhere);
                // notify change using the "content://im/contacts" url,
                // so the change will be observed by listeners interested
                // in contacts changes.
                if (count > 0) {
                    getContext().getContentResolver().notifyChange(
                            Im.Contacts.CONTENT_URI, null);
                }
                return count;

            case MATCH_CONTACTLIST:
                tableToChange = TABLE_CONTACT_LIST;
                changedItemId = url.getPathSegments().get(1);
                notifyContactListContentUri = true;
                break;

            case MATCH_CONTACTS_ETAGS:
                tableToChange = TABLE_CONTACTS_ETAG;
                break;

            case MATCH_CONTACTS_ETAG:
                tableToChange = TABLE_CONTACTS_ETAG;
                changedItemId = url.getPathSegments().get(1);
                break;

            case MATCH_MESSAGES:
                tableToChange = TABLE_MESSAGES;
                break;

            case MATCH_MESSAGES_BY_CONTACT:
                tableToChange = TABLE_MESSAGES;
                appendWhere(whereClause, Im.Messages.ACCOUNT, "=",
                        url.getPathSegments().get(2));
                appendWhere(whereClause, Im.Messages.CONTACT, "=",
                    decodeURLSegment(url.getPathSegments().get(3)));
                notifyMessagesContentUri = true;
                break;

            case MATCH_MESSAGE:
                tableToChange = TABLE_MESSAGES;
                changedItemId = url.getPathSegments().get(1);
                notifyMessagesContentUri = true;
                break;

            case MATCH_GROUP_MESSAGES:
                tableToChange = TABLE_GROUP_MESSAGES;
                break;

            case MATCH_GROUP_MESSAGE_BY:
                tableToChange = TABLE_GROUP_MESSAGES;
                changedItemId = url.getPathSegments().get(1);
                idColumnName = Im.GroupMessages.GROUP;
                notifyGroupMessagesContentUri = true;
                break;

            case MATCH_GROUP_MESSAGE:
                tableToChange = TABLE_GROUP_MESSAGES;
                changedItemId = url.getPathSegments().get(1);
                notifyGroupMessagesContentUri = true;
                break;

            case MATCH_AVATARS:
                tableToChange = TABLE_AVATARS;
                break;

            case MATCH_AVATAR:
                tableToChange = TABLE_AVATARS;
                changedItemId = url.getPathSegments().get(1);
                break;

            case MATCH_AVATAR_BY_PROVIDER:
                tableToChange = TABLE_AVATARS;
                changedItemId = url.getPathSegments().get(2);
                idColumnName = Im.Avatars.ACCOUNT;
                break;

            case MATCH_CHATS:
                tableToChange = TABLE_CHATS;
                break;

            case MATCH_CHATS_ID:
                tableToChange = TABLE_CHATS;
                changedItemId = url.getPathSegments().get(1);
                idColumnName = Im.Chats.CONTACT_ID;
                break;

            case MATCH_PRESENCE:
                //if (DBG) log("update presence: where='" + userWhere + "'");
                tableToChange = TABLE_PRESENCE;
                break;

            case MATCH_PRESENCE_ID:
                tableToChange = TABLE_PRESENCE;
                changedItemId = url.getPathSegments().get(1);
                idColumnName = Im.Presence.CONTACT_ID;
                break;

            case MATCH_PRESENCE_BULK:
                count = updateBulkPresence(values, userWhere, whereArgs);
                // notify change using the "content://im/contacts" url,
                // so the change will be observed by listeners interested
                // in contacts changes.
                if (count > 0) {
                     getContext().getContentResolver().notifyChange(Im.Contacts.CONTENT_URI, null);
                }

                return count;

            case MATCH_INVITATION:
                tableToChange = TABLE_INVITATIONS;
                changedItemId = url.getPathSegments().get(1);
                break;

            case MATCH_SESSIONS:
                tableToChange = TABLE_SESSION_COOKIES;
                break;

            case MATCH_PROVIDER_SETTINGS_BY_ID_AND_NAME:
                tableToChange = TABLE_PROVIDER_SETTINGS;

                String providerId = url.getPathSegments().get(1);
                String name = url.getPathSegments().get(2);

                if (values.containsKey(Im.ProviderSettings.PROVIDER) ||
                        values.containsKey(Im.ProviderSettings.NAME)) {
                    throw new SecurityException("Cannot override the value for provider|name");
                }

                appendWhere(whereClause, Im.ProviderSettings.PROVIDER, "=", providerId);
                appendWhere(whereClause, Im.ProviderSettings.NAME, "=", name);

                break;

            case MATCH_OUTGOING_RMQ_MESSAGES:
                tableToChange = TABLE_OUTGOING_RMQ_MESSAGES;
                break;

            case MATCH_LAST_RMQ_ID:
                tableToChange = TABLE_LAST_RMQ_ID;
                break;

            default:
                throw new UnsupportedOperationException("Cannot update URL: " + url);
        }

        if (idColumnName == null) {
            idColumnName = "_id";
        }
        if(changedItemId != null) {
            appendWhere(whereClause, idColumnName, "=", changedItemId);
        }

        if (DBG) log("update " + url + " WHERE " + whereClause);

        final SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        count = db.update(tableToChange, values, whereClause.toString(), whereArgs);

        if (count > 0) {
            // In most case, we query contacts with presence and chats joined, thus
            // we should also notify that contacts changes when presence or chats changed.
            if (match == MATCH_CHATS || match == MATCH_CHATS_ID
                    || match == MATCH_PRESENCE || match == MATCH_PRESENCE_ID
                    || match == MATCH_CONTACTS_BAREBONE) {
                getContext().getContentResolver().notifyChange(Im.Contacts.CONTENT_URI, null);
            } else if (notifyMessagesContentUri) {
                if (DBG) log("notify change for " + Im.Messages.CONTENT_URI);
                getContext().getContentResolver().notifyChange(Im.Messages.CONTENT_URI, null);
            } else if (notifyGroupMessagesContentUri) {
                getContext().getContentResolver().notifyChange(Im.GroupMessages.CONTENT_URI, null);
            } else if (notifyContactListContentUri) {
                getContext().getContentResolver().notifyChange(Im.ContactList.CONTENT_URI, null);
            } else if (notifyProviderAccountContentUri) {
                if (DBG) log("notify change for " + Im.Provider.CONTENT_URI_WITH_ACCOUNT);
                getContext().getContentResolver().notifyChange(Im.Provider.CONTENT_URI_WITH_ACCOUNT,
                        null);
            }
        }

        return count;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode)
            throws FileNotFoundException {
        return openFileHelper(uri, mode);
    }

    private static void appendWhere(StringBuilder where, String columnName,
            String condition, Object value) {
        if (where.length() > 0) {
            where.append(" AND ");
        }
        where.append(columnName).append(condition);
        if(value != null) {
            DatabaseUtils.appendValueToSql(where, value);
        }
    }

    private static void appendWhere(StringBuilder where, String clause) {
        if (where.length() > 0) {
            where.append(" AND ");
        }
        where.append(clause);
    }

    private static String decodeURLSegment(String segment) {
        try {
            return URLDecoder.decode(segment, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            // impossible
            return segment;
        }
    }

    static void log(String message) {
        Log.d(LOG_TAG, message);
    }
}
