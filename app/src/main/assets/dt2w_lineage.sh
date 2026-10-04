#!/system/bin/sh
# DT2W (LineageOS) - Q25 Toolbox
#
# Hardware gesture-wake does not exist on this kernel (the touch module's own gesture code is never enabled
# by the device tree, and no sysfs/proc node or gesture key is exposed). But the Synaptics controller keeps
# reporting touches with the screen off, so a double tap can be detected in software.
#
# Unlike the older always-on watchdog (dt2w.sh), which streamed every touch of the day through awk and slowly
# degraded SystemUI, this script lives ONLY while the screen is off: the accessibility service starts it on
# ACTION_SCREEN_OFF and stops it on ACTION_SCREEN_ON, and it ends by itself after waking the device.
#
# Measured on the device with the screen off: well-spaced taps are all reported, a fast burst loses about
# one tap in four, so a double tap is accepted when two taps fall within GAP_MAX of each other.

LOCK=/data/adb/.dt2w_lineage.lock
# PID lock that also checks /proc/$PID/cmdline (a bare kill -0 can match a recycled PID).
if [ -f "$LOCK" ]; then
    OLD_PID=$(cat "$LOCK" 2>/dev/null)
    if [ -n "$OLD_PID" ] && grep -q dt2w_lineage "/proc/$OLD_PID/cmdline" 2>/dev/null; then
        exit 0
    fi
fi
echo $$ > "$LOCK"
# setsid makes this the leader of its own process group, so `kill 0` takes getevent and awk down with it.
trap 'trap - TERM INT; rm -f "$LOCK"; kill 0' TERM INT

BL=/sys/class/leds/lcd-backlight/brightness
GAP_MAX=0.8      # max seconds between the two taps (DOWN -> DOWN)
GAP_MIN=0.06     # ignore contact bounce closer than this
PRESS_MAX=0.35   # a longer press is not a tap

find_touch_dev() {
    for d in /dev/input/event*; do
        if getevent -pl "$d" 2>/dev/null | grep -q "BTN_TOUCH"; then
            echo "$d"
            return 0
        fi
    done
    return 1
}

DEV=$(find_touch_dev) || { rm -f "$LOCK"; exit 1; }

getevent -lt "$DEV" 2>/dev/null | awk -v bl="$BL" -v gmax="$GAP_MAX" -v gmin="$GAP_MIN" -v pmax="$PRESS_MAX" '
    # The backlight node is the cheap "is the screen really off" signal (0 = off).
    function screen_off(   b, rc) {
        rc = (getline b < bl)
        close(bl)
        return (rc > 0 && b == "0")
    }
    {
        # Line: [ 4095.524777] EV_KEY  BTN_TOUCH  DOWN  - the space after "[" makes the timestamp field $2.
        ts = $2
        gsub(/[\[\]]/, "", ts)
        t = ts + 0
    }
    /BTN_TOUCH/ && /DOWN/ {
        gap = t - last_down
        if (prev_valid && gap <= gmax && gap >= gmin && screen_off()) {
            system("input keyevent KEYCODE_WAKEUP")
            exit
        }
        pending_down = t
        next
    }
    /BTN_TOUCH/ && /UP/ {
        if (pending_down > 0) {
            if ((t - pending_down) <= pmax) {
                prev_valid = 1
                last_down = pending_down
            } else {
                prev_valid = 0
            }
            pending_down = 0
        }
    }
' &
wait
rm -f "$LOCK"
kill 0
