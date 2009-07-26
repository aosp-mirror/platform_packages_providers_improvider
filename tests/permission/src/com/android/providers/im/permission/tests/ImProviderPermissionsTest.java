/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.providers.im.permission.tests;

import java.io.IOException;

import android.net.Uri;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.MediumTest;

/**
 * Verify that protected Im provider actions require specific permissions.
 */
public class ImProviderPermissionsTest extends AndroidTestCase {

    private static final String CONTENT_IM = "content://im";

    /**
     * Test that an untrusted app cannot read from the im provider
     * <p>Tests Permission:
     *   {@link com.android.providers.im.Manifest.permission#READ_ONLY}
     */
    @MediumTest
    public void testReadImProvider() throws Exception {
        assertReadingContentUriRequiresPermission(Uri.parse(CONTENT_IM),
                "com.android.providers.im.permission.READ_ONLY");
    }

    /**
     * Test that an untrusted app cannot write to the download provider
     * <p>Tests Permission:
     *   {@link com.android.providers.downloads.Manifest.permission#ACCESS_DOWNLOAD_MANAGER}
     */
    @MediumTest
    public void testWriteImProvider() throws IOException {
        assertWritingContentUriRequiresPermission(Uri.parse(CONTENT_IM),
                "com.android.providers.im.permission.WRITE_ONLY");
    }
}
