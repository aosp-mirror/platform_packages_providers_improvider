ifeq (0,1)

LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := user

LOCAL_SRC_FILES :=  $(call all-java-files-under,src)

LOCAL_JAVA_LIBRARIES := ext \
                        com.android.im.plugin           # TODO: remove this and load this on demand.
                                                        # (HACK: include this so we can load the
                                                        # classes defined in this plugin package)

LOCAL_PACKAGE_NAME := ImProvider
LOCAL_CERTIFICATE := vendor/google/certs/app

include $(BUILD_PACKAGE)

# additionally, build sub-tests in a separate .apk
include $(call all-makefiles-under,$(LOCAL_PATH))

endif # ifeq (0,1)
