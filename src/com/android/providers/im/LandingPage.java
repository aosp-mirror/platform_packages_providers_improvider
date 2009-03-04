/*
 * Copyright (C) 2008 The Android Open Source Project
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

import android.app.ListActivity;
import android.app.ActivityManagerNative;
import android.app.ActivityThread;
import android.app.Application;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.database.Cursor;
import android.im.IImPlugin;
import android.im.ImPluginConsts;
import android.im.BrandingResourceIDs;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.IBinder;
import android.provider.Im;
import android.util.Log;
import android.util.AttributeSet;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.CursorAdapter;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;

import com.android.providers.im.R;

import dalvik.system.PathClassLoader;

public class LandingPage extends ListActivity implements View.OnCreateContextMenuListener {
    private static final String TAG = "IM";
    private final static boolean LOCAL_DEBUG = false;

    private static final int ID_SIGN_IN = Menu.FIRST + 1;
    private static final int ID_SIGN_OUT = Menu.FIRST + 2;
    private static final int ID_EDIT_ACCOUNT = Menu.FIRST + 3;
    private static final int ID_REMOVE_ACCOUNT = Menu.FIRST + 4;
    private static final int ID_SIGN_OUT_ALL = Menu.FIRST + 5;
    private static final int ID_ADD_ACCOUNT = Menu.FIRST + 6;
    private static final int ID_VIEW_CONTACT_LIST = Menu.FIRST + 7;
    private static final int ID_SETTINGS = Menu.FIRST + 8;

    private ProviderAdapter mAdapter;
    private Cursor mProviderCursor;

    private static final String[] PROVIDER_PROJECTION = {
            Im.Provider._ID,
            Im.Provider.NAME,
            Im.Provider.FULLNAME,
            Im.Provider.CATEGORY,
            Im.Provider.ACTIVE_ACCOUNT_ID,
            Im.Provider.ACTIVE_ACCOUNT_USERNAME,
            Im.Provider.ACTIVE_ACCOUNT_PW,
            Im.Provider.ACTIVE_ACCOUNT_LOCKED,
            Im.Provider.ACCOUNT_PRESENCE_STATUS,
            Im.Provider.ACCOUNT_CONNECTION_STATUS,
    };

    private static final int PROVIDER_ID_COLUMN = 0;
    private static final int PROVIDER_NAME_COLUMN = 1;
    private static final int PROVIDER_FULLNAME_COLUMN = 2;
    private static final int PROVIDER_CATEGORY_COLUMN = 3;
    private static final int ACTIVE_ACCOUNT_ID_COLUMN = 4;
    private static final int ACTIVE_ACCOUNT_USERNAME_COLUMN = 5;
    private static final int ACTIVE_ACCOUNT_PW_COLUMN = 6;
    private static final int ACTIVE_ACCOUNT_LOCKED = 7;
    private static final int ACCOUNT_PRESENCE_STATUS = 8;
    private static final int ACCOUNT_CONNECTION_STATUS = 9;

    private static final String PROVIDER_SELECTION = "providers.name!=?";

    private HashMap<String, PluginInfo> mProviderToPluginMap;
    private HashMap<Long, PluginInfo> mAccountToPluginMap;
    private HashMap<Long, BrandingResources> mBrandingResources;
    private BrandingResources mDefaultBrandingResources;

    private String[] mProviderSelectionArgs = new String[1];

    public class PluginInfo {
        public IImPlugin mPlugin;
        /**
         * The name of the package that the plugin is in.
         */
        public String mPackageName;

        /**
         * The name of the class that implements {@link @ImFrontDoorPlugin} in this plugin.
         */
        public String mClassName;

        /**
         * The full path to the location of the package that the plugin is in.
         */
        public String mSrcPath;

        public PluginInfo(IImPlugin plugin, String packageName, String className,
                String srcPath) {
            mPackageName = packageName;
            mClassName = className;
            mSrcPath = srcPath;
            mPlugin = plugin;
        }
    };

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        setTitle(R.string.landing_page_title);

        if (!loadPlugins()) {
            Log.e(TAG, "[onCreate] load plugin failed, no plugin found!");
            finish();
            return;
        }

        startPlugins();

        // get everything except for Google Talk.
        mProviderSelectionArgs[0] = Im.ProviderNames.GTALK;
        mProviderCursor = managedQuery(Im.Provider.CONTENT_URI_WITH_ACCOUNT,
                PROVIDER_PROJECTION,
                PROVIDER_SELECTION /* selection */,
                mProviderSelectionArgs /* selection args */,
                Im.Provider.DEFAULT_SORT_ORDER);
        mAdapter = new ProviderAdapter(this, mProviderCursor);
        setListAdapter(mAdapter);
        
        rebuildAccountToPluginMap();

        mBrandingResources = new HashMap<Long, BrandingResources>();
        loadDefaultBrandingRes();
        loadBrandingResources();

        registerForContextMenu(getListView());
    }

    @Override
    protected void onRestart() {
        super.onRestart();

        // refresh the accountToPlugin map after mProviderCursor is requeried
        if (!rebuildAccountToPluginMap()) {
            Log.w(TAG, "[onRestart] rebuiltAccountToPluginMap failed, reload plugins...");
            
            if (!loadPlugins()) {
                Log.e(TAG, "[onRestart] load plugin failed, no plugin found!");
                finish();
                return;
            }
            rebuildAccountToPluginMap();
        }

        startPlugins();
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopPlugins();
    }

    private boolean loadPlugins() {
        mProviderToPluginMap = new HashMap<String, PluginInfo>();
        
        PackageManager pm = getPackageManager();
        List<ResolveInfo> plugins = pm.queryIntentServices(
                new Intent(ImPluginConsts.PLUGIN_ACTION_NAME),
                PackageManager.GET_META_DATA);
        for (ResolveInfo info : plugins) {
            if (Log.isLoggable(TAG, Log.DEBUG)) log("loadPlugins: found plugin " + info);

            ServiceInfo serviceInfo = info.serviceInfo;
            if (serviceInfo == null) {
                Log.e(TAG, "Ignore bad IM frontdoor plugin: " + info);
                continue;
            }

            IImPlugin plugin = null;

            // Load the plug-in directly from the apk instead of binding the service
            // and calling through the IPC binder API. It's more effective in this way
            // and we can avoid the async behaviors of binding service.
            PathClassLoader classLoader = new PathClassLoader(serviceInfo.applicationInfo.sourceDir,
                    getClassLoader());
            try {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    log("loadPlugin: load class " + serviceInfo.name);
                }
                Class cls = classLoader.loadClass(serviceInfo.name);
                Object newInstance = cls.newInstance();
                Method m;

                // call "attach" method, so the plugin will get initialized with the proper context
                m = cls.getMethod("attach", Context.class, ActivityThread.class, String.class,
                        IBinder.class, Application.class, Object.class);
                m.invoke(newInstance,
                        new Object[] {this, null, serviceInfo.name, null, getApplication(),
                                ActivityManagerNative.getDefault()});

                // call "bind" to get the plugin object
                m = cls.getMethod("onBind", Intent.class);
                plugin = (IImPlugin)m.invoke(newInstance, new Object[]{null});
            } catch (ClassNotFoundException e) {
                Log.e(TAG, "Failed load the plugin", e);
            } catch (IllegalAccessException e) {
                Log.e(TAG, "Failed load the plugin", e);
            } catch (InstantiationException e) {
                Log.e(TAG, "Failed load the plugin", e);
            } catch (SecurityException e) {
                Log.e(TAG, "Failed load the plugin", e);
            } catch (NoSuchMethodException e) {
                Log.e(TAG, "Failed load the plugin", e);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Failed load the plugin", e);
            } catch (InvocationTargetException e) {
                Log.e(TAG, "Failed load the plugin", e);
            }

            if (plugin != null) {
                if (Log.isLoggable(TAG, Log.DEBUG)) log("loadPlugin: plugin " + plugin + " loaded");
                ArrayList<String> providers = getSupportedProviders(plugin);

                if (providers == null || providers.size() == 0) {
                    Log.e(TAG, "Ignore bad IM frontdoor plugin: " + info + ". No providers found");
                    continue;
                }

                PluginInfo pluginInfo = new PluginInfo(plugin,
                        serviceInfo.packageName,
                        serviceInfo.name,
                        serviceInfo.applicationInfo.sourceDir);

                for (String providerName : providers) {
                    mProviderToPluginMap.put(providerName, pluginInfo);
                }
            }
        }

        return mProviderToPluginMap.size() > 0;
    }

    private void startPlugins() {
        Iterator<PluginInfo> itor = mProviderToPluginMap.values().iterator();

        while (itor.hasNext()) {
            PluginInfo pluginInfo = itor.next();
            try {
                pluginInfo.mPlugin.onStart();
            } catch (RemoteException e) {
                Log.e(TAG, "Could not start plugin " + pluginInfo.mPackageName, e);
            }
        }
    }

    private void stopPlugins() {
        Iterator<PluginInfo> itor = mProviderToPluginMap.values().iterator();

        while (itor.hasNext()) {
            PluginInfo pluginInfo = itor.next();
            try {
                pluginInfo.mPlugin.onStop();
            } catch (RemoteException e) {
                Log.e(TAG, "Could not stop plugin " + pluginInfo.mPackageName, e);
            }
        }
    }

    private ArrayList<String> getSupportedProviders(IImPlugin plugin) {
        ArrayList<String> providers = null;

        try {
            providers = (ArrayList<String>) plugin.getSupportedProviders();
        } catch (RemoteException ex) {
            Log.e(TAG, "getSupportedProviders caught ", ex);
        }

        return providers;
    }

    private void loadDefaultBrandingRes() {
        HashMap<Integer, Integer> resMapping = new HashMap<Integer, Integer>();

        resMapping.put(BrandingResourceIDs.DRAWABLE_LOGO, R.drawable.imlogo_s);
        resMapping.put(BrandingResourceIDs.DRAWABLE_PRESENCE_ONLINE,
                android.R.drawable.presence_online);
        resMapping.put(BrandingResourceIDs.DRAWABLE_PRESENCE_AWAY,
                android.R.drawable.presence_away);
        resMapping.put(BrandingResourceIDs.DRAWABLE_PRESENCE_BUSY,
                android.R.drawable.presence_busy);
        resMapping.put(BrandingResourceIDs.DRAWABLE_PRESENCE_INVISIBLE,
                android.R.drawable.presence_invisible);
        resMapping.put(BrandingResourceIDs.DRAWABLE_PRESENCE_OFFLINE,
                android.R.drawable.presence_offline);
        resMapping.put(BrandingResourceIDs.STRING_MENU_CONTACT_LIST,
                R.string.menu_view_contact_list);

        mDefaultBrandingResources = new BrandingResources(this, resMapping, null /* default res */);
    }

    private void loadBrandingResources() {
        mProviderCursor.moveToFirst();
        do {
            long providerId = mProviderCursor.getLong(PROVIDER_ID_COLUMN);
            String providerName = mProviderCursor.getString(PROVIDER_NAME_COLUMN);
            PluginInfo pluginInfo = mProviderToPluginMap.get(providerName);

            if (pluginInfo == null) {
                Log.w(TAG, "[LandingPage] loadBrandingResources: no plugin found for " + providerName);
                continue;
            }
            
            if (!mBrandingResources.containsKey(providerId)) {
                BrandingResources res = new BrandingResources(this, pluginInfo, providerName,
                        mDefaultBrandingResources);
                mBrandingResources.put(providerId, res);
            }
        } while (mProviderCursor.moveToNext()) ;
    }

    public BrandingResources getBrandingResource(long providerId) {
        BrandingResources res = mBrandingResources.get(providerId);
        return res == null ? mDefaultBrandingResources : res;
    }

    private boolean rebuildAccountToPluginMap() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            log("rebuildAccountToPluginMap");
        }
        
        if (mAccountToPluginMap != null) {
            mAccountToPluginMap.clear();
        }
        
        mAccountToPluginMap = new HashMap<Long, PluginInfo>();

        mProviderCursor.moveToFirst();

        boolean retVal = true;

        do {
            long accountId = mProviderCursor.getLong(ACTIVE_ACCOUNT_ID_COLUMN);

            if (accountId == 0) {
                continue;
            }
            
            String name = mProviderCursor.getString(PROVIDER_NAME_COLUMN);
            PluginInfo pluginInfo = mProviderToPluginMap.get(name);
            if (pluginInfo != null) {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    log("rebuildAccountToPluginMap: add plugin for acct=" + accountId + ", provider=" + name);
                }
                mAccountToPluginMap.put(accountId, pluginInfo);
            } else {
                Log.w(TAG, "[LandingPage] no plugin found for " + name);
                retVal = false;
            }
        } while (mProviderCursor.moveToNext()) ;

        return retVal;
    }

    private void signIn(long accountId) {
        if (accountId == 0) {
            Log.w(TAG, "signIn: account id is 0, bail");
            return;
        }

        boolean isAccountEditible = mProviderCursor.getInt(ACTIVE_ACCOUNT_LOCKED) == 0;
        if (isAccountEditible && mProviderCursor.isNull(ACTIVE_ACCOUNT_PW_COLUMN)) {
            // no password, edit the account
            if (Log.isLoggable(TAG, Log.DEBUG)) log("no pw for account " + accountId);
            Intent intent = getEditAccountIntent();
            startActivity(intent);
            return;
        }


        PluginInfo pluginInfo = mAccountToPluginMap.get(accountId);
        if (pluginInfo == null) {
            Log.e(TAG, "signIn: cannot find plugin for account " + accountId);
            return;
        }

        try {
            if (Log.isLoggable(TAG, Log.DEBUG)) log("sign in for account " + accountId);
            pluginInfo.mPlugin.signIn(accountId);
        } catch (RemoteException ex) {
            Log.e(TAG, "signIn failed", ex);
        }
    }

    boolean isSigningIn(Cursor cursor) {
        int connectionStatus = cursor.getInt(ACCOUNT_CONNECTION_STATUS);
        return connectionStatus == Im.ConnectionStatus.CONNECTING;
    }

    boolean isSignedIn(Cursor cursor) {
        int connectionStatus = cursor.getInt(ACCOUNT_CONNECTION_STATUS);
        return connectionStatus == Im.ConnectionStatus.ONLINE;
    }

    private boolean allAccountsSignedOut() {
        mProviderCursor.moveToFirst();
        do {
            if (isSignedIn(mProviderCursor)) {
                return false;
            }
        } while (mProviderCursor.moveToNext()) ;

        return true;
    }

    private void signoutAll() {
        do {
            long accountId = mProviderCursor.getLong(ACTIVE_ACCOUNT_ID_COLUMN);
            signOut(accountId);
        } while (mProviderCursor.moveToNext()) ;
    }

    private void signOut(long accountId) {
        if (accountId == 0) {
            Log.w(TAG, "signOut: account id is 0, bail");
            return;
        }

        PluginInfo pluginInfo = mAccountToPluginMap.get(accountId);
        if (pluginInfo == null) {
            Log.e(TAG, "signOut: cannot find plugin for account " + accountId);
            return;
        }

        try {
            if (Log.isLoggable(TAG, Log.DEBUG)) log("sign out for account " + accountId);
            pluginInfo.mPlugin.signOut(accountId);
        } catch (RemoteException ex) {
            Log.e(TAG, "signOut failed", ex);
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.findItem(ID_SIGN_OUT_ALL).setVisible(!allAccountsSignedOut());
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, ID_SIGN_OUT_ALL, 0, R.string.menu_sign_out_all)
                .setIcon(android.R.drawable.ic_menu_close_clear_cancel);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case ID_SIGN_OUT_ALL:
                signoutAll();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        AdapterView.AdapterContextMenuInfo info;
        try {
            info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        } catch (ClassCastException e) {
            Log.e(TAG, "bad menuInfo", e);
            return;
        }

        Cursor providerCursor = (Cursor) getListAdapter().getItem(info.position);
        menu.setHeaderTitle(providerCursor.getString(PROVIDER_FULLNAME_COLUMN));

        if (providerCursor.isNull(ACTIVE_ACCOUNT_ID_COLUMN)) {
            menu.add(0, ID_ADD_ACCOUNT, 0, R.string.menu_add_account);
            return;
        }

        long providerId = providerCursor.getLong(PROVIDER_ID_COLUMN);
        boolean isLoggingIn = isSigningIn(providerCursor);
        boolean isLoggedIn = isSignedIn(providerCursor);

        if (!isLoggedIn) {
            menu.add(0, ID_SIGN_IN, 0, R.string.sign_in).setIcon(com.android.internal.R.drawable.ic_menu_login);
        } else {
            BrandingResources brandingRes = getBrandingResource(providerId);
            menu.add(0, ID_VIEW_CONTACT_LIST, 0,
                    brandingRes.getString(BrandingResourceIDs.STRING_MENU_CONTACT_LIST));
            menu.add(0, ID_SIGN_OUT, 0, R.string.menu_sign_out)
                .setIcon(android.R.drawable.ic_menu_close_clear_cancel);
        }

        boolean isAccountEditible = providerCursor.getInt(ACTIVE_ACCOUNT_LOCKED) == 0;
        if (isAccountEditible && !isLoggingIn && !isLoggedIn) {
            menu.add(0, ID_EDIT_ACCOUNT, 0, R.string.menu_edit_account)
                .setIcon(android.R.drawable.ic_menu_edit);
            menu.add(0, ID_REMOVE_ACCOUNT, 0, R.string.menu_remove_account)
                .setIcon(android.R.drawable.ic_menu_delete);
        }

        // always add a settings menu item
        menu.add(0, ID_SETTINGS, 0, R.string.menu_settings);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info;
        try {
            info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        } catch (ClassCastException e) {
            Log.e(TAG, "bad menuInfo", e);
            return false;
        }
        long providerId = info.id;
        Cursor providerCursor = (Cursor) getListAdapter().getItem(info.position);
        long accountId = providerCursor.getLong(ACTIVE_ACCOUNT_ID_COLUMN);

        switch (item.getItemId()) {
            case ID_EDIT_ACCOUNT:
            {
                startActivity(getEditAccountIntent());
                return true;
            }

            case ID_REMOVE_ACCOUNT:
            {
                Uri accountUri = ContentUris.withAppendedId(Im.Account.CONTENT_URI, accountId);
                getContentResolver().delete(accountUri, null, null);
                // Requery the cursor to force refreshing screen
                providerCursor.requery();
                return true;
            }

            case ID_VIEW_CONTACT_LIST:
            {
                Intent intent = getViewContactsIntent();
                startActivity(intent);
                return true;
            }
            case ID_ADD_ACCOUNT:
            {
                startActivity(getCreateAccountIntent());
                return true;
            }

            case ID_SIGN_IN:
            {
                signIn(accountId);
                return true;
            }

            case ID_SIGN_OUT:
            {
                // TODO: progress bar
                signOut(accountId);
                return true;
            }

            case ID_SETTINGS:
            {
                Intent intent = new Intent(Intent.ACTION_VIEW, Im.ProviderSettings.CONTENT_URI);
                intent.addCategory(getProviderCategory(providerCursor));
                intent.putExtra("providerId", providerId);
                startActivity(intent);
                return true;
            }

        }

        return false;
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        Intent intent = null;
        mProviderCursor.moveToPosition(position);

        if (mProviderCursor.isNull(ACTIVE_ACCOUNT_ID_COLUMN)) {
            // add account
            intent = getCreateAccountIntent();
        } else {
            int state = mProviderCursor.getInt(ACCOUNT_CONNECTION_STATUS);

            if (state == Im.ConnectionStatus.OFFLINE || state == Im.ConnectionStatus.CONNECTING) {
                boolean isAccountEditible = mProviderCursor.getInt(ACTIVE_ACCOUNT_LOCKED) == 0;
                if (isAccountEditible && mProviderCursor.isNull(ACTIVE_ACCOUNT_PW_COLUMN)) {
                    // no password, edit the account
                    intent = getEditAccountIntent();
                } else {
                    long accountId = mProviderCursor.getLong(ACTIVE_ACCOUNT_ID_COLUMN);
                    signIn(accountId);
                }
            } else {
                intent = getViewContactsIntent();
            }
        }

        if (intent != null) {
            startActivity(intent);
        }
    }

    Intent getCreateAccountIntent() {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_INSERT);

        long providerId = mProviderCursor.getLong(PROVIDER_ID_COLUMN);
        intent.setData(ContentUris.withAppendedId(Im.Provider.CONTENT_URI, providerId));
        intent.addCategory(getProviderCategory(mProviderCursor));
        return intent;
    }

    Intent getEditAccountIntent() {
        Intent intent = new Intent(Intent.ACTION_EDIT,
                ContentUris.withAppendedId(Im.Account.CONTENT_URI,
                        mProviderCursor.getLong(ACTIVE_ACCOUNT_ID_COLUMN)));
        intent.addCategory(getProviderCategory(mProviderCursor));
        return intent;
    }

    Intent getViewContactsIntent() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Im.Contacts.CONTENT_URI);
        intent.addCategory(getProviderCategory(mProviderCursor));
        intent.putExtra("accountId", mProviderCursor.getLong(ACTIVE_ACCOUNT_ID_COLUMN));
        return intent;
    }

    private String getProviderCategory(Cursor cursor) {
        return cursor.getString(PROVIDER_CATEGORY_COLUMN);
    }

    
    static void log(String msg) {
        Log.d(TAG, "[LandingPage]" + msg);
    }

    private class ProviderListItemFactory implements LayoutInflater.Factory {
        public View onCreateView(String name, Context context, AttributeSet attrs) {
            if (name != null && name.equals(ProviderListItem.class.getName())) {
                return new ProviderListItem(context, LandingPage.this);
            }
            return null;
        }
    }

    private final class ProviderAdapter extends CursorAdapter {
        private LayoutInflater mInflater;

        public ProviderAdapter(Context context, Cursor c) {
            super(context, c);
            mInflater = LayoutInflater.from(context).cloneInContext(context);
            mInflater.setFactory(new ProviderListItemFactory());
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            // create a custom view, so we can manage it ourselves. Mainly, we want to
            // initialize the widget views (by calling getViewById()) in newView() instead of in
            // bindView(), which can be called more often.
            ProviderListItem view = (ProviderListItem) mInflater.inflate(
                    R.layout.account_view, parent, false);
            view.init(cursor);
            return view;
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            ((ProviderListItem) view).bindView(cursor);
        }
    }

}
