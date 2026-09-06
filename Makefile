.PHONY: all help clean debug release redmi tablet apk install

# Terminal colors
GREEN := \033[0;32m
CYAN  := \033[0;36m
YELLOW := \033[1;33m
RESET := \033[0m

APP_NAME := termux-sorter
GRADLE := ./gradlew
ADB ?= adb

BUILD_DIR := app/build/outputs/apk

# Default target
all: help

help:
	@echo ""
	@echo "$(CYAN)$(APP_NAME) Android build system$(RESET)"
	@echo ""
	@echo "$(GREEN)Available targets:$(RESET)"
	@echo "  $(YELLOW)make debug$(RESET)      Build debug APK"
	@echo "  $(YELLOW)make release$(RESET)    Build release APK"
	@echo "  $(YELLOW)make redmi$(RESET)      Build for Redmi (Android 14 / API 34)"
	@echo "  $(YELLOW)make tablet$(RESET)     Build for Shield Tablet (Android 8.1 / API 27)"
	@echo "  $(YELLOW)make clean$(RESET)      Remove build files"
	@echo "  $(YELLOW)make install$(RESET)    Install debug APK via adb"
	@echo ""

# Build debug APK
# Uses the current Android SDK/NDK configuration from Termux
# Android version is controlled by Gradle configuration
#
debug:
	@echo "$(GREEN)Building debug APK...$(RESET)"
	$(GRADLE) assembleDebug

# Build release APK
release:
	@echo "$(GREEN)Building release APK...$(RESET)"
	$(GRADLE) assembleRelease

# Redmi build profile
# Redmi Note 11 uses modern Android and can use the default SDK target
redmi:
	@echo "$(GREEN)Building for Redmi Android device...$(RESET)"
	ANDROID_API_LEVEL=34 $(GRADLE) assembleRelease

# Shield Tablet K1 Android 8.1 build profile
# Keeps compatibility with older Android runtime
# Minimum SDK remains defined in Gradle
#
tablet:
	@echo "$(GREEN)Building for NVIDIA Shield Tablet Android 8.1...$(RESET)"
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
