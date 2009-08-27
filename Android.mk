LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := user

LOCAL_SRC_FILES :=  $(call all-java-files-under,src)

LOCAL_JAVA_LIBRARIES := ext \

LOCAL_PACKAGE_NAME := ImProvider
LOCAL_CERTIFICATE := vendor/google/certs/app

include $(BUILD_PACKAGE)

# additionally, build sub-tests in a separate .apk
include $(call all-makefiles-under,$(LOCAL_PATH))
