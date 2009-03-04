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

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.RemoteException;
import android.util.Log;

import java.util.Map;

/**
 * The provider specific branding resources.
 */
public class BrandingResources {
    private static final String TAG = "IM";
    private static final boolean LOCAL_DEBUG = false;

    private Map<Integer, Integer> mResMapping;
    private Resources mPackageRes;

    private BrandingResources mDefaultRes;

    /**
     * Creates a new BrandingResource of a specific plug-in. The resources will
     * be retrieved from the plug-in package.
     *
     * @param context The current application context.
     * @param pluginInfo The info about the plug-in.
     * @param provider the name of the IM service provider.
     * @param defaultRes The default branding resources. If the resource is not
     *            found in the plug-in, the default resource will be returned.
     */
    public BrandingResources(Context context, LandingPage.PluginInfo pluginInfo, String provider,
            BrandingResources defaultRes) {
        String packageName = null;
        mDefaultRes = defaultRes;

        try {
            mResMapping = pluginInfo.mPlugin.getResourceMapForProvider(provider);
            packageName = pluginInfo.mPlugin.getResourcePackageNameForProvider(provider);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed load the plugin resource map", e);
        }

        if (packageName == null) {
            packageName = pluginInfo.mPackageName;
        }

        PackageManager pm = context.getPackageManager();
        try {
            if (LOCAL_DEBUG) log("load resources from " + packageName);
            mPackageRes = pm.getResourcesForApplication(packageName);
        } catch (NameNotFoundException e) {
            Log.e(TAG, "Can not load resources from " + packageName);
        }
    }

    /**
     * Creates a BrandingResource with application context and the resource ID map.
     * The resource will be retrieved from the context directly instead from the plug-in package.
     *
     * @param context
     * @param resMapping
     */
    public BrandingResources(Context context, Map<Integer, Integer> resMapping,
            BrandingResources defaultRes) {
        mPackageRes = context.getResources();
        mResMapping = resMapping;
        mDefaultRes = defaultRes;
    }

    /**
     * Gets a drawable object associated with a particular resource ID defined
     * in {@link com.android.im.plugin.BrandingResourceIDs}
     *
     * @param id The ID defined in
     *            {@link com.android.im.plugin.BrandingResourceIDs}
     * @return Drawable An object that can be used to draw this resource.
     */
    public Drawable getDrawable(int id) {
        int resId = getPackageResourceId(id);
        if (resId != 0) {
            return mPackageRes.getDrawable(resId);
        } else if (mDefaultRes != null){
            return mDefaultRes.getDrawable(id);
        } else {
            return null;
        }
    }

    /**
     * Gets the string value associated with a particular resource ID defined in
     * {@link com.android.im.plugin.BrandingResourceIDs}
     *
     * @param id The ID of the string resource defined in
     *            {@link com.android.im.plugin.BrandingResourceIDs}
     * @param formatArgs The format arguments that will be used for
     *            substitution.
     * @return The string data associated with the resource
     */
    public String getString(int id, Object... formatArgs) {
        int resId = getPackageResourceId(id);
        if (resId != 0) {
            return mPackageRes.getString(resId, formatArgs);
        } else if (mDefaultRes != null){
            return  mDefaultRes.getString(id, formatArgs);
        } else {
            return null;
        }
    }

    /**
     * Gets the string array associated with a particular resource ID defined in
     * {@link com.android.im.plugin.BrandingResourceIDs}
     *
     * @param id The ID of the string resource defined in
     *            {@link com.android.im.plugin.BrandingResourceIDs}
     * @return The string array associated with the resource.
     */
    public String[] getStringArray(int id) {
        int resId = getPackageResourceId(id);
        if (resId != 0) {
            return mPackageRes.getStringArray(resId);
        } else if (mDefaultRes != null){
            return mDefaultRes.getStringArray(id);
        } else {
            return null;
        }
    }

    private int getPackageResourceId(int id) {
        if (mResMapping == null || mPackageRes == null) {
            return 0;
        }
        Integer resId = mResMapping.get(id);
        return resId == null ? 0 : resId;
    }

    private void log(String msg) {
        Log.d(TAG, "[BrandingRes] " + msg);
    }
}
