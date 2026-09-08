PYTHON ?= python3
DOCKER_COMPOSE ?= docker compose
DOCKER_PROGRESS ?= plain

.PHONY: help fap fap-launch android android-install bridge-wheel bridge-install clean

help:
	@printf '%s\n' \
		'make fap             Build the Flipper application' \
		'make fap-launch      Build and launch it on a connected Flipper' \
		'make android         Build the Android APK' \
		'make android-install Build and install the Android APK' \
		'make bridge-wheel    Build the Python bridge wheel' \
		'make bridge-install  Install the Python bridge locally' \
		'make clean           Remove generated build outputs'

fap:
	$(DOCKER_COMPOSE) build --progress=$(DOCKER_PROGRESS) flipper-build
	$(DOCKER_COMPOSE) run --rm flipper-build

fap-launch:
	$(DOCKER_COMPOSE) build --progress=$(DOCKER_PROGRESS) flipper-build
	$(DOCKER_COMPOSE) run --rm flipper-launch

android:
	$(DOCKER_COMPOSE) run --rm --build android-build

android-install:
	$(DOCKER_COMPOSE) run --rm --build android-install

bridge-wheel:
	rm -rf bridge/dist
	mkdir -p bridge/dist
	$(PYTHON) -m pip wheel --no-deps --wheel-dir bridge/dist ./bridge

bridge-install:
	$(PYTHON) -m pip install ./bridge

clean:
	rm -rf \
		bridge/build bridge/dist bridge/*.egg-info \
		apps/flipper/.ufbt apps/flipper/build apps/flipper/dist \
		apps/android/.gradle apps/android/build apps/android/app/build apps/android/dist
