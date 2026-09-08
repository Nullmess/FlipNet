# FlipNet Bridge

FlipNet Bridge connects the Flipper app to network system and extension services.
It provides USB CDC Bluetooth LE FN2 FNCAP2 FNERR1 and the declarative plugin runtime.

## Install

```sh
python -m pip install .
```

Linux source installation with device permissions and a service:

```sh
./installers/install-linux.sh
```

The Linux installer supports udev/eudev and Alpine mdev/mdevd. It grants the
service user access to the serial group used by `/dev/ttyACM*`.

### Alpine Linux

FlipNet supports Alpine Linux with musl and OpenRC. A minimal installation
should provide Python, pip and a virtual-environment implementation:

```sh
doas apk add python3 py3-pip py3-virtualenv
./installers/install-linux.sh
```

For BLE transport, install the D-Bus and BlueZ runtime too:

```sh
doas apk add dbus bluez
doas rc-update add dbus default
doas rc-update add bluetooth default
doas rc-service dbus start
doas rc-service bluetooth start
FLIPNET_TRANSPORT=ble ./installers/install-linux.sh
```

USB transport does not require BlueZ. The installer works with udev/eudev or
mdev/mdevd and also works on minimal Alpine systems where `getent` is absent.

Existing persistent data from older FlipNet releases is migrated
automatically. In particular, historical schema 3 files are upgraded to the
current schema without requiring deletion of `~/.config/flipnet/data.json`.

## Check

```sh
flipnet-bridge doctor --strict
```

## Run

```sh
flipnet-bridge
```
