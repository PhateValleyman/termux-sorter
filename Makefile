.PHONY: all help clean debug release redmi tablet apk install env

# Terminal colors
GREEN := \033[0;32m
CYAN  := \033[0;36m
YELLOW := \033[1;33m
RED := \033[0;31m
RESET := \033[0m

APP_NAME := termux-sorter
GRADLE := ./gradlew
ADB ?= adb
BUILD_DIR := app/build/outputs/apk

# Android environment defaults for Termux
ANDROID_SDK_ROOT ?= $(ANDROID_HOME)
NDK_ROOT ?= $(ANDROID_NDK_HOME)

# Default target
all: help

help:
	@echo ""
	@echo "$(CYAN)$(APP_NAME) Android build system$(RESET)"
	@echo ""
	@echo "$(GREEN)Build targets:$(RESET)"
	@echo "  $(YELLOW)make debug$(RESET)      Build debug APK"
	@echo "  $(YELLOW)make release$(RESET)    Build release APK"
	@echo "  $(YELLOW)make redmi$(RESET)      Build Redmi profile (Android 14 / API 34)"
	@echo "  $(YELLOW)make tablet$(RESET)     Build Shield Tablet profile (Android 8.1 / API 27)"
	@echo "  $(YELLOW)make clean$(RESET)      Remove build files"
	@echo "  $(YELLOW)make install$(RESET)    Install debug APK via adb"
	@echo "  $(YELLOW)make apk$(RESET)        List generated APK files"
	@echo ""
	@echo "$(GREEN)Current Android build environment:$(RESET)"
	@echo "  TERMUX_PREFIX      = $$PREFIX"
	@echo "  Android SDK root   = $${ANDROID_SDK_ROOT:-not set}"
	@echo "  Android NDK root   = $${NDK_ROOT:-not set}"
	@echo "  NDK version        = $$(basename $${NDK_ROOT:-unknown})"
	@echo "  ANDROID_API_LEVEL  = $${ANDROID_API_LEVEL:-not set}"
	@echo "  TARGET_ARCH        = $${TARGET_ARCH:-$$(uname -m)}"
	@echo "  JAVA_HOME          = $${JAVA_HOME:-not set}"
	@echo "  Gradle             = $(GRADLE)"
	@echo ""
	@echo "$(GREEN)Recommended Termux variables:$(RESET)"
	@echo "  export ANDROID_SDK_ROOT=\$$HOME/lib/android-sdk"
	@echo "  export ANDROID_NDK_HOME=\$$ANDROID_SDK_ROOT/ndk/<version>"
	@echo "  export PATH=\$$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin:\$$PATH"
	@echo ""

# Print only build environment
env:
	@echo "$(CYAN)Android NDK environment$(RESET)"
	@echo "SDK: $${ANDROID_SDK_ROOT:-not set}"
	@echo "NDK: $${NDK_ROOT:-not set}"
	@echo "API: $${ANDROID_API_LEVEL:-not set}"

# Build debug APK
debug:
	@echo "$(GREEN)Building debug APK...$(RESET)"
	$(GRADLE) assembleDebug

# Build release APK
release:
	@echo "$(GREEN)Building release APK...$(RESET)"
	$(GRADLE) assembleRelease

# Redmi Note 11 Android 14 profile
redmi:
	@echo "$(GREEN)Building for Redmi Android device...$(RESET)"
	ANDROID_API_LEVEL=34 $(GRADLE) assembleRelease

# NVIDIA Shield Tablet Android 8.1 profile
tablet:
	@echo "$(GREEN)Building for Android 8.1 tablet...$(RESET)"
	ANDROID_API_LEVEL=27 $(GRADLE) assembleRelease

# Remove generated build output
clean:
	@echo "$(YELLOW)Cleaning build files...$(RESET)"
	$(GRADLE) clean

# Install debug APK using adb
install: debug
	@echo "$(GREEN)Installing APK...$(RESET)"
	$(ADB) install -r $(BUILD_DIR)/debug/app-debug.apk

# Show generated APK files
apk:
	@find $(BUILD_DIR) -type f -name '*.apk' -print
