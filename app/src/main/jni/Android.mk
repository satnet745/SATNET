# Capture JNI root path early and use consistently
JNI_PATH := $(call my-dir)
LOCAL_PATH := $(JNI_PATH)

SODIUM_ARCH_FOLDER := $(APP_ABI)
ifeq ($(SODIUM_ARCH_FOLDER),armeabi-v7a)
        SODIUM_ARCH_FOLDER = armv7-a
endif
ifeq ($(SODIUM_ARCH_FOLDER),x86)
        SODIUM_ARCH_FOLDER = i686
endif

# SODIUM_INCLUDE now points to source headers (libsodium built from source)
SODIUM_INCLUDE := $(JNI_PATH)/libsodium/src/libsodium/include

# Build iwconfig binary
include $(CLEAR_VARS)
LOCAL_MODULE:= iwconfig-NOPIE
LOCAL_SRC_FILES:= wireless_tools.29/iwlib.c wireless_tools.29/iwconfig.c
LOCAL_C_INCLUDES += wireless_tools.29/
include $(BUILD_EXECUTABLE)

include $(CLEAR_VARS)
LOCAL_MODULE:= iwconfig-PIE
LOCAL_SRC_FILES:= wireless_tools.29/iwlib.c wireless_tools.29/iwconfig.c
LOCAL_C_INCLUDES += wireless_tools.29/
LOCAL_CFLAGS += -fPIE
LOCAL_LDFLAGS += -fPIE -pie
include $(BUILD_EXECUTABLE)

# Build ifconfig binaries
include $(CLEAR_VARS)
LOCAL_MODULE:= ifconfig-NOPIE
LOCAL_SRC_FILES:= ifconfig/ifconfig.c
include $(BUILD_EXECUTABLE)

include $(CLEAR_VARS)
LOCAL_MODULE:= ifconfig-PIE
LOCAL_SRC_FILES:= ifconfig/ifconfig.c
LOCAL_CFLAGS += -fPIE
LOCAL_LDFLAGS += -fPIE -pie
include $(BUILD_EXECUTABLE)

# Build adhoc-edify - DISABLED: depends on hardware_legacy which is not available
# include $(LOCAL_PATH)/adhoc-edify/Android.mk

# Codec2 shared library
include $(CLEAR_VARS)
LOCAL_MODULE := libservalcodec2
LOCAL_CFLAGS := -O3 -ffast-math -DNDEBUG
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/codec2
LOCAL_SRC_FILES := codec2/dump.c codec2/lpc.c \
        codec2/nlp.c codec2/postfilter.c \
        codec2/sine.c codec2/codec2.c \
        codec2/fifo.c codec2/fdmdv.c \
        codec2/kiss_fft.c codec2/interp.c \
        codec2/lsp.c codec2/phase.c \
        codec2/quantise.c codec2/pack.c \
        codec2/codebook.c codec2/codebookd.c \
        codec2/codebookvq.c codec2/codebookjnd.c \
        codec2/codebookjvm.c codec2/codebookvqanssi.c \
        codec2/codebookdt.c codec2/codebookge.c \
	codec2_jni.c
include $(BUILD_SHARED_LIBRARY)


# Build libopus (this include overrides LOCAL_PATH internally)
include $(JNI_PATH)/opus/Android.mk
# Restore LOCAL_PATH to JNI root after including opus
LOCAL_PATH := $(JNI_PATH)

# libnl static library
include $(CLEAR_VARS)
LOCAL_SRC_FILES :=  libnl/cache.c \
        libnl/data.c \
        libnl/nl.c \
        libnl/doc.c \
        libnl/cache_mngr.c \
        libnl/addr.c \
        libnl/socket.c \
        libnl/fib_lookup/lookup.c \
        libnl/fib_lookup/request.c \
        libnl/msg.c \
        libnl/object.c \
        libnl/attr.c \
        libnl/utils.c \
        libnl/cache_mngt.c \
        libnl/handlers.c \
        libnl/genl/ctrl.c \
        libnl/genl/mngt.c \
        libnl/genl/family.c \
        libnl/genl/genl.c \
        libnl/route/rtnl.c \
        libnl/route/route_utils.c
LOCAL_C_INCLUDES := $(LOCAL_PATH)/libnl
LOCAL_MODULE := libnl
include $(BUILD_STATIC_LIBRARY)


include $(CLEAR_VARS)
FILE_LIST := $(wildcard $(LOCAL_PATH)/iw/*.c)
LOCAL_SRC_FILES := $(FILE_LIST:$(LOCAL_PATH)/%=%)
LOCAL_CFLAGS := -I$(LOCAL_PATH)/iw/ -I$(LOCAL_PATH)/libnl/ $(TARGET_GLOBAL_CFLAGS) $(PRIVATE_ARM_CFLAGS)
LOCAL_MODULE := iw-NOPIE
LOCAL_LDFLAGS := -Wl,--no-gc-sections
LOCAL_STATIC_LIBRARIES += libnl
include $(BUILD_EXECUTABLE)

include $(CLEAR_VARS)
FILE_LIST := $(wildcard $(LOCAL_PATH)/iw/*.c)
LOCAL_SRC_FILES := $(FILE_LIST:$(LOCAL_PATH)/%=%)
LOCAL_CFLAGS := -I$(LOCAL_PATH)/iw/ -I$(LOCAL_PATH)/libnl/ $(TARGET_GLOBAL_CFLAGS) $(PRIVATE_ARM_CFLAGS) -fPIE
LOCAL_MODULE := iw-PIE
LOCAL_LDFLAGS := -Wl,--no-gc-sections -fPIE -pie
LOCAL_STATIC_LIBRARIES += libnl
include $(BUILD_EXECUTABLE)

# Build libsodium from source (this include overrides LOCAL_PATH internally)
include $(JNI_PATH)/libsodium/Android.mk
# Restore LOCAL_PATH to JNI root BEFORE including serval-dna to avoid wrong path
LOCAL_PATH := $(JNI_PATH)

# Build serval-dna static library (defines module 'servaldstatic')
include $(LOCAL_PATH)/serval-dna/Android.mk

# Ensure we are back at JNI root
LOCAL_PATH := $(JNI_PATH)

# servaldaemon shared library linking against servaldstatic
include $(CLEAR_VARS)
LOCAL_STATIC_LIBRARIES := servaldstatic
LOCAL_C_INCLUDES += $(JNI_PATH)/serval-dna
LOCAL_SRC_FILES := batphone_features.c
LOCAL_MODULE := servaldaemon
LOCAL_LDLIBS += -llog
include $(BUILD_SHARED_LIBRARY)

# Optional wrapped executable
ifdef SERVALD_WRAP
  include $(CLEAR_VARS)
  LOCAL_SRC_FILES:= $(JNI_PATH)/serval-dna/servalwrap.c
  LOCAL_MODULE:= servald
  LOCAL_CFLAGS += -fPIE
  LOCAL_LDFLAGS += -fPIE -pie
  include $(BUILD_EXECUTABLE)
endif

# Optional simple executable
ifdef SERVALD_SIMPLE
  include $(CLEAR_VARS)
  LOCAL_C_INCLUDES += $(JNI_PATH)/serval-dna
  LOCAL_SRC_FILES := batphone_features.c
  LOCAL_MODULE:= servaldsimple
  LOCAL_CFLAGS += -fPIE
  LOCAL_LDFLAGS += -fPIE -pie
  LOCAL_LDLIBS += -L$(SYSROOT)/usr/lib -llog
  LOCAL_STATIC_LIBRARIES := servaldstatic
  include $(BUILD_EXECUTABLE)
endif
